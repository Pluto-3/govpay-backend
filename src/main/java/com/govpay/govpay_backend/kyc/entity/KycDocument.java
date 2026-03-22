package com.govpay.govpay_backend.kyc.entity;

import com.govpay.govpay_backend.auth.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "kyc_documents", indexes = {
        @Index(name = "idx_kyc_user_id", columnList = "user_id"),
        @Index(name = "idx_kyc_status",  columnList = "status")
})
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class KycDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DocumentType documentType;

    @Column(nullable = false, length = 100)
    private String documentNumber;

    @Column(length = 500)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private KycDocumentStatus status = KycDocumentStatus.PENDING;

    // Set by admin on rejection
    @Column(length = 500)
    private String rejectionReason;

    // Admin who reviewed this document
    @Column(length = 255)
    private String reviewedBy;

    private Instant reviewedAt;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant submittedAt;

    @LastModifiedDate
    private Instant updatedAt;

    public enum DocumentType {
        NATIONAL_ID,
        PASSPORT,
        DRIVERS_LICENSE,
        VOTERS_CARD,
        RESIDENCE_PERMIT
    }

    public enum KycDocumentStatus {
        PENDING,    // submitted, awaiting review
        APPROVED,   // accepted by admin
        REJECTED    // rejected by admin
    }
}