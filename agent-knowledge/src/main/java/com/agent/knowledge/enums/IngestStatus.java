package com.agent.knowledge.enums;

/**
 * Chunk ingestion status (doc 07-knowledge §3.2, doc 08 T8 IngestStatus).
 *
 * <p>PENDING: 分块完成, 等待向量化
 * VECTORIZED: 向量化完成, 已写入 Milvus, 可被检索
 * FAILED: 向量化失败, 需重试或人工介入</p>
 */
public enum IngestStatus {

    PENDING("pending", "待向量化"),
    VECTORIZED("vectorized", "向量化完成"),
    FAILED("failed", "向量化失败");

    private final String code;
    private final String description;

    IngestStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Parse status from code string (case-insensitive).
     *
     * @param code status code (pending / vectorized / failed)
     * @return IngestStatus enum, default PENDING if null/empty, default PENDING if unrecognized
     */
    public static IngestStatus fromCode(String code) {
        if (code == null || code.isEmpty()) {
            return PENDING;
        }
        String lower = code.toLowerCase();
        for (IngestStatus s : values()) {
            if (s.code.equals(lower)) {
                return s;
            }
        }
        return PENDING;
    }
}
