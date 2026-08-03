package com.odwambombo.fraudruleengine.transaction.api;

import com.odwambombo.fraudruleengine.shared.validation.ApiIdentifier;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransactionEventRequest(
        @NotBlank @ApiIdentifier
        @Schema(description = "Unique delivery identifier and idempotency key.", example = "evt-10001")
        String eventId,
        @NotBlank @ApiIdentifier
        @Schema(description = "Transaction identifier from the source system.", example = "txn-50001")
        String transactionId,
        @NotBlank @ApiIdentifier
        @Schema(description = "Customer whose recent transaction activity is evaluated.", example = "cust-123")
        String customerId,
        @NotNull @DecimalMin(value = "0.00", inclusive = false) @Digits(integer = 17, fraction = 2)
        @Schema(description = "Positive transaction amount with at most two decimal places.", example = "25000.00")
        BigDecimal amount,
        @NotBlank @Pattern(regexp = "(?i)[A-Z]{3}", message = "must be a three-letter ISO currency code")
        @Schema(description = "Three-letter ISO currency code.", example = "ZAR")
        String currency,
        @NotBlank @Size(max = 64)
        @Schema(description = "Upstream transaction category.", example = "ELECTRONICS")
        String category,
        @NotBlank @Size(max = 64)
        @Schema(description = "Upstream transaction type.", example = "CARD_PURCHASE")
        String transactionType,
        @NotBlank @Size(max = 255)
        @Schema(description = "Merchant display name.", example = "Tech World")
        String merchant,
        @NotBlank @Pattern(regexp = "(?i)[A-Z]{2}", message = "must be a two-letter ISO country code")
        @Schema(description = "Two-letter ISO country where the transaction occurred.", example = "ZA")
        String country,
        @Pattern(regexp = "(?i)[A-Z]{2}", message = "must be a two-letter ISO country code")
        @Schema(
                description = "Optional registered customer country used by the foreign-transaction rule.",
                example = "ZA"
        )
        String customerCountry,
        @NotNull
        @Schema(
                description = "Offset-free local transaction time supplied by the source system.",
                example = "2026-07-27T02:15:00"
        )
        LocalDateTime transactionTime) {
}
