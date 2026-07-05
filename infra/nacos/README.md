# Agent Platform Nacos 配置中心

## 目录结构

```
infra/nacos/
├── bootstrap-common.yml      # 所有服务的 bootstrap.yml 模板
├── shared/                   # COMMON_GROUP 共享配置（5 个）
│   ├── datasource-common.yml       # HikariCP + MySQL 连接池 + Vault 凭证
│   ├── redis-common.yml            # Redis Cluster + Lettuce + Redisson
│   ├── rocketmq-common.yml         # NameServer + Producer + Topic 注册表
│   ├── observability-common.yml    # Actuator + Prometheus + SkyWalking + 日志
│   └── governance-rules.yml        # 漂移/幻觉/成本/限流/采样阈值
├── services/                 # SERVICE_GROUP 服务级配置（dev/prod 双 profile）
│   ├── task-orchestrator-prod.yml  # ShardingSphere 16 库 + gRPC + RocketMQ 事务
│   └── memory-service-prod.yml     # Milvus + embedding + XXL-Job 蒸馏调度
├── import-nacos.ps1          # 批量导入脚本
└── README.md
```

## 命名空间与 Group 规则

| 项 | 规则 | 示例 |
|---|---|---|
| Namespace | `agent-platform-{profile}` | agent-platform-dev / agent-platform-prod |
| Group | COMMON_GROUP（共享）/ SERVICE_GROUP（服务级） | - |
| DataId | 文件名（含 .yml 后缀） | datasource-common.yml |
| Profile 命名 | `{service}-{profile}.yml` | task-orchestrator-prod.yml |

## 与 Vault 的协作

所有敏感字段通过 `${vault:secret/data/agent-platform/...}` 占位符引用，禁止明文密钥：

```yaml
mysql:
  username: ${vault:secret/data/agent-platform/common/mysql:username}
  password: ${vault:secret/data/agent-platform/common/mysql:password}
```

Vault 后端：`agent-platform`（KV v2），路径规划：
- `secret/agent-platform/common/*` — 共享凭证（mysql/redis/milvus/neo4j/minio）
- `secret/agent-platform/model/*` — 模型 API key（openai/anthropic/qwen/deepseek）
- `secret/agent-platform/service/*` — 服务级密钥（jwt signing key）

## 刷新策略

- `shared-configs` 的 `refresh: true` — 共享配置变更自动热更新
- 服务级配置默认支持热更新（Nacos Config 默认行为）
- Bean 需要 `@RefreshScope` 注解才能感知配置变化

## 使用步骤

### 1. 启动 Nacos（本地开发）
```powershell
cd e:\git\Agent-Platform-Prototype\infra\docker-compose
docker-compose -f docker-compose.yml up -d nacos
```

### 2. 导入配置
```powershell
cd e:\git\Agent-Platform-Prototype\infra\nacos
.\import-nacos.ps1 -Environment dev -NacosPassword nacos
```

### 3. 验证
```powershell
# 通过 Nacos Console: http://localhost:8848/nacos (nacos/nacos)
# 或通过 API:
$resp = Invoke-RestMethod "http://localhost:8848/nacos/v1/cs/configs?dataId=datasource-common.yml&group=COMMON_GROUP&tenant=agent-platform-dev"
$resp | Should -Not -BeNullOrEmpty
```

## 扩展服务级配置

新增服务的 prod 配置：
1. 在 `services/` 下创建 `{service}-prod.yml`
2. 引用 5 个 shared-configs（已在 bootstrap-common.yml 声明）
3. 服务特定配置（端口 / gRPC / 业务参数）
4. 敏感字段用 `${vault:...}` 占位
5. 运行 `import-nacos.ps1 -Environment prod` 导入
