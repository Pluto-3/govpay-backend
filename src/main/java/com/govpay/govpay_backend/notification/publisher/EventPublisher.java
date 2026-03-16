package com.govpay.govpay_backend.notification.publisher;

import com.govpay.govpay_backend.notification.dto.NotificationEvents.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventPublisher {

    private static final String EXCHANGE = "govpay.events";

    private final RabbitTemplate rabbitTemplate;

    @Async
    public void publishUserRegistered(UserRegisteredEvent event) {
        publish("user.registered", event);
    }

    @Async
    public void publishKycStatusChanged(KycStatusChangedEvent event) {
        publish("kyc.status.changed", event);
    }

    @Async
    public void publishPaymentCompleted(PaymentCompletedEvent event) {
        publish("payment.completed", event);
    }

    @Async
    public void publishPaymentFailed(PaymentFailedEvent event) {
        publish("payment.failed", event);
    }

    @Async
    public void publishLowBalance(LowBalanceEvent event) {
        publish("wallet.low.balance", event);
    }

    @Async
    public void publishBillGenerated(BillGeneratedEvent event) {
        publish("bill.generated", event);
    }

    private void publish(String routingKey, Object event) {
        try {
            rabbitTemplate.convertAndSend(EXCHANGE, routingKey, event);
            log.debug("Published event [{}] with routing key [{}]",
                    event.getClass().getSimpleName(), routingKey);
        } catch (Exception ex) {
            log.error("Failed to publish event [{}] with routing key [{}]: {}",
                    event.getClass().getSimpleName(), routingKey, ex.getMessage(), ex);
        }
    }
}