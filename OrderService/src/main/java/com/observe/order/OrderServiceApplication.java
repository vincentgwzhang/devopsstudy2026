package com.observe.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.random.RandomGenerator;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

@SpringBootApplication
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }

    @Bean
    RestClient paymentClient(@Value("${services.payment.url}") String paymentUrl) {
        return RestClient.builder().baseUrl(paymentUrl).build();
    }

    @RestController
    static class OrderController {

        private final RestClient paymentClient;

        OrderController(RestClient paymentClient) {
            this.paymentClient = paymentClient;
        }

        @PostMapping(value = "/orders", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
        OrderResponse createOrder(@RequestBody OrderRequest request) throws InterruptedException {
            int jitterMs = RandomGenerator.getDefault().nextInt(120, 900);
            Thread.sleep(jitterMs);

            String orderId = "ord-" + UUID.randomUUID();
            PaymentResponse payment = paymentClient.post()
                    .uri("/payments")
                    .body(new PaymentRequest(orderId, request.customerId(), request.amount(), request.currency()))
                    .retrieve()
                    .body(PaymentResponse.class);

            return new OrderResponse(orderId, "CONFIRMED", jitterMs, payment, Instant.now());
        }
    }

    record OrderRequest(String customerId, BigDecimal amount, String currency) {
    }

    record PaymentRequest(String orderId, String customerId, BigDecimal amount, String currency) {
    }

    record PaymentResponse(String paymentId, String status, int jitterMs, BankResponse bank, Instant processedAt) {
    }

    record BankResponse(String transactionId, String status, int jitterMs, Instant settledAt) {
    }

    record OrderResponse(String orderId, String status, int jitterMs, PaymentResponse payment, Instant createdAt) {
    }
}
