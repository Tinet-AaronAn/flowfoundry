# FlowFoundry 仓库目录说明

## 总览

```
flowfoundry/
├── pom.xml                                    # Maven 父工程
├── flowfoundry-core/                          # ★ 平台内核（建模器 + API + 解释器）
├── flowfoundry-app/                           # ★ 业务场景聚合（packaging=pom，无 src）
│   └── modules/
│       └── ai-collection-strategy/            # ★ 可独立启动的业务场景
├── docs/                                      # 架构与设计文档（业务开发见 workflow-development-guide.md）
├── scripts/                                   # 本地开发 / redeploy
├── deploy/                                    # Docker / K8s / Helm
└── e2e/                                       # Playwright 建模器测试
```

**依赖关系（严格分层）：**

```
flowfoundry-core                              ← 平台，业务无关
flowfoundry-app/modules/ai-collection-strategy  ──depends──►  flowfoundry-core
flowfoundry-app                               ← 仅聚合 modules，无代码
```

本地联调入口：**http://127.0.0.1:8081/**（场景 JAR：`ai-collection-strategy-demo`）

**服务地址权威表**：[service-urls.md](service-urls.md)

---

## flowfoundry-core/（平台内核）

**包名：** `com.tinet.flowfoundry.*`（启动类在 `com.tinet.flowfoundry.boot`）

| 包 | 作用 |
|----|------|
| `flow/` | 建模器 JSON → `ExecutionPlan` 编译；FEEL 子集 |
| `interpreter/` | Temporal 通用流程解释器；`DynamicActivityRouter` 接口；`DualModeActivityHandler`、`RunSource` |
| `registry/` | Activity 注册表加载机制；core Registry 见 `core-activities-registry.yaml` |
| `activity/` | 通用 Activity 实现（如 `script-runtime`）与 `CompositeDynamicActivityRouter` |
| `idempotency/` | Redis Activity 幂等 |
| `api/` | REST：`/api/flows/*`、`/api/workflows/*` |
| `workflow/` | 流程定义 PostgreSQL 持久化 |
| `temporal/` | Temporal Worker 启动、`TemporalWorkerExtension` 扩展点 |
| `config/` | Spring 配置 |
| `boot/` | `FlowFoundryCoreApplication`（平台独立启动入口） |
| `src/main/resources/application-flowfoundry-platform.yml` | 平台共享 Spring 配置（数据源、Redis、Temporal 默认等） |
| `src/main/resources/static/` | **建模器** HTML/JS/CSS（通用空画布） |

平台可独立启动：`mvn -pl flowfoundry-core -am package && java -jar flowfoundry-core/target/flowfoundry-core-*-exec.jar`

---

## flowfoundry-app/（业务场景聚合）

`packaging=pom`，仅列出 `modules/*` 子模块，**无 `src/`、无统一 main**。

新增业务场景：在 `modules/` 下新建可独立启动的 Spring Boot 子模块，并在 `flowfoundry-app/pom.xml` 的 `<modules>` 中注册。

---

## flowfoundry-app/modules/ai-collection-strategy/（业务场景示例）

**包名：** `com.tinet.flowfoundry.demo.aicollection.*`

| 内容 | 作用 |
|------|------|
| `AiCollectionStrategyApplication.java` | 场景启动类（`main`） |
| `src/main/resources/application.yml` | 场景配置（`spring.config.import` 引入平台配置 + Temporal/注册表覆盖） |
| `config/activities-registry.yaml` | 本业务 Activity 注册表 |
| `*Activities*` | 催收相关 Activity 实现（`CallCampaignActivitiesImpl` 真实 + `CallCampaignActivitiesStub` 桩） |
| `AiCollectionActivityRouter` | `activityType` → Activity 方法路由；继承 `DualModeActivityHandler` |
| `AiCollectionWorkerExtension` | 向平台 Worker 注册 Workflow/Activity |
| `Dockerfile` | 生产镜像 |

构建：`mvn -pl flowfoundry-app/modules/ai-collection-strategy -am package`  
产物：`flowfoundry-app/modules/ai-collection-strategy/target/ai-collection-strategy-demo-1.0.0-SNAPSHOT.jar`

详见 [flowfoundry-app/modules/ai-collection-strategy/README.md](../flowfoundry-app/modules/ai-collection-strategy/README.md)。

---

## 外部参考

| 路径 | 说明 |
|------|------|
| `getstarted/` | Temporal 官方 TS 示例，**仅参考**，已加入 `.gitignore` |

---

## 改代码指南

| 目标 | 改哪里 |
|------|--------|
| 解释器 / 编译器 / 画布 / 平台 API | `flowfoundry-core/` |
| 实体命名与分层定义 | [docs/entity-naming.md](entity-naming.md) |
| **业务场景 / 流程软件开发** | [docs/workflow-development-guide.md](workflow-development-guide.md) |
| 新增 Activity 类型定义 | `flowfoundry-app/modules/<场景>/config/activities-registry.yaml` |
| Activity 业务实现 | `flowfoundry-app/modules/<场景>/src/...` |
| 新增一套业务 | 新建 `flowfoundry-app/modules/xxx/`（含 `main` + `pom.xml`），注册到 `flowfoundry-app/pom.xml` |
| 本地联调 | `./scripts/redeploy-worker.sh`（默认场景 `ai-collection-strategy`） |
