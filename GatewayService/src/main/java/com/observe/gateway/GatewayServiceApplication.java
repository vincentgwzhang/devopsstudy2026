package com.observe.gateway;

import com.observe.common.tracing.ObservabilityDemoRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class GatewayServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayServiceApplication.class, args);
    }

    @Bean
    ObservabilityDemoRunner observabilityDemoRunner() {
        return new ObservabilityDemoRunner("GatewayService");
    }
}
