package com.odwambombo.fraudruleengine.transaction.application;

import com.odwambombo.fraudruleengine.transaction.api.TransactionEventRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

public record ProcessTransactionCommand(
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
        LocalDateTime transactionTime) {

    public static ProcessTransactionCommand fromRequest(TransactionEventRequest transactionEventRequest) {
        return new ProcessTransactionCommand(
                transactionEventRequest.eventId().trim(),
                transactionEventRequest.transactionId().trim(),
                transactionEventRequest.customerId().trim(),
                transactionEventRequest.amount(),
                normalizeToUppercase(transactionEventRequest.currency()),
                normalizeToUppercase(transactionEventRequest.category()),
                normalizeToUppercase(transactionEventRequest.transactionType()),
                transactionEventRequest.merchant().trim(),
                normalizeToUppercase(transactionEventRequest.country()),
                transactionEventRequest.customerCountry() == null
                        ? null
                        : normalizeToUppercase(transactionEventRequest.customerCountry()),
                transactionEventRequest.transactionTime().truncatedTo(ChronoUnit.MICROS)
        );
    }

    private static String normalizeToUppercase(String rawValue) {
        return rawValue.trim().toUpperCase(Locale.ROOT);
    }
}
