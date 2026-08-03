package com.odwambombo.fraudruleengine.transaction.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionEventRepository extends JpaRepository<TransactionEventEntity, UUID> {

    Optional<TransactionEventEntity> findByEventId(String eventId);

    boolean existsByEventId(String eventId);

    List<TransactionEventEntity> findAllByTransactionIdOrderByReceivedAtDesc(String transactionId);

    @Query("""
            select count(transactionEvent)
            from TransactionEventEntity transactionEvent
            where transactionEvent.customerId = :customerId
              and transactionEvent.transactionTime >= :fromInclusive
              and transactionEvent.transactionTime <= :toInclusive
            """)
    long countByCustomerIdAndTransactionTimeBetween(
            @Param("customerId") String customerId,
            @Param("fromInclusive") LocalDateTime fromInclusive,
            @Param("toInclusive") LocalDateTime toInclusive);
}
