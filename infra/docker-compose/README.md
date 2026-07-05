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

| 服务 | URL | 默认账号 |
|---|---|---|
| Nacos Console | http://localhost:8848/nacos | nacos / nacos |
| Grafana | http://localhost:3000 | admin / agentplatform |
| Prometheus | http://localhost:9090 | - |
| SkyWalking UI | http://localhost:18080 | - |
| MinIO Console | http://localhost:9001 | agentplatform / agentplatform |
| Neo4j Browser | http://localhost:7474 | neo4j / agentplatform |
| Elasticsearch | http://localhost:9200 | - |
| Vault | http://localhost:8200 | token: agentplatform |
| agent-gateway | http://localhost:8080 | - |

> NOTE: SkyWalking UI mapped to 18080 to avoid clashing with agent-gateway (8080).

## 停止与清理
```powershell
docker-compose -f docker-compose-services.yml down
docker-compose -f docker-compose.yml down
# 清理卷（谨慎，会删除所有数据）
# docker volume rm agent-platform_mysql-data agent-platform_redis-data
```

## 中间件清单（14 服务）

| # | 服务 | 端口 | 用途 |
|---|---|---|---|
| 1 | mysql | 3306 | 主数据库 |
| 2 | redis | 6379 | 缓存 + 分布式锁 |
| 3 | milvus-etcd | - | Milvus 元数据 |
| 4 | milvus-minio | - | Milvus 对象存储 |
| 5 | milvus | 19530 | 向量库 |
| 6 | rocketmq-namesrv | 9876 | MQ NameServer |
| 7 | rocketmq-broker | 10911 | MQ Broker |
| 8 | elasticsearch | 9200 | 搜索 + SkyWalking 存储 |
| 9 | neo4j | 7687 | 图数据库 |
| 10 | minio | 9000 | 应用对象存储 |
| 11 | nacos | 8848 | 配置 + 注册中心 |
| 12 | vault | 8200 | 密钥管理 |
| 13 | skywalking-oap | 11800 | 链路追踪后端 |
| 14 | skywalking-ui | 18080 | 链路追踪 UI |
| 15 | prometheus | 9090 | 指标采集 |
| 16 | grafana | 3000 | 监控面板 |
| 17 | loki | 3100 | 日志聚合 |
| 18 | promtail | - | 日志采集 |
