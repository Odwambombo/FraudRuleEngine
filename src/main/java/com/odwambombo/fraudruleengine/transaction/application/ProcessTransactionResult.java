package com.odwambombo.fraudruleengine.transaction.application;

import com.odwambombo.fraudruleengine.assessment.domain.FraudAssessment;

public record ProcessTransactionResult(FraudAssessment assessment, boolean created) { }
