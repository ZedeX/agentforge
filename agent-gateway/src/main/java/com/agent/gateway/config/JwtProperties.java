package com.agent.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gateway.jwt")
public class JwtProperties {

    private String secret;
    private String issuer = "agent-platform";
    private long ttlMinutes = 60L;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public long getTtlMinutes() {
        return ttlMinutes;
    }

    public void setTtlMinutes(long ttlMinutes) {
        this.ttlMinutes = ttlMinutes;
    }
}
