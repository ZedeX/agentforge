package com.agent.memory.api.impl;

import com.agent.memory.api.MemoryExtractor;
import com.agent.memory.enums.MemoryType;
import com.agent.memory.enums.TaskOutcome;
import com.agent.memory.extractor.MemoryExtractRule;
import com.agent.memory.model.ExtractedMemory;
import com.agent.memory.model.TaskResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 记忆提取实现（F12.D2: episodic / semantic / procedural / reflective extraction）。
 *
 * <p>从 {@link TaskResult} 中按 {@link MemoryType} 提取关键记忆：
 * <ul>
 *   <li>EPISODIC：保留任务步骤序列 stepSequence</li>
 *   <li>SEMANTIC：提取目标 goal 作为事实 fact</li>
 *   <li>PROCEDURAL：将首步作为操作模板 patternTemplate（无步骤时回退到 goal）</li>
 *   <li>REFLECTIVE：从 FAILURE/TIMEOUT 提取失败反思（goal 作为失败原因）</li>
 * </ul>
 * source 字段统一记录 taskId，便于回溯。</p>
 *
 * <p>{@link #extractFromTaskResult} 按 outcome 自动分流：
 * SUCCESS→PROCEDURAL / FAILURE→REFLECTIVE / PARTIAL→both / TIMEOUT→REFLECTIVE。</p>
 *
 * <p>内容过滤：长度 &lt; 20 字符 / 命中黑名单关键词 → 过滤返回 null。</p>
 *
 * @see MemoryExtractor
 * @see MemoryExtractRule
 */
@Component
public class MemoryExtractorImpl implements MemoryExtractor {

    private static final Logger log = LoggerFactory.getLogger(MemoryExtractorImpl.class);

    private final MemoryExtractRule rule;

    public MemoryExtractorImpl(MemoryExtractRule rule) {
        this.rule = rule;
    }

    @Override
    public ExtractedMemory extract(TaskResult taskResult, MemoryType type) {
        if (taskResult == null) {
            log.warn("记忆提取失败：TaskResult 为 null");
            return null;
        }
        if (type == null) {
            log.warn("记忆提取失败：MemoryType 为 null");
            return null;
        }

        // 内容过滤：检查 goal 是否通过规则
        if (rule.shouldFilter(taskResult.getGoal())) {
            log.debug("内容被过滤（长度不足或命中黑名单）taskId={} goalLen={}",
                    taskResult.getTaskId(),
                    taskResult.getGoal() == null ? 0 : taskResult.getGoal().length());
            return null;
        }

        ExtractedMemory memory = new ExtractedMemory(type);
        memory.setExtractedAt(Instant.now());
        memory.setSource(taskResult.getTaskId());

        switch (type) {
            case EPISODIC: {
                List<String> steps = taskResult.getSteps();
                memory.setStepSequence(steps == null ? new ArrayList<>() : new ArrayList<>(steps));
                log.debug("提取 EPISODIC 记忆 taskId={} steps={}",
                        taskResult.getTaskId(), memory.getStepSequence().size());
                break;
            }
            case SEMANTIC: {
                memory.setFact(taskResult.getGoal());
                log.debug("提取 SEMANTIC 记忆 taskId={} fact={}",
                        taskResult.getTaskId(), taskResult.getGoal());
                break;
            }
            case PROCEDURAL: {
                memory.setPatternTemplate(extractPattern(taskResult));
                log.debug("提取 PROCEDURAL 记忆 taskId={} template={}",
                        taskResult.getTaskId(), memory.getPatternTemplate());
                break;
            }
            case REFLECTIVE: {
                memory.setFact(buildReflection(taskResult));
                log.debug("提取 REFLECTIVE 记忆 taskId={} outcome={}",
                        taskResult.getTaskId(), taskResult.getOutcome());
                break;
            }
            default:
                log.warn("未知的记忆类型：{}", type);
        }
        return memory;
    }

    @Override
    public List<ExtractedMemory> extractFromTaskResult(TaskResult taskResult) {
        if (taskResult == null || taskResult.getOutcome() == null) {
            log.warn("自动提取失败：TaskResult 或 outcome 为 null");
            return List.of();
        }

        List<ExtractedMemory> results = new ArrayList<>();
        TaskOutcome outcome = taskResult.getOutcome();

        switch (outcome) {
            case SUCCESS: {
                ExtractedMemory m = extract(taskResult, MemoryType.PROCEDURAL);
                if (m != null) results.add(m);
                break;
            }
            case FAILURE:
            case TIMEOUT: {
                ExtractedMemory m = extract(taskResult, MemoryType.REFLECTIVE);
                if (m != null) results.add(m);
                break;
            }
            case PARTIAL: {
                ExtractedMemory proc = extract(taskResult, MemoryType.PROCEDURAL);
                if (proc != null) results.add(proc);
                ExtractedMemory refl = extract(taskResult, MemoryType.REFLECTIVE);
                if (refl != null) results.add(refl);
                break;
            }
            default:
                log.warn("未知的任务结果：{}", outcome);
        }

        log.info("自动提取完成 taskId={} outcome={} extracted={}",
                taskResult.getTaskId(), outcome, results.size());
        return results;
    }

    /**
     * 从任务结果中提取操作模板：优先取第一步，否则回退到 goal。
     */
    private String extractPattern(TaskResult taskResult) {
        List<String> steps = taskResult.getSteps();
        if (steps != null && !steps.isEmpty()) {
            return steps.get(0);
        }
        return taskResult.getGoal();
    }

    /**
     * 构建反思内容（REFLECTIVE）：失败原因 + 任务目标。
     */
    private String buildReflection(TaskResult taskResult) {
        TaskOutcome outcome = taskResult.getOutcome();
        String goal = taskResult.getGoal();
        if (outcome == TaskOutcome.TIMEOUT) {
            return String.format("任务超时未完成：目标=%s，需分析超时原因并优化执行策略", goal);
        }
        return String.format("任务失败：目标=%s，需分析失败原因并提取经验教训", goal);
    }
}
