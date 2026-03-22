package com.govpay.govpay_backend.admin.dto;

import com.govpay.govpay_backend.auth.entity.User;
import com.govpay.govpay_backend.wallet.entity.Transaction;
import com.govpay.govpay_backend.wallet.entity.Wallet;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class AdminDto {

    // ── User detail ───────────────────────────────────────────────────────────

    @Data @Builder
    public static class AdminUserResponse {
        private UUID id;
        private String email;
        private String firstName;
        private String lastName;
        private String phoneNumber;
        private User.Role role;
        private User.UserStatus status;
        private User.KycStatus kycStatus;
        private Instant createdAt;

        // Wallet info
        private UUID walletId;
        private BigDecimal walletBalance;
        private String walletCurrency;
        private Wallet.WalletStatus walletStatus;

        public static AdminUserResponse from(User user, Wallet wallet) {
            AdminUserResponseBuilder builder = AdminUserResponse.builder()
                    .id(user.getId())
                    .email(user.getEmail())
                    .firstName(user.getFirstName())
                    .lastName(user.getLastName())
                    .phoneNumber(user.getPhoneNumber())
                    .role(user.getRole())
                    .status(user.getStatus())
                    .kycStatus(user.getKycStatus())
                    .createdAt(user.getCreatedAt());

            if (wallet != null) {
                builder.walletId(wallet.getId())
                        .walletBalance(wallet.getDisplayBalance())
                        .walletCurrency(wallet.getCurrency())
                        .walletStatus(wallet.getStatus());
            }

            return builder.build();
        }
    }

    // ── Transaction detail ────────────────────────────────────────────────────

    @Data @Builder
    public static class AdminTransactionResponse {
        private UUID id;
        private UUID senderWalletId;
        private String senderEmail;
        private UUID recipientWalletId;
        private String recipientEmail;
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

        public static AdminTransactionResponse from(Transaction tx) {
            return AdminTransactionResponse.builder()
                    .id(tx.getId())
                    .senderWalletId(tx.getSenderWallet() != null ? tx.getSenderWallet().getId() : null)
                    .senderEmail(tx.getSenderWallet() != null ? tx.getSenderWallet().getUser().getEmail() : null)
                    .recipientWalletId(tx.getRecipientWallet() != null ? tx.getRecipientWallet().getId() : null)
                    .recipientEmail(tx.getRecipientWallet() != null ? tx.getRecipientWallet().getUser().getEmail() : null)
                    .type(tx.getType())
                    .status(tx.getStatus())
                    .amountRaw(tx.getAmount())
                    .amount(BigDecimal.valueOf(tx.getAmount(), 2))
                    .currency(tx.getCurrency())
                    .description(tx.getDescription())
                    .reference(tx.getReference())
                    .failedReason(tx.getFailedReason())
                    .createdAt(tx.getCreatedAt())
                    .completedAt(tx.getCompletedAt())
                    .build();
        }
    }

    // ── Dashboard stats ───────────────────────────────────────────────────────

    @Data @Builder
    public static class DashboardResponse {
        // Users
        private long totalUsers;
        private long activeUsers;
        private long suspendedUsers;
        private long pendingKyc;
        private long approvedKyc;

        // Wallets
        private long totalWallets;
        private BigDecimal totalWalletBalance;

        // Transactions
        private long totalTransactions;
        private long completedTransactions;
        private long failedTransactions;
        private BigDecimal totalVolume;
        private BigDecimal totalTopUpVolume;
        private BigDecimal totalTransferVolume;
        private BigDecimal totalUtilityVolume;
    }
}