package com.agent.memory.api;

import com.agent.memory.model.MemoryRecord;
import com.agent.memory.scorer.ImportanceResult;
import com.agent.memory.scorer.ScoringContext;

/**
 * Importance scorer port (F12.D3 + Plan 03 T7).
 *
 * <p>Exposes two scoring paths:
 * <ul>
 *   <li>{@link #score(MemoryRecord, ScoringContext)} — Plan 03 T7 design: 5-dimension
 *       weighted scoring (emotionIntensity 0.20 + frequency 0.25 + novelty 0.20 +
 *       taskRelevance 0.25 + timeDecay 0.10) returning {@link ImportanceResult}
 *       with score + level + dimension breakdown.</li>
 *   <li>{@link #score(int, double, double)} — F12 skeleton backward-compatible:
 *       3-dimension weighted scoring (relevance 0.4 + recency 0.3 + freq 0.3)
 *       returning plain double.</li>
 * </ul>
 */
public interface ImportanceScorer {

    /**
     * 5-dimension weighted scoring (Plan 03 T7).
     *
     * <p>Dimensions and weights (doc 04-memory §4.2):
     * <ol>
     *   <li>emotionIntensity (0.20) — emotional strength, from ScoringContext or default 0.5</li>
     *   <li>frequency (0.25) — accessCount/10 saturated to 1.0</li>
     *   <li>novelty (0.20) — 1 - cosine similarity with reference vectors</li>
     *   <li>taskRelevance (0.25) — taskKeywords ∩ record.keywords hit rate</li>
     *   <li>timeDecay (0.10) — exp(-Δt/30d)</li>
     * </ol>
     *
     * @param record  memory record to score
     * @param context scoring context (task keywords, reference vectors, base time)
     * @return result with score [0,1], level (HIGH/MEDIUM/LOW), and dimension breakdown
     */
    ImportanceResult score(MemoryRecord record, ScoringContext context);

    /**
     * Legacy 3-dimension scoring (F12.D3 backward-compatible).
     *
     * @param accessCount recall count
     * @param recency     recency score [0,1]
     * @param relevance   relevance score [0,1]
     * @return weighted score in [0,1]
     */
    double score(int accessCount, double recency, double relevance);
}
