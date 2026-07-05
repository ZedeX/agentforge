package com.agent.tool.engine.recall;

import com.agent.tool.engine.model.ToolRecallResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T10 {@link ToolSemanticRecallerImpl} unit tests.
 *
 * <p>Uses Mockito to mock {@link MemoryServiceClient} and
 * {@link KeywordFallbackRecaller}, verifying the orchestration logic:
 * memory recall → importance filter → score sort → topK, with keyword
 * fallback on UNAVAILABLE / DEADLINE_EXCEEDED.</p>
 */
@ExtendWith(MockitoExtension.class)
class ToolSemanticRecallerImplTest {

    @Mock
    private MemoryServiceClient memoryClient;

    @Mock
    private KeywordFallbackRecaller keywordFallback;

    private ToolSemanticRecallerImpl recaller;

    @BeforeEach
    void setUp() {
        recaller = new ToolSemanticRecallerImpl(memoryClient, keywordFallback);
    }

    // ==================== Helpers ====================

    private RecalledMemoryDto memory(String id, String content, String sourceTaskId,
                                      double importance, double relevance) {
        return new RecalledMemoryDto(id, content, "task", sourceTaskId,
                importance, relevance, System.currentTimeMillis());
    }

    private ToolRecallResult keywordResult(String toolId, String name, double score) {
        return new ToolRecallResult(toolId, name, score);
    }

    // ==================== T10 Plan §Red tests ====================

    @Test
    @DisplayName("recall_returnsMemoryHits_whenMemoryServiceUp: 3 memories → size=3")
    void recall_returnsMemoryHits_whenMemoryServiceUp() {
        when(memoryClient.isAvailable()).thenReturn(true);
        when(memoryClient.recallMemories(eq("tenant-A"), anyString(), eq(3)))
                .thenReturn(List.of(
                        memory("m1", "use timeout param for slow API", "task-001", 0.8, 0.9),
                        memory("m2", "cache results for repeat calls", "task-002", 0.7, 0.85),
                        memory("m3", "retry on 503", "task-003", 0.6, 0.8)
                ));

        List<ToolRecallResult> results = recaller.recall(
                "tenant-A", "tool-search", Map.of("q", "test"), 3);

        assertThat(results).hasSize(3);
        verify(keywordFallback, never()).recall(anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("recall_mapsMemoryToRecallResult: content / sourceTaskId / importance / score mapped")
    void recall_mapsMemoryToRecallResult() {
        when(memoryClient.isAvailable()).thenReturn(true);
        when(memoryClient.recallMemories(eq("tenant-A"), anyString(), eq(3)))
                .thenReturn(List.of(
                        memory("m1", "content-text", "task-001", 0.85, 0.92)
                ));

        List<ToolRecallResult> results = recaller.recall(
                "tenant-A", "tool-x", Map.of("k", "v"), 3);

        assertThat(results).hasSize(1);
        ToolRecallResult r = results.get(0);
        assertThat(r.getContent()).isEqualTo("content-text");
        assertThat(r.getSourceTaskId()).isEqualTo("task-001");
        assertThat(r.getImportance()).isEqualTo(0.85);
        assertThat(r.getScore()).isEqualTo(0.92);
    }

    @Test
    @DisplayName("recall_filtersByImportanceBelowThreshold: importance < 0.4 filtered out")
    void recall_filtersByImportanceBelowThreshold() {
        when(memoryClient.isAvailable()).thenReturn(true);
        when(memoryClient.recallMemories(eq("tenant-A"), anyString(), eq(5)))
                .thenReturn(List.of(
                        memory("m1", "high", "task-1", 0.9, 0.9),
                        memory("m2", "low", "task-2", 0.3, 0.8),   // filtered
                        memory("m3", "border", "task-3", 0.39, 0.7), // filtered
                        memory("m4", "ok", "task-4", 0.5, 0.6)
                ));

        List<ToolRecallResult> results = recaller.recall(
                "tenant-A", "tool-x", Map.of(), 5);

        assertThat(results).hasSize(2);
        assertThat(results).allSatisfy(r ->
                assertThat(r.getImportance()).isGreaterThanOrEqualTo(0.4));
    }

    @Test
    @DisplayName("recall_sortsByScoreDesc: results sorted by score descending")
    void recall_sortsByScoreDesc() {
        when(memoryClient.isAvailable()).thenReturn(true);
        when(memoryClient.recallMemories(eq("tenant-A"), anyString(), eq(5)))
                .thenReturn(List.of(
                        memory("m1", "low-score", "task-1", 0.8, 0.5),
                        memory("m2", "high-score", "task-2", 0.8, 0.95),
                        memory("m3", "mid-score", "task-3", 0.8, 0.7)
                ));

        List<ToolRecallResult> results = recaller.recall(
                "tenant-A", "tool-x", Map.of(), 5);

        assertThat(results).hasSize(3);
        assertThat(results.get(0).getScore()).isGreaterThanOrEqualTo(results.get(1).getScore());
        assertThat(results.get(1).getScore()).isGreaterThanOrEqualTo(results.get(2).getScore());
        assertThat(results.get(0).getScore()).isEqualTo(0.95);
    }

    @Test
    @DisplayName("recall_topK_default3: recallDefault3 passes topK=3")
    void recall_topK_default3() {
        when(memoryClient.isAvailable()).thenReturn(true);
        when(memoryClient.recallMemories(anyString(), anyString(), eq(3)))
                .thenReturn(List.of(
                        memory("m1", "c1", "t1", 0.8, 0.9),
                        memory("m2", "c2", "t2", 0.7, 0.8),
                        memory("m3", "c3", "t3", 0.6, 0.7)
                ));

        List<ToolRecallResult> results = recaller.recallDefault3(
                "tenant-A", "tool-x", Map.of());

        assertThat(results).hasSize(3);
        verify(memoryClient, times(1)).recallMemories(anyString(), anyString(), eq(3));
    }

    @Test
    @DisplayName("recall_fallsBackToKeywordMatch_whenMemoryServiceDown: UNAVAILABLE → fallback")
    void recall_fallsBackToKeywordMatch_whenMemoryServiceDown() {
        when(memoryClient.isAvailable()).thenReturn(true);
        when(memoryClient.recallMemories(anyString(), anyString(), anyInt()))
                .thenThrow(new MemoryServiceException("UNAVAILABLE",
                        "memory service connection refused"));
        when(keywordFallback.recall(eq("tenant-A"), eq("tool-x"), any(), eq(3)))
                .thenReturn(List.of(keywordResult("tool-x", "search-tool", 0.6)));

        List<ToolRecallResult> results = recaller.recall(
                "tenant-A", "tool-x", Map.of("q", "test"), 3);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getToolId()).isEqualTo("tool-x");
        verify(keywordFallback, times(1)).recall(eq("tenant-A"), eq("tool-x"), any(), eq(3));
    }

    @Test
    @DisplayName("recall_fallsBackToKeywordMatch_whenMemoryServiceTimesOut: DEADLINE_EXCEEDED → fallback")
    void recall_fallsBackToKeywordMatch_whenMemoryServiceTimesOut() {
        when(memoryClient.isAvailable()).thenReturn(true);
        when(memoryClient.recallMemories(anyString(), anyString(), anyInt()))
                .thenThrow(new MemoryServiceException("DEADLINE_EXCEEDED",
                        "memory service timed out after 2000ms"));
        when(keywordFallback.recall(eq("tenant-A"), eq("tool-y"), any(), eq(3)))
                .thenReturn(List.of(keywordResult("tool-y", "fallback-tool", 0.5)));

        List<ToolRecallResult> results = recaller.recall(
                "tenant-A", "tool-y", Map.of(), 3);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getToolId()).isEqualTo("tool-y");
        verify(keywordFallback, times(1)).recall(eq("tenant-A"), eq("tool-y"), any(), eq(3));
    }

    @Test
    @DisplayName("recall_returnsEmpty_whenNoHits: no memory hits + no keyword hits → empty list")
    void recall_returnsEmpty_whenNoHits() {
        when(memoryClient.isAvailable()).thenReturn(true);
        when(memoryClient.recallMemories(anyString(), anyString(), anyInt()))
                .thenReturn(List.of());
        when(keywordFallback.recall(anyString(), anyString(), any(), anyInt()))
                .thenReturn(List.of());

        List<ToolRecallResult> results = recaller.recall(
                "tenant-empty", "tool-none", Map.of(), 3);

        assertThat(results).isEmpty();
    }

    // ==================== Supplementary tests ====================

    @Test
    @DisplayName("recall_fallsBackToKeyword_whenMemoryClientNull: no bean → keyword only")
    void recall_fallsBackToKeyword_whenMemoryClientNull() {
        ToolSemanticRecallerImpl noMemRecaller =
                new ToolSemanticRecallerImpl(null, keywordFallback);
        when(keywordFallback.recall(eq("tenant-A"), eq("tool-x"), any(), eq(3)))
                .thenReturn(List.of(keywordResult("tool-x", "name", 0.7)));

        List<ToolRecallResult> results = noMemRecaller.recall(
                "tenant-A", "tool-x", Map.of("q", "test"), 3);

        assertThat(results).hasSize(1);
        verify(keywordFallback, times(1)).recall(eq("tenant-A"), eq("tool-x"), any(), eq(3));
    }

    @Test
    @DisplayName("recall_respectsTopK: more hits than topK → only topK returned")
    void recall_respectsTopK() {
        when(memoryClient.isAvailable()).thenReturn(true);
        when(memoryClient.recallMemories(anyString(), anyString(), eq(2)))
                .thenReturn(List.of(
                        memory("m1", "c1", "t1", 0.9, 0.9),
                        memory("m2", "c2", "t2", 0.8, 0.8),
                        memory("m3", "c3", "t3", 0.7, 0.7),
                        memory("m4", "c4", "t4", 0.6, 0.6)
                ));

        List<ToolRecallResult> results = recaller.recall(
                "tenant-A", "tool-x", Map.of(), 2);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getScore()).isGreaterThanOrEqualTo(results.get(1).getScore());
    }

    @Test
    @DisplayName("recall_topKZero_returnsEmpty: topK<=0 → empty list")
    void recall_topKZero_returnsEmpty() {
        List<ToolRecallResult> results = recaller.recall(
                "tenant-A", "tool-x", Map.of(), 0);
        assertThat(results).isEmpty();
    }
}
