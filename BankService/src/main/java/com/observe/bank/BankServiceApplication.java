package com.observe.bank;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.random.RandomGenerator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
public class BankServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(BankServiceApplication.class, args);
    }

    @RestController
    static class BankController {

        @PostMapping(value = "/bank/transfers", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
        BankResponse settleTransfer(@RequestBody BankTransferRequest request) throws InterruptedException {
            int jitterMs = RandomGenerator.getDefault().nextInt(200, 1400);
            Thread.sleep(jitterMs);

            String transactionId = "bank-" + UUID.randomUUID();
            return new BankResponse(transactionId, "SETTLED", jitterMs, Instant.now());
        }
    }

    record BankTransferRequest(String customerId, String orderId, BigDecimal amount, String currency) {
    }

    record BankResponse(String transactionId, String status, int jitterMs, Instant settledAt) {
    }
}
