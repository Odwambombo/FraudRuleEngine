variable "project_name" {
  description = "Short, lowercase project name used in resource names and tags."
  type        = string

  validation {
    condition     = can(regex("^[a-z0-9][a-z0-9-]{1,38}[a-z0-9]$", var.project_name))
    error_message = "project_name must be 3-40 lowercase letters, numbers, or hyphens, and must start and end with a letter or number."
  }
}

variable "environment" {
  description = "Deployment environment, for example staging or production."
  type        = string

  validation {
    condition     = can(regex("^[a-z0-9][a-z0-9-]{1,18}[a-z0-9]$", var.environment))
    error_message = "environment must be 3-20 lowercase letters, numbers, or hyphens, and must start and end with a letter or number."
  }
}

variable "alb_arn" {
  description = "ARN of the existing Application Load Balancer to associate with the REGIONAL WAF web ACL."
  type        = string

  validation {
    condition     = can(regex("^arn:[^:]+:elasticloadbalancing:[^:]+:[0-9]{12}:loadbalancer/app/.+$", var.alb_arn))
    error_message = "alb_arn must be an Application Load Balancer ARN."
  }
}

variable "target_group_arn" {
  description = "ARN of the existing ALB target group used by the ECS service."
  type        = string

  validation {
    condition     = can(regex("^arn:[^:]+:elasticloadbalancing:[^:]+:[0-9]{12}:targetgroup/.+$", var.target_group_arn))
    error_message = "target_group_arn must be an Application Load Balancer target group ARN."
  }
}

variable "ecs_cluster_name" {
  description = "Name of the existing ECS cluster."
  type        = string

  validation {
    condition     = length(trimspace(var.ecs_cluster_name)) > 0
    error_message = "ecs_cluster_name cannot be empty."
  }
}

variable "ecs_service_name" {
  description = "Name of the existing ECS service."
  type        = string

  validation {
    condition     = length(trimspace(var.ecs_service_name)) > 0
    error_message = "ecs_service_name cannot be empty."
  }
}

variable "rds_db_instance_identifier" {
  description = "DBInstanceIdentifier of the existing RDS PostgreSQL instance."
  type        = string

  validation {
    condition     = length(trimspace(var.rds_db_instance_identifier)) > 0
    error_message = "rds_db_instance_identifier cannot be empty."
  }
}

variable "ecr_repository_name" {
  description = "Optional ECR repository name. Defaults to <project_name>-<environment>."
  type        = string
  default     = null
  nullable    = true

  validation {
    condition = var.ecr_repository_name == null ? true : (
      length(var.ecr_repository_name) <= 256 && can(regex(
        "^([a-z0-9]+([._-][a-z0-9]+)*/)*[a-z0-9]+([._-][a-z0-9]+)*$",
        var.ecr_repository_name
      ))
    )
    error_message = "ecr_repository_name must be a valid private ECR repository name."
  }
}

variable "ecr_kms_key_arn" {
  description = "Optional customer-managed KMS key ARN for ECR. AES-256 ECR encryption is used when null."
  type        = string
  default     = null
  nullable    = true

  validation {
    condition     = var.ecr_kms_key_arn == null || can(regex("^arn:[^:]+:kms:[^:]+:[0-9]{12}:key/.+$", var.ecr_kms_key_arn))
    error_message = "ecr_kms_key_arn must be a KMS key ARN or null."
  }
}

variable "ecr_retained_image_count" {
  description = "Maximum number of images retained after lifecycle evaluation. Size this to cover the rollback window."
  type        = number
  default     = 50

  validation {
    condition     = var.ecr_retained_image_count >= 1 && floor(var.ecr_retained_image_count) == var.ecr_retained_image_count
    error_message = "ecr_retained_image_count must be a positive integer."
  }
}

variable "ecr_untagged_image_retention_days" {
  description = "Age in days after which untagged ECR images are expired."
  type        = number
  default     = 14

  validation {
    condition     = var.ecr_untagged_image_retention_days >= 1 && floor(var.ecr_untagged_image_retention_days) == var.ecr_untagged_image_retention_days
    error_message = "ecr_untagged_image_retention_days must be a positive integer."
  }
}

variable "waf_rate_limit" {
  description = "Maximum requests allowed per source IP during waf_rate_evaluation_window_seconds before blocking."
  type        = number
  default     = 2000

  validation {
    condition     = var.waf_rate_limit >= 10 && var.waf_rate_limit <= 2000000000 && floor(var.waf_rate_limit) == var.waf_rate_limit
    error_message = "waf_rate_limit must be an integer between 10 and 2,000,000,000."
  }
}

variable "waf_rate_evaluation_window_seconds" {
  description = "Look-back window used by the WAF rate-based rule."
  type        = number
  default     = 300

  validation {
    condition     = contains([60, 120, 300, 600], var.waf_rate_evaluation_window_seconds)
    error_message = "waf_rate_evaluation_window_seconds must be one of 60, 120, 300, or 600."
  }
}

variable "waf_managed_rules_count_mode" {
  description = "Override AWS managed rule groups to COUNT for a staged rollout. Keep false to enforce their actions."
  type        = bool
  default     = false
}

variable "waf_sampled_requests_enabled" {
  description = "Whether AWS WAF stores sampled requests for rule inspection."
  type        = bool
  default     = true
}

variable "waf_log_blocked_requests_only" {
  description = "When true, retain only requests whose final WAF action is BLOCK. The default logs every evaluated request."
  type        = bool
  default     = false
}

variable "waf_redacted_headers" {
  description = "Lowercase request headers to redact from WAF logs."
  type        = set(string)
  default     = ["authorization", "cookie"]

  validation {
    condition = alltrue([
      for header in var.waf_redacted_headers : can(regex("^[a-z0-9-]+$", header))
    ])
    error_message = "Every waf_redacted_headers value must be a lowercase HTTP header name."
  }
}

variable "waf_log_retention_days" {
  description = "CloudWatch Logs retention for WAF request logs."
  type        = number
  default     = 90

  validation {
    condition = contains([
      1, 3, 5, 7, 14, 30, 60, 90, 120, 150, 180, 365, 400, 545, 731,
      1096, 1827, 2192, 2557, 2922, 3288, 3653
    ], var.waf_log_retention_days)
    error_message = "waf_log_retention_days must be a retention value supported by CloudWatch Logs."
  }
}

variable "cloudwatch_logs_kms_key_arn" {
  description = "Optional customer-managed KMS key ARN for the WAF CloudWatch log group."
  type        = string
  default     = null
  nullable    = true

  validation {
    condition     = var.cloudwatch_logs_kms_key_arn == null || can(regex("^arn:[^:]+:kms:[^:]+:[0-9]{12}:key/.+$", var.cloudwatch_logs_kms_key_arn))
    error_message = "cloudwatch_logs_kms_key_arn must be a KMS key ARN or null."
  }
}

variable "sns_kms_master_key_id" {
  description = "Optional KMS key ID, ARN, or alias for SNS server-side encryption. Its key policy must allow CloudWatch alarms and RDS event notifications to use it."
  type        = string
  default     = null
  nullable    = true

  validation {
    condition     = var.sns_kms_master_key_id == null ? true : length(trimspace(var.sns_kms_master_key_id)) > 0
    error_message = "sns_kms_master_key_id must be a non-empty KMS key ID, ARN, or alias, or null."
  }
}

variable "alarm_actions_enabled" {
  description = "Whether CloudWatch alarm actions and notifications are enabled."
  type        = bool
  default     = true
}

variable "alarm_period_seconds" {
  description = "Metric aggregation period shared by the alarms."
  type        = number
  default     = 300

  validation {
    condition     = var.alarm_period_seconds >= 60 && var.alarm_period_seconds % 60 == 0
    error_message = "alarm_period_seconds must be 60 or a larger multiple of 60."
  }
}

variable "alarm_evaluation_periods" {
  description = "Number of periods evaluated by each CloudWatch alarm."
  type        = number
  default     = 2

  validation {
    condition     = var.alarm_evaluation_periods >= 1 && floor(var.alarm_evaluation_periods) == var.alarm_evaluation_periods
    error_message = "alarm_evaluation_periods must be a positive integer."
  }
}

variable "alarm_datapoints_to_alarm" {
  description = "Number of breaching periods required to enter ALARM. Must not exceed alarm_evaluation_periods."
  type        = number
  default     = 2

  validation {
    condition     = var.alarm_datapoints_to_alarm >= 1 && floor(var.alarm_datapoints_to_alarm) == var.alarm_datapoints_to_alarm
    error_message = "alarm_datapoints_to_alarm must be a positive integer."
  }
}

variable "application_log_group_name" {
  description = "Optional name of the existing ECS application CloudWatch log group. When null, application log metric filters and their alarms are not created."
  type        = string
  default     = null
  nullable    = true

  validation {
    condition = var.application_log_group_name == null ? true : (
      length(var.application_log_group_name) <= 512 &&
      can(regex("^[A-Za-z0-9._/#-]+$", var.application_log_group_name))
    )
    error_message = "application_log_group_name must be null or a valid non-empty CloudWatch Logs log group name of at most 512 characters."
  }
}

variable "application_log_alarm_period_seconds" {
  description = "Aggregation period for application log-derived alarms. Thresholds are counts per this period."
  type        = number
  default     = 300

  validation {
    condition     = var.application_log_alarm_period_seconds >= 60 && var.application_log_alarm_period_seconds % 60 == 0
    error_message = "application_log_alarm_period_seconds must be 60 or a larger multiple of 60."
  }
}

variable "application_log_alarm_evaluation_periods" {
  description = "Number of periods evaluated by each application log-derived alarm."
  type        = number
  default     = 1

  validation {
    condition     = var.application_log_alarm_evaluation_periods >= 1 && floor(var.application_log_alarm_evaluation_periods) == var.application_log_alarm_evaluation_periods
    error_message = "application_log_alarm_evaluation_periods must be a positive integer."
  }
}

variable "application_log_alarm_datapoints_to_alarm" {
  description = "Number of breaching periods required for an application log-derived alarm. Must not exceed application_log_alarm_evaluation_periods."
  type        = number
  default     = 1

  validation {
    condition     = var.application_log_alarm_datapoints_to_alarm >= 1 && floor(var.application_log_alarm_datapoints_to_alarm) == var.application_log_alarm_datapoints_to_alarm
    error_message = "application_log_alarm_datapoints_to_alarm must be a positive integer."
  }
}

variable "application_exception_threshold" {
  description = "Unhandled application exception log events per application log alarm period before alarming."
  type        = number
  default     = 1

  validation {
    condition     = var.application_exception_threshold >= 1 && floor(var.application_exception_threshold) == var.application_exception_threshold
    error_message = "application_exception_threshold must be a positive integer."
  }
}

variable "fraud_processing_failure_threshold" {
  description = "Fraud transaction processing failure log events per application log alarm period before alarming."
  type        = number
  default     = 1

  validation {
    condition     = var.fraud_processing_failure_threshold >= 1 && floor(var.fraud_processing_failure_threshold) == var.fraud_processing_failure_threshold
    error_message = "fraud_processing_failure_threshold must be a positive integer."
  }
}

variable "authentication_failure_threshold" {
  description = "Authentication failure log events per application log alarm period before alarming."
  type        = number
  default     = 5

  validation {
    condition     = var.authentication_failure_threshold >= 1 && floor(var.authentication_failure_threshold) == var.authentication_failure_threshold
    error_message = "authentication_failure_threshold must be a positive integer."
  }
}

variable "alb_5xx_threshold" {
  description = "ALB-generated HTTP 5xx responses per alarm period."
  type        = number
  default     = 5

  validation {
    condition     = var.alb_5xx_threshold >= 0
    error_message = "alb_5xx_threshold cannot be negative."
  }
}

variable "target_5xx_threshold" {
  description = "Target-generated HTTP 5xx responses per alarm period."
  type        = number
  default     = 5

  validation {
    condition     = var.target_5xx_threshold >= 0
    error_message = "target_5xx_threshold cannot be negative."
  }
}

variable "alb_p95_latency_threshold_seconds" {
  description = "p95 ALB TargetResponseTime threshold in seconds."
  type        = number
  default     = 1

  validation {
    condition     = var.alb_p95_latency_threshold_seconds > 0
    error_message = "alb_p95_latency_threshold_seconds must be greater than zero."
  }
}

variable "unhealthy_target_threshold" {
  description = "Maximum unhealthy target count before alarming."
  type        = number
  default     = 1

  validation {
    condition     = var.unhealthy_target_threshold >= 1 && floor(var.unhealthy_target_threshold) == var.unhealthy_target_threshold
    error_message = "unhealthy_target_threshold must be a positive integer."
  }
}

variable "ecs_cpu_threshold_percent" {
  description = "Average ECS service CPU utilization percentage threshold."
  type        = number
  default     = 75

  validation {
    condition     = var.ecs_cpu_threshold_percent > 0 && var.ecs_cpu_threshold_percent <= 100
    error_message = "ecs_cpu_threshold_percent must be greater than zero and at most 100."
  }
}

variable "ecs_memory_threshold_percent" {
  description = "Average ECS service memory utilization percentage threshold."
  type        = number
  default     = 80

  validation {
    condition     = var.ecs_memory_threshold_percent > 0 && var.ecs_memory_threshold_percent <= 100
    error_message = "ecs_memory_threshold_percent must be greater than zero and at most 100."
  }
}

variable "rds_cpu_threshold_percent" {
  description = "Average RDS CPU utilization percentage threshold."
  type        = number
  default     = 80

  validation {
    condition     = var.rds_cpu_threshold_percent > 0 && var.rds_cpu_threshold_percent <= 100
    error_message = "rds_cpu_threshold_percent must be greater than zero and at most 100."
  }
}

variable "rds_free_storage_threshold_bytes" {
  description = "Minimum acceptable RDS FreeStorageSpace in bytes."
  type        = number
  default     = 5368709120

  validation {
    condition     = var.rds_free_storage_threshold_bytes > 0
    error_message = "rds_free_storage_threshold_bytes must be greater than zero."
  }
}

variable "rds_free_memory_threshold_bytes" {
  description = "Minimum acceptable RDS FreeableMemory in bytes."
  type        = number
  default     = 536870912

  validation {
    condition     = var.rds_free_memory_threshold_bytes > 0
    error_message = "rds_free_memory_threshold_bytes must be greater than zero."
  }
}

variable "rds_connections_threshold" {
  description = "Maximum acceptable RDS DatabaseConnections. Tune this below the instance's connection limit."
  type        = number
  default     = 100

  validation {
    condition     = var.rds_connections_threshold > 0
    error_message = "rds_connections_threshold must be greater than zero."
  }
}

variable "rds_read_latency_threshold_seconds" {
  description = "Maximum acceptable average RDS ReadLatency, in seconds."
  type        = number
  default     = 0.1

  validation {
    condition     = var.rds_read_latency_threshold_seconds > 0
    error_message = "rds_read_latency_threshold_seconds must be greater than zero."
  }
}

variable "rds_write_latency_threshold_seconds" {
  description = "Maximum acceptable average RDS WriteLatency, in seconds."
  type        = number
  default     = 0.1

  validation {
    condition     = var.rds_write_latency_threshold_seconds > 0
    error_message = "rds_write_latency_threshold_seconds must be greater than zero."
  }
}

variable "tags" {
  description = "Additional tags merged with the module's Project, Environment, and ManagedBy tags."
  type        = map(string)
  default     = {}
}

variable "cognito_enabled" {
  description = "Whether to create the environment's Cognito user pool, SPA client, managed login, scopes, and groups."
  type        = bool
  default     = false
}

variable "cognito_domain_prefix" {
  description = "Globally unique Amazon Cognito managed-login domain prefix. Required when cognito_enabled is true."
  type        = string
  default     = null
  nullable    = true

  validation {
    condition = var.cognito_domain_prefix == null ? true : (
      length(var.cognito_domain_prefix) <= 63 &&
      can(regex("^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$", var.cognito_domain_prefix)) &&
      !can(regex("(aws|amazon|cognito)", var.cognito_domain_prefix))
    )
    error_message = "cognito_domain_prefix must be null or 1-63 lowercase letters, numbers, or hyphens; start and end with a letter or number; and not contain aws, amazon, or cognito."
  }
}

variable "cognito_callback_urls" {
  description = "Allowed SPA OAuth callback URLs. HTTPS is required except for http://localhost development callbacks."
  type        = list(string)
  default     = []

  validation {
    condition = (
      length(var.cognito_callback_urls) <= 100 &&
      length(distinct(var.cognito_callback_urls)) == length(var.cognito_callback_urls) &&
      alltrue([
        for url in var.cognito_callback_urls :
        can(regex("^https://[^#\\s]+$", url)) ||
        can(regex("^http://localhost(:[0-9]{1,5})?(/[^#\\s]*)?$", url))
      ])
    )
    error_message = "cognito_callback_urls must contain at most 100 distinct absolute HTTPS URLs (or http://localhost URLs), without fragments or whitespace."
  }
}

variable "cognito_logout_urls" {
  description = "Allowed SPA post-logout redirect URLs. HTTPS is required except for http://localhost development URLs."
  type        = list(string)
  default     = []

  validation {
    condition = (
      length(var.cognito_logout_urls) <= 100 &&
      length(distinct(var.cognito_logout_urls)) == length(var.cognito_logout_urls) &&
      alltrue([
        for url in var.cognito_logout_urls :
        can(regex("^https://[^#\\s]+$", url)) ||
        can(regex("^http://localhost(:[0-9]{1,5})?(/[^#\\s]*)?$", url))
      ])
    )
    error_message = "cognito_logout_urls must contain at most 100 distinct absolute HTTPS URLs (or http://localhost URLs), without fragments or whitespace."
  }
}

variable "cognito_resource_server_identifier" {
  description = "Stable Cognito resource-server identifier prefixed to the application's four custom OAuth scopes."
  type        = string
  default     = "fraud-rule-engine"

  validation {
    condition = (
      length(var.cognito_resource_server_identifier) >= 1 &&
      length(var.cognito_resource_server_identifier) <= 256 &&
      trimspace(var.cognito_resource_server_identifier) == var.cognito_resource_server_identifier &&
      !can(regex("\\s", var.cognito_resource_server_identifier)) &&
      !can(regex("/$", var.cognito_resource_server_identifier))
    )
    error_message = "cognito_resource_server_identifier must be 1-256 non-whitespace characters and must not end with a slash."
  }
}

variable "cognito_self_signup_enabled" {
  description = "Whether managed login permits public self-registration. Defaults to administrator-created users only."
  type        = bool
  default     = false
}

variable "cognito_deletion_protection" {
  description = "Whether Cognito deletion protection is ACTIVE. Disable and apply before intentionally destroying the user pool."
  type        = bool
  default     = true
}
