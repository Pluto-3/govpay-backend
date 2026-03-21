package com.govpay.govpay_backend.wallet.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transactions", indexes = {
        @Index(name = "idx_transactions_sender",     columnList = "sender_wallet_id"),
        @Index(name = "idx_transactions_recipient",  columnList = "recipient_wallet_id"),
        @Index(name = "idx_transactions_status",     columnList = "status"),
        @Index(name = "idx_transactions_created_at", columnList = "created_at"),
        @Index(name = "idx_transactions_reference",  columnList = "reference")
})
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_wallet_id")
    private Wallet senderWallet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_wallet_id")
    private Wallet recipientWallet;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private TransactionStatus status = TransactionStatus.PENDING;

    // Amount in smallest currency unit — always positive
    @Column(nullable = false)
    private Long amount;

    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "TZS";

    @Column(length = 255)
    private String description;

    // Idempotency key — prevents duplicate transactions
    @Column(unique = true, length = 100)
    private String reference;

    @Column(length = 500)
    private String failedReason;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant completedAt;

    public enum TransactionType {
        TOP_UP, P2P_TRANSFER, UTILITY_PAYMENT, GOVERNMENT_FEE,
        REFUND, REVERSAL, ADJUSTMENT
    }

    public enum TransactionStatus {
        PENDING, COMPLETED, FAILED, REVERSED
    }

    public void markCompleted() {
        this.status = TransactionStatus.COMPLETED;
        this.completedAt = Instant.now();
    }

    public void markFailed(String reason) {
        this.status = TransactionStatus.FAILED;
        this.failedReason = reason;
    }
}