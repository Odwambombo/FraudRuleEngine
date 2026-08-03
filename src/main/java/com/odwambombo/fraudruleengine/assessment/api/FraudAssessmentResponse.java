package com.odwambombo.fraudruleengine.assessment.api;

import com.odwambombo.fraudruleengine.assessment.domain.RiskLevel;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record FraudAssessmentResponse(
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
        List<MatchedRuleResponse> matchedRules,
        LocalDateTime transactionTime,
        Instant evaluatedAt) { }
