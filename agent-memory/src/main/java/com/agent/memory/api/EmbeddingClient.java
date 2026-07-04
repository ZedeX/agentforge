package com.agent.memory.api;

import com.agent.memory.model.EmbeddingVector;

import java.util.List;

/**
 * Embedding client port (F12.D5: write-time vectorization, bge-large-zh 1024 dim).
 *
 * <p>对齐 doc 04-memory §7.5：
 * <ul>
 *   <li>{@link #embed(String, String)} 单条文本嵌入，返回 1024 维 float[]</li>
 *   <li>{@link #embedBatch(List, String)} 批量嵌入，返回 List&lt;float[]&gt;</li>
 *   <li>{@link #embed(String)} 旧方法（无 tenantId），包装为 {@link EmbeddingVector}，
 *       供 {@code LongTermMemoryWriter} 等已有调用方使用</li>
 * </ul>
 */
public interface EmbeddingClient {

    /**
     * 旧版嵌入方法（向后兼容）：返回 {@link EmbeddingVector} 包装。
     *
     * <p>实现应委托给 {@link #embed(String, String)}，tenantId 传 null 或默认值。
     * 仅供已有调用方（如 {@code LongTermMemoryWriterImpl}）使用，新代码请用
     * {@link #embed(String, String)}。
     */
    EmbeddingVector embed(String text);

    /**
     * 单条文本嵌入：调用 agent-model-gateway /v1/embeddings 接口，返回 1024 维 float[]。
     *
     * @param text     待嵌入文本（null 或空字符串抛 IllegalArgumentException）
     * @param tenantId 租户 ID（写入 X-Tenant-Id 请求头，可为 null）
     * @return 1024 维 float[]
     */
    float[] embed(String text, String tenantId);

    /**
     * 批量文本嵌入：单次 HTTP 调用嵌入多条文本。
     *
     * @param texts    文本列表（空列表返回空 List，null 抛 IllegalArgumentException）
     * @param tenantId 租户 ID
     * @return List&lt;float[]&gt;，顺序与输入一致
     */
    List<float[]> embedBatch(List<String> texts, String tenantId);
}
