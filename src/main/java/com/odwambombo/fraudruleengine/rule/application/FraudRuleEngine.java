package com.odwambombo.fraudruleengine.rule.application;

import com.odwambombo.fraudruleengine.rule.domain.FraudContext;
import com.odwambombo.fraudruleengine.rule.domain.FraudRule;
import com.odwambombo.fraudruleengine.rule.domain.FraudRuleResult;
import com.odwambombo.fraudruleengine.transaction.domain.TransactionEvent;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
public class FraudRuleEngine {

    private static final Comparator<FraudRule> RULE_ORDER = Comparator
            .comparingInt(FraudRule::getOrder)
            .thenComparing(FraudRule::getRuleCode);

    private final List<FraudRule> fraudRules;

    public FraudRuleEngine(List<FraudRule> fraudRules) {
        this.fraudRules = fraudRules.stream().sorted(RULE_ORDER).toList();
    }

    public List<FraudRuleResult> evaluate(
            TransactionEvent transactionEvent,
            FraudContext fraudContext) {
        Objects.requireNonNull(transactionEvent, "transactionEvent must not be null");
        Objects.requireNonNull(fraudContext, "fraudContext must not be null");

        return fraudRules.stream()
                .filter(FraudRule::isEnabled)
                .map(fraudRule -> fraudRule.evaluate(transactionEvent, fraudContext))
                .filter(FraudRuleResult::matched)
                .toList();
    }
}
