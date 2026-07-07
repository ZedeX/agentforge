[English](./ops-guide.md) | [中文](./ops-guide.zh-CN.md)

# AgentForge Operations & Maintenance Guide

> Version: v1.0 | Updated: 2026-07-08 | Target Audience: SRE / Operations Engineers / Platform Administrators

---

## Table of Contents

1. [Deployment Architecture](#1-deployment-architecture)
2. [Environment Preparation](#2-environment-preparation)
3. [Docker Compose Local Deployment](#3-docker-compose-local-deployment)
4. [K8s Production Deployment](#4-k8s-production-deployment)
5. [Middleware Deployment](#5-middleware-deployment)
6. [Configuration Management](#6-configuration-management)
7. [Observability Stack](#7-observability-stack)
8. [Security Hardening Checklist](#8-security-hardening-checklist)
9. [Database Initialization & Migration](#9-database-initialization--migration)
10. [Daily Operations](#10-daily-operations)
11. [Alert Rules & Handling](#11-alert-rules--handling)
12. [Troubleshooting Guide](#12-troubleshooting-guide)
13. [Performance Tuning](#13-performance-tuning)
14. [Backup & Recovery](#14-backup--recovery)

---

## 1. Deployment Architecture

### 1.1 Service Topology

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
     │       Core Engine Layer            │
     └────────────────────────────────────┘
              │            │            │
     ┌────────▼──┐  ┌──────▼──┐  ┌─────▼───┐
     │  MySQL    │  │  Redis  │  │ Milvus  │
     │  Cluster  │  │ Cluster │  │ Cluster │
     └───────────┘  └─────────┘  └─────────┘
```

### 1.2 Service Inventory

| Service | Port | Replicas | CPU/Mem | Dependencies |
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

## 2. Environment Preparation

### 2.1 Base Software

| Software | Version | Installation Method |
|---|---|---|
| JDK 17 | Eclipse Temurin 17+ | `sdk install java 17.0.9-tem` |
| Maven | 3.9+ | `sdk install maven 3.9.6` |
| Docker | 20+ | [Docker Desktop](https://www.docker.com/products/docker-desktop) |
| kubectl | 1.28+ | `curl -LO "https://dl.k8s.io/release/$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl"` |
| Helm | 3.14+ | `curl https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash` |

### 2.2 Middleware

| Middleware | Version | Purpose |
|---|---|---|
| MySQL | 8.0.36 | Relational data storage |
| Redis | 7.2-alpine | Cache + short-term memory |
| Milvus | 2.4 | Vector storage |
| Neo4j | 5.18 | Graph database |
| RocketMQ | 5.x | Message queue |
| Nacos | 2.3 | Service registry + configuration center |
| Vault | 1.15+ | Secrets management |
| ClickHouse | 24.x | Metrics storage |
| Elasticsearch | 8.13.4 | Full-text search |
| SkyWalking | 9.7 | Distributed tracing |
| Prometheus | 2.x | Metrics collection |
| Loki | v13 | Log aggregation |
| Grafana | 10.x | Visualization |

---

## 3. Docker Compose Local Deployment

### 3.1 Prepare Configuration

```bash
cd infra/docker-compose
cp .env.example .env
```

Edit the `.env` file to configure database passwords and middleware connection details:

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

### 3.2 Start Middleware

```bash
# Start all middleware services
docker compose -f docker-compose-services.yml up -d

# Check service status
docker compose -f docker-compose-services.yml ps
```

### 3.3 Start Application Services

```bash
# Build packages first
cd ../..
mvn clean package -DskipTests

# Start all applications
docker compose up -d

# View logs
docker compose logs -f agent-gateway
```

### 3.4 Verification

```bash
# Health check
curl http://localhost:8080/health

# Expected response
# {"code":"OK","message":"success","data":{"service":"agent-gateway","status":"UP"}}
```

---

## 4. K8s Production Deployment

### 4.1 Namespace & RBAC

```bash
# Create namespace
kubectl apply -f infra/k8s/00-namespace.yaml

# Create ServiceAccounts (one per service)
kubectl apply -f infra/k8s/01-serviceaccounts.yaml

# Configure Vault roles
kubectl apply -f infra/k8s/03-vault-config.yaml
```

### 4.2 Deploy Services

```bash
# Deploy all 12 services
kubectl apply -f infra/k8s/deployments/

# Deploy Services
kubectl apply -f infra/k8s/services/

# Configure Ingress
kubectl apply -f infra/k8s/services/ingress-gateway.yaml
```

Each Deployment is configured with:
- **startupProbe**: `failureThreshold=30, periodSeconds=10` (max 5 minutes startup wait)
- **readinessProbe**: HTTP `/actuator/health/readiness`
- **livenessProbe**: HTTP `/actuator/health/liveness`
- **preStop**: `sleep 10 && curl -X POST /actuator/shutdown || true` (graceful shutdown)
- **securityContext**: `runAsNonRoot: true, readOnlyRootFilesystem: true`

### 4.3 HPA Auto-Scaling

```bash
kubectl apply -f infra/k8s/hpa/
```

| Service | Min | Max | Metric | Target |
|---|---|---|---|---|
| agent-runtime | 2 | 10 | `agent_active_instances` | 50 |
| agent-gateway | 2 | 8 | `http_requests_qps` | 100 |
| agent-model-gateway | 2 | 6 | `grpc_requests_qps` | 200 |
| agent-memory | 2 | 6 | CPU 70% | — |
| agent-tool-engine | 2 | 6 | CPU 70% | — |
| agent-task-orchestrator | 2 | 4 | CPU 70% | — |

**Stabilization windows**: scaleUp 30s (fast response), scaleDown 300s (prevent flapping)

### 4.4 PDB Disruption Budget

```bash
kubectl apply -f infra/k8s/pdb/agent-runtime-pdb.yaml
```

### 4.5 OPA Security Policy Verification

```bash
# Verify RBAC compliance
cd infra/k8s/__tests__
opa test . --format=json
```

---

## 5. Middleware Deployment

### 5.1 MySQL

```bash
# Initialize DDL
cd infra/sql
./init-all.ps1 -DbType mysql -TenantId default

# Database inventory:
# 01-agent-session   (session, message tables)
# 02-agent-task      (task_instance, subtask tables)
# 03-agent-memory    (memory_record, outbox_message tables)
# 04-agent-tool      (tool_meta, approval_record, tool_call_audit_log tables)
# 05-agent-model     (model_usage_log table)
# 06-agent-repo      (agent_definition table)
# 07-agent-knowledge (knowledge_base, document, version tables)
# 08-agent-quality   (quality_report, badcase tables)
# 09-agent-risk      (audit_log, event_consume_log tables)
```

### 5.2 Redis

```bash
# Initialize Redis data
redis-cli < infra/sql/redis/01-init-data.redis
```

### 5.3 Milvus

```bash
# Initialize Collections
python infra/sql/milvus/01-init-collections.py
```

### 5.4 Neo4j

```bash
# Initialize constraints
cypher-shell < infra/sql/neo4j/01-init-constraints.cypher
cypher-shell < infra/sql/neo4j/02-init-relationships.cypher
```

---

## 6. Configuration Management

### 6.1 Nacos Configuration Center

Configuration files are located at `infra/nacos/`, imported via `import-nacos.ps1`:

```powershell
cd infra/nacos
./import-nacos.ps1 -NacosAddr nacos:8848
```

**Configuration structure**:

```
COMMON_GROUP/
  ├── datasource-common.yml    # MySQL datasource
  ├── redis-common.yml         # Redis connection
  ├── rocketmq-common.yml      # RocketMQ producer/consumer
  ├── observability-common.yml # SkyWalking/Prometheus
  └── governance-rules.yml     # Circuit breaker / rate limiting rules

SERVICE_GROUP/
  ├── memory-service-prod.yml       # agent-memory specific
  └── task-orchestrator-prod.yml    # task-orchestrator specific
```

**Bootstrap order**: `bootstrap-common.yml` → Nacos shared → service-level

### 6.2 Vault Secrets Management

All sensitive fields use Vault placeholders:

```yaml
# In application.yml
spring:
  datasource:
    password: ${vault:secret/data/agent-platform/common/datasource#password}
```

**Vault policy files**: one `.hcl` file per service under `infra/vault/vault-policies/`.

Initialize Vault:

```bash
cd infra/vault
./vault-seeds.sh
```

### 6.3 mTLS Configuration

gRPC mTLS is activated via the `application-mtls.yml` profile:

```bash
# Activate mTLS on startup
java -jar app.jar --spring.profiles.active=prod,mtls
```

Certificate paths are injected via environment variables:
- `GRPC_TLS_CERT_PATH=/etc/grpc/certs/tls.crt`
- `GRPC_TLS_KEY_PATH=/etc/grpc/certs/tls.key`
- `GRPC_TLS_CA_PATH=/etc/grpc/certs/ca.crt`

Dev environment certificate generation:

```bash
./infra/certs/generate-dev-certs.sh
```

---

## 7. Observability Stack

### 7.1 Component Inventory

| Component | Port | Purpose |
|---|---|---|
| SkyWalking OAP | 11800/12800 | Distributed tracing backend |
| SkyWalking UI | 8080 | Trace query interface |
| Prometheus | 9090 | Metrics collection & storage |
| Loki | 3100 | Log aggregation |
| Grafana | 3000 | Unified visualization |
| ClickHouse | 8123 | Long-term metrics storage |

### 7.2 Log Pipeline

```
Application (logback-spring.xml, JSON + MDC)
  → Promtail (K8s SD + JSON parse)
    → Loki (TSDB v13)
      → Grafana (query / dashboards)
```

**TraceId propagation**: `TraceIdHeaderInterceptor` binds `X-Trace-Id` → MDC → Logback JSON

### 7.3 Prometheus Alert Rules

Rule files are located at `infra/observability/prometheus/alerts/`:

| File | Alert Items |
|---|---|
| `service-availability.yaml` | Service availability < 99.9% |
| `drift-alerts.yaml` | Behavior drift detection |
| `hallucination-rate.yaml` | Hallucination rate exceeds threshold |

Outbox alert rules: `infra/prometheus/outbox-alerts.yml`

### 7.4 Grafana Dashboards

Import `infra/observability/grafana/dashboards/agent-platform-overview.json`

**Datasource configuration**:
- Prometheus: `infra/observability/grafana/datasources/prometheus.yml`
- Loki: `infra/observability/grafana/datasources/loki.yml`

---

## 8. Security Hardening Checklist

### 8.1 Authentication & Authorization

- [ ] JWT secret injected via environment variables, not hardcoded
- [ ] API Key bound to tenant, no global hardcoded key
- [ ] PermissionChecker uses role from JWT claims
- [ ] gRPC mTLS profile activated (required for production)

### 8.2 K8s Security

- [ ] Each ServiceAccount has independent RoleBinding
- [ ] Pod securityContext: `runAsNonRoot: true`
- [ ] Container securityContext: `readOnlyRootFilesystem: true`
- [ ] OPA conftest policy verification passed

### 8.3 Tool Engine Security

- [ ] riskLevel never downgraded (never-downgrade rule)
- [ ] R3 tools require manual approval
- [ ] Docker sandbox: `cap-drop ALL + user=nobody + no-new-privileges`
- [ ] Tool results sanitized for PII

### 8.4 CI/CD Security

- [ ] gitleaks secret scanning passed
- [ ] trivy container image scanning passed
- [ ] CodeQL SAST scanning passed
- [ ] All passwords injected via `${vault:...}`

### 8.5 Dependency Security

- [ ] Spring Boot >= 3.2.12 (CVE-2024-38816 fixed)
- [ ] Protobuf >= 3.25.5 (CVE-2024-7254 fixed)

---

## 9. Database Initialization & Migration

### 9.1 DDL Script Reference

| File | Database | Main Tables |
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
| 10-clickhouse-metrics.sql | — | Metrics storage tables |
| 11-seed-data.sql | — | Seed data (model catalog, default tools) |
| 12-outbox-message.sql | — | Outbox compensation framework tables |

### 9.2 Initialization Procedure

```powershell
cd infra/sql

# Full initialization (first-time deployment)
./init-all.ps1 -DbType mysql -TenantId default

# Single database initialization
mysql -u root -p agent_memory < mysql/03-agent-memory.sql
```

---

## 10. Daily Operations

### 10.1 Scaling

```bash
# Manual scale-up
kubectl scale deployment agent-runtime --replicas=5 -n agent-platform

# HPA auto-scaling (recommended)
kubectl get hpa -n agent-platform
```

### 10.2 Canary Deployment

```bash
# 1. Deploy new Canary version
kubectl apply -f canary-deployment.yaml

# 2. Observe Canary metrics
kubectl logs -f deployment/agent-runtime-canary -n agent-platform

# 3. Promote to full rollout after verification
kubectl set image deployment/agent-runtime app=agent-runtime:v2 -n agent-platform

# 4. Rollback (if issues occur)
kubectl rollout undo deployment/agent-runtime -n agent-platform
```

### 10.3 Graceful Shutdown

Each service is configured with a preStop hook + Spring Boot graceful shutdown:

```yaml
# K8s lifecycle
lifecycle:
  preStop:
    exec:
      command: ["sh", "-c", "sleep 10 && curl -X POST http://localhost:8080/actuator/shutdown || true"]
```

### 10.4 Log Queries

```bash
# kubectl logs
kubectl logs -f deployment/agent-runtime -n agent-platform --tail=100

# Loki query (LogQL)
{app="agent-runtime"} |= "ERROR" | json | line_format "{{.timestamp}} [{{.traceId}}] {{.message}}"
```

### 10.5 Configuration Hot-Reload

```bash
# Modify configuration via Nacos console
# Applications automatically detect Nacos configuration changes (@RefreshScope)

# Alternatively, refresh via API
curl -X POST http://nacos:8848/nacos/v1/cs/configs \
  -d "dataId=memory-service-prod.yml&group=SERVICE_GROUP&content=..."
```

---

## 11. Alert Rules & Handling

### 11.1 Prometheus Alerts

| Alert Name | Condition | Severity | Action |
|---|---|---|---|
| ServiceDown | up == 0 for 1m | CRITICAL | Check Pod status and logs |
| HighErrorRate | 5xx_rate > 1% for 5m | HIGH | Check application logs, inspect dependencies |
| SlowResponse | p99 > 5s for 10m | MEDIUM | Check database slow queries, JVM GC |
| MemoryPressure | memory_usage > 85% | MEDIUM | Scale up or optimize memory |
| DriftDetected | behavior_drift_score > 0.3 | MEDIUM | Check DriftService.GetBaseline |
| HallucinationRate | hallucination_rate > 5% | HIGH | Check model quality and knowledge base |
| OutboxStuck | outbox_pending > 100 for 5m | HIGH | Check OutboxRelay thread and consumers |
| CircuitBreakerOpen | circuit_open == 1 | HIGH | Check downstream service health |

### 11.2 Alert Handling Workflow

```
Alert triggered
  │
  ├── CRITICAL → Immediate response (within 15 minutes)
  │     ├── Confirm impact scope
  │     ├── Activate incident response
  │     └── Notify responsible personnel
  │
  ├── HIGH → Response within 30 minutes
  │     ├── Review related metrics and logs
  │     ├── Determine root cause
  │     └── Execute fix or degradation
  │
  └── MEDIUM → Handle during business hours
        ├── Log to ops ticket
        └── Schedule optimization plan
```

---

## 12. Troubleshooting Guide

### 12.1 Service Startup Failure

**Symptom**: Pod CrashLoopBackOff

**Troubleshooting steps**:

```bash
# 1. Check Pod status
kubectl describe pod <pod-name> -n agent-platform

# 2. Check container logs
kubectl logs <pod-name> -n agent-platform --previous

# 3. Common causes
#    - Database connection failure → Check DB_PASSWORD env var and MySQL connectivity
#    - Nacos registration failure → Check NACOS_SERVER_ADDR
#    - gRPC port conflict → Check port configuration
#    - OOM → Increase resources.limits.memory
```

### 12.2 Task Stuck in PENDING

**Symptom**: Task submitted but not executing for a long time

**Troubleshooting steps**:

```bash
# 1. Check task-orchestrator logs
kubectl logs -f deployment/agent-task-orchestrator -n agent-platform | grep "taskId"

# 2. Check RocketMQ consumers
#    Review consumer group online status and backlog

# 3. Check if Agent exists
#    Call RepoService.GetAgent to confirm

# 4. Check model-gateway availability
#    Call ModelService.ListModels to confirm models are online
```

### 12.3 Memory Service Timeout

**Symptom**: Recall RPC returns DEADLINE_EXCEEDED

**Troubleshooting steps**:

```bash
# 1. Check Milvus connection
kubectl exec -it <memory-pod> -- curl http://milvus:19530/healthz

# 2. Check vector Collection status
#    Confirm Collection is created and index is loaded

# 3. Check Redis connection
kubectl exec -it <memory-pod> -- redis-cli -h redis -a $REDIS_PASSWORD ping

# 4. Check slow query logs
#    Review Milvus query latency
```

### 12.4 Tool Invocation Failure

**Symptom**: ToolEngine.Invoke returns INTERNAL

**Troubleshooting steps**:

```bash
# 1. Confirm tool is registered
#    Call ToolGateway.ListTools

# 2. Check risk level and approval status
#    R3 tools require prior approval

# 3. Check Docker sandbox
docker ps | grep sandbox
docker logs <sandbox-container>

# 4. Check tool endpoint reachability
#    HTTP_API type requires endpoint to be accessible
```

### 12.5 gRPC Call UNAVAILABLE

**Symptom**: Inter-service gRPC calls return UNAVAILABLE

**Troubleshooting steps**:

```bash
# 1. Check if target service is online
kubectl get pods -n agent-platform -l app=<target-service>

# 2. Check K8s Service port
kubectl get svc <target-service> -n agent-platform

# 3. Check gRPC load balancing
#    net.devh gRPC client uses Spring Cloud registry for discovery

# 4. Check mTLS certificates
#    If mTLS is enabled, confirm certificates have not expired
```

### 12.6 Circuit Breaker Open

**Symptom**: CircuitOpenException, requests rejected

**Troubleshooting steps**:

```bash
# 1. Identify which circuit breaker is open
#    Search logs for "Circuit breaker [xxx] is OPEN"

# 2. Check downstream service health
#    Confirm model-gateway / tool-engine are healthy

# 3. Wait for circuit breaker half-open
#    Default: enters HALF_OPEN state after 60s

# 4. Manually close circuit breaker (emergency only)
#    Via Actuator endpoint: POST /actuator/circuitbreakers/{name}/close
```

---

## 13. Performance Tuning

### 13.1 JVM Parameters

```bash
# Recommended production JVM parameters
JAVA_OPTS="-Xms2g -Xmx2g \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -XX:+HeapDumpOnOutOfMemoryError \
  -XX:HeapDumpPath=/tmp/heapdump.hprof \
  -XX:+PrintGCDetails \
  -XX:+PrintGCDateStamps \
  -Xloggc:/tmp/gc.log"
```

### 13.2 Connection Pool

```yaml
# application.yml - MySQL connection pool
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000

# Redis connection pool
spring:
  data:
    redis:
      lettuce:
        pool:
          max-active: 16
          max-idle: 8
          min-idle: 4
```

### 13.3 gRPC Tuning

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

### 13.4 Resilience4j Configuration

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

### 13.5 Milvus Tuning

```yaml
# Vector index parameters
milvus:
  index:
    type: HNSW
    params:
      M: 16          # Number of connections (higher = more accurate, more memory)
      efConstruction: 256  # Build-time search width
  search:
    ef: 128          # Query-time search width (higher = more accurate, slower)
```

---

## 14. Backup & Recovery

### 14.1 MySQL Backup

```bash
# Full backup (daily)
mysqldump -u root -p --all-databases --single-transaction \
  --quick --lock-tables=false > backup_$(date +%Y%m%d).sql

# Single database backup
mysqldump -u root -p agent_memory > backup_memory_$(date +%Y%m%d).sql
```

### 14.2 Redis Backup

```bash
# RDB snapshot
redis-cli -h redis -a $REDIS_PASSWORD BGSAVE

# Copy dump.rdb
kubectl cp agent-platform/redis-0:/data/dump.rdb ./backup/redis_$(date +%Y%m%d).rdb
```

### 14.3 Milvus Backup

```bash
# Using milvus-backup tool
milvus-backup create -n backup_$(date +%Y%m%d) --collection memory_vectors
```

### 14.4 Recovery

```bash
# MySQL recovery
mysql -u root -p < backup_20260708.sql

# Redis recovery
# Place dump.rdb in Redis data directory and restart

# Milvus recovery
milvus-backup restore -n backup_20260708
```

### 14.5 Nacos Configuration Backup

```bash
# Export configuration
curl "http://nacos:8848/nacos/v1/cs/configs?export=true&group=COMMON_GROUP&tenant=" \
  -o nacos_common_backup.zip

curl "http://nacos:8848/nacos/v1/cs/configs?export=true&group=SERVICE_GROUP&tenant=" \
  -o nacos_service_backup.zip
```

---

> 📖 For the user guide, see [user-guide.md](./user-guide.md) | For design documentation, see [README.md](./README.md)
