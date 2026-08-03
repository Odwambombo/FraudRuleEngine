data "aws_caller_identity" "current" {}

data "aws_partition" "current" {}

data "aws_region" "current" {}

locals {
  name_prefix = "${var.project_name}-${var.environment}"

  ecr_repository_name = coalesce(var.ecr_repository_name, local.name_prefix)
  alb_arn_suffix      = regex("loadbalancer/(.+)$", var.alb_arn)[0]
  target_group_suffix = regex("(targetgroup/.+)$", var.target_group_arn)[0]

  common_tags = merge(var.tags, {
    Environment = var.environment
    ManagedBy   = "Terraform"
    Project     = var.project_name
  })

  application_log_metric_namespace = "${var.project_name}/${var.environment}"

  application_log_alerts = var.application_log_group_name == null ? {} : {
    "application-exceptions" = {
      description    = "The application emitted one or more unhandled exception alerts."
      filter_pattern = "\"APPLICATION_EXCEPTION Unhandled application exception\""
      metric_name    = "ApplicationExceptionCount"
      threshold      = var.application_exception_threshold
    }
    "fraud-processing-failures" = {
      description    = "One or more fraud transaction processing attempts failed."
      filter_pattern = "\"FRAUD_PROCESSING_FAILURE Fraud transaction processing failed\""
      metric_name    = "FraudProcessingFailureCount"
      threshold      = var.fraud_processing_failure_threshold
    }
    "authentication-failures" = {
      description    = "Authentication failures exceeded the configured threshold."
      filter_pattern = "\"AUTHENTICATION_FAILURE Authentication failed\""
      metric_name    = "AuthenticationFailureCount"
      threshold      = var.authentication_failure_threshold
    }
  }

  standard_alarms = {
    "alb-5xx" = {
      description         = "ALB-generated 5xx responses exceeded the configured threshold."
      comparison_operator = "GreaterThanOrEqualToThreshold"
      metric_name         = "HTTPCode_ELB_5XX_Count"
      namespace           = "AWS/ApplicationELB"
      statistic           = "Sum"
      threshold           = var.alb_5xx_threshold
      treat_missing_data  = "notBreaching"
      dimensions = {
        LoadBalancer = local.alb_arn_suffix
      }
    }
    "target-5xx" = {
      description         = "Application targets returned too many 5xx responses."
      comparison_operator = "GreaterThanOrEqualToThreshold"
      metric_name         = "HTTPCode_Target_5XX_Count"
      namespace           = "AWS/ApplicationELB"
      statistic           = "Sum"
      threshold           = var.target_5xx_threshold
      treat_missing_data  = "notBreaching"
      dimensions = {
        LoadBalancer = local.alb_arn_suffix
        TargetGroup  = local.target_group_suffix
      }
    }
    "unhealthy-targets" = {
      description         = "One or more ALB targets are unhealthy."
      comparison_operator = "GreaterThanOrEqualToThreshold"
      metric_name         = "UnHealthyHostCount"
      namespace           = "AWS/ApplicationELB"
      statistic           = "Maximum"
      threshold           = var.unhealthy_target_threshold
      treat_missing_data  = "breaching"
      dimensions = {
        LoadBalancer = local.alb_arn_suffix
        TargetGroup  = local.target_group_suffix
      }
    }
    "ecs-cpu-high" = {
      description         = "ECS service average CPU utilization is high."
      comparison_operator = "GreaterThanOrEqualToThreshold"
      metric_name         = "CPUUtilization"
      namespace           = "AWS/ECS"
      statistic           = "Average"
      threshold           = var.ecs_cpu_threshold_percent
      treat_missing_data  = "missing"
      dimensions = {
        ClusterName = var.ecs_cluster_name
        ServiceName = var.ecs_service_name
      }
    }
    "ecs-memory-high" = {
      description         = "ECS service average memory utilization is high."
      comparison_operator = "GreaterThanOrEqualToThreshold"
      metric_name         = "MemoryUtilization"
      namespace           = "AWS/ECS"
      statistic           = "Average"
      threshold           = var.ecs_memory_threshold_percent
      treat_missing_data  = "missing"
      dimensions = {
        ClusterName = var.ecs_cluster_name
        ServiceName = var.ecs_service_name
      }
    }
    "rds-cpu-high" = {
      description         = "RDS average CPU utilization is high."
      comparison_operator = "GreaterThanOrEqualToThreshold"
      metric_name         = "CPUUtilization"
      namespace           = "AWS/RDS"
      statistic           = "Average"
      threshold           = var.rds_cpu_threshold_percent
      treat_missing_data  = "missing"
      dimensions = {
        DBInstanceIdentifier = var.rds_db_instance_identifier
      }
    }
    "rds-free-storage-low" = {
      description         = "RDS free storage is below the configured threshold."
      comparison_operator = "LessThanOrEqualToThreshold"
      metric_name         = "FreeStorageSpace"
      namespace           = "AWS/RDS"
      statistic           = "Minimum"
      threshold           = var.rds_free_storage_threshold_bytes
      treat_missing_data  = "missing"
      dimensions = {
        DBInstanceIdentifier = var.rds_db_instance_identifier
      }
    }
    "rds-free-memory-low" = {
      description         = "RDS freeable memory is below the configured threshold."
      comparison_operator = "LessThanOrEqualToThreshold"
      metric_name         = "FreeableMemory"
      namespace           = "AWS/RDS"
      statistic           = "Minimum"
      threshold           = var.rds_free_memory_threshold_bytes
      treat_missing_data  = "missing"
      dimensions = {
        DBInstanceIdentifier = var.rds_db_instance_identifier
      }
    }
    "rds-connections-high" = {
      description         = "RDS database connections exceeded the configured threshold."
      comparison_operator = "GreaterThanOrEqualToThreshold"
      metric_name         = "DatabaseConnections"
      namespace           = "AWS/RDS"
      statistic           = "Maximum"
      threshold           = var.rds_connections_threshold
      treat_missing_data  = "missing"
      dimensions = {
        DBInstanceIdentifier = var.rds_db_instance_identifier
      }
    }
    "rds-read-latency-high" = {
      description         = "RDS average read latency exceeded the configured threshold."
      comparison_operator = "GreaterThanOrEqualToThreshold"
      metric_name         = "ReadLatency"
      namespace           = "AWS/RDS"
      statistic           = "Average"
      threshold           = var.rds_read_latency_threshold_seconds
      treat_missing_data  = "missing"
      dimensions = {
        DBInstanceIdentifier = var.rds_db_instance_identifier
      }
    }
    "rds-write-latency-high" = {
      description         = "RDS average write latency exceeded the configured threshold."
      comparison_operator = "GreaterThanOrEqualToThreshold"
      metric_name         = "WriteLatency"
      namespace           = "AWS/RDS"
      statistic           = "Average"
      threshold           = var.rds_write_latency_threshold_seconds
      treat_missing_data  = "missing"
      dimensions = {
        DBInstanceIdentifier = var.rds_db_instance_identifier
      }
    }
  }
}

resource "aws_ecr_repository" "application" {
  name                 = local.ecr_repository_name
  image_tag_mutability = "IMMUTABLE"
  force_delete         = false

  encryption_configuration {
    encryption_type = var.ecr_kms_key_arn == null ? "AES256" : "KMS"
    kms_key         = var.ecr_kms_key_arn
  }

  image_scanning_configuration {
    scan_on_push = true
  }

  tags = merge(local.common_tags, {
    Name = local.ecr_repository_name
  })
}

resource "aws_ecr_lifecycle_policy" "application" {
  repository = aws_ecr_repository.application.name

  policy = jsonencode({
    rules = [
      {
        rulePriority = 1
        description  = "Expire untagged images after ${var.ecr_untagged_image_retention_days} days"
        selection = {
          tagStatus   = "untagged"
          countType   = "sinceImagePushed"
          countUnit   = "days"
          countNumber = var.ecr_untagged_image_retention_days
        }
        action = {
          type = "expire"
        }
      },
      {
        rulePriority = 2
        description  = "Retain the newest ${var.ecr_retained_image_count} images"
        selection = {
          tagStatus   = "any"
          countType   = "imageCountMoreThan"
          countNumber = var.ecr_retained_image_count
        }
        action = {
          type = "expire"
        }
      }
    ]
  })
}

resource "aws_wafv2_web_acl" "application" {
  name        = "${local.name_prefix}-web-acl"
  description = "Regional application protections for ${local.name_prefix}."
  scope       = "REGIONAL"

  default_action {
    allow {}
  }

  rule {
    name     = "RateLimitPerIp"
    priority = 0

    action {
      block {}
    }

    statement {
      rate_based_statement {
        aggregate_key_type    = "IP"
        evaluation_window_sec = var.waf_rate_evaluation_window_seconds
        limit                 = var.waf_rate_limit
      }
    }

    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "${local.name_prefix}-rate-limit"
      sampled_requests_enabled   = var.waf_sampled_requests_enabled
    }
  }

  rule {
    name     = "AWSManagedRulesCommonRuleSet"
    priority = 10

    override_action {
      dynamic "count" {
        for_each = var.waf_managed_rules_count_mode ? [1] : []
        content {}
      }

      dynamic "none" {
        for_each = var.waf_managed_rules_count_mode ? [] : [1]
        content {}
      }
    }

    statement {
      managed_rule_group_statement {
        name        = "AWSManagedRulesCommonRuleSet"
        vendor_name = "AWS"
      }
    }

    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "${local.name_prefix}-common-rules"
      sampled_requests_enabled   = var.waf_sampled_requests_enabled
    }
  }

  rule {
    name     = "AWSManagedRulesKnownBadInputsRuleSet"
    priority = 20

    override_action {
      dynamic "count" {
        for_each = var.waf_managed_rules_count_mode ? [1] : []
        content {}
      }

      dynamic "none" {
        for_each = var.waf_managed_rules_count_mode ? [] : [1]
        content {}
      }
    }

    statement {
      managed_rule_group_statement {
        name        = "AWSManagedRulesKnownBadInputsRuleSet"
        vendor_name = "AWS"
      }
    }

    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "${local.name_prefix}-known-bad-inputs"
      sampled_requests_enabled   = var.waf_sampled_requests_enabled
    }
  }

  visibility_config {
    cloudwatch_metrics_enabled = true
    metric_name                = "${local.name_prefix}-web-acl"
    sampled_requests_enabled   = var.waf_sampled_requests_enabled
  }

  tags = merge(local.common_tags, {
    Name = "${local.name_prefix}-web-acl"
  })
}

resource "aws_wafv2_web_acl_association" "application" {
  resource_arn = var.alb_arn
  web_acl_arn  = aws_wafv2_web_acl.application.arn
}

resource "aws_cloudwatch_log_group" "waf" {
  name              = "aws-waf-logs-${local.name_prefix}"
  retention_in_days = var.waf_log_retention_days
  kms_key_id        = var.cloudwatch_logs_kms_key_arn

  tags = merge(local.common_tags, {
    Name = "aws-waf-logs-${local.name_prefix}"
  })
}

data "aws_iam_policy_document" "waf_log_delivery" {
  statement {
    sid    = "AllowWafLogDelivery"
    effect = "Allow"

    principals {
      type        = "Service"
      identifiers = ["delivery.logs.amazonaws.com"]
    }

    actions = [
      "logs:CreateLogStream",
      "logs:PutLogEvents"
    ]

    resources = ["${aws_cloudwatch_log_group.waf.arn}:*"]

    condition {
      test     = "ArnLike"
      variable = "aws:SourceArn"
      values = [
        "arn:${data.aws_partition.current.partition}:logs:${data.aws_region.current.region}:${data.aws_caller_identity.current.account_id}:*"
      ]
    }

    condition {
      test     = "StringEquals"
      variable = "aws:SourceAccount"
      values   = [data.aws_caller_identity.current.account_id]
    }
  }
}

resource "aws_cloudwatch_log_resource_policy" "waf" {
  policy_name     = "${local.name_prefix}-waf-log-delivery"
  policy_document = data.aws_iam_policy_document.waf_log_delivery.json
}

resource "aws_wafv2_web_acl_logging_configuration" "application" {
  log_destination_configs = [aws_cloudwatch_log_group.waf.arn]
  resource_arn            = aws_wafv2_web_acl.application.arn

  dynamic "logging_filter" {
    for_each = var.waf_log_blocked_requests_only ? [1] : []

    content {
      default_behavior = "DROP"

      filter {
        behavior    = "KEEP"
        requirement = "MEETS_ANY"

        condition {
          action_condition {
            action = "BLOCK"
          }
        }
      }
    }
  }

  dynamic "redacted_fields" {
    for_each = var.waf_redacted_headers

    content {
      single_header {
        name = redacted_fields.value
      }
    }
  }

  depends_on = [aws_cloudwatch_log_resource_policy.waf]
}

resource "aws_sns_topic" "alarms" {
  name              = "${local.name_prefix}-alarms"
  kms_master_key_id = var.sns_kms_master_key_id

  tags = merge(local.common_tags, {
    Name = "${local.name_prefix}-alarms"
  })
}

data "aws_iam_policy_document" "alarm_topic" {
  statement {
    sid    = "AllowAccountAdministration"
    effect = "Allow"

    principals {
      type = "AWS"
      identifiers = [
        "arn:${data.aws_partition.current.partition}:iam::${data.aws_caller_identity.current.account_id}:root"
      ]
    }

    actions   = ["sns:*"]
    resources = [aws_sns_topic.alarms.arn]
  }

  statement {
    sid    = "AllowCloudWatchAlarmPublishing"
    effect = "Allow"

    principals {
      type        = "Service"
      identifiers = ["cloudwatch.amazonaws.com"]
    }

    actions   = ["sns:Publish"]
    resources = [aws_sns_topic.alarms.arn]

    condition {
      test     = "StringEquals"
      variable = "aws:SourceAccount"
      values   = [data.aws_caller_identity.current.account_id]
    }

    condition {
      test     = "ArnLike"
      variable = "aws:SourceArn"
      values = [
        "arn:${data.aws_partition.current.partition}:cloudwatch:${data.aws_region.current.region}:${data.aws_caller_identity.current.account_id}:alarm:${local.name_prefix}-*"
      ]
    }
  }

  statement {
    sid    = "AllowRdsEventPublishing"
    effect = "Allow"

    principals {
      type        = "Service"
      identifiers = ["events.rds.amazonaws.com"]
    }

    actions   = ["sns:Publish"]
    resources = [aws_sns_topic.alarms.arn]

    condition {
      test     = "StringEquals"
      variable = "aws:SourceAccount"
      values   = [data.aws_caller_identity.current.account_id]
    }

    condition {
      test     = "ArnLike"
      variable = "aws:SourceArn"
      values = [
        "arn:${data.aws_partition.current.partition}:rds:${data.aws_region.current.region}:${data.aws_caller_identity.current.account_id}:db:${var.rds_db_instance_identifier}"
      ]
    }
  }

  statement {
    sid    = "DenyInsecureTransport"
    effect = "Deny"

    principals {
      type        = "AWS"
      identifiers = ["*"]
    }

    actions   = ["sns:*"]
    resources = [aws_sns_topic.alarms.arn]

    condition {
      test     = "Bool"
      variable = "aws:SecureTransport"
      values   = ["false"]
    }
  }
}

resource "aws_sns_topic_policy" "alarms" {
  arn    = aws_sns_topic.alarms.arn
  policy = data.aws_iam_policy_document.alarm_topic.json
}

resource "aws_db_event_subscription" "application" {
  name      = "${local.name_prefix}-rds-events"
  sns_topic = aws_sns_topic.alarms.arn

  source_type = "db-instance"
  source_ids  = [var.rds_db_instance_identifier]
  event_categories = [
    "availability",
    "backup",
    "deletion",
    "failover",
    "failure",
    "low storage",
    "maintenance",
    "recovery",
    "restoration"
  ]
  enabled = true

  tags = merge(local.common_tags, {
    Name = "${local.name_prefix}-rds-events"
  })

  depends_on = [aws_sns_topic_policy.alarms]
}

resource "aws_cloudwatch_log_metric_filter" "application" {
  for_each = local.application_log_alerts

  name           = "${local.name_prefix}-${each.key}"
  pattern        = each.value.filter_pattern
  log_group_name = var.application_log_group_name

  metric_transformation {
    name          = each.value.metric_name
    namespace     = local.application_log_metric_namespace
    value         = "1"
    default_value = "0"
    unit          = "Count"
  }
}

resource "aws_cloudwatch_metric_alarm" "application_log" {
  for_each = local.application_log_alerts

  alarm_name          = "${local.name_prefix}-${each.key}"
  alarm_description   = each.value.description
  actions_enabled     = var.alarm_actions_enabled
  alarm_actions       = [aws_sns_topic.alarms.arn]
  ok_actions          = [aws_sns_topic.alarms.arn]
  comparison_operator = "GreaterThanOrEqualToThreshold"
  datapoints_to_alarm = var.application_log_alarm_datapoints_to_alarm
  evaluation_periods  = var.application_log_alarm_evaluation_periods
  metric_name         = each.value.metric_name
  namespace           = local.application_log_metric_namespace
  period              = var.application_log_alarm_period_seconds
  statistic           = "Sum"
  threshold           = each.value.threshold
  treat_missing_data  = "notBreaching"
  unit                = "Count"

  insufficient_data_actions = []

  tags = merge(local.common_tags, {
    Name = "${local.name_prefix}-${each.key}"
  })

  lifecycle {
    precondition {
      condition     = var.application_log_alarm_datapoints_to_alarm <= var.application_log_alarm_evaluation_periods
      error_message = "application_log_alarm_datapoints_to_alarm must not exceed application_log_alarm_evaluation_periods."
    }
  }

  depends_on = [
    aws_cloudwatch_log_metric_filter.application,
    aws_sns_topic_policy.alarms
  ]
}

resource "aws_cloudwatch_metric_alarm" "standard" {
  for_each = local.standard_alarms

  alarm_name          = "${local.name_prefix}-${each.key}"
  alarm_description   = each.value.description
  actions_enabled     = var.alarm_actions_enabled
  alarm_actions       = [aws_sns_topic.alarms.arn]
  ok_actions          = [aws_sns_topic.alarms.arn]
  comparison_operator = each.value.comparison_operator
  datapoints_to_alarm = var.alarm_datapoints_to_alarm
  dimensions          = each.value.dimensions
  evaluation_periods  = var.alarm_evaluation_periods
  metric_name         = each.value.metric_name
  namespace           = each.value.namespace
  period              = var.alarm_period_seconds
  statistic           = each.value.statistic
  threshold           = each.value.threshold
  treat_missing_data  = each.value.treat_missing_data

  insufficient_data_actions = []

  tags = merge(local.common_tags, {
    Name = "${local.name_prefix}-${each.key}"
  })

  lifecycle {
    precondition {
      condition     = var.alarm_datapoints_to_alarm <= var.alarm_evaluation_periods
      error_message = "alarm_datapoints_to_alarm must not exceed alarm_evaluation_periods."
    }
  }

  depends_on = [aws_sns_topic_policy.alarms]
}

resource "aws_cloudwatch_metric_alarm" "alb_p95_latency" {
  alarm_name          = "${local.name_prefix}-alb-p95-latency"
  alarm_description   = "ALB target response time p95 exceeded the configured threshold."
  actions_enabled     = var.alarm_actions_enabled
  alarm_actions       = [aws_sns_topic.alarms.arn]
  ok_actions          = [aws_sns_topic.alarms.arn]
  comparison_operator = "GreaterThanOrEqualToThreshold"
  datapoints_to_alarm = var.alarm_datapoints_to_alarm
  dimensions = {
    LoadBalancer = local.alb_arn_suffix
    TargetGroup  = local.target_group_suffix
  }
  evaluate_low_sample_count_percentiles = "ignore"
  evaluation_periods                    = var.alarm_evaluation_periods
  extended_statistic                    = "p95"
  metric_name                           = "TargetResponseTime"
  namespace                             = "AWS/ApplicationELB"
  period                                = var.alarm_period_seconds
  threshold                             = var.alb_p95_latency_threshold_seconds
  treat_missing_data                    = "notBreaching"

  insufficient_data_actions = []

  tags = merge(local.common_tags, {
    Name = "${local.name_prefix}-alb-p95-latency"
  })

  lifecycle {
    precondition {
      condition     = var.alarm_datapoints_to_alarm <= var.alarm_evaluation_periods
      error_message = "alarm_datapoints_to_alarm must not exceed alarm_evaluation_periods."
    }
  }

  depends_on = [aws_sns_topic_policy.alarms]
}
