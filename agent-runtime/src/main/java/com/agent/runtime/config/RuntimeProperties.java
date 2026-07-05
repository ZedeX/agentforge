package com.agent.runtime.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * agent-runtime 模块配置属性（对齐 doc 06-runtime §13）。
 *
 * <p>字段命名与 application.yml 中 {@code runtime.*} 路径一一对应。</p>
 */
@ConfigurationProperties(prefix = "runtime")
public class RuntimeProperties {

    /** agent-model-gateway gRPC 客户端开关（默认关闭，测试环境保持关闭） */
    private ClientToggle modelGatewayClient = new ClientToggle();
    /** agent-tool-engine gRPC 客户端开关 */
    private ClientToggle toolEngineClient = new ClientToggle();
    /** agent-memory gRPC 客户端开关 */
    private ClientToggle memoryClient = new ClientToggle();
    /** ReAct 循环参数 */
    private React react = new React();
    /** Reflexion 反思参数 */
    private Reflexion reflexion = new Reflexion();
    /** 熔断器参数 */
    private Circuit circuit = new Circuit();
    /** 重试器参数 */
    private Retry retry = new Retry();

    public ClientToggle getModelGatewayClient() { return modelGatewayClient; }
    public void setModelGatewayClient(ClientToggle modelGatewayClient) { this.modelGatewayClient = modelGatewayClient; }

    public ClientToggle getToolEngineClient() { return toolEngineClient; }
    public void setToolEngineClient(ClientToggle toolEngineClient) { this.toolEngineClient = toolEngineClient; }

    public ClientToggle getMemoryClient() { return memoryClient; }
    public void setMemoryClient(ClientToggle memoryClient) { this.memoryClient = memoryClient; }

    public React getReact() { return react; }
    public void setReact(React react) { this.react = react; }

    public Reflexion getReflexion() { return reflexion; }
    public void setReflexion(Reflexion reflexion) { this.reflexion = reflexion; }

    public Circuit getCircuit() { return circuit; }
    public void setCircuit(Circuit circuit) { this.circuit = circuit; }

    public Retry getRetry() { return retry; }
    public void setRetry(Retry retry) { this.retry = retry; }

    /** gRPC 客户端开关 */
    public static class ClientToggle {
        private boolean enabled = false;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }

    /** ReAct 循环参数（doc 06 §4.1 / §4.2） */
    public static class React {
        /** 最大步数，超限触发 ABORT（doc 06 §4.1，默认 20） */
        private int maxSteps = 20;
        /** token 预算（默认 32K） */
        private int tokenBudget = 32000;
        /** token 黄色水位阈值（0~1，默认 0.6 = 60%） */
        private double tokenYellowThreshold = 0.6;
        /** token 红色水位阈值（0~1，默认 0.8 = 80%） */
        private double tokenRedThreshold = 0.8;

        public int getMaxSteps() { return maxSteps; }
        public void setMaxSteps(int maxSteps) { this.maxSteps = maxSteps; }

        public int getTokenBudget() { return tokenBudget; }
        public void setTokenBudget(int tokenBudget) { this.tokenBudget = tokenBudget; }

        public double getTokenYellowThreshold() { return tokenYellowThreshold; }
        public void setTokenYellowThreshold(double v) { this.tokenYellowThreshold = v; }

        public double getTokenRedThreshold() { return tokenRedThreshold; }
        public void setTokenRedThreshold(double v) { this.tokenRedThreshold = v; }
    }

    /** Reflexion 反思参数（doc 06 §5.1） */
    public static class Reflexion {
        /** 每 N 步触发一次 Reflexion（默认 3） */
        private int interval = 3;

        public int getInterval() { return interval; }
        public void setInterval(int interval) { this.interval = interval; }
    }

    /** 熔断器参数（doc 06 §6.1） */
    public static class Circuit {
        private ModelCircuit model = new ModelCircuit();
        private ToolCircuit tool = new ToolCircuit();

        public ModelCircuit getModel() { return model; }
        public void setModel(ModelCircuit model) { this.model = model; }

        public ToolCircuit getTool() { return tool; }
        public void setTool(ToolCircuit tool) { this.tool = tool; }

        /** ModelGateway 熔断配置：连续 5 次失败 → 熔断 30s */
        public static class ModelCircuit {
            private int failureThreshold = 5;
            private long openDurationMs = 30000;

            public int getFailureThreshold() { return failureThreshold; }
            public void setFailureThreshold(int v) { this.failureThreshold = v; }

            public long getOpenDurationMs() { return openDurationMs; }
            public void setOpenDurationMs(long v) { this.openDurationMs = v; }
        }

        /** ToolEngine 熔断配置：连续 3 次失败 → 熔断 30s */
        public static class ToolCircuit {
            private int failureThreshold = 3;
            private long openDurationMs = 30000;

            public int getFailureThreshold() { return failureThreshold; }
            public void setFailureThreshold(int v) { this.failureThreshold = v; }

            public long getOpenDurationMs() { return openDurationMs; }
            public void setOpenDurationMs(long v) { this.openDurationMs = v; }
        }
    }

    /** 重试器参数（doc 06 §6.2：3 次指数退避 200/600/1800 ms） */
    public static class Retry {
        private int maxAttempts = 3;
        private long initialBackoffMs = 200;
        private double multiplier = 3.0;

        public int getMaxAttempts() { return maxAttempts; }
        public void setMaxAttempts(int v) { this.maxAttempts = v; }

        public long getInitialBackoffMs() { return initialBackoffMs; }
        public void setInitialBackoffMs(long v) { this.initialBackoffMs = v; }

        public double getMultiplier() { return multiplier; }
        public void setMultiplier(double v) { this.multiplier = v; }
    }
}
