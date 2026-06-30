package com.agent.knowledge.enums;

/**
 * Document type for parsing strategy selection (doc 07-knowledge §4.1).
 *
 * <p>TEXT: 纯文本, 无需预处理
 * MARKDOWN: Markdown 文本, 解析时去除标记符号
 * HTML: HTML 文本, 解析时去除标签
 * PDF: PDF 二进制 (skeleton stage 仅占位, 真实解析需 T9)
 * UNKNOWN: 未知类型, 按纯文本处理</p>
 */
public enum DocumentType {

    TEXT("text", "纯文本"),
    MARKDOWN("markdown", "Markdown 文本"),
    HTML("html", "HTML 文本"),
    PDF("pdf", "PDF 文档"),
    UNKNOWN("unknown", "未知类型");

    private final String code;
    private final String description;

    DocumentType(String code, String description) {
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
     * Parse document type from code string (case-insensitive).
     *
     * @param code type code (text / markdown / html / pdf / unknown)
     * @return DocumentType enum, default TEXT if null/empty, default UNKNOWN if unrecognized
     */
    public static DocumentType fromCode(String code) {
        if (code == null || code.isEmpty()) {
            return TEXT;
        }
        String lower = code.toLowerCase();
        for (DocumentType t : values()) {
            if (t.code.equals(lower)) {
                return t;
            }
        }
        return UNKNOWN;
    }
}
