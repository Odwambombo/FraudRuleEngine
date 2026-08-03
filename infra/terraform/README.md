# Fraud Rule Engine AWS production controls

This Terraform module adds environment-specific security, image-retention,
logging, and alerting controls around an existing AWS deployment. It does not
create the VPC, subnets, security groups, ALB listener/TLS/DNS, ECS task
definition or service, deployment circuit breaker or autoscaling, RDS instance
or backups, Secrets Manager database secret, task/execution IAM roles,
application log group, OTLP collector/backend, SNS recipients, or deployment
pipeline. It cannot deploy an empty AWS account.

It creates:

- an encrypted ECR repository with immutable tags, scan-on-push, and lifecycle
  retention;
- a REGIONAL WAFv2 web ACL associated with the supplied ALB;
- AWS managed Common and Known Bad Inputs rule groups;
- a configurable per-source-IP rate-based blocking rule;
- CloudWatch WAF request logs with configurable retention and sensitive-header
  redaction;
- a least-privilege CloudWatch Logs resource policy for WAF delivery;
- an optional per-environment Amazon Cognito user pool, public SPA client,
  managed-login domain, API scopes, and application groups;
- an SNS alarm topic and topic policy;
- CloudWatch alarms for ALB and target 5xx responses, p95 target response time,
  unhealthy targets, ECS CPU and memory, and RDS CPU, free storage, freeable
  memory, database connections, and read/write latency;
- an RDS event subscription for DB-instance availability, backup, failover,
  failure, storage, maintenance, recovery, restoration, and deletion events;
- optional CloudWatch Logs metric filters and alarms for unhandled application
  exceptions, fraud-processing failures, and authentication failures.

ECR scan-on-push starts scanning according to the account's registry scanning
configuration. This module does not wait for findings, enforce a severity gate,
or create an SBOM or image signature; the release pipeline must own those
controls.

## Prerequisites

- Terraform 1.6 or newer.
- AWS provider 6.25 or newer within the 6.x release line.
- An existing Application Load Balancer and target group.
- The ALB must not already be managed by another WAF web ACL association.
- An existing ECS cluster and service.
- An existing RDS PostgreSQL DB instance.
- For application log alerts, an existing CloudWatch log group receiving the
  ECS application's standard output. The input is optional for compatibility.
- AWS credentials for the target environment account and an AWS Region selected
  through the root provider or `AWS_REGION`.

The supplied ALB, target group, ECS cluster/service, RDS instance, and optional
application log group must be in the same AWS account and Region as the module's
AWS provider. The WAF association protects traffic reaching that ALB only; it
does not protect Cognito Managed Login, which needs separate abuse monitoring
or controls when required.

Use separate state and preferably separate AWS accounts for staging and
production. The module deliberately contains no backend or provider block so it
can be called from an environment root that owns those concerns.

Validate the module in CI before planning an environment:

```shell
terraform -chdir=infra/terraform fmt -check -recursive
terraform -chdir=infra/terraform init -backend=false
terraform -chdir=infra/terraform validate
```

## Staging example

This example belongs in a staging Terraform root and state:

```hcl
terraform {
  backend "s3" {}
}

provider "aws" {
  region = "af-south-1"
}

module "fraud_rule_engine_controls" {
  source = "../../infra/terraform"

  project_name               = "fraud-rule-engine"
  environment                = "staging"
  alb_arn                    = module.staging_service.alb_arn
  target_group_arn           = module.staging_service.target_group_arn
  ecs_cluster_name           = module.staging_service.ecs_cluster_name
  ecs_service_name           = module.staging_service.ecs_service_name
  rds_db_instance_identifier = module.staging_database.db_instance_identifier
  application_log_group_name = module.staging_service.application_log_group_name

  ecr_retained_image_count           = 20
  ecr_untagged_image_retention_days  = 7
  waf_rate_limit                     = 1000
  waf_rate_evaluation_window_seconds = 300
  waf_log_retention_days             = 30

  cognito_enabled             = true
  cognito_domain_prefix       = "exampleco-fraud-stage-za" # Must be globally unique.
  cognito_callback_urls       = ["https://fraud-staging.example.com/"]
  cognito_logout_urls         = ["https://fraud-staging.example.com/"]
  cognito_self_signup_enabled = false
  cognito_deletion_protection = true

  # Start in count mode, inspect WAF logs, then change to false to enforce the
  # AWS managed rule actions before promoting the release to production.
  waf_managed_rules_count_mode = true

  alb_5xx_threshold                      = 5
  target_5xx_threshold                   = 5
  alb_p95_latency_threshold_seconds      = 1
  ecs_cpu_threshold_percent              = 75
  ecs_memory_threshold_percent           = 80
  rds_cpu_threshold_percent              = 80
  rds_free_storage_threshold_bytes       = 5368709120
  rds_free_memory_threshold_bytes        = 536870912
  rds_connections_threshold              = 80
  rds_read_latency_threshold_seconds     = 0.1
  rds_write_latency_threshold_seconds    = 0.1

  application_exception_threshold      = 1
  fraud_processing_failure_threshold   = 1
  authentication_failure_threshold     = 10

  tags = {
    Owner      = "fraud-platform"
    CostCentre = "risk"
  }
}
```

Use the staging state backend and credentials when planning:

```shell
export AWS_PROFILE=fraud-staging
export AWS_REGION=af-south-1

terraform init \
  -backend-config="bucket=company-terraform-state-staging" \
  -backend-config="key=fraud-rule-engine/staging/production-controls.tfstate" \
  -backend-config="region=af-south-1"

terraform plan -out=staging.tfplan
terraform apply staging.tfplan
```

After representative API tests, review sampled requests and WAF logs. Change
`waf_managed_rules_count_mode` to `false`, apply again, and repeat the tests.

## Production example

Production should use a separate root, state backend, and AWS account. Copy or
replicate the already-tested OCI image into the production ECR repository,
verify that the destination manifest digest matches staging, and deploy by
digest; do not rebuild during promotion. Build for the ECS task definition's
declared CPU architecture, or publish a multi-architecture manifest that
includes it.

```hcl
provider "aws" {
  region = "af-south-1"
}

module "fraud_rule_engine_controls" {
  source = "../../infra/terraform"

  project_name               = "fraud-rule-engine"
  environment                = "production"
  alb_arn                    = module.production_service.alb_arn
  target_group_arn           = module.production_service.target_group_arn
  ecs_cluster_name           = module.production_service.ecs_cluster_name
  ecs_service_name           = module.production_service.ecs_service_name
  rds_db_instance_identifier = module.production_database.db_instance_identifier
  application_log_group_name = module.production_service.application_log_group_name

  ecr_retained_image_count          = 100
  ecr_untagged_image_retention_days = 14
  ecr_kms_key_arn                   = aws_kms_key.ecr.arn

  waf_managed_rules_count_mode       = false
  waf_rate_limit                     = 2000
  waf_rate_evaluation_window_seconds = 300
  waf_log_retention_days             = 90
  waf_sampled_requests_enabled       = false
  cloudwatch_logs_kms_key_arn        = aws_kms_key.logs.arn

  cognito_enabled             = true
  cognito_domain_prefix       = "exampleco-fraud-prod-za" # Must be globally unique.
  cognito_callback_urls       = ["https://fraud.example.com/"]
  cognito_logout_urls         = ["https://fraud.example.com/"]
  cognito_self_signup_enabled = false
  cognito_deletion_protection = true

  alarm_period_seconds      = 300
  alarm_evaluation_periods  = 2
  alarm_datapoints_to_alarm = 2

  # Tune these against load-test results, RDS instance capacity, and the
  # service's normal traffic profile before production use.
  alb_5xx_threshold                    = 10
  target_5xx_threshold                 = 10
  alb_p95_latency_threshold_seconds    = 1
  unhealthy_target_threshold           = 1
  ecs_cpu_threshold_percent            = 70
  ecs_memory_threshold_percent         = 75
  rds_cpu_threshold_percent            = 75
  rds_free_storage_threshold_bytes     = 10737418240
  rds_free_memory_threshold_bytes      = 1073741824
  rds_connections_threshold            = 150
  rds_read_latency_threshold_seconds   = 0.1
  rds_write_latency_threshold_seconds  = 0.1

  application_log_alarm_period_seconds = 300
  application_exception_threshold      = 1
  fraud_processing_failure_threshold   = 1
  authentication_failure_threshold     = 5

  tags = {
    Owner       = "fraud-platform"
    CostCentre  = "risk"
    DataClass   = "confidential"
    Criticality = "high"
  }
}
```

Do not copy the example thresholds blindly. A connection alarm in particular
must stay below the selected PostgreSQL instance's effective connection limit,
read and write latency thresholds must reflect the database's observed baseline,
and ECR retention must preserve every image digest needed for the rollback
window. The latency thresholds are seconds, so `0.1` means 100 milliseconds.

## Cognito SPA authentication

Cognito is disabled by default so existing module callers are unchanged. Each
environment root that enables it must supply its own globally unique domain
prefix and exact callback and logout URLs. Callback URLs must match the URL the
browser loads (including its path), use HTTPS outside localhost, and must not
contain URL fragments. Do not register staging or localhost URLs on the
production client.

This directory is a combined production-controls module, not a standalone
Cognito module. Its existing ALB, ECS, and RDS inputs remain required and it
also creates WAF, ECR, and alarm resources, so do not pass fake infrastructure
identifiers merely to create a user pool. Apply it after the environment's
hosting resources exist. If authentication must be tested before hosting,
either create an equivalent temporary user pool in the AWS console with a
`http://localhost:<port>/` callback, or extract these Cognito resources into a
dedicated identity module and root state. A separate module is preferable when
identity must have an independent lifecycle; import any manually created
resources before Terraform takes ownership rather than creating duplicates.

The generated browser client is *public* in the OAuth sense: it has no client
secret. This does not require the console itself to be publicly reachable. The
client supports only the OAuth 2.0 authorization-code flow, and the frontend
uses PKCE with the `S256` challenge method. Token revocation is enabled. The
client permits `openid`, `profile`, `email`, transaction-write, and
assessment-read. Although the resource server defines operations-read and
docs-read too, those higher-trust scopes are deliberately excluded from the
browser client. Provision a separate trusted operator or machine client and
its token-acquisition flow if those scopes are needed; this module does not
create that client. Do not add the scopes to the SPA merely for convenience.

The user pool uses the Cognito Essentials tier, enables software-token TOTP,
and currently configures MFA as optional. A user who has not enrolled TOTP can
therefore sign in with a password only. Require MFA for production internal
fraud operations, or federate Cognito to a corporate identity provider that
enforces it. This module does not create SAML/OIDC federation.

Pass these outputs into the ECS task definition as application environment
variables (or equivalent secret/config references). These are explicit
Cognito-to-application mappings for staging and production; local JWTs do not
use them:

```text
FRAUD_SECURITY_ENABLED=true
FRAUD_OAUTH2_ISSUER_URI=<cognito_issuer_uri>
FRAUD_OAUTH2_AUDIENCE=<cognito_spa_client_id>
FRAUD_COGNITO_DOMAIN=<cognito_domain>
FRAUD_COGNITO_CLIENT_ID=<cognito_spa_client_id>
FRAUD_COGNITO_SIGNUP_ENABLED=false
FRAUD_SCOPE_TRANSACTION_WRITE=<cognito_full_scopes.transaction_write>
FRAUD_SCOPE_ASSESSMENT_READ=<cognito_full_scopes.assessment_read>
FRAUD_SCOPE_OPERATIONS_READ=<cognito_full_scopes.operations_read>
FRAUD_SCOPE_DOCS_READ=<cognito_full_scopes.docs_read>
```

The Java domain and controller annotations refer only to `FraudPermission` and
`FraudRole` enum values. The adapter maps the Cognito strings above to those
internal values at the hosted identity-provider boundary. These issuer,
domain, client ID, and scope values are identifiers rather than credentials;
the SPA intentionally has no secret. Keep actual credentials such as database
passwords in Secrets Manager. Inspect the generated values with
`terraform output -raw cognito_issuer_uri`,
`terraform output -raw cognito_spa_client_id`, and
`terraform output -json cognito_full_scopes`.

The audience is the SPA client ID because this application validates Cognito
access tokens against that client. Keep `FRAUD_COGNITO_SIGNUP_ENABLED` equal to
`cognito_self_signup_enabled`; the former controls what the UI displays while
the latter controls whether Cognito accepts `SignUp` requests.

Self-signup is off by default. Create an invited user and assign one or more
application groups with the AWS CLI:

```shell
aws cognito-idp admin-create-user \
  --user-pool-id "$USER_POOL_ID" \
  --username analyst@example.com \
  --user-attributes Name=email,Value=analyst@example.com \
  --desired-delivery-mediums EMAIL

aws cognito-idp admin-add-user-to-group \
  --user-pool-id "$USER_POOL_ID" \
  --username analyst@example.com \
  --group-name FRAUD_ANALYST
```

An administrator-created user's email is not necessarily verified. After
independently verifying ownership, an administrator can mark it verified with
`admin-update-user-attributes`; do not set `email_verified=true` based only on
an unverified request. Verified email is the configured account-recovery
channel.

```shell
aws cognito-idp admin-update-user-attributes \
  --user-pool-id "$USER_POOL_ID" \
  --username analyst@example.com \
  --user-attributes Name=email_verified,Value=true
```

The groups are application roles and intentionally have no IAM role attached:

- `FRAUD_OPERATOR` maps to `FraudRole.OPERATOR` and can use the transaction submission flow.
- `FRAUD_ANALYST` and `FRAUD_AUDITOR` map to the corresponding enum roles and can read fraud assessments.
- `FRAUD_ADMIN` maps to `FraudRole.ADMIN` and can use both application flows.

Membership appears in the `cognito:groups` token claim. Group membership does
not itself issue an OAuth scope; the backend requires both the relevant scope
and group for transaction and assessment endpoints. Newly self-registered
users have no group until an administrator assigns one. Existing tokens must
be refreshed or the user must sign in again after a group change.

The pool uses Cognito's default email sender. Review its quotas and configure
Amazon SES before using this for production-volume email. Managed-login domain
creation can take about a minute to become available. Deletion protection is
on in the examples; an intentional destroy therefore requires setting
`cognito_deletion_protection = false`, applying that change, and only then
destroying the pool.

## Application log alerts

Set `application_log_group_name` to the existing CloudWatch log group used by
the ECS task's `awslogs` driver (or another log router that preserves the
application's standard-output messages). When the input is null, no application
log metric filters or log-derived alarms are created, preserving compatibility
for environments that have not connected ECS logs to CloudWatch.

The filters count these exact stable marker-and-message phrases:

| Alarm key | Log phrase | Default threshold |
| --- | --- | ---: |
| `application-exceptions` | `APPLICATION_EXCEPTION Unhandled application exception` | 1 |
| `fraud-processing-failures` | `FRAUD_PROCESSING_FAILURE Fraud transaction processing failed` | 1 |
| `authentication-failures` | `AUTHENTICATION_FAILURE Authentication failed` | 5 |

The defaults evaluate one five-minute period and treat absent log events as
non-breaching. Tune the period with `application_log_alarm_period_seconds` and
the three event-count thresholds for each environment. You can require multiple
breaching periods with `application_log_alarm_evaluation_periods` and
`application_log_alarm_datapoints_to_alarm`.

Authentication-failure signals include protected requests with no bearer token
as well as malformed, invalid, or expired tokens, plus failed local-development
login. Cognito Managed Login password or MFA failures occur before the request
reaches ECS and therefore require Cognito-specific monitoring if they must also
page operators.

Test each filter pattern without writing a log event:

```shell
aws logs test-metric-filter \
  --filter-pattern '"APPLICATION_EXCEPTION Unhandled application exception"' \
  --log-event-messages \
  'APPLICATION_EXCEPTION Unhandled application exception'
```

After applying, confirm the filters and alarms in staging:

```shell
aws logs describe-metric-filters \
  --log-group-name "$APPLICATION_LOG_GROUP"

aws cloudwatch describe-alarms \
  --alarm-name-prefix "fraud-rule-engine-staging-"
```

An alarm can be put temporarily into `ALARM` in staging to verify the complete
CloudWatch-to-SNS contact path. CloudWatch will replace that test state at its
next evaluation:

```shell
aws cloudwatch set-alarm-state \
  --alarm-name "fraud-rule-engine-staging-application-exceptions" \
  --state-value ALARM \
  --state-reason "Staging notification-path test"
```

## Database availability, latency, and backup alerts

`ReadLatency` and `WriteLatency` alarms use the AWS/RDS average latency metrics.
Their default threshold is `0.1` seconds. They use the module's shared alarm
period and evaluation settings and publish both alarm and recovery state changes
to the SNS topic.

The RDS DB-instance event subscription also publishes native events directly to
that topic for these verified categories: `availability`, `backup`, `deletion`,
`failover`, `failure`, `low storage`, `maintenance`, `recovery`, and
`restoration`. The topic policy restricts RDS publication to the configured DB
instance ARN and AWS account. Native RDS notifications are events, not
CloudWatch alarms, so they do not have an `OK` transition and can take several
minutes to arrive. The `backup` category includes routine backup start and
completion messages as well as backup-related problems; use SNS subscriber
filtering or an incident-management routing rule if routine events should not
page the on-call engineer.

Verify that AWS accepted the event subscription after applying:

```shell
aws rds describe-event-subscriptions \
  --subscription-name "fraud-rule-engine-production-rds-events" \
  --query 'EventSubscriptionsList[0].{Status:Status,Categories:EventCategoriesList}'
```

The expected status is `active`. A `no-permission` status indicates that the SNS
topic or its KMS key policy does not permit RDS event delivery.

## Subscribe operators to alarms

The module creates the SNS topic but intentionally does not choose its
recipients. Add subscriptions in the environment root, for example:

```hcl
resource "aws_sns_topic_subscription" "operations_email" {
  topic_arn = module.fraud_rule_engine_controls.alarm_sns_topic_arn
  protocol  = "email"
  endpoint  = "fraud-operations@example.com"
}
```

Email subscriptions do not receive notifications until the recipient confirms
the AWS subscription message. Production teams can instead subscribe an HTTPS
incident-management endpoint, Lambda function, or SQS queue with the required
resource policy. The same topic carries CloudWatch alarm state changes and the
native RDS events described above.

## Encryption notes

- With no `ecr_kms_key_arn`, ECR still encrypts repository data using AES-256.
- Supplying customer-managed keys makes their key policies part of the
  deployment contract. The ECR, CloudWatch Logs delivery, CloudWatch-to-SNS,
  and RDS-to-SNS service principals must be permitted to use their respective
  keys.
- `authorization` and `cookie` headers are redacted from WAF logs by default.
  Add any application-specific secret-bearing headers to
  `waf_redacted_headers`.
- WAF log redaction does not redact AWS WAF sampled requests. The production
  example disables sampled requests; if they are enabled in another
  environment, apply the organization's data-access and retention controls.

## WAF behavior

The rate rule blocks before the managed rule groups are evaluated. Its limit is
the number of requests per source IP during
`waf_rate_evaluation_window_seconds`, not a requests-per-second value.

AWS managed rule groups can reject legitimate API requests. Roll them out in
staging with `waf_managed_rules_count_mode = true`, inspect logs, add narrowly
scoped exclusions when justified, and only then enforce them. The rate rule
continues to block while the managed groups are in count mode.

Set `waf_log_blocked_requests_only = true` when full request logging is too
costly and only blocked-request evidence is required.

The module creates a dedicated account-level CloudWatch Logs resource policy
for each environment. Check the CloudWatch Logs resource-policy quota before
creating many environment instances in one AWS account.

## Useful outputs

- `ecr_repository_url`
- `waf_web_acl_arn`
- `waf_log_group_name`
- `alarm_sns_topic_arn`
- `cloudwatch_alarm_arns`
- `application_log_metric_filter_names`
- `rds_event_subscription_arn`
- `rds_event_subscription_name`
- `cognito_user_pool_id`
- `cognito_issuer_uri`
- `cognito_domain`
- `cognito_spa_client_id`
- `cognito_resource_server_identifier`
- `cognito_full_scopes`
- `cognito_spa_allowed_scopes`
- `cognito_user_groups`

## References

- [Terraform AWS ECR repository](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/ecr_repository)
- [Amazon ECR image scanning](https://docs.aws.amazon.com/AmazonECR/latest/userguide/image-scanning.html)
- [Amazon ECR tag immutability](https://docs.aws.amazon.com/AmazonECR/latest/userguide/image-tag-mutability.html)
- [Terraform AWS WAFv2 web ACL](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/wafv2_web_acl)
- [Terraform AWS WAF logging](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/wafv2_web_acl_logging_configuration)
- [AWS managed rule groups](https://docs.aws.amazon.com/waf/latest/developerguide/aws-managed-rule-groups-list.html)
- [AWS WAF logging](https://docs.aws.amazon.com/waf/latest/developerguide/logging-cw-logs.html)
- [AWS WAF log redaction and request sampling](https://docs.aws.amazon.com/waf/latest/developerguide/logging-management.html)
- [Application Load Balancer metrics](https://docs.aws.amazon.com/elasticloadbalancing/latest/application/load-balancer-cloudwatch-metrics.html)
- [Amazon ECS metrics](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/available-metrics.html)
- [Amazon RDS metrics](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/rds-metrics.html)
- [Amazon RDS event categories and messages](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/USER_Events.Messages.html)
- [Granting RDS permission to publish to SNS](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/USER_Events.GrantingPermissions.html)
- [Terraform CloudWatch Logs metric filter](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/cloudwatch_log_metric_filter)
- [Terraform RDS event subscription](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/db_event_subscription)
- [Terraform Cognito user pool](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/cognito_user_pool)
- [Terraform Cognito app client](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/cognito_user_pool_client)
- [Terraform Cognito resource server](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/cognito_resource_server)
- [Terraform Cognito managed-login branding](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/cognito_managed_login_branding)
- [Cognito authorization code flow with PKCE](https://docs.aws.amazon.com/cognito/latest/developerguide/using-pkce-in-authorization-code.html)
- [Cognito custom scopes](https://docs.aws.amazon.com/cognito/latest/developerguide/cognito-user-pools-define-resource-servers.html)
- [Cognito managed login](https://docs.aws.amazon.com/cognito/latest/developerguide/cognito-user-pools-managed-login.html)
