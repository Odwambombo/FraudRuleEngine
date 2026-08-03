package com.odwambombo.fraudruleengine.transaction.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransactionEvent(
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
        LocalDateTime transactionTime) { }
