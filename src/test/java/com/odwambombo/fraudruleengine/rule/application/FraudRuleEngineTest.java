package com.odwambombo.fraudruleengine.rule.application;

import com.odwambombo.fraudruleengine.rule.configuration.FraudProperties;
import com.odwambombo.fraudruleengine.rule.domain.FraudContext;
import com.odwambombo.fraudruleengine.rule.domain.FraudRuleResult;
import com.odwambombo.fraudruleengine.rule.implementation.ForeignTransactionRule;
import com.odwambombo.fraudruleengine.rule.implementation.HighValueTransactionRule;
import com.odwambombo.fraudruleengine.rule.implementation.RiskyCategoryRule;
import com.odwambombo.fraudruleengine.rule.implementation.TransactionVelocityRule;
import com.odwambombo.fraudruleengine.rule.implementation.UnusualTransactionTimeRule;
import com.odwambombo.fraudruleengine.transaction.domain.TransactionEvent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FraudRuleEngineTest {

    @Test
    void evaluatesEnabledRulesAndReturnsMatchesInDeterministicOrder() {
        final FraudProperties properties = new FraudProperties();
        properties.getRules().getRiskyCategory().setEnabled(false);
        final FraudRuleEngine engine = new FraudRuleEngine(List.of(
                new TransactionVelocityRule(properties),
                new ForeignTransactionRule(properties),
                new RiskyCategoryRule(properties),
                new UnusualTransactionTimeRule(properties),
                new HighValueTransactionRule(properties)
        ));
        final TransactionEvent transaction = new TransactionEvent(
                "evt-10001",
                "txn-50001",
                "cust-123",
                new BigDecimal("25000.00"),
                "ZAR",
                "GAMBLING",
                "CARD_PURCHASE",
                "Merchant",
                "US",
                "ZA",
                LocalDateTime.of(2026, 7, 27, 2, 15)
        );

        final List<FraudRuleResult> results = engine.evaluate(transaction, new FraudContext(4));

        assertEquals(
                List.of(
                        HighValueTransactionRule.RULE_CODE,
                        UnusualTransactionTimeRule.RULE_CODE,
                        ForeignTransactionRule.RULE_CODE,
                        TransactionVelocityRule.RULE_CODE
                ),
                results.stream().map(FraudRuleResult::ruleCode).toList()
        );
        assertEquals(List.of(40, 20, 25, 35), results.stream().map(FraudRuleResult::score).toList());
    }
}
