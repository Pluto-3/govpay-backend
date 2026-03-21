package com.govpay.govpay_backend.wallet.dto;

import com.govpay.govpay_backend.wallet.entity.Transaction;
import com.govpay.govpay_backend.wallet.entity.Wallet;
import jakarta.validation.constraints.*;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class WalletDto {

    // ── Requests ──────────────────────────────────────────────────────────────

    @Data
    public static class TopUpRequest {

        @NotNull(message = "Amount is required")
        @Min(value = 100, message = "Minimum top-up is 1.00 TZS")
        @Max(value = 10000000, message = "Maximum top-up is 100,000.00 TZS")
        private Long amount;

        @NotBlank(message = "Reference is required")
        @Size(max = 100)
        private String reference;

        private String description;
    }

    @Data
    public static class TransferRequest {

        @NotNull(message = "Recipient user ID is required")
        private UUID recipientUserId;

        @NotNull(message = "Amount is required")
        @Min(value = 1, message = "Amount must be greater than 0")
        private Long amount;

        @Size(max = 255, message = "Description too long")
        private String description;

        // Optional idempotency key — client can supply this to prevent
        // duplicate transfers if a request is retried
        @Size(max = 100)
        private String idempotencyKey;
    }

    // ── Responses ─────────────────────────────────────────────────────────────

    @Data @Builder
    public static class WalletResponse {
        private UUID id;
        private UUID userId;
        private Long balanceRaw;        // raw long for internal use
        private BigDecimal balance;     // divided by 100 for display
        private String currency;
        private Wallet.WalletStatus status;
        private Instant createdAt;
        private Instant updatedAt;

        public static WalletResponse from(Wallet w) {
            return WalletResponse.builder()
                    .id(w.getId())
                    .userId(w.getUser().getId())
                    .balanceRaw(w.getBalance())
                    .balance(w.getDisplayBalance())
                    .currency(w.getCurrency())
                    .status(w.getStatus())
                    .createdAt(w.getCreatedAt())
                    .updatedAt(w.getUpdatedAt())
                    .build();
        }
    }

    @Data @Builder
    public static class TransactionResponse {
        private UUID id;
        private UUID senderWalletId;
        private UUID recipientWalletId;
        private Transaction.TransactionType type;
        private Transaction.TransactionStatus status;
        private Long amountRaw;
        private BigDecimal amount;
        private String currency;
        private String description;
        private String reference;
        private String failedReason;
        private Instant createdAt;
        private Instant completedAt;

        public static TransactionResponse from(Transaction t) {
            return TransactionResponse.builder()
                    .id(t.getId())
                    .senderWalletId(t.getSenderWallet() != null ? t.getSenderWallet().getId() : null)
                    .recipientWalletId(t.getRecipientWallet() != null ? t.getRecipientWallet().getId() : null)
                    .type(t.getType())
                    .status(t.getStatus())
                    .amountRaw(t.getAmount())
                    .amount(BigDecimal.valueOf(t.getAmount(), 2))
                    .currency(t.getCurrency())
                    .description(t.getDescription())
                    .reference(t.getReference())
                    .failedReason(t.getFailedReason())
                    .createdAt(t.getCreatedAt())
                    .completedAt(t.getCompletedAt())
                    .build();
        }
    }
}