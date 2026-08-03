package com.odwambombo.fraudruleengine.assessment.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record FraudAssessment(
        UUID assessmentId,
        String eventId,
        String transactionId,
        String customerId,
        BigDecimal amount,
        String currency,
        String category,
        String transactionType,
        String merchant,
        String country,
        String customerCountry,
        int riskScore,
        RiskLevel riskLevel,
        boolean flagged,
        List<MatchedRule> matchedRules,
        LocalDateTime transactionTime,
        Instant evaluatedAt) {

    public FraudAssessment {
        matchedRules = List.copyOf(matchedRules);
    }

}
