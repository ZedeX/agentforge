package com.agent.orchestrator.validator;

import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;
import com.agent.orchestrator.model.DagEdge;
import com.agent.orchestrator.model.DagNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 5 维度 DAG 校验器（对齐 doc 03-task-engine §3.3 Step 5 规划自检优化 + PRD 五维度自检）。
 *
 * <p>对 DAG 执行 5 维度校验，依据 PRD「完备性、原子性、效率、成本、容错五个维度」：</p>
 * <ol>
 *   <li><b>完备性</b>：所有 deliverables 都有对应 outputs 节点。失败动作：补节点或
 *       抛 {@link ErrorCode#COMPLETENESS_FAIL}（doc 中 PLAN_INCOMPLETE 语义）。</li>
 *   <li><b>原子性</b>：单节点不混合多个职责（title 不含"和"/"并且"等连词）。失败动作：拆分节点。</li>
 *   <li><b>效率</b>：检测可并行但被串行的节点对（依赖边无 paramMapping）。失败动作：调整
 *       depType 为 none。本维度采用宽松语义：仅追加 warning 不阻塞 allPass。</li>
 *   <li><b>成本</b>：预估总成本不超 cost_limit_cent × 80%。失败动作：削减节点或抛
 *       {@link ErrorCode#COST_BUDGET_EXCEEDED}（doc 中 PLAN_TOO_EXPENSIVE 语义）。</li>
 *   <li><b>容错</b>：R3 写操作节点（nodeType 含 "write"）须配置 maxRetries/undoAction。
 *       失败动作：强制注入补偿配置。</li>
 * </ol>
 *
 * <p>失败处理约定（对齐 doc §3.3 Step 5）：</p>
 * <ul>
 *   <li>{@link #validate(ValidationContext)} 返回 {@link ValidationResult}，
 *       任一阻塞性维度失败 → allPass=false 并记录到 errors；</li>
 *   <li>{@link #validateOrThrow(ValidationContext)} 在第一次校验失败时抛
 *       {@link BusinessException}，errorCode 与失败维度对应：</li>
 *   <li>完备性失败 → {@link ErrorCode#COMPLETENESS_FAIL}</li>
 *   <li>成本超限 → {@link ErrorCode#COST_BUDGET_EXCEEDED}</li>
 *   <li>其他维度失败 → {@link ErrorCode#VALIDATION_FAILED}（对应 doc 中 PLAN_VALIDATION_FAILED）</li>
 *   <li>调用方（AiPlanner / Orchestrator）负责"修正后重试最多 2 轮"，仍失败时
 *       抛 {@link ErrorCode#VALIDATION_FAILED} → 转 WAITING_HUMAN。</li>
 * </ul>
 *
 * <p>设计说明：本类为无状态纯函数式校验器，便于单测覆盖所有分支。
 * 参考 {@code ReplanModeSelector} 的风格（纯函数 + 静态上下文 POJO）。</p>
 */
@Component
public class PlanValidator {

    /** 原子性检测的多职责关键词（节点 title 含其中任一 → 视为混合职责） */
    private static final String[] MULTI_RESPONSIBILITY_KEYWORDS = {"和", "并且", "以及", "同时"};

    /** 容错校验触发的节点类型关键字（nodeType 含此词 → 视为 R3 写操作节点） */
    private static final String WRITE_NODE_KEYWORD = "write";

    /** 容错配置必须包含的 JSON 字段名 */
    private static final String FAULT_TOLERANCE_FIELD_MAX_RETRIES = "maxRetries";
    private static final String FAULT_TOLERANCE_FIELD_UNDO_ACTION = "undoAction";

    /** 成本阈值比例（80%），estimatedCostCent > costLimitCent × 0.8 → 失败 */
    private static final double COST_THRESHOLD_RATIO = 0.8;

    /** 依赖边 edgeType 取值：数据依赖 */
    private static final String EDGE_TYPE_DATA = "DATA";
    /** 依赖边 edgeType 取值：逻辑依赖 */
    private static final String EDGE_TYPE_LOGIC = "LOGIC";

    /**
     * 执行 5 维度 DAG 校验，返回结构化结果（不抛异常）。
     *
     * @param context 校验上下文（不可为 null）
     * @return 校验结果，包含各维度结果、阻塞性 errors 与非阻塞 warnings
     */
    public ValidationResult validate(ValidationContext context) {
        if (context == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID,
                    "ValidationContext 不可为 null");
        }

        Map<ValidationDimension, Boolean> dimensionResults = new EnumMap<>(ValidationDimension.class);
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // 1. 完备性
        boolean completenessOk = validateCompleteness(context, errors);
        dimensionResults.put(ValidationDimension.COMPLETENESS, completenessOk);

        // 2. 原子性
        boolean atomicityOk = validateAtomicity(context, errors);
        dimensionResults.put(ValidationDimension.ATOMICITY, atomicityOk);

        // 3. 效率（仅 warning 不阻塞）
        boolean efficiencyOk = validateEfficiency(context, warnings);
        dimensionResults.put(ValidationDimension.EFFICIENCY, efficiencyOk);

        // 4. 成本
        boolean costOk = validateCost(context, errors);
        dimensionResults.put(ValidationDimension.COST, costOk);

        // 5. 容错
        boolean faultToleranceOk = validateFaultTolerance(context, errors);
        dimensionResults.put(ValidationDimension.FAULT_TOLERANCE, faultToleranceOk);

        boolean allPass = dimensionResults.values().stream().allMatch(Boolean::booleanValue);

        return ValidationResult.builder()
                .allPass(allPass)
                .dimensionResults(dimensionResults)
                .errors(errors)
                .warnings(warnings)
                .build();
    }

    /**
     * 执行 5 维度校验，任一阻塞性维度失败时抛 {@link BusinessException}。
     *
     * <p>错误码映射（对齐 doc §3.3 Step 5 + 任务要求）：</p>
     * <ul>
     *   <li>完备性失败 → {@link ErrorCode#COMPLETENESS_FAIL}（对应 PLAN_INCOMPLETE / PLAN_VALIDATION_FAILED）</li>
     *   <li>成本超限 → {@link ErrorCode#COST_BUDGET_EXCEEDED}（对应 PLAN_TOO_EXPENSIVE）</li>
     *   <li>其他维度失败 → {@link ErrorCode#VALIDATION_FAILED}（对应 PLAN_VALIDATION_FAILED）</li>
     * </ul>
     *
     * <p>注：本方法不实现"2 轮重试"逻辑——重试需调用方修正上下文后再次调用，
     * 由 AiPlanner / Orchestrator 层负责。本方法仅承担"单次校验失败即抛对应错误码"的契约。</p>
     *
     * @param context 校验上下文
     * @return 校验通过时返回 allPass=true 的结果
     * @throws BusinessException 校验失败时抛出，errorCode 取决于首个失败维度
     */
    public ValidationResult validateOrThrow(ValidationContext context) {
        ValidationResult result = validate(context);
        if (result.isAllPass()) {
            return result;
        }

        Map<ValidationDimension, Boolean> dims = result.getDimensionResults();
        if (Boolean.FALSE.equals(dims.get(ValidationDimension.COMPLETENESS))) {
            throw new BusinessException(ErrorCode.COMPLETENESS_FAIL,
                    "规划自检失败（完备性维度）：" + String.join("; ", result.getErrors()));
        }
        if (Boolean.FALSE.equals(dims.get(ValidationDimension.COST))) {
            throw new BusinessException(ErrorCode.COST_BUDGET_EXCEEDED,
                    "规划自检失败（成本维度）：" + String.join("; ", result.getErrors()));
        }
        throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                "规划自检失败（PLAN_VALIDATION_FAILED）：" + String.join("; ", result.getErrors()));
    }

    /**
     * 完备性校验：所有 deliverables 都应有对应 outputs 节点。
     *
     * <p>规则：</p>
     * <ul>
     *   <li>deliverables 为空 → 通过（无完备性要求）</li>
     *   <li>deliverables 非空但 nodes 为空 → 失败</li>
     *   <li>每个 deliverable 须能在某个节点的 outputs（JSON 字符串）中匹配到 → 通过；任一缺失 → 失败</li>
     * </ul>
     *
     * @param context 校验上下文
     * @param errors  失败时追加错误信息（不创建新列表，复用调用方传入的列表）
     * @return true=通过，false=失败
     */
    boolean validateCompleteness(ValidationContext context, List<String> errors) {
        List<String> deliverables = context.getDeliverables();
        if (deliverables == null || deliverables.isEmpty()) {
            return true;
        }

        List<DagNode> nodes = context.getNodes();
        if (nodes == null || nodes.isEmpty()) {
            errors.add("完备性校验失败：节点为空但存在 " + deliverables.size() + " 个 deliverables 未覆盖");
            return false;
        }

        // 收集所有节点的 outputs（JSON 字符串）
        Set<String> allOutputs = new HashSet<>();
        for (DagNode node : nodes) {
            if (node.getOutputs() != null && !node.getOutputs().isEmpty()) {
                allOutputs.add(node.getOutputs());
            }
        }

        List<String> missing = new ArrayList<>();
        for (String deliverable : deliverables) {
            if (deliverable == null || deliverable.isEmpty()) {
                continue;
            }
            boolean found = false;
            for (String output : allOutputs) {
                if (output.contains(deliverable)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                missing.add(deliverable);
            }
        }

        if (!missing.isEmpty()) {
            errors.add("完备性校验失败：以下 deliverables 缺少对应产出节点：" + missing);
            return false;
        }

        return true;
    }

    /**
     * 原子性校验：单节点不混合多个职责。
     *
     * <p>规则：节点 title 含 {@link #MULTI_RESPONSIBILITY_KEYWORDS} 中任一关键词 → 失败。</p>
     *
     * @param context 校验上下文
     * @param errors  失败时追加错误信息
     * @return true=通过，false=失败
     */
    boolean validateAtomicity(ValidationContext context, List<String> errors) {
        List<DagNode> nodes = context.getNodes();
        if (nodes == null || nodes.isEmpty()) {
            return true;
        }

        List<String> violatedNodes = new ArrayList<>();
        for (DagNode node : nodes) {
            String title = node.getTitle();
            if (title == null || title.isEmpty()) {
                continue;
            }
            for (String keyword : MULTI_RESPONSIBILITY_KEYWORDS) {
                if (title.contains(keyword)) {
                    violatedNodes.add(node.getNodeId() + "(" + title + ")");
                    break;
                }
            }
        }

        if (!violatedNodes.isEmpty()) {
            errors.add("原子性校验失败：以下节点混合多职责（含连词关键词）：" + violatedNodes);
            return false;
        }

        return true;
    }

    /**
     * 效率校验：检测可并行但被串行的节点对（仅生成 warning，不阻塞 allPass）。
     *
     * <p>规则：遍历 DAG 边，对于 edgeType=DATA/LOGIC 的依赖边，若其 paramMapping 为空
     * 或仅 "{}"，则该依赖无真实数据传递——可调整为 none 并行执行，记为 warning。</p>
     *
     * <p>设计说明：与 doc §3.3 "调整 depType 为 none" 的失败动作一致——降级为修正建议
     * 而非硬性失败，避免过度阻塞规划流程。</p>
     *
     * @param context   校验上下文
     * @param warnings  检测到可优化依赖时追加 warning
     * @return 始终返回 true（效率维度不阻塞 allPass）
     */
    boolean validateEfficiency(ValidationContext context, List<String> warnings) {
        List<DagEdge> edges = context.getEdges();
        if (edges == null || edges.isEmpty()) {
            return true;
        }

        List<String> inefficientPairs = new ArrayList<>();
        for (DagEdge edge : edges) {
            String edgeType = edge.getEdgeType();
            if (EDGE_TYPE_DATA.equalsIgnoreCase(edgeType) || EDGE_TYPE_LOGIC.equalsIgnoreCase(edgeType)) {
                String paramMapping = edge.getParamMapping();
                if (paramMapping == null || paramMapping.isEmpty() || "{}".equals(paramMapping)) {
                    inefficientPairs.add(edge.getParentNodeId() + "->" + edge.getChildNodeId());
                }
            }
        }

        if (!inefficientPairs.isEmpty()) {
            warnings.add("效率提示：以下依赖边无数据映射，建议调整 depType 为 none 以并行："
                    + inefficientPairs);
        }

        return true;
    }

    /**
     * 成本校验：预估总成本不超 cost_limit_cent × 80%。
     *
     * <p>规则：</p>
     * <ul>
     *   <li>costLimitCent &lt;= 0 → 跳过（视为通过，未设置预算）</li>
     *   <li>estimatedCostCent &gt; costLimitCent × 0.8 → 失败</li>
     *   <li>否则通过</li>
     * </ul>
     *
     * @param context 校验上下文
     * @param errors  失败时追加错误信息
     * @return true=通过，false=失败
     */
    boolean validateCost(ValidationContext context, List<String> errors) {
        long costLimitCent = context.getCostLimitCent();
        if (costLimitCent <= 0) {
            return true;
        }

        long estimatedCostCent = context.getEstimatedCostCent();
        long threshold = (long) (costLimitCent * COST_THRESHOLD_RATIO);
        if (estimatedCostCent > threshold) {
            errors.add("成本校验失败：预估成本 " + estimatedCostCent
                    + " cent 超过预算阈值 " + threshold + " cent（cost_limit_cent × 80%）");
            return false;
        }

        return true;
    }

    /**
     * 容错校验：R3 写操作节点须配置 maxRetries/undoAction。
     *
     * <p>规则：nodeType 含 "write" 关键字的节点，outputs 字段须同时包含
     * "maxRetries" 与 "undoAction" 两个 JSON 字段名（容错配置标记）。</p>
     *
     * <p>设计说明：DagNode POJO 未独立暴露 config 字段（参考现有 DagNode.java），
     * 故将容错配置以 JSON 字段形式存于 outputs 字符串中，校验时检查字段名存在性。
     * 后续若 DagNode 扩展 config 字段，可在此方法内迁移判定逻辑。</p>
     *
     * @param context 校验上下文
     * @param errors  失败时追加错误信息
     * @return true=通过，false=失败
     */
    boolean validateFaultTolerance(ValidationContext context, List<String> errors) {
        List<DagNode> nodes = context.getNodes();
        if (nodes == null || nodes.isEmpty()) {
            return true;
        }

        List<String> violatedNodes = new ArrayList<>();
        for (DagNode node : nodes) {
            String nodeType = node.getNodeType();
            if (nodeType == null || !nodeType.toLowerCase().contains(WRITE_NODE_KEYWORD)) {
                continue;
            }

            String outputs = node.getOutputs();
            if (outputs == null
                    || !outputs.contains(FAULT_TOLERANCE_FIELD_MAX_RETRIES)
                    || !outputs.contains(FAULT_TOLERANCE_FIELD_UNDO_ACTION)) {
                violatedNodes.add(node.getNodeId() + "(" + nodeType + ")");
            }
        }

        if (!violatedNodes.isEmpty()) {
            errors.add("容错校验失败：以下 R3 写操作节点缺少 maxRetries/undoAction 配置：" + violatedNodes);
            return false;
        }

        return true;
    }
}
