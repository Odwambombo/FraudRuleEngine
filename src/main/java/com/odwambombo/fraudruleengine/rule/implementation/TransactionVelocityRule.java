package com.odwambombo.fraudruleengine.rule.implementation;

import com.odwambombo.fraudruleengine.rule.configuration.FraudProperties;
import com.odwambombo.fraudruleengine.rule.domain.FraudContext;
import com.odwambombo.fraudruleengine.rule.domain.FraudRule;
import com.odwambombo.fraudruleengine.rule.domain.FraudRuleResult;
import com.odwambombo.fraudruleengine.transaction.domain.TransactionEvent;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class TransactionVelocityRule implements FraudRule {

    public static final String RULE_CODE = "TRANSACTION_VELOCITY";
    private static final int ORDER = 50;

    private final FraudProperties fraudProperties;

    public TransactionVelocityRule(FraudProperties fraudProperties) {
        this.fraudProperties = fraudProperties;
    }

    @Override
    public FraudRuleResult evaluate(
            TransactionEvent transactionEvent,
            FraudContext fraudContext) {
        Objects.requireNonNull(transactionEvent, "transactionEvent must not be null");
        Objects.requireNonNull(fraudContext, "fraudContext must not be null");

        final FraudProperties.Velocity configuration = fraudProperties.getRules().getVelocity();
        final int transactionCountIncludingCurrent = Math.addExact(
                fraudContext.recentTransactionCount(),
                1
        );
        final boolean matched = transactionCountIncludingCurrent >= configuration.getMinimumTransactionCount();

        if (!matched) {
            return FraudRuleResult.notMatched(RULE_CODE);
        }

        return FraudRuleResult.matched(
                RULE_CODE,
                configuration.getScore(),
                transactionCountIncludingCurrent
                        + " transactions occurred within "
                        + configuration.getWindow().toMinutes()
                        + " minutes."
        );
    }

    @Override
    public String getRuleCode() {
        return RULE_CODE;
    }

    @Override
    public boolean isEnabled() {
        return fraudProperties.getRules().getVelocity().isEnabled();
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
