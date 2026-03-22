package com.govpay.govpay_backend.utility.entity;

import com.govpay.govpay_backend.auth.entity.User;
import com.govpay.govpay_backend.wallet.entity.Transaction;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bills", indexes = {
        @Index(name = "idx_bills_user_id",  columnList = "user_id"),
        @Index(name = "idx_bills_status",   columnList = "status"),
        @Index(name = "idx_bills_due_date", columnList = "due_date")
})
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Bill {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "utility_service_id", nullable = false)
    private UtilityProvider utilityService;

    // Amount in smallest currency unit
    @Column(nullable = false)
    private Long amount;

    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "TZS";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private BillStatus status = BillStatus.UNPAID;

    @Column(nullable = false)
    private Instant dueDate;

    @Column(length = 500)
    private String description;

    // Set when bill is paid — links to the transaction that paid it
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id")
    private Transaction transaction;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant paidAt;

    public enum BillStatus {
        UNPAID, PAID, OVERDUE, CANCELLED
    }

    public boolean isPayable() {
        return status == BillStatus.UNPAID || status == BillStatus.OVERDUE;
    }
}
