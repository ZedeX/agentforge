package com.agent.tool.engine.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Tool Engine 配置属性（对齐 doc 05-tool-engine §13）。
 *
 * <p>前缀 {@code tool}，包含 Cache / RateLimit / Sandbox / Approval / Cleaner / Docker / MemoryClient
 * 七个子配置。</p>
 */
@ConfigurationProperties(prefix = "tool")
public class ToolEngineProperties {

    private Cache cache = new Cache();
    private RateLimit rateLimit = new RateLimit();
    private Sandbox sandbox = new Sandbox();
    private Approval approval = new Approval();
    private Cleaner cleaner = new Cleaner();
    private Docker docker = new Docker();
    private MemoryClient memoryClient = new MemoryClient();

    public Cache getCache() { return cache; }
    public void setCache(Cache cache) { this.cache = cache; }

    public RateLimit getRateLimit() { return rateLimit; }
    public void setRateLimit(RateLimit rateLimit) { this.rateLimit = rateLimit; }

    public Sandbox getSandbox() { return sandbox; }
    public void setSandbox(Sandbox sandbox) { this.sandbox = sandbox; }

    public Approval getApproval() { return approval; }
    public void setApproval(Approval approval) { this.approval = approval; }

    public Cleaner getCleaner() { return cleaner; }
    public void setCleaner(Cleaner cleaner) { this.cleaner = cleaner; }

    public Docker getDocker() { return docker; }
    public void setDocker(Docker docker) { this.docker = docker; }

    public MemoryClient getMemoryClient() { return memoryClient; }
    public void setMemoryClient(MemoryClient memoryClient) { this.memoryClient = memoryClient; }

    /** ToolCache 配置（doc 05 §6）。 */
    public static class Cache {
        private boolean enabled = true;
        private int ttlSeconds = 300;
        private int maxEntries = 10000;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public int getTtlSeconds() { return ttlSeconds; }
        public void setTtlSeconds(int ttlSeconds) { this.ttlSeconds = ttlSeconds; }
        public int getMaxEntries() { return maxEntries; }
        public void setMaxEntries(int maxEntries) { this.maxEntries = maxEntries; }
    }

    /** Rate Limiter 配置（doc 05 §6.5）。 */
    public static class RateLimit {
        private int defaultQps = 10;

        public int getDefaultQps() { return defaultQps; }
        public void setDefaultQps(int defaultQps) { this.defaultQps = defaultQps; }
    }

    /** Docker 沙箱配置（doc 05 §5）。 */
    public static class Sandbox {
        private boolean enabled = true;
        private int poolSize = 5;
        private int maxConcurrent = 20;
        private long idleTimeoutMs = 600000;
        private long execTimeoutMs = 60000;
        private String image = "agent-sandbox:latest";
        private double cpuCores = 1.0;
        private long memoryBytes = 536870912L; // 512MB
        private long tmpfsBytes = 67108864L;   // 64MB

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public int getPoolSize() { return poolSize; }
        public void setPoolSize(int poolSize) { this.poolSize = poolSize; }
        public int getMaxConcurrent() { return maxConcurrent; }
        public void setMaxConcurrent(int maxConcurrent) { this.maxConcurrent = maxConcurrent; }
        public long getIdleTimeoutMs() { return idleTimeoutMs; }
        public void setIdleTimeoutMs(long idleTimeoutMs) { this.idleTimeoutMs = idleTimeoutMs; }
        public long getExecTimeoutMs() { return execTimeoutMs; }
        public void setExecTimeoutMs(long execTimeoutMs) { this.execTimeoutMs = execTimeoutMs; }
        public String getImage() { return image; }
        public void setImage(String image) { this.image = image; }
        public double getCpuCores() { return cpuCores; }
        public void setCpuCores(double cpuCores) { this.cpuCores = cpuCores; }
        public long getMemoryBytes() { return memoryBytes; }
        public void setMemoryBytes(long memoryBytes) { this.memoryBytes = memoryBytes; }
        public long getTmpfsBytes() { return tmpfsBytes; }
        public void setTmpfsBytes(long tmpfsBytes) { this.tmpfsBytes = tmpfsBytes; }
    }

    /** 审批配置（doc 05 §4.4）。 */
    public static class Approval {
        private boolean enabled = true;
        private long r2TimeoutMs = 300000;   // 5min
        private long r3TimeoutMs = 1800000;  // 30min

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public long getR2TimeoutMs() { return r2TimeoutMs; }
        public void setR2TimeoutMs(long r2TimeoutMs) { this.r2TimeoutMs = r2TimeoutMs; }
        public long getR3TimeoutMs() { return r3TimeoutMs; }
        public void setR3TimeoutMs(long r3TimeoutMs) { this.r3TimeoutMs = r3TimeoutMs; }
    }

    /** 结果清洗配置（doc 05 §9）。 */
    public static class Cleaner {
        private int maxBytes = 8192;

        public int getMaxBytes() { return maxBytes; }
        public void setMaxBytes(int maxBytes) { this.maxBytes = maxBytes; }
    }

    /** Docker 客户端配置（doc 05 §5.2）。 */
    public static class Docker {
        private boolean enabled = false;
        private String host = "unix:///var/run/docker.sock";
        private String apiVersion = "v1.43";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }
        public String getApiVersion() { return apiVersion; }
        public void setApiVersion(String apiVersion) { this.apiVersion = apiVersion; }
    }

    /** agent-memory gRPC 客户端配置（doc 05 §8）。 */
    public static class MemoryClient {
        private boolean enabled = false;
        private String address = "static://localhost:9088";
        private long timeoutMs = 2000;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getAddress() { return address; }
        public void setAddress(String address) { this.address = address; }
        public long getTimeoutMs() { return timeoutMs; }
        public void setTimeoutMs(long timeoutMs) { this.timeoutMs = timeoutMs; }
    }
}
