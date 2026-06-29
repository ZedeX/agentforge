package com.agent.orchestrator.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DagNode 实体单元测试（Red 阶段：实现尚未存在，预期编译失败）。
 *
 * <p>对齐 doc 03-task-engine §4.1 DagNode POJO 定义：
 * 节点字段 id / dagId / nodeId / nodeType / subtaskId / title / agentId /
 * abilityTags / inputs / outputs / status。</p>
 *
 * <p>设计说明：doc 中 DagNode 为 POJO，本模块按 T3.1 任务要求实现为 JPA @Entity，
 * 字段类型沿用 TaskInstance 风格（JSON 字段以 String 存储，由调用方负责序列化）。</p>
 *
 * <p>风格参考 TaskInstanceTest.java：使用 JUnit 5 Assertions。</p>
 */
class DagNodeTest {

    @Test
    @DisplayName("通过 Builder 构建时应正确设置所有字段")
    void should_SetAllFields_When_UsingBuilder() {
        DagNode node = DagNode.builder()
                .id(1L)
                .dagId(10086L)
                .nodeId("n1")
                .nodeType("subtask")
                .subtaskId("st_001")
                .title("查询订单")
                .agentId(2001L)
                .abilityTags("[\"query\",\"order\"]")
                .inputs("{\"userId\":\"u_123\"}")
                .outputs("{\"orderList\":[]}")
                .status("pending")
                .build();

        assertThat(node.getId()).isEqualTo(1L);
        assertThat(node.getDagId()).isEqualTo(10086L);
        assertThat(node.getNodeId()).isEqualTo("n1");
        assertThat(node.getNodeType()).isEqualTo("subtask");
        assertThat(node.getSubtaskId()).isEqualTo("st_001");
        assertThat(node.getTitle()).isEqualTo("查询订单");
        assertThat(node.getAgentId()).isEqualTo(2001L);
        assertThat(node.getAbilityTags()).isEqualTo("[\"query\",\"order\"]");
        assertThat(node.getInputs()).isEqualTo("{\"userId\":\"u_123\"}");
        assertThat(node.getOutputs()).isEqualTo("{\"orderList\":[]}");
        assertThat(node.getStatus()).isEqualTo("pending");
    }

    @Test
    @DisplayName("Builder 生成的 toString 应包含字段名")
    void should_GenerateToStringContainingFieldName_When_UsingBuilder() {
        DagNode node = DagNode.builder()
                .nodeId("n_toString")
                .nodeType("start")
                .status("success")
                .build();

        String str = node.toString();
        assertThat(str).isNotNull();
        assertThat(str.contains("nodeId=n_toString")).as("toString should contain nodeId field").isTrue();
        assertThat(str.contains("nodeType=start")).as("toString should contain nodeType field").isTrue();
        assertThat(str.contains("status=success")).as("toString should contain status field").isTrue();
    }

    @Test
    @DisplayName("equals/hashCode 应基于所有字段实现，同值节点相等，不同值节点不等")
    void should_ImplementEqualsAndHashCodeByAllFields_When_NodesHaveSameOrDifferentValues() {
        DagNode n1 = DagNode.builder().nodeId("n1").nodeType("subtask").status("pending").build();
        DagNode n2 = DagNode.builder().nodeId("n1").nodeType("subtask").status("pending").build();
        DagNode n3 = DagNode.builder().nodeId("n1").nodeType("subtask").status("running").build();

        assertThat(n1).as("同值节点应相等").isEqualTo(n2);
        assertThat(n1.hashCode()).as("同值节点 hashCode 应相等").isEqualTo(n2.hashCode());
        assertThat(n1.equals(n3)).as("status 不同应不等").isFalse();
    }

    @Test
    @DisplayName("无参构造函数创建实例时所有引用类型字段应为 null")
    void should_CreateInstanceWithNullFields_When_UsingNoArgsConstructor() {
        DagNode node = new DagNode();
        assertThat(node.getNodeId()).isNull();
        assertThat(node.getNodeType()).isNull();
        assertThat(node.getStatus()).isNull();
        assertThat(node.getSubtaskId()).isNull();
    }

    @Test
    @DisplayName("全参构造函数应正确设置所有字段")
    void should_SetAllFields_When_UsingAllArgsConstructor() {
        DagNode node = new DagNode(
                7L, 10086L, "n_ctor", "end", null,
                "任务终点", null, null, null, null, "pending");

        assertThat(node.getId()).isEqualTo(7L);
        assertThat(node.getDagId()).isEqualTo(10086L);
        assertThat(node.getNodeId()).isEqualTo("n_ctor");
        assertThat(node.getNodeType()).isEqualTo("end");
        assertThat(node.getSubtaskId()).isNull();
        assertThat(node.getTitle()).isEqualTo("任务终点");
        assertThat(node.getStatus()).isEqualTo("pending");
    }

    @Test
    @DisplayName("PrePersist 应将 status 默认值初始化为 pending 并填充时间戳字段")
    void should_InitializeDefaultStatusToPending_When_PrePersistIsCalled() {
        DagNode node = DagNode.builder()
                .dagId(10086L)
                .nodeId("n_default")
                .nodeType("subtask")
                .title("默认状态测试")
                .build();
        // PrePersist 应填充 status 默认值 "pending"
        node.prePersist();

        assertThat(node.getStatus()).as("status 默认应为 pending").isEqualTo("pending");
        assertThat(node.getCreatedAt()).as("createdAt 应被 PrePersist 填充").isNotNull();
        assertThat(node.getUpdatedAt()).as("updatedAt 应被 PrePersist 填充").isNotNull();
    }
}
