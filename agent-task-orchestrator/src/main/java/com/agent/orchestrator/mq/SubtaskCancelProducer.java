package com.agent.orchestrator.mq;

import com.agent.orchestrator.config.RocketMqProperties;
import com.agent.orchestrator.mq.event.SubtaskCancelEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.spring.support.RocketMQHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/** 子任务取消生产者（对齐 doc 03-task-engine §7.1 task.subtask.cancel）。 */
@Slf4j
@Component
public class SubtaskCancelProducer {

    private final RocketMQTemplate rocketMQTemplate;
    private final RocketMqProperties properties;

    public SubtaskCancelProducer(RocketMQTemplate rocketMQTemplate, RocketMqProperties properties) {
        this.rocketMQTemplate = rocketMQTemplate;
        this.properties = properties;
    }

    public String cancel(SubtaskCancelEvent event) {
        if (event.getEventId() == null) event.setEventId(UUID.randomUUID().toString());
        if (event.getEventType() == null) event.setEventType("task.subtask.cancel");
        if (event.getEventTime() == null) event.setEventTime(Instant.now().toString());

        String destination = properties.getTopics().getSubtaskCancel()
                + ":" + event.getTenantId();
        String keys = event.getTaskId() + ":" + event.getNodeId();

        org.springframework.messaging.Message<SubtaskCancelEvent> message =
                MessageBuilder.withPayload(event)
                        .setHeader(RocketMQHeaders.KEYS, keys)
                        .build();

        SendResult result = rocketMQTemplate.syncSend(destination, message, 5000L, 2);
        log.info("子任务取消发送 taskId={}, nodeId={}, msgId={}",
                event.getTaskId(), event.getNodeId(), result.getMsgId());
        return result.getMsgId();
    }
}
