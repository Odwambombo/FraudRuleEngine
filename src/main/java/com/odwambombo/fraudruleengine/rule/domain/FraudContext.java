package com.odwambombo.fraudruleengine.rule.domain;

/**
 * Historical facts loaded before rule evaluation.
 *
 * @param recentTransactionCount prior persisted transactions for the customer
 *                               within the configured velocity window
 */
public record FraudContext(int recentTransactionCount) {

    public FraudContext {
        if (recentTransactionCount < 0) {
            throw new IllegalArgumentException("recentTransactionCount must not be negative");
        }
    }
}
