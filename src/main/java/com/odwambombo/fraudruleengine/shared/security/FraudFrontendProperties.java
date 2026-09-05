package com.odwambombo.fraudruleengine.shared.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "fraud.frontend.cognito")
class FraudFrontendProperties {

    private String domain;
    private String clientId;
    private boolean signupEnabled;

}
