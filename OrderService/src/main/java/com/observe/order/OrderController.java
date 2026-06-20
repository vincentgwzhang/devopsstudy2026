package com.observe.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.random.RandomGenerator;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

@RestController
class OrderController {

    private final RestClient paymentClient;
    private final Counter createOrderCounter;

    OrderController(RestClient paymentClient, MeterRegistry meterRegistry) {
        this.paymentClient = paymentClient;
        this.createOrderCounter = Counter.builder("order_controller_create")
                .description("Total number of create order endpoint calls")
                .tag("endpoint", "POST /orders")
                .register(meterRegistry);
    }

    @PostMapping(value = "/orders", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    OrderResponse createOrder(@RequestBody OrderRequest request) throws InterruptedException {
        createOrderCounter.increment();

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
