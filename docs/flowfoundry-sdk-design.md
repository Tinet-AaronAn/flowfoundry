# FlowFoundry SDK 与独立 App 仓库架构设计

> **状态**：已实现（Phase 1，2026-07-09）。发布见 [sdk-maven-publish.md](./sdk-maven-publish.md)。

## 评审结论（已定稿）

| # | 议题 | 决定 |
|---|------|------|
| 1 | SDK 是否含平台 HTTP 客户端 | **需要**。所有 App 对 FlowFoundry 平台的 HTTP 调用**必须经 SDK Client 从 App 后端发出**；**禁止**业务前端直连 `:8081` |
| 2 | 官方示例是否保留在平台仓库 | **保留 1 个**（`ai-collection-strategy`，迁至 `examples/`） |
| 3 | Maven 发布仓库 | **GitHub Packages**（`com.tinet.flowfoundry`） |
| 4 | 版本统一 | 提供 **`flowfoundry-sdk-bom`**，业务 App 通过 BOM import 对齐 SDK / Client / 传递依赖版本 |

---

## 1. 背景与问题

### 1.1 现状

当前仓库为 Maven 单体结构：

```text
flowfoundry/
├── flowfoundry-core/          # 平台（建模器、API、解释器、Temporal 管理）
└── flowfoundry-app/           # 业务场景聚合（packaging=pom）
    └── modules/
        └── ai-collection-strategy/   # 示例场景，依赖整个 flowfoundry-core
```

业务场景模块的 `pom.xml` 直接依赖 `flowfoundry-core` 全量 artifact，带来依赖过重、仓库耦合、职责混淆、无法独立分发 SDK 等问题（详见初版 §1.1）。

### 1.2 目标

```text
平台团队维护 flowfoundry（core）
  → 发布 flowfoundry-sdk-bom + flowfoundry-sdk + flowfoundry-sdk-client 至 GitHub Packages

业务团队维护独立 flowfoundry-app 仓库
  → import BOM，依赖 flowfoundry-sdk / flowfoundry-sdk-client
  → App 后端经 SDK Client 访问 flowfoundry-core；前端只调 App 自身 API
  → Temporal Worker 经 SDK Worker 扩展点执行 Activity

本仓库 examples/ 保留 1 个官方示例（ai-collection-strategy）
```

**设计原则**：

1. **平台与业务物理分离**：业务代码不进平台仓库；平台实现不进业务仓库。
2. **SDK 是唯一契约**：Worker 扩展点、Registry schema、**平台 HTTP 契约**均通过 SDK 暴露。
3. **BFF 边界**：业务前端 → App 后端（同源）→ SDK Client → 平台 `:8081`；不暴露平台 API Key 给浏览器。
4. **运行时仍通过 Temporal 协作**：App Worker 与平台解释器共享 namespace / task queue。
5. **Namespace 由平台管理**：App 在 Registry / 配置中声明同名 namespace，但不能自助创建平台 namespace；须管理员先登记（或本地依赖平台 bootstrap）。详见 [workflow-development-guide.md §4.5](./workflow-development-guide.md#45-namespace-前置条件必读)。
6. **渐进迁移**：monorepo 内先拆模块并验证，再发布 GitHub Packages。

---

## 2. 目标架构

### 2.1 平台仓库三件套 + SDK 子模块

```text
flowfoundry/（平台仓库）
├── flowfoundry-sdk/                    # packaging=pom
│   ├── flowfoundry-sdk-bom/            # 版本 BOM（发布）
│   ├── flowfoundry-sdk/                # Worker 运行时（发布）
│   └── flowfoundry-sdk-client/         # 平台 HTTP 客户端（发布）
├── flowfoundry-core/                   # 平台服务 JAR（:8081）
└── examples/
    └── ai-collection-strategy/           # 唯一官方示例
```

### 2.2 运行时关系（含 BFF）

```text
┌─────────────────────┐   同源 /app/api/*    ┌──────────────────────────┐
│  业务前端 / iframe壳  │ ──────────────────► │  App 后端 :8082           │
│  （:8082 静态页）     │                      │  BFF Controller         │
└─────────────────────┘                      │  + SDK Client           │
         │                                   └────────────┬─────────────┘
         │ embed 建模器 iframe（静态/嵌入页仍来自平台）      │ HTTP（服务端）
         ▼                                                ▼
┌─────────────────────┐                      ┌──────────────────────────┐
│  :8081 modeler/embed │                      │  flowfoundry-core :8081  │
│  apiBase → App BFF   │ ◄── SDK Client ──────│  /api/workflows|flows/*  │
└─────────────────────┘                      └────────────┬─────────────┘
                                                            │ Temporal
                                                            ▼
                                               ┌──────────────────────────┐
                                               │  App Worker Activity      │
                                               └──────────────────────────┘
```

**关键点**：

- 业务 App **不**启动平台 REST API，也**不**在浏览器配置 `apiBase: http://platform:8081/api`。
- 建模器 iframe 的 `apiBase` 指向 **App BFF**（如 `http://127.0.0.1:8082/app/api/flowfoundry`），由 BFF 经 SDK Client 转发至平台。
- `GET /api/platform/public-config` 等初始化请求同样经 App BFF 代理（示例 `workflow-admin.html` 需改造）。

### 2.3 模块依赖方向

```text
flowfoundry-sdk-client  ──depends──►  flowfoundry-sdk（共享 DTO / 配置）
       ▲                                      ▲
       │                                      │ depends
flowfoundry-core                           examples/*
       ▲
       │ depends（core 复用 SDK 中的 API DTO 与契约类型）

外部 flowfoundry-app 仓库
  ├── import flowfoundry-sdk-bom
  ├── flowfoundry-sdk          （Worker）
  └── flowfoundry-sdk-client   （平台 HTTP）
```

---

## 3. flowfoundry-sdk 模块范围

### 3.1 `flowfoundry-sdk`（Worker 运行时）

| 包 / 资源 | 用途 |
|-----------|------|
| `com.tinet.flowfoundry.sdk` | `@EnableFlowFoundryWorker` |
| `com.tinet.flowfoundry.activity` | `BusinessActivityRouter` |
| `com.tinet.flowfoundry.interpreter.runtime` | `DualModeActivityHandler`、`RunSource` 等 |
| `com.tinet.flowfoundry.temporal` | `TemporalWorkerExtension`、`DeploymentContract` |
| `com.tinet.flowfoundry.registry` | Registry 模型与加载 |
| `com.tinet.flowfoundry.idempotency` | `IdempotentActivityExecutor` |
| `com.tinet.flowfoundry.config`（Worker 子集） | Worker Spring 配置 |
| `com.tinet.flowfoundry.contract` | **新增**：与平台 REST 共享的 DTO（`RunRequest`、`FlowRunListPage` 等） |
| `application-flowfoundry-worker.yml` | Worker 默认配置 |
| `application-flowfoundry-sdk.yml` | Redis、Temporal 等最小基础设施 |

`@EnableFlowFoundry`（完整平台）**不进入 SDK**，仅 `flowfoundry-core` boot 使用。

### 3.2 `flowfoundry-sdk-client`（平台 HTTP 客户端）

| 组件 | 用途 |
|------|------|
| `FlowFoundryPlatformClient` | 同步 HTTP 客户端门面（基于 `RestClient` 或 `WebClient`） |
| `FlowFoundryPlatformProperties` | `base-url`、`api-key`、默认 `namespace`、超时 |
| `FlowFoundryPlatformClientAutoConfiguration` | Spring Boot 自动配置（`@EnableFlowFoundryPlatformClient` 或随 Worker 可选启用） |
| `FlowFoundryPlatformBffController`（可选） | SDK 提供的可挂载 BFF 控制器，映射 `/app/api/flowfoundry/**` → 平台 API |

**客户端 API 覆盖范围（Phase 1 最小集）**：

| 分组 | 平台路径 | SDK Client 方法（示意） |
|------|----------|-------------------------|
| 平台配置 | `GET /api/platform/public-config` | `getPublicConfig()` |
| Namespace 上下文 | `GET /api/workflows/context` | `getWorkflowContext()` |
| Workflow CRUD | `GET/POST /api/workflows/**` | `listWorkflows()`、`getWorkflow()`、`createWorkflow()`、`saveVersion()` … |
| Flow 运行 | `POST /api/flows/run`、`GET /api/flows/runs/**` | `runFlow()`、`listRuns()`、`getRunStatus()`、`completeHumanTask()` |
| 按版本启动 | `POST /api/workflows/{id}/versions/{v}/run` | `startWorkflowVersion()` |
| Activity 目录 | `GET /api/activities` | `listActivities()` |
| 编译 | `POST /api/flows/compile` | `compileFlow()` |

**认证与请求头**（Client 统一注入，前端不传平台密钥）：

```text
Authorization: Bearer <api-key>     # 或平台约定的 X-Api-Key
X-Platform-Namespace: <namespace>   # 来自配置或每次调用参数
```

Admin API（`/api/admin/**`）**不**纳入 SDK Client——由平台运维 / 管理员直连平台，业务 App 无权代管。

### 3.3 留在 flowfoundry-core

平台 HTTP Controller 实现、建模器 UI、JPA/Flyway、解释器主体、平台 RBAC、平台 Worker 引导、`script-runtime` / `human-task` 实现。

Controller 层逐步改为使用 SDK `contract` 包中的 DTO，避免重复定义。

### 3.4 `flowfoundry-sdk-bom`

```xml
<!-- 业务 App pom.xml -->
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

<dependencies>
  <dependency>
    <groupId>com.tinet.flowfoundry</groupId>
    <artifactId>flowfoundry-sdk</artifactId>
  </dependency>
  <dependency>
    <groupId>com.tinet.flowfoundry</groupId>
    <artifactId>flowfoundry-sdk-client</artifactId>
  </dependency>
  <!-- 版本由 BOM 管理，无需写 version -->
</dependencies>
```

BOM 锁定：`flowfoundry-sdk`、`flowfoundry-sdk-client`、`temporal-sdk`、与 Worker 相关的 Spring Boot 传递版本（与平台 parent 对齐）。

---

## 4. 独立 flowfoundry-app 仓库标准结构

### 4.1 目录模板

```text
my-scenario-app/
├── pom.xml                             # import flowfoundry-sdk-bom
├── config/activities-registry.yaml
├── Dockerfile
└── src/main/
    ├── java/.../
    │   ├── MyScenarioApplication.java
    │   ├── MyActivityRouter.java / MyWorkerExtension.java / ...
    │   └── web/
    │       └── FlowFoundryBffConfig.java   # 启用 SDK BFF 或自定义薄封装
    └── resources/
        ├── application.yml
        └── static/app/
            └── workflow-admin.html         # 仅 fetch 同源 /app/api/flowfoundry/*
```

### 4.2 `application.yml`（增补平台 Client）

```yaml
spring:
  config:
    import:
      - classpath:application-flowfoundry-sdk.yml
      - classpath:application-flowfoundry-worker.yml
  application:
    name: my-scenario

temporal:
  host: ${TEMPORAL_HOST:127.0.0.1:7233}

flowfoundry:
  platform:
    base-url: ${FLOWFOUNDRY_PLATFORM_URL:http://127.0.0.1:8081}
    api-key: ${FLOWFOUNDRY_API_KEY:}
    namespace: ${FLOWFOUNDRY_NAMESPACE:my-scenario}
  bff:
    enabled: true
    base-path: /app/api/flowfoundry    # 业务前端唯一入口

platform:
  activity-registry:
    path: ${ACTIVITY_REGISTRY_PATH:classpath:activities-registry.yaml}
```

### 4.3 前端约束（强制）

| 禁止 | 必须 |
|------|------|
| `fetch('http://platform:8081/api/...')` | `fetch('/app/api/flowfoundry/...')` 或 App 自定义同源路径 |
| 浏览器持有平台 API Key | API Key 仅配置在 App 服务端环境变量 |
| `FlowFoundryModeler` 的 `apiBase` 指向 `:8081` | `apiBase` 指向 App BFF（同源） |

---

## 5. 平台与 App 的协作契约

### 5.1 Registry / DeploymentContract / RunSource

与初版一致：Registry yaml 由 App 维护、平台加载合并；Worker 发布 `DeploymentContract`；建模器 Run 走 Stub、production API 走 Impl。

### 5.2 版本兼容矩阵

| flowfoundry-core | flowfoundry-sdk-bom | Registry schema | 说明 |
|------------------|---------------------|-----------------|------|
| 1.0.x | 1.0.x | v1.0 | 当前基线 |
| 1.1.x | 1.0.x | v1.0 | SDK 向后兼容时允许 patch 领先 |
| 2.0.x | 2.0.x | v2.0 | 破坏性变更同步 major |

业务 App 的 `flowfoundry.version`（BOM）须与已部署平台 **minor 版本一致**。

---

## 6. 本仓库演进计划

### 6.1 目录变更

| 现在 | 目标 |
|------|------|
| `flowfoundry-app/` | `examples/`（packaging=pom，仅含 ai-collection-strategy） |
| `flowfoundry-app/modules/ai-collection-strategy/` | `examples/ai-collection-strategy/` |
| （无） | `flowfoundry-sdk/`（bom + sdk + sdk-client） |

父 `pom.xml` modules：

```xml
<modules>
  <module>flowfoundry-sdk</module>
  <module>flowfoundry-core</module>
  <module>examples</module>
</modules>
```

### 6.2 分阶段实施

```text
Phase 1 — SDK 模块抽取（monorepo 内）
  ├── 新建 flowfoundry-sdk/{bom,sdk,sdk-client}
  ├── 从 core 迁出 Worker 扩展点 + contract DTO
  ├── 实现 FlowFoundryPlatformClient + BFF 控制器
  ├── core 依赖 sdk；Controller 改用 contract DTO
  ├── examples/ai-collection-strategy 仅依赖 BOM + sdk + sdk-client
  ├── 改造 workflow-admin.html：apiBase / public-config 走 App BFF
  └── 全量测试 + 8081/8082 联调

Phase 2 — 发布 GitHub Packages
  ├── CI workflow：mvn deploy（sdk-bom、sdk、sdk-client）
  ├── 文档：settings.xml、PACKAGE_README、版本发布流程
  └── 提供 archetype 或以 examples/ai-collection-strategy 为 clone 模板

Phase 3 — 清理
  ├── 删除旧 flowfoundry-app 聚合模块
  ├── redeploy-app.sh 默认路径改为 examples/
  └── service-urls.md 更新 BFF 路径说明
```

> **不再外置示例仓库**（评审结论：官方示例保留 1 个在平台仓库）。

---

## 7. CI / 发布（GitHub Packages）

### 7.1 坐标

| Artifact | 说明 |
|----------|------|
| `com.tinet.flowfoundry:flowfoundry-sdk-bom` | BOM，`packaging=pom` |
| `com.tinet.flowfoundry:flowfoundry-sdk` | Worker 运行时 |
| `com.tinet.flowfoundry:flowfoundry-sdk-client` | 平台 HTTP 客户端 |

Registry URL：`https://maven.pkg.github.com/Tinet-AaronAn/flowfoundry`

### 7.2 平台仓库 `pom.xml` 发布段（示意）

```xml
<distributionManagement>
  <repository>
    <id>github</id>
    <url>https://maven.pkg.github.com/Tinet-AaronAn/flowfoundry</url>
  </repository>
</distributionManagement>
```

### 7.3 业务开发者 `~/.m2/settings.xml`（示意）

```xml
<settings>
  <servers>
    <server>
      <id>github</id>
      <username>${env.GITHUB_ACTOR}</username>
      <password>${env.GITHUB_TOKEN}</password>
    </server>
  </servers>
</settings>
```

读取依赖时仓库需声明 GitHub Packages（或使用平台提供的 `settings.xml` 片段文档）。

### 7.4 CI 发布流程

```text
tag v1.0.x 或 push main（sdk 目录变更）
  → GitHub Actions: mvn -pl flowfoundry-sdk -am deploy
  → 发布 bom + sdk + sdk-client 同版本号
  → GitHub Release 附 Maven 坐标与 BOM import 示例
```

`flowfoundry-core` Docker 镜像**不**发布到 Maven；仅 SDK 三件套发布。

### 7.5 业务 App 仓库 CI

```text
mvn test package（从 GitHub Packages 解析 SDK）
  → Docker build Worker 镜像
  → 集成测试：App BFF → staging 平台
```

---

## 8. 安全与边界

| 项 | 策略 |
|----|------|
| 平台 API Key | 仅存 App 服务端；SDK Client 注入；**禁止**下发浏览器 |
| BFF 鉴权 | App 对前端使用自有会话 / JWT；BFF 再映射为平台 API Key |
| SDK 公开 API | 文档化 `com.tinet.flowfoundry.sdk.*`、`client.*`、`contract.*` |
| 依赖约束 | 业务 App **禁止**依赖 `flowfoundry-core`（Maven Enforcer + 文档） |
| Admin API | 不经过 SDK Client；管理员直连平台 |

---

## 9. 验收标准（开发完成后）

- [ ] `examples/ai-collection-strategy` 仅依赖 BOM + sdk + sdk-client，`dependency:tree` 无 `flowfoundry-core`
- [ ] `flowfoundry-core` 依赖 SDK contract 类型，平台测试全绿
- [ ] `FlowFoundryPlatformClient` 覆盖 §3.2 最小 API 集
- [ ] `workflow-admin.html` 无直连 `:8081/api/*`；建模器 `apiBase` 指向 App BFF
- [ ] 本地 8081 + 8082 联调：画布保存、Run、查 Run 状态均经 BFF → SDK Client → 平台
- [ ] `flowfoundry-sdk-bom`、`flowfoundry-sdk`、`flowfoundry-sdk-client` 发布至 GitHub Packages
- [ ] 空目录按 §4 模板新建外部 App，仅从 GitHub Packages 拉 SDK 即可完成集成

---

## 10. 相关文档

| 文档 | 关系 |
|------|------|
| [workflow-development-guide.md](./workflow-development-guide.md) | 业务开发操作指南 |
| [project-structure.md](./project-structure.md) | 仓库目录说明 |
| [service-urls.md](./service-urls.md) | 端口与 BFF 路径 |
| [business-orchestration-architecture.md](./business-orchestration-architecture.md) | 平台定位 |
