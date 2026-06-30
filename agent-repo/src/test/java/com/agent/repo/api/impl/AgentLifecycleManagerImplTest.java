package com.agent.repo.api.impl;

import com.agent.repo.enums.AgentStatus;
import com.agent.repo.model.AgentDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AgentLifecycleManagerImpl unit tests (doc 06-agent-repo §2.3).
 */
@DisplayName("AgentLifecycleManagerImpl Agent 生命周期管理器")
class AgentLifecycleManagerImplTest {

    private final AgentLifecycleManagerImpl manager = new AgentLifecycleManagerImpl();

    private AgentDefinition sample(String id, AgentStatus status) {
        AgentDefinition a = new AgentDefinition(id, "Agent " + id);
        a.setStatus(status);
        return a;
    }

    @Test
    @DisplayName("register 后 getCurrentStatus 反映 agent 初始状态")
    void should_ReturnRegisteredStatus_When_AgentRegistered() {
        manager.register(sample("agent-1", AgentStatus.DRAFT));
        assertThat(manager.getCurrentStatus("agent-1")).isEqualTo(AgentStatus.DRAFT);
    }

    @Test
    @DisplayName("未注册的 agent getCurrentStatus 返回 null")
    void should_ReturnNull_When_AgentNotRegistered() {
        assertThat(manager.getCurrentStatus("unknown")).isNull();
    }

    @Test
    @DisplayName("DRAFT → PUBLISHED 转换成功")
    void should_TransitionToPublished_When_FromDraft() {
        manager.register(sample("agent-1", AgentStatus.DRAFT));
        AgentStatus result = manager.transition("agent-1", AgentStatus.PUBLISHED);
        assertThat(result).isEqualTo(AgentStatus.PUBLISHED);
        assertThat(manager.getCurrentStatus("agent-1")).isEqualTo(AgentStatus.PUBLISHED);
    }

    @Test
    @DisplayName("PUBLISHED → DEPRECATED 转换成功")
    void should_TransitionToDeprecated_When_FromPublished() {
        manager.register(sample("agent-1", AgentStatus.PUBLISHED));
        assertThat(manager.transition("agent-1", AgentStatus.DEPRECATED)).isEqualTo(AgentStatus.DEPRECATED);
    }

    @Test
    @DisplayName("DEPRECATED → ARCHIVED 转换成功")
    void should_TransitionToArchived_When_FromDeprecated() {
        manager.register(sample("agent-1", AgentStatus.DEPRECATED));
        assertThat(manager.transition("agent-1", AgentStatus.ARCHIVED)).isEqualTo(AgentStatus.ARCHIVED);
    }

    @Test
    @DisplayName("PUBLISHED → DRAFT 反向转换被拒绝, 抛 IllegalStateException")
    void should_Throw_When_BackwardTransition() {
        manager.register(sample("agent-1", AgentStatus.PUBLISHED));
        assertThatThrownBy(() -> manager.transition("agent-1", AgentStatus.DRAFT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Illegal transition");
    }

    @Test
    @DisplayName("ARCHIVED 不可再转换到任何其他状态")
    void should_RejectAllTransitions_When_FromArchived() {
        manager.register(sample("agent-1", AgentStatus.ARCHIVED));
        assertThatThrownBy(() -> manager.transition("agent-1", AgentStatus.PUBLISHED))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> manager.transition("agent-1", AgentStatus.DRAFT))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("canTransition 合法转换返回 true, 非法返回 false")
    void should_ReturnCorrectCanTransition_When_Called() {
        manager.register(sample("agent-1", AgentStatus.DRAFT));
        assertThat(manager.canTransition("agent-1", AgentStatus.PUBLISHED)).isTrue();
        assertThat(manager.canTransition("agent-1", AgentStatus.DRAFT)).isTrue(); // no-op
        assertThat(manager.canTransition("agent-1", AgentStatus.ARCHIVED)).isFalse();
    }

    @Test
    @DisplayName("canTransition 未注册 agent 允许任意非 null target (作为初始注册)")
    void should_AllowAnyTarget_When_AgentNotRegistered() {
        assertThat(manager.canTransition("unregistered", AgentStatus.PUBLISHED)).isTrue();
        assertThat(manager.canTransition("unregistered", null)).isFalse();
    }

    @Test
    @DisplayName("transition 未注册 agent 自动用 target 注册")
    void should_AutoRegister_When_TransitionUnregisteredAgent() {
        AgentStatus result = manager.transition("agent-new", AgentStatus.DRAFT);
        assertThat(result).isEqualTo(AgentStatus.DRAFT);
        assertThat(manager.getCurrentStatus("agent-new")).isEqualTo(AgentStatus.DRAFT);
    }

    @Test
    @DisplayName("transition null agentId 抛 IllegalStateException")
    void should_Throw_When_TransitionNullId() {
        assertThatThrownBy(() -> manager.transition(null, AgentStatus.PUBLISHED))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("transition null target 抛 IllegalStateException")
    void should_Throw_When_TransitionNullTarget() {
        manager.register(sample("agent-1", AgentStatus.DRAFT));
        assertThatThrownBy(() -> manager.transition("agent-1", null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("register null agent / null agentId 安全跳过")
    void should_Skip_When_RegisterNullOrNullId() {
        manager.register(null);
        manager.register(new AgentDefinition()); // agentId = null
        // No crash, no registration
        assertThat(manager.getCurrentStatus("anything")).isNull();
    }

    @Test
    @DisplayName("register 重复 agent 保留首次状态 (putIfAbsent 语义)")
    void should_PreserveFirstStatus_When_ReRegistered() {
        manager.register(sample("agent-1", AgentStatus.DRAFT));
        // Re-register with different status — should be ignored
        manager.register(sample("agent-1", AgentStatus.PUBLISHED));
        assertThat(manager.getCurrentStatus("agent-1")).isEqualTo(AgentStatus.DRAFT);
    }

    @Test
    @DisplayName("register null status 兜底为 DRAFT")
    void should_DefaultToDraft_When_StatusNull() {
        AgentDefinition a = new AgentDefinition("agent-1", "Agent 1");
        a.setStatus(null);
        manager.register(a);
        assertThat(manager.getCurrentStatus("agent-1")).isEqualTo(AgentStatus.DRAFT);
    }
}
