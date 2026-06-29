package com.agent.orchestrator.mq;

import com.agent.orchestrator.config.RocketMqProperties;
import com.agent.orchestrator.mq.event.StateChangeEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.spring.support.RocketMQHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * 任务状态变更广播生产者（对齐 doc 03-task-engine §6.3）。
 * Topic: task.state.change / 消费者: session / observability
 */
@Slf4j
@Component
public class StateChangeProducer {

    private final RocketMQTemplate rocketMQTemplate;
    private final RocketMqProperties properties;

    public StateChangeProducer(RocketMQTemplate rocketMQTemplate, RocketMqProperties properties) {
        this.rocketMQTemplate = rocketMQTemplate;
        this.properties = properties;
    }

    public String broadcast(StateChangeEvent event) {
        if (event.getCreatedAt() == null) {
            event.setCreatedAt(Instant.now().toString());
        }
        String destination = properties.getTopics().getStateChange()
                + ":" + event.getTenantId();
        String keys = event.getTaskId() + ":" + event.getFromStatus() + "->" + event.getToStatus();

        org.springframework.messaging.Message<StateChangeEvent> message =
                MessageBuilder.withPayload(event)
                        .setHeader(RocketMQHeaders.KEYS, keys)
                        .build();

        SendResult result = rocketMQTemplate.syncSend(destination, message, 3000L, 1);
        log.info("状态变更广播 taskId={} {}->{} msgId={}",
                event.getTaskId(), event.getFromStatus(), event.getToStatus(), result.getMsgId());
        return result.getMsgId();
    }
}
