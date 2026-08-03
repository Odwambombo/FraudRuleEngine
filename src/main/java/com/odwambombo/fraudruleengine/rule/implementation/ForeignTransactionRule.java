package com.odwambombo.fraudruleengine.rule.implementation;

import com.odwambombo.fraudruleengine.rule.configuration.FraudProperties;
import com.odwambombo.fraudruleengine.rule.domain.FraudContext;
import com.odwambombo.fraudruleengine.rule.domain.FraudRule;
import com.odwambombo.fraudruleengine.rule.domain.FraudRuleResult;
import com.odwambombo.fraudruleengine.transaction.domain.TransactionEvent;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Objects;

@Component
public class ForeignTransactionRule implements FraudRule {

    public static final String RULE_CODE = "FOREIGN_TRANSACTION";
    private static final int ORDER = 40;

    private final FraudProperties fraudProperties;

    public ForeignTransactionRule(FraudProperties fraudProperties) {
        this.fraudProperties = fraudProperties;
    }

    @Override
    public FraudRuleResult evaluate(
            TransactionEvent transactionEvent,
            FraudContext fraudContext) {
        Objects.requireNonNull(transactionEvent, "transactionEvent must not be null");

        final String transactionCountry = normalizeCountryOrNull(transactionEvent.country());
        final String customerCountry = normalizeCountryOrNull(transactionEvent.customerCountry());
        final boolean matched = transactionCountry != null
                && customerCountry != null
                && !transactionCountry.equals(customerCountry);

        if (!matched) {
            return FraudRuleResult.notMatched(RULE_CODE);
        }

        return FraudRuleResult.matched(
                RULE_CODE,
                fraudProperties.getRules().getForeignTransaction().getScore(),
                "Transaction country " + transactionCountry
                        + " differs from customer country " + customerCountry + "."
        );
    }

    @Override
    public String getRuleCode() {
        return RULE_CODE;
    }

    @Override
    public boolean isEnabled() {
        return fraudProperties.getRules().getForeignTransaction().isEnabled();
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    private String normalizeCountryOrNull(String countryCode) {
        return countryCode == null || countryCode.isBlank()
                ? null
                : countryCode.trim().toUpperCase(Locale.ROOT);
    }
}
