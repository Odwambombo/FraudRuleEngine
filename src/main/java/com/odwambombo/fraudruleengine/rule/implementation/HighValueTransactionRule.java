package com.odwambombo.fraudruleengine.rule.implementation;

import com.odwambombo.fraudruleengine.rule.configuration.FraudProperties;
import com.odwambombo.fraudruleengine.rule.domain.FraudContext;
import com.odwambombo.fraudruleengine.rule.domain.FraudRule;
import com.odwambombo.fraudruleengine.rule.domain.FraudRuleResult;
import com.odwambombo.fraudruleengine.transaction.domain.TransactionEvent;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Objects;

@Component
public class HighValueTransactionRule implements FraudRule {

    public static final String RULE_CODE = "HIGH_VALUE_TRANSACTION";
    private static final int ORDER = 10;

    private final FraudProperties fraudProperties;

    public HighValueTransactionRule(FraudProperties fraudProperties) {
        this.fraudProperties = fraudProperties;
    }

    @Override
    public FraudRuleResult evaluate(
            TransactionEvent transactionEvent,
            FraudContext fraudContext) {
        Objects.requireNonNull(transactionEvent, "transactionEvent must not be null");

        final FraudProperties.HighValue configuration = fraudProperties.getRules().getHighValue();
        final boolean currencyMatches = currenciesMatch(transactionEvent.currency(), configuration.getCurrency());
        final BigDecimal transactionAmount = transactionEvent.amount();
        final boolean matched = currencyMatches
                && transactionAmount != null
                && transactionAmount.compareTo(configuration.getThreshold()) > 0;

        if (!matched) {
            return FraudRuleResult.notMatched(RULE_CODE);
        }

        return FraudRuleResult.matched(
                RULE_CODE,
                configuration.getScore(),
                "Transaction amount exceeded "
                        + configuration.getCurrency().toUpperCase()
                        + " "
                        + configuration.getThreshold().stripTrailingZeros().toPlainString()
                        + "."
        );
    }

    @Override
    public String getRuleCode() {
        return RULE_CODE;
    }

    @Override
    public boolean isEnabled() {
        return fraudProperties.getRules().getHighValue().isEnabled();
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    private boolean currenciesMatch(String transactionCurrency, String configuredCurrency) {
        return transactionCurrency != null
                && configuredCurrency != null
                && transactionCurrency.trim().equalsIgnoreCase(configuredCurrency.trim());
    }
}
