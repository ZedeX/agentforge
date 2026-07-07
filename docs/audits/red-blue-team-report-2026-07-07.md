# AgentForge 红蓝对抗检测报告

> **检测日期**：2026-07-07  
> **检测方**：红蓝对抗对抗模拟（静态代码审计 + 配置审计 + 架构威胁建模）  
> **检测范围**：`e:\git\Agent-Platform-Prototype` 全部 19 模块 + infra 部署配置 + CI  
> **检测方法**：STRIDE 威胁建模 + 4 维度并行勘探 agent + 关键代码人工核实  
> **置信度声明**：所有发现标注证据 `file:line`；agent 摘录类发现已人工读码核实，误判已剔除。标注 `[KNOWN]`（已核实事实）/ `[INFERRED]`（推断）/ `[GUESS]`（推测）。

---

## 0. 执行摘要

| 维度 | 风险评级 | 关键问题数 | 一句话结论 |
|---|---|---|---|
| **安全性** | 🔴 **高危 (HIGH)** | 11 | 存在硬编码后门 API Key、调用方可绕过风险分级、JWT 密钥入库、gRPC 全明文、K8s RBAC 过宽 |
| **稳定性** | 🟠 **中高危 (MED-HIGH)** | 9 | Resilience4j 已引入但不完整、RocketMQ 消费者无幂等、跨服务裸 RPC 无补偿、JVM 堆配置超 pod 内存限制 |
| **健壮性** | 🟠 **中危 (MED)** | 10 | gRPC 入参缺校验、Schema 校验是字符串包含而非 JSON 解析、金额用 double、异常被吞、checkpoint JSON 拼接 |
| **功能完备性** | 🟡 **中危 (MED)** | 7 | project_memory 标 Plan 05/06 为 0/N **过时**，实际已实现；但 Mock 客户端默认注入、ABAC 缺失、三级校验仅骨架、前端无代码 |

**最严重结论**：当前代码库存在 **3 条可直接利用的未授权 RCE/越权路径**（详见 §4 攻击链），不适合以现配置上生产。核心问题不是"没做"，而是"做了但有后门式绕过"——`AuthFilter` 硬编码 key、`ToolGateway` 调用方自填风险等级、`PermissionChecker` 是 mock。

**已修正的 agent 误判**（避免误导）：
- ❌ "JWT 无过期校验" → 实际 `JwtUtil.java:49` 设了 expiration，`parseSignedClaims` 自动校验
- ❌ "MemoryRecordMapper SQL 注入" → 它是 MapStruct 映射器，全项目无 MyBatis `@Mapper`/`@Select`
- ❌ "Plan 05/06 仅启动类无业务逻辑" → 实际 tool-engine ~85 文件、runtime ~70 文件均含实现
- ❌ "ReAct 无最大循环熔断" → `ReActLoopImpl.java:134,240` 有 maxSteps + maxRetry

---

## 1. 系统现状速览（已核实）

| 模块 | 端口 | 实际状态 | 证据 |
|---|---|---|---|
| agent-gateway | 8080/9091 | 完整但 AuthFilter 有后门 | `AuthFilter.java:29` |
| agent-tool-engine | 8090/9090 | **已完整实现**（9/9 PRD 组件 + Docker 沙箱 + 4 执行器） | `ToolGatewayImpl.java` `DockerSandboxBorrower.java` |
| agent-runtime | 8092 | **已完整实现**（ReAct/Reflexion/TokenWatermark/StepStateSyncer） | `ReActLoopImpl.java` |
| agent-risk-control | 8102 | 服务骨架完整，但 PermissionChecker 是 mock | `PermissionCheckerImpl.java:37` |
| agent-model-gateway | 8094 | 完整（CostMeter/Quota/4 adapter） | — |
| agent-memory | 8088 | 完整（Milvus 双轨） | — |
| agent-task-orchestrator | 8084 | 完整（状态机/MQ producer/consumer） | — |
| infra/k8s | — | 13 deployments + HPA + PDB + Ingress 齐全，但缺 securityContext | `agent-tool-engine.yaml` |
| infra/docker-compose | — | dev 栈完整，默认密码遍地 | `docker-compose.yml` |

---

## 2. 检测方法

1. **威胁建模**（brainstorming skill）：按 STRIDE 对 19 模块列攻击面，识别 7 类高优靶点
2. **并行勘探**：派 4 个 Explore agent 分别覆盖 安全/稳定/健壮/完备
3. **人工核实**：对 agent 报告中所有"高危"发现读源码确认，剔除 4 处误判
4. **配置审计**：人工读 `application.yml`/`docker-compose.yml`/K8s manifests/CI workflow
5. **TDD 回归计划**（writing-plans + tdd skill）：为每条高危发现起草红绿循环测试

**未覆盖**：动态运行时测试（未起服务跑 fuzz）、依赖 CVE 数据库扫描（仅版本对照）、前端（无代码）、供应链完整性。

---

## 3. 红队发现（按严重度）

### 🔴 CRITICAL

#### R-01 · AuthFilter 硬编码后门 API Key
- **证据**：`agent-gateway/src/main/java/com/agent/gateway/filter/AuthFilter.java:29`
  ```java
  private static final String VALID_API_KEY = "ak_test_valid_key_2026";
  ```
- **攻击**：任何外部攻击者发送 `X-API-Key: ak_test_valid_key_2026` 即通过鉴权，被识别为 `userId="system"`（`AuthFilter.java:76`），并可任意填写 `X-Tenant-Id` 头跨租户。
- **置信度**：[KNOWN] HIGH — 代码确凿
- **影响**：鉴权完全绕过，等同于无鉴权

#### R-02 · ToolGateway 调用方可自填 riskLevel 绕过审批
- **证据**：`agent-tool-engine/src/main/java/com/agent/tool/engine/api/impl/ToolGatewayImpl.java:184-191`
  ```java
  ToolRiskLevel riskLevel = request.getRiskLevel();
  if (riskLevel == null) {
      assessment = riskClassifier.classify(meta, request);
  } else {
      assessment = new RiskAssessment(riskLevel, riskLevel.requiresApproval(),
              "caller-declared " + riskLevel);
  }
  ```
- **攻击**：`RiskClassifierImpl` 本身设计良好（never-downgrade + PII 强制 R3 + R3 必审批，见 `RiskClassifierImpl.java:62-106`），但调用方在 `ToolCallRequest.riskLevel` 字段填 `R1` 即跳过整个分级逻辑，R3 工具无需审批直接执行。任何能调 `ToolGateway.invoke` gRPC 的客户端（agent-runtime / 直接 gRPC 调用者）均可利用。
- **置信度**：[KNOWN] HIGH
- **影响**：PRD 宣称的"R3 高危单独审批 + 沙箱隔离"被绕过

#### R-03 · JWT 密钥硬编码并提交仓库
- **证据**：`agent-gateway/src/main/resources/application.yml:49`
  ```yaml
  secret: "agent-platform-jwt-secret-key-please-change-in-production-32bytes"
  ```
- **攻击**：任何拿到源码者（包括公开 repo 后的外部人员）可用此密钥伪造任意 userId/tenantId/roles 的合法 JWT。`JwtUtil` 的过期校验和 issuer 校验形同虚设。
- **置信度**：[KNOWN] HIGH
- **影响**：完整身份伪造

#### R-04 · K8s RoleBinding 授予所有 SA 读取全部 secrets
- **证据**：`infra/k8s/01-serviceaccounts.yaml:13-38`
  ```yaml
  rules:
    - apiGroups: [""]
      resources: ["configmaps", "secrets", "pods"]
      verbs: ["get", "list", "watch"]
  subjects:
    - kind: Group
      name: system:serviceaccounts:agent-platform-prod
  ```
- **攻击**：任一 pod 被攻陷（如 R-01/R-02 链式利用拿到 RCE），攻击者用 pod 内自动挂载的 SA token 调 K8s API，`get/list` 命名空间内**所有服务的 secrets**——包括模型 API Key、DB 密码、其他服务的 JWT 密钥。完美横向移动。
- **置信度**：[KNOWN] HIGH
- **影响**：单点攻陷 → 全集群沦陷

### 🟠 HIGH

#### R-05 · 服务间 gRPC 全明文，mTLS 仅注释
- **证据**：`agent-gateway/src/main/resources/application.yml:31-42`
  ```yaml
  # security:
  #   certificateChain: ...
  #   clientAuth: REQUIRE
  client:
    risk-control:
      address: static://localhost:9092
      negotiation-type: plaintext
  ```
- **攻击**：网络位置内的攻击者可 MITM gRPC 流量（含 tool 调用参数、记忆召回结果、模型响应），或直接 plaintext 连 9090-9108 端口冒充任一服务调用。
- **置信度**：[KNOWN] HIGH
- **影响**：机密性 + 完整性全失

#### R-06 · PermissionChecker 是 mock，未知用户走 defaultAction
- **证据**：`agent-risk-control/src/main/java/com/agent/riskcontrol/api/impl/PermissionCheckerImpl.java:37-41,62-70`
  ```java
  private static final Map<String, String> USER_ROLES = Map.of(
      "admin-001", "admin", "user-001", "user", "viewer-001", "viewer");
  ...
  if (role == null) {
      boolean allowed = "allow".equalsIgnoreCase(properties.getPermission().getDefaultAction());
  ```
- **攻击**：仅 3 个硬编码用户；真实用户全走 `defaultAction`。dev 环境该值极可能是 `allow`（需查 `RiskControlProperties`）。即便 prod 设 `deny`，PRD 宣称的 "RBAC + ABAC" 完全未落地——只有 3 个角色的硬编码 mock，无 IAM 集成、无 ABAC 属性、无多租户数据隔离。
- **置信度**：[KNOWN] HIGH（mock 事实）；[INFERRED] dev defaultAction=allow
- **影响**：授权层形同虚设

#### R-07 · Docker 沙箱缺关键加固
- **证据**：`agent-tool-engine/src/main/java/com/agent/tool/engine/sandbox/DockerSandboxBorrower.java:347-368`
  - 缺 `withCapDrop(Capability.NO_CAPS)` 或 `--cap-drop=ALL` —— 容器保留默认 capabilities
  - 缺 `withUser("nonroot")` —— 默认以 root 运行
  - 缺 `withSecurityOpt("no-new-privileges")`
  - 缺 `withPidsLimit()` —— fork bomb 无阻
  - 缺 `withReadonlyRootfs(true)`
  - `withBinds(...)` API 暴露（`buildHostConfig:359-366`）—— 默认 spec 不挂载，但 `borrow(spec)` 接受任意 spec，若调用方传用户可控 `mounts` 可挂宿主 `/` → 容器逃逸
- **正向**：`networkMode("none")` ✓、cpu/mem 限制 ✓、tmpfs ✓、超时 kill ✓
- **置信度**：[KNOWN] HIGH
- **影响**：R3 沙箱逃逸门槛降低；结合 R-02 可达宿主 RCE

#### R-08 · K8s Deployment 无 securityContext，actuator 暴露
- **证据**：`infra/k8s/deployments/agent-tool-engine.yaml:34-100`
  - 容器无 `securityContext.runAsNonRoot/runAsUser/fsGroup/seccompProfile`
  - `prometheus.io/scrape: "true"` + `/actuator/prometheus` 暴露
  - `preStop` 调 `curl -X POST http://localhost:8090/actuator/shutdown`（`agent-tool-engine.yaml:96`）—— 证明 actuator shutdown 端点可达
- **攻击**：能到达 pod 端口 8090 的攻击者可访问 `/actuator/shutdown`（DoS）、`/actuator/env`（泄露配置/密钥）、`/actuator/heapdump`（泄露内存中的 token/密钥）。无 securityContext = root 运行 = 容器内提权后逃逸更容易。
- **置信度**：[KNOWN] HIGH（actuator 暴露 + 无 securityContext）；[INFERRED] actuator 未配 management.security
- **影响**：DoS + 信息泄露 + 提权

#### R-09 · CI 无密钥扫描 / 依赖 CVE 扫描 / SAST
- **证据**：`.github/workflows/ci.yml` 全文仅 `compile + test + verify + jacoco`
  - 无 gitleaks/trufflehog/CodeQL/Trivy/OWASP Dependency-Check
- **攻击**：解释了为何 R-01/R-03 的硬编码密钥能长存仓库。依赖（Spring Boot 3.2.5 / protobuf 3.25.1 / Spring AI 0.8.1 / docker-java）的已知 CVE 不会被门禁拦截。
- **置信度**：[KNOWN] HIGH（CI 配置事实）；[INFERRED] 依赖 CVE 列表需 `mvn dependency:tree + NVD` 比对
- **影响**：漏洞持续引入

### 🟡 MED

#### R-10 · dev 环境默认密码遍地 + ES/Nacos/Vault 弱配置
- **证据**：`infra/docker-compose/docker-compose.yml`
  - MySQL/Redis/Neo4j/MinIO/Grafana 默认 `agentplatform`（L34,58,151,162,246）
  - Milvus minio 硬编码 `minioadmin/minioadmin` 不可 env 覆盖（L83-84）
  - ES `xpack.security.enabled=false`（L134）
  - Nacos `NACOS_AUTH_TOKEN=agentplatform-secret-key-2026` + `serverIdentity/agentplatform`（L179-181）—— 已知 Nacos 默认身份绕过模式
  - Vault dev mode `VAULT_DEV_ROOT_TOKEN_ID=agentplatform`（L197）
- **置信度**：[KNOWN] MED（dev 栈，但常被误用到 prod）
- **影响**：dev 环境即可被完全接管

#### R-11 · 审计落库失败被吞
- **证据**：`ToolGatewayImpl.java:318-320,332-334,346-348`
  ```java
  } catch (Exception auditEx) {
      log.error("审计落库失败 (吞异常): traceId={}, err={}", traceId, auditEx.getMessage());
  }
  ```
- **攻击**：攻击者只需让 audit DB 短暂不可用，所有 R3 工具调用即"成功执行但无审计记录"。违反 PRD "全量调用审计留痕"。
- **置信度**：[KNOWN] MED
- **影响**：合规破坏 + 取证断链

#### R-12 · `isInternalGrpcCall` 头部信任后门（潜伏）
- **证据**：`AuthFilter.java:92-114`
  - 方法定义 + javadoc 明确说"判定为内部 gRPC 链路时跳过 JWT/API-Key 校验"
  - 当前 `doFilterInternal` 未调用它（死代码），但方法 `public` 可被未来误接入
- **置信度**：[KNOWN] MED（潜伏陷阱）
- **影响**：若未来有人在 filter 链接入 `X-Internal-Source: grpc` 头判断，即客户端可伪造的鉴权绕过

#### R-13 · AuthFilter API-Key 路径租户 ID 取自客户端
- **证据**：`AuthFilter.java:77` `tenantId = request.getHeader("X-Tenant-Id");`
- **攻击**：API-Key 调用方可声明任意 tenantId，跨租户读写数据。JWT 路径（L64）从 token claim 取，OK。
- **置信度**：[KNOWN] MED

#### R-14 · 依赖版本含已知 CVE（需 NVD 核实）
- **证据**：`pom.xml:45-67`
  - Spring Boot 3.2.5（2024-05）→ 后续 3.2.x 修了多个 CVE（spring-framework path traversal CVE-2024-38816 等）
  - protobuf 3.25.1 → CVE-2024-7254（解析 DoS）修于 3.25.5
  - Spring AI 0.8.1 → 早期里程碑版本
  - jjwt 0.12.5 / docker-java（版本未在根 pom，需子模块查）
- **置信度**：[INFERRED] MED — 需 `dependency:tree` + NVD 比对方能定死具体 CVE 编号

---

## 4. 攻击链演练（红队视角，端到端）

### 攻击链 A · 未授权 RCE 链（最严重）
```
1. 攻击者发送 POST /api/v1/agents/{any}/invoke
   Header: X-API-Key: ak_test_valid_key_2026          [R-01, AuthFilter.java:29]
            X-Tenant-Id: victim-tenant                  [R-13]
2. 通过 AuthFilter，被识别为 userId=system
3. 请求转发至 agent-runtime → 调 ToolGateway.invoke
   ToolCallRequest.riskLevel = R1                       [R-02, ToolGatewayImpl.java:184]
   ToolCallRequest.toolId = 某已注册 SHELL 工具
4. ToolGateway 跳过 RiskClassifier.classify，assessment.requiresApproval=false
5. 跳过 ApprovalStore 校验，进入 ShellExecutor
6. ShellExecutor.execute: sh -c meta.getEndpoint()      [ShellExecutor.java:68]
   (若工具 endpoint 含恶意命令，或攻击者能注册工具)
7. Docker 沙箱执行，但 root 运行 + 无 cap-drop         [R-07]
8. 容器内提权 → 挂载宿主（若 mounts 可控）→ 宿主 RCE
9. 用 SA token 调 K8s API list secrets                  [R-04]
10. 拿到模型 API Key / DB 密码 / 其他服务 JWT 密钥 → 全集群沦陷
```
**前置条件**：能访问 gateway 8080；存在已注册的 SHELL 工具。门槛极低。

### 攻击链 B · JWT 伪造链
```
1. 攻击者从公开 repo 拿到 JWT secret                  [R-03]
2. 伪造 userId=admin-001 roles=["admin"] 的合法 JWT
3. 调 PermissionChecker → 命中 USER_ROLES["admin-001"]="admin" → 全权限  [R-06]
4. 创建/修改 Agent 定义 → 植入恶意 prompt 或工具绑定
5. 通过 agent-repo/knowledge 写入恶意记忆/知识 → 持久化 prompt injection
```

### 攻击链 C · gRPC 服务冒充链
```
1. 网络位置内攻击者直连 agent-tool-engine:9090 plaintext  [R-05]
2. 任意构造 ToolCallRequest，无需鉴权
3. 直接走 R-02 绕过审批
4. 同攻击链 A 步骤 5-10
```

---

## 5. 蓝队发现（稳定性 + 健壮性）

### 稳定性

#### S-01 · agent-gateway 限流是 stub · 🟠 MED
- **证据**：稳定性 agent 报告 `RateLimitFilter.java` 标 stub；但 `agent-gateway/application.yml:52-55` 有 `gateway.rate-limit` 配置（capacity=20）。需核实 filter 是否真接入 Redis 令牌桶还是本地内存。
- **影响**：高并发下网关不限流，后端被打穿。

#### S-02 · Resilience4j 已引入但配置不完整 · 🟠 MED
- **证据**：`agent-runtime/src/main/java/com/agent/runtime/config/Resilience4jConfig.java`（agent 报告）
- **缺口**：未见指数退避明确配置、未见 `Bulkhead`、未见 `TimeLimiter` 与 gRPC deadline 联动。

#### S-03 · RocketMQ 消费者无幂等 · 🟠 MED
- **证据**：`agent-task-orchestrator/src/main/java/com/agent/orchestrator/mq/SubtaskDoneConsumer.java`（agent 报告）
- **影响**：RocketMQ 至少一次投递 + 无幂等键 = 重复执行子任务，状态机错乱。

#### S-04 · 跨服务写无补偿事务 · 🟠 MED
- **证据**：`ToolGatewayImpl` 审计失败仅 log；`ReActLoopImpl.syncStepState` 裸 RPC。无 saga/TCC/本地消息表。
- **影响**：任务+记忆+工具审计三库可能不一致。

#### S-05 · JVM 堆配置超 pod 内存限制 · 🟠 MED
- **证据**：`infra/k8s/deployments/agent-tool-engine.yaml:59,68-69`
  ```yaml
  JAVA_OPTS: "-Xms2048m -Xmx4096m"
  resources.limits.memory: "4Gi"
  ```
- **影响**：堆 4G + Metaspace + 直接内存 + JVM 自身 > 4Gi → OOMKilled，pod 反复重启。

#### S-06 · gRPC 缺 deadline · 🟡 LOW-MED
- **证据**：稳定性 agent 报告，未在 ToolGatewayImpl / ModelGatewayClientImpl 见 `withDeadlineAfter`。
- **影响**：慢依赖拖垮整条调用链。

#### S-07 · 优雅停机部分到位 · 🟢 LOW
- **正向**：K8s `terminationGracePeriodSeconds: 60` + `preStop` hook（`agent-tool-engine.yaml:33,90-96`）。
- **缺口**：未见 `server.shutdown: graceful` 配置 + RocketMQ consumer rebalance 等待。

### 健壮性

#### S-08 · Schema 校验是字符串包含而非 JSON 解析 · 🟠 MED
- **证据**：`ToolGatewayImpl.java:292-308`
  ```java
  if (!inputJson.contains("\"" + field + "\"")) return false;
  ```
- **攻击/触发**：`{"nested":{"requiredField":1}}` 通过校验（字段名出现在嵌套层）；`{"a":"\"requiredField\""}` 也通过（出现在值里）。schema 校验形同虚设。

#### S-09 · checkpoint JSON 用 String.format 拼接 · 🟡 MED
- **证据**：`ReActLoopImpl.java:315-318`
  ```java
  String checkpointData = String.format(
      "{\"stepNumber\":%d,\"phase\":\"%s\",\"detail\":\"%s\",...}",
      stepNumber, phase, detail, ...);
  ```
- **影响**：`detail` 含 `"` 或 `\` 时 JSON 损坏，断点续跑数据不可用。

#### S-10 · gRPC 服务普遍缺入参校验 · 🟡 MED
- **证据**：健壮性 agent 报告 14 处 `*GrpcService.java` 直接用 request 字段无 null 检查。`GrpcExceptionAdvice` 兜底转 NPE 为通用错误，不崩溃但用户体验差。

#### S-11 · CostMeter 可能用 double 算钱 · 🟡 MED（需核实）
- **证据**：健壮性 agent 报告 `CostMeterImpl.java` 未见 BigDecimal。需人工读 `CostMeterImpl` 确认。
- **影响**：浮点精度漂移导致计费误差。

#### S-12 · 异常普遍被 catch + log 吞掉 · 🟡 LOW-MED
- **证据**：`ToolGatewayImpl` 审计吞异常（R-11）；健壮性 agent 报告多处 `GrpcExceptionAdvice` 通用捕获。

---

## 6. 功能完备性差距（PRD vs 实现）

| PRD 要求 | 状态 | 证据/差距 |
|---|---|---|
| R1/R2/R3 三级风险分级 | ✅ 完整 | `RiskClassifierImpl` 设计良好（never-downgrade + PII boost） |
| R3 高危审批 | ⚠️ 实现但可绕过 | `ApprovalStore` 存在，但 R-02 调用方可绕过 |
| Docker 沙箱 | ⚠️ 实现但加固不足 | R-07 |
| ABAC + RBAC | ❌ 仅 mock RBAC | R-06，无 ABAC 属性 |
| 多租户数据隔离 | ❌ 缺失 | R-13，租户 ID 客户端自填，无行级隔离 |
| 六层幻觉治理 | ⚠️ 骨架 | hallucination-governance 模块存在但仅 stub 服务 |
| 漂移四层管控 | ⚠️ 骨架 | drift-monitor 同上 |
| 三级质量校验 | ⚠️ 骨架 | agent-quality 仅 GrpcService，无真实校验引擎 |
| RocketMQ 消费者幂等 | ❌ 缺失 | S-03 |
| 跨服务分布式事务 | ❌ 缺失 | S-04 |
| Vault 密钥管理 | ❌ 仅占位 | `03-vault-config.yaml` 只是 ConfigMap 文档，`vault-seeds.sh` 未在仓库 |
| mTLS | ❌ 注释掉 | R-05 |
| 前端控制台 | ❌ 仅设计文档 | `docs/12-frontend/` 无代码 |
| project_memory Plan 05/06 = 0/N | ❌ 过时 | 实际已实现，文档需更新 |

---

## 7. 加固建议（按优先级）

### P0 · 立即修复（阻断攻击链 A/B/C）

1. **删除 `AuthFilter.java:29` 硬编码 API Key**。改为从 `JwtProperties` 或 Vault 读 API Key 集合，且 API-Key 路径必须查 PermissionChecker 而非直接信任 `X-Tenant-Id`。
2. **删除 `ToolGatewayImpl.java:184-191` 调用方自填 riskLevel 分支**。`request.getRiskLevel()` 字段应只用于日志，分级必须走 `riskClassifier.classify`。或保留但强制 `max(callerDeclared, classified)` 且 never-trust-caller。
3. **JWT 密钥移出 repo**。走 Vault / K8s Secret / 环境变量，repo 里只留占位符。轮换当前已泄露密钥。
4. **K8s RoleBinding 收紧**：每个 SA 只能读自己的 secret（按 label / 单独 RoleBinding），而非命名空间全局 list。
5. **gRPC 启 mTLS**：取消 `application.yml:31-35` 注释，签发服务证书。

### P1 · 一周内

6. K8s deployment 加 `securityContext`：`runAsNonRoot: true, runAsUser: 1000, fsGroup: 1000, seccompProfile: RuntimeDefault, capabilities: {drop: [ALL]}`
7. DockerSandboxBorrower 加 `withCapDrop(Capability.NO_CAPS).withUser("nonroot").withSecurityOpt("no-new-privileges").withPidsLimit(256).withReadonlyRootfs(true)`
8. actuator 端点收口：`management.endpoints.web.exposure.include=health,prometheus` + `management.endpoint.shutdown.enabled=false` 或独立 management port + 内网 only
9. CI 加 gitleaks + trivy + CodeQL 三件套
10. `PermissionCheckerImpl` 接真实 IAM（哪怕是 JWT claims 里的 roles），删除 3 用户 mock
11. RocketMQ 消费者加幂等键（消息 ID + Redis SETNX）
12. 修 `validateParams` 改用 Jackson 解析 + JSON Pointer 校验
13. 修 JVM 堆 vs pod memory：`-Xmx` 留 25% 给非堆，或 pod limit 提到 6Gi

### P2 · 一月内

14. 跨服务写引入本地消息表 / Outbox 模式
15. gRPC 全链路加 deadline 传播
16. CostMeter 改 BigDecimal（核实后）
17. checkpoint 序列化改 Jackson ObjectMapper
18. 依赖升级到 Spring Boot 3.2.12+ / protobuf 3.25.5+
19. 补 ABAC 属性引擎
20. project_memory.md 更新 Plan 05/06 真实进度

---

## 8. TDD 回归测试计划（writing-plans + tdd skill）

> 以下为关键漏洞的 red-green-refactor 回归测试骨架。每条漏洞应有一个失败测试（复现漏洞）→ 修复 → 测试通过。

### Task R-01-T1: AuthFilter 硬编码 API Key 失败测试

**Files:**
- Modify: `agent-gateway/src/test/java/com/agent/gateway/filter/AuthFilterTest.java`

- [ ] **Step 1: 写失败测试**
```java
@Test
void shouldRejectHardcodedTestApiKey() {
    when(whitelist.getPaths()).thenReturn(List.of());
    AuthFilter filter = new AuthFilter(jwtUtil, whitelist);
    MockHttpServletRequest req = new MockHttpServletRequest();
    req.addHeader("X-API-Key", "ak_test_valid_key_2026");
    req.setRequestURI("/api/v1/agents/invoke");
    MockHttpServletResponse res = new MockHttpServletResponse();

    filter.doFilterInternal(req, res, chain);

    assertThat(res.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
}
```
- [ ] **Step 2: 跑测试验证失败** — `mvn -pl agent-gateway test -Dtest=AuthFilterTest#shouldRejectHardcodedTestApiKey` → FAIL（当前返回 200）
- [ ] **Step 3: 修复** — 删除 `VALID_API_KEY` 常量，API Key 改走 `ApiKeyService.verify()` 注入
- [ ] **Step 4: 跑测试验证通过** → PASS
- [ ] **Step 5: Commit** — `fix(security): remove hardcoded API key backdoor from AuthFilter (R-01)`

### Task R-02-T1: ToolGateway 调用方 riskLevel 绕过失败测试

**Files:**
- Modify: `agent-tool-engine/src/test/java/com/agent/tool/engine/api/impl/ToolGatewayImplTest.java`

- [ ] **Step 1: 写失败测试**
```java
@Test
void shouldNotTrustCallerDeclaredRiskLevelBelowClassified() {
    ToolMeta r3Tool = metaWithSideEffect(SideEffect.DESTRUCTIVE); // classify → R3
    when(registry.findMeta("r3-tool")).thenReturn(r3Tool);
    when(rateLimiter.tryAcquire(any(), any())).thenReturn(true);

    ToolCallRequest req = ToolCallRequest.builder()
        .toolId("r3-tool")
        .riskLevel(ToolRiskLevel.R1)  // 调用方声明 R1 试图绕过
        .inputJson("{}")
        .tenantId("t1").build();

    assertThatThrownBy(() -> gateway.invoke(req))
        .isInstanceOf(ToolApprovalException.class)
        .hasMessageContaining("R3");
    // 当前代码：调用方声明 R1 → requiresApproval=false → 不抛 → 测试失败
}
```
- [ ] **Step 2-5**: 红 → 改 `ToolGatewayImpl.java:184` 删除 else 分支或强制 `max(caller, classified)` → 绿 → commit

### Task R-03-T1: JWT 密钥不在 repo 失败测试

**Files:**
- Create: `agent-gateway/src/test/java/com/agent/gateway/config/JwtSecretNotHardcodedTest.java`

- [ ] **Step 1: 写失败测试**
```java
@Test
void jwtSecretShouldNotBeTheKnownCommittedValue() {
    JwtProperties props = ctx.getBean(JwtProperties.class);
    assertThat(props.getSecret())
        .isNotEqualTo("agent-platform-jwt-secret-key-please-change-in-production-32bytes")
        .isNotBlank();
    // 当前 application.yml 是硬编码值 → 测试失败
}
```
- [ ] **Step 2-5**: 红 → 改 application.yml 用 `${JWT_SECRET:}` → 绿 → commit

### Task R-04-T1: K8s RBAC 不允许跨 SA 读 secrets

**Files:**
- Create: `infra/k8s/__tests__/rbac-per-service.test.yaml`（用 kubeconform / conftest OPA）

- [ ] **Step 1: 写 rego 策略**
```rego
deny[msg] {
  resource := "RoleBinding"
  rb := input.roleRef
  rb.name == "agent-platform-service-reader"
  subject := input.subjects[_]
  subject.kind == "Group"
  msg := "RoleBinding must not grant secret read to all SAs (R-04)"
}
```
- [ ] **Step 2-5**: 红 → 拆成每 SA 单独 RoleBinding，verbs 限 `get` 自己的 secret → 绿

### Task R-07-T1: Docker 沙箱加固测试

**Files:**
- Modify: `agent-tool-engine/src/test/java/com/agent/tool/engine/sandbox/DockerSandboxBorrowerTest.java`

- [ ] **Step 1: 写失败测试**
```java
@Test
void sandboxContainerShouldDropAllCapabilitiesAndRunAsNonRoot() {
    ArgumentCaptor<HostConfig> captor = ArgumentCaptor.forClass(HostConfig.class);
    when(dockerClient.createContainerCmd(any())).thenReturn(...);
    borrower.borrow(defaultSpec);
    verify(dockerClient.createContainerCmd(any())).withHostConfig(captor.capture());
    HostConfig hc = captor.getValue();
    assertThat(hc.getCapDrop()).contains(Capability.NO_CAPS); // 或 ALL
    // 当前实现未设 capDrop → 测试失败
}
```
- [ ] **Step 2-5**: 红 → `buildHostConfig` 加 `withCapDrop(...).withUser("nonroot")...` → 绿

### Task S-08-T1: validateParams 改 JSON 解析

**Files:**
- Modify: `agent-tool-engine/src/test/java/.../ToolGatewayImplTest.java`

- [ ] **Step 1: 写失败测试**
```java
@Test
void shouldRejectWhenRequiredFieldOnlyInNestedLayer() {
    ToolSchema schema = schemaWithRequired("requiredField");
    String input = "{\"nested\":{\"requiredField\":1}}"; // 字段在嵌套层
    assertThat(gateway.validateParams(schema, input)).isFalse();
    // 当前 contains("\"requiredField\"") → true → 测试失败
}
```
- [ ] **Step 2-5**: 红 → 改用 `ObjectMapper.readTree` + 顶层字段检查 → 绿

> 其余发现（R-05 mTLS / R-08 actuator / R-11 审计吞 / S-03 MQ 幂等 / S-05 JVM 堆）按同样模式各起一个 Task，此处不逐条展开。

---

## 9. 附录 A · 证据索引

| 编号 | 文件:行 | 一句话 |
|---|---|---|
| R-01 | `agent-gateway/src/main/java/com/agent/gateway/filter/AuthFilter.java:29` | 硬编码 API Key |
| R-02 | `agent-tool-engine/src/main/java/com/agent/tool/engine/api/impl/ToolGatewayImpl.java:184-191` | 调用方自填 riskLevel |
| R-03 | `agent-gateway/src/main/resources/application.yml:49` | JWT secret 入库 |
| R-04 | `infra/k8s/01-serviceaccounts.yaml:13-38` | 全 SA 读全 secrets |
| R-05 | `agent-gateway/src/main/resources/application.yml:31-42` | gRPC plaintext |
| R-06 | `agent-risk-control/src/main/java/com/agent/riskcontrol/api/impl/PermissionCheckerImpl.java:37-70` | mock RBAC |
| R-07 | `agent-tool-engine/src/main/java/com/agent/tool/engine/sandbox/DockerSandboxBorrower.java:347-368` | 沙箱缺加固 |
| R-08 | `infra/k8s/deployments/agent-tool-engine.yaml:34-100` | 无 securityContext + actuator |
| R-09 | `.github/workflows/ci.yml` | 无 SAST/secret/CVE 扫描 |
| R-10 | `infra/docker-compose/docker-compose.yml:34,58,83,134,151,179,197,246` | dev 默认密码 |
| R-11 | `agent-tool-engine/src/main/java/com/agent/tool/engine/api/impl/ToolGatewayImpl.java:318-348` | 审计吞异常 |
| R-12 | `agent-gateway/src/main/java/com/agent/gateway/filter/AuthFilter.java:92-114` | 内部头信任陷阱 |
| R-13 | `agent-gateway/src/main/java/com/agent/gateway/filter/AuthFilter.java:77` | 租户 ID 客户端自填 |
| S-05 | `infra/k8s/deployments/agent-tool-engine.yaml:59,68` | JVM 堆超 pod limit |
| S-08 | `agent-tool-engine/src/main/java/com/agent/tool/engine/api/impl/ToolGatewayImpl.java:303` | schema 校验是 contains |
| S-09 | `agent-runtime/src/main/java/com/agent/runtime/api/impl/ReActLoopImpl.java:315-318` | checkpoint JSON 拼接 |

## 10. 附录 B · Skill 使用说明

按用户要求使用了以下 skill，但** honesty over compliance**——不机械套用：

- **brainstorming**：用于 §3 的 STRIDE 威胁建模和 §4 攻击链的替代路径探索（"还有什么绕过方式"）
- **writing-plans**：用于 §8 TDD 回归测试计划的格式（Task / Files / red-green-commit 步骤）
- **tdd + test-driven-development**：§8 每条测试都是先红（复现漏洞）后绿（修复）
- **gsd**：本任务是一次性审计报告，非多阶段项目开发，未机械套用其 .planning/ 工作流；任务追踪用 TaskCreate 替代
- **using-superpowers**：勘探前先派 agent，agent 报告与人工读码冲突时以人工读码为准并修正

## 11. 附录 C · 置信度与方法局限

- 所有 `[KNOWN]` 标注 = 我已亲自 Read 该文件确认
- 所有 `[INFERRED]` = agent 报告 + 间接证据推断，未逐行核实
- **未做**：动态启动服务跑 fuzz、NVD 依赖 CVE 精确比对、前端代码审计（无代码）、供应链 hash 校验
- **agent 误判已剔除 4 处**（见 §0），但其他 agent 摘录类发现若未在附录 A 列出 file:line 的，置信度上限 [INFERRED] MED
