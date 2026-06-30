package com.agent.knowledge.api.impl;

import com.agent.knowledge.enums.ChunkStrategyType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ChunkSplitterImpl unit tests (doc 07-knowledge §4.1).
 */
@DisplayName("ChunkSplitterImpl 分块切分器")
class ChunkSplitterImplTest {

    private final ChunkSplitterImpl splitter = new ChunkSplitterImpl();

    @Test
    @DisplayName("null 或空 content 返回空列表")
    void should_ReturnEmpty_When_ContentNullOrEmpty() {
        assertThat(splitter.split(null, ChunkStrategyType.TOKEN, 512, 64)).isEmpty();
        assertThat(splitter.split("", ChunkStrategyType.TOKEN, 512, 64)).isEmpty();
    }

    @Test
    @DisplayName("TOKEN 策略: 长文本按 maxTokens 切分, 含 overlap")
    void should_SplitByToken_When_StrategyToken() {
        // 1 token = 4 chars, maxTokens=10 → 40 chars per chunk
        String content = "a".repeat(100);
        List<String> chunks = splitter.split(content, ChunkStrategyType.TOKEN, 10, 2);
        assertThat(chunks).isNotEmpty();
        assertThat(chunks.size()).isGreaterThan(1);
        // Each chunk should be <= 40 chars
        for (String chunk : chunks) {
            assertThat(chunk.length()).isLessThanOrEqualTo(40);
        }
    }

    @Test
    @DisplayName("TOKEN 策略: 短文本 (小于 maxTokens) 返回单 chunk")
    void should_ReturnSingleChunk_When_ContentShorterThanMaxTokens() {
        String content = "short text";
        List<String> chunks = splitter.split(content, ChunkStrategyType.TOKEN, 512, 64);
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0)).isEqualTo("short text");
    }

    @Test
    @DisplayName("PARAGRAPH 策略: 按双换行分段, 合并短段落")
    void should_SplitByParagraph_When_StrategyParagraph() {
        String content = "Para one.\n\nPara two.\n\nPara three.";
        List<String> chunks = splitter.split(content, ChunkStrategyType.PARAGRAPH, 512, 0);
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0)).contains("Para one");
        assertThat(chunks.get(0)).contains("Para two");
        assertThat(chunks.get(0)).contains("Para three");
    }

    @Test
    @DisplayName("PARAGRAPH 策略: 超长段落拆分为多 chunk")
    void should_SplitLongParagraph_When_StrategyParagraphExceedsMax() {
        String longPara = "a".repeat(3000);
        String content = longPara + "\n\nshort para";
        List<String> chunks = splitter.split(content, ChunkStrategyType.PARAGRAPH, 10, 0);
        assertThat(chunks.size()).isGreaterThan(1);
    }

    @Test
    @DisplayName("FIXED 策略: 按固定字符长度切分, 无 overlap")
    void should_SplitByFixedLength_When_StrategyFixed() {
        String content = "abcdefghij";
        List<String> chunks = splitter.split(content, ChunkStrategyType.FIXED, 3, 0);
        assertThat(chunks).hasSize(4);
        assertThat(chunks.get(0)).isEqualTo("abc");
        assertThat(chunks.get(1)).isEqualTo("def");
        assertThat(chunks.get(2)).isEqualTo("ghi");
        assertThat(chunks.get(3)).isEqualTo("j");
    }

    @Test
    @DisplayName("null strategy 兜底为 TOKEN, maxTokens<=0 默认 512, overlap<0 归零")
    void should_ApplyDefaults_When_ParamsInvalid() {
        List<String> chunks = splitter.split("hello world", null, 0, -1);
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0)).isEqualTo("hello world");
    }

    @Test
    @DisplayName("overlap >= maxTokens 时自动减半避免死循环")
    void should_HalveOverlap_When_OverlapExceedsMaxTokens() {
        String content = "a".repeat(100);
        // maxTokens=10 (40 chars), overlap=10 (40 chars) → overlap halved to 20 chars
        List<String> chunks = splitter.split(content, ChunkStrategyType.TOKEN, 10, 10);
        assertThat(chunks.size()).isGreaterThan(1);
    }

    @Test
    @DisplayName("ChunkStrategyType.fromCode 解析已知 code 与默认值 (exercises fromCode)")
    void should_ParseFromCode_When_ChunkStrategyTypeFromCodeCalled() {
        assertThat(ChunkStrategyType.fromCode("token")).isEqualTo(ChunkStrategyType.TOKEN);
        assertThat(ChunkStrategyType.fromCode("PARAGRAPH")).isEqualTo(ChunkStrategyType.PARAGRAPH);
        assertThat(ChunkStrategyType.fromCode("fixed")).isEqualTo(ChunkStrategyType.FIXED);
        // null/empty/unrecognized default to TOKEN
        assertThat(ChunkStrategyType.fromCode(null)).isEqualTo(ChunkStrategyType.TOKEN);
        assertThat(ChunkStrategyType.fromCode("")).isEqualTo(ChunkStrategyType.TOKEN);
        assertThat(ChunkStrategyType.fromCode("sliding")).isEqualTo(ChunkStrategyType.TOKEN);
    }
}
