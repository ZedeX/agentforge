package com.agent.riskcontrol.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * agent-risk-control configuration properties.
 *
 * <p>Prefix {@code risk-control}, maps to {@code risk-control.*} in application.yml.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "risk-control")
public class RiskControlProperties {

    /** Content safety configuration. */
    private Content content = new Content();

    /** Permission configuration. */
    private Permission permission = new Permission();

    /** Audit configuration. */
    private Audit audit = new Audit();

    @Getter
    @Setter
    public static class Content {
        /** Sensitive words to detect in content. */
        private List<String> sensitiveWords = new ArrayList<>(List.of("绝对", "100%", "保证"));

        /** Whether to enable PII detection. */
        private boolean enablePiiDetection = true;

        /** Whether to enable injection detection (SQL / prompt injection). */
        private boolean enableInjectionDetection = true;
    }

    @Getter
    @Setter
    public static class Permission {
        /** Default action when no explicit rule matches: "allow" or "deny". */
        private String defaultAction = "deny";
    }

    @Getter
    @Setter
    public static class Audit {
        /** Whether audit logging is enabled. */
        private boolean enabled = true;
    }
}
