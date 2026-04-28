// AWS infra for the metrics-lambda fan-out path.
//
//   ┌─────────────────┐   git bundle   ┌──────────────┐
//   │ analysis tool   │───────────────▶│ S3 bundles   │
//   │ (caller)        │                │ bucket       │
//   │                 │   InvokeFunction (one per SHA) │
//   │                 │───────────────▶┌──────────────┐
//   └─────────────────┘                │ Lambda fn    │
//                                      │ (container)  │
//                                      └──────┬───────┘
//                                             │ GetObject
//                                             ▼
//                                     S3 bundles bucket
//
// Resources defined here:
//   - aws_s3_bucket.bundles           shadow-repo bundles, content-addressed
//   - aws_ecr_repository.metrics_compute  the lambda's container image registry
//   - aws_iam_role.lambda_exec        Lambda execution role (logs + S3 read)
//   - aws_lambda_function.metrics     the function itself
//   - aws_iam_policy.caller           policy doc the analysis tool's principal attaches
//
// Deploy on first apply:
//   terraform apply -target=aws_ecr_repository.metrics_compute
//   ./gradlew :metrics-lambda:fatJar
//   docker build -t metrics-lambda:dev tool/metrics-lambda
//   docker tag metrics-lambda:dev <repo-url>:dev
//   docker push <repo-url>:dev
//   terraform apply

locals {
  name_prefix = "${var.project}-${var.env}"
  tags = {
    project = var.project
    env     = var.env
    managed = "terraform"
  }
}

data "aws_caller_identity" "current" {}
data "aws_region" "current" {}

// -- S3: shadow-repo bundle store -------------------------------------------

resource "aws_s3_bucket" "bundles" {
  bucket        = "${local.name_prefix}-shadow-bundles"
  force_destroy = false // bundles are cheap to recreate but not free
  tags          = local.tags
}

resource "aws_s3_bucket_public_access_block" "bundles" {
  bucket                  = aws_s3_bucket.bundles.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_server_side_encryption_configuration" "bundles" {
  bucket = aws_s3_bucket.bundles.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "bundles" {
  bucket = aws_s3_bucket.bundles.id
  rule {
    id     = "expire-bundles"
    status = "Enabled"
    filter {} // applies to every object
    expiration {
      days = var.bundle_retention_days
    }
  }
}

// -- ECR: container image for the lambda -----------------------------------

resource "aws_ecr_repository" "metrics_compute" {
  name                 = "${local.name_prefix}-metrics-compute"
  image_tag_mutability = "MUTABLE"
  // Tags are mutable so the same `dev` tag can be re-pushed during
  // iteration. Production should pin to immutable digests via
  // var.lambda_image_tag.
  image_scanning_configuration {
    scan_on_push = true
  }
  tags = local.tags
}

resource "aws_ecr_lifecycle_policy" "metrics_compute" {
  repository = aws_ecr_repository.metrics_compute.name
  policy = jsonencode({
    rules = [
      {
        rulePriority = 1
        description  = "Keep last 10 images, expire older"
        selection = {
          tagStatus   = "any"
          countType   = "imageCountMoreThan"
          countNumber = 10
        }
        action = { type = "expire" }
      }
    ]
  })
}

// -- IAM: Lambda execution role --------------------------------------------

data "aws_iam_policy_document" "lambda_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["lambda.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "lambda_exec" {
  name               = "${local.name_prefix}-metrics-compute-exec"
  assume_role_policy = data.aws_iam_policy_document.lambda_assume.json
  tags               = local.tags
}

// CloudWatch Logs (the basic execution policy AWS publishes).
resource "aws_iam_role_policy_attachment" "lambda_logs" {
  role       = aws_iam_role.lambda_exec.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

// Read bundles from S3.
data "aws_iam_policy_document" "lambda_s3_read" {
  statement {
    actions = ["s3:GetObject"]
    resources = [
      "${aws_s3_bucket.bundles.arn}/*",
    ]
  }
}

resource "aws_iam_role_policy" "lambda_s3_read" {
  name   = "s3-bundles-read"
  role   = aws_iam_role.lambda_exec.id
  policy = data.aws_iam_policy_document.lambda_s3_read.json
}

// -- Lambda function -------------------------------------------------------

resource "aws_lambda_function" "metrics" {
  function_name = "${local.name_prefix}-metrics-compute"
  role          = aws_iam_role.lambda_exec.arn

  package_type = "Image"
  image_uri    = "${aws_ecr_repository.metrics_compute.repository_url}:${var.lambda_image_tag}"

  // Match the architecture the Dockerfile builds on the typical dev
  // host (Apple Silicon → arm64). Graviton Lambda is also ~20% cheaper
  // per GB-second than x86. If a CI runner ever builds on x86, force
  // `docker build --platform linux/arm64`.
  architectures = ["arm64"]

  memory_size = var.lambda_memory_mb
  timeout     = var.lambda_timeout_seconds
  ephemeral_storage {
    size = var.lambda_ephemeral_storage_mb
  }

  // SnapStart would shave ~1-3 s off handler-side JVM cold start but
  // caps /tmp at 512 MB — incompatible with the 10 GB we need for
  // bundle clones + Gradle build/. Dropped: the bigger lever is baking
  // the Gradle dep cache into the image layer (Dockerfile follow-up).

  tags = local.tags
}

resource "aws_cloudwatch_log_group" "lambda" {
  name              = "/aws/lambda/${aws_lambda_function.metrics.function_name}"
  retention_in_days = 14
  tags              = local.tags
}

// -- Caller policy ----------------------------------------------------------
// Attach this to whatever principal runs the analysis tool (an IAM user
// for local dev, an EC2 instance role for the analysis server, etc.). It
// grants exactly what RemoteCheckpointExecutor needs: invoke the
// function, plus put/head bundles into the bucket.

data "aws_iam_policy_document" "caller" {
  statement {
    sid     = "InvokeMetricsCompute"
    actions = ["lambda:InvokeFunction"]
    resources = [
      aws_lambda_function.metrics.arn,
      "${aws_lambda_function.metrics.arn}:*", // any alias / version
    ]
  }
  statement {
    sid     = "WriteBundles"
    actions = ["s3:PutObject", "s3:HeadObject"]
    resources = [
      "${aws_s3_bucket.bundles.arn}/*",
    ]
  }
}

resource "aws_iam_policy" "caller" {
  name        = "${local.name_prefix}-metrics-compute-caller"
  description = "Permissions a client needs to drive metrics-compute fan-out."
  policy      = data.aws_iam_policy_document.caller.json
  tags        = local.tags
}
