# QuantumBPM + Temporal 流程编排平台 — 可开工交付物

基于 QuantumBPM（BPMN/DMN 可视化）+ Temporal（Durable Execution）的多轮呼叫任务管理示例，包含生产级 Helm 配置、Activity 注册表、Redis 幂等骨架与完整 Worker 实现。

## 仓库结构

```
quantumbpm-temporal-platform/
├── deploy/
│   ├── helm/
│   │   ├── temporal/values-production.yaml      # Temporal 生产 Helm values
│   │   └── quantumbpm/values-production.yaml    # QuantumBPM Enterprise values
│   ├── k8s/
│   │   ├── namespaces.yaml
│   │   ├── secrets.example.yaml
│   │   └── call-campaign-worker.yaml
│   └── scripts/install-production.sh
├── bpmn/
│   └── multi-round-call-campaign.bpmn20.xml     # 多轮呼叫 BPMN 流程
├── registry/
│   └── activities-registry.yaml               # Activity 积木注册表
└── worker/                                      # Java Activity Worker
    ├── pom.xml
    └── src/main/java/com/example/platform/
        ├── registry/                            # 注册表加载
        ├── idempotency/                         # Redis SET NX 幂等
        ├── callcampaign/                        # 呼叫活动 Activities
        └── worker/TemporalWorkerBootstrap.java
```

## 快速开始（本地开发）

### 一键启动（推荐，无需 Docker）

```bash
chmod +x scripts/local-dev.sh scripts/check-progress.sh
./scripts/local-dev.sh up      # 启动 Redis + Temporal + Worker
./scripts/check-progress.sh    # 验证全部就绪（ALL_GREEN）
./scripts/local-dev.sh down    # 停止
```

启动后访问：
- Temporal UI: http://127.0.0.1:8233
- Worker 健康检查: http://127.0.0.1:8081/actuator/health

### 手动启动

```bash
# Temporal dev server
temporal server start-dev

# Redis
docker run -d --name redis -p 6379:6379 redis:7-alpine
```

### 2. 创建 Temporal Namespace

```bash
temporal operator namespace create call-campaign
```

### 3. 启动 Activity Worker

```bash
cd worker
mvn spring-boot:run
```

### 4. QuantumBPM 本地评估（可选）

```bash
docker run --rm -p 9060:9060 \
  -v "$PWD/qbpm-data:/var/lib/devserver" \
  hub.quantumbpm.com/quantumbpm-public/devserver:latest
```

打开 http://localhost:9060 ，导入 `bpmn/multi-round-call-campaign.bpmn20.xml`。

### Docker 全栈（OrbStack，推荐用于完整验证）

[OrbStack](https://orbstack.dev/) 提供轻量 Docker 运行时（macOS）。安装后启动 OrbStack，再运行：

```bash
# 安装 OrbStack（任选其一）
brew install --cask orbstack          # 或从 https://orbstack.dev 下载 DMG

# 启动全栈：Postgres + Temporal + Redis + Worker + Temporal UI
chmod +x scripts/docker-stack.sh scripts/smoke-test.sh
./scripts/docker-stack.sh up

# 可选：启动 QuantumBPM DevServer
docker compose -f deploy/docker-compose.local.yml --profile full up -d quantumbpm-devserver

# 验证 + 端到端冒烟
./scripts/check-progress.sh
./scripts/smoke-test.sh
```

Docker 模式访问地址：
- Temporal UI: http://localhost:8080
- Worker 健康检查: http://localhost:8081/actuator/health
- QuantumBPM: http://localhost:9060（`--profile full` 时）
- Temporal gRPC: localhost:7233

本地开发环境变量（`deploy/docker-compose.local.yml`）：
- `DEV_MAX_ROUNDS=1` — 冒烟测试只跑一轮，避免 30 分钟定时器
- `DEV_ROUND_INTERVAL_MINUTES=1` — 多轮间隔缩短为 1 分钟

## 生产部署

```bash
# 1. 编辑密钥
cp deploy/k8s/secrets.example.yaml deploy/k8s/secrets.yaml
# 填写 DB / Redis / OIDC / QuantumBPM License

# 2. 一键安装（Temporal + Worker）
chmod +x deploy/scripts/install-production.sh
SKIP_SECRETS=1 ./deploy/scripts/install-production.sh

# 3. QuantumBPM Enterprise（需厂商 License）
helm upgrade --install quantumbpm <enterprise-chart> \
  -n bpm -f deploy/helm/quantumbpm/values-production.yaml
```

## BPMN → Activity 映射

| BPMN 节点 | qbpm:activityType | Java 方法 |
|-----------|-------------------|-----------|
| 加载呼叫活动 | load-campaign | `loadCampaign` |
| 准备本轮呼叫 | prepare-call-round | `prepareCallRound` |
| 执行本轮外呼 | execute-call-round | `executeCallRound` |
| 等待本轮完成 | wait-round-completion | `waitRoundCompletion` |
| 汇总本轮结果 | aggregate-round-results | `aggregateRoundResults` |
| 评估是否进入下一轮 | evaluate-next-round | `evaluateNextRound` |
| 主管复核 | supervisor-review | `supervisorReview` |
| 结束活动 | finalize-campaign | `finalizeCampaign` |

Task Queue: `call-campaign`（与 `registry/activities-registry.yaml` 一致）

## 多轮呼叫流程说明

```
提交活动 → 加载配置 → [循环] 准备批次 → 外呼 → 等待完成 → 汇总
    → (可选) 主管复核 → 评估下一轮 → 定时等待 → round++ → 继续 or 结束
```

- **并行**：单轮内外呼批次一次提交（可扩展为多批次并行）
- **多轮**：`evaluate-next-round` + 定时器 + `roundNumber++` 回路
- **人工**：`remainingContacts > 100` 时进入 User Task
- **幂等**：每个 Activity 经 `IdempotentActivityExecutor` + Redis `SET NX`

## 幂等设计要点

```java
// 统一入口 — IdempotentActivityExecutor
idempotentExecutor.execute("execute-call-round", Map.of(
    "campaignId", campaignId,
    "roundNumber", roundNumber
), () -> { /* 业务逻辑 */ });
```

- `CLAIMED` → 执行并标记 `COMPLETED`
- `ALREADY_DONE` → 跳过（生产环境建议扩展 Redis 结果缓存）
- `IN_PROGRESS` → 抛 `ConcurrentExecutionException`，Temporal 退避重试

## 生产 Checklist

- [ ] `numHistoryShards: 2048` 已确认（创建后不可改）
- [ ] QuantumBPM Enterprise License + OIDC 已配置
- [ ] Temporal namespace `call-campaign` 已创建
- [ ] Redis 集群 AOF 持久化已开启
- [ ] Worker HPA 与 Schedule-to-Start 告警已配置
- [ ] `DialerService` 替换为真实外呼平台 Adapter
- [ ] BPMN 已在 QuantumBPM 发布并通过仿真

## 参考

- [Temporal Helm Charts](https://github.com/temporalio/helm-charts)
- [QuantumBPM Enterprise Docs](https://quantumbpm.com/docs/enterprise)
- [Temporal Activity 幂等](https://temporal.io/blog/idempotency-and-durable-execution)
