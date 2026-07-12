package com.observe.order;

import com.observe.common.tracing.ObservabilityDemoRunner;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

@SpringBootApplication
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }

    @Bean
    ObservabilityDemoRunner observabilityDemoRunner() {
        return new ObservabilityDemoRunner("OrderService");
    }

    @Bean
    RestClient.Builder restClientBuilder(ObservationRegistry observationRegistry) {
        return RestClient.builder().observationRegistry(observationRegistry);
    }

    @Bean
    RestClient paymentClient(RestClient.Builder builder, @Value("${services.payment.url}") String paymentUrl) {
        return builder
                .baseUrl(paymentUrl)
                .build();
    }
}
