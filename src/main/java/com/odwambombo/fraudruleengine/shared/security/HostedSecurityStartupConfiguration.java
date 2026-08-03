package com.odwambombo.fraudruleengine.shared.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.util.Assert;

/**
 * Prevents a hosted web process from starting with the compatibility security
 * chain that permits every request.
 *
 * <p>The migration profile deliberately disables security, but it also sets
 * {@code spring.main.web-application-type=none}. Conditioning this invariant
 * on a servlet application therefore keeps one-shot hosted migration tasks
 * functional without allowing a staging or production API to fail open.
 */
@Configuration(proxyBeanMethods = false)
@Profile("staging | production")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class HostedSecurityStartupConfiguration {

    static final String DISABLED_HOSTED_SECURITY_MESSAGE =
            "fraud.security.enabled must be true when staging or production "
                    + "runs as a servlet web application";

    HostedSecurityStartupConfiguration(FraudSecurityProperties securityProperties) {
        Assert.state(
                securityProperties.isEnabled(),
                DISABLED_HOSTED_SECURITY_MESSAGE
        );
    }
}
