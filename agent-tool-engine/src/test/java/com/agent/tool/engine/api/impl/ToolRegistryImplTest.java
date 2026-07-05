package com.agent.tool.engine.api.impl;

import com.agent.tool.engine.entity.ToolRegistryEntity;
import com.agent.tool.engine.enums.ExecutorType;
import com.agent.tool.engine.enums.SideEffect;
import com.agent.tool.engine.enums.ToolRiskLevel;
import com.agent.tool.engine.enums.ToolStatus;
import com.agent.tool.engine.exception.ToolValidationException;
import com.agent.tool.engine.model.ToolMeta;
import com.agent.tool.engine.model.ToolSchema;
import com.agent.tool.engine.repository.ToolRegistryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ToolRegistryImpl} 单元测试 (T3 JPA 版).
 *
 * <p>使用 Mockito 模拟 {@link ToolRegistryRepository}, 验证 ToolMeta/ToolSchema
 * 与 ToolRegistryEntity 的双向转换 + JPA 持久化调用.</p>
 */
@ExtendWith(MockitoExtension.class)
class ToolRegistryImplTest {

    @Mock
    private ToolRegistryRepository repository;

    @InjectMocks
    private ToolRegistryImpl registry;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistryImpl(repository);
    }

    @Test
    @DisplayName("toolId 缺失时: register 自动生成 tool-xxx 并保存")
    void should_GenerateToolId_When_NotProvided() {
        ToolMeta meta = new ToolMeta(null, "auto_name", ExecutorType.GENERAL, SideEffect.NONE);
        when(repository.existsByToolId(any())).thenReturn(false);
        when(repository.save(any(ToolRegistryEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        String toolId = registry.register(meta, new ToolSchema(List.of("q")), null);

        assertThat(toolId).startsWith("tool-");
        assertThat(meta.getToolId()).isEqualTo(toolId);
        verify(repository, times(1)).save(any(ToolRegistryEntity.class));
    }

    @Test
    @DisplayName("注册后: findMeta 返回元数据 (Entity -> ToolMeta 转换)")
    void should_FindMeta_When_Registered() {
        ToolRegistryEntity entity = buildEntity("tool_db", "db_query",
                ExecutorType.PROXY, ToolRiskLevel.R2.getLevel());
        when(repository.findByToolId("tool_db")).thenReturn(Optional.of(entity));

        ToolMeta found = registry.findMeta("tool_db");

        assertThat(found).isNotNull();
        assertThat(found.getName()).isEqualTo("db_query");
        assertThat(found.getExecutorType()).isEqualTo(ExecutorType.PROXY);
        assertThat(found.getSideEffect()).isEqualTo(SideEffect.WRITE_LOCAL);
    }

    @Test
    @DisplayName("注册后: findInputSchema 解析 JSON 返回 requiredFields")
    void should_FindInputSchema_When_Registered() {
        ToolRegistryEntity entity = buildEntity("tool_v", "validate",
                ExecutorType.GENERAL, ToolRiskLevel.R1.getLevel());
        entity.setInputSchema("{\"type\":\"object\",\"required\":[\"orderId\",\"tenantId\"]}");
        when(repository.findByToolId("tool_v")).thenReturn(Optional.of(entity));

        ToolSchema found = registry.findInputSchema("tool_v");

        assertThat(found).isNotNull();
        assertThat(found.getRequiredFields()).containsExactly("orderId", "tenantId");
    }

    @Test
    @DisplayName("未注册或空 toolId: findMeta / findInputSchema 返回 null")
    void should_ReturnNull_When_NotFound() {
        when(repository.findByToolId("not_exist")).thenReturn(Optional.empty());

        assertThat(registry.findMeta("not_exist")).isNull();
        assertThat(registry.findInputSchema("not_exist")).isNull();
        assertThat(registry.findMeta(null)).isNull();
        assertThat(registry.findInputSchema("  ")).isNull();
    }

    @Test
    @DisplayName("meta 为 null: register 抛 IllegalArgumentException")
    void should_Throw_When_MetaIsNull() {
        assertThatThrownBy(() -> registry.register(null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("meta");

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("重复 toolId: register 抛 ToolValidationException")
    void should_Throw_When_DuplicateToolId() {
        when(repository.existsByToolId("tool_dup")).thenReturn(true);
        ToolMeta meta = new ToolMeta("tool_dup", "dup", ExecutorType.GENERAL, SideEffect.NONE);

        assertThatThrownBy(() -> registry.register(meta, null, null))
                .isInstanceOf(ToolValidationException.class)
                .hasMessageContaining("已注册");

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("unregister 已注册工具: 设置 status=OFFLINE 并返回 true")
    void should_Unregister_When_Exists() {
        ToolRegistryEntity entity = buildEntity("tool_del", "del",
                ExecutorType.GENERAL, ToolRiskLevel.R1.getLevel());
        when(repository.findByToolId("tool_del")).thenReturn(Optional.of(entity));
        when(repository.save(any())).thenReturn(entity);

        boolean removed = registry.unregister("tool_del");

        assertThat(removed).isTrue();
        assertThat(entity.getStatusEnum()).isEqualTo(ToolStatus.OFFLINE);
        verify(repository, times(1)).save(any(ToolRegistryEntity.class));
    }

    @Test
    @DisplayName("SideEffect -> RiskLevel 映射: NONE=R1, WRITE_LOCAL=R2, DESTRUCTIVE=R3")
    void should_MapSideEffectToRiskLevel_When_Register() {
        when(repository.existsByToolId(any())).thenReturn(false);
        when(repository.save(any(ToolRegistryEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        // NONE -> R1
        ToolMeta r1 = new ToolMeta("t_r1", "r1", ExecutorType.GENERAL, SideEffect.NONE);
        registry.register(r1, null, null);

        // DESTRUCTIVE -> R3
        ToolMeta r3 = new ToolMeta("t_r3", "r3", ExecutorType.SANDBOX, SideEffect.DESTRUCTIVE);
        registry.register(r3, null, null);

        verify(repository, times(2)).save(any(ToolRegistryEntity.class));
    }

    // ==================== 辅助方法 ====================

    private ToolRegistryEntity buildEntity(String toolId, String name,
                                           ExecutorType execType, int riskLevel) {
        ToolRegistryEntity entity = new ToolRegistryEntity();
        entity.setId(1L);
        entity.setToolId(toolId);
        entity.setName(name);
        entity.setDisplayName(name);
        entity.setDescription("desc");
        entity.setExecutorType(execType);
        entity.setRiskLevel(riskLevel);
        entity.setInputSchema("{\"type\":\"object\",\"required\":[]}");
        entity.setOutputSchema("{\"type\":\"object\"}");
        entity.setErrorCodes("[]");
        entity.setSceneTags("[]");
        entity.setStatus(ToolStatus.ENABLED.getCode());
        return entity;
    }
}
