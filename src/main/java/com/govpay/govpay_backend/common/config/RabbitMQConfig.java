package com.govpay.govpay_backend.common.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    // ── Exchange ──────────────────────────────────────────────────────────────

    @Bean
    public TopicExchange govpayExchange() {
        return ExchangeBuilder
                .topicExchange("govpay.events")
                .durable(true)
                .build();
    }

    // ── Queues ────────────────────────────────────────────────────────────────

    @Bean
    public Queue notificationQueue() {
        return QueueBuilder
                .durable("govpay.notification.queue")
                .withArgument("x-dead-letter-exchange", "govpay.events.dlx")
                .withArgument("x-dead-letter-routing-key", "dead.notification")
                .build();
    }

    @Bean
    public Queue walletEventsQueue() {
        return QueueBuilder
                .durable("govpay.wallet.events.queue")
                .withArgument("x-dead-letter-exchange", "govpay.events.dlx")
                .withArgument("x-dead-letter-routing-key", "dead.wallet")
                .build();
    }

    // ── Dead Letter Exchange & Queue ──────────────────────────────────────────

    @Bean
    public DirectExchange deadLetterExchange() {
        return ExchangeBuilder
                .directExchange("govpay.events.dlx")
                .durable(true)
                .build();
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable("govpay.dead.letter.queue").build();
    }

    @Bean
    public Binding deadLetterNotificationBinding() {
        return BindingBuilder
                .bind(deadLetterQueue())
                .to(deadLetterExchange())
                .with("dead.notification");
    }

    @Bean
    public Binding deadLetterWalletBinding() {
        return BindingBuilder
                .bind(deadLetterQueue())
                .to(deadLetterExchange())
                .with("dead.wallet");
    }

    // ── Bindings ──────────────────────────────────────────────────────────────

    @Bean
    public Binding notificationUserRegisteredBinding() {
        return BindingBuilder.bind(notificationQueue())
                .to(govpayExchange())
                .with("user.#");
    }

    @Bean
    public Binding notificationPaymentBinding() {
        return BindingBuilder.bind(notificationQueue())
                .to(govpayExchange())
                .with("payment.#");
    }

    @Bean
    public Binding notificationWalletBinding() {
        return BindingBuilder.bind(notificationQueue())
                .to(govpayExchange())
                .with("wallet.#");
    }

    @Bean
    public Binding notificationBillBinding() {
        return BindingBuilder.bind(notificationQueue())
                .to(govpayExchange())
                .with("bill.#");
    }

    @Bean
    public Binding walletEventsBinding() {
        return BindingBuilder.bind(walletEventsQueue())
                .to(govpayExchange())
                .with("payment.#");
    }

    // ── Serialization ─────────────────────────────────────────────────────────

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        // Enable publisher confirms for reliability
        template.setMandatory(true);
        return template;
    }
}