package com.agent.orchestrator.planning.grpc;

import com.agent.orchestrator.model.DagEdge;
import com.agent.orchestrator.model.DagNode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * DAG ↔ JSON 序列化器（对齐 planning.proto dag_json 字段格式）。
 *
 * <p>proto 的 {@code PlanResponse.dag_json} / {@code ValidateRequest.dag_json} 为 JSON 字符串，
 * 内部结构为 {@code {"nodes":[...],"edges":[...]}}，节点/边复用
 * {@link com.agent.orchestrator.model.DagNode} / {@link com.agent.orchestrator.model.DagEdge}
 * JPA 实体 POJO（与 proto {@code DagNode}/{@code DagEdge} 同名但属不同包）。</p>
 *
 * <p>设计说明：</p>
 * <ul>
 *   <li>使用 Jackson {@link ObjectMapper} 序列化，注册 {@link JavaTimeModule}
 *       以处理 BaseEntity 的 {@code createdAt/updatedAt} Instant 字段；</li>
 *   <li>反序列化失败时返回空列表（不抛异常），由 {@code PlanValidator} 在后续校验时
 *       标记完备性失败，避免 gRPC 服务层吞掉 JSON 解析错误；</li>
 *   <li>本类为无状态组件，{@code ObjectMapper} 在构造时初始化，线程安全。</li>
 * </ul>
 */
@Component
public class DagJsonMapper {

    private final ObjectMapper objectMapper;

    /**
     * 默认构造器：初始化 ObjectMapper（注册 JavaTimeModule + 关闭 FAIL_ON_EMPTY_BEANS）。
     * <p>Spring 注入时使用；测试可直接 {@code new DagJsonMapper()} 调用。</p>
     */
    public DagJsonMapper() {
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    }

    /**
     * 注入式构造器：便于外部传入定制化 ObjectMapper（如测试场景）。
     *
     * @param objectMapper Jackson ObjectMapper 实例（不可为 null）
     */
    public DagJsonMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * DAG 节点 + 边 → JSON 字符串。
     *
     * <p>输出格式：{@code {"nodes":[...],"edges":[...]}}，对齐 proto dag_json 字段。</p>
     *
     * @param nodes DAG 节点列表（可为 null，序列化为空数组）
     * @param edges DAG 边列表（可为 null，序列化为空数组）
     * @return JSON 字符串；序列化失败时返回 {@code {"nodes":[],"edges":[]}}
     */
    public String toDagJson(List<DagNode> nodes, List<DagEdge> edges) {
        List<DagNode> safeNodes = nodes == null ? Collections.emptyList() : nodes;
        List<DagEdge> safeEdges = edges == null ? Collections.emptyList() : edges;
        try {
            DagPayload payload = new DagPayload(safeNodes, safeEdges);
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            // 序列化失败不应阻塞主流程，返回空 DAG 让后续校验报错
            return "{\"nodes\":[],\"edges\":[]}";
        }
    }

    /**
     * 从 JSON 字符串解析节点列表。
     *
     * @param dagJson DAG JSON 字符串（格式：{@code {"nodes":[...],"edges":[...]}}）
     * @return 节点列表；解析失败或 nodes 字段缺失时返回空列表
     */
    public List<DagNode> fromDagJsonNodes(String dagJson) {
        if (dagJson == null || dagJson.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            JsonNode root = objectMapper.readTree(dagJson);
            JsonNode nodesNode = root.get("nodes");
            if (nodesNode == null || !nodesNode.isArray()) {
                return Collections.emptyList();
            }
            List<DagNode> nodes = new ArrayList<>(nodesNode.size());
            for (JsonNode node : nodesNode) {
                nodes.add(objectMapper.treeToValue(node, DagNode.class));
            }
            return nodes;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /**
     * 从 JSON 字符串解析边列表。
     *
     * @param dagJson DAG JSON 字符串
     * @return 边列表；解析失败或 edges 字段缺失时返回空列表
     */
    public List<DagEdge> fromDagJsonEdges(String dagJson) {
        if (dagJson == null || dagJson.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            JsonNode root = objectMapper.readTree(dagJson);
            JsonNode edgesNode = root.get("edges");
            if (edgesNode == null || !edgesNode.isArray()) {
                return Collections.emptyList();
            }
            List<DagEdge> edges = new ArrayList<>(edgesNode.size());
            for (JsonNode edge : edgesNode) {
                edges.add(objectMapper.treeToValue(edge, DagEdge.class));
            }
            return edges;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /**
     * DAG 序列化载荷（仅用于 JSON 输出，字段名对齐 proto dag_json 约定）。
     */
    private static final class DagPayload {
        @SuppressWarnings("unused")
        private final List<DagNode> nodes;
        @SuppressWarnings("unused")
        private final List<DagEdge> edges;

        DagPayload(List<DagNode> nodes, List<DagEdge> edges) {
            this.nodes = nodes;
            this.edges = edges;
        }

        public List<DagNode> getNodes() {
            return nodes;
        }

        public List<DagEdge> getEdges() {
            return edges;
        }
    }
}
