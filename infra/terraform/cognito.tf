locals {
  cognito_scope_descriptions = {
    "fraud.transactions.write" = "Submit transaction events for fraud assessment."
    "fraud.assessments.read"   = "Read fraud assessments and assessment summaries."
    "fraud.operations.read"    = "Read protected application operations endpoints."
    "fraud.docs.read"          = "Read protected API documentation endpoints."
  }

  cognito_full_scopes = {
    transaction_write = "${var.cognito_resource_server_identifier}/fraud.transactions.write"
    assessment_read   = "${var.cognito_resource_server_identifier}/fraud.assessments.read"
    operations_read   = "${var.cognito_resource_server_identifier}/fraud.operations.read"
    docs_read         = "${var.cognito_resource_server_identifier}/fraud.docs.read"
  }

  # Browser users need only the two interactive application scopes. Keep the
  # operations and documentation scopes available for separate trusted clients.
  cognito_spa_custom_scopes = [
    local.cognito_full_scopes.transaction_write,
    local.cognito_full_scopes.assessment_read,
  ]

  cognito_groups = {
    FRAUD_OPERATOR = "Can submit transaction events for fraud assessment."
    FRAUD_ANALYST  = "Can investigate fraud assessments."
    FRAUD_AUDITOR  = "Can read fraud assessments for audit purposes."
    FRAUD_ADMIN    = "Can administer and operate the fraud application."
  }
}

resource "aws_cognito_user_pool" "application" {
  count = var.cognito_enabled ? 1 : 0

  name                = "${local.name_prefix}-users"
  deletion_protection = var.cognito_deletion_protection ? "ACTIVE" : "INACTIVE"
  user_pool_tier      = "ESSENTIALS"

  username_attributes      = ["email"]
  auto_verified_attributes = ["email"]

  username_configuration {
    case_sensitive = false
  }

  user_attribute_update_settings {
    attributes_require_verification_before_update = ["email"]
  }

  account_recovery_setting {
    recovery_mechanism {
      name     = "verified_email"
      priority = 1
    }
  }

  admin_create_user_config {
    allow_admin_create_user_only = !var.cognito_self_signup_enabled

    invite_message_template {
      email_subject = "Your ${var.project_name} account"
      email_message = "Your username is {username} and your temporary password is {####}."
      sms_message   = "Your username is {username} and your temporary password is {####}."
    }
  }

  email_configuration {
    email_sending_account = "COGNITO_DEFAULT"
  }

  verification_message_template {
    default_email_option = "CONFIRM_WITH_CODE"
    email_subject        = "Verify your ${var.project_name} account"
    email_message        = "Your verification code is {####}."
  }

  password_policy {
    minimum_length                   = 14
    require_lowercase                = true
    require_numbers                  = true
    require_symbols                  = true
    require_uppercase                = true
    temporary_password_validity_days = 7
  }

  mfa_configuration = "OPTIONAL"

  software_token_mfa_configuration {
    enabled = true
  }

  tags = merge(local.common_tags, {
    Name = "${local.name_prefix}-users"
  })

  lifecycle {
    precondition {
      condition     = var.cognito_domain_prefix != null
      error_message = "cognito_domain_prefix must be set when cognito_enabled is true."
    }

    precondition {
      condition     = length(var.cognito_callback_urls) > 0
      error_message = "At least one cognito_callback_urls value is required when cognito_enabled is true."
    }

    precondition {
      condition     = length(var.cognito_logout_urls) > 0
      error_message = "At least one cognito_logout_urls value is required when cognito_enabled is true."
    }
  }
}

resource "aws_cognito_resource_server" "application" {
  count = var.cognito_enabled ? 1 : 0

  identifier   = var.cognito_resource_server_identifier
  name         = "${local.name_prefix}-api"
  user_pool_id = aws_cognito_user_pool.application[0].id

  dynamic "scope" {
    for_each = local.cognito_scope_descriptions

    content {
      scope_name        = scope.key
      scope_description = scope.value
    }
  }
}

resource "aws_cognito_user_pool_client" "spa" {
  count = var.cognito_enabled ? 1 : 0

  name         = "${local.name_prefix}-spa"
  user_pool_id = aws_cognito_user_pool.application[0].id

  generate_secret                      = false
  allowed_oauth_flows_user_pool_client = true
  allowed_oauth_flows                  = ["code"]
  allowed_oauth_scopes = concat(
    ["openid", "profile", "email"],
    local.cognito_spa_custom_scopes,
  )
  callback_urls                 = var.cognito_callback_urls
  logout_urls                   = var.cognito_logout_urls
  default_redirect_uri          = try(var.cognito_callback_urls[0], null)
  supported_identity_providers  = ["COGNITO"]
  enable_token_revocation       = true
  prevent_user_existence_errors = "ENABLED"

  depends_on = [aws_cognito_resource_server.application]
}

resource "aws_cognito_user_pool_domain" "managed_login" {
  count = var.cognito_enabled ? 1 : 0

  domain                = coalesce(var.cognito_domain_prefix, "disabled-prefix")
  managed_login_version = 2
  user_pool_id          = aws_cognito_user_pool.application[0].id

  depends_on = [aws_cognito_user_pool_client.spa]
}

resource "aws_cognito_managed_login_branding" "spa" {
  count = var.cognito_enabled ? 1 : 0

  client_id                   = aws_cognito_user_pool_client.spa[0].id
  user_pool_id                = aws_cognito_user_pool.application[0].id
  use_cognito_provided_values = true

  depends_on = [aws_cognito_user_pool_domain.managed_login]
}

resource "aws_cognito_user_group" "application_roles" {
  for_each = var.cognito_enabled ? local.cognito_groups : {}

  name         = each.key
  description  = each.value
  user_pool_id = aws_cognito_user_pool.application[0].id
}
