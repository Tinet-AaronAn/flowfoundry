# FlowFoundry + Temporal 流程编排平台

FlowFoundry 是当前自研项目代号。本仓库提供流程编排平台（`flowfoundry-core`）与业务场景聚合（`flowfoundry-app`），当前内置 **AI 催收策略** 可独立启动场景作为示例。

**Git 仓库：** `git@github.com:Tinet-AaronAn/Flow-Foundary.git`（remote 名 `origin`，基线分支 `main`）。详见 [AGENTS.md](AGENTS.md) 与 [docs/local-development.md](docs/local-development.md)。

## 仓库结构

```
flowfoundry/
├── pom.xml
├── flowfoundry-core/                         # 平台：建模器 + API + 解释器（业务无关）
├── flowfoundry-app/                          # 业务场景聚合（packaging=pom）
│   └── modules/
│       └── ai-collection-strategy/           # 可独立启动的 AI 催收策略场景
├── deploy/                                   # Docker / Helm / K8s
├── scripts/                                  # local-dev、redeploy-worker 等
└── e2e/                                      # Playwright 建模器测试
```

各目录详细说明见 [docs/project-structure.md](docs/project-structure.md)。**服务地址**见 [docs/service-urls.md](docs/service-urls.md)。

## 分层原则

| 层级 | 模块 | 职责 |
|------|------|------|
| 平台 | `flowfoundry-core` | 建模器、Workflow/Flow API、编译器、解释器、扩展点 |
| 聚合 | `flowfoundry-app` | Maven 聚合各 `modules/*` 场景（无代码） |
| 业务 | `flowfoundry-app/modules/*` | 可独立启动的场景（`main`、Activity、注册表） |

**新增业务只改 `flowfoundry-app/modules/`，不改 `flowfoundry-core`。**

## 快速开始（本地开发）

> **日常建模器联调**：改完 `flowfoundry-core/`、`flowfoundry-app/` 或 `flowfoundry-app/modules/` 后执行 `./scripts/redeploy-worker.sh`，再刷新 **http://127.0.0.1:8081/**。  
> 详见 [docs/local-development.md](docs/local-development.md) 与 [AGENTS.md](AGENTS.md)。

```bash
chmod +x scripts/local-dev.sh scripts/check-progress.sh scripts/redeploy-worker.sh

# 首次：启动 Redis + Temporal + Worker
./scripts/local-dev.sh up
./scripts/check-progress.sh

# 每次改代码后：重新打包并重启 8081
./scripts/redeploy-worker.sh
```

| 用途 | 地址 |
|------|------|
| **建模器 / 联调** | http://127.0.0.1:8081/ |
| Worker 健康检查 | http://127.0.0.1:8081/actuator/health |
| Temporal UI（Docker 栈） | http://127.0.0.1:8080/ |
| Temporal UI（`temporal server start-dev`） | http://127.0.0.1:8233 |

前端代码在 `flowfoundry-core/src/main/resources/static/`，打进 JAR 后由场景应用提供，**没有热更新**。

### 手动启动（默认场景 ai-collection-strategy）

```bash
mvn -pl flowfoundry-app/modules/ai-collection-strategy -am -DskipTests package
java -jar flowfoundry-app/modules/ai-collection-strategy/target/ai-collection-strategy-demo-1.0.0-SNAPSHOT.jar \
  --server.port=8081 \
  --platform.activity-registry.path="file:$(pwd)/flowfoundry-app/modules/ai-collection-strategy/config/activities-registry.yaml" \
  --temporal.host=127.0.0.1:7233 \
  --temporal.namespace=call-campaign \
  --temporal.task-queue=ai-collection-strategy \
  --spring.data.redis.host=127.0.0.1 \
  --spring.data.redis.port=6379
```

### 平台独立启动（无业务 Activity）

```bash
mvn -pl flowfoundry-core -am -DskipTests package
java -jar flowfoundry-core/target/flowfoundry-core-*-exec.jar --server.port=8081
```

可访问建模器与 API；执行业务 Service Task 需启动带业务模块的场景 JAR（如 `ai-collection-strategy`）。

### Docker 全栈

```bash
chmod +x scripts/docker-stack.sh scripts/runtime-test.sh scripts/smoke-test.sh
./scripts/docker-stack.sh up
./scripts/check-progress.sh
./scripts/runtime-test.sh
```

## 业务场景示例（AI 催收）

Activity 注册表：`flowfoundry-app/modules/ai-collection-strategy/config/activities-registry.yaml`  
Task Queue：`ai-collection-strategy`

| BPMN 节点 | activityType | Java 方法 |
|-----------|--------------|-----------|
| 下发号码批次 | import-numbers | `importNumbers` |
| 规则筛选并分批 | filter-and-split-batches | `filterAndSplitBatches` |
| 汇报批次结果 | notify-owner-report | `notifyOwnerReport` |
| 准备本轮外呼 | prepare-call-round | `prepareCallRound` |
| 执行本轮外呼 | execute-call-round | `executeCallRound` |
| 等待外呼结束 | wait-round-completion | `waitRoundCompletion` |
| 启动录音 AI 打标 | start-ai-tagging | `startAiTagging` |
| 等待打标完成 | wait-tagging-completion | `waitTaggingCompletion` |
| 筛选下一轮名单 | filter-next-round | `filterNextRound` |
| 结束活动 | finalize-campaign | `finalizeCampaign` |

## 测试

```bash
mvn test                                    # Java 单元测试
npm run test:e2e                            # Playwright 建模器 E2E（:4173 静态服务）
```

## 参考

- [docs/project-structure.md](docs/project-structure.md)
- [docs/local-development.md](docs/local-development.md)
- [Temporal Activity 幂等](https://temporal.io/blog/idempotency-and-durable-execution)
