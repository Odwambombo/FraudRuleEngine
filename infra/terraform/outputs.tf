output "ecr_repository_arn" {
  description = "ARN of the environment-specific ECR repository."
  value       = aws_ecr_repository.application.arn
}

output "ecr_repository_name" {
  description = "Name of the environment-specific ECR repository."
  value       = aws_ecr_repository.application.name
}

output "ecr_repository_url" {
  description = "Repository URL used to tag and push the application image."
  value       = aws_ecr_repository.application.repository_url
}

output "waf_web_acl_arn" {
  description = "ARN of the REGIONAL WAFv2 web ACL associated with the ALB."
  value       = aws_wafv2_web_acl.application.arn
}

output "waf_web_acl_id" {
  description = "ID of the REGIONAL WAFv2 web ACL."
  value       = aws_wafv2_web_acl.application.id
}

output "waf_log_group_arn" {
  description = "ARN of the CloudWatch log group receiving WAF request logs."
  value       = aws_cloudwatch_log_group.waf.arn
}

output "waf_log_group_name" {
  description = "Name of the CloudWatch log group receiving WAF request logs."
  value       = aws_cloudwatch_log_group.waf.name
}

output "alarm_sns_topic_arn" {
  description = "ARN of the SNS topic used by every CloudWatch alarm."
  value       = aws_sns_topic.alarms.arn
}

output "alarm_sns_topic_name" {
  description = "Name of the SNS alarm topic."
  value       = aws_sns_topic.alarms.name
}

output "cloudwatch_alarm_arns" {
  description = "CloudWatch alarm ARNs keyed by their short alarm purpose."
  value = merge(
    { for key, alarm in aws_cloudwatch_metric_alarm.standard : key => alarm.arn },
    { "alb-p95-latency" = aws_cloudwatch_metric_alarm.alb_p95_latency.arn },
    { for key, alarm in aws_cloudwatch_metric_alarm.application_log : key => alarm.arn }
  )
}

output "application_log_metric_filter_names" {
  description = "CloudWatch Logs metric-filter names keyed by application alert purpose. Empty when application_log_group_name is null."
  value       = { for key, filter in aws_cloudwatch_log_metric_filter.application : key => filter.name }
}

output "rds_event_subscription_arn" {
  description = "ARN of the RDS DB-instance event subscription that publishes availability, backup, and lifecycle events to the alarm SNS topic."
  value       = aws_db_event_subscription.application.arn
}

output "rds_event_subscription_name" {
  description = "Name of the RDS DB-instance event subscription."
  value       = aws_db_event_subscription.application.name
}

output "cognito_user_pool_id" {
  description = "Cognito user pool ID, or null when Cognito is disabled."
  value       = var.cognito_enabled ? aws_cognito_user_pool.application[0].id : null
}

output "cognito_user_pool_arn" {
  description = "Cognito user pool ARN, or null when Cognito is disabled."
  value       = var.cognito_enabled ? aws_cognito_user_pool.application[0].arn : null
}

output "cognito_issuer_uri" {
  description = "OIDC issuer URI for the application resource server, or null when Cognito is disabled."
  value = var.cognito_enabled ? format(
    "https://cognito-idp.%s.%s/%s",
    data.aws_region.current.region,
    data.aws_partition.current.dns_suffix,
    aws_cognito_user_pool.application[0].id,
  ) : null
}

output "cognito_domain" {
  description = "HTTPS base URL of the Cognito managed-login domain, or null when Cognito is disabled."
  value = var.cognito_enabled ? format(
    "https://%s.auth.%s.%s",
    aws_cognito_user_pool_domain.managed_login[0].domain,
    data.aws_region.current.region,
    replace(data.aws_partition.current.dns_suffix, "amazonaws", "amazoncognito"),
  ) : null
}

output "cognito_spa_client_id" {
  description = "Public SPA app-client ID, or null when Cognito is disabled. This client has no secret."
  value       = var.cognito_enabled ? aws_cognito_user_pool_client.spa[0].id : null
}

output "cognito_resource_server_identifier" {
  description = "Cognito resource-server identifier, or null when Cognito is disabled."
  value       = var.cognito_enabled ? aws_cognito_resource_server.application[0].identifier : null
}

output "cognito_full_scopes" {
  description = "Full custom OAuth scope strings keyed by application purpose, or null when Cognito is disabled."
  value       = var.cognito_enabled ? local.cognito_full_scopes : null
}

output "cognito_spa_allowed_scopes" {
  description = "Scopes permitted on the public SPA client, or null when Cognito is disabled."
  value = var.cognito_enabled ? concat(
    ["openid", "profile", "email"],
    local.cognito_spa_custom_scopes,
  ) : null
}

output "cognito_user_groups" {
  description = "Application groups created in the user pool, or null when Cognito is disabled."
  value       = var.cognito_enabled ? sort(keys(local.cognito_groups)) : null
}
