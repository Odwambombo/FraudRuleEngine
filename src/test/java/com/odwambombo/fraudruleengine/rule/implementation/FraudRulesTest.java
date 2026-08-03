package com.odwambombo.fraudruleengine.rule.implementation;

import com.odwambombo.fraudruleengine.rule.configuration.FraudProperties;
import com.odwambombo.fraudruleengine.rule.domain.FraudContext;
import com.odwambombo.fraudruleengine.rule.domain.FraudRuleResult;
import com.odwambombo.fraudruleengine.transaction.domain.TransactionEvent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FraudRulesTest {

    private final FraudProperties properties = new FraudProperties();
    private final FraudContext emptyContext = new FraudContext(0);

    @Test
    void highValueMatchesOnlyZarAmountsStrictlyAboveThreshold() {
        final HighValueTransactionRule rule = new HighValueTransactionRule(properties);

        assertTrue(rule.evaluate(transaction("20000.01", "ZAR", "ELECTRONICS", "ZA", "ZA",
                LocalDateTime.of(2026, 7, 27, 12, 0)), emptyContext).matched());
        assertFalse(rule.evaluate(transaction("20000.00", "ZAR", "ELECTRONICS", "ZA", "ZA",
                LocalDateTime.of(2026, 7, 27, 12, 0)), emptyContext).matched());
        assertFalse(rule.evaluate(transaction("50000.00", "USD", "ELECTRONICS", "US", "ZA",
                LocalDateTime.of(2026, 7, 27, 12, 0)), emptyContext).matched());
    }

    @Test
    void unusualTimeUsesInclusiveStartAndExclusiveEnd() {
        final UnusualTransactionTimeRule rule = new UnusualTransactionTimeRule(properties);

        assertTrue(rule.evaluate(transactionAt(LocalDateTime.of(2026, 7, 27, 0, 0)), emptyContext).matched());
        assertTrue(rule.evaluate(transactionAt(LocalDateTime.of(2026, 7, 27, 3, 59, 59)), emptyContext).matched());
        assertFalse(rule.evaluate(transactionAt(LocalDateTime.of(2026, 7, 27, 4, 0)), emptyContext).matched());
    }

    @Test
    void riskyCategoryMatchesConfiguredCategoriesIgnoringCase() {
        final RiskyCategoryRule rule = new RiskyCategoryRule(properties);

        final FraudRuleResult matched = rule.evaluate(
                transaction("100.00", "ZAR", " cryptocurrency ", "ZA", "ZA",
                        LocalDateTime.of(2026, 7, 27, 12, 0)),
                emptyContext
        );

        assertTrue(matched.matched());
        assertEquals(15, matched.score());
        assertFalse(rule.evaluate(transaction("100.00", "ZAR", "GROCERIES", "ZA", "ZA",
                LocalDateTime.of(2026, 7, 27, 12, 0)), emptyContext).matched());
    }

    @Test
    void foreignTransactionRequiresBothCountriesAndComparesCaseInsensitively() {
        final ForeignTransactionRule rule = new ForeignTransactionRule(properties);

        assertTrue(rule.evaluate(transaction("100.00", "ZAR", "GROCERIES", "US", "ZA",
                LocalDateTime.of(2026, 7, 27, 12, 0)), emptyContext).matched());
        assertFalse(rule.evaluate(transaction("100.00", "ZAR", "GROCERIES", "za", "ZA",
                LocalDateTime.of(2026, 7, 27, 12, 0)), emptyContext).matched());
        assertFalse(rule.evaluate(transaction("100.00", "ZAR", "GROCERIES", "US", null,
                LocalDateTime.of(2026, 7, 27, 12, 0)), emptyContext).matched());
    }

    @Test
    void velocityIncludesCurrentTransactionInConfiguredMinimum() {
        final TransactionVelocityRule rule = new TransactionVelocityRule(properties);

        assertFalse(rule.evaluate(transactionAt(LocalDateTime.of(2026, 7, 27, 12, 0)),
                new FraudContext(3)).matched());

        final FraudRuleResult matched = rule.evaluate(
                transactionAt(LocalDateTime.of(2026, 7, 27, 12, 0)),
                new FraudContext(4)
        );

        assertTrue(matched.matched());
        assertEquals(35, matched.score());
    }

    private TransactionEvent transactionAt(LocalDateTime transactionTime) {
        return transaction("100.00", "ZAR", "GROCERIES", "ZA", "ZA", transactionTime);
    }

    private TransactionEvent transaction(
            String amount,
            String currency,
            String category,
            String country,
            String customerCountry,
            LocalDateTime transactionTime) {
        return new TransactionEvent(
                "evt-10001",
                "txn-50001",
                "cust-123",
                new BigDecimal(amount),
                currency,
                category,
                "CARD_PURCHASE",
                "Merchant",
                country,
                customerCountry,
                transactionTime
        );
    }
}
