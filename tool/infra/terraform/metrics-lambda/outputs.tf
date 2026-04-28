output "ecr_repository_url" {
  description = "Push the lambda container image here (`docker tag … && docker push <url>:<tag>`)."
  value       = aws_ecr_repository.metrics_compute.repository_url
}

output "lambda_function_arn" {
  description = "Pass this as ANALYSIS_LAMBDA_ARN to the analysis tool."
  value       = aws_lambda_function.metrics.arn
}

output "lambda_function_name" {
  description = "For aws-cli debugging (`aws lambda invoke --function-name …`)."
  value       = aws_lambda_function.metrics.function_name
}

output "bundle_bucket_name" {
  description = "Pass this as ANALYSIS_BUNDLE_BUCKET to the analysis tool."
  value       = aws_s3_bucket.bundles.bucket
}

output "caller_policy_arn" {
  description = "Attach this managed policy to the principal that runs the analysis tool."
  value       = aws_iam_policy.caller.arn
}

output "aws_region" {
  description = "Region where everything was deployed; pass as ANALYSIS_AWS_REGION."
  value       = var.aws_region
}

output "lambda_log_group" {
  description = "CloudWatch log group for handler output. `aws logs tail <name> --follow` to live-stream."
  value       = aws_cloudwatch_log_group.lambda.name
}
