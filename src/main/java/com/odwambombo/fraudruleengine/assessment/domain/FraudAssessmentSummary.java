package com.odwambombo.fraudruleengine.assessment.domain;

import java.time.Instant;
import java.util.UUID;

public record FraudAssessmentSummary(
        UUID assessmentId,
        String transactionId,
        String customerId,
        int riskScore,
        RiskLevel riskLevel,
        boolean flagged,
        Instant evaluatedAt) { }
