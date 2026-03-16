package com.govpay.govpay_backend.notification.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class NotificationEvents {

    // ── Base event ────────────────────────────────────────────────────────────

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "eventType")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = UserRegisteredEvent.class,   name = "USER_REGISTERED"),
            @JsonSubTypes.Type(value = KycStatusChangedEvent.class, name = "KYC_STATUS_CHANGED"),
            @JsonSubTypes.Type(value = PaymentCompletedEvent.class, name = "PAYMENT_COMPLETED"),
            @JsonSubTypes.Type(value = PaymentFailedEvent.class,    name = "PAYMENT_FAILED"),
            @JsonSubTypes.Type(value = LowBalanceEvent.class,       name = "LOW_BALANCE"),
            @JsonSubTypes.Type(value = BillGeneratedEvent.class,    name = "BILL_GENERATED"),
    })
    public abstract static class BaseEvent {
        private String eventType;
        private Instant occurredAt = Instant.now();
        private UUID correlationId = UUID.randomUUID();
    }

    // ── Auth events ───────────────────────────────────────────────────────────

    @Data
    @Builder
    @NoArgsConstructor
    public static class UserRegisteredEvent extends BaseEvent {
        private UUID userId;
        private String email;
        private String firstName;
        private String lastName;

        public UserRegisteredEvent(UUID userId, String email, String firstName, String lastName) {
            super("USER_REGISTERED", Instant.now(), UUID.randomUUID());
            this.userId = userId;
            this.email = email;
            this.firstName = firstName;
            this.lastName = lastName;
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    public static class KycStatusChangedEvent extends BaseEvent {
        private UUID userId;
        private String email;
        private String firstName;
        private String newStatus;
        private String rejectionReason;

        public KycStatusChangedEvent(UUID userId, String email, String firstName,
                                     String newStatus, String rejectionReason) {
            super("KYC_STATUS_CHANGED", Instant.now(), UUID.randomUUID());
            this.userId = userId;
            this.email = email;
            this.firstName = firstName;
            this.newStatus = newStatus;
            this.rejectionReason = rejectionReason;
        }
    }

    // ── Wallet / payment events ───────────────────────────────────────────────

    @Data
    @Builder
    @NoArgsConstructor
    public static class PaymentCompletedEvent extends BaseEvent {
        private UUID transactionId;
        private UUID senderId;
        private String senderEmail;
        private UUID recipientId;
        private String recipientEmail;
        private BigDecimal amount;
        private String currency;
        private String description;

        public PaymentCompletedEvent(UUID transactionId, UUID senderId, String senderEmail,
                                     UUID recipientId, String recipientEmail,
                                     BigDecimal amount, String currency, String description) {
            super("PAYMENT_COMPLETED", Instant.now(), UUID.randomUUID());
            this.transactionId = transactionId;
            this.senderId = senderId;
            this.senderEmail = senderEmail;
            this.recipientId = recipientId;
            this.recipientEmail = recipientEmail;
            this.amount = amount;
            this.currency = currency;
            this.description = description;
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    public static class PaymentFailedEvent extends BaseEvent {
        private UUID userId;
        private String email;
        private BigDecimal attemptedAmount;
        private String reason;

        public PaymentFailedEvent(UUID userId, String email, BigDecimal attemptedAmount, String reason) {
            super("PAYMENT_FAILED", Instant.now(), UUID.randomUUID());
            this.userId = userId;
            this.email = email;
            this.attemptedAmount = attemptedAmount;
            this.reason = reason;
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    public static class LowBalanceEvent extends BaseEvent {
        private UUID userId;
        private String email;
        private String firstName;
        private BigDecimal currentBalance;
        private BigDecimal threshold;
        private String currency;

        public LowBalanceEvent(UUID userId, String email, String firstName,
                               BigDecimal currentBalance, BigDecimal threshold, String currency) {
            super("LOW_BALANCE", Instant.now(), UUID.randomUUID());
            this.userId = userId;
            this.email = email;
            this.firstName = firstName;
            this.currentBalance = currentBalance;
            this.threshold = threshold;
            this.currency = currency;
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    public static class BillGeneratedEvent extends BaseEvent {
        private UUID billId;
        private UUID userId;
        private String email;
        private String firstName;
        private String serviceType;
        private BigDecimal amount;
        private String currency;
        private Instant dueDate;

        public BillGeneratedEvent(UUID billId, UUID userId, String email, String firstName,
                                  String serviceType, BigDecimal amount, String currency, Instant dueDate) {
            super("BILL_GENERATED", Instant.now(), UUID.randomUUID());
            this.billId = billId;
            this.userId = userId;
            this.email = email;
            this.firstName = firstName;
            this.serviceType = serviceType;
            this.amount = amount;
            this.currency = currency;
            this.dueDate = dueDate;
        }
    }
}