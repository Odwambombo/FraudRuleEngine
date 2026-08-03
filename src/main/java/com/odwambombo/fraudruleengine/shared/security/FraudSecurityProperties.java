package com.odwambombo.fraudruleengine.shared.security;

import java.util.Arrays;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

@Component
@ConfigurationProperties(prefix = "fraud.security")
@Validated
public class FraudSecurityProperties {

    private boolean enabled;
    private Provider provider = Provider.LOCAL;
    private String issuerUri;
    private String audience;

    @Valid
    private final Scopes scopes = new Scopes();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Provider getProvider() {
        return provider;
    }

    public void setProvider(Provider provider) {
        this.provider = provider;
    }

    public String getIssuerUri() {
        return issuerUri;
    }

    public void setIssuerUri(String issuerUri) {
        this.issuerUri = issuerUri;
    }

    public String getAudience() {
        return audience;
    }

    public void setAudience(String audience) {
        this.audience = audience;
    }

    public Scopes getScopes() {
        return scopes;
    }

    public enum Provider {
        LOCAL,
        COGNITO
    }

    @AssertTrue(message = "issuer-uri and audience must be configured when fraud security is enabled")
    public boolean isIdentityProviderConfigurationValid() {
        return !enabled || StringUtils.hasText(issuerUri) && StringUtils.hasText(audience);
    }

    @AssertTrue(message = "an OAuth scope must be configured for every fraud permission when using Cognito")
    public boolean areCognitoPermissionScopesConfigured() {
        return !requiresCognitoScopes() || Arrays.stream(FraudPermission.values())
                .map(this::scopeFor)
                .allMatch(StringUtils::hasText);
    }

    @AssertTrue(message = "each fraud permission must use a unique OAuth scope when using Cognito")
    public boolean arePermissionScopesUnique() {
        return !requiresCognitoScopes() || Arrays.stream(FraudPermission.values())
                .map(this::scopeFor)
                .distinct()
                .count() == FraudPermission.values().length;
    }

    private boolean requiresCognitoScopes() {
        return enabled && provider == Provider.COGNITO;
    }

    public String scopeFor(FraudPermission permission) {
        final String configuredScope = switch (permission) {
            case TRANSACTION_WRITE -> scopes.getTransactionWrite();
            case ASSESSMENT_READ -> scopes.getAssessmentRead();
            case OPERATIONS_READ -> scopes.getOperationsRead();
            case DOCS_READ -> scopes.getDocsRead();
        };
        return configuredScope == null ? "" : configuredScope.trim();
    }

    public static class Scopes {

        private String transactionWrite;

        private String assessmentRead;

        private String operationsRead;

        private String docsRead;

        public String getTransactionWrite() {
            return transactionWrite;
        }

        public void setTransactionWrite(String transactionWrite) {
            this.transactionWrite = transactionWrite;
        }

        public String getAssessmentRead() {
            return assessmentRead;
        }

        public void setAssessmentRead(String assessmentRead) {
            this.assessmentRead = assessmentRead;
        }

        public String getOperationsRead() {
            return operationsRead;
        }

        public void setOperationsRead(String operationsRead) {
            this.operationsRead = operationsRead;
        }

        public String getDocsRead() {
            return docsRead;
        }

        public void setDocsRead(String docsRead) {
            this.docsRead = docsRead;
        }
    }
}
