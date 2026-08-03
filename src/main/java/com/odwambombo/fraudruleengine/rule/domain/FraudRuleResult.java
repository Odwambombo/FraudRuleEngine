package com.odwambombo.fraudruleengine.rule.domain;

import java.util.Objects;

public record FraudRuleResult(
        String ruleCode,
        boolean matched,
        int score,
        String reason) {

    public FraudRuleResult {
        Objects.requireNonNull(ruleCode, "ruleCode must not be null");

        if (score < 0) {
            throw new IllegalArgumentException("score must not be negative");
        }
        if (!matched && score != 0) {
            throw new IllegalArgumentException("An unmatched rule must have a score of zero");
        }
        if (matched && (reason == null || reason.isBlank())) {
            throw new IllegalArgumentException("A matched rule must provide a reason");
        }
    }

    public static FraudRuleResult matched(String ruleCode, int score, String reason) {
        return new FraudRuleResult(ruleCode, true, score, reason);
    }

    public static FraudRuleResult notMatched(String ruleCode) {
        return new FraudRuleResult(ruleCode, false, 0, null);
    }
}
