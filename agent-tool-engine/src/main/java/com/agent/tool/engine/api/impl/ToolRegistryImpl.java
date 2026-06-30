package com.agent.tool.engine.api.impl;

import com.agent.tool.engine.api.ToolRegistry;
import com.agent.tool.engine.model.ToolMeta;
import com.agent.tool.engine.model.ToolSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * F8 工具注册中心实现 (three-layer schema 注册 + 查询)。
 *
 * <p>骨架阶段使用 3 个 ConcurrentHashMap 分别存储 meta / inputSchema / outputSchema,
 * register() 在 toolId 为空时自动生成。生产实现应替换为 RDBMS / 配置中心。</p>
 */
@Component
public class ToolRegistryImpl implements ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistryImpl.class);

    private final Map<String, ToolMeta> metaStore = new ConcurrentHashMap<>();
    private final Map<String, ToolSchema> inputSchemaStore = new ConcurrentHashMap<>();
    private final Map<String, ToolSchema> outputSchemaStore = new ConcurrentHashMap<>();
    private final AtomicLong idSeq = new AtomicLong(0);

    @Override
    public String register(ToolMeta meta, ToolSchema inputSchema, ToolSchema outputSchema) {
        if (meta == null) {
            log.warn("注册工具收到空 meta, 拒绝");
            throw new IllegalArgumentException("meta 不能为空");
        }
        String toolId = meta.getToolId();
        if (toolId == null || toolId.isBlank()) {
            toolId = "tool-" + idSeq.incrementAndGet();
            meta.setToolId(toolId);
        }
        metaStore.put(toolId, meta);
        if (inputSchema != null) {
            inputSchemaStore.put(toolId, inputSchema);
        }
        if (outputSchema != null) {
            outputSchemaStore.put(toolId, outputSchema);
        }
        log.info("注册工具: toolId={}, name={}", toolId, meta.getName());
        return toolId;
    }

    @Override
    public ToolSchema findInputSchema(String toolId) {
        if (toolId == null || toolId.isBlank()) {
            return null;
        }
        return inputSchemaStore.get(toolId);
    }

    @Override
    public ToolMeta findMeta(String toolId) {
        if (toolId == null || toolId.isBlank()) {
            return null;
        }
        return metaStore.get(toolId);
    }

    /** 当前已注册工具数 (供测试 / 监控使用)。 */
    public int size() {
        return metaStore.size();
    }

    /** 注销工具 (供测试 / 运维使用)。 */
    public boolean unregister(String toolId) {
        if (toolId == null) {
            return false;
        }
        boolean removed = metaStore.remove(toolId) != null;
        inputSchemaStore.remove(toolId);
        outputSchemaStore.remove(toolId);
        if (removed) {
            log.info("注销工具: toolId={}", toolId);
        }
        return removed;
    }
}
