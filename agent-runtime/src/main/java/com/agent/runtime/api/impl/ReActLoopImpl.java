package com.agent.runtime.api.impl;

import com.agent.runtime.api.ModelGatewayClient;
import com.agent.runtime.api.ReActLoop;
import com.agent.runtime.api.ToolEngineClient;
import com.agent.runtime.enums.ReActPhaseType;
import com.agent.runtime.exception.CircuitOpenException;
import com.agent.runtime.model.ReActContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * ReAct 循环控制器默认实现 (doc 06 §2, F6 think/act/observe/finish).
 *
 * <p>骨架阶段简单实现: 通过 ModelGatewayClient + ToolEngineClient 串联
 * THINK → ACT → OBSERVE → FINISH 循环, 最多 3 轮, 超过 maxLoop (10) 触发熔断.</p>
 */
@Component
public class ReActLoopImpl implements ReActLoop {

    private static final Logger log = LoggerFactory.getLogger(ReActLoopImpl.class);

    /** 最大循环次数, 超过触发熔断 (doc F6 CIRCUIT_OPEN) */
    private static final int MAX_LOOP_COUNT = 10;

    /** 骨架阶段默认循环上限: 3 轮 (THINK/ACT/OBSERVE 各一次) */
    private static final int DEFAULT_LOOP_LIMIT = 3;

    private final ModelGatewayClient modelGateway;
    private final ToolEngineClient toolEngine;

    @Autowired
    public ReActLoopImpl(ModelGatewayClient modelGateway, ToolEngineClient toolEngine) {
        this.modelGateway = modelGateway;
        this.toolEngine = toolEngine;
    }

    @Override
    public String start(ReActContext context) {
        log.info("启动 ReAct 循环: agentId={}, taskId={}", context.getAgentId(), context.getTaskId());
        context.setPhase(ReActPhaseType.THINK);

        String finalAnswer = null;
        for (int round = 0; round < DEFAULT_LOOP_LIMIT; round++) {
            context.incrementLoopCount();
            if (context.getLoopCount() > MAX_LOOP_COUNT) {
                log.warn("循环次数超限触发熔断: loopCount={}", context.getLoopCount());
                throw new CircuitOpenException(context.getLoopCount());
            }

            String prompt = "round=" + round + ",taskId=" + context.getTaskId();
            String modelOutput = modelGateway.chat(prompt);
            log.debug("第 {} 轮模型输出: {}", round, modelOutput);

            ReActPhaseType next = transit(context, modelOutput);
            context.setPhase(next);

            if (next == ReActPhaseType.FINISH) {
                finalAnswer = modelOutput;
                context.setFinalAnswer(finalAnswer);
                break;
            }

            if (next == ReActPhaseType.ACT && modelOutput != null && modelOutput.contains("tool_call")) {
                String toolResult = toolEngine.invoke("default_tool", "{}");
                context.getMemory().put("last_tool_result", toolResult);
                context.setPhase(ReActPhaseType.OBSERVE);
            }
        }

        if (finalAnswer == null) {
            finalAnswer = "default-final-answer";
            context.setFinalAnswer(finalAnswer);
        }
        log.info("ReAct 循环结束: agentId={}, finalAnswer={}", context.getAgentId(), finalAnswer);
        return finalAnswer;
    }

    @Override
    public ReActPhaseType transit(ReActContext context, String modelOutput) {
        if (modelOutput == null) {
            log.warn("模型输出为 null, 默认转 FINISH");
            return ReActPhaseType.FINISH;
        }

        if (modelOutput.contains("final_answer")) {
            log.debug("检测到 final_answer, 转 FINISH");
            return ReActPhaseType.FINISH;
        }
        if (modelOutput.contains("tool_call")) {
            log.debug("检测到 tool_call, 转 ACT");
            return ReActPhaseType.ACT;
        }
        if (context.getPhase() == ReActPhaseType.ACT) {
            log.debug("ACT 阶段结束, 转 OBSERVE");
            return ReActPhaseType.OBSERVE;
        }
        log.debug("默认转 FINISH (modelOutput={})", modelOutput);
        return ReActPhaseType.FINISH;
    }
}
