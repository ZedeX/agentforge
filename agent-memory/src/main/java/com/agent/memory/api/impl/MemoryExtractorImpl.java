package com.agent.memory.api.impl;

import com.agent.memory.api.MemoryExtractor;
import com.agent.memory.enums.MemoryType;
import com.agent.memory.model.ExtractedMemory;
import com.agent.memory.model.TaskResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 记忆提取实现（F12.D2: episodic / semantic / procedural extraction）。
 *
 * <p>从 {@link TaskResult} 中按 {@link MemoryType} 提取关键记忆：
 * <ul>
 *   <li>EPISODIC：保留任务步骤序列 stepSequence</li>
 *   <li>SEMANTIC：提取目标 goal 作为事实 fact</li>
 *   <li>PROCEDURAL：将首步作为操作模板 patternTemplate（无步骤时回退到 goal）</li>
 * </ul>
 * source 字段统一记录 taskId，便于回溯。</p>
 *
 * @see MemoryExtractor
 */
@Component
public class MemoryExtractorImpl implements MemoryExtractor {

    private static final Logger log = LoggerFactory.getLogger(MemoryExtractorImpl.class);

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
            default:
                log.warn("未知的记忆类型：{}", type);
        }
        return memory;
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
}