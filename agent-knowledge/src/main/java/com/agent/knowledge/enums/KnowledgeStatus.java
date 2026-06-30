package com.agent.knowledge.enums;

/**
 * Knowledge base lifecycle status (doc 07-knowledge §3.2, doc 01-database §7.1).
 *
 * <p>CREATING: 知识库创建中, Milvus collection 初始化中
 * READY: 知识库就绪, 可 ingest / search
 * UPDATING: 文档导入中, 短暂阻塞 search
 * ERROR: 导入失败 / 向量化失败, 需人工介入
 * DELETED: 软删除, 不再可见但保留 Milvus collection 待清理</p>
 */
public enum KnowledgeStatus {

    CREATING("creating", "创建中"),
    READY("ready", "就绪"),
    UPDATING("updating", "更新中"),
    ERROR("error", "异常"),
    DELETED("deleted", "已删除");

    private final String code;
    private final String description;

    KnowledgeStatus(String code, String description) {
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
     * @param code status code (creating / ready / updating / error / deleted)
     * @return KnowledgeStatus enum, default READY if null/empty, default READY if unrecognized
     */
    public static KnowledgeStatus fromCode(String code) {
        if (code == null || code.isEmpty()) {
            return READY;
        }
        String lower = code.toLowerCase();
        for (KnowledgeStatus s : values()) {
            if (s.code.equals(lower)) {
                return s;
            }
        }
        return READY;
    }
}
