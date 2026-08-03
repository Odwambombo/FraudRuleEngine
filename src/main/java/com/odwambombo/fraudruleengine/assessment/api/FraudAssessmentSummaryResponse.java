package com.odwambombo.fraudruleengine.assessment.api;

import com.odwambombo.fraudruleengine.assessment.domain.RiskLevel;

import java.time.Instant;
import java.util.UUID;

public record FraudAssessmentSummaryResponse(
        UUID assessmentId,
        String transactionId,
        String customerId,
        int riskScore,
        RiskLevel riskLevel,
        boolean flagged,
        Instant evaluatedAt) { }
