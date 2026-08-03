package com.odwambombo.fraudruleengine.shared.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * Enables {@code @PreAuthorize} checks only when API authentication is enabled.
 * Explicit security-disabled modes, such as the migration profile or selected
 * test compatibility contexts, therefore keep their permit-all behaviour.
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
@ConditionalOnProperty(prefix = "fraud.security", name = "enabled", havingValue = "true")
class FraudMethodSecurityConfiguration {
}
