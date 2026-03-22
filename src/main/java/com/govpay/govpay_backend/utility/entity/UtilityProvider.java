package com.govpay.govpay_backend.utility.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "utility_services", indexes = {
        @Index(name = "idx_utility_services_code", columnList = "code", unique = true),
        @Index(name = "idx_utility_services_type", columnList = "type")
})
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UtilityProvider {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private UtilityType type;

    @Column(nullable = false, length = 100)
    private String providerName;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public enum UtilityType {
        WATER, ELECTRICITY, TAX, FINE, GOVERNMENT_FEE, OTHER
    }
}