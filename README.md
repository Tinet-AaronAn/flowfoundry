# FlowFoundry + Temporal 流程编排平台

FlowFoundry 是当前自研项目代号。本仓库提供流程编排平台（`flowfoundry-core`）、SDK（`flowfoundry-sdk`）与官方示例（`examples/ai-collection-strategy`）。业务 Activity 可独立 Worker App（`:8082`）交付，也可打成插件包由平台 K8s runner 托管。

**Git 仓库：** `git@github.com:Tinet-AaronAn/flowfoundry.git`（remote 名 `origin`，基线分支 `main`）。

## 文档

| 场景 | 文档 |
|------|------|
| **本地调试**（改代码、建模器联调） | [docs/local-development.md](docs/local-development.md) |
| **插件开发**（打包、上传、K8s runner） | [docs/plugin-development-guide.md](docs/plugin-development-guide.md) |
| **生产部署**（K8s / Helm，不涉及本地构建镜像） | [docs/production-deployment.md](docs/production-deployment.md) |
| 服务地址与路径 | [docs/service-urls.md](docs/service-urls.md) |
| 仓库结构 | [docs/project-structure.md](docs/project-structure.md) |
| 插件运行时设计（评审 / 架构） | [docs/plugin-runtime-design.md](docs/plugin-runtime-design.md) |
| 协作者 / Agent 约定 | [AGENTS.md](AGENTS.md) |

## 仓库结构

```
flowfoundry/
├── pom.xml
├── flowfoundry-sdk/                          # SDK + plugin-runner
│   ├── flowfoundry-sdk/                      # Worker 运行时
│   ├── flowfoundry-sdk-client/               # 平台 HTTP 客户端
│   └── flowfoundry-plugin-runner/            # 插件 runner 宿主
├── flowfoundry-core/                         # 平台：建模器 + API + 解释器 + 插件控制面
├── examples/
│   └── ai-collection-strategy/               # 官方示例（Worker App 或插件包）
├── deploy/                                   # Docker Compose / Helm / K8s
├── scripts/                                  # local-dev、redeploy、plugin-runtime-dev 等
└── e2e/                                      # Playwright 建模器测试
```

## 分层原则

| 层级 | 模块 | 职责 |
|------|------|------|
| 平台 | `flowfoundry-core` | 建模器、Workflow/Flow API、编译器、解释器、插件管理 |
| SDK | `flowfoundry-sdk/*` | Worker 扩展点、SDK Client、plugin-runner |
| 业务 | `examples/*` 或独立 App 仓库 | Activity、Registry、Worker 或插件包 |

**新增业务**：独立 App 仓库，或打成插件包由平台托管（见 [docs/plugin-development-guide.md](docs/plugin-development-guide.md)）。**不要**把业务逻辑写进 `flowfoundry-core/`。

## 快速开始（本地调试）

> Docker 跑 Postgres / Redis / Temporal，应用在宿主机 JAR，改代码后约 10–15 秒可 redeploy。

```bash
chmod +x scripts/local-dev.sh scripts/check-progress.sh scripts/redeploy-worker.sh

./scripts/local-dev.sh up       # 首次或完整重启
./scripts/check-progress.sh     # 期望 ALL_GREEN

# 每次改代码后
./scripts/redeploy-worker.sh
```

| 用途 | 地址 |
|------|------|
| **建模器 / 联调** | http://127.0.0.1:8081/ |
| Worker 健康检查 | http://127.0.0.1:8081/actuator/health |
| Temporal UI | http://127.0.0.1:8080/ |

前端代码在 `flowfoundry-core/src/main/resources/static/`，打进 JAR 后由场景应用提供，**没有热更新**。

## 测试

```bash
mvn test                                    # Java 单元测试
npm run test:e2e                            # Playwright 建模器 E2E（:4173 静态服务）
./scripts/runtime-test.sh                   # FlowInterpreter E2E（需本地栈已起）
```

## 业务场景示例（AI 催收）

Activity 注册表：`examples/ai-collection-strategy/config/activities-registry.yaml`  
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
