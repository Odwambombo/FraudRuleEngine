package com.odwambombo.fraudruleengine.assessment.api;

public record MatchedRuleResponse(String ruleCode, int score, String reason) { }
