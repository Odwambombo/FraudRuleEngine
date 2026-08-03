package com.odwambombo.fraudruleengine.rule.implementation;

import com.odwambombo.fraudruleengine.rule.configuration.FraudProperties;
import com.odwambombo.fraudruleengine.rule.domain.FraudContext;
import com.odwambombo.fraudruleengine.rule.domain.FraudRule;
import com.odwambombo.fraudruleengine.rule.domain.FraudRuleResult;
import com.odwambombo.fraudruleengine.transaction.domain.TransactionEvent;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class RiskyCategoryRule implements FraudRule {

    public static final String RULE_CODE = "RISKY_TRANSACTION_CATEGORY";
    private static final int ORDER = 30;

    private final FraudProperties fraudProperties;

    public RiskyCategoryRule(FraudProperties fraudProperties) {
        this.fraudProperties = fraudProperties;
    }

    @Override
    public FraudRuleResult evaluate(
            TransactionEvent transactionEvent,
            FraudContext fraudContext) {
        Objects.requireNonNull(transactionEvent, "transactionEvent must not be null");

        final FraudProperties.RiskyCategory configuration = fraudProperties
                .getRules()
                .getRiskyCategory();
        final Set<String> configuredCategories = configuration.getCategories().stream()
                .filter(Objects::nonNull)
                .map(this::normalizeCategory)
                .collect(Collectors.toUnmodifiableSet());
        final String transactionCategory = transactionEvent.category() == null
                ? null
                : normalizeCategory(transactionEvent.category());
        final boolean matched = transactionCategory != null
                && configuredCategories.contains(transactionCategory);

        if (!matched) {
            return FraudRuleResult.notMatched(RULE_CODE);
        }

        return FraudRuleResult.matched(
                RULE_CODE,
                configuration.getScore(),
                "Transaction category " + transactionCategory + " is configured as risky."
        );
    }

    @Override
    public String getRuleCode() {
        return RULE_CODE;
    }

    @Override
    public boolean isEnabled() {
        return fraudProperties.getRules().getRiskyCategory().isEnabled();
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    private String normalizeCategory(String category) {
        return category.trim().toUpperCase(Locale.ROOT);
    }
}
