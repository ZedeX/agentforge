package com.agent.orchestrator.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * RocketMQ 配置属性绑定（对齐 doc 03-task-engine §11）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "rocketmq.orchestrator")
public class RocketMqProperties {

    private Topics topics = new Topics();
    private Groups groups = new Groups();

    @Data
    public static class Topics {
        private String subtaskExecute = "task.subtask.execute";
        private String subtaskDone = "task.subtask.done";
        private String stateChange = "task.state.change";
        private String subtaskCancel = "task.subtask.cancel";
    }

    @Data
    public static class Groups {
        private String orchestratorConsumer = "orchestrator-cg";
    }
}
