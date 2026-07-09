# FlowFoundry 仓库目录说明

## 总览

```text
flowfoundry/                          # 平台仓库（本仓库）
├── pom.xml                           # Maven 父工程
├── flowfoundry-sdk/                  # ★ SDK 父模块（packaging=pom）【规划中】
│   ├── flowfoundry-sdk-bom/          #    版本 BOM → GitHub Packages
│   ├── flowfoundry-sdk/              #    Worker 运行时
│   └── flowfoundry-sdk-client/       #    平台 HTTP 客户端 + BFF
├── flowfoundry-core/                 # ★ 平台内核（建模器 + API + 解释器）
├── examples/                         # ★ 官方示例（仅 ai-collection-strategy）【规划中】
├── docs/
├── scripts/
├── deploy/
└── e2e/
```

> **架构演进**：业务 App 在**独立 Git 仓库**开发；import **`flowfoundry-sdk-bom`**，依赖 sdk + sdk-client；平台 HTTP **仅经 SDK Client 从 App 后端发出**。本仓库 `examples/` 保留 1 个官方示例。详见 [flowfoundry-sdk-design.md](flowfoundry-sdk-design.md)。

**依赖关系（目标分层）：**

```text
flowfoundry-sdk-client  ──depends──►  flowfoundry-sdk
       ▲                                      ▲
       │                                      │ depends
flowfoundry-core                           examples/ai-collection-strategy

外部 flowfoundry-app 仓库
  import flowfoundry-sdk-bom
  depends flowfoundry-sdk + flowfoundry-sdk-client
  运行时：SDK Client → flowfoundry-core (:8081)；Worker → Temporal
```

本地联调：**http://127.0.0.1:8081/**（平台）；示例 Worker **http://127.0.0.1:8082/**（Worker + BFF + iframe 壳）

**服务地址权威表**：[service-urls.md](service-urls.md)

---

## flowfoundry-sdk/（SDK 父模块）【规划中】

发布至 **GitHub Packages**：`https://maven.pkg.github.com/Tinet-AaronAn/flowfoundry`

| 子模块 | Artifact | 用途 |
|--------|----------|------|
| `flowfoundry-sdk-bom` | `com.tinet.flowfoundry:flowfoundry-sdk-bom` | BOM；业务 App `dependencyManagement` import |
| `flowfoundry-sdk` | `com.tinet.flowfoundry:flowfoundry-sdk` | Worker：`@EnableFlowFoundryWorker`、Router、Registry、幂等 |
| `flowfoundry-sdk-client` | `com.tinet.flowfoundry:flowfoundry-sdk-client` | `FlowFoundryPlatformClient`、BFF 控制器、共享 contract DTO |

业务 App 依赖示例：

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>com.tinet.flowfoundry</groupId>
      <artifactId>flowfoundry-sdk-bom</artifactId>
      <version>${flowfoundry.version}</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

**不包含在 SDK**：平台 Controller 实现、建模器 UI、JPA/Flyway、解释器主体、`@EnableFlowFoundry`。

---

## flowfoundry-core/（平台内核）

**包名：** `com.tinet.flowfoundry.*`（启动类在 `com.tinet.flowfoundry.boot`）

| 包 | 作用 |
|----|------|
| `flow/` | 建模器 JSON → `ExecutionPlan` 编译；FEEL 子集 |
| `interpreter/` | Temporal 通用流程解释器；`CompositeDynamicActivityRouter` |
| `registry/` | Activity 注册表加载与合并；core Registry 见 `core-activities-registry.yaml` |
| `activity/` | 通用 Activity 实现（`script-runtime`、`human-task`） |
| `idempotency/` | Redis Activity 幂等（平台侧；SDK 暴露 Executor 给业务） |
| `api/` | REST：`/api/flows/*`、`/api/workflows/*` |
| `workflow/` | 流程定义 PostgreSQL 持久化 |
| `temporal/` | Temporal Worker 启动、平台解释器 Worker |
| `config/` | Spring 配置 |
| `boot/` | `FlowFoundryCoreApplication`（`run-mode=platform`） |
| `src/main/resources/static/` | **建模器** HTML/JS/CSS |

平台可独立启动：`mvn -pl flowfoundry-core -am package && java -jar flowfoundry-core/target/flowfoundry-core-*-exec.jar`

**依赖**：`flowfoundry-sdk`（目标架构；当前尚未拆分）。

---

## examples/（官方示例）【规划中】

原 `flowfoundry-app/modules/` 迁移至此。`packaging=pom` 聚合示例子模块，**非业务交付模板**。

新增官方示例：在 `examples/` 下新建可独立启动的 Spring Boot 子模块；业务团队应复制到自己的独立仓库，而非在本目录长期开发。

---

## examples/ai-collection-strategy/（示例场景）

**包名：** `com.tinet.flowfoundry.demo.aicollection.*`

| 内容 | 作用 |
|------|------|
| `AiCollectionStrategyApplication.java` | Worker 启动类（`@EnableFlowFoundryWorker`） |
| `config/activities-registry.yaml` | 本业务 Activity 注册表 |
| `*Activities*` | 催收 Activity（Impl + Stub） |
| `AiCollectionActivityRouter` | `activityType` → real/stub 路由 |
| `AiCollectionWorkerExtension` | 向 Temporal Worker 注册本业务 |
| `Dockerfile` | 生产镜像 |

构建：`mvn -pl examples/ai-collection-strategy -am package`（迁移后路径）

详见 [flowfoundry-app/modules/ai-collection-strategy/README.md](../flowfoundry-app/modules/ai-collection-strategy/README.md)（迁移后迁至 `examples/`）。

---

## 独立 flowfoundry-app 仓库（业务团队）

业务交付物**不在本仓库**。标准结构见 [workflow-development-guide.md](workflow-development-guide.md) §3 与 [flowfoundry-sdk-design.md §4](flowfoundry-sdk-design.md#4-独立-flowfoundry-app-仓库标准结构)。

---

## 当前状态 vs 目标（迁移对照）

| 项 | 当前 | 目标 |
|----|------|------|
| 业务示例位置 | `flowfoundry-app/modules/` | `examples/`（**保留 1 个**） |
| 示例依赖 | `flowfoundry-core` 全量 | BOM + `flowfoundry-sdk` + `flowfoundry-sdk-client` |
| 平台 HTTP 调用 | 前端直连 `:8081`（示例壳） | App BFF → SDK Client → 平台 |
| 业务团队仓库 | 耦在 monorepo | 独立 Git + GitHub Packages |
| SDK 发布 | 无 | GitHub Packages + `flowfoundry-sdk-bom` |

---

## 改代码指南

| 目标 | 改哪里 |
|------|--------|
| 解释器 / 编译器 / 画布 / 平台 API | `flowfoundry-core/` |
| SDK 公开 API / Worker 扩展点 | `flowfoundry-sdk/`（拆分后） |
| 实体命名与分层定义 | [entity-naming.md](entity-naming.md) |
| **业务场景 / 流程软件开发** | [workflow-development-guide.md](workflow-development-guide.md)（独立 App 仓库） |
| 官方示例 | `examples/<场景>/` |
| 本地联调 | `./scripts/redeploy-worker.sh`（:8081）+ `./scripts/redeploy-app.sh`（:8082 示例） |
