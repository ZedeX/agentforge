# SkyWalking 链路追踪接入说明

## 架构

```
App (javaagent) --> SkyWalking OAP (11800) --> Elasticsearch (9200)
                          |
                          v
                    SkyWalking UI (12800/18080)
```

- **Agent**: SkyWalking Java Agent 9.7.0，通过 `-javaagent:/skywalking/skywalking-agent.jar` 嵌入（已在 `Dockerfile.base` 完成）
- **OAP**: 接收 Agent 上报的 trace/metric 数据，存储到 Elasticsearch
- **UI**: 可视化查询 trace 拓扑、服务依赖图、慢调用链

## 文件说明

| 文件 | 用途 |
|---|---|
| `agent.config` | Agent 全局配置，通过 ConfigMap 挂载到 `/skywalking/config/agent.config` |
| `application.yml` | OAP Server 配置，指向 infra 命名空间的 ES 集群 |
| `custom-plugin/TraceIdHeaderInterceptor.java` | 自定义插件，绑定 X-Trace-Id 与 SkyWalking TraceID + MDC 注入 |

## 服务名注入

每个服务通过环境变量 `SW_AGENT_NAME=agent-platform-<service-name>` 注入（已在 `Dockerfile.<service>` 声明）。

示例（agent-runtime）：
```
ENV SW_AGENT_NAME="agent-platform-agent-runtime"
ENV SW_COLLECTOR_BACKEND_SERVICE="skywalking-oap.agent-platform-infra:11800"
```

## 自定义插件构建

`TraceIdHeaderInterceptor.java` 需构建为 jar 放到 `/skywalking/plugins/`：

```bash
# 在独立 maven 项目中编译
mvn package -Dskywalking.version=9.7.0
# 将 agent-platform-custom-plugin.jar 复制到 base 镜像的 /skywalking/plugins/
```

## K8s 部署

```yaml
# ConfigMap 挂载 agent.config
apiVersion: v1
kind: ConfigMap
metadata:
  name: skywalking-agent-config
  namespace: agent-platform-prod
data:
  agent.config: |
    # 内容来自 infra/observability/skywalking/agent.config
```

## 验证

启动后访问 SkyWalking UI（本地 docker-compose: http://localhost:18080）：
- General Service → 应看到 12 个 agent-platform-* 服务
- Trace → 应看到跨服务调用链
- Topology → 应看到服务依赖图
