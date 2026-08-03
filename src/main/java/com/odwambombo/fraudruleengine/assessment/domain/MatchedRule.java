package com.odwambombo.fraudruleengine.assessment.domain;

public record MatchedRule(String ruleCode, int score, String reason) { }
