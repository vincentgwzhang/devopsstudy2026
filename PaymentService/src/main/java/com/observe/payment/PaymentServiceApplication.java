package com.observe.payment;

import com.observe.common.tracing.ObservabilityDemoRunner;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

@SpringBootApplication
public class PaymentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }

    @Bean
    ObservabilityDemoRunner observabilityDemoRunner() {
        return new ObservabilityDemoRunner("PaymentService");
    }

    @Bean
    RestClient.Builder restClientBuilder(ObservationRegistry observationRegistry) {
        return RestClient.builder().observationRegistry(observationRegistry);
    }

    @Bean
    RestClient bankClient(RestClient.Builder builder, @Value("${services.bank.url}") String bankUrl) {
        return builder
                .baseUrl(bankUrl)
                .build();
    }
}
