package com.agent.knowledge.api.impl;

import com.agent.knowledge.enums.DocumentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DocumentParserImpl unit tests (doc 07-knowledge §4).
 */
@DisplayName("DocumentParserImpl 文档解析器")
class DocumentParserImplTest {

    private final DocumentParserImpl parser = new DocumentParserImpl();

    @Test
    @DisplayName("TEXT 类型原样返回, null content 返回空串")
    void should_ReturnAsIs_When_TypeText() {
        assertThat(parser.parse("Hello World", DocumentType.TEXT)).isEqualTo("Hello World");
        assertThat(parser.parse(null, DocumentType.TEXT)).isEmpty();
    }

    @Test
    @DisplayName("null type 兜底为 TEXT")
    void should_DefaultToText_When_TypeNull() {
        assertThat(parser.parse("hello", null)).isEqualTo("hello");
    }

    @Test
    @DisplayName("MARKDOWN 去除标题/加粗/斜体/代码/链接标记")
    void should_StripMarkdown_When_TypeMarkdown() {
        String md = "# Title\n**bold** and *italic* and `code` [link](http://x)";
        String result = parser.parse(md, DocumentType.MARKDOWN);
        assertThat(result).contains("Title");
        assertThat(result).contains("bold");
        assertThat(result).contains("italic");
        assertThat(result).contains("code");
        assertThat(result).contains("link");
        assertThat(result).doesNotContain("http://x");
        assertThat(result).doesNotContain("# ");
        assertThat(result).doesNotContain("**");
    }

    @Test
    @DisplayName("HTML 去除所有标签")
    void should_StripHtml_When_TypeHtml() {
        String html = "<html><body><h1>Title</h1><p>Content</p></body></html>";
        String result = parser.parse(html, DocumentType.HTML);
        assertThat(result).doesNotContain("<");
        assertThat(result).doesNotContain(">");
        assertThat(result).contains("Title");
        assertThat(result).contains("Content");
    }

    @Test
    @DisplayName("PDF 返回占位文本含字节数")
    void should_ReturnPlaceholder_When_TypePdf() {
        String result = parser.parse("binary-content", DocumentType.PDF);
        assertThat(result).startsWith("[pdf-placeholder]");
        assertThat(result).contains("bytes");
    }

    @Test
    @DisplayName("detectType 识别 HTML / MARKDOWN / TEXT / UNKNOWN")
    void should_DetectType_When_ContentAnalyzed() {
        assertThat(parser.detectType("<html><body>x</body></html>")).isEqualTo(DocumentType.HTML);
        assertThat(parser.detectType("# Markdown Title")).isEqualTo(DocumentType.MARKDOWN);
        assertThat(parser.detectType("Some **bold** text")).isEqualTo(DocumentType.MARKDOWN);
        assertThat(parser.detectType("Plain text content")).isEqualTo(DocumentType.TEXT);
        assertThat(parser.detectType("")).isEqualTo(DocumentType.UNKNOWN);
        assertThat(parser.detectType(null)).isEqualTo(DocumentType.UNKNOWN);
    }

    @Test
    @DisplayName("DocumentType.fromCode 解析已知 code 与默认值 (exercises fromCode)")
    void should_ParseFromCode_When_DocumentTypeFromCodeCalled() {
        assertThat(DocumentType.fromCode("text")).isEqualTo(DocumentType.TEXT);
        assertThat(DocumentType.fromCode("MARKDOWN")).isEqualTo(DocumentType.MARKDOWN);
        assertThat(DocumentType.fromCode("html")).isEqualTo(DocumentType.HTML);
        assertThat(DocumentType.fromCode("pdf")).isEqualTo(DocumentType.PDF);
        // null/empty defaults to TEXT
        assertThat(DocumentType.fromCode(null)).isEqualTo(DocumentType.TEXT);
        assertThat(DocumentType.fromCode("")).isEqualTo(DocumentType.TEXT);
        // unrecognized defaults to UNKNOWN
        assertThat(DocumentType.fromCode("docx")).isEqualTo(DocumentType.UNKNOWN);
    }
}
