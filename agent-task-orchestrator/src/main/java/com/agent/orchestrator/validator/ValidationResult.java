package com.agent.orchestrator.validator;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 5 维度 DAG 校验结果 POJO（对齐 doc 03-task-engine §3.3 Step 5 规划自检优化）。
 *
 * <p>承载：</p>
 * <ul>
 *   <li>{@link #allPass} — 综合是否通过（所有阻塞性维度均通过）</li>
 *   <li>{@link #dimensionResults} — 各维度结果（true=pass, false=fail）</li>
 *   <li>{@link #errors} — 阻塞性错误信息（任一非空时 allPass=false）</li>
 *   <li>{@link #warnings} — 非阻塞警告信息（如效率维度的修正建议，不阻塞 allPass）</li>
 * </ul>
 *
 * <p>设计说明：参考 {@code ReplanModeSelector.ReplanContext} 的 Lombok 风格
 * （@Data + @Builder），通过静态工厂方法 {@link #pass()} / {@link #fail(Map, List)}
 * 提供常用构造场景。</p>
 */
@Data
@Builder
public class ValidationResult {

    /**
     * 是否全部维度通过。
     * <p>true 表示所有阻塞性维度（完备性/原子性/成本/容错）均通过；
     * false 表示存在阻塞性维度失败，需修正后重试。</p>
     */
    private boolean allPass;

    /**
     * 各维度结果（true=pass, false=fail）。
     * <p>key 为 {@link ValidationDimension}，value 为该维度是否通过。
     * 效率维度采用宽松语义：检测到可并行但串行的情况仍记为 pass，仅追加 warning。</p>
     */
    @Builder.Default
    private Map<ValidationDimension, Boolean> dimensionResults = new EnumMap<>(ValidationDimension.class);

    /**
     * 阻塞性错误信息列表。
     * <p>任一非空时 allPass=false。每条 error 对应一个维度失败原因，便于上层
     * （AiPlanner / Orchestrator）选择修正策略或抛出对应 {@code BusinessException}。</p>
     */
    @Builder.Default
    private List<String> errors = new ArrayList<>();

    /**
     * 非阻塞警告信息列表。
     * <p>如效率维度检测到可并行但串行的节点对，建议调整 depType 为 none。
     * 不影响 allPass，仅作为修正建议输出给调用方。</p>
     */
    @Builder.Default
    private List<String> warnings = new ArrayList<>();

    /**
     * 构造「全部通过」结果：5 个维度全部 pass，无 errors / warnings。
     *
     * @return allPass=true 的校验结果
     */
    public static ValidationResult pass() {
        Map<ValidationDimension, Boolean> all = new EnumMap<>(ValidationDimension.class);
        for (ValidationDimension d : ValidationDimension.values()) {
            all.put(d, Boolean.TRUE);
        }
        return ValidationResult.builder()
                .allPass(true)
                .dimensionResults(all)
                .errors(new ArrayList<>())
                .warnings(new ArrayList<>())
                .build();
    }

    /**
     * 构造「校验失败」结果：allPass=false，附带各维度结果与阻塞性错误列表。
     *
     * @param dimensionResults 各维度结果（可为 null，将替换为空 EnumMap）
     * @param errors          阻塞性错误列表（可为 null，将替换为空列表）
     * @return allPass=false 的校验结果
     */
    public static ValidationResult fail(Map<ValidationDimension, Boolean> dimensionResults,
                                        List<String> errors) {
        Map<ValidationDimension, Boolean> dims = dimensionResults != null
                ? dimensionResults
                : new EnumMap<>(ValidationDimension.class);
        List<String> errs = errors != null ? new ArrayList<>(errors) : new ArrayList<>();
        return ValidationResult.builder()
                .allPass(false)
                .dimensionResults(dims)
                .errors(errs)
                .warnings(new ArrayList<>())
                .build();
    }

    /**
     * 返回不可修改的 errors 视图，防止外部误改内部状态。
     */
    public List<String> getErrors() {
        return errors == null ? Collections.emptyList() : Collections.unmodifiableList(errors);
    }

    /**
     * 返回不可修改的 warnings 视图，防止外部误改内部状态。
     */
    public List<String> getWarnings() {
        return warnings == null ? Collections.emptyList() : Collections.unmodifiableList(warnings);
    }
}
