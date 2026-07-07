[English](./ops-guide.md) | [中文](./ops-guide.zh-CN.md)

# AgentForge 运营维护手册

> 版本：v1.0 | 更新日期：2026-07-08 | 面向角色：SRE / 运维工程师 / 平台管理员

---

## 目录

1. [部署架构](#1-部署架构)
2. [环境准备](#2-环境准备)
3. [Docker Compose 本地部署](#3-docker-compose-本地部署)
4. [K8s 生产部署](#4-k8s-生产部署)
5. [中间件部署](#5-中间件部署)
6. [配置管理](#6-配置管理)
7. [可观测体系](#7-可观测体系)
8. [安全加固检查清单](#8-安全加固检查清单)
9. [数据库初始化与迁移](#9-数据库初始化与迁移)
10. [日常运维操作](#10-日常运维操作)
11. [告警规则与处理](#11-告警规则与处理)
12. [故障排查手册](#12-故障排查手册)
13. [性能调优](#13-性能调优)
14. [备份与恢复](#14-备份与恢复)

---

## 1. 部署架构

### 1.1 服务拓扑

```
                        ┌──────────────┐
                        │   Ingress    │
                        │  Controller  │
                        └──────┬───────┘
                               │
                        ┌──────▼───────┐
                        │  agent-      │
                        │  gateway     │ :8080
                        └──────┬───────┘
                               │
              ┌────────────────┼────────────────┐
              │                │                │
     ┌────────▼──────┐ ┌──────▼──────┐ ┌───────▼──────┐
     │ agent-session │ │task-orchestr│ │  risk-       │
     │   :8082       │ │  ator:8084  │ │  control     │
     └────────┬──────┘ └──────┬──────┘ └──────────────┘
              │               │
     ┌────────▼───────────────▼──────────┐
     │                                    │
     │  ┌──────────┐  ┌──────────────┐   │
     │  │ planning │  │   runtime    │   │
     │  │  :8086   │  │   :8092      │   │
     │  └──────────┘  └──┬───┬───┬───┘   │
     │                    │   │   │       │
     │  ┌──────────┐  ┌──▼─┐ ┌▼──┐       │
     │  │ memory   │  │tool│ │mod│       │
     │  │ :8088    │  │eng │ │el │       │
     │  └──────────┘  │:8090│ │:8094│     │
     │                └────┘ └────┘       │
     │                                    │
     │  ┌──────────┐  ┌──────────────┐   │
     │  │  repo    │  │  knowledge   │   │
     │  │ :8096    │  │  :8098       │   │
     │  └──────────┘  └──────────────┘   │
     │                                    │
     │  ┌──────────┐  ┌──────────────┐   │
     │  │ quality  │  │observability │   │
     │  │ :8100    │  │              │   │
     │  └──────────┘  └──────────────┘   │
     │       核心引擎层                    │
     └────────────────────────────────────┘
              │            │            │
     ┌────────▼──┐  ┌──────▼──┐  ┌─────▼───┐
     │  MySQL    │  │  Redis  │  │ Milvus  │
     │  Cluster  │  │ Cluster │  │ Cluster │
     └───────────┘  └─────────┘  └─────────┘
```

### 1.2 服务清单

| 服务 | 端口 | 副本数 | CPU/Mem | 依赖 |
|---|---|---|---|---|
| agent-gateway | 8080 | 2+ | 1C/2G | Redis, risk-control |
| agent-session | 8082 | 2+ | 1C/2G | MySQL, Redis |
| agent-task-orchestrator | 8084 | 2+ | 2C/4G | MySQL, RocketMQ |
| agent-planning | 8086 | 1+ | 2C/4G | model-gateway |
| agent-memory | 8088 | 2+ | 2C/4G | MySQL, Milvus, Redis |
| agent-tool-engine | 8090 | 2+ | 2C/4G | MySQL, Redis, Milvus |
| agent-runtime | 8092 | 2+ | 2C/4G | MySQL, model-gateway, tool-engine, memory |
| agent-model-gateway | 8094 | 2+ | 2C/4G | — |
| agent-repo | 8096 | 1+ | 1C/2G | MySQL |
| agent-knowledge | 8098 | 1+ | 1C/2G | MySQL, Milvus, Neo4j |
| agent-quality | 8100 | 1+ | 1C/2G | MySQL |
| risk-control | — | 1+ | 0.5C/1G | — |
| observability | — | 1+ | 0.5C/1G | — |

---

## 2. 环境准备

### 2.1 基础软件

| 软件 | 版本 | 安装方式 |
|---|---|---|
| JDK 17 | Eclipse Temurin 17+ | `sdk install java 17.0.9-tem` |
| Maven | 3.9+ | `sdk install maven 3.9.6` |
| Docker | 20+ | [Docker Desktop](https://www.docker.com/products/docker-desktop) |
| kubectl | 1.28+ | `curl -LO "https://dl.k8s.io/release/$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl"` |
| Helm | 3.14+ | `curl https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash` |

### 2.2 中间件

| 中间件 | 版本 | 用途 |
|---|---|---|
| MySQL | 8.0.36 | 关系数据存储 |
| Redis | 7.2-alpine | 缓存 + 短期记忆 |
| Milvus | 2.4 | 向量存储 |
| Neo4j | 5.18 | 图数据库 |
| RocketMQ | 5.x | 消息队列 |
| Nacos | 2.3 | 注册中心 + 配置中心 |
| Vault | 1.15+ | 密钥管理 |
| ClickHouse | 24.x | 指标沉淀 |
| Elasticsearch | 8.13.4 | 全文搜索 |
| SkyWalking | 9.7 | 链路追踪 |
| Prometheus | 2.x | 指标采集 |
| Loki | v13 | 日志聚合 |
| Grafana | 10.x | 可视化 |

---

## 3. Docker Compose 本地部署

### 3.1 准备配置

```bash
cd infra/docker-compose
cp .env.example .env
```

编辑 `.env` 文件，配置数据库密码和中间件连接信息：

```env
# MySQL
MYSQL_ROOT_PASSWORD=${DB_PASSWORD}
MYSQL_DATABASE=agent_session

# Redis
REDIS_PASSWORD=${REDIS_PASSWORD}

# Milvus
MILVUS_HOST=milvus-standalone
MILVUS_PORT=19530

# Nacos
NACOS_SERVER_ADDR=nacos:8848
```

### 3.2 启动中间件

```bash
# 启动所有中间件
docker compose -f docker-compose-services.yml up -d

# 检查服务状态
docker compose -f docker-compose-services.yml ps
```

### 3.3 启动应用服务

```bash
# 先编译打包
cd ../..
mvn clean package -DskipTests

# 启动全部应用
docker compose up -d

# 查看日志
docker compose logs -f agent-gateway
```

### 3.4 验证

```bash
# 健康检查
curl http://localhost:8080/health

# 预期响应
# {"code":"OK","message":"success","data":{"service":"agent-gateway","status":"UP"}}
```

---

## 4. K8s 生产部署

### 4.1 命名空间与 RBAC

```bash
# 创建命名空间
kubectl apply -f infra/k8s/00-namespace.yaml

# 创建 ServiceAccount（每个服务独立 SA）
kubectl apply -f infra/k8s/01-serviceaccounts.yaml

# 配置 Vault 角色
kubectl apply -f infra/k8s/03-vault-config.yaml
```

### 4.2 部署服务

```bash
# 部署全部 12 个服务
kubectl apply -f infra/k8s/deployments/

# 部署 Service
kubectl apply -f infra/k8s/services/

# 配置 Ingress
kubectl apply -f infra/k8s/services/ingress-gateway.yaml
```

每个 Deployment 配置了：
- **startupProbe**：`failureThreshold=30, periodSeconds=10`（最长等待 5 分钟启动）
- **readinessProbe**：HTTP `/actuator/health/readiness`
- **livenessProbe**：HTTP `/actuator/health/liveness`
- **preStop**：`sleep 10 && curl -X POST /actuator/shutdown || true`（优雅停机）
- **securityContext**：`runAsNonRoot: true, readOnlyRootFilesystem: true`

### 4.3 HPA 自动扩缩容

```bash
kubectl apply -f infra/k8s/hpa/
```

| 服务 | Min | Max | 指标 | 目标值 |
|---|---|---|---|---|
| agent-runtime | 2 | 10 | `agent_active_instances` | 50 |
| agent-gateway | 2 | 8 | `http_requests_qps` | 100 |
| agent-model-gateway | 2 | 6 | `grpc_requests_qps` | 200 |
| agent-memory | 2 | 6 | CPU 70% | — |
| agent-tool-engine | 2 | 6 | CPU 70% | — |
| agent-task-orchestrator | 2 | 4 | CPU 70% | — |

**稳定窗口**：scaleUp 30s（快速响应），scaleDown 300s（防止抖动）

### 4.4 PDB 中断预算

```bash
kubectl apply -f infra/k8s/pdb/agent-runtime-pdb.yaml
```

### 4.5 OPA 安全策略验证

```bash
# 验证 RBAC 合规
cd infra/k8s/__tests__
opa test . --format=json
```

---

## 5. 中间件部署

### 5.1 MySQL

```bash
# 初始化 DDL
cd infra/sql
./init-all.ps1 -DbType mysql -TenantId default

# 库清单：
# 01-agent-session   (session, message 表)
# 02-agent-task      (task_instance, subtask 表)
# 03-agent-memory    (memory_record, outbox_message 表)
# 04-agent-tool      (tool_meta, approval_record, tool_call_audit_log 表)
# 05-agent-model     (model_usage_log 表)
# 06-agent-repo      (agent_definition 表)
# 07-agent-knowledge (knowledge_base, document, version 表)
# 08-agent-quality   (quality_report, badcase 表)
# 09-agent-risk      (audit_log, event_consume_log 表)
```

### 5.2 Redis

```bash
# 初始化 Redis 数据
redis-cli < infra/sql/redis/01-init-data.redis
```

### 5.3 Milvus

```bash
# 初始化 Collections
python infra/sql/milvus/01-init-collections.py
```

### 5.4 Neo4j

```bash
# 初始化约束
cypher-shell < infra/sql/neo4j/01-init-constraints.cypher
cypher-shell < infra/sql/neo4j/02-init-relationships.cypher
```

---

## 6. 配置管理

### 6.1 Nacos 配置中心

配置文件位于 `infra/nacos/`，通过 `import-nacos.ps1` 导入：

```powershell
cd infra/nacos
./import-nacos.ps1 -NacosAddr nacos:8848
```

**配置结构**：

```
COMMON_GROUP/
  ├── datasource-common.yml    # MySQL 数据源
  ├── redis-common.yml         # Redis 连接
  ├── rocketmq-common.yml      # RocketMQ 生产者/消费者
  ├── observability-common.yml # SkyWalking/Prometheus
  └── governance-rules.yml     # 熔断/限流规则

SERVICE_GROUP/
  ├── memory-service-prod.yml       # agent-memory 专属
  └── task-orchestrator-prod.yml    # task-orchestrator 专属
```

**启动顺序**：`bootstrap-common.yml` → Nacos shared → service-level

### 6.2 Vault 密钥管理

所有敏感字段使用 Vault 占位符：

```yaml
# application.yml 中
spring:
  datasource:
    password: ${vault:secret/data/agent-platform/common/datasource#password}
```

**Vault 策略文件**：`infra/vault/vault-policies/` 下每个服务一个 `.hcl` 文件。

初始化 Vault：

```bash
cd infra/vault
./vault-seeds.sh
```

### 6.3 mTLS 配置

gRPC mTLS 通过 `application-mtls.yml` Profile 激活：

```bash
# 启动时激活 mTLS
java -jar app.jar --spring.profiles.active=prod,mtls
```

证书路径通过环境变量注入：
- `GRPC_TLS_CERT_PATH=/etc/grpc/certs/tls.crt`
- `GRPC_TLS_KEY_PATH=/etc/grpc/certs/tls.key`
- `GRPC_TLS_CA_PATH=/etc/grpc/certs/ca.crt`

开发环境证书生成：

```bash
./infra/certs/generate-dev-certs.sh
```

---

## 7. 可观测体系

### 7.1 组件清单

| 组件 | 端口 | 用途 |
|---|---|---|
| SkyWalking OAP | 11800/12800 | 链路追踪后端 |
| SkyWalking UI | 8080 | 追踪查询界面 |
| Prometheus | 9090 | 指标采集存储 |
| Loki | 3100 | 日志聚合 |
| Grafana | 3000 | 统一可视化 |
| ClickHouse | 8123 | 指标长期存储 |

### 7.2 日志链路

```
应用 (logback-spring.xml, JSON + MDC)
  → Promtail (K8s SD + JSON parse)
    → Loki (TSDB v13)
      → Grafana (查询/面板)
```

**TraceId 透传**：`TraceIdHeaderInterceptor` 绑定 `X-Trace-Id` → MDC → Logback JSON

### 7.3 Prometheus 告警规则

规则文件位于 `infra/observability/prometheus/alerts/`：

| 文件 | 告警项 |
|---|---|
| `service-availability.yaml` | 服务可用性 < 99.9% |
| `drift-alerts.yaml` | 行为漂移检测 |
| `hallucination-rate.yaml` | 幻觉率超标 |

Outbox 告警规则：`infra/prometheus/outbox-alerts.yml`

### 7.4 Grafana 仪表盘

导入 `infra/observability/grafana/dashboards/agent-platform-overview.json`

**数据源配置**：
- Prometheus：`infra/observability/grafana/datasources/prometheus.yml`
- Loki：`infra/observability/grafana/datasources/loki.yml`

---

## 8. 安全加固检查清单

### 8.1 认证与授权

- [ ] JWT secret 通过环境变量注入，非硬编码
- [ ] API Key 绑定租户，无全局硬编码 Key
- [ ] PermissionChecker 使用 JWT claims 中的 role
- [ ] gRPC mTLS Profile 已激活（生产环境必须）

### 8.2 K8s 安全

- [ ] 每个 ServiceAccount 独立 RoleBinding
- [ ] Pod securityContext: `runAsNonRoot: true`
- [ ] Container securityContext: `readOnlyRootFilesystem: true`
- [ ] OPA conftest 策略验证通过

### 8.3 工具引擎安全

- [ ] riskLevel 永不降级（never-downgrade rule）
- [ ] R3 工具需要人工审批
- [ ] Docker 沙箱：`cap-drop ALL + user=nobody + no-new-privileges`
- [ ] 工具结果经过 PII 脱敏清洗

### 8.4 CI/CD 安全

- [ ] gitleaks 密钥扫描通过
- [ ] trivy 容器镜像扫描通过
- [ ] CodeQL SAST 扫描通过
- [ ] 所有密码通过 `${vault:...}` 注入

### 8.5 依赖安全

- [ ] Spring Boot ≥ 3.2.12（CVE-2024-38816 已修复）
- [ ] Protobuf ≥ 3.25.5（CVE-2024-7254 已修复）

---

## 9. 数据库初始化与迁移

### 9.1 DDL 脚本说明

| 文件 | 库 | 主要表 |
|---|---|---|
| 01-agent-session.sql | agent_session | session, message |
| 02-agent-task.sql | agent_task | task_instance, subtask, dag_node |
| 03-agent-memory.sql | agent_memory | memory_record, outbox_message, event_consume_log |
| 04-agent-tool.sql | agent_tool | tool_meta, approval_record, tool_call_audit_log |
| 05-agent-model.sql | agent_model | model_usage_log |
| 06-agent-repo.sql | agent_repo | agent_definition |
| 07-agent-knowledge.sql | agent_knowledge | knowledge_base, knowledge_document, knowledge_version |
| 08-agent-quality.sql | agent_quality | quality_report, badcase |
| 09-agent-risk.sql | agent_risk | audit_log, event_consume_log |
| 10-clickhouse-metrics.sql | — | 指标沉淀表 |
| 11-seed-data.sql | — | 种子数据（模型目录、默认工具） |
| 12-outbox-message.sql | — | Outbox 补偿框架表 |

### 9.2 初始化流程

```powershell
cd infra/sql

# 全量初始化（首次部署）
./init-all.ps1 -DbType mysql -TenantId default

# 单库初始化
mysql -u root -p agent_memory < mysql/03-agent-memory.sql
```

---

## 10. 日常运维操作

### 10.1 扩缩容

```bash
# 手动扩容
kubectl scale deployment agent-runtime --replicas=5 -n agent-platform

# HPA 自动扩缩容（推荐）
kubectl get hpa -n agent-platform
```

### 10.2 灰度发布

```bash
# 1. 部署新版本 Canary
kubectl apply -f canary-deployment.yaml

# 2. 观察 Canary 指标
kubectl logs -f deployment/agent-runtime-canary -n agent-platform

# 3. 确认无误后全量更新
kubectl set image deployment/agent-runtime app=agent-runtime:v2 -n agent-platform

# 4. 回滚（如果出问题）
kubectl rollout undo deployment/agent-runtime -n agent-platform
```

### 10.3 优雅停机

每个服务已配置 preStop hook + Spring Boot graceful shutdown：

```yaml
# K8s lifecycle
lifecycle:
  preStop:
    exec:
      command: ["sh", "-c", "sleep 10 && curl -X POST http://localhost:8080/actuator/shutdown || true"]
```

### 10.4 日志查询

```bash
# kubectl logs
kubectl logs -f deployment/agent-runtime -n agent-platform --tail=100

# Loki 查询（LogQL）
{app="agent-runtime"} |= "ERROR" | json | line_format "{{.timestamp}} [{{.traceId}}] {{.message}}"
```

### 10.5 配置热更新

```bash
# 通过 Nacos 控制台修改配置
# 应用会自动感知 Nacos 配置变化（@RefreshScope）

# 也可以通过 API 刷新
curl -X POST http://nacos:8848/nacos/v1/cs/configs \
  -d "dataId=memory-service-prod.yml&group=SERVICE_GROUP&content=..."
```

---

## 11. 告警规则与处理

### 11.1 Prometheus 告警

| 告警名 | 条件 | 严重度 | 处置 |
|---|---|---|---|
| ServiceDown | up == 0 持续 1m | CRITICAL | 检查 Pod 状态和日志 |
| HighErrorRate | 5xx_rate > 1% 持续 5m | HIGH | 查看应用日志，检查依赖 |
| SlowResponse | p99 > 5s 持续 10m | MEDIUM | 检查数据库慢查询、JVM GC |
| MemoryPressure | memory_usage > 85% | MEDIUM | 扩容或优化内存 |
| DriftDetected | behavior_drift_score > 0.3 | MEDIUM | 查看 DriftService.GetBaseline |
| HallucinationRate | hallucination_rate > 5% | HIGH | 检查模型质量和知识库 |
| OutboxStuck | outbox_pending > 100 持续 5m | HIGH | 检查 OutboxRelay 线程和消费者 |
| CircuitBreakerOpen | circuit_open == 1 | HIGH | 检查下游服务健康状态 |

### 11.2 告警处理流程

```
告警触发
  │
  ├── CRITICAL → 立即响应（15分钟内）
  │     ├── 确认影响范围
  │     ├── 启动应急响应
  │     └── 通知相关负责人
  │
  ├── HIGH → 30分钟内响应
  │     ├── 查看相关指标和日志
  │     ├── 确定根因
  │     └── 执行修复或降级
  │
  └── MEDIUM → 工作时间内处理
        ├── 记录到运维工单
        └── 安排优化计划
```

---

## 12. 故障排查手册

### 12.1 服务启动失败

**症状**：Pod CrashLoopBackOff

**排查步骤**：

```bash
# 1. 查看 Pod 状态
kubectl describe pod <pod-name> -n agent-platform

# 2. 查看容器日志
kubectl logs <pod-name> -n agent-platform --previous

# 3. 常见原因
#    - 数据库连接失败 → 检查 DB_PASSWORD 环境变量和 MySQL 连通性
#    - Nacos 注册失败 → 检查 NACOS_SERVER_ADDR
#    - gRPC 端口冲突 → 检查端口配置
#    - OOM → 增加 resources.limits.memory
```

### 12.2 任务卡在 PENDING

**症状**：任务提交后长时间不执行

**排查步骤**：

```bash
# 1. 检查 task-orchestrator 日志
kubectl logs -f deployment/agent-task-orchestrator -n agent-platform | grep "taskId"

# 2. 检查 RocketMQ 消费者
#    查看消费者组在线状态和堆积量

# 3. 检查 Agent 是否存在
#    调用 RepoService.GetAgent 确认

# 4. 检查 model-gateway 可用性
#    调用 ModelService.ListModels 确认模型在线
```

### 12.3 内存服务超时

**症状**：Recall RPC 返回 DEADLINE_EXCEEDED

**排查步骤**：

```bash
# 1. 检查 Milvus 连接
kubectl exec -it <memory-pod> -- curl http://milvus:19530/healthz

# 2. 检查向量 Collection 状态
#    确认 Collection 已创建且索引已加载

# 3. 检查 Redis 连接
kubectl exec -it <memory-pod> -- redis-cli -h redis -a $REDIS_PASSWORD ping

# 4. 查看慢查询日志
#    检查 Milvus query 耗时
```

### 12.4 工具调用失败

**症状**：ToolEngine.Invoke 返回 INTERNAL

**排查步骤**：

```bash
# 1. 确认工具已注册
#    调用 ToolGateway.ListTools

# 2. 检查风险级别和审批状态
#    R3 工具需要已审批

# 3. 检查 Docker 沙箱
docker ps | grep sandbox
docker logs <sandbox-container>

# 4. 检查工具端点可达性
#    HTTP_API 类型需要 endpoint 可访问
```

### 12.5 gRPC 调用 UNAVAILABLE

**症状**：服务间 gRPC 调用返回 UNAVAILABLE

**排查步骤**：

```bash
# 1. 检查目标服务是否在线
kubectl get pods -n agent-platform -l app=<target-service>

# 2. 检查 K8s Service 端口
kubectl get svc <target-service> -n agent-platform

# 3. 检查 gRPC 负载均衡
#    net.devh 的 gRPC 客户端使用 Spring Cloud 注册中心发现

# 4. 检查 mTLS 证书
#    如启用 mTLS，确认证书未过期
```

### 12.6 熔断器打开

**症状**：CircuitOpenException，请求被拒绝

**排查步骤**：

```bash
# 1. 确认哪个熔断器打开
#    日志中搜索 "Circuit breaker [xxx] is OPEN"

# 2. 检查下游服务健康状态
#    确认 model-gateway / tool-engine 是否正常

# 3. 等待熔断器半开
#    默认等待 60s 后进入 HALF_OPEN 状态

# 4. 手动关闭熔断器（紧急情况）
#    通过 Actuator 端点: POST /actuator/circuitbreakers/{name}/close
```

---

## 13. 性能调优

### 13.1 JVM 参数

```bash
# 推荐生产 JVM 参数
JAVA_OPTS="-Xms2g -Xmx2g \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -XX:+HeapDumpOnOutOfMemoryError \
  -XX:HeapDumpPath=/tmp/heapdump.hprof \
  -XX:+PrintGCDetails \
  -XX:+PrintGCDateStamps \
  -Xloggc:/tmp/gc.log"
```

### 13.2 连接池

```yaml
# application.yml - MySQL 连接池
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000

# Redis 连接池
spring:
  data:
    redis:
      lettuce:
        pool:
          max-active: 16
          max-idle: 8
          min-idle: 4
```

### 13.3 gRPC 调优

```yaml
# gRPC server
grpc:
  server:
    port: 9090
    max-inbound-message-size: 10MB
    max-connection-age: 300s
    max-connection-age-grace: 30s
    keep-alive-time: 60s
    keep-alive-timeout: 20s
    permit-keep-alive-without-calls: false

# gRPC client
grpc:
  client:
    model-gateway:
      negotiation-type: plaintext
      max-inbound-message-size: 10MB
      deadline: 30s
```

### 13.4 Resilience4j 配置

```yaml
# Circuit Breaker
resilience4j:
  circuitbreaker:
    configs:
      default:
        failure-rate-threshold: 50
        slow-call-rate-threshold: 80
        slow-call-duration-threshold: 5s
        sliding-window-size: 10
        minimum-number-of-calls: 5
        wait-duration-in-open-state: 60s
        permitted-number-of-calls-in-half-open-state: 3

  # Bulkhead
  bulkhead:
    configs:
      default:
        max-concurrent-calls: 20
        max-wait-duration: 5s

  # TimeLimiter
  timelimiter:
    configs:
      default:
        timeout-duration: 30s
        cancel-running-future: true
```

### 13.5 Milvus 调优

```yaml
# 向量索引参数
milvus:
  index:
    type: HNSW
    params:
      M: 16          # 连接数（越大越精确，内存越高）
      efConstruction: 256  # 构建时搜索宽度
  search:
    ef: 128          # 查询时搜索宽度（越大越精确，越慢）
```

---

## 14. 备份与恢复

### 14.1 MySQL 备份

```bash
# 全量备份（每日）
mysqldump -u root -p --all-databases --single-transaction \
  --quick --lock-tables=false > backup_$(date +%Y%m%d).sql

# 单库备份
mysqldump -u root -p agent_memory > backup_memory_$(date +%Y%m%d).sql
```

### 14.2 Redis 备份

```bash
# RDB 快照
redis-cli -h redis -a $REDIS_PASSWORD BGSAVE

# 拷贝 dump.rdb
kubectl cp agent-platform/redis-0:/data/dump.rdb ./backup/redis_$(date +%Y%m%d).rdb
```

### 14.3 Milvus 备份

```bash
# 使用 milvus-backup 工具
milvus-backup create -n backup_$(date +%Y%m%d) --collection memory_vectors
```

### 14.4 恢复

```bash
# MySQL 恢复
mysql -u root -p < backup_20260708.sql

# Redis 恢复
# 将 dump.rdb 放入 Redis 数据目录后重启

# Milvus 恢复
milvus-backup restore -n backup_20260708
```

### 14.5 Nacos 配置备份

```bash
# 导出配置
curl "http://nacos:8848/nacos/v1/cs/configs?export=true&group=COMMON_GROUP&tenant=" \
  -o nacos_common_backup.zip

curl "http://nacos:8848/nacos/v1/cs/configs?export=true&group=SERVICE_GROUP&tenant=" \
  -o nacos_service_backup.zip
```

---

> 📖 使用手册请参考 [user-guide.md](./user-guide.md) | 设计文档请参考 [README.md](./README.md)
