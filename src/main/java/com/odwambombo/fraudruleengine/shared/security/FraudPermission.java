package com.odwambombo.fraudruleengine.shared.security;

public enum FraudPermission {

    TRANSACTION_WRITE,
    ASSESSMENT_READ,
    OPERATIONS_READ,
    DOCS_READ;

    private static final String AUTHORITY_PREFIX = "PERMISSION_";

    public String authority() {
        return AUTHORITY_PREFIX + name();
    }
}
