package com.agent.memory.api;

import com.agent.memory.model.MemoryRecord;
import com.agent.memory.model.MemoryTopic;

import java.util.List;

/**
 * Memory distiller port (F12.D7 + Plan 03 T4).
 *
 * <p>Exposes two distillation paths:
 * <ul>
 *   <li>{@link #distill(String, String, List)} — Plan 03 T4 design: distill a batch of
 *       ACTIVE {@link MemoryRecord} for the same (tenantId, topic) into a single
 *       DISTILLED record. Source records are archived atomically.</li>
 *   <li>{@link #distill(MemoryTopic)} — F12 skeleton backward-compatible path:
 *       template-string summary, no model call, no persistence.</li>
 * </ul>
 */
public interface MemoryDistiller {

    /**
     * Distill a batch of ACTIVE memories for the same tenant + topic.
     *
     * <p>Plan 03 T4 behavior:
     * <ol>
     *   <li>Skips if {@code activeRecords} size &lt; {@code memory.distill.triggerCount} (default 20).</li>
     *   <li>Builds distillation prompt from all source contents.</li>
     *   <li>Calls {@link com.agent.memory.gateway.ModelGatewayClient#chat} to generate summary.</li>
     *   <li>Creates a new DISTILLED {@link MemoryRecord} with aggregated importance.</li>
     *   <li>Archives (sets status = ARCHIVED) all source ACTIVE records.</li>
     *   <li>Persists the new DISTILLED record.</li>
     * </ol>
     *
     * @param tenantId      tenant id
     * @param topic         memory topic
     * @param activeRecords ACTIVE memories to distill (must share tenantId + topic)
     * @return the new DISTILLED record, or {@code null} if skipped (below threshold / null / empty)
     * @throws RuntimeException if the model gateway call fails (source ACTIVE state is preserved)
     */
    MemoryRecord distill(String tenantId, String topic, List<MemoryRecord> activeRecords);

    /**
     * Distill a topic: generate summary + archive originals (F12.D7 backward-compatible).
     *
     * <p>Triggered when same-topic fragments &gt;= 5.
     * Compression ratio should be &gt; 0.5 (50%).</p>
     *
     * @return distilled topic with summary + compression ratio.
     */
    MemoryTopic distill(MemoryTopic topic);
}
