# AI 催收策略业务场景

本目录是 **官方示例**：演示 Activity 注册表 + 动态流程解释器跑通「多轮 AI 催收」。

**平台 HTTP（建模器、Workflow API、Registry）由 `flowfoundry-core :8081` 提供**。本模块支持两种运行方式：

| 模式 | 说明 | 联调 |
|------|------|------|
| **Worker App** | 独立 Spring Boot 进程（`@EnableFlowFoundryWorker`） | `./scripts/redeploy-app.sh` → :8082 |
| **插件包** | 打成 `*-plugin.jar`，由平台 K8s runner 托管 | [plugin-development-guide.md](../../docs/plugin-development-guide.md) |

## 目录

```
examples/ai-collection-strategy/
├── src/main/java/.../aicollection/
│   ├── AiCollectionStrategyApplication.java   # Worker 模式启动类（插件包排除）
│   ├── CallCampaignActivitiesImpl.java      # 真实 Activity 实现
│   ├── CallCampaignActivitiesStub.java        # Web 联调桩
│   ├── AiCollectionActivityRouter             # activityType → real/stub
│   ├── AiCollectionWorkerExtension            # Temporal Worker 注册
│   └── CallCampaignWorkflow*.java             # typed workflow（可选）
├── config/activities-registry.yaml            # Activity 注册表
├── src/plugin/META-INF/flowfoundry-plugin.yaml  # 插件描述符（-Pplugin）
├── src/main/resources/static/app/             # iframe 业务壳（仅 Worker 模式 :8082）
├── Dockerfile
└── pom.xml
```

## 与平台的关系

| 层级 | 模块 | 职责 |
|------|------|------|
| 平台 | `flowfoundry-core` (:8081) | 流程 CRUD、编译、建模器、Registry、插件管理、解释器 Worker |
| 业务 | 本目录 | Activity 实现、Registry yaml、Worker 或插件包 |

**Namespace 前置**：平台 namespace 须先由管理员创建（或本地 bootstrap）；详见 [workflow-development-guide.md §4.5](../../docs/workflow-development-guide.md#45-namespace-前置条件必读)。

## 本地调试（Worker App 模式）

```bash
./scripts/redeploy-worker.sh   # 平台 :8081
./scripts/redeploy-app.sh      # Worker :8082
```

- 建模器 / Registry：http://127.0.0.1:8081/
- iframe 业务壳：http://127.0.0.1:8082/app/workflow-admin.html

产物：`target/ai-collection-strategy-demo-1.0.4.jar`

## 插件包模式

```bash
./scripts/plugin-runtime-dev.sh
./scripts/build-ai-collection-plugin.sh
# 建模器 → 插件 → 上传 → 启动
```

产物：`target/ai-collection-strategy-demo-1.0.4-plugin.jar`

完整步骤见 [docs/plugin-development-guide.md](../../docs/plugin-development-guide.md)。
