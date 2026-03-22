package com.govpay.govpay_backend.utility.dto;

import com.govpay.govpay_backend.utility.entity.Bill;
import com.govpay.govpay_backend.utility.entity.UtilityProvider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class UtilityDto {

    // ── Requests ──────────────────────────────────────────────────────────────

    @Data
    public static class GenerateBillRequest {

        @NotBlank(message = "Utility service code is required")
        private String serviceCode;

        // Optional — if not provided, a mock amount is generated
        private Long amount;

        private String description;
    }

    // ── Responses ─────────────────────────────────────────────────────────────

    @Data @Builder
    public static class UtilityProviderResponse {
        private UUID id;
        private String name;
        private String code;
        private UtilityProvider.UtilityType type;
        private String providerName;

        public static UtilityProviderResponse from(UtilityProvider u) {
            return UtilityProviderResponse.builder()
                    .id(u.getId())
                    .name(u.getName())
                    .code(u.getCode())
                    .type(u.getType())
                    .providerName(u.getProviderName())
                    .build();
        }
    }

    @Data @Builder
    public static class BillResponse {
        private UUID id;
        private UUID userId;
        private UtilityProviderResponse utilityService;
        private Long amountRaw;
        private BigDecimal amount;
        private String currency;
        private Bill.BillStatus status;
        private Instant dueDate;
        private String description;
        private UUID transactionId;
        private Instant createdAt;
        private Instant paidAt;

        public static BillResponse from(Bill bill) {
            return BillResponse.builder()
                    .id(bill.getId())
                    .userId(bill.getUser().getId())
                    .utilityService(UtilityProviderResponse.from(bill.getUtilityService()))
                    .amountRaw(bill.getAmount())
                    .amount(BigDecimal.valueOf(bill.getAmount(), 2))
                    .currency(bill.getCurrency())
                    .status(bill.getStatus())
                    .dueDate(bill.getDueDate())
                    .description(bill.getDescription())
                    .transactionId(bill.getTransaction() != null ? bill.getTransaction().getId() : null)
                    .createdAt(bill.getCreatedAt())
                    .paidAt(bill.getPaidAt())
                    .build();
        }
    }
}