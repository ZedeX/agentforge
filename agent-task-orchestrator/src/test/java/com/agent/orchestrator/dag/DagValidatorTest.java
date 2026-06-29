package com.agent.orchestrator.dag;

import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;
import com.agent.orchestrator.model.DagEdge;
import com.agent.orchestrator.model.DagNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * DagValidator 单元测试（Red 阶段：实现尚未存在，预期编译失败）。
 *
 * <p>对齐 doc 03-task-engine §4.3 + 任务要求 T3.3 五维校验：
 * 节点非空 / subtask_id 唯一 / 入度出度合法 / 无孤立节点 / 无环。</p>
 *
 * <p>错误码映射：</p>
 * <ul>
 *   <li>节点非空 / subtask_id 唯一 / 入度出度合法 / 无孤立节点 → {@link ErrorCode#PARAM_INVALID}</li>
 *   <li>无环 → {@link ErrorCode#DAG_CYCLE_DETECTED}</li>
 * </ul>
 */
class DagValidatorTest {

    private final DagValidator validator = new DagValidator();

    private DagNode node(String nodeId, String subtaskId, String nodeType) {
        return DagNode.builder()
                .dagId(1L)
                .nodeId(nodeId)
                .subtaskId(subtaskId)
                .nodeType(nodeType)
                .title("node-" + nodeId)
                .build();
    }

    private DagEdge logicEdge(String from, String to) {
        return DagEdge.builder()
                .dagId(1L)
                .parentNodeId(from)
                .childNodeId(to)
                .edgeType("LOGIC")
                .build();
    }

    @Test
    @DisplayName("节点列表为空时应抛出 BusinessException 且错误码为 PARAM_INVALID")
    void should_ThrowBusinessExceptionWithParamInvalid_When_NodesListIsEmpty() {
        assertThatThrownBy(() -> validator.validate(Collections.emptyList(), Collections.emptyList()))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PARAM_INVALID));
    }

    @Test
    @DisplayName("subtask_id 重复时应抛出 BusinessException 且错误码为 PARAM_INVALID")
    void should_ThrowBusinessExceptionWithParamInvalid_When_SubtaskIdsAreDuplicate() {
        DagNode n1 = node("n1", "st_dup", "subtask");
        DagNode n2 = node("n2", "st_dup", "subtask"); // 重复 subtaskId
        DagNode n0 = node("n0", null, "start");
        DagNode n3 = node("n3", null, "end");

        List<DagNode> nodes = List.of(n0, n1, n2, n3);
        List<DagEdge> edges = List.of(
                logicEdge("n0", "n1"),
                logicEdge("n0", "n2"),
                logicEdge("n1", "n3"),
                logicEdge("n2", "n3"));

        assertThatThrownBy(() -> validator.validate(nodes, edges))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PARAM_INVALID));
    }

    @Test
    @DisplayName("边引用不存在的节点时应抛出 BusinessException 且错误码为 PARAM_INVALID")
    void should_ThrowBusinessExceptionWithParamInvalid_When_EdgeReferencesNonExistentNode() {
        // 边引用不存在的节点 n99
        DagNode n0 = node("n0", null, "start");
        DagNode n1 = node("n1", "st_001", "subtask");
        DagNode n3 = node("n3", null, "end");

        List<DagNode> nodes = List.of(n0, n1, n3);
        List<DagEdge> edges = List.of(
                logicEdge("n0", "n1"),
                logicEdge("n1", "n99"), // n99 不存在
                logicEdge("n1", "n3"));

        assertThatThrownBy(() -> validator.validate(nodes, edges))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PARAM_INVALID));
    }

    @Test
    @DisplayName("subtask 节点孤立（无入边无出边）时应抛出 BusinessException 且错误码为 PARAM_INVALID")
    void should_ThrowBusinessExceptionWithParamInvalid_When_SubtaskNodeIsOrphan() {
        // n2 是 subtask 节点但没有入边也没有出边（孤立）
        DagNode n0 = node("n0", null, "start");
        DagNode n1 = node("n1", "st_001", "subtask");
        DagNode n2 = node("n2", "st_orphan", "subtask"); // 孤立节点
        DagNode n3 = node("n3", null, "end");

        List<DagNode> nodes = List.of(n0, n1, n2, n3);
        List<DagEdge> edges = List.of(
                logicEdge("n0", "n1"),
                logicEdge("n1", "n3"));
        // n2 既无入边也无出边 → 孤立

        assertThatThrownBy(() -> validator.validate(nodes, edges))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PARAM_INVALID));
    }

    @Test
    @DisplayName("DAG 含环时应抛出 BusinessException 且错误码为 DAG_CYCLE_DETECTED")
    void should_ThrowBusinessExceptionWithDagCycleDetected_When_DagHasCycle() {
        // n1 -> n2 -> n3 -> n1 (形成环)
        DagNode n1 = node("n1", "st_001", "subtask");
        DagNode n2 = node("n2", "st_002", "subtask");
        DagNode n3 = node("n3", "st_003", "subtask");

        List<DagNode> nodes = List.of(n1, n2, n3);
        List<DagEdge> edges = List.of(
                logicEdge("n1", "n2"),
                logicEdge("n2", "n3"),
                logicEdge("n3", "n1"));

        assertThatThrownBy(() -> validator.validate(nodes, edges))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.DAG_CYCLE_DETECTED));
    }

    @Test
    @DisplayName("合法 DAG 通过校验时应正常返回，不抛出任何异常")
    void should_NotThrowAnyException_When_DagIsValid() {
        // 合法 DAG: n0(start) -> n1 -> n2 -> n3(end)
        DagNode n0 = node("n0", null, "start");
        DagNode n1 = node("n1", "st_001", "subtask");
        DagNode n2 = node("n2", "st_002", "subtask");
        DagNode n3 = node("n3", null, "end");

        List<DagNode> nodes = List.of(n0, n1, n2, n3);
        List<DagEdge> edges = List.of(
                logicEdge("n0", "n1"),
                logicEdge("n1", "n2"),
                logicEdge("n2", "n3"));

        assertThatCode(() -> validator.validate(nodes, edges)).doesNotThrowAnyException();
    }
}
