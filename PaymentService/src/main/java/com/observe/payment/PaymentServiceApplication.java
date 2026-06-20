package com.observe.payment;

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
public class PaymentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }

    @Bean
    RestClient bankClient(@Value("${services.bank.url}") String bankUrl) {
        return RestClient.builder().baseUrl(bankUrl).build();
    }

    @RestController
    static class PaymentController {

        private final RestClient bankClient;

        PaymentController(RestClient bankClient) {
            this.bankClient = bankClient;
        }

        @PostMapping(value = "/payments", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
        PaymentResponse authorizePayment(@RequestBody PaymentRequest request) throws InterruptedException {
            int jitterMs = RandomGenerator.getDefault().nextInt(150, 1100);
            Thread.sleep(jitterMs);

            BankResponse bank = bankClient.post()
                    .uri("/bank/transfers")
                    .body(new BankTransferRequest(request.customerId(), request.orderId(), request.amount(), request.currency()))
                    .retrieve()
                    .body(BankResponse.class);

            return new PaymentResponse("pay-" + UUID.randomUUID(), "AUTHORIZED", jitterMs, bank, Instant.now());
        }
    }

    record PaymentRequest(String orderId, String customerId, BigDecimal amount, String currency) {
    }

    record BankTransferRequest(String customerId, String orderId, BigDecimal amount, String currency) {
    }

    record BankResponse(String transactionId, String status, int jitterMs, Instant settledAt) {
    }

    record PaymentResponse(String paymentId, String status, int jitterMs, BankResponse bank, Instant processedAt) {
    }
}
