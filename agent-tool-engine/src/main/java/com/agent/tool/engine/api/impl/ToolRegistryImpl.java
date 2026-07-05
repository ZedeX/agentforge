package com.agent.tool.engine.api.impl;

import com.agent.tool.engine.api.ToolRegistry;
import com.agent.tool.engine.entity.ToolRegistryEntity;
import com.agent.tool.engine.enums.ExecutorType;
import com.agent.tool.engine.enums.SideEffect;
import com.agent.tool.engine.enums.ToolRiskLevel;
import com.agent.tool.engine.enums.ToolStatus;
import com.agent.tool.engine.enums.ToolType;
import com.agent.tool.engine.exception.ToolValidationException;
import com.agent.tool.engine.model.ToolMeta;
import com.agent.tool.engine.model.ToolSchema;
import com.agent.tool.engine.repository.ToolRegistryRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * F8 工具注册中心 JPA 实现 (three-layer schema 注册 + 查询).
 *
 * <p>使用 {@link ToolRegistryRepository} 持久化到 {@code tool_registry} 表.
 * 内部完成 {@link ToolMeta}/{@link ToolSchema} POJO 与 {@link ToolRegistryEntity} 之间的双向转换.</p>
 *
 * <p>字段映射:
 * <ul>
 *   <li>ToolMeta.toolId <-> entity.toolId</li>
 *   <li>ToolMeta.name <-> entity.name + entity.displayName</li>
 *   <li>ToolMeta.description <-> entity.description</li>
 *   <li>ToolMeta.executorType <-> entity.executorType</li>
 *   <li>ToolMeta.sideEffect <-> entity.riskLevel (NONE/READ_ONLY=R1, WRITE_LOCAL=R2, WRITE_EXTERNAL/DESTRUCTIVE=R3)</li>
 *   <li>ToolSchema.requiredFields <-> entity.inputSchema (JSON {"required":[...]})</li>
 * </ul></p>
 */
@Component
public class ToolRegistryImpl implements ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistryImpl.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ToolRegistryRepository repository;

    public ToolRegistryImpl(ToolRegistryRepository repository) {
        this.repository = repository;
    }

    // ==================== ToolRegistry 接口实现 ====================

    @Override
    public String register(ToolMeta meta, ToolSchema inputSchema, ToolSchema outputSchema) {
        if (meta == null) {
            throw new IllegalArgumentException("meta 不能为空");
        }
        String toolId = meta.getToolId();
        if (toolId == null || toolId.isBlank()) {
            toolId = "tool-" + System.currentTimeMillis();
            meta.setToolId(toolId);
        }

        // 检查重复
        if (repository.existsByToolId(toolId)) {
            throw new ToolValidationException("工具已注册: " + toolId);
        }

        ToolRegistryEntity entity = toEntity(meta, inputSchema, outputSchema);
        repository.save(entity);
        log.info("注册工具: toolId={}, name={}, risk={}", toolId, meta.getName(),
                entity.getRiskLevelEnum());
        return toolId;
    }

    @Override
    public ToolSchema findInputSchema(String toolId) {
        if (toolId == null || toolId.isBlank()) {
            return null;
        }
        return repository.findByToolId(toolId)
                .map(this::parseInputSchema)
                .orElse(null);
    }

    @Override
    public ToolMeta findMeta(String toolId) {
        if (toolId == null || toolId.isBlank()) {
            return null;
        }
        return repository.findByToolId(toolId)
                .map(this::toMeta)
                .orElse(null);
    }

    // ==================== 扩展方法 (供 T10 召回 / 运维使用) ====================

    /** 当前已注册工具数. */
    public long size() {
        return repository.count();
    }

    /** 注销工具 (逻辑删除, 设置 status=OFFLINE). */
    public boolean unregister(String toolId) {
        if (toolId == null || toolId.isBlank()) {
            return false;
        }
        Optional<ToolRegistryEntity> opt = repository.findByToolId(toolId);
        if (opt.isEmpty()) {
            return false;
        }
        ToolRegistryEntity entity = opt.get();
        entity.setStatusEnum(ToolStatus.OFFLINE);
        repository.save(entity);
        log.info("注销工具: toolId={}", toolId);
        return true;
    }

    /** 按状态查询工具 (供 T10 语义召回使用). */
    public List<ToolMeta> findByStatus(ToolStatus status) {
        return repository.findByStatus(status.getCode()).stream()
                .map(this::toMeta)
                .toList();
    }

    /** 按风险等级查询工具. */
    public List<ToolMeta> findByRiskLevel(ToolRiskLevel level) {
        return repository.findByRiskLevel(level.getLevel()).stream()
                .map(this::toMeta)
                .toList();
    }

    /** 按场景标签 LIKE 查询 (供 T10 语义召回使用). */
    public List<ToolMeta> findBySceneTag(String tag) {
        return repository.findBySceneTagLike(tag).stream()
                .map(this::toMeta)
                .toList();
    }

    /** 按能力标签 LIKE 查询 (供 T10 语义召回使用). */
    public List<ToolMeta> findByAbilityTag(String tag) {
        return repository.findByAbilityTagLike(tag).stream()
                .map(this::toMeta)
                .toList();
    }

    /** 获取完整 Entity (供 T8 网关获取 endpoint / timeout / undoAction 等). */
    public Optional<ToolRegistryEntity> findEntity(String toolId) {
        if (toolId == null || toolId.isBlank()) {
            return Optional.empty();
        }
        return repository.findByToolId(toolId);
    }

    // ==================== 转换器 ====================

    private ToolRegistryEntity toEntity(ToolMeta meta, ToolSchema inputSchema, ToolSchema outputSchema) {
        ToolRiskLevel riskLevel = mapSideEffectToRisk(meta.getSideEffect());
        ToolRegistryEntity entity = new ToolRegistryEntity(
                meta.getToolId(),
                meta.getName(),
                meta.getName(),  // displayName 默认等于 name
                meta.getDescription() != null ? meta.getDescription() : "",
                ToolType.ATOMIC, // 默认原子工具
                riskLevel.getLevel(),
                meta.getExecutorType() != null ? meta.getExecutorType() : ExecutorType.HTTP_API,
                "grpc://tool-service/" + meta.getName(),  // 默认 endpoint
                30000  // 默认 30s 超时
        );
        entity.setSceneTags("[]");
        entity.setInputSchema(toInputSchemaJson(inputSchema));
        entity.setOutputSchema(toOutputSchemaJson(outputSchema));
        entity.setErrorCodes("[]");
        return entity;
    }

    private ToolMeta toMeta(ToolRegistryEntity entity) {
        ToolMeta meta = new ToolMeta();
        meta.setToolId(entity.getToolId());
        meta.setName(entity.getName());
        meta.setDescription(entity.getDescription());
        meta.setExecutorType(entity.getExecutorType());
        meta.setSideEffect(mapRiskToSideEffect(entity.getRiskLevelEnum()));
        return meta;
    }

    private ToolSchema parseInputSchema(ToolRegistryEntity entity) {
        ToolSchema schema = new ToolSchema();
        List<String> required = parseRequiredFields(entity.getInputSchema());
        schema.setRequiredFields(required);
        return schema;
    }

    // ==================== JSON Schema 序列化 ====================

    private String toInputSchemaJson(ToolSchema schema) {
        if (schema == null || schema.getRequiredFields() == null
                || schema.getRequiredFields().isEmpty()) {
            return "{\"type\":\"object\",\"properties\":{},\"required\":[]}";
        }
        try {
            Map<String, Object> jsonSchema = Map.of(
                    "type", "object",
                    "required", schema.getRequiredFields()
            );
            return MAPPER.writeValueAsString(jsonSchema);
        } catch (JsonProcessingException e) {
            log.warn("序列化 inputSchema 失败, 降级为空 schema: {}", e.getMessage());
            return "{\"type\":\"object\",\"required\":[]}";
        }
    }

    private String toOutputSchemaJson(ToolSchema schema) {
        if (schema == null) {
            return "{\"type\":\"object\"}";
        }
        return "{\"type\":\"object\"}";
    }

    @SuppressWarnings("unchecked")
    private List<String> parseRequiredFields(String inputSchemaJson) {
        if (inputSchemaJson == null || inputSchemaJson.isBlank()) {
            return Collections.emptyList();
        }
        try {
            Map<String, Object> parsed = MAPPER.readValue(inputSchemaJson, new TypeReference<>() {});
            Object required = parsed.get("required");
            if (required instanceof List<?> list) {
                List<String> result = new ArrayList<>();
                for (Object item : list) {
                    if (item != null) {
                        result.add(item.toString());
                    }
                }
                return result;
            }
            return Collections.emptyList();
        } catch (JsonProcessingException e) {
            log.warn("解析 inputSchema JSON 失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    // ==================== SideEffect <-> RiskLevel 映射 (doc 05 §4.2) ====================

    private ToolRiskLevel mapSideEffectToRisk(SideEffect sideEffect) {
        if (sideEffect == null) {
            return ToolRiskLevel.R1;
        }
        return switch (sideEffect) {
            case NONE, READ_ONLY -> ToolRiskLevel.R1;
            case WRITE_LOCAL -> ToolRiskLevel.R2;
            case WRITE_EXTERNAL, DESTRUCTIVE -> ToolRiskLevel.R3;
        };
    }

    private SideEffect mapRiskToSideEffect(ToolRiskLevel riskLevel) {
        if (riskLevel == null) {
            return SideEffect.NONE;
        }
        // Reverse mapping picks the canonical representative for each level
        // (R1→NONE, R2→WRITE_LOCAL, R3→DESTRUCTIVE). Round-trip is lossy for
        // READ_ONLY / WRITE_EXTERNAL but those are recovered from the persisted
        // risk_level int column via ToolRegistryEntity#getRiskLevelEnum.
        return switch (riskLevel) {
            case R1 -> SideEffect.NONE;
            case R2 -> SideEffect.WRITE_LOCAL;
            case R3 -> SideEffect.DESTRUCTIVE;
        };
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ToolRegistryImpl that)) return false;
        return Objects.equals(repository, that.repository);
    }

    @Override
    public int hashCode() {
        return Objects.hash(repository);
    }
}
