# AI 催收策略业务场景

本目录是 `flowfoundry-app/modules/` 下的**可独立启动业务场景**，演示如何用 Activity 注册表 + 动态流程解释器跑通「多轮 AI 催收」。

## 目录

```
flowfoundry-app/modules/ai-collection-strategy/
├── AiCollectionStrategyApplication.java   # 启动类（main）
├── config/
│   └── activities-registry.yaml             # Activity 注册表（本地 redeploy 可用 file: 读取）
├── src/main/java/.../aicollection/
│   ├── CallCampaignActivitiesImpl.java    # 真实 Activity 实现
│   ├── CallCampaignActivitiesStub.java    # Web 联调桩实现（长等待 Activity 立即完成）
│   ├── AiCollectionActivityRouter         # activityType → real/stub 路由（DualModeActivityHandler）
│   └── AiCollectionWorkerExtension        # 向平台注册本业务的 Workflow/Activity
├── src/main/resources/application.yml
├── Dockerfile
└── pom.xml
```

## 与平台的关系

| 层级 | 模块 | 职责 |
|------|------|------|
| 平台 | `flowfoundry-core` | 流程 CRUD、编译、解释器、建模器 UI、Temporal 扩展点 |
| 聚合 | `flowfoundry-app` | Maven 聚合各场景子模块 |
| 业务 | 本目录 | 催收 Activity、注册表、路由、独立 `main` |

新增业务场景时，复制本目录结构，实现 `TemporalWorkerExtension`，并在 `flowfoundry-app/pom.xml` 的 `<modules>` 中注册。

## 本地调试

改完本目录代码或 `config/activities-registry.yaml` 后：

```bash
./scripts/redeploy-worker.sh
```

注册表路径：`flowfoundry-app/modules/ai-collection-strategy/config/activities-registry.yaml`

构建产物：`target/ai-collection-strategy-demo-1.0.0-SNAPSHOT.jar`
