package com.odwambombo.fraudruleengine.rule.domain;

import com.odwambombo.fraudruleengine.transaction.domain.TransactionEvent;

public interface FraudRule {

    FraudRuleResult evaluate(TransactionEvent transactionEvent, FraudContext fraudContext);

    String getRuleCode();

    default boolean isEnabled() {
        return true;
    }

    default int getOrder() {
        return 0;
    }
}
