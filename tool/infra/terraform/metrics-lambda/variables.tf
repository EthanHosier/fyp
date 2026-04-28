variable "aws_region" {
  description = "AWS region where everything is deployed. Default matches the AWS CLI's default region so LambdaClient.builder().build() in the analysis tool resolves to the same place without needing ANALYSIS_AWS_REGION set."
  type        = string
  default     = "us-east-1"
}

variable "project" {
  description = "Project name prefix for resource naming + tags."
  type        = string
  default     = "fyp"
}

variable "env" {
  description = "Environment slug (dev / prod). Becomes part of resource names."
  type        = string
  default     = "dev"
}

variable "lambda_image_tag" {
  description = <<EOT
Tag in the metrics-compute ECR repo to point the Lambda at. The image
must already be pushed before `terraform apply` reaches the lambda
function (the apply will fail otherwise).

Workflow on first deploy:
  1. terraform apply -target=aws_ecr_repository.metrics_compute
  2. ./gradlew :metrics-lambda:fatJar
  3. docker build -t metrics-lambda:dev tool/metrics-lambda
  4. docker tag + push to the ECR repo URL output above
  5. terraform apply
EOT
  type        = string
  default     = "dev"
}

variable "bundle_retention_days" {
  description = "Days a shadow-repo bundle in S3 lives before lifecycle deletes it."
  type        = number
  default     = 7
}

variable "lambda_memory_mb" {
  description = "Lambda memory in MB. Must accommodate Gradle daemon-equivalent build."
  type        = number
  default     = 4096
}

variable "lambda_ephemeral_storage_mb" {
  description = "Lambda /tmp size in MB. Holds bundle clones + Gradle build outputs."
  type        = number
  default     = 10240
}

variable "lambda_timeout_seconds" {
  description = "Per-invocation timeout. Cap on the slowest possible build+test pair."
  type        = number
  default     = 900
}
