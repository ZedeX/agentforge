package com.agent.riskcontrol;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Agent Risk Control Spring Boot entrypoint.
 *
 * <p>HTTP 8102 / gRPC 9102 (horizontal service).
 * Content safety, permission checking, compliance audit.</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan("com.agent.riskcontrol.config")
public class RiskControlApplication {

    public static void main(String[] args) {
        SpringApplication.run(RiskControlApplication.class, args);
    }
}
