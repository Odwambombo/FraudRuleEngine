package com.odwambombo.fraudruleengine.rule.implementation;

import com.odwambombo.fraudruleengine.rule.configuration.FraudProperties;
import com.odwambombo.fraudruleengine.rule.domain.FraudContext;
import com.odwambombo.fraudruleengine.rule.domain.FraudRule;
import com.odwambombo.fraudruleengine.rule.domain.FraudRuleResult;
import com.odwambombo.fraudruleengine.transaction.domain.TransactionEvent;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.util.Objects;

@Component
public class UnusualTransactionTimeRule implements FraudRule {

    public static final String RULE_CODE = "UNUSUAL_TRANSACTION_TIME";
    private static final int ORDER = 20;

    private final FraudProperties fraudProperties;

    public UnusualTransactionTimeRule(FraudProperties fraudProperties) {
        this.fraudProperties = fraudProperties;
    }

    @Override
    public FraudRuleResult evaluate(
            TransactionEvent transactionEvent,
            FraudContext fraudContext) {
        Objects.requireNonNull(transactionEvent, "transactionEvent must not be null");

        final FraudProperties.UnusualTime configuration = fraudProperties
                .getRules()
                .getUnusualTime();
        final LocalTime transactionTime = transactionEvent.transactionTime() == null
                ? null
                : transactionEvent.transactionTime().toLocalTime();
        final boolean matched = transactionTime != null
                && isTimeWithinHalfOpenWindow(
                        transactionTime,
                        configuration.getStartInclusive(),
                        configuration.getEndExclusive()
                );

        if (!matched) {
            return FraudRuleResult.notMatched(RULE_CODE);
        }

        return FraudRuleResult.matched(
                RULE_CODE,
                configuration.getScore(),
                "Transaction occurred between "
                        + configuration.getStartInclusive()
                        + " (inclusive) and "
                        + configuration.getEndExclusive()
                        + " (exclusive)."
        );
    }

    @Override
    public String getRuleCode() {
        return RULE_CODE;
    }

    @Override
    public boolean isEnabled() {
        return fraudProperties.getRules().getUnusualTime().isEnabled();
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    private boolean isTimeWithinHalfOpenWindow(
            LocalTime transactionTime,
            LocalTime startInclusive,
            LocalTime endExclusive) {
        if (startInclusive.equals(endExclusive)) {
            return false;
        }
        if (startInclusive.isBefore(endExclusive)) {
            return !transactionTime.isBefore(startInclusive)
                    && transactionTime.isBefore(endExclusive);
        }

        return !transactionTime.isBefore(startInclusive)
                || transactionTime.isBefore(endExclusive);
    }
}
