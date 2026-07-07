package com.agent.memory.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * agent-memory 配置属性（对齐 doc 04-memory §13）。
 *
 * <p>前缀 {@code memory}，映射 application.yml 中 {@code memory.*} 配置项。
 * 各子配置（TTL / Recall / Distill / Dedup / Milvus）以静态内部类承载。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "memory")
public class MemoryProperties {

    /** TTL 状态流转策略。 */
    private Ttl ttl = new Ttl();
    /** Recall 召回参数。 */
    private Recall recall = new Recall();
    /** Distill 蒸馏触发条件。 */
    private Distill distill = new Distill();
    /** Dedup 去重阈值。 */
    private Dedup dedup = new Dedup();
    /** Milvus 向量库配置。 */
    private Milvus milvus = new Milvus();
    /** T4: 模型网关 gRPC 客户端开关。 */
    private ModelGateway modelGateway = new ModelGateway();
    /** T5: Embedding 客户端配置（HTTP 调用 agent-model-gateway /v1/embeddings）。 */
    private Embedding embedding = new Embedding();

    @Getter
    @Setter
    public static class Ttl {
        /** RAW→ACTIVE：立即（0 秒）。 */
        private String rawToActive = "0";
        /** ACTIVE→DISTILLED：7 天。 */
        private String activeToDistilled = "7d";
        /** DISTILLED→ARCHIVED：30 天。 */
        private String distilledToArchived = "30d";
    }

    @Getter
    @Setter
    public static class Recall {
        /** 召回 topK（默认 10）。 */
        private int topK = 10;
        /** 相似度阈值（默认 0.75）。 */
        private double scoreThreshold = 0.75;
    }

    @Getter
    @Setter
    public static class Distill {
        /** 蒸馏触发条数（同 tenantId + topic 下 ACTIVE 记忆 ≥20 条）。 */
        private int triggerCount = 20;
    }

    @Getter
    @Setter
    public static class Dedup {
        /** 完全相同 hash 阈值（1.0 = 完全一致）。 */
        private double exactThreshold = 1.0;
        /** 余弦相似度高阈值（≥0.95 → 合并）。 */
        private double cosineHigh = 0.95;
        /** 余弦相似度低阈值（0.85~0.95 → 标记关联）。 */
        private double cosineLow = 0.85;
    }

    @Getter
    @Setter
    public static class Milvus {
        /** 是否启用 Milvus（测试环境禁用）。 */
        private boolean enabled = false;
        /** Milvus 主机。 */
        private String host = "localhost";
        /** Milvus 端口。 */
        private int port = 19530;
        /** Collection 名称。 */
        private String collection = "agent_memory_vector";
    }

    @Getter
    @Setter
    public static class ModelGateway {
        /** 是否启用模型网关 gRPC 客户端（测试环境禁用，避免 channel 初始化）。 */
        private boolean enabled = true;
        /** 蒸馏场景标识（对齐 model.proto ChatRequest.scene）。 */
        private String distillScene = "summary";
        /** 蒸馏模型层级（light / middle / strong）。 */
        private String distillTier = "middle";
        /** S-06: gRPC 调用超时时间（毫秒），防止慢依赖拖垮调用链。 */
        private long timeoutMs = 30000;
    }

    /**
     * T5: Embedding 客户端配置。
     *
     * <p>对齐 doc 04-memory §7.2 / §12.3：
     * <ul>
     *   <li>{@code httpEnabled=false}（默认）→ 使用 MockEmbeddingClientImpl（确定性伪向量）</li>
     *   <li>{@code httpEnabled=true}（生产）→ 使用 EmbeddingClientImpl（真实 HTTP 调用）</li>
     * </ul>
     */
    @Getter
    @Setter
    public static class Embedding {
        /** 是否启用真实 HTTP Embedding 客户端（测试环境禁用）。 */
        private boolean httpEnabled = false;
        /** agent-model-gateway 嵌入接口 base URL。 */
        private String baseUrl = "http://localhost:8080";
        /** 嵌入接口路径（OpenAI 兼容）。 */
        private String path = "/v1/embeddings";
        /** 模型名（对齐 doc 04 §7.2：text-embedding-v3，1024 维）。 */
        private String model = "text-embedding-v3";
        /** API Key（Bearer Token，可为空）。 */
        private String apiKey = "";
        /** 连接超时（毫秒，对齐 §12.3：2 秒）。 */
        private int connectTimeoutMs = 2000;
        /** 读取超时（毫秒，对齐 §12.3：10 秒）。 */
        private int readTimeoutMs = 10000;
        /** 最大重试次数（对齐 §12.3：3 次指数退避）。 */
        private int maxRetries = 3;
        /** 重试退避基础间隔（毫秒，对齐 §12.3：100/300/1000）。 */
        private long retryBackoffBaseMs = 100;
        /** 重试退避乘数（每轮乘以此系数）。 */
        private double retryBackoffMultiplier = 3.0;
        /** 是否启用本地缓存。 */
        private boolean cacheEnabled = true;
        /** 缓存 TTL（分钟，对齐 §7.5：1 小时）。 */
        private long cacheTtlMinutes = 60;
        /** 缓存最大条目数。 */
        private int cacheMaxSize = 10000;
    }
}
