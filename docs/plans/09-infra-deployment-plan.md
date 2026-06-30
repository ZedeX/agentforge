# infra 部署模块编码计划（K8s + Docker + Nacos + 可观测）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 `infra/` 目录下补齐全部部署配置：13 个微服务的 Dockerfile、本地 docker-compose 一键起、K8s Deployment+Service+HPA、Nacos 配置注入、Vault 密钥、健康检查、SkyWalking/Prometheus/Loki 可观测组件，以及 PowerShell 部署脚本。`infra/sql/` 已由 Plan 03 完成 DDL，本计划聚焦 **infra/docker/、infra/k8s/、infra/nacos/、infra/observability/、infra/scripts/** 五个子目录，与 doc 09-governance-and-deployment §12~§15 完全对齐。

**Architecture:** 13 个微服务（11 核心 + 2 横向）部署到 K8s `agent-platform-prod` 命名空间；中间件（MySQL/Milvus/Redis/RocketMQ/ES/Neo4j/MinIO/Nacos/Vault/SkyWalking/Prometheus/Loki）部署到独立 `agent-platform-infra` 命名空间。所有业务服务无状态（ADR-002），通过 HPA 弹性扩缩容。配置统一走 Nacos 配置中心（共享 + 服务级 + profile 差异化），密钥统一走 HashiCorp Vault（禁止明文环境变量）。

**Tech Stack:** Docker multi-stage build（maven:3.9-eclipse-temurin-17 → eclipse-temurin:17-jre-jammy）/ Kubernetes 1.29 / Nacos 2.x / HashiCorp Vault 1.15 / SkyWalking 9.7 Java Agent / Prometheus 2.51 + Grafana 10.4 / Loki 3.0 + Promtail / docker-compose v3.8 / PowerShell 5.1+（脚本）

---

## 设计文档对齐

| 项 | 来源 | 锁定值 |
|---|---|---|
| 13 微服务清单与端口 | doc 00-overview §3.1 | gateway:8080 / session:8082 / task-orch:8084+9090 / planning:8086+9091（合并） / memory:8088 / tool:8090 / runtime:8092 / model-gw:8094 / repo:8096 / knowledge:8098 / quality:8100 / risk:8102 / observ:8104 |
| Dockerfile 多阶段模板 | doc 09 §13.1 | maven:3.9-eclipse-temurin-17 → eclipse-temurin:17-jre-jammy，含 SkyWalking Agent 9.7.0 |
| K8s Deployment + HPA | doc 09 §13.2 / §14.3 | agent-runtime HPA 3-30，gateway 3-10，其余 2-N；ResourceQuota CPU 200C / 内存 400G |
| K8s 命名空间规划 | doc 09 §13.3 | agent-platform-dev / -staging / -prod / -infra 四套 |
| Nacos DataId/Group | doc 09 §12.1 | namespace=`agent-platform-{profile}` / Group=COMMON_GROUP（共享）+ SERVICE_GROUP（服务级） |
| bootstrap.yml 共享配置 | doc 09 §12.1 | spring.cloud.nacos.discovery + config + shared-configs 5 项 |
| Vault 密钥路径 | doc 09 §12.4 | secret/data/agent-platform/{common/*,model/*,service/*}，spring-cloud-vault K8s SA 认证 |
| SkyWalking Agent | doc 09 §11.1 | -javaagent:/skywalking/skywalking-agent.jar，X-Trace-Id 透传 |
| Prometheus 指标 | doc 09 §11.2 | /actuator/prometheus，业务埋点 Counter/Timer |
| Loki 日志采集 | doc 09 §11.3 | Logback JSON + Promtail + Loki，TraceID 注入 MDC |
| 健康检查端点 | doc 09 §13.2 | /actuator/health/readiness + /liveness + /actuator/shutdown（preStop） |
| 部署脚本风格 | infra/sql/init-all.ps1 | Write-Log 多级别 / Find-Tool 兜底路径 / -Skip 开关 / 日志文件落盘 |
| 中间件集群规模 | doc 09 §14.2 | MySQL 32 节点 / Milvus 9 / Redis 12 / RocketMQ 9 / ES 8 / Neo4j 1 / MinIO 4 / Nacos 3 / Vault 3 |
| 已完成 infra/sql | Plan 03 | mysql(11) / milvus(1) / neo4j(2) / redis(1) / clickhouse(1) DDL + init-all.ps1 编排器 |

---

## 文件结构总览

### 目标新增目录与文件

```
infra/
├── sql/                          # 已完成（Plan 03）
│   ├── mysql/  milvus/  neo4j/  redis/  logs/
│   └── init-all.ps1
├── docker/                       # T1-T2 新增
│   ├── Dockerfile.base           # 共享基础镜像（仅 JVM + SkyWalking + curl + tzdata）
│   ├── Dockerfile.template       # 通用多阶段构建模板（带 ARG SERVICE_NAME）
│   ├── Dockerfile.agent-gateway
│   ├── Dockerfile.agent-session
│   ├── Dockerfile.agent-task-orchestrator
│   ├── Dockerfile.agent-memory
│   ├── Dockerfile.agent-tool-engine
│   ├── Dockerfile.agent-runtime
│   ├── Dockerfile.agent-model-gateway
│   ├── Dockerfile.agent-repo
│   ├── Dockerfile.agent-knowledge
│   ├── Dockerfile.agent-quality
│   ├── Dockerfile.agent-risk-control
│   ├── Dockerfile.agent-observability
│   └── .dockerignore
├── docker-compose/               # T3 新增
│   ├── docker-compose.yml        # 中间件一键起（MySQL/Milvus/Redis/RocketMQ/ES/Neo4j/MinIO/Nacos/Vault）
│   ├── docker-compose-services.yml # 13 业务服务依赖中间件起
│   ├── .env.example              # 环境变量模板（不含密钥，密钥走 Vault）
│   └── README.md                 # 启动顺序说明
├── k8s/                          # T4-T8 + T13 新增
│   ├── 00-namespace.yaml         # 4 命名空间 + ResourceQuota + LimitRange
│   ├── 01-serviceaccounts.yaml   # 13 ServiceAccount + RBAC ClusterRole
│   ├── 02-configmap-bootstrap.yaml # Nacos 地址 / Namespace / profile 共享 ConfigMap
│   ├── 03-vault-config.yaml      # Vault Auth Method + K8s SA 绑定
│   ├── deployments/
│   │   ├── agent-gateway.yaml
│   │   ├── agent-session.yaml
│   │   ├── agent-task-orchestrator.yaml
│   │   ├── agent-memory.yaml
│   │   ├── agent-tool-engine.yaml
│   │   ├── agent-runtime.yaml
│   │   ├── agent-model-gateway.yaml
│   │   ├── agent-repo.yaml
│   │   ├── agent-knowledge.yaml
│   │   ├── agent-quality.yaml
│   │   ├── agent-risk-control.yaml
│   │   └── agent-observability.yaml
│   ├── services/
│   │   ├── agent-gateway-svc.yaml      # ClusterIP + Ingress
│   │   ├── agent-session-svc.yaml
│   │   ├── ...（共 12 个 ClusterIP + 1 个 gateway Ingress）
│   │   └── ingress-gateway.yaml        # Ingress + TLS
│   ├── hpa/
│   │   ├── agent-runtime-hpa.yaml      # 3-30，CPU+内存+agent_active_instances
│   │   ├── agent-gateway-hpa.yaml      # 3-10，CPU+QPS
│   │   ├── agent-model-gateway-hpa.yaml # 2-15，CPU+QPS
│   │   ├── agent-memory-hpa.yaml       # 2-10
│   │   ├── agent-tool-engine-hpa.yaml  # 2-10
│   │   └── agent-task-orchestrator-hpa.yaml # 2-8
│   └── pdb/                       # PodDisruptionBudget
│       └── agent-runtime-pdb.yaml # minAvailable: 2
├── nacos/                        # T9-T10 新增
│   ├── bootstrap-common.yml      # 所有服务 bootstrap.yml 模板
│   ├── shared/                   # COMMON_GROUP 共享配置
│   │   ├── datasource-common.yml
│   │   ├── redis-common.yml
│   │   ├── rocketmq-common.yml
│   │   ├── observability-common.yml
│   │   └── governance-rules.yml
│   ├── services/                 # SERVICE_GROUP 服务级配置
│   │   ├── task-orchestrator.yml
│   │   ├── task-orchestrator-prod.yml
│   │   ├── memory-service.yml
│   │   ├── memory-service-prod.yml
│   │   └── ...（13 服务 × dev/prod 双 profile）
│   ├── import-nacos.ps1          # 批量导入脚本
│   └── README.md
├── vault/                        # T11 新增
│   ├── vault-policies/           # Vault Policy
│   │   ├── agent-runtime.hcl
│   │   ├── agent-model-gateway.hcl
│   │   └── ...（13 服务）
│   ├── vault-seeds.sh            # 初始化密钥脚本（开发环境用）
│   └── README.md
├── observability/                # T12-T14 新增
│   ├── skywalking/
│   │   ├── agent.config          # SkyWalking Agent 全局配置
│   │   ├── application.yml       # OAP 配置
│   │   └── custom-plugin/       # X-Trace-Id 透传插件
│   ├── prometheus/
│   │   ├── prometheus.yml        # 抓取配置
│   │   ├── alerts/
│   │   │   ├── service-availability.yml
│   │   │   ├── hallucination-rate.yml
│   │   │   └── drift-alerts.yml
│   │   └── recording-rules.yml
│   ├── grafana/
│   │   ├── dashboards/
│   │   │   ├── agent-platform-overview.json
│   │   │   ├── agent-runtime-detail.json
│   │   │   ├── hallucination-drift.json
│   │   │   └── cost-quota.json
│   │   └── datasources/
│   │       ├── prometheus.yml
│   │       └── loki.yml
│   └── loki/
│       ├── loki-config.yml
│       └── promtail-config.yml
└── scripts/                      # T15 新增
    ├── build-all.ps1             # Maven 全量构建 + Docker 镜像构建
    ├── deploy.ps1                # K8s 一键部署（apply + rollout）
    ├── deploy-middleware.ps1     # 中间件 Helm 部署
    ├── health-check.ps1          # 部署后健康检查
    └── logs/                     # 脚本日志输出目录
```

### 已完成文件（Plan 03，不在本计划范围）

| 文件 | 职责 |
|---|---|
| `infra/sql/init-all.ps1` | 5 中间件 DDL 编排器（MySQL/ClickHouse/Milvus/Neo4j/Redis） |
| `infra/sql/mysql/*.sql` | 9 业务库 + 1 ClickHouse + 1 种子数据 |
| `infra/sql/milvus/01-init-collections.py` | 6 Milvus Collections |
| `infra/sql/neo4j/*.cypher` | Neo4j 约束 + 关系 |
| `infra/sql/redis/01-init-data.redis` | Redis 热配置 |

---

## 依赖准备（前置）

### Step P.1: 确认 Maven 多模块可构建

Run: `mvn -pl agent-common,agent-proto,agent-gateway,agent-session,agent-task-orchestrator,agent-memory,agent-tool-engine,agent-runtime,agent-quality -am package -DskipTests -q`

Expected: `BUILD SUCCESS`（若部分模块未实现，记录跳过列表，T2 Dockerfile 用通用模板兜底）

### Step P.2: 创建 infra 子目录骨架

```powershell
$infra = "e:\git\Agent-Platform-Prototype\infra"
@("docker","docker-compose","k8s","k8s\deployments","k8s\services","k8s\hpa","k8s\pdb","nacos","nacos\shared","nacos\services","vault","vault\vault-policies","observability","observability\skywalking","observability\prometheus","observability\prometheus\alerts","observability\grafana\dashboards","observability\grafana\datasources","observability\loki","scripts","scripts\logs") | ForEach-Object {
    $p = Join-Path $infra $_
    if (-not (Test-Path $p)) { New-Item -ItemType Directory -Path $p -Force | Out-Null }
}
```

### Step P.3: 工具链确认

| 工具 | 用途 | 检查命令 | 兜底路径 |
|---|---|---|---|
| docker | 镜像构建 | `docker --version` | Docker Desktop |
| kubectl | K8s 部署 | `kubectl version --client` | - |
| helm | 中间件部署 | `helm version` | D:\_program\ |
| mysql client | 健康检查 | `Get-Command mysql` | D:\_program\mariadb\bin\mysql.exe |
| jq | 配置解析 | `Get-Command jq` | - |

---

## Task 1: 共享 Dockerfile 基础镜像与模板

**Files:**
- Create: `infra/docker/Dockerfile.base`
- Create: `infra/docker/Dockerfile.template`
- Create: `infra/docker/.dockerignore`

**对齐设计：** doc 09 §13.1 多阶段构建（maven 编译 → jre 运行），SkyWalking Agent 9.7.0 嵌入。

- [ ] **Step 1.1: 创建 Dockerfile.base（共享运行时基础镜像）**

文件路径：`e:\git\Agent-Platform-Prototype\infra\docker\Dockerfile.base`

```dockerfile
# Shared runtime base image for all agent-platform services.
# Builds SkyWalking Agent + JRE 17 + common tools once, reused by per-service Dockerfiles.
# Build: docker build -t agentplatform/base:17-jre-skywalking-9.7 -f Dockerfile.base .

FROM eclipse-temurin:17-jre-jammy

LABEL maintainer="agent-platform-team"
LABEL base="agent-platform-runtime"

# Install curl (healthcheck) + tzdata + ca-certificates; download SkyWalking Agent 9.7.0
ARG SKYWALKING_VERSION=9.7.0
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl tzdata ca-certificates \
    && rm -rf /var/lib/apt/lists/* \
    && curl -fsSL https://archive.apache.org/dist/skywalking/java-agent/${SKYWALKING_VERSION}/apache-skywalking-java-agent-${SKYWALKING_VERSION}.tgz \
       | tar -xzf - -C /opt \
    && ln -s /opt/skywalking-agent /skywalking \
    && mkdir -p /var/log/agent-platform /app

ENV TZ=Asia/Shanghai
ENV LANG=C.UTF-8
ENV JAVA_OPTS_BASE="-XX:+UseZGC -XX:+ZGenerational \
    -XX:MaxRAMPercentage=75 \
    -XX:InitialRAMPercentage=50 \
    -XX:+HeapDumpOnOutOfMemoryError \
    -XX:HeapDumpPath=/var/log/agent-platform/heapdump \
    -javaagent:/skywalking/skywalking-agent.jar"

WORKDIR /app
```

- [ ] **Step 1.2: 创建 Dockerfile.template（通用多阶段构建模板）**

文件路径：`e:\git\Agent-Platform-Prototype\infra\docker\Dockerfile.template`

```dockerfile
# Template for per-service Dockerfile. Use ARG SERVICE_NAME to build a specific service.
# Build: docker build -t agentplatform/<svc>:<tag> --build-arg SERVICE_NAME=agent-runtime -f Dockerfile.template .

# ---------- Stage 1: Maven build ----------
FROM maven:3.9-eclipse-temurin-17 AS builder

ARG SERVICE_NAME
ENV SERVICE_NAME=${SERVICE_NAME}

WORKDIR /build

# Copy parent pom + all module poms first to leverage Docker layer cache
COPY pom.xml .
COPY agent-common/pom.xml agent-common/
COPY agent-proto/pom.xml agent-proto/
COPY agent-gateway/pom.xml agent-gateway/
COPY agent-session/pom.xml agent-session/
COPY agent-task-orchestrator/pom.xml agent-task-orchestrator/
COPY agent-memory/pom.xml agent-memory/
COPY agent-tool-engine/pom.xml agent-tool-engine/
COPY agent-runtime/pom.xml agent-runtime/
COPY agent-model-gateway/pom.xml agent-model-gateway/
COPY agent-repo/pom.xml agent-repo/
COPY agent-knowledge/pom.xml agent-knowledge/
COPY agent-quality/pom.xml agent-quality/
COPY agent-risk-control/pom.xml agent-risk-control/
COPY agent-observability/pom.xml agent-observability/

# Resolve dependencies offline (cached layer)
RUN mvn -B -q dependency:go-offline -pl agent-common,agent-proto -am || true

# Copy all sources
COPY agent-common/src agent-common/src
COPY agent-proto/src agent-proto/src
COPY agent-gateway/src agent-gateway/src
COPY agent-session/src agent-session/src
COPY agent-task-orchestrator/src agent-task-orchestrator/src
COPY agent-memory/src agent-memory/src
COPY agent-tool-engine/src agent-tool-engine/src
COPY agent-runtime/src agent-runtime/src
COPY agent-model-gateway/src agent-model-gateway/src
COPY agent-repo/src agent-repo/src
COPY agent-knowledge/src agent-knowledge/src
COPY agent-quality/src agent-quality/src
COPY agent-risk-control/src agent-risk-control/src
COPY agent-observability/src agent-observability/src

# Build the target service and its dependencies
RUN mvn -B -q package -pl ${SERVICE_NAME} -am -DskipTests

# ---------- Stage 2: Runtime ----------
FROM agentplatform/base:17-jre-skywalking-9.7

ARG SERVICE_NAME
ENV SPRING_APPLICATION_NAME=${SERVICE_NAME}

LABEL service=${SERVICE_NAME}
LABEL maintainer="agent-platform-team"

WORKDIR /app

# Copy the built jar (use wildcard to handle version suffix)
COPY --from=builder /build/${SERVICE_NAME}/target/${SERVICE_NAME}-*.jar app.jar

# Service-specific ports injected at per-service Dockerfile level
# EXPOSE declared in derived image

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS_BASE $JAVA_OPTS -jar app.jar"]
```

- [ ] **Step 1.3: 创建 .dockerignore**

文件路径：`e:\git\Agent-Platform-Prototype\infra\docker\.dockerignore`

```
# Build artifacts
**/target/
**/*.class

# IDE files
**/.idea/
**/.vscode/
**/*.iml
**/.settings/

# Logs
**/logs/
**/*.log

# Git
**/.git/
**/.gitignore

# Docs (not needed in image)
docs/
**/*.md

# Test artifacts
**/test-results/
**/coverage/

# Temp files
tmp/
ci-logs/
**/.DS_Store
```

- [ ] **Step 1.4: 构建基础镜像并验证**

```powershell
docker build -t agentplatform/base:17-jre-skywalking-9.7 -f e:\git\Agent-Platform-Prototype\infra\docker\Dockerfile.base e:\git\Agent-Platform-Prototype\infra\docker
docker image inspect agentplatform/base:17-jre-skywalking-9.7 --format '{{.Config.Env}}'
```

Expected: 镜像构建成功，环境变量含 `JAVA_OPTS_BASE` 与 `TZ=Asia/Shanghai`

**验收标准（Task 1）：**
- [ ] `Dockerfile.base` 构建出 `agentplatform/base:17-jre-skywalking-9.7` 镜像
- [ ] 镜像内 `/skywalking/skywalking-agent.jar` 存在
- [ ] `Dockerfile.template` 支持 `--build-arg SERVICE_NAME=` 参数化构建任意服务
- [ ] `.dockerignore` 排除 target/docs/logs/git 目录

---

## Task 2: 各微服务 Dockerfile

**Files:**
- Create: `infra/docker/Dockerfile.{13 个服务名}`

**对齐设计：** doc 00-overview §3.1 端口表 + doc 09 §13.1 HEALTHCHECK 端口。

13 个服务的 Dockerfile 极简（继承 template + 声明 EXPOSE 与 HEALTHCHECK）：

| 服务 | HTTP 端口 | gRPC 端口 | Dockerfile 文件名 |
|---|---|---|---|
| agent-gateway | 8080 | - | Dockerfile.agent-gateway |
| agent-session | 8082 | - | Dockerfile.agent-session |
| agent-task-orchestrator | 8084 | 9090 | Dockerfile.agent-task-orchestrator |
| agent-memory | 8088 | 9088 | Dockerfile.agent-memory |
| agent-tool-engine | 8090 | 9090 | Dockerfile.agent-tool-engine |
| agent-runtime | 8092 | 9092 | Dockerfile.agent-runtime |
| agent-model-gateway | 8094 | 9094 | Dockerfile.agent-model-gateway |
| agent-repo | 8096 | 9096 | Dockerfile.agent-repo |
| agent-knowledge | 8098 | 9098 | Dockerfile.agent-knowledge |
| agent-quality | 8100 | 9100 | Dockerfile.agent-quality |
| agent-risk-control | 8102 | 9102 | Dockerfile.agent-risk-control |
| agent-observability | 8104 | - | Dockerfile.agent-observability |
| agent-planning | (合并入 task-orchestrator，不单独出 Dockerfile) | - | - |

- [ ] **Step 2.1: 为每个服务创建 Dockerfile（以 agent-runtime 为例）**

文件路径：`e:\git\Agent-Platform-Prototype\infra\docker\Dockerfile.agent-runtime`

```dockerfile
# Agent Runtime service (ReAct loop, stateless, HPA 3-30).
# Build: docker build -t agentplatform/agent-runtime:1.0.0 -f Dockerfile.agent-runtime .

FROM agentplatform/base:17-jre-skywalking-9.7

LABEL service="agent-runtime"
LABEL tier="runtime"

# SkyWalking service name
ENV SW_AGENT_NAME="agent-platform-agent-runtime"
ENV SW_COLLECTOR_BACKEND_SERVICE="skywalking-oap.agent-platform-infra:11800"

EXPOSE 8092 9092

# Volume for heap dumps and logs
VOLUME ["/var/log/agent-platform"]

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -fs http://localhost:8092/actuator/health || exit 1

# Copy the built jar from template's builder stage
# Re-build via template: docker build -t agentplatform/agent-runtime:1.0.0 \
#   --build-arg SERVICE_NAME=agent-runtime -f Dockerfile.template .
```

- [ ] **Step 2.2: 批量生成其余 11 个服务 Dockerfile**

为每个服务生成对应 Dockerfile，调整：
1. `LABEL service=` 与文件名一致
2. `SW_AGENT_NAME=agent-platform-<service-name>`
3. `EXPOSE` 端口按上表填入
4. `HEALTHCHECK` 中的端口改为该服务的 HTTP 端口

可使用 PowerShell 脚本批量生成：

```powershell
$services = @(
    @{name="agent-gateway"; http=8080; grpc=$null},
    @{name="agent-session"; http=8082; grpc=$null},
    @{name="agent-task-orchestrator"; http=8084; grpc=9090},
    @{name="agent-memory"; http=8088; grpc=9088},
    @{name="agent-tool-engine"; http=8090; grpc=9090},
    @{name="agent-runtime"; http=8092; grpc=9092},
    @{name="agent-model-gateway"; http=8094; grpc=9094},
    @{name="agent-repo"; http=8096; grpc=9096},
    @{name="agent-knowledge"; http=8098; grpc=9098},
    @{name="agent-quality"; http=8100; grpc=9100},
    @{name="agent-risk-control"; http=8102; grpc=9102},
    @{name="agent-observability"; http=8104; grpc=$null}
)
$dir = "e:\git\Agent-Platform-Prototype\infra\docker"
foreach ($svc in $services) {
    $expose = "EXPOSE $($svc.http)"
    if ($svc.grpc) { $expose += " $($svc.grpc)" }
    $content = @"
FROM agentplatform/base:17-jre-skywalking-9.7
LABEL service="$($svc.name)"
LABEL tier="service"
ENV SW_AGENT_NAME="agent-platform-$($svc.name)"
ENV SW_COLLECTOR_BACKEND_SERVICE="skywalking-oap.agent-platform-infra:11800"
$expose
VOLUME ["/var/log/agent-platform"]
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 `
  CMD curl -fs http://localhost:$($svc.http)/actuator/health || exit 1
"@
    Set-Content -Path (Join-Path $dir "Dockerfile.$($svc.name)") -Value $content -Encoding UTF8
}
```

- [ ] **Step 2.3: 验证一个服务的完整构建（agent-runtime）**

```powershell
cd e:\git\Agent-Platform-Prototype
docker build -t agentplatform/agent-runtime:test -f infra\docker\Dockerfile.template --build-arg SERVICE_NAME=agent-runtime .
docker image inspect agentplatform/agent-runtime:test --format '{{.Config.Labels.service}}'
```

Expected: 镜像构建成功，`service=agent-runtime` 标签正确

**验收标准（Task 2）：**
- [ ] 12 个 `Dockerfile.<service>` 文件齐全（planning 合并入 task-orchestrator）
- [ ] 每个 Dockerfile 含正确的 `SW_AGENT_NAME` 与 `EXPOSE`
- [ ] 使用 `Dockerfile.template --build-arg SERVICE_NAME=agent-runtime` 可成功构建镜像
- [ ] `HEALTHCHECK` 端口与 EXPOSE 端口一致

---

## Task 3: docker-compose 本地开发环境

**Files:**
- Create: `infra/docker-compose/docker-compose.yml`（中间件一键起）
- Create: `infra/docker-compose/docker-compose-services.yml`（业务服务起）
- Create: `infra/docker-compose/.env.example`
- Create: `infra/docker-compose/README.md`

**对齐设计：** doc 09 §14.2 中间件清单 + doc 09 §12.4 Vault 路径。本地开发环境用单节点简化版（不部署集群）。

- [ ] **Step 3.1: 创建 docker-compose.yml（中间件）**

文件路径：`e:\git\Agent-Platform-Prototype\infra\docker-compose\docker-compose.yml`

```yaml
# Local development middleware stack for Agent Platform.
# Start: docker-compose -f docker-compose.yml up -d
# Stop:  docker-compose -f docker-compose.yml down
# Volumes persisted under ./volumes/<service>/

version: "3.8"

networks:
  agent-platform:
    driver: bridge

volumes:
  mysql-data:
  milvus-data:
  redis-data:
  rocketmq-data:
  es-data:
  neo4j-data:
  minio-data:
  nacos-data:
  vault-data:

services:
  # ===== MySQL 8.0.36 =====
  mysql:
    image: mysql:8.0.36
    container_name: ap-mysql
    restart: unless-stopped
    environment:
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD:-agentplatform}
      MYSQL_DATABASE: agent_platform
      TZ: Asia/Shanghai
    ports:
      - "3306:3306"
    volumes:
      - mysql-data:/var/lib/mysql
      - ../sql/mysql:/docker-entrypoint-initdb.d:ro
    command: --character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci --default-authentication-plugin=mysql_native_password
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost", "-p${MYSQL_ROOT_PASSWORD:-agentplatform}"]
      interval: 10s
      timeout: 5s
      retries: 10
    networks: [agent-platform]

  # ===== Redis 7.2 =====
  redis:
    image: redis:7.2-alpine
    container_name: ap-redis
    restart: unless-stopped
    ports: ["6379:6379"]
    volumes:
      - redis-data:/data
    command: redis-server --appendonly yes --requirepass ${REDIS_PASSWORD:-agentplatform}
    healthcheck:
      test: ["CMD", "redis-cli", "-a", "${REDIS_PASSWORD:-agentplatform}", "ping"]
      interval: 5s
      timeout: 3s
      retries: 5
    networks: [agent-platform]

  # ===== Milvus 2.4 (standalone) =====
  milvus-etcd:
    image: quay.io/coreos/etcd:v3.5.5
    container_name: ap-milvus-etcd
    environment:
      ETCD_AUTO_COMPACTION_MODE: revision
      ETCD_AUTO_COMPACTION_RETENTION: "1000"
      ETCD_QUOTA_BACKEND_BYTES: "4294967296"
    volumes: [milvus-data:/etcd]
    command: etcd -advertise-client-urls=http://etcd:2379 -listen-client-urls http://0.0.0.0:2379
    networks: [agent-platform]

  milvus-minio:
    image: minio/minio:RELEASE.2024-03-30T09-41-56Z
    container_name: ap-milvus-minio
    environment:
      MINIO_ACCESS_KEY: minioadmin
      MINIO_SECRET_KEY: minioadmin
    volumes: [milvus-data:/minio_data"]
    command: minio server /minio_data
    networks: [agent-platform]

  milvus:
    image: milvusdb/milvus:v2.4.0
    container_name: ap-milvus
    depends_on: [milvus-etcd, milvus-minio]
    ports: ["19530:19530", "9091:9091"]
    environment:
      ETCD_ENDPOINTS: milvus-etcd:2379
      MINIO_ADDRESS: milvus-minio:9000
    volumes: [milvus-data:/var/lib/milvus"]
    command: ["milvus", "run", "standalone"]
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:9091/healthz"]
      interval: 30s
      timeout: 10s
      retries: 10
    networks: [agent-platform]

  # ===== RocketMQ 5.3 (single broker) =====
  rocketmq-namesrv:
    image: apache/rocketmq:5.3.0
    container_name: ap-rocketmq-namesrv
    ports: ["9876:9876"]
    command: sh mqnamesrv
    networks: [agent-platform]

  rocketmq-broker:
    image: apache/rocketmq:5.3.0
    container_name: ap-rocketmq-broker
    depends_on: [rocketmq-namesrv]
    ports: ["10909:10909", "10911:10911"]
    environment:
      NAMESRV_ADDR: rocketmq-namesrv:9876
    command: sh mqbroker -n rocketmq-namesrv:9876 --enable-proxy
    volumes: [rocketmq-data:/home/rocketmq/store"]
    networks: [agent-platform]

  # ===== Elasticsearch 8.13 (single node) =====
  elasticsearch:
    image: docker.elastic.co/elasticsearch/elasticsearch:8.13.4
    container_name: ap-elasticsearch
    environment:
      - discovery.type=single-node
      - xpack.security.enabled=false
      - ES_JAVA_OPTS=-Xms1g -Xmx1g
    ports: ["9200:9200", "9300:9300"]
    volumes: [es-data:/usr/share/elasticsearch/data"]
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:9200/_cluster/health"]
      interval: 30s
      timeout: 10s
      retries: 10
    networks: [agent-platform]

  # ===== Neo4j 5.18 =====
  neo4j:
    image: neo4j:5.18-community
    container_name: ap-neo4j
    environment:
      NEO4J_AUTH: neo4j/${NEO4J_PASSWORD:-agentplatform}
    ports: ["7474:7474", "7687:7687"]
    volumes: [neo4j-data:/data"]
    networks: [agent-platform]

  # ===== MinIO (for app object storage, separate from Milvus's) =====
  minio:
    image: minio/minio:RELEASE.2024-03-30T09-41-56Z
    container_name: ap-minio
    environment:
      MINIO_ROOT_USER: ${MINIO_ROOT_USER:-agentplatform}
      MINIO_ROOT_PASSWORD: ${MINIO_ROOT_PASSWORD:-agentplatform}
    ports: ["9000:9000", "9001:9001"]
    volumes: [minio-data:/data"]
    command: server /data --console-address ":9001"
    networks: [agent-platform]

  # ===== Nacos 2.x (standalone) =====
  nacos:
    image: nacos/nacos-server:v2.3.0
    container_name: ap-nacos
    environment:
      MODE: standalone
      JVM_XMS: "512m"
      JVM_XMX: "512m"
      NACOS_AUTH_ENABLE: "true"
      NACOS_AUTH_TOKEN: ${NACOS_AUTH_TOKEN:-agentplatform-secret-key-2026}
      NACOS_AUTH_IDENTITY_KEY: serverIdentity
      NACOS_AUTH_IDENTITY_VALUE: agentplatform
    ports: ["8848:8848", "9848:9848"]
    volumes: [nacos-data:/home/nacos/data"]
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8848/nacos/v1/console/health/readiness"]
      interval: 10s
      timeout: 5s
      retries: 10
    networks: [agent-platform]

  # ===== HashiCorp Vault (dev mode) =====
  vault:
    image: hashicorp/vault:1.15.2
    container_name: ap-vault
    environment:
      VAULT_DEV_ROOT_TOKEN_ID: ${VAULT_DEV_TOKEN:-agentplatform}
      VAULT_DEV_LISTEN_ADDRESS: 0.0.0.0:8200
    ports: ["8200:8200"]
    cap_add: ["IPC_LOCK"]
    volumes: [vault-data:/vault/file"]
    networks: [agent-platform]

  # ===== SkyWalking OAP 9.7 =====
  skywalking-oap:
    image: apache/skywalking-oap-server:9.7.0
    container_name: ap-skywalking-oap
    environment:
      SW_STORAGE: elasticsearch
      SW_STORAGE_ES_CLUSTER_NODES: elasticsearch:9200
      SW_HEALTH_CHECKER: default
    ports: ["11800:11800", "12800:12800"]
    depends_on: [elasticsearch]
    healthcheck:
      test: ["CMD", "/bin/sh", "-c", "/skywalking/bin/swctl ch || exit 1"]
      interval: 30s
      timeout: 15s
      retries: 15
    networks: [agent-platform]

  skywalking-ui:
    image: apache/skywalking-ui:9.7.0
    container_name: ap-skywalking-ui
    environment:
      SW_OAP_ADDRESS: http://skywalking-oap:12800
    ports: ["8080:8080"]
    depends_on: [skywalking-oap]
    networks: [agent-platform]

  # ===== Prometheus + Grafana =====
  prometheus:
    image: prom/prometheus:v2.51.2
    container_name: ap-prometheus
    ports: ["9090:9090"]
    volumes:
      - ../observability/prometheus/prometheus.yml:/etc/prometheus/prometheus.yml:ro
      - ../observability/prometheus/alerts:/etc/prometheus/rules:ro
    networks: [agent-platform]

  grafana:
    image: grafana/grafana:10.4.0
    container_name: ap-grafana
    environment:
      GF_SECURITY_ADMIN_PASSWORD: ${GRAFANA_PASSWORD:-agentplatform}
    ports: ["3000:3000"]
    volumes:
      - ../observability/grafana/dashboards:/var/lib/grafana/dashboards:ro
      - ../observability/grafana/datasources:/etc/grafana/provisioning/datasources:ro
    depends_on: [prometheus]
    networks: [agent-platform]

  # ===== Loki + Promtail =====
  loki:
    image: grafana/loki:3.0.0
    container_name: ap-loki
    ports: ["3100:3100"]
    volumes:
      - ../observability/loki/loki-config.yml:/etc/loki/local-config.yaml:ro
    command: -config.file=/etc/loki/local-config.yaml
    networks: [agent-platform]

  promtail:
    image: grafana/promtail:3.0.0
    container_name: ap-promtail
    volumes:
      - /var/lib/docker/containers:/var/lib/docker/containers:ro
      - /var/log:/var/log:ro
      - ../observability/loki/promtail-config.yml:/etc/promtail/promtail.yml:ro
    command: -config.file=/etc/promtail/promtail.yml
    networks: [agent-platform]
```

- [ ] **Step 3.2: 创建 .env.example**

文件路径：`e:\git\Agent-Platform-Prototype\infra\docker-compose\.env.example`

```
# Agent Platform docker-compose environment variables.
# Copy to .env and override values. NEVER commit real .env with secrets.

MYSQL_ROOT_PASSWORD=agentplatform
REDIS_PASSWORD=agentplatform
NEO4J_PASSWORD=agentplatform
MINIO_ROOT_USER=agentplatform
MINIO_ROOT_PASSWORD=agentplatform
NACOS_AUTH_TOKEN=agentplatform-secret-key-2026-please-change
VAULT_DEV_TOKEN=agentplatform
GRAFANA_PASSWORD=agentplatform

# Service image tag (filled by build script)
IMAGE_TAG=latest
```

- [ ] **Step 3.3: 创建 docker-compose-services.yml（业务服务）**

文件路径：`e:\git\Agent-Platform-Prototype\infra\docker-compose\docker-compose-services.yml`

```yaml
# Local business services stack. Depends on docker-compose.yml middleware running.
# Start: docker-compose -f docker-compose-services.yml up -d
# Image tag from .env IMAGE_TAG, default latest.

version: "3.8"

networks:
  agent-platform:
    external: true
    name: agent-platform_default

x-service-defaults: &service-defaults
  restart: unless-stopped
  environment:
    SPRING_PROFILES_ACTIVE: dev
    NACOS_ADDR: nacos:8848
    SPRING_CLOUD_NACOS_DISCOVERY_NAMESPACE: agent-platform-dev
    MYSQL_HOST: mysql
    REDIS_HOST: redis
    MILVUS_HOST: milvus
    ROCKETMQ_NAMESRV: rocketmq-namesrv:9876
    ES_HOST: elasticsearch
    NEO4J_HOST: neo4j
    MINIO_ENDPOINT: http://minio:9000
    SW_AGENT_COLLECTOR_BACKEND_SERVICES: skywalking-oap:11800
    HOSTNAME_HASH: 1
  networks: [agent-platform]

services:
  agent-gateway:
    <<: *service-defaults
    image: agentplatform/agent-gateway:${IMAGE_TAG:-latest}
    container_name: ap-agent-gateway
    ports: ["8080:8080"]
    depends_on: [mysql, redis, nacos]

  agent-session:
    <<: *service-defaults
    image: agentplatform/agent-session:${IMAGE_TAG:-latest}
    container_name: ap-agent-session
    ports: ["8082:8082"]
    depends_on: [mysql, redis, nacos]

  agent-task-orchestrator:
    <<: *service-defaults
    image: agentplatform/agent-task-orchestrator:${IMAGE_TAG:-latest}
    container_name: ap-agent-task-orchestrator
    ports: ["8084:8084", "9090:9090"]
    depends_on: [mysql, redis, nacos, rocketmq-namesrv]

  agent-memory:
    <<: *service-defaults
    image: agentplatform/agent-memory:${IMAGE_TAG:-latest}
    container_name: ap-agent-memory
    ports: ["8088:8088", "9088:9088"]
    depends_on: [mysql, redis, nacos, milvus]

  agent-tool-engine:
    <<: *service-defaults
    image: agentplatform/agent-tool-engine:${IMAGE_TAG:-latest}
    container_name: ap-agent-tool-engine
    ports: ["8090:8090"]
    depends_on: [mysql, redis, nacos, minio]

  agent-runtime:
    <<: *service-defaults
    image: agentplatform/agent-runtime:${IMAGE_TAG:-latest}
    container_name: ap-agent-runtime
    ports: ["8092:8092", "9092:9092"]
    depends_on: [redis, nacos]

  agent-model-gateway:
    <<: *service-defaults
    image: agentplatform/agent-model-gateway:${IMAGE_TAG:-latest}
    container_name: ap-agent-model-gateway
    ports: ["8094:8094", "9094:9094"]
    depends_on: [redis, nacos]

  agent-repo:
    <<: *service-defaults
    image: agentplatform/agent-repo:${IMAGE_TAG:-latest}
    container_name: ap-agent-repo
    ports: ["8096:8096"]
    depends_on: [mysql, nacos, minio]

  agent-knowledge:
    <<: *service-defaults
    image: agentplatform/agent-knowledge:${IMAGE_TAG:-latest}
    container_name: ap-agent-knowledge
    ports: ["8098:8098"]
    depends_on: [mysql, nacos, milvus, minio, elasticsearch]

  agent-quality:
    <<: *service-defaults
    image: agentplatform/agent-quality:${IMAGE_TAG:-latest}
    container_name: ap-agent-quality
    ports: ["8100:8100"]
    depends_on: [mysql, nacos]

  agent-risk-control:
    <<: *service-defaults
    image: agentplatform/agent-risk-control:${IMAGE_TAG:-latest}
    container_name: ap-agent-risk-control
    ports: ["8102:8102"]
    depends_on: [mysql, nacos]

  agent-observability:
    <<: *service-defaults
    image: agentplatform/agent-observability:${IMAGE_TAG:-latest}
    container_name: ap-agent-observability
    ports: ["8104:8104"]
    depends_on: [nacos]
```

- [ ] **Step 3.4: 创建 README.md**

文件路径：`e:\git\Agent-Platform-Prototype\infra\docker-compose\README.md`

```markdown
# Agent Platform docker-compose 本地开发环境

## 启动顺序

### 1. 初始化数据库（首次）
```powershell
cd e:\git\Agent-Platform-Prototype\infra\sql
.\init-all.ps1 -MySqlPassword agentplatform -Neo4jPassword agentplatform -RedisPassword agentplatform -MilvusHost localhost -Neo4jHost localhost -RedisHost localhost
```

### 2. 启动中间件
```powershell
cd e:\git\Agent-Platform-Prototype\infra\docker-compose
Copy-Item .env.example .env   # 首次
docker-compose -f docker-compose.yml up -d
docker-compose -f docker-compose.yml ps   # 等待所有 healthy
```

### 3. 构建业务服务镜像
```powershell
cd e:\git\Agent-Platform-Prototype
.\infra\scripts\build-all.ps1 -SkipTests
```

### 4. 启动业务服务
```powershell
docker-compose -f docker-compose-services.yml up -d
docker-compose -f docker-compose-services.yml ps
```

## 访问端点

| 服务 | URL |
|---|---|
| Nacos Console | http://localhost:8848/nacos (nacos/nacos) |
| Grafana | http://localhost:3000 (admin/agentplatform) |
| Prometheus | http://localhost:9090 |
| SkyWalking UI | http://localhost:8080 |
| MinIO Console | http://localhost:9001 |
| Neo4j Browser | http://localhost:7474 |
| Elasticsearch | http://localhost:9200 |
| agent-gateway | http://localhost:8080 |

## 停止与清理
```powershell
docker-compose -f docker-compose-services.yml down
docker-compose -f docker-compose.yml down
# 清理卷（谨慎）
# docker volume rm agent-platform_mysql-data agent-platform_redis-data ...
```
```

- [ ] **Step 3.5: 验证 docker-compose 配置语法**

```powershell
cd e:\git\Agent-Platform-Prototype\infra\docker-compose
docker-compose -f docker-compose.yml config --quiet
docker-compose -f docker-compose-services.yml config --quiet
```

Expected: 两条命令均无输出（配置语法正确）

**验收标准（Task 3）：**
- [ ] `docker-compose config` 两个文件均通过语法检查
- [ ] 中间件 compose 含 14 个服务（MySQL/Redis/Milvus×3/RocketMQ×2/ES/Neo4j/MinIO/Nacos/Vault/SkyWalking×2/Prometheus/Grafana/Loki/Promtail）
- [ ] 业务服务 compose 含 12 个服务，依赖关系正确
- [ ] `.env.example` 不含真实密钥，仅占位
- [ ] README 含完整启动顺序

---

## Task 4: K8s Namespace + ResourceQuota + ServiceAccount

**Files:**
- Create: `infra/k8s/00-namespace.yaml`
- Create: `infra/k8s/01-serviceaccounts.yaml`

**对齐设计：** doc 09 §13.3 四命名空间 + §14.3 ResourceQuota + §12.4 Vault K8s SA 认证。

- [ ] **Step 4.1: 创建 00-namespace.yaml**

文件路径：`e:\git\Agent-Platform-Prototype\infra\k8s\00-namespace.yaml`

```yaml
# Four namespaces per doc 09 §13.3.
# Apply: kubectl apply -f 00-namespace.yaml

---
apiVersion: v1
kind: Namespace
metadata:
  name: agent-platform-dev
  labels:
    app.kubernetes.io/part-of: agent-platform
    env: dev
---
apiVersion: v1
kind: Namespace
metadata:
  name: agent-platform-staging
  labels:
    app.kubernetes.io/part-of: agent-platform
    env: staging
---
apiVersion: v1
kind: Namespace
metadata:
  name: agent-platform-prod
  labels:
    app.kubernetes.io/part-of: agent-platform
    env: prod
---
apiVersion: v1
kind: Namespace
metadata:
  name: agent-platform-infra
  labels:
    app.kubernetes.io/part-of: agent-platform
    env: infra

# ResourceQuota for prod namespace (doc 09 §13.3)
---
apiVersion: v1
kind: ResourceQuota
metadata:
  name: agent-platform-prod-quota
  namespace: agent-platform-prod
spec:
  hard:
    requests.cpu: "200"
    requests.memory: 400Gi
    limits.cpu: "400"
    limits.memory: 800Gi
    persistentvolumeclaims: "20"
    services.loadbalancers: "2"
    pods: "100"
---
apiVersion: v1
kind: LimitRange
metadata:
  name: agent-platform-prod-limits
  namespace: agent-platform-prod
spec:
  limits:
    - type: Container
      defaultRequest:
        cpu: "200m"
        memory: "512Mi"
      default:
        cpu: "1000m"
        memory: "2Gi"
      max:
        cpu: "4000m"
        memory: "8Gi"
---
apiVersion: v1
kind: ResourceQuota
metadata:
  name: agent-platform-infra-quota
  namespace: agent-platform-infra
spec:
  hard:
    requests.cpu: "100"
    requests.memory: 300Gi
    persistentvolumeclaims: "40"
```

- [ ] **Step 4.2: 创建 01-serviceaccounts.yaml**

文件路径：`e:\git\Agent-Platform-Prototype\infra\k8s\01-serviceaccounts.yaml`

```yaml
# ServiceAccount per service (for Vault K8s auth + RBAC).
# Apply: kubectl apply -f 01-serviceaccounts.yaml

# RBAC: each service can read its own configmaps/secrets in its namespace
---
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: agent-platform-service-reader
  namespace: agent-platform-prod
rules:
  - apiGroups: [""]
    resources: ["configmaps", "secrets", "pods"]
    verbs: ["get", "list", "watch"]
  - apiGroups: [""]
    resources: ["pods/log"]
    verbs: ["get", "list"]
---
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: agent-platform-service-reader-binding
  namespace: agent-platform-prod
subjects:
  - kind: Group
    name: system:serviceaccounts:agent-platform-prod
    apiGroup: rbac.authorization.k8s.io
roleRef:
  kind: Role
  name: agent-platform-service-reader
  apiGroup: rbac.authorization.k8s.io

# 12 service accounts (one per deployable service)
---
apiVersion: v1
kind: ServiceAccount
metadata:
  name: agent-gateway-sa
  namespace: agent-platform-prod
  annotations:
    vault.hashicorp.com/auth-path: auth/kubernetes
---
apiVersion: v1
kind: ServiceAccount
metadata:
  name: agent-session-sa
  namespace: agent-platform-prod
---
apiVersion: v1
kind: ServiceAccount
metadata:
  name: agent-task-orchestrator-sa
  namespace: agent-platform-prod
---
apiVersion: v1
kind: ServiceAccount
metadata:
  name: agent-memory-sa
  namespace: agent-platform-prod
---
apiVersion: v1
kind: ServiceAccount
metadata:
  name: agent-tool-engine-sa
  namespace: agent-platform-prod
---
apiVersion: v1
kind: ServiceAccount
metadata:
  name: agent-runtime-sa
  namespace: agent-platform-prod
---
apiVersion: v1
kind: ServiceAccount
metadata:
  name: agent-model-gateway-sa
  namespace: agent-platform-prod
---
apiVersion: v1
kind: ServiceAccount
metadata:
  name: agent-repo-sa
  namespace: agent-platform-prod
---
apiVersion: v1
kind: ServiceAccount
metadata:
  name: agent-knowledge-sa
  namespace: agent-platform-prod
---
apiVersion: v1
kind: ServiceAccount
metadata:
  name: agent-quality-sa
  namespace: agent-platform-prod
---
apiVersion: v1
kind: ServiceAccount
metadata:
  name: agent-risk-control-sa
  namespace: agent-platform-prod
---
apiVersion: v1
kind: ServiceAccount
metadata:
  name: agent-observability-sa
  namespace: agent-platform-prod
```

- [ ] **Step 4.3: 验证 yaml 语法**

```powershell
kubectl apply --dry-run=client -f e:\git\Agent-Platform-Prototype\infra\k8s\00-namespace.yaml
kubectl apply --dry-run=client -f e:\git\Agent-Platform-Prototype\infra\k8s\01-serviceaccounts.yaml
```

Expected: 两条命令均输出 `created (dry run)` 无报错

**验收标准（Task 4）：**
- [ ] 4 命名空间 yaml 齐全（dev/staging/prod/infra）
- [ ] prod namespace 含 ResourceQuota（CPU 200C / 内存 400G）+ LimitRange
- [ ] 12 个 ServiceAccount 创建（每服务一个，planning 合并入 task-orchestrator）
- [ ] Role + RoleBinding 授予服务账号读 ConfigMap/Secret/Pod 权限

---

## Task 5: K8s ConfigMap + Secret + Vault 配置

**Files:**
- Create: `infra/k8s/02-configmap-bootstrap.yaml`
- Create: `infra/k8s/03-vault-config.yaml`
- Create: `infra/vault/vault-policies/agent-runtime.hcl`（示例）
- Create: `infra/vault/vault-seeds.sh`

**对齐设计：** doc 09 §12.1 bootstrap.yml + §12.4 Vault K8s SA 认证 + 密钥路径规划。

- [ ] **Step 5.1: 创建 02-configmap-bootstrap.yaml（共享 bootstrap）**

文件路径：`e:\git\Agent-Platform-Prototype\infra\k8s\02-configmap-bootstrap.yaml`

```yaml
# Shared bootstrap ConfigMap for all services. Each Deployment mounts this and
# the service-specific application.yml from Nacos.
---
apiVersion: v1
kind: ConfigMap
metadata:
  name: agent-platform-bootstrap
  namespace: agent-platform-prod
data:
  bootstrap.yml: |
    spring:
      application:
        name: ${SPRING_APPLICATION_NAME}
      profiles:
        active: ${SPRING_PROFILES_ACTIVE:prod}
      cloud:
        nacos:
          discovery:
            server-addr: ${NACOS_ADDR:nacos-cluster.agent-platform-infra:8848}
            namespace: agent-platform-${spring.profiles.active}
            group: SERVICE_GROUP
          config:
            server-addr: ${NACOS_ADDR:nacos-cluster.agent-platform-infra:8848}
            namespace: agent-platform-${spring.profiles.active}
            group: SERVICE_GROUP
            file-extension: yaml
            shared-configs:
              - data-id: datasource-common.yml
                group: COMMON_GROUP
                refresh: true
              - data-id: redis-common.yml
                group: COMMON_GROUP
                refresh: true
              - data-id: rocketmq-common.yml
                group: COMMON_GROUP
                refresh: true
              - data-id: observability-common.yml
                group: COMMON_GROUP
                refresh: true
              - data-id: governance-rules.yml
                group: COMMON_GROUP
                refresh: true
        vault:
          uri: http://vault.agent-platform-infra:8200
          authentication: kubernetes
          kubernetes:
            role: agent-platform-${spring.application.name}
            kubernetes-path: kubernetes
          kv:
            backend: agent-platform
            application-name: ${spring.application.name}
```

- [ ] **Step 5.2: 创建 03-vault-config.yaml（Vault K8s Auth Method）**

文件路径：`e:\git\Agent-Platform-Prototype\infra\k8s\03-vault-config.yaml`

```yaml
# Vault Kubernetes auth method setup (run once per cluster, by cluster admin).
# Requires Vault CLI and cluster admin kubeconfig.
#
# 1. Enable Kubernetes auth:
#   vault auth enable kubernetes
# 2. Configure:
#   vault write auth/kubernetes/config \
#     kubernetes_host="https://kubernetes.default.svc:443" \
#     kubernetes_ca_cert=@/var/run/secrets/kubernetes.io/serviceaccount/ca.crt
# 3. For each service, create role + policy (see vault-seeds.sh)

# This yaml only declares a placeholder ConfigMap documenting the binding;
# actual Vault roles created via vault-seeds.sh in T11.
---
apiVersion: v1
kind: ConfigMap
metadata:
  name: vault-auth-method-doc
  namespace: agent-platform-infra
data:
  vault-roles.txt: |
    Vault roles per service (created by infra/vault/vault-seeds.sh):
      agent-platform-agent-gateway        -> secret/data/agent-platform/common/*
      agent-platform-agent-session         -> secret/data/agent-platform/common/*
      agent-platform-agent-task-orchestrator -> secret/data/agent-platform/common/*
      agent-platform-agent-memory          -> secret/data/agent-platform/common/*
      agent-platform-agent-tool-engine     -> secret/data/agent-platform/common/*
      agent-platform-agent-runtime         -> secret/data/agent-platform/common/*
      agent-platform-agent-model-gateway   -> secret/data/agent-platform/common/* + model/*
      agent-platform-agent-repo            -> secret/data/agent-platform/common/*
      agent-platform-agent-knowledge       -> secret/data/agent-platform/common/*
      agent-platform-agent-quality         -> secret/data/agent-platform/common/*
      agent-platform-agent-risk-control    -> secret/data/agent-platform/common/* + service/jwt
      agent-platform-agent-observability   -> secret/data/agent-platform/common/*
```

- [ ] **Step 5.3: 创建示例 Vault Policy（agent-runtime）**

文件路径：`e:\git\Agent-Platform-Prototype\infra\vault\vault-policies\agent-runtime.hcl`

```hcl
# Policy for agent-runtime service account.
# Grants read access to common secrets (MySQL/Redis/Milvus) used by runtime.

path "secret/data/agent-platform/common/mysql" {
  capabilities = ["read"]
}

path "secret/data/agent-platform/common/redis" {
  capabilities = ["read"]
}

path "secret/data/agent-platform/common/milvus" {
  capabilities = ["read"]
}

# agent-runtime is stateless, only needs session-level secrets
path "secret/data/agent-platform/service/jwt" {
  capabilities = ["read"]
}
```

- [ ] **Step 5.4: 批量生成其余 11 个服务 Policy**

```powershell
$services = @("agent-gateway","agent-session","agent-task-orchestrator","agent-memory",
              "agent-tool-engine","agent-model-gateway","agent-repo","agent-knowledge",
              "agent-quality","agent-risk-control","agent-observability")
$dir = "e:\git\Agent-Platform-Prototype\infra\vault\vault-policies"
$template = Get-Content (Join-Path $dir "agent-runtime.hcl") -Raw
foreach ($svc in $services) {
    $policy = $template -replace "agent-runtime", $svc
    Set-Content -Path (Join-Path $dir "$svc.hcl") -Value $policy -Encoding UTF8
}
# agent-model-gateway needs additional model/* permissions - patch manually
$modelPolicy = Get-Content (Join-Path $dir "agent-model-gateway.hcl") -Raw
$modelPolicy += "`npath `"secret/data/agent-platform/model/openai`" { capabilities = [`"read`"] }`n"
$modelPolicy += "path `"secret/data/agent-platform/model/anthropic`" { capabilities = [`"read`"] }`n"
$modelPolicy += "path `"secret/data/agent-platform/model/qwen`" { capabilities = [`"read`"] }`n"
$modelPolicy += "path `"secret/data/agent-platform/model/deepseek`" { capabilities = [`"read`"] }`n"
Set-Content -Path (Join-Path $dir "agent-model-gateway.hcl") -Value $modelPolicy -Encoding UTF8
```

- [ ] **Step 5.5: 创建 vault-seeds.sh（开发环境密钥初始化）**

文件路径：`e:\git\Agent-Platform-Prototype\infra\vault\vault-seeds.sh`

```bash
#!/usr/bin/env bash
# Initialize dev Vault with seed secrets. Run against Vault dev instance only.
# Usage: VAULT_ADDR=http://localhost:8200 VAULT_TOKEN=agentplatform ./vault-seeds.sh

set -euo pipefail

: "${VAULT_ADDR:?VAULT_ADDR must be set}"
: "${VAULT_TOKEN:?VAULT_TOKEN must be set}"

export VAULT_ADDR VAULT_TOKEN

echo "[1/8] Enabling kv-v2 at agent-platform/ ..."
vault secrets enable -path=secret -version=2 kv || true

echo "[2/8] Writing common secrets ..."
vault kv put secret/agent-platform/common/mysql \
    username="agent_platform" password="$(openssl rand -base64 24)" > /dev/null
vault kv put secret/agent-platform/common/redis \
    password="$(openssl rand -base64 24)" > /dev/null
vault kv put secret/agent-platform/common/milvus \
    username="root" password="$(openssl rand -base64 24)" > /dev/null
vault kv put secret/agent-platform/common/neo4j \
    username="neo4j" password="$(openssl rand -base64 24)" > /dev/null
vault kv put secret/agent-platform/common/minio \
    access_key="$(openssl rand -hex 12)" secret_key="$(openssl rand -base64 24)" > /dev/null

echo "[3/8] Writing model provider API keys (REPLACE with real keys in prod) ..."
vault kv put secret/agent-platform/model/openai api_key="sk-REPLACE_ME" > /dev/null
vault kv put secret/agent-platform/model/anthropic api_key="sk-ant-REPLACE_ME" > /dev/null
vault kv put secret/agent-platform/model/qwen api_key="sk-REPLACE_ME" > /dev/null
vault kv put secret/agent-platform/model/deepseek api_key="sk-REPLACE_ME" > /dev/null

echo "[4/8] Writing JWT signing key ..."
vault kv put secret/agent-platform/service/jwt \
    signing_key="$(openssl rand -base64 48)" > /dev/null

echo "[5/8] Enabling Kubernetes auth ..."
vault auth enable kubernetes || true
vault write auth/kubernetes/config \
    kubernetes_host="https://kubernetes.default.svc:443" || true

echo "[6/8] Writing policies ..."
for svc in agent-gateway agent-session agent-task-orchestrator agent-memory \
           agent-tool-engine agent-runtime agent-model-gateway agent-repo \
           agent-knowledge agent-quality agent-risk-control agent-observability; do
    vault policy write "agent-platform-${svc}" "vault-policies/${svc}.hcl"
done

echo "[7/8] Creating roles (binding K8s SA -> Vault role) ..."
for svc in agent-gateway agent-session agent-task-orchestrator agent-memory \
           agent-tool-engine agent-runtime agent-model-gateway agent-repo \
           agent-knowledge agent-quality agent-risk-control agent-observability; do
    vault write "auth/kubernetes/role/agent-platform-${svc}" \
        bound_service_account_names="${svc}-sa" \
        bound_service_account_namespaces="agent-platform-prod" \
        policies="agent-platform-${svc}" \
        ttl=1h
done

echo "[8/8] Done. Verify with:"
echo "  vault kv get secret/agent-platform/common/mysql"
echo "  vault read auth/kubernetes/role/agent-platform-agent-runtime"
```

- [ ] **Step 5.6: 验证 yaml 与脚本语法**

```powershell
kubectl apply --dry-run=client -f e:\git\Agent-Platform-Prototype\infra\k8s\02-configmap-bootstrap.yaml
kubectl apply --dry-run=client -f e:\git\Agent-Platform-Prototype\infra\k8s\03-vault-config.yaml
# Vault seeds script syntax check (bash)
bash -n e:\git\Agent-Platform-Prototype\infra\vault\vault-seeds.sh
```

Expected: 三条命令均通过

**验收标准（Task 5）：**
- [ ] `02-configmap-bootstrap.yaml` 含 Nacos + Vault 完整 bootstrap 配置
- [ ] 5 个共享 Nacos 配置正确引用（datasource/redis/rocketmq/observability/governance）
- [ ] 12 个 Vault Policy 文件齐全
- [ ] `agent-model-gateway.hcl` 含额外 model/* 路径
- [ ] `vault-seeds.sh` 通过 bash 语法检查
- [ ] 禁止明文密钥写入任何 yaml/sh（开发密钥用 openssl rand 生成）

---

## Task 6: K8s Deployment + Service（12 服务）

**Files:**
- Create: `infra/k8s/deployments/{12 服务}.yaml`
- Create: `infra/k8s/services/{12 服务}-svc.yaml`
- Create: `infra/k8s/services/ingress-gateway.yaml`

**对齐设计：** doc 09 §13.2 Deployment 模板 + §14.3 资源配额建议表 + doc 09 §13.3 命名空间。

- [ ] **Step 6.1: 创建 agent-runtime Deployment（HPA 主对象，含完整探针与 lifecycle）**

文件路径：`e:\git\Agent-Platform-Prototype\infra\k8s\deployments\agent-runtime.yaml`

```yaml
# agent-runtime Deployment (stateless, HPA 3-30).
# Aligned with doc 09 §13.2 + §14.3 (CPU 500m-2000m, mem 1Gi-4Gi).
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: agent-runtime
  namespace: agent-platform-prod
  labels:
    app: agent-runtime
    tier: runtime
spec:
  replicas: 3
  selector:
    matchLabels:
      app: agent-runtime
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1
      maxUnavailable: 0
  template:
    metadata:
      labels:
        app: agent-runtime
        tier: runtime
      annotations:
        prometheus.io/scrape: "true"
        prometheus.io/port: "8092"
        prometheus.io/path: /actuator/prometheus
    spec:
      serviceAccountName: agent-runtime-sa
      terminationGracePeriodSeconds: 60
      containers:
        - name: agent-runtime
          image: agentplatform/agent-runtime:1.0.0
          imagePullPolicy: IfNotPresent
          ports:
            - name: http
              containerPort: 8092
              protocol: TCP
            - name: grpc
              containerPort: 9092
              protocol: TCP
          env:
            - name: SPRING_APPLICATION_NAME
              value: agent-runtime
            - name: SPRING_PROFILES_ACTIVE
              value: prod
            - name: HOSTNAME_HASH
              valueFrom:
                fieldRef:
                  fieldPath: metadata.uid
            - name: SW_AGENT_NAME
              value: agent-platform-agent-runtime
            - name: SW_COLLECTOR_BACKEND_SERVICE
              value: skywalking-oap.agent-platform-infra:11800
            - name: JAVA_OPTS
              value: "-Xms1g -Xmx3g"
          envFrom:
            - configMapRef:
                name: agent-platform-bootstrap
          resources:
            requests:
              cpu: "500m"
              memory: "1Gi"
            limits:
              cpu: "2000m"
              memory: "4Gi"
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8092
            initialDelaySeconds: 30
            periodSeconds: 10
            failureThreshold: 3
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8092
            initialDelaySeconds: 60
            periodSeconds: 20
            failureThreshold: 5
          startupProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8092
            failureThreshold: 30
            periodSeconds: 10
          lifecycle:
            preStop:
              exec:
                command:
                  - sh
                  - -c
                  - "sleep 10 && curl -X POST http://localhost:8092/actuator/shutdown || true"
          volumeMounts:
            - name: bootstrap
              mountPath: /app/config
              readOnly: true
            - name: heapdump
              mountPath: /var/log/agent-platform
      volumes:
        - name: bootstrap
          configMap:
            name: agent-platform-bootstrap
        - name: heapdump
          emptyDir: {}
```

- [ ] **Step 6.2: 创建 agent-runtime Service**

文件路径：`e:\git\Agent-Platform-Prototype\infra\k8s\services\agent-runtime-svc.yaml`

```yaml
---
apiVersion: v1
kind: Service
metadata:
  name: agent-runtime
  namespace: agent-platform-prod
spec:
  type: ClusterIP
  selector:
    app: agent-runtime
  ports:
    - name: http
      port: 8092
      targetPort: 8092
      protocol: TCP
    - name: grpc
      port: 9092
      targetPort: 9092
      protocol: TCP
```

- [ ] **Step 6.3: 批量生成其余 11 个服务 Deployment + Service**

```powershell
# Each service follows the agent-runtime template; vary by:
#   - image name + label app
#   - ports (HTTP only or HTTP+gRPC)
#   - resources (per doc 09 §14.3 资源配额建议表)
#   - serviceAccountName
$services = @(
    @{name="agent-gateway"; http=8080; grpc=$null; replicas=3; req_cpu="500m"; lim_cpu="1000m"; req_mem="1Gi"; lim_mem="2Gi"},
    @{name="agent-session"; http=8082; grpc=$null; replicas=2; req_cpu="500m"; lim_cpu="1000m"; req_mem="1Gi"; lim_mem="2Gi"},
    @{name="agent-task-orchestrator"; http=8084; grpc=9090; replicas=2; req_cpu="1000m"; lim_cpu="2000m"; req_mem="2Gi"; lim_mem="4Gi"},
    @{name="agent-memory"; http=8088; grpc=9088; replicas=2; req_cpu="1000m"; lim_cpu="2000m"; req_mem="2Gi"; lim_mem="4Gi"},
    @{name="agent-tool-engine"; http=8090; grpc=9090; replicas=2; req_cpu="1000m"; lim_cpu="2000m"; req_mem="2Gi"; lim_mem="4Gi"},
    @{name="agent-model-gateway"; http=8094; grpc=9094; replicas=2; req_cpu="1000m"; lim_cpu="2000m"; req_mem="2Gi"; lim_mem="4Gi"},
    @{name="agent-repo"; http=8096; grpc=9096; replicas=2; req_cpu="500m"; lim_cpu="1000m"; req_mem="1Gi"; lim_mem="2Gi"},
    @{name="agent-knowledge"; http=8098; grpc=9098; replicas=2; req_cpu="500m"; lim_cpu="1000m"; req_mem="1Gi"; lim_mem="2Gi"},
    @{name="agent-quality"; http=8100; grpc=9100; replicas=2; req_cpu="500m"; lim_cpu="1000m"; req_mem="1Gi"; lim_mem="2Gi"},
    @{name="agent-risk-control"; http=8102; grpc=9102; replicas=2; req_cpu="500m"; lim_cpu="1000m"; req_mem="1Gi"; lim_mem="2Gi"},
    @{name="agent-observability"; http=8104; grpc=$null; replicas=2; req_cpu="500m"; lim_cpu="1000m"; req_mem="1Gi"; lim_mem="2Gi"}
)
# Generate deployments and services by templating agent-runtime.yaml (omitted for brevity;
# implementation: load agent-runtime.yaml, string-replace service name, ports, resources, replicas)
```

执行实际批量生成（替换 name/ports/resources/replicas），生成 11 个 Deployment 文件与 11 个 Service 文件。

- [ ] **Step 6.4: 创建 ingress-gateway.yaml（仅 agent-gateway 对外暴露）**

文件路径：`e:\git\Agent-Platform-Prototype\infra\k8s\services\ingress-gateway.yaml`

```yaml
# Ingress for agent-gateway (only externally-exposed service).
---
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: agent-gateway-ingress
  namespace: agent-platform-prod
  annotations:
    nginx.ingress.kubernetes.io/ssl-redirect: "true"
    nginx.ingress.kubernetes.io/proxy-body-size: "50m"
    nginx.ingress.kubernetes.io/proxy-read-timeout: "300"
    nginx.ingress.kubernetes.io/proxy-send-timeout: "300"
    nginx.ingress.kubernetes.io/websocket-services: "agent-gateway"
spec:
  ingressClassName: nginx
  tls:
    - hosts: [api.agent-platform.example.com]
      secretName: agent-platform-tls
  rules:
    - host: api.agent-platform.example.com
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: agent-gateway
                port:
                  number: 8080
```

- [ ] **Step 6.5: 验证全部 yaml 语法**

```powershell
kubectl apply --dry-run=client -f e:\git\Agent-Platform-Prototype\infra\k8s\deployments\
kubectl apply --dry-run=client -f e:\git\Agent-Platform-Prototype\infra\k8s\services\
```

Expected: 24 个 yaml（12 Deployment + 12 Service）全部 dry-run 通过

**验收标准（Task 6）：**
- [ ] 12 个 Deployment yaml 齐全
- [ ] 12 个 Service yaml 齐全（11 ClusterIP + 1 Ingress for gateway）
- [ ] 资源配额与 doc 09 §14.3 一致（agent-runtime 500m-2000m / 1Gi-4Gi 等）
- [ ] 每个 Deployment 含 readiness + liveness + startup 三探针
- [ ] 每个 Deployment 含 preStop lifecycle（graceful shutdown）
- [ ] prometheus.io/scrape 注解正确（指向各自 HTTP 端口）
- [ ] serviceAccountName 与 Task 4 的 SA 一一对应

---

## Task 7: K8s HPA + PDB（弹性扩缩容）

**Files:**
- Create: `infra/k8s/hpa/{6 服务}-hpa.yaml`
- Create: `infra/k8s/pdb/agent-runtime-pdb.yaml`

**对齐设计：** doc 09 §13.2 HPA 模板（minReplicas/maxReplicas/CPU+内存+自定义指标）+ §14.3 副本数建议。

| 服务 | min | max | 指标 | 备注 |
|---|---|---|---|---|
| agent-runtime | 3 | 30 | CPU 70% + 内存 80% + agent_active_instances 20 | HPA 主对象 |
| agent-gateway | 3 | 10 | CPU 70% + http_requests_qps 1000 | 入口流量 |
| agent-model-gateway | 2 | 15 | CPU 70% + grpc_requests_qps 500 | 高 QPS |
| agent-memory | 2 | 10 | CPU 75% + 内存 80% | Milvus + embedding |
| agent-tool-engine | 2 | 10 | CPU 75% + 内存 80% | 沙箱执行 |
| agent-task-orchestrator | 2 | 8 | CPU 70% | 调度 |

- [ ] **Step 7.1: 创建 agent-runtime-hpa.yaml（含自定义指标）**

文件路径：`e:\git\Agent-Platform-Prototype\infra\k8s\hpa\agent-runtime-hpa.yaml`

```yaml
# HPA for agent-runtime (3-30 replicas, doc 09 §13.2).
# Custom metric agent_active_instances requires Prometheus Adapter.
---
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: agent-runtime-hpa
  namespace: agent-platform-prod
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: agent-runtime
  minReplicas: 3
  maxReplicas: 30
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70
    - type: Resource
      resource:
        name: memory
        target:
          type: Utilization
          averageUtilization: 80
    - type: Pods
      pods:
        metric:
          name: agent_active_instances
        target:
          type: AverageValue
          averageValue: "20"
  behavior:
    scaleUp:
      stabilizationWindowSeconds: 30
      policies:
        - type: Percent
          value: 100
          periodSeconds: 30
    scaleDown:
      stabilizationWindowSeconds: 300
      policies:
        - type: Percent
          value: 50
          periodSeconds: 60
```

- [ ] **Step 7.2: 创建 agent-gateway-hpa.yaml（HTTP QPS 指标）**

文件路径：`e:\git\Agent-Platform-Prototype\infra\k8s\hpa\agent-gateway-hpa.yaml`

```yaml
---
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: agent-gateway-hpa
  namespace: agent-platform-prod
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: agent-gateway
  minReplicas: 3
  maxReplicas: 10
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70
    - type: Pods
      pods:
        metric:
          name: http_requests_qps
        target:
          type: AverageValue
          averageValue: "1000"
  behavior:
    scaleUp:
      stabilizationWindowSeconds: 30
      policies:
        - type: Percent
          value: 100
          periodSeconds: 30
    scaleDown:
      stabilizationWindowSeconds: 300
      policies:
        - type: Percent
          value: 50
          periodSeconds: 60
```

- [ ] **Step 7.3: 批量生成其余 4 个 HPA（model-gateway/memory/tool-engine/task-orchestrator）**

按上表模板生成，仅修改 `scaleTargetRef.name`、`minReplicas`、`maxReplicas`、指标阈值。model-gateway 用 `grpc_requests_qps` 指标，memory/tool-engine 用 CPU+内存，task-orchestrator 仅 CPU。

- [ ] **Step 7.4: 创建 agent-runtime-pdb.yaml（PodDisruptionBudget）**

文件路径：`e:\git\Agent-Platform-Prototype\infra\k8s\pdb\agent-runtime-pdb.yaml`

```yaml
# Ensure at least 2 agent-runtime pods always available during voluntary disruptions.
---
apiVersion: policy/v1
kind: PodDisruptionBudget
metadata:
  name: agent-runtime-pdb
  namespace: agent-platform-prod
spec:
  minAvailable: 2
  selector:
    matchLabels:
      app: agent-runtime
```

- [ ] **Step 7.5: 验证 HPA + PDB yaml**

```powershell
kubectl apply --dry-run=client -f e:\git\Agent-Platform-Prototype\infra\k8s\hpa\
kubectl apply --dry-run=client -f e:\git\Agent-Platform-Prototype\infra\k8s\pdb\
```

Expected: 7 个 yaml 全部 dry-run 通过

**验收标准（Task 7）：**
- [ ] 6 个 HPA yaml 齐全（runtime/gateway/model-gateway/memory/tool-engine/task-orchestrator）
- [ ] agent-runtime HPA 含 CPU + 内存 + 自定义指标（agent_active_instances）三维度
- [ ] agent-gateway HPA 含 http_requests_qps 自定义指标
- [ ] scaleUp 稳定窗口 30s，scaleDown 稳定窗口 300s（防止抖动）
- [ ] agent-runtime PDB minAvailable=2

---

## Task 8: Nacos 配置中心（共享 + 服务级 + bootstrap）

**Files:**
- Create: `infra/nacos/bootstrap-common.yml`
- Create: `infra/nacos/shared/{5 共享配置}.yml`
- Create: `infra/nacos/services/{13 服务 × dev/prod}.yml`（节选示例 + 模板说明）
- Create: `infra/nacos/import-nacos.ps1`
- Create: `infra/nacos/README.md`

**对齐设计：** doc 09 §12.1 DataId/Group 规划表 + §12.2 task-orchestrator 完整配置示例 + §12.3 memory-service 配置示例。

- [ ] **Step 8.1: 创建 bootstrap-common.yml（所有服务 bootstrap 模板）**

文件路径：`e:\git\Agent-Platform-Prototype\infra\nacos\bootstrap-common.yml`

```yaml
# bootstrap.yml template for all services.
# Replace ${SPRING_APPLICATION_NAME} at deploy time (ConfigMap envsubst or init container).
# Copy to each service's src/main/resources/bootstrap.yml as baseline.

spring:
  application:
    name: ${SPRING_APPLICATION_NAME}
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:dev}
  cloud:
    nacos:
      discovery:
        server-addr: ${NACOS_ADDR:localhost:8848}
        namespace: agent-platform-${spring.profiles.active}
        group: SERVICE_GROUP
      config:
        server-addr: ${NACOS_ADDR:localhost:8848}
        namespace: agent-platform-${spring.profiles.active}
        group: SERVICE_GROUP
        file-extension: yaml
        shared-configs:
          - data-id: datasource-common.yml
            group: COMMON_GROUP
            refresh: true
          - data-id: redis-common.yml
            group: COMMON_GROUP
            refresh: true
          - data-id: rocketmq-common.yml
            group: COMMON_GROUP
            refresh: true
          - data-id: observability-common.yml
            group: COMMON_GROUP
            refresh: true
          - data-id: governance-rules.yml
            group: COMMON_GROUP
            refresh: true
    vault:
      uri: ${VAULT_ADDR:http://localhost:8200}
      authentication: kubernetes
      kubernetes:
        role: agent-platform-${spring.application.name}
        kubernetes-path: kubernetes
      kv:
        backend: agent-platform
        application-name: ${spring.application.name}
```

- [ ] **Step 8.2: 创建 5 个共享配置（COMMON_GROUP）**

**a. datasource-common.yml** — HikariCP + MySQL 连接池（doc 09 §4.1）

文件路径：`e:\git\Agent-Platform-Prototype\infra\nacos\shared\datasource-common.yml`

```yaml
spring:
  datasource:
    type: com.zaxxer.hikari.HikariDataSource
    driver-class-name: com.mysql.cj.jdbc.Driver
    hikari:
      pool-name: ${spring.application.name}-pool
      minimum-idle: 5
      maximum-pool-size: 20
      idle-timeout: 30000
      max-lifetime: 1800000
      connection-timeout: 30000
      connection-test-query: SELECT 1
      leak-detection-threshold: 60000
      data-source-properties:
        cachePrepStmts: true
        prepStmtCacheSize: 250
        prepStmtCacheSqlLimit: 2048
        useServerPrepStmts: true
        useLocalSessionState: true
        rewriteBatchedStatements: true

# Vault-resolved MySQL credentials (no plaintext here)
mysql:
  host: ${MYSQL_HOST:mysql.agent-platform-infra}
  port: 3306
  username: ${vault:secret/data/agent-platform/common/mysql:username}
  password: ${vault:secret/data/agent-platform/common/mysql:password}
```

**b. redis-common.yml** — Redis Cluster + Lettuce 池（doc 09 §6.1）

文件路径：`e:\git\Agent-Platform-Prototype\infra\nacos\shared\redis-common.yml`

```yaml
spring:
  data:
    redis:
      cluster:
        nodes:
          - redis-0.redis.agent-platform-infra:6379
          - redis-1.redis.agent-platform-infra:6379
          - redis-2.redis.agent-platform-infra:6379
          - redis-3.redis.agent-platform-infra:6379
          - redis-4.redis.agent-platform-infra:6379
          - redis-5.redis.agent-platform-infra:6379
        max-redirects: 3
        topology-refresh-period: 30s
      password: ${vault:secret/data/agent-platform/common/redis:password}
      timeout: 3s
      lettuce:
        pool:
          max-active: 50
          max-idle: 20
          min-idle: 5
          max-wait: 2000ms
        shutdown-timeout: 100ms
        cluster:
          refresh:
            adaptive: true
            period: 30s

# Redisson distributed lock config (doc 09 §6.3)
redisson:
  config: |
    clusterServersConfig:
      nodeAddresses:
        - redis://redis-0.redis.agent-platform-infra:6379
        - redis://redis-1.redis.agent-platform-infra:6379
        - redis://redis-2.redis.agent-platform-infra:6379
        - redis://redis-3.redis.agent-platform-infra:6379
        - redis://redis-4.redis.agent-platform-infra:6379
        - redis://redis-5.redis.agent-platform-infra:6379
      password: ${vault:secret/data/agent-platform/common/redis:password}
      scanInterval: 2000
      readMode: SLAVE
      subscriptionMode: SLAVE
    threads: 16
    nettyThreads: 32
```

**c. rocketmq-common.yml** — RocketMQ NameServer + Producer（doc 09 §7.2）

文件路径：`e:\git\Agent-Platform-Prototype\infra\nacos\shared\rocketmq-common.yml`

```yaml
rocketmq:
  name-server: rocketmq-namesrv.agent-platform-infra:9876
  producer:
    send-message-timeout: 5000
    retry-times-when-send-failed: 2
    retry-times-when-send-async-failed: 3
    max-message-size: 4194304  # 4MB

# Topic registry (doc 09 §7.1)
rocketmq:
  topics:
    task-subtask-execute: task.subtask.execute
    task-subtask-done: task.subtask.done
    task-state-change: task.state.change
    task-subtask-cancel: task.subtask.cancel
    tool-call-audit: tool.call.audit
    memory-write: memory.write
    quality-badcase: quality.badcase
    governance-drift-alert: governance.drift.alert
    memory-distill-request: memory.distill.request
  consumer-groups:
    runtime-execute: CG_RUNTIME_EXECUTE
    orchestrator-done: CG_ORCH_DONE
    session-state: CG_SESSION_STATE
    obs-state: CG_OBS_STATE
    risk-audit: CG_RISK_AUDIT
    mem-write: CG_MEM_WRITE
    obs-badcase: CG_OBS_BADCASE
    obs-drift: CG_OBS_DRIFT
    risk-drift: CG_RISK_DRIFT
    mem-distill: CG_MEM_DISTILL
```

**d. observability-common.yml** — SkyWalking + Prometheus + 日志（doc 09 §11）

文件路径：`e:\git\Agent-Platform-Prototype\infra\nacos\shared\observability-common.yml`

```yaml
management:
  endpoints:
    web:
      exposure:
        include: prometheus,health,info,metrics
      base-path: /actuator
  endpoint:
    prometheus:
      enabled: true
    health:
      show-details: when-authorized
  metrics:
    tags:
      application: ${spring.application.name}
      namespace: agent-platform
    distribution:
      percentiles-histogram:
        http.server.requests: true
        grpc.server.requests: true
      percentiles:
        http.server.requests: 0.5, 0.95, 0.99
  prometheus:
    metrics:
      export:
        enabled: true
        step: 30s

# SkyWalking Agent configuration
skywalking:
  agent:
    service-name: agent-platform-${spring.application.name}
    namespace: agent-platform
    collector-backend-service: skywalking-oap.agent-platform-infra:11800
    sample-n-per-3-secs: 5000
    ignore-suffix: .jpg,.png,.css,.js,.html

# Logback structured JSON output (doc 09 §11.3)
logging:
  pattern:
    console: '{"@timestamp":"%d","app":"${spring.application.name}","level":"%p","logger":"%c","traceId":"%X{traceId:-}","tenantId":"%X{tenantId:-}","taskId":"%X{taskId:-}","message":"%m"}%n'
  level:
    com.agentplatform: INFO
    org.springframework: WARN
```

**e. governance-rules.yml** — 治理规则（漂移/幻觉/成本阈值，doc 09 §1.3 + §2.2.3 + §3.4）

文件路径：`e:\git\Agent-Platform-Prototype\infra\nacos\shared\governance-rules.yml`

```yaml
governance:
  hallucination:
    enabled: true
    layers: [L1, L2, L3, L4, L5, L6]
    alert-threshold:
      daily-rate-warning: 0.05
      daily-rate-critical: 0.10
  drift:
    detection-enabled: true
    alert-threshold:
      task-level: 0.3
      system-level: 0.5
      critical: 0.7
    weekly-full-regression: true
  cost:
    circuit-breaker:
      task-warn-ratio: 0.8
      task-block-ratio: 1.0
      tenant-daily-limit-cent: 10000000
  rate-limit:
    tenant-qps: 100
    user-qps: 20
  sampling:
    online-eval-rate: 0.01
    badcase-auto-collect: true
```

- [ ] **Step 8.3: 创建 task-orchestrator 服务级配置（含 ShardingSphere，doc 09 §12.2 完整示例）**

文件路径：`e:\git\Agent-Platform-Prototype\infra\nacos\services\task-orchestrator-prod.yml`

完整引用 doc 09 §12.2 的 task-orchestrator-prod.yml 配置（含 ShardingSphere 16 分库、gRPC client、RocketMQ 事务消息、task.orchestrator 业务配置、governance 规则引用、model.gateway 地址等）。文件内容较长，按 doc 09 §12.2 原文落盘。

- [ ] **Step 8.4: 创建 memory-service 服务级配置（doc 09 §12.3 完整示例）**

文件路径：`e:\git\Agent-Platform-Prototype\infra\nacos\services\memory-service-prod.yml`

完整引用 doc 09 §12.3 的 memory-service-prod.yml 配置（含 Milvus 连接、memory 业务配置、embedding 模型、RocketMQ 消费组、XXL-Job 蒸馏调度等）。

- [ ] **Step 8.5: 创建 import-nacos.ps1（批量导入 Nacos 脚本）**

文件路径：`e:\git\Agent-Platform-Prototype\infra\nacos\import-nacos.ps1`

```powershell
<#
.SYNOPSIS
    Batch import Nacos configurations from infra/nacos/.
.DESCRIPTION
    Reads all .yml files under shared/ and services/ and POSTs them to Nacos config API.
    Supports namespace selection (dev/staging/prod) via -Environment.
.PARAMETER NacosHost
    Nacos host. Default: localhost
.PARAMETER NacosPort
    Nacos port. Default: 8848
.PARAMETER NacosUser
    Nacos username. Default: nacos
.PARAMETER NacosPassword
    Nacos password (mandatory).
.PARAMETER Environment
    Target namespace suffix: dev | staging | prod
.EXAMPLE
    .\import-nacos.ps1 -Environment prod -NacosPassword nacos
#>
param(
    [string]$NacosHost = "localhost",
    [int]$NacosPort = 8848,
    [string]$NacosUser = "nacos",
    [Parameter(Mandatory=$true)]
    [string]$NacosPassword,
    [Parameter(Mandatory=$true)]
    [ValidateSet("dev","staging","prod")]
    [string]$Environment
)

$ErrorActionPreference = "Stop"
$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$logDir = Join-Path $scriptRoot "logs"
if (-not (Test-Path $logDir)) { New-Item -ItemType Directory -Path $logDir -Force | Out-Null }
$logFile = Join-Path $logDir "import-$(Get-Date -Format 'yyyyMMdd-HHmmss').log"

function Write-Log {
    param([string]$Level, [string]$Message)
    $ts = Get-Date -Format "yyyy-MM-dd HH:mm:ss.fff"
    $line = "[$ts] [$Level] $Message"
    Add-Content -Path $logFile -Value $line -Encoding UTF8
    switch ($Level) {
        "ERROR" { Write-Host $line -ForegroundColor Red }
        "OK"    { Write-Host $line -ForegroundColor Cyan }
        default { Write-Host $line -ForegroundColor Green }
    }
}

# Step 1: Login to get access token
Write-Log "INFO" "Logging in to Nacos as $NacosUser ..."
$loginBody = @{ username = $NacosUser; password = $NacosPassword }
$loginResp = Invoke-RestMethod -Uri "http://${NacosHost}:${NacosPort}/nacos/v1/auth/login" -Method Post -Body $loginBody
$accessToken = $loginResp.accessToken
if (-not $accessToken) { Write-Log "ERROR" "Login failed"; exit 1 }
Write-Log "OK" "Login OK"

$namespace = "agent-platform-$Environment"

# Step 2: Import shared configs (COMMON_GROUP)
$sharedDir = Join-Path $scriptRoot "shared"
$sharedFiles = Get-ChildItem -Path $sharedDir -Filter "*.yml"
foreach ($f in $sharedFiles) {
    $dataId = $f.Name
    $group = "COMMON_GROUP"
    $content = Get-Content $f.FullName -Raw
    $body = @{
        dataId = $dataId
        group = $group
        tenant = $namespace
        type = "yaml"
        content = $content
    }
    $url = "http://${NacosHost}:${NacosPort}/nacos/v1/cs/configs?accessToken=$accessToken"
    try {
        Invoke-RestMethod -Uri $url -Method Post -Body $body -ErrorAction Stop | Out-Null
        Write-Log "OK" "Imported shared/$dataId -> $namespace / $group"
    } catch {
        Write-Log "ERROR" "Failed shared/$dataId : $($_.Exception.Message)"
    }
}

# Step 3: Import service configs (SERVICE_GROUP)
$svcDir = Join-Path $scriptRoot "services"
$svcFiles = Get-ChildItem -Path $svcDir -Filter "*-$Environment.yml" -ErrorAction SilentlyContinue
foreach ($f in $svcFiles) {
    $dataId = $f.Name -replace ".yml$", ".yml"
    $group = "SERVICE_GROUP"
    $content = Get-Content $f.FullName -Raw
    $body = @{
        dataId = $dataId
        group = $group
        tenant = $namespace
        type = "yaml"
        content = $content
    }
    $url = "http://${NacosHost}:${NacosPort}/nacos/v1/cs/configs?accessToken=$accessToken"
    try {
        Invoke-RestMethod -Uri $url -Method Post -Body $body -ErrorAction Stop | Out-Null
        Write-Log "OK" "Imported services/$dataId -> $namespace / $group"
    } catch {
        Write-Log "ERROR" "Failed services/$dataId : $($_.Exception.Message)"
    }
}

Write-Log "INFO" "Import done. Log: $logFile"
```

- [ ] **Step 8.6: 创建 README.md**

文件路径：`e:\git\Agent-Platform-Prototype\infra\nacos\README.md`

简述配置中心结构（5 共享 + N 服务级）、命名空间与 Group 规则、profile 命名约定、刷新策略、与 Vault 的协作关系（敏感字段通过 `${vault:...}` 占位符引用）。

- [ ] **Step 8.7: 验证 Nacos 配置导入（本地 docker-compose 起的 Nacos）**

```powershell
# 前置：docker-compose -f docker-compose.yml up -d nacos 已起
cd e:\git\Agent-Platform-Prototype\infra\nacos
.\import-nacos.ps1 -Environment dev -NacosPassword nacos
# 验证
$resp = Invoke-RestMethod "http://localhost:8848/nacos/v1/cs/configs?dataId=datasource-common.yml&group=COMMON_GROUP&tenant=agent-platform-dev" -Headers @{accessToken="..."}
$resp | Should -Not -BeNullOrEmpty
```

Expected: 5 共享配置 + N 服务配置全部导入成功，GET 接口可读回

**验收标准（Task 8）：**
- [ ] `bootstrap-common.yml` 含 Nacos + Vault 完整 bootstrap（5 共享配置 + Vault K8s 认证）
- [ ] 5 个共享配置齐全（datasource/redis/rocketmq/observability/governance-rules）
- [ ] task-orchestrator-prod.yml + memory-service-prod.yml 完整示例（对齐 doc 09 §12.2/§12.3）
- [ ] 所有敏感字段使用 `${vault:secret/data/...}` 占位符，无明文密钥
- [ ] `import-nacos.ps1` 通过 PowerShell 语法检查（`powershell -NoProfile -Command "Get-Command -Syntax .\import-nacos.ps1"`）
- [ ] 实际导入 Nacos 后 GET 可读回

---

## Task 9: SkyWalking Agent 配置 + Trace 透传

**Files:**
- Create: `infra/observability/skywalking/agent.config`
- Create: `infra/observability/skywalking/application.yml`
- Create: `infra/observability/skywalking/custom-plugin/TraceIdHeaderInterceptor.java`
- Create: `infra/observability/skywalking/README.md`

**对齐设计：** doc 09 §11.1 SkyWalking Agent 字节码增强 + 自定义 X-Trace-Id 透传。

- [ ] **Step 9.1: 创建 agent.config（SkyWalking Agent 全局配置）**

文件路径：`e:\git\Agent-Platform-Prototype\infra\observability\skywalking\agent.config`

```properties
# SkyWalking Java Agent 9.7.0 global config (doc 09 §11.1).

# Service name overridden by env SW_AGENT_NAME per service
agent.service_name=${SW_AGENT_NAME:agent-platform-default}
agent.namespace=agent-platform

# OAP backend
collector.backend_service=${SW_COLLECTOR_BACKEND_SERVICE:skywalking-oap.agent-platform-infra:11800}

# Sampling: 5000 traces per 3 seconds
agent.sample_n_per_3_secs=5000

# Ignore static resources
agent.ignore_suffix=.jpg,.png,.css,.js,.html,.ico,.svg

# Cache size for enhanced classes
agent.cache_size=10000

# Logging
logging.level=INFO
logging.file_name=/var/log/agent-platform/skywalking.log
logging.max_file_size=10485760

# Plugin mount for custom TraceId plugin
plugin.mount=agent-platform-custom-plugin

# Exclude unnecessary plugins to reduce overhead
agent.plugin.exclude_plugins=skywalking-spring-annotation-plugin

# Trace context propagation across threads
plugin.toolites_trace_context=org.apache.skywalking.apm.plugin.trace.SimpleDateFormatCallback
```

- [ ] **Step 9.2: 创建 application.yml（OAP Server 配置）**

文件路径：`e:\git\Agent-Platform-Prototype\infra\observability\skywalking\application.yml`

```yaml
# SkyWalking OAP Server 9.7.0 config (doc 09 §11.1).
# Storage: Elasticsearch 8.13 (uses same ES cluster as app).

cluster:
  selector: ${SW_CLUSTER:standalone}
  standalone:

core:
  selector: ${SW_CORE_DEFAULT:default}
  default:
    gRPCPort: 11800
    restPort: 12800
    restContextPath: /
    gRPCSslEnabled: false
    dataPath: /skywalking/data
    enableDataKeeperExecutor: true

storage:
  selector: ${SW_STORAGE:elasticsearch}
  elasticsearch:
    namespace: ${SW_NAMESPACE:"skywalking"}
    clusterNodes: ${SW_STORAGE_ES_CLUSTER_NODES:elasticsearch.agent-platform-infra:9200}
    user: ${SW_ES_USER:}
    password: ${SW_ES_PASSWORD:}
    indexShardsNumber: ${SW_STORAGE_ES_INDEX_SHARDS_NUMBER:2}
    indexReplicasNumber: ${SW_STORAGE_ES_INDEX_REPLICAS_NUMBER:0}
    superDatasetIndexShardsFactor: ${SW_STORAGE_ES_SUPER_DATASET_INDEX_SHARDS_FACTOR:5}
    bulkActions: ${SW_STORAGE_ES_BULK_ACTIONS:2000}
    flushInterval: ${SW_STORAGE_ES_FLUSH_INTERVAL:10}
    concurrentRequests: ${SW_STORAGE_ES_CONCURRENT_REQUESTS:2}

agent-analyzer:
  selector: ${SW_AGENT_ANALYZER:default}
  default:
    sampleRate: ${SW_AGENT_SAMPLE_RATE:5000}

# Health check
health-checker:
  selector: ${SW_HEALTH_CHECKER:-}
  default:
    checkIntervalSeconds: 30
```

- [ ] **Step 9.3: 创建 TraceIdHeaderInterceptor.java（自定义 X-Trace-Id 透传插件）**

文件路径：`e:\git\Agent-Platform-Prototype\infra\observability\skywalking\custom-plugin\TraceIdHeaderInterceptor.java`

```java
package com.agentplatform.observability.skywalking;

import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.slf4j.MDC;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;

/**
 * SkyWalking custom plugin: binds X-Trace-Id HTTP header to SkyWalking TraceID
 * and propagates via MDC for structured logging (doc 09 §11.1 + §11.3).
 *
 * <p>Mount path: agent-platform-custom-plugin (per agent.config plugin.mount).</p>
 */
public class TraceIdHeaderInterceptor implements InstanceMethodsAroundInterceptor {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String MDC_TRACE_ID = "traceId";

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments,
                              Class<?>[] argumentsTypes, MethodInterceptResult result) {
        if (allArguments == null || allArguments.length == 0
                || !(allArguments[0] instanceof HttpServletRequest)) {
            return;
        }
        HttpServletRequest req = (HttpServletRequest) allArguments[0];
        String traceId = req.getHeader(TRACE_ID_HEADER);
        if (traceId == null || traceId.isEmpty()) {
            // Use SkyWalking's global trace id if no incoming header
            traceId = ContextManager.getGlobalTraceId();
        }
        MDC.put(MDC_TRACE_ID, traceId);
        // Bind to current span for cross-system correlation
        AbstractSpan span = ContextManager.activeSpan();
        if (span != null) {
            span.tag("x-trace-id", traceId);
        }
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments,
                               Class<?>[] argumentsTypes, Object ret) {
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
                                      Class<?>[] argumentsTypes, Throwable t) {
        // MDC cleared by TraceIdFilter in finally block (application layer)
    }
}
```

- [ ] **Step 9.4: 创建 README.md（SkyWalking 接入说明）**

文件路径：`e:\git\Agent-Platform-Prototype\infra\observability\skywalking\README.md`

说明：SkyWalking Agent 9.7.0 通过 `-javaagent:/skywalking/skywalking-agent.jar` 嵌入（已在 Dockerfile.base 完成）；`agent.config` 通过 ConfigMap 挂载到 `/skywalking/config/agent.config`；自定义插件位于 `custom-plugin/`，构建为 jar 放到 `/skywalking/plugins/`；服务名通过 `SW_AGENT_NAME` 环境变量按服务注入。

- [ ] **Step 9.5: 验证 agent.config 语法**

```powershell
# SkyWalking agent.config 是 properties 格式
Get-Content e:\git\Agent-Platform-Prototype\infra\observability\skywalking\agent.config | ForEach-Object {
    if ($_ -match '^\s*[^#=\s]+\s*=') { "OK: $_" } elseif ($_ -match '^\s*$' -or $_ -match '^\s*#') { } else { "WARN: $_" }
}
```

Expected: 所有非空非注释行均为 `key=value` 格式

**验收标准（Task 9）：**
- [ ] `agent.config` 含 service_name / OAP 后端 / 采样率 / 自定义插件挂载点
- [ ] `application.yml` OAP 配置指向 infra 命名空间的 ES 集群
- [ ] `TraceIdHeaderInterceptor.java` 实现 X-Trace-Id 与 SkyWalking TraceID 绑定 + MDC 注入
- [ ] 服务名通过 `SW_AGENT_NAME` 环境变量按服务注入（已在 Task 2 的 Dockerfile 完成）

---

## Task 10: Prometheus + Grafana 监控

**Files:**
- Create: `infra/observability/prometheus/prometheus.yml`
- Create: `infra/observability/prometheus/recording-rules.yml`
- Create: `infra/observability/prometheus/alerts/service-availability.yaml`
- Create: `infra/observability/prometheus/alerts/hallucination-rate.yaml`
- Create: `infra/observability/prometheus/alerts/drift-alerts.yaml`
- Create: `infra/observability/grafana/dashboards/agent-platform-overview.json`（占位说明）
- Create: `infra/observability/grafana/datasources/prometheus.yml`
- Create: `infra/observability/grafana/datasources/loki.yml`

**对齐设计：** doc 09 §11.2 Prometheus 指标 + 业务埋点（Counter/Timer）+ doc 09 §1.2.6 幻觉率告警 + §2.3 漂移告警。

- [ ] **Step 10.1: 创建 prometheus.yml（抓取配置）**

文件路径：`e:\git\Agent-Platform-Prototype\infra\observability\prometheus\prometheus.yml`

```yaml
# Prometheus 2.51 scrape config (doc 09 §11.2).
# Scrape all agent-platform services via Kubernetes service discovery + pod annotations.

global:
  scrape_interval: 30s
  scrape_timeout: 10s
  evaluation_interval: 30s
  external_labels:
    cluster: agent-platform-prod

rule_files:
  - /etc/prometheus/rules/*.yml

alerting:
  alertmanagers:
    - static_configs:
        - targets: ['alertmanager.agent-platform-infra:9093']

scrape_configs:
  # Auto-discover agent-platform services via pod annotations
  - job_name: 'agent-platform-services'
    kubernetes_sd_configs:
      - role: pod
        namespaces:
          names: ['agent-platform-prod']
    relabel_configs:
      - source_labels: [__meta_kubernetes_pod_annotation_prometheus_io_scrape]
        action: keep
        regex: 'true'
      - source_labels: [__meta_kubernetes_pod_annotation_prometheus_io_path]
        action: replace
        target_label: __metrics_path__
        regex: (.+)
      - source_labels: [__meta_kubernetes_pod_annotation_prometheus_io_port, __meta_kubernetes_pod_ip]
        action: replace
        target_label: __address__
        regex: (.+);(.+)
        replacement: $2:$1
      - source_labels: [__meta_kubernetes_pod_label_app]
        action: replace
        target_label: service
      - source_labels: [__meta_kubernetes_namespace]
        action: replace
        target_label: namespace

  # SkyWalking OAP self-metrics
  - job_name: 'skywalking-oap'
    static_configs:
      - targets: ['skywalking-oap.agent-platform-infra:12800']

  # Middleware exporters
  - job_name: 'mysql-exporter'
    static_configs:
      - targets: ['mysql-exporter.agent-platform-infra:9104']
  - job_name: 'redis-exporter'
    static_configs:
      - targets: ['redis-exporter.agent-platform-infra:9121']
  - job_name: 'milvus'
    static_configs:
      - targets: ['milvus.agent-platform-infra:9091']
```

- [ ] **Step 10.2: 创建 recording-rules.yml（预聚合规则）**

文件路径：`e:\git\Agent-Platform-Prototype\infra\observability\prometheus\recording-rules.yml`

```yaml
# Pre-aggregated recording rules for dashboard performance (doc 09 §11.2).

groups:
  - name: agent-platform-recording
    interval: 30s
    rules:
      # Task success rate per service
      - record: agent:task_success_rate
        expr: |
          sum(rate(agent_task_completed_total{status="success"}[5m])) by (service, namespace)
          /
          sum(rate(agent_task_submitted_total[5m])) by (service, namespace)

      # Hallucination rate (P2/P1 alert thresholds: 5% / 10%)
      - record: agent:hallucination_rate
        expr: |
          sum(rate(agent_hallucination_detected_total[5m])) by (service, tenant_id)
          /
          sum(rate(agent_task_completed_total[5m])) by (service, tenant_id)

      # Tool call success rate
      - record: agent:tool_call_success_rate
        expr: |
          sum(rate(tool_call_total{result="success"}[5m])) by (tool_id)
          /
          sum(rate(tool_call_total[5m])) by (tool_id)

      # Token consumption rate (per minute)
      - record: agent:token_per_minute
        expr: |
          sum(rate(model_usage_tokens_total[1m])) by (model, tenant_id)

      # Cost per minute (cents)
      - record: agent:cost_per_minute
        expr: |
          sum(rate(model_usage_cost_cent_total[1m])) by (tenant_id)

      # Active agent instances (HPA custom metric source)
      - record: agent:active_instances
        expr: |
          sum(agent_runtime_active_instances) by (service, namespace)
```

- [ ] **Step 10.3: 创建 3 组告警规则**

**a. service-availability.yaml** — 服务可用性告警

文件路径：`e:\git\Agent-Platform-Prototype\infra\observability\prometheus\alerts\service-availability.yaml`

```yaml
groups:
  - name: service-availability
    rules:
      # Service down (no metrics for 2 minutes)
      - alert: AgentServiceDown
        expr: up{job="agent-platform-services"} == 0
        for: 2m
        labels:
          severity: P1
        annotations:
          summary: "Service {{ $labels.service }} is down"
          description: "{{ $labels.service }} in {{ $labels.namespace }} has been down for 2 minutes."

      # High error rate (>5% for 5 minutes)
      - alert: HighErrorRate
        expr: |
          sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m])) by (service)
          /
          sum(rate(http_server_requests_seconds_count[5m])) by (service) > 0.05
        for: 5m
        labels:
          severity: P2
        annotations:
          summary: "High error rate on {{ $labels.service }}"
          description: "Error rate > 5% for 5 minutes."

      # High latency P95 > 2s
      - alert: HighLatencyP95
        expr: |
          histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket[5m])) by (le, service)) > 2
        for: 5m
        labels:
          severity: P2
        annotations:
          summary: "High P95 latency on {{ $labels.service }}"
          description: "P95 latency > 2s for 5 minutes."
```

**b. hallucination-rate.yaml** — 幻觉率告警（doc 09 §1.2.6）

```yaml
groups:
  - name: hallucination-governance
    rules:
      # Daily hallucination rate > 5% -> P2
      - alert: HallucinationRateWarning
        expr: agent:hallucination_rate > 0.05
        for: 10m
        labels:
          severity: P2
        annotations:
          summary: "Hallucination rate > 5% on {{ $labels.service }}"
          description: "Daily hallucination rate exceeds 5% threshold (doc 09 §1.2.6)."

      # Daily hallucination rate > 10% -> P1, auto-pause agent
      - alert: HallucinationRateCritical
        expr: agent:hallucination_rate > 0.10
        for: 10m
        labels:
          severity: P1
        annotations:
          summary: "Hallucination rate CRITICAL > 10% on {{ $labels.service }}"
          description: "Auto-pause agent triggered. Manual review required (doc 09 §1.2.6)."
```

**c. drift-alerts.yaml** — 漂移告警（doc 09 §2.3）

```yaml
groups:
  - name: drift-governance
    rules:
      # drift_score > 0.3 for 3 days -> P3, task-level correction
      - alert: DriftScoreTaskLevel
        expr: agent_drift_score > 0.3
        for: 72h
        labels:
          severity: P3
        annotations:
          summary: "Drift score > 0.3 for 3 days on {{ $labels.agent_id }}"
          description: "Trigger task-level correction (doc 09 §2.3)."

      # drift_score > 0.5 single day -> P2, system-level
      - alert: DriftScoreSystemLevel
        expr: agent_drift_score > 0.5
        for: 1h
        labels:
          severity: P2
        annotations:
          summary: "Drift score > 0.5 on {{ $labels.agent_id }}"
          description: "Trigger system-level rollback (doc 09 §2.3)."

      # drift_score > 0.7 single day -> P1, auto-rollback stable version
      - alert: DriftScoreCritical
        expr: agent_drift_score > 0.7
        for: 5m
        labels:
          severity: P1
        annotations:
          summary: "Drift CRITICAL > 0.7 on {{ $labels.agent_id }}"
          description: "Auto-rollback to stable agent_version (doc 09 §2.3)."

      # Memory error rate > 5%
      - alert: MemoryErrorRateHigh
        expr: agent_memory_error_rate > 0.05
        for: 1h
        labels:
          severity: P2
        annotations:
          summary: "Memory error rate > 5% in domain {{ $labels.domain }}"
          description: "Trigger memory drift correction (doc 09 §2.2.2)."
```

- [ ] **Step 10.4: 创建 Grafana datasources（Prometheus + Loki）**

文件路径：`e:\git\Agent-Platform-Prototype\infra\observability\grafana\datasources\prometheus.yml`

```yaml
apiVersion: 1
datasources:
  - name: Prometheus
    type: prometheus
    access: proxy
    url: http://prometheus.agent-platform-infra:9090
    isDefault: true
    editable: false
```

文件路径：`e:\git\Agent-Platform-Prototype\infra\observability\grafana\datasources\loki.yml`

```yaml
apiVersion: 1
datasources:
  - name: Loki
    type: loki
    access: proxy
    url: http://loki.agent-platform-infra:3100
    isDefault: false
    editable: false
    jsonData:
      maxLines: 1000
```

- [ ] **Step 10.5: 创建 agent-platform-overview.json（占位说明）**

文件路径：`e:\git\Agent-Platform-Prototype\infra\observability\grafana\dashboards\agent-platform-overview.json`

```json
{
  "title": "Agent Platform Overview",
  "comment": "Placeholder dashboard - import from Grafana.com or build via UI. Required panels: task success rate, hallucination rate, tool call success, model cost, token consumption, drift trend, quota usage (doc 09 §11.2).",
  "panels": [
    {"title": "Task Success Rate", "query": "agent:task_success_rate", "type": "stat"},
    {"title": "Hallucination Rate", "query": "agent:hallucination_rate", "type": "gauge"},
    {"title": "Tool Call Success", "query": "agent:tool_call_success_rate", "type": "stat"},
    {"title": "Model Cost (per min)", "query": "agent:cost_per_minute", "type": "timeseries"},
    {"title": "Token Consumption", "query": "agent:token_per_minute", "type": "timeseries"},
    {"title": "Drift Score Trend", "query": "agent_drift_score", "type": "timeseries"},
    {"title": "Active Agent Instances", "query": "agent:active_instances", "type": "stat"}
  ]
}
```

- [ ] **Step 10.6: 验证 Prometheus 配置**

```powershell
# If promtool available:
promtool check config e:\git\Agent-Platform-Prototype\infra\observability\prometheus\prometheus.yml
promtool check rules e:\git\Agent-Platform-Prototype\infra\observability\prometheus\recording-rules.yml
Get-ChildItem e:\git\Agent-Platform-Prototype\infra\observability\prometheus\alerts\ | ForEach-Object { promtool check rules $_.FullName }
```

Expected: 所有配置与规则文件通过 promtool 检查

**验收标准（Task 10）：**
- [ ] `prometheus.yml` 含 K8s SD 自动发现 + 中间件 exporter 抓取
- [ ] `recording-rules.yml` 含 6 条预聚合规则（成功率/幻觉率/工具成功率/Token/成本/活动实例）
- [ ] 3 组告警规则齐全（service-availability + hallucination-rate + drift-alerts）
- [ ] 告警阈值与 doc 09 §1.2.6（幻觉 5%/10%）+ §2.3（漂移 0.3/0.5/0.7）一致
- [ ] Grafana 2 个数据源（Prometheus + Loki）配置正确
- [ ] `agent-platform-overview.json` 含 7 个必需 panel

---

## Task 11: Loki 日志聚合 + 结构化 JSON 日志

**Files:**
- Create: `infra/observability/loki/loki-config.yml`
- Create: `infra/observability/loki/promtail-config.yml`
- Create: `infra/observability/loki/logback-spring.xml`（应用端结构化日志模板）
- Create: `infra/observability/loki/README.md`

**对齐设计：** doc 09 §11.3 Logback JSON + Promtail 采集 + Loki 存储 + TraceID 注入 MDC。

- [ ] **Step 11.1: 创建 loki-config.yml**

文件路径：`e:\git\Agent-Platform-Prototype\infra\observability\loki\loki-config.yml`

```yaml
# Loki 3.0 single-node config (dev/staging). For prod use S3 backend + memcached.
auth_enabled: false

server:
  http_listen_port: 3100
  grpc_listen_port: 9096

common:
  path_prefix: /loki
  storage:
    filesystem:
      chunks_directory: /loki/chunks
      rules_directory: /loki/rules
  replication_factor: 1
  ring:
    kvstore:
      store: inmemory

schema_config:
  configs:
    - from: 2026-01-01
      store: tsdb
      object_store: filesystem
      schema: v13
      index:
        prefix: index_
        period: 24h

limits_config:
  retention_period: 30d
  max_query_length: 720h
  reject_old_samples: true
  reject_old_samples_max_age: 168h
  ingestion_rate_mb: 10
  ingestion_burst_size_mb: 20
  # Trace ID-based log correlation
  max_entries_limit_per_query: 5000

analytics:
  reporting_enabled: false
```

- [ ] **Step 11.2: 创建 promtail-config.yml**

文件路径：`e:\git\Agent-Platform-Prototype\infra\observability\loki\promtail-config.yml`

```yaml
# Promtail 3.0 config (doc 09 §11.3).
# Scrapes container stdout logs from K8s pod annotations.

server:
  http_listen_port: 9080
  grpc_listen_port: 0

positions:
  filename: /var/lib/promtail/positions.yaml

clients:
  - url: http://loki.agent-platform-infra:3100/loki/api/v1/push
    tenant_id: agent-platform

scrape_configs:
  # K8s pod discovery
  - job_name: agent-platform-pods
    kubernetes_sd_configs:
      - role: pod
        namespaces:
          names: [agent-platform-prod, agent-platform-infra]
    relabel_configs:
      - source_labels: [__meta_kubernetes_namespace]
        target_label: namespace
      - source_labels: [__meta_kubernetes_pod_label_app]
        target_label: app
      - source_labels: [__meta_kubernetes_pod_name]
        target_label: pod
      - source_labels: [__meta_kubernetes_pod_node_name]
        target_label: node
    pipeline_stages:
      # Parse JSON log line
      - json:
          expressions:
            level: level
            traceId: traceId
            tenantId: tenantId
            taskId: taskId
            logger: logger
            app: app
            ts: "@timestamp"
      # Promote fields to labels for fast filtering
      - labels:
          level:
          app:
      # Store traceId as structured metadata (Loki 3.0 feature)
      - structured_metadata:
          traceId:
          tenantId:
          taskId:

  # Container log files (fallback for non-JSON logs)
  - job_name: container-logs
    static_configs:
      - targets: [localhost]
        labels:
          job: container
          __path__: /var/log/containers/*.log
```

- [ ] **Step 11.3: 创建 logback-spring.xml（应用端结构化日志模板）**

文件路径：`e:\git\Agent-Platform-Prototype\infra\observability\loki\logback-spring.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!--
  Logback structured JSON template for all agent-platform services (doc 09 §11.3).
  Copy to each service's src/main/resources/logback-spring.xml.
  Outputs JSON to stdout (captured by Promtail via container logs).

  Required dependencies (in each service pom.xml):
    <dependency>
      <groupId>net.logstash.logback</groupId>
      <artifactId>logstash-logback-encoder</artifactId>
      <version>7.4</version>
    </dependency>
-->
<configuration>
    <springProperty scope="context" name="appName" source="spring.application.name"/>
    <springProperty scope="context" name="env" source="spring.profiles.active"/>

    <appender name="JSON" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
            <includeMdcKeyName>traceId</includeMdcKeyName>
            <includeMdcKeyName>spanId</includeMdcKeyName>
            <includeMdcKeyName>tenantId</includeMdcKeyName>
            <includeMdcKeyName>userId</includeMdcKeyName>
            <includeMdcKeyName>taskId</includeMdcKeyName>
            <includeMdcKeyName>agentId</includeMdcKeyName>
            <customFields>
                {"app":"${appName}","env":"${env}"}</customFields>
            <fieldNames>
                <timestamp>@timestamp</timestamp>
                <message>message</message>
                <thread>thread</thread>
                <level>level</level>
                <logger>logger</logger>
            </fieldNames>
        </encoder>
    </appender>

    <!-- Sensitive field masking (doc 09 §12.4 禁止事项) -->
    <appender name="MASKED_JSON" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
            <jsonGeneratorDecorator class="net.logstash.logback.mask.MaskingJsonGeneratorDecorator">
                <defaultMaskingMatcher>password|secret|apiKey|api_key|token|authorization</defaultMaskingMatcher>
            </jsonGeneratorDecorator>
        </encoder>
    </appender>

    <root level="INFO">
        <appender-ref ref="JSON"/>
    </root>

    <logger name="com.agentplatform" level="INFO"/>
    <logger name="org.springframework" level="WARN"/>
    <logger name="org.hibernate" level="WARN"/>
    <logger name="io.grpc" level="WARN"/>
</configuration>
```

- [ ] **Step 11.4: 创建 README.md（Loki 接入说明）**

文件路径：`e:\git\Agent-Platform-Prototype\infra\observability\loki\README.md`

```markdown
# Loki 日志聚合

## 架构
- 应用端：Logback 输出结构化 JSON 到 stdout，含 TraceID/tenantId/taskId（MDC 注入）
- Promtail：通过 K8s SD 发现 pod，解析 JSON 日志，提取字段为 Loki label/structured_metadata
- Loki 3.0：TSDB schema v13，单节点 dev / S3 后端 prod
- Grafana：通过 Loki 数据源查询，支持 LogQL `{app="agent-runtime"} | json | traceId="a1b2c3d4"`

## 应用接入步骤
1. 各服务 pom.xml 添加 logstash-logback-encoder 7.4 依赖
2. 将 `logback-spring.xml` 复制到 `src/main/resources/`
3. 应用代码中通过 `MDC.put("traceId", traceId)` 注入 traceId（TraceIdFilter 已实现）

## 查询示例
```logql
# 按 traceId 查全链路日志
{app=~"agent-.*"} | json | traceId="a1b2c3d4"

# 查所有 ERROR 日志
{app=~"agent-.*"} | json | level="ERROR"

# 按 tenantId 过滤
{app="agent-runtime"} | json | tenantId="1001"

# 查指定任务的所有日志
{app=~"agent-.*"} | json | taskId="tk_001"
```

## 保留策略
- dev: 7 天
- staging: 30 天
- prod: 90 天（通过 limits_config.retention_period 配置）

## 脱敏
logback-spring.xml 中已配置 MaskingJsonGeneratorDecorator，自动屏蔽 password/secret/apiKey/token 等字段。
```

- [ ] **Step 11.5: 验证 Loki 配置**

```powershell
# Loki config syntax (if loki binary available)
loki -config.file=e:\git\Agent-Platform-Prototype\infra\observability\loki\loki-config.yml -verify-config
# Promtail config syntax (if promtail binary available)
promtail -config.file=e:\git\Agent-Platform-Prototype\infra\observability\loki\promtail-config.yml -verify-config
```

Expected: 两条命令均通过

**验收标准（Task 11）：**
- [ ] `loki-config.yml` 含 TSDB v13 schema + 30d 保留 + TraceID 关联
- [ ] `promtail-config.yml` 含 K8s SD + JSON 解析 + traceId 提取为 structured_metadata
- [ ] `logback-spring.xml` 含 5 个 MDC 字段 + 脱敏 MaskingJsonGeneratorDecorator
- [ ] README 含 LogQL 查询示例（按 traceId/tenantId/taskId）
- [ ] 脱敏规则覆盖 password/secret/apiKey/token/authorization

---

## Task 12: 健康检查与优雅停机（端到端验证）

**Files:**
- Update: 各服务 K8s Deployment 的探针配置（已在 Task 6 完成）
- Create: `infra/scripts/health-check.ps1`

**对齐设计：** doc 09 §13.2 readiness/liveness/startup + preStop graceful shutdown + Spring Boot Actuator endpoints。

- [ ] **Step 12.1: 确认 Actuator 端点配置（每个服务 application.yml 需含）**

每个服务的 `application.yml`（或 Nacos 服务级配置）必须含以下 Actuator 配置：

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,metrics,shutdown
      base-path: /actuator
  endpoint:
    health:
      probes:
        enabled: true
      show-details: when-authorized
      group:
        readiness:
          include: db,redis,milvus,rocketmq  # 按服务依赖选
        liveness:
          include: ping
    shutdown:
      enabled: true
  health:
    livenessstate:
      enabled: true
    readinessstate:
      enabled: true
```

- [ ] **Step 12.2: 确认 Deployment 三探针配置（已在 Task 6 完成）**

每个 Deployment 含：
- `startupProbe`（failureThreshold=30, periodSeconds=10，总等待 5 分钟启动）
- `readinessProbe`（/actuator/health/readiness，决定是否接流量）
- `livenessProbe`（/actuator/health/liveness，失败触发重启）
- `preStop`（sleep 10 + curl /actuator/shutdown，优雅退出）

- [ ] **Step 12.3: 创建 health-check.ps1（部署后健康检查脚本）**

文件路径：`e:\git\Agent-Platform-Prototype\infra\scripts\health-check.ps1`

```powershell
<#
.SYNOPSIS
    Post-deploy health check for all agent-platform services.
.DESCRIPTION
    Polls /actuator/health of each service via kubectl port-forward or Ingress.
    Fails if any service is not UP within timeout.
.PARAMETER Namespace
    K8s namespace. Default: agent-platform-prod
.PARAMETER TimeoutSeconds
    Per-service timeout. Default: 120
.EXAMPLE
    .\health-check.ps1 -Namespace agent-platform-prod
#>
param(
    [string]$Namespace = "agent-platform-prod",
    [int]$TimeoutSeconds = 120,
    [string]$LogFile = "./logs/health-check-$(Get-Date -Format 'yyyyMMdd-HHmmss').log"
)

$ErrorActionPreference = "Stop"
$logDir = Split-Path -Parent $LogFile
if (-not (Test-Path $logDir)) { New-Item -ItemType Directory -Path $logDir -Force | Out-Null }

function Write-Log {
    param([string]$Level, [string]$Message)
    $ts = Get-Date -Format "yyyy-MM-dd HH:mm:ss.fff"
    $line = "[$ts] [$Level] $Message"
    Add-Content -Path $LogFile -Value $line -Encoding UTF8
    switch ($Level) {
        "ERROR" { Write-Host $line -ForegroundColor Red }
        "OK"    { Write-Host $line -ForegroundColor Cyan }
        default { Write-Host $line -ForegroundColor Green }
    }
}

$services = @(
    @{name="agent-gateway"; port=8080},
    @{name="agent-session"; port=8082},
    @{name="agent-task-orchestrator"; port=8084},
    @{name="agent-memory"; port=8088},
    @{name="agent-tool-engine"; port=8090},
    @{name="agent-runtime"; port=8092},
    @{name="agent-model-gateway"; port=8094},
    @{name="agent-repo"; port=8096},
    @{name="agent-knowledge"; port=8098},
    @{name="agent-quality"; port=8100},
    @{name="agent-risk-control"; port=8102},
    @{name="agent-observability"; port=8104}
)

Write-Log "INFO" "=== Health Check Start (ns=$Namespace) ==="

$allHealthy = $true
foreach ($svc in $services) {
    $svcName = $svc.name
    $svcPort = $svc.port
    Write-Log "INFO" "Checking $svcName ..."

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    $healthy = $false
    while ((Get-Date) -lt $deadline) {
        # Use kubectl exec to curl from inside the pod (no port-forward needed)
        $pod = kubectl get pods -n $Namespace -l app=$svcName -o jsonpath='{.items[0].metadata.name}' 2>$null
        if (-not $pod) {
            Start-Sleep -Seconds 5
            continue
        }
        $result = kubectl exec $pod -n $Namespace -- curl -fs -m 5 http://localhost:$svcPort/actuator/health 2>$null
        if ($result -match '"status":"UP"') {
            $healthy = $true
            break
        }
        Start-Sleep -Seconds 5
    }

    if ($healthy) {
        Write-Log "OK" "$svcName healthy"
    } else {
        Write-Log "ERROR" "$svcName NOT healthy within ${TimeoutSeconds}s"
        $allHealthy = $false
    }
}

if ($allHealthy) {
    Write-Log "OK" "=== All services healthy ==="
    exit 0
} else {
    Write-Log "ERROR" "=== Some services unhealthy, see log: $LogFile ==="
    exit 1
}
```

- [ ] **Step 12.4: 验证健康检查脚本**

```powershell
# 语法检查
powershell -NoProfile -Command "Get-Command -Syntax e:\git\Agent-Platform-Prototype\infra\scripts\health-check.ps1"
# 帮助检查
powershell -NoProfile -File e:\git\Agent-Platform-Prototype\infra\scripts\health-check.ps1 -? 2>$null
```

Expected: 脚本可被 PowerShell 解析，参数定义正确

**验收标准（Task 12）：**
- [ ] Actuator 配置含 readiness/liveness 分组 + shutdown 端点启用
- [ ] Deployment 含 startup + readiness + liveness + preStop 四组件
- [ ] `health-check.ps1` 含 12 服务健康轮询 + 超时机制
- [ ] 健康检查通过 `kubectl exec` 在 pod 内 curl，无需 port-forward
- [ ] 全部服务健康时返回 exit 0，否则 exit 1

---

## Task 13: 部署脚本（PowerShell）

**Files:**
- Create: `infra/scripts/build-all.ps1`
- Create: `infra/scripts/deploy.ps1`
- Create: `infra/scripts/deploy-middleware.ps1`

**对齐设计：** doc 09 §15.2 deploy.ps1 + §15.3 build-all.ps1 + infra/sql/init-all.ps1 编排风格（Write-Log + Find-Tool + Skip 开关）。

- [ ] **Step 13.1: 创建 build-all.ps1（Maven 构建 + Docker 镜像构建）**

文件路径：`e:\git\Agent-Platform-Prototype\infra\scripts\build-all.ps1`

```powershell
<#
.SYNOPSIS
    Agent Platform full build: Maven compile + Docker images.
.DESCRIPTION
    1. Run maven clean package (skip tests optional).
    2. Build Docker image per service using infra/docker/Dockerfile.<service>.
    3. Optionally push to registry.
.PARAMETER SkipTests
    Skip unit tests.
.PARAMETER SkipImage
    Skip Docker image build.
.PARAMETER PushImage
    Push built images to registry.
.PARAMETER Services
    Comma-separated service list. Default: all.
.PARAMETER ImageTag
    Image tag. Default: yyyyMMdd-HHmmss.
.EXAMPLE
    .\build-all.ps1 -SkipTests -PushImage
#>
param(
    [switch]$SkipTests,
    [switch]$SkipImage,
    [switch]$PushImage,
    [string]$Services = "all",
    [string]$ImageTag = (Get-Date -Format "yyyyMMdd-HHmmss"),
    [string]$LogFile = "./logs/build-$((Get-Date -Format 'yyyyMMdd-HHmmss')).log"
)

$ErrorActionPreference = "Stop"
$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = Split-Path -Parent (Split-Path -Parent $scriptRoot)
$logDir = Split-Path -Parent $LogFile
if (-not (Test-Path $logDir)) { New-Item -ItemType Directory -Path $logDir -Force | Out-Null }

function Write-Log {
    param([string]$Level, [string]$Message)
    $ts = Get-Date -Format "yyyy-MM-dd HH:mm:ss.fff"
    $line = "[$ts] [$Level] $Message"
    Add-Content -Path $LogFile -Value $line -Encoding UTF8
    switch ($Level) {
        "ERROR" { Write-Host $line -ForegroundColor Red }
        "WARN"  { Write-Host $line -ForegroundColor Yellow }
        "OK"    { Write-Host $line -ForegroundColor Cyan }
        default { Write-Host $line -ForegroundColor Green }
    }
}

function Find-Tool {
    param([string]$Name, [string]$FallbackPath)
    $exe = (Get-Command $Name -ErrorAction SilentlyContinue).Source
    if ($exe) { return $exe }
    if ($FallbackPath -and (Test-Path $FallbackPath)) { return $FallbackPath }
    return $null
}

Write-Log "INFO" "=== Agent Platform Build Start ==="
Write-Log "INFO" "Project root: $projectRoot"
Write-Log "INFO" "Image tag: $ImageTag"
Write-Log "INFO" "Log: $LogFile"

# Step 1: Maven build
$mvn = Find-Tool "mvn"