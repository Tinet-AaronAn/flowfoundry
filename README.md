# FlowFoundry + Temporal 流程编排平台 — 可开工交付物

FlowFoundry 是当前自研项目代号。本仓库参考 QuantumBPM 的 BPMN/DMN 建模设计，使用 Temporal（Durable Execution）作为可靠执行内核，提供多轮呼叫任务管理示例、部署配置、Activity 注册表、Redis 幂等骨架与完整 Worker 实现。

## 仓库结构

```
flowfoundry-temporal-platform/
├── deploy/
│   ├── helm/
│   │   ├── temporal/values-production.yaml      # Temporal 生产 Helm values
│   │   └── flowfoundry/values-production.yaml    # FlowFoundry 平台生产 values 草案
│   ├── k8s/
│   │   ├── namespaces.yaml
│   │   ├── secrets.example.yaml
│   │   └── call-campaign-worker.yaml
│   └── scripts/install-production.sh
├── bpmn/
│   └── multi-round-call-campaign.bpmn20.xml     # 多轮呼叫 BPMN 流程
├── registry/
│   └── activities-registry.yaml               # Activity 积木注册表
├── scripts/
│   ├── local-dev.sh                           # 首次启动 Redis + Temporal + Worker
│   ├── redeploy-worker.sh                     # 改代码后重新打包并重启 :8081（日常必用）
│   ├── check-progress.sh                      # 健康检查
│   └── docker-stack.sh                        # Docker 全栈（集成 / 冒烟）
└── worker/                                      # Java Activity Worker + 建模器静态资源
    ├── pom.xml
    └── src/main/resources/static/             # 建模器 HTML/JS/CSS（打进 JAR）
```

## 快速开始（本地开发）

> **日常建模器联调**：改完 `worker/` 代码后执行 `./scripts/redeploy-worker.sh`，再刷新 **http://127.0.0.1:8081/**。  
> 详见 [docs/local-development.md](docs/local-development.md) 与 [AGENTS.md](AGENTS.md)。

### 日常开发（建模器 + Worker，推荐）

```bash
chmod +x scripts/local-dev.sh scripts/check-progress.sh scripts/redeploy-worker.sh

# 首次：启动 Redis + Temporal + Worker
./scripts/local-dev.sh up
./scripts/check-progress.sh

# 每次改 worker/ 代码（含 static 前端）后：重新打包并重启 8081
./scripts/redeploy-worker.sh
# 或
./scripts/local-dev.sh redeploy
```

| 用途 | 地址 |
|------|------|
| **建模器 / 联调** | http://127.0.0.1:8081/ |
| Worker 健康检查 | http://127.0.0.1:8081/actuator/health |

前端代码在 `worker/src/main/resources/static/`，打进 JAR 后由 Worker 提供，**没有热更新**；仅保存文件不会更新 8081 页面。

停止本地栈：`./scripts/local-dev.sh down`

### 一键启动（无需 Docker）

```bash
chmod +x scripts/local-dev.sh scripts/check-progress.sh
./scripts/local-dev.sh up      # 启动 Redis + Temporal + Worker
./scripts/check-progress.sh    # 验证全部就绪（ALL_GREEN）
./scripts/local-dev.sh down    # 停止
```

启动后访问：
- 建模器：http://127.0.0.1:8081/
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

### 4. QuantumBPM 本地参考评估（可选）

```bash
docker run --rm -p 9060:9060 \
  -v "$PWD/flowfoundry-data:/var/lib/devserver" \
  hub.quantumbpm.com/quantumbpm-public/devserver:latest
```

打开 http://localhost:9060 ，导入 `bpmn/multi-round-call-campaign.bpmn20.xml`。这里的 DevServer 是 QuantumBPM 参考产品界面，不是当前 FlowFoundry 项目自身实现。

### Docker 全栈（OrbStack，集成 / 冒烟验证）

日常前端迭代请用上方 **`redeploy-worker.sh`**。Docker 全栈用于 Postgres + Temporal + Redis + Worker 一体化冒烟，**不是**每次改静态页面的默认路径。

[OrbStack](https://orbstack.dev/) 提供轻量 Docker 运行时（macOS）。安装后启动 OrbStack，再运行：

```bash
# 安装 OrbStack（任选其一）
brew install --cask orbstack          # 或从 https://orbstack.dev 下载 DMG

# 启动全栈：Postgres + Temporal + Redis + Worker + Temporal UI
chmod +x scripts/docker-stack.sh scripts/smoke-test.sh
./scripts/docker-stack.sh up

# 可选：启动 QuantumBPM DevServer（参考产品界面）
docker compose -f deploy/docker-compose.local.yml --profile full up -d flowfoundry-devserver

# 验证 + 端到端冒烟
./scripts/check-progress.sh
./scripts/smoke-test.sh
```

Docker 模式访问地址：
- Temporal UI: http://localhost:8080
- 建模器 / Worker: http://localhost:8081/
- Worker 健康检查: http://localhost:8081/actuator/health
- QuantumBPM DevServer: http://localhost:9060（`--profile full` 时，参考产品界面）
- Temporal gRPC: localhost:7233

本地开发环境变量（`deploy/docker-compose.local.yml`）：
- `DEV_MAX_ROUNDS=1` — 冒烟测试只跑一轮，避免 30 分钟定时器
- `DEV_ROUND_INTERVAL_MINUTES=1` — 多轮间隔缩短为 1 分钟

## 生产部署

```bash
# 1. 编辑密钥
cp deploy/k8s/secrets.example.yaml deploy/k8s/secrets.yaml
# 填写 DB / Redis / OIDC 等生产配置

# 2. 一键安装（Temporal + Worker）
chmod +x deploy/scripts/install-production.sh
SKIP_SECRETS=1 ./deploy/scripts/install-production.sh

# 3. FlowFoundry 平台服务（未来生产部署形态）
helm upgrade --install flowfoundry <flowfoundry-chart> \
  -n bpm -f deploy/helm/flowfoundry/values-production.yaml
```

## BPMN → Activity 映射

| BPMN 节点 | flowfoundry:activityType | Java 方法 |
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
- **人工**：`remainingContacts > 100` 时进入 Human Task（managed，等待主管复核 Signal）
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
- [ ] FlowFoundry 平台 OIDC 与生产配置已确认
- [ ] Temporal namespace `call-campaign` 已创建
- [ ] Redis 集群 AOF 持久化已开启
- [ ] Worker HPA 与 Schedule-to-Start 告警已配置
- [ ] `DialerService` 替换为真实外呼平台 Adapter
- [ ] BPMN 已在 QuantumBPM 参考界面或 FlowFoundry 自研画布中完成建模验证

## 参考

- [Temporal Helm Charts](https://github.com/temporalio/helm-charts)
- QuantumBPM / BPMN 建模产品文档（作为交互和建模参考）
- [Temporal Activity 幂等](https://temporal.io/blog/idempotency-and-durable-execution)
