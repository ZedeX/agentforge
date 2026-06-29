package com.agent.orchestrator.mq;

import com.agent.orchestrator.mq.event.SubtaskDoneEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 子任务完成消费者（对齐 doc 03-task-engine §8.1.7）。
 *
 * <p>消费 task.subtask.done Topic，委托给 {@link SubtaskDoneHandler} 处理。</p>
 */
@Slf4j
@Component
@RocketMQMessageListener(
        topic = "${rocketmq.orchestrator.topics.subtask-done:task.subtask.done}",
        consumerGroup = "${rocketmq.orchestrator.groups.orchestrator-consumer:orchestrator-cg}",
        selectorExpression = "*"
)
public class SubtaskDoneConsumer implements RocketMQListener<SubtaskDoneEvent> {

    private final SubtaskDoneHandler handler;

    public SubtaskDoneConsumer(SubtaskDoneHandler handler) {
        this.handler = handler;
    }

    @Override
    public void onMessage(SubtaskDoneEvent event) {
        log.info("收到子任务完成事件 taskId={}, nodeId={}, status={}",
                event.getTaskId(), event.getNodeId(), event.getStatus());
        try {
            handler.handle(event);
        } catch (Exception e) {
            log.error("处理子任务完成事件失败 eventId={}", event.getEventId(), e);
            throw e;  // 抛出后 RocketMQ 会重试
        }
    }
}
