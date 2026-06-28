package com.agent.orchestrator.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DagEdge 实体单元测试（Red 阶段：实现尚未存在，预期编译失败）。
 *
 * <p>对齐 doc 03-task-engine §4.1 DagEdge POJO 定义 + T3.1 任务要求：
 * 边字段 id / dagId / parentNodeId / childNodeId / edgeType / paramMapping。</p>
 *
 * <p>命名说明：doc 中字段为 from / to / depType，本模块按 T3.1 任务要求
 * 改为 parentNodeId / childNodeId / edgeType，与 DDL 列命名风格一致
 * （parent_node_id / child_node_id / edge_type）。</p>
 *
 * <p>edgeType 取值：DATA（数据依赖）/ LOGIC（逻辑依赖）/ NONE（无依赖，仅并行批次标记）。</p>
 */
class DagEdgeTest {

    @Test
    void builder_shouldSetAllFields() {
        DagEdge edge = DagEdge.builder()
                .id(1L)
                .dagId(10086L)
                .parentNodeId("n1")
                .childNodeId("n2")
                .edgeType("DATA")
                .paramMapping("{\"n1.outputs.orderList\":\"n2.inputs.data\"}")
                .build();

        assertEquals(1L, edge.getId());
        assertEquals(10086L, edge.getDagId());
        assertEquals("n1", edge.getParentNodeId());
        assertEquals("n2", edge.getChildNodeId());
        assertEquals("DATA", edge.getEdgeType());
        assertEquals("{\"n1.outputs.orderList\":\"n2.inputs.data\"}", edge.getParamMapping());
    }

    @Test
    void builder_shouldGenerateToStringContainingFieldName() {
        DagEdge edge = DagEdge.builder()
                .parentNodeId("n_from")
                .childNodeId("n_to")
                .edgeType("LOGIC")
                .build();

        String str = edge.toString();
        assertNotNull(str);
        assertTrue(str.contains("parentNodeId=n_from"), "toString should contain parentNodeId field");
        assertTrue(str.contains("childNodeId=n_to"), "toString should contain childNodeId field");
        assertTrue(str.contains("edgeType=LOGIC"), "toString should contain edgeType field");
    }

    @Test
    void data_shouldImplementEqualsAndHashCodeByAllFields() {
        DagEdge e1 = DagEdge.builder().parentNodeId("n1").childNodeId("n2").edgeType("DATA").build();
        DagEdge e2 = DagEdge.builder().parentNodeId("n1").childNodeId("n2").edgeType("DATA").build();
        DagEdge e3 = DagEdge.builder().parentNodeId("n1").childNodeId("n2").edgeType("LOGIC").build();

        assertEquals(e1, e2, "同值边应相等");
        assertEquals(e1.hashCode(), e2.hashCode(), "同值边 hashCode 应相等");
        assertFalse(e1.equals(e3), "edgeType 不同应不等");
    }

    @Test
    void noArgsConstructor_shouldCreateInstanceWithNullFields() {
        DagEdge edge = new DagEdge();
        assertNull(edge.getParentNodeId());
        assertNull(edge.getChildNodeId());
        assertNull(edge.getEdgeType());
        assertNull(edge.getParamMapping());
    }

    @Test
    void allArgsConstructor_shouldSetAllFields() {
        DagEdge edge = new DagEdge(
                9L, 10086L, "n0", "n1", "LOGIC", null);

        assertEquals(9L, edge.getId());
        assertEquals(10086L, edge.getDagId());
        assertEquals("n0", edge.getParentNodeId());
        assertEquals("n1", edge.getChildNodeId());
        assertEquals("LOGIC", edge.getEdgeType());
        assertNull(edge.getParamMapping());
    }

    @Test
    void edgeType_shouldAcceptDataLogicNoneValues() {
        // 验证 edgeType 字段可接受 doc §4.1 定义的 DATA / LOGIC / NONE 三种取值
        DagEdge dataEdge = DagEdge.builder().edgeType("DATA").build();
        DagEdge logicEdge = DagEdge.builder().edgeType("LOGIC").build();
        DagEdge noneEdge = DagEdge.builder().edgeType("NONE").build();

        assertEquals("DATA", dataEdge.getEdgeType());
        assertEquals("LOGIC", logicEdge.getEdgeType());
        assertEquals("NONE", noneEdge.getEdgeType());
    }
}
