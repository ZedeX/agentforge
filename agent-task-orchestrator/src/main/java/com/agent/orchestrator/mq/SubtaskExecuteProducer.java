package com.agent.orchestrator.mq;

import com.agent.orchestrator.config.RocketMqProperties;
import com.agent.orchestrator.mq.event.SubtaskExecuteEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.spring.support.RocketMQHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * 子任务分发生产者（对齐 doc 03-task-engine §7.2 + §8.1.5）。
 *
 * <p>消息规范：</p>
 * <ul>
 *   <li>Topic: task.subtask.execute</li>
 *   <li>Key: {taskId}:{nodeId}（用于幂等去重）</li>
 *   <li>Tag: {tenantId}（消费者按租户过滤）</li>
 *   <li>Payload: SubtaskExecuteEvent JSON</li>
 * </ul>
 */
@Slf4j
@Component
public class SubtaskExecuteProducer {

    private final RocketMQTemplate rocketMQTemplate;
    private final RocketMqProperties properties;

    public SubtaskExecuteProducer(RocketMQTemplate rocketMQTemplate, RocketMqProperties properties) {
        this.rocketMQTemplate = rocketMQTemplate;
        this.properties = properties;
    }

    /** 投递单个子任务，返回 messageId（用于幂等校验）。 */
    public String dispatch(SubtaskExecuteEvent event) {
        fillEventMetadata(event);
        String destination = properties.getTopics().getSubtaskExecute()
                + ":" + event.getTenantId();  // topic:tag
        String keys = event.getTaskId() + ":" + event.getNodeId();

        org.springframework.messaging.Message<SubtaskExecuteEvent> message =
                MessageBuilder.withPayload(event)
                        .setHeader(RocketMQHeaders.KEYS, keys)
                        .setHeader(RocketMQHeaders.TAGS, String.valueOf(event.getTenantId()))
                        .build();

        SendResult result = rocketMQTemplate.syncSend(destination, message, 5000L, 3);
        log.info("子任务分发成功 taskId={}, nodeId={}, msgId={}",
                event.getTaskId(), event.getNodeId(), result.getMsgId());
        return result.getMsgId();
    }

    private void fillEventMetadata(SubtaskExecuteEvent event) {
        if (event.getEventId() == null) {
            event.setEventId(UUID.randomUUID().toString());
        }
        if (event.getEventType() == null) {
            event.setEventType("task.subtask.execute");
        }
        if (event.getEventTime() == null) {
            event.setEventTime(Instant.now().toString());
        }
    }
}
