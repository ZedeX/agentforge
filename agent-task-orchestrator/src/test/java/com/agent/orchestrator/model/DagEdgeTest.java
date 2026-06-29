package com.agent.orchestrator.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
    @DisplayName("通过 Builder 构建时应正确设置所有字段")
    void should_SetAllFields_When_UsingBuilder() {
        DagEdge edge = DagEdge.builder()
                .id(1L)
                .dagId(10086L)
                .parentNodeId("n1")
                .childNodeId("n2")
                .edgeType("DATA")
                .paramMapping("{\"n1.outputs.orderList\":\"n2.inputs.data\"}")
                .build();

        assertThat(edge.getId()).isEqualTo(1L);
        assertThat(edge.getDagId()).isEqualTo(10086L);
        assertThat(edge.getParentNodeId()).isEqualTo("n1");
        assertThat(edge.getChildNodeId()).isEqualTo("n2");
        assertThat(edge.getEdgeType()).isEqualTo("DATA");
        assertThat(edge.getParamMapping()).isEqualTo("{\"n1.outputs.orderList\":\"n2.inputs.data\"}");
    }

    @Test
    @DisplayName("Builder 生成的 toString 应包含字段名")
    void should_GenerateToStringContainingFieldName_When_UsingBuilder() {
        DagEdge edge = DagEdge.builder()
                .parentNodeId("n_from")
                .childNodeId("n_to")
                .edgeType("LOGIC")
                .build();

        String str = edge.toString();
        assertThat(str).isNotNull();
        assertThat(str.contains("parentNodeId=n_from")).as("toString should contain parentNodeId field").isTrue();
        assertThat(str.contains("childNodeId=n_to")).as("toString should contain childNodeId field").isTrue();
        assertThat(str.contains("edgeType=LOGIC")).as("toString should contain edgeType field").isTrue();
    }

    @Test
    @DisplayName("equals/hashCode 应基于所有字段实现，同值边相等，不同值边不等")
    void should_ImplementEqualsAndHashCodeByAllFields_When_EdgesHaveSameOrDifferentValues() {
        DagEdge e1 = DagEdge.builder().parentNodeId("n1").childNodeId("n2").edgeType("DATA").build();
        DagEdge e2 = DagEdge.builder().parentNodeId("n1").childNodeId("n2").edgeType("DATA").build();
        DagEdge e3 = DagEdge.builder().parentNodeId("n1").childNodeId("n2").edgeType("LOGIC").build();

        assertThat(e1).as("同值边应相等").isEqualTo(e2);
        assertThat(e1.hashCode()).as("同值边 hashCode 应相等").isEqualTo(e2.hashCode());
        assertThat(e1.equals(e3)).as("edgeType 不同应不等").isFalse();
    }

    @Test
    @DisplayName("无参构造函数创建实例时所有引用类型字段应为 null")
    void should_CreateInstanceWithNullFields_When_UsingNoArgsConstructor() {
        DagEdge edge = new DagEdge();
        assertThat(edge.getParentNodeId()).isNull();
        assertThat(edge.getChildNodeId()).isNull();
        assertThat(edge.getEdgeType()).isNull();
        assertThat(edge.getParamMapping()).isNull();
    }

    @Test
    @DisplayName("全参构造函数应正确设置所有字段")
    void should_SetAllFields_When_UsingAllArgsConstructor() {
        DagEdge edge = new DagEdge(
                9L, 10086L, "n0", "n1", "LOGIC", null);

        assertThat(edge.getId()).isEqualTo(9L);
        assertThat(edge.getDagId()).isEqualTo(10086L);
        assertThat(edge.getParentNodeId()).isEqualTo("n0");
        assertThat(edge.getChildNodeId()).isEqualTo("n1");
        assertThat(edge.getEdgeType()).isEqualTo("LOGIC");
        assertThat(edge.getParamMapping()).isNull();
    }

    @Test
    @DisplayName("edgeType 字段应接受 DATA/LOGIC/NONE 三种取值")
    void should_AcceptDataLogicNoneValues_When_EdgeTypeIsSet() {
        // 验证 edgeType 字段可接受 doc §4.1 定义的 DATA / LOGIC / NONE 三种取值
        DagEdge dataEdge = DagEdge.builder().edgeType("DATA").build();
        DagEdge logicEdge = DagEdge.builder().edgeType("LOGIC").build();
        DagEdge noneEdge = DagEdge.builder().edgeType("NONE").build();

        assertThat(dataEdge.getEdgeType()).isEqualTo("DATA");
        assertThat(logicEdge.getEdgeType()).isEqualTo("LOGIC");
        assertThat(noneEdge.getEdgeType()).isEqualTo("NONE");
    }
}
