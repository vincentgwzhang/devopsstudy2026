package com.observe.bank;

import com.observe.common.tracing.ObservabilityDemoRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class BankServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(BankServiceApplication.class, args);
    }

    @Bean
    ObservabilityDemoRunner observabilityDemoRunner() {
        return new ObservabilityDemoRunner("BankService");
    }
}
