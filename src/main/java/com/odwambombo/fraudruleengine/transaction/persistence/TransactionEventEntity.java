package com.odwambombo.fraudruleengine.transaction.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.domain.Persistable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(
        name = "transaction_event",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_transaction_event_event_id",
                        columnNames = "event_id"
                )
        },
        indexes = {
                @Index(
                        name = "idx_transaction_event_transaction_id",
                        columnList = "transaction_id"
                ),
                @Index(
                        name = "idx_transaction_event_customer_time",
                        columnList = "customer_id, transaction_time"
                ),
                @Index(
                        name = "idx_transaction_event_received_at",
                        columnList = "received_at"
                )
        }
)
public class TransactionEventEntity implements Persistable<UUID> {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "event_id", nullable = false, updatable = false, length = 100)
    private String eventId;

    @Column(name = "transaction_id", nullable = false, updatable = false, length = 100)
    private String transactionId;

    @Column(name = "customer_id", nullable = false, updatable = false, length = 100)
    private String customerId;

    @Column(name = "amount", nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, updatable = false, length = 3)
    private String currency;

    @Column(name = "category", nullable = false, updatable = false, length = 64)
    private String category;

    @Column(name = "transaction_type", nullable = false, updatable = false, length = 64)
    private String transactionType;

    @Column(name = "merchant", nullable = false, updatable = false, length = 255)
    private String merchant;

    @Column(name = "country", nullable = false, updatable = false, length = 2)
    private String country;

    @Column(name = "customer_country", updatable = false, length = 2)
    private String customerCountry;

    @Column(name = "transaction_time", nullable = false, updatable = false)
    private LocalDateTime transactionTime;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @Transient
    private boolean newEntity = true;

    protected TransactionEventEntity() {
        // Required by JPA.
    }

    private TransactionEventEntity(
            UUID id,
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
            LocalDateTime transactionTime,
            Instant receivedAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.eventId = Objects.requireNonNull(eventId, "eventId must not be null");
        this.transactionId = Objects.requireNonNull(transactionId, "transactionId must not be null");
        this.customerId = Objects.requireNonNull(customerId, "customerId must not be null");
        this.amount = Objects.requireNonNull(amount, "amount must not be null");
        this.currency = Objects.requireNonNull(currency, "currency must not be null");
        this.category = Objects.requireNonNull(category, "category must not be null");
        this.transactionType = Objects.requireNonNull(transactionType, "transactionType must not be null");
        this.merchant = Objects.requireNonNull(merchant, "merchant must not be null");
        this.country = Objects.requireNonNull(country, "country must not be null");
        this.customerCountry = customerCountry;
        this.transactionTime = Objects.requireNonNull(transactionTime, "transactionTime must not be null");
        this.receivedAt = Objects.requireNonNull(receivedAt, "receivedAt must not be null");
    }

    public static TransactionEventEntity create(
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
            LocalDateTime transactionTime,
            Instant receivedAt) {
        return new TransactionEventEntity(
                UUID.randomUUID(),
                eventId,
                transactionId,
                customerId,
                amount,
                currency,
                category,
                transactionType,
                merchant,
                country,
                customerCountry,
                transactionTime,
                receivedAt
        );
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return newEntity;
    }

    @PostLoad
    @PostPersist
    private void markNotNew() {
        newEntity = false;
    }
}
