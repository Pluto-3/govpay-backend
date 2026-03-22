package com.govpay.govpay_backend.notification.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationClient {

    private final RestClient restClient;

    @Value("${govpay.notification.service-url}")
    private String notificationServiceUrl;

    // ── Public send methods ───────────────────────────────────────────────────

    @Async
    public void sendWelcomeEmail(String email, String name) {
        send(NotificationRequest.builder()
                .type("EMAIL")
                .recipient(email)
                .subject("Welcome to GovPay!")
                .templateName("welcome_govpay")
                .data(Map.of("name", name))
                .build());
    }

    @Async
    public void sendTopUpConfirmation(String email, String name,
                                      String amount, String newBalance,
                                      String currency, String reference) {
        send(NotificationRequest.builder()
                .type("EMAIL")
                .recipient(email)
                .subject("Wallet Top-Up Confirmed — " + amount + " " + currency)
                .templateName("wallet_topup")
                .data(Map.of(
                        "name", name,
                        "amount", amount,
                        "newBalance", newBalance,
                        "currency", currency,
                        "reference", reference
                ))
                .build());
    }

    @Async
    public void sendPaymentSentEmail(String email, String name,
                                     String amount, String currency,
                                     String recipientEmail, String description,
                                     String transactionId) {
        send(NotificationRequest.builder()
                .type("EMAIL")
                .recipient(email)
                .subject("Payment Sent — " + amount + " " + currency)
                .templateName("payment_sent")
                .data(Map.of(
                        "name", name,
                        "amount", amount,
                        "currency", currency,
                        "recipientEmail", recipientEmail,
                        "description", description != null ? description : "",
                        "transactionId", transactionId
                ))
                .build());
    }

    @Async
    public void sendPaymentReceivedEmail(String email, String name,
                                         String amount, String currency,
                                         String senderEmail, String description,
                                         String transactionId) {
        send(NotificationRequest.builder()
                .type("EMAIL")
                .recipient(email)
                .subject("You received " + amount + " " + currency)
                .templateName("payment_received")
                .data(Map.of(
                        "name", name,
                        "amount", amount,
                        "currency", currency,
                        "senderEmail", senderEmail,
                        "description", description != null ? description : "",
                        "transactionId", transactionId
                ))
                .build());
    }

    @Async
    public void sendLowBalanceAlert(String email, String name,
                                    String currentBalance, String threshold,
                                    String currency) {
        send(NotificationRequest.builder()
                .type("EMAIL")
                .recipient(email)
                .subject("Low Balance Alert — GovPay Wallet")
                .templateName("low_balance")
                .data(Map.of(
                        "name", name,
                        "currentBalance", currentBalance,
                        "threshold", threshold,
                        "currency", currency
                ))
                .build());
    }

    // ── Private HTTP call ─────────────────────────────────────────────────────

    private void send(NotificationRequest request) {
        try {
            restClient.post()
                    .uri(notificationServiceUrl + "/notifications")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
            log.debug("Notification sent: type={} recipient={}", request.getType(), request.getRecipient());
        } catch (Exception ex) {
            // Never let notification failure affect the main transaction
            log.error("Failed to send notification to {}: {}", request.getRecipient(), ex.getMessage());
        }
    }

    // ── Inner request DTO ─────────────────────────────────────────────────────

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class NotificationRequest {
        private String type;
        private String recipient;
        private String subject;
        private String templateName;
        private String message;
        private Map<String, Object> data;
    }
}