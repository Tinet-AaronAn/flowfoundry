# AI 催收策略业务场景

本目录是 `flowfoundry-app/modules/` 下的 **Temporal Worker 场景**，演示如何用 Activity 注册表 + 动态流程解释器跑通「多轮 AI 催收」。

**平台 HTTP（建模器、Workflow API、Registry 展示）由 `flowfoundry-core :8081` 提供**；本模块以 `flowfoundry.run-mode=worker` 运行，不启动第二套平台 API。

## 目录

```
flowfoundry-app/modules/ai-collection-strategy/
├── AiCollectionStrategyApplication.java   # Worker 启动类（@EnableFlowFoundryWorker）
├── config/
│   └── activities-registry.yaml             # Activity 注册表（平台 :8081 与 Worker 均读取）
├── src/main/java/.../aicollection/
│   ├── CallCampaignActivitiesImpl.java    # 真实 Activity 实现
│   ├── CallCampaignActivitiesStub.java    # Web 联调桩实现
│   ├── AiCollectionActivityRouter         # activityType → real/stub 路由
│   └── AiCollectionWorkerExtension        # 向 Temporal Worker 注册本业务
├── src/main/resources/static/app/         # iframe 业务壳（:8082）
├── Dockerfile
└── pom.xml
```

## 与平台的关系

| 层级 | 模块 | 职责 |
|------|------|------|
| 平台 | `flowfoundry-core` (:8081) | 流程 CRUD、编译、建模器、Registry 合并展示、解释器 Worker |
| 聚合 | `flowfoundry-app` | Maven 聚合各场景子模块 |
| 业务 | 本目录 (:8082) | Activity 实现、Registry yaml、Temporal Worker、`workflow-admin.html` |

新增业务场景时，复制本目录结构，使用 `@EnableFlowFoundryWorker`，实现 `TemporalWorkerExtension`。

## 本地调试

```bash
./scripts/redeploy-worker.sh   # 平台 :8081（加载本目录 config/activities-registry.yaml）
./scripts/redeploy-app.sh      # Worker :8082
```

- 建模器 / Registry：http://127.0.0.1:8081/
- iframe 业务壳：http://127.0.0.1:8082/app/workflow-admin.html

构建产物：`target/ai-collection-strategy-demo-1.0.0-SNAPSHOT.jar`
