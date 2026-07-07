package com.agent.quality.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * agent-quality 配置属性（对齐 doc 11-detail-flow F9, PRD §三(一)2 / §四(二)4）。
 *
 * <p>前缀 {@code quality}，映射 application.yml 中 {@code quality.*} 配置项。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "quality")
public class QualityProperties {

    /** L4 校验配置。 */
    private L4 l4 = new L4();

    /** Badcase 归集配置。 */
    private Badcase badcase = new Badcase();

    /** 人工审核队列配置。 */
    private Review review = new Review();

    /** L4 校验默认执行层级配置。 */
    private Validation validation = new Validation();

    @Getter
    @Setter
    public static class L4 {
        /** L4-2 事实一致性 cosine_sim 阈值（默认 0.75）。 */
        private double consistencyThreshold = 0.75;
        /** L4-3 强模型终审 overall 评分阈值（默认 0.7）。 */
        private double auditThreshold = 0.7;
        /** L4 最大重试次数（默认 2）。 */
        private int maxRetry = 2;
    }

    @Getter
    @Setter
    public static class Badcase {
        /** 推送人工审核队列的 severityScore 阈值（默认 0.8）。 */
        private double reviewThreshold = 0.8;
        /** Badcase ID 自动前缀（默认 "bc-"）。 */
        private String autoIdPrefix = "bc-";
    }

    @Getter
    @Setter
    public static class Review {
        /** 审核队列分页默认大小（默认 20）。 */
        private int defaultPageSize = 20;
        /** 推送人工审核队列的 severity 阈值（默认 0.8）。 */
        private double severityThreshold = 0.8;
    }

    @Getter
    @Setter
    public static class Validation {
        /** 默认校验层级列表（默认 hard, consistency, audit）。 */
        private List<String> defaultLayers = new ArrayList<>(List.of("hard", "consistency", "audit"));
    }
}
