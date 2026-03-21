package com.govpay.govpay_backend.wallet.entity;

import com.govpay.govpay_backend.auth.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "wallets", indexes = {
        @Index(name = "idx_wallets_user_id", columnList = "user_id", unique = true),
        @Index(name = "idx_wallets_status",  columnList = "status")
})
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    // Stored in smallest currency unit (e.g. TZS cents)
    // NEVER use floating point for money
    @Column(nullable = false)
    @Builder.Default
    private Long balance = 0L;

    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "TZS";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private WalletStatus status = WalletStatus.ACTIVE;

    // Optimistic locking — prevents lost updates under concurrent transactions
    // If two transactions read the same version and both try to update,
    // the second one will get an OptimisticLockException and must retry
    @Version
    @Column(nullable = false)
    @Builder.Default
    private Long version = 0L;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    public enum WalletStatus {
        ACTIVE, FROZEN, CLOSED
    }

    public boolean isActive() {
        return status == WalletStatus.ACTIVE;
    }

    public boolean hasSufficientBalance(Long amount) {
        return this.balance >= amount;
    }

    public void credit(Long amount) {
        this.balance += amount;
    }

    public void debit(Long amount) {
        if (!hasSufficientBalance(amount)) {
            throw new IllegalStateException("Insufficient balance");
        }
        this.balance -= amount;
    }

    // Convert stored long to BigDecimal for display (divide by 100)
    public BigDecimal getDisplayBalance() {
        return BigDecimal.valueOf(balance, 2);
    }
}