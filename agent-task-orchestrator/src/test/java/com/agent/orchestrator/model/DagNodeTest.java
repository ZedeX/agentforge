package com.agent.orchestrator.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void builder_shouldSetAllFields() {
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

        assertEquals(1L, node.getId());
        assertEquals(10086L, node.getDagId());
        assertEquals("n1", node.getNodeId());
        assertEquals("subtask", node.getNodeType());
        assertEquals("st_001", node.getSubtaskId());
        assertEquals("查询订单", node.getTitle());
        assertEquals(2001L, node.getAgentId());
        assertEquals("[\"query\",\"order\"]", node.getAbilityTags());
        assertEquals("{\"userId\":\"u_123\"}", node.getInputs());
        assertEquals("{\"orderList\":[]}", node.getOutputs());
        assertEquals("pending", node.getStatus());
    }

    @Test
    void builder_shouldGenerateToStringContainingFieldName() {
        DagNode node = DagNode.builder()
                .nodeId("n_toString")
                .nodeType("start")
                .status("success")
                .build();

        String str = node.toString();
        assertNotNull(str);
        assertTrue(str.contains("nodeId=n_toString"), "toString should contain nodeId field");
        assertTrue(str.contains("nodeType=start"), "toString should contain nodeType field");
        assertTrue(str.contains("status=success"), "toString should contain status field");
    }

    @Test
    void data_shouldImplementEqualsAndHashCodeByAllFields() {
        DagNode n1 = DagNode.builder().nodeId("n1").nodeType("subtask").status("pending").build();
        DagNode n2 = DagNode.builder().nodeId("n1").nodeType("subtask").status("pending").build();
        DagNode n3 = DagNode.builder().nodeId("n1").nodeType("subtask").status("running").build();

        assertEquals(n1, n2, "同值节点应相等");
        assertEquals(n1.hashCode(), n2.hashCode(), "同值节点 hashCode 应相等");
        assertFalse(n1.equals(n3), "status 不同应不等");
    }

    @Test
    void noArgsConstructor_shouldCreateInstanceWithNullFields() {
        DagNode node = new DagNode();
        assertNull(node.getNodeId());
        assertNull(node.getNodeType());
        assertNull(node.getStatus());
        assertNull(node.getSubtaskId());
    }

    @Test
    void allArgsConstructor_shouldSetAllFields() {
        DagNode node = new DagNode(
                7L, 10086L, "n_ctor", "end", null,
                "任务终点", null, null, null, null, "pending");

        assertEquals(7L, node.getId());
        assertEquals(10086L, node.getDagId());
        assertEquals("n_ctor", node.getNodeId());
        assertEquals("end", node.getNodeType());
        assertNull(node.getSubtaskId());
        assertEquals("任务终点", node.getTitle());
        assertEquals("pending", node.getStatus());
    }

    @Test
    void prePersist_shouldInitializeDefaultStatusToPending() {
        DagNode node = DagNode.builder()
                .dagId(10086L)
                .nodeId("n_default")
                .nodeType("subtask")
                .title("默认状态测试")
                .build();
        // PrePersist 应填充 status 默认值 "pending"
        node.prePersist();

        assertEquals("pending", node.getStatus(), "status 默认应为 pending");
        assertNotNull(node.getCreatedAt(), "createdAt 应被 PrePersist 填充");
        assertNotNull(node.getUpdatedAt(), "updatedAt 应被 PrePersist 填充");
    }
}
