package com.agent.observability.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * agent-observability configuration properties.
 *
 * <p>Prefix {@code observability}, maps to {@code observability.*} in application.yml.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "observability")
public class ObservabilityProperties {

    /** Trace query configuration. */
    private Trace trace = new Trace();

    /** Metrics configuration. */
    private Metrics metrics = new Metrics();

    /** Health check configuration. */
    private Health health = new Health();

    @Getter
    @Setter
    public static class Trace {
        /** Maximum number of trace results returned by a query. */
        private int maxResults = 100;
    }

    @Getter
    @Setter
    public static class Metrics {
        /** Default granularity for metrics aggregation: 1m, 5m, 1h, 1d. */
        private String granularity = "1m";
    }

    @Getter
    @Setter
    public static class Health {
        /** Timeout for health check requests in milliseconds. */
        private int checkTimeout = 5000;
    }
}
