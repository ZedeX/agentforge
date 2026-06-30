package com.agent.knowledge.api.impl;

import com.agent.knowledge.api.DocumentParser;
import com.agent.knowledge.enums.DocumentType;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * In-memory document parser (doc 07-knowledge §4).
 *
 * <p>Skeleton stage: regex-based stripping for markdown / html. PDF returns placeholder text.
 * Apache Tika / PDFBox integration deferred to Plan 08 T9.</p>
 */
@Component
public class DocumentParserImpl implements DocumentParser {

    private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");
    private static final Pattern MARKDOWN_HEADER = Pattern.compile("#{1,6}\\s*");
    private static final Pattern MARKDOWN_BOLD = Pattern.compile("\\*\\*([^*]+)\\*\\*");
    private static final Pattern MARKDOWN_ITALIC = Pattern.compile("(?<!\\*)\\*([^*]+)\\*(?!\\*)");
    private static final Pattern MARKDOWN_CODE = Pattern.compile("`([^`]+)`");
    private static final Pattern MARKDOWN_LINK = Pattern.compile("\\[([^]]+)]\\([^)]+\\)");

    @Override
    public String parse(String content, DocumentType type) {
        if (content == null) {
            return "";
        }
        if (type == null) {
            type = DocumentType.TEXT;
        }
        switch (type) {
            case TEXT:
                return content;
            case MARKDOWN:
                return parseMarkdown(content);
            case HTML:
                return parseHtml(content);
            case PDF:
                return "[pdf-placeholder] " + content.length() + " bytes";
            case UNKNOWN:
                return content;
            default:
                return content;
        }
    }

    private String parseMarkdown(String content) {
        String result = content;
        result = MARKDOWN_LINK.matcher(result).replaceAll("$1");
        result = MARKDOWN_BOLD.matcher(result).replaceAll("$1");
        result = MARKDOWN_ITALIC.matcher(result).replaceAll("$1");
        result = MARKDOWN_CODE.matcher(result).replaceAll("$1");
        result = MARKDOWN_HEADER.matcher(result).replaceAll("");
        return result;
    }

    private String parseHtml(String content) {
        return HTML_TAG.matcher(content).replaceAll("");
    }

    @Override
    public DocumentType detectType(String content) {
        if (content == null || content.isEmpty()) {
            return DocumentType.UNKNOWN;
        }
        String trimmed = content.trim();
        if (trimmed.startsWith("<") || trimmed.toLowerCase().contains("<html")) {
            return DocumentType.HTML;
        }
        if (trimmed.contains("# ") || trimmed.contains("**") || trimmed.contains("```")) {
            return DocumentType.MARKDOWN;
        }
        return DocumentType.TEXT;
    }
}
