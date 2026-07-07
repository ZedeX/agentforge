package com.agent.hallucination.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * hallucination-governance 配置属性（对齐 doc 11 F10 六层幻觉治理）。
 *
 * <p>前缀 {@code hallucination}，映射 application.yml 中 {@code hallucination.*} 配置项。
 * 各子配置以静态内部类承载。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "hallucination")
public class HallucinationProperties {

    /** 自检引擎配置。 */
    private SelfCheck selfCheck = new SelfCheck();

    /** RAG 锚定配置。 */
    private RagAnchor ragAnchor = new RagAnchor();

    /** 工具网关守卫配置。 */
    private ToolGuard toolGuard = new ToolGuard();

    /** L4 硬校验配置。 */
    private HardValidation hardValidation = new HardValidation();

    /** 指标追踪配置。 */
    private Metric metric = new Metric();

    @Getter
    @Setter
    public static class SelfCheck {
        /** 是否启用自检引擎（L2）。 */
        private boolean enabled = true;
        /** 自检置信度阈值（低于此值判定为 SUSPECTED）。 */
        private double confidenceThreshold = 0.6;
    }

    @Getter
    @Setter
    public static class RagAnchor {
        /** 是否启用 RAG 锚定（L3）。 */
        private boolean enabled = true;
        /** 锚定得分阈值（低于此值判定为 unanchored）。 */
        private double anchorScoreThreshold = 0.5;
    }

    @Getter
    @Setter
    public static class ToolGuard {
        /** 是否启用工具网关守卫（L5）。 */
        private boolean enabled = true;
    }

    @Getter
    @Setter
    public static class HardValidation {
        /** 是否启用 L4 硬校验。 */
        private boolean enabled = true;
    }

    @Getter
    @Setter
    public static class Metric {
        /** 是否启用指标追踪（L6）。 */
        private boolean enabled = true;
        /** 指标保留天数。 */
        private int retentionDays = 90;
    }
}
