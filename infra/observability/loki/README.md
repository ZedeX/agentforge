# Loki 日志聚合

## 架构

```
App (logback-spring.xml)  -->  stdout (JSON)  -->  Promtail  -->  Loki  -->  Grafana
                              |                       |
                              v                       v
                          container log         K8s SD + JSON parse
```

- **应用端**：Logback 输出结构化 JSON 到 stdout，含 traceId/tenantId/taskId（MDC 注入）
- **Promtail**：通过 K8s SD 发现 pod，解析 JSON 日志，提取字段为 Loki label/structured_metadata
- **Loki 3.0**：TSDB schema v13，单节点 dev / S3 后端 prod
- **Grafana**：通过 Loki 数据源查询，支持 LogQL

## 应用接入步骤

1. 各服务 `pom.xml` 添加 `logstash-logback-encoder 7.4` 依赖：
   ```xml
   <dependency>
     <groupId>net.logstash.logback</groupId>
     <artifactId>logstash-logback-encoder</artifactId>
     <version>7.4</version>
   </dependency>
   ```
2. 将 `logback-spring.xml` 复制到 `src/main/resources/`
3. 应用代码中通过 `MDC.put("traceId", traceId)` 注入 traceId（TraceIdFilter 已实现）

## 查询示例（LogQL）

```logql
# 按 traceId 查全链路日志
{app=~"agent-.*"} | json | traceId="a1b2c3d4"

# 查所有 ERROR 日志
{app=~"agent-.*"} | json | level="ERROR"

# 按 tenantId 过滤
{app="agent-runtime"} | json | tenantId="1001"

# 查指定任务的所有日志
{app=~"agent-.*"} | json | taskId="tk_001"

# 查最近 5 分钟异常日志趋势
sum(count_over_time({app=~"agent-.*"} | json | level="ERROR" [5m])) by (app)
```

## 保留策略

| 环境 | 保留期 | 配置位置 |
|---|---|---|
| dev | 7 天 | `limits_config.retention_period` |
| staging | 30 天 | 同上 |
| prod | 90 天 | 同上（通过 Nacos profile 覆盖） |

## 脱敏

`logback-spring.xml` 中已配置 `MaskingJsonGeneratorDecorator`，自动屏蔽以下字段：
- `password` / `secret` / `apiKey` / `api_key` / `token` / `authorization`

匹配字段值会被替换为 `****`，原文不落日志。
