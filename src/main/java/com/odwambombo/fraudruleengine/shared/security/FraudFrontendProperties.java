package com.odwambombo.fraudruleengine.shared.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "fraud.frontend.cognito")
class FraudFrontendProperties {

    private String domain;
    private String clientId;
    private boolean signupEnabled;

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public boolean isSignupEnabled() {
        return signupEnabled;
    }

    public void setSignupEnabled(boolean signupEnabled) {
        this.signupEnabled = signupEnabled;
    }
}
