package com.agent.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Agent Platform Gateway 启动类。
 *
 * 说明：doc 00-overview 规划基于 Spring Cloud Alibaba Nacos 注册中心，
 * 在未集成 Nacos 时 @EnableDiscoveryClient 会被 spring-cloud-commons 忽略，
 * 故此处先不显式声明；当引入 spring-cloud-starter-alibaba-nacos-discovery 后
 * 自动注册会生效（Spring Cloud 2023.0+ 默认开启自动注册）。
 */
@SpringBootApplication
@ConfigurationPropertiesScan(basePackages = "com.agent.gateway")
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
