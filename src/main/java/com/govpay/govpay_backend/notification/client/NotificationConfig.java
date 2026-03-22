package com.govpay.govpay_backend.notification.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class NotificationConfig {

    @Value("${govpay.notification.service-url}")
    private String notificationServiceUrl;

    @Bean
    public RestClient restClient() {
        return RestClient.builder()
                .baseUrl(notificationServiceUrl)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
}