# FlowFoundry 流程软件开发指南

本文面向 **业务 App / 插件开发者**：说明如何用 FlowFoundry 平台交付一套可编排、可运行、可联调的工作流软件。

**适用读者**：维护独立业务 App 仓库的后端工程师；需要与建模器、Temporal 联调的 FDE / 全栈开发者。

> **架构背景**：业务 App 应在**独立 Git 仓库**开发，通过 **`flowfoundry-sdk` + `flowfoundry-sdk-client`** 依赖平台能力；**所有对平台 HTTP 的调用必须由 App 后端经 SDK Client 发出**，业务前端只访问 App 同源 BFF。运行时 Worker 对接 **`flowfoundry-core`**。本仓库 `examples/` 保留 1 个官方示例。详见 [flowfoundry-sdk-design.md](./flowfoundry-sdk-design.md)。

**相关文档**（按需深入）：

| 文档 | 用途 |
|------|------|
| [flowfoundry-sdk-design.md](./flowfoundry-sdk-design.md) | **SDK 与独立仓库架构设计（评审稿）** |
| [project-structure.md](./project-structure.md) | 平台仓库目录与分层 |
| [local-development.md](./local-development.md) | 本地环境、redeploy、排错 |
| [service-urls.md](./service-urls.md) | 端口与路径权威表 |
| [entity-naming.md](./entity-naming.md) | 画布 → DSL → 解释器命名对照 |
| [detailed-design.md](./detailed-design.md) | API、持久化、节点语义 |
| [business-orchestration-architecture.md](./business-orchestration-architecture.md) | 平台定位与架构背景 |
| [examples/ai-collection-strategy/README.md](../examples/ai-collection-strategy/README.md) | 官方示例场景 |
| [plugin-development-guide.md](./plugin-development-guide.md) | **插件包开发**（平台托管 Worker） |
| [plugin-runtime-design.md](./plugin-runtime-design.md) | 插件运行时架构设计 |

---

## 1. 你要交付什么

FlowFoundry 上的「流程软件」通常包含四块，缺一不可：

```text
① Activity Registry   — 声明「画布能选哪些业务能力」
② Activity 实现       — 真实逻辑 + 联调桩 + Router 路由
③ Worker 扩展         — 向 Temporal Worker 注册 Workflow/Activity
④ 流程定义（可选）     — 建模器画布 / Flow DSL，或手写 Temporal Workflow
```

**分工原则**（平台设计边界）：

| 角色 | 负责 | 不负责 |
|------|------|--------|
| 业务 / FDE（画布） | 节点连线、参数映射、分支、人工任务配置 | HTTP 重试、幂等、第三方 SDK |
| 开发者（Activity） | 外部系统调用、幂等、错误归一化、结构化入出参 | 改平台解释器、改 core 通用节点 |
| 平台（flowfoundry-core） | 编译、解释器、建模器、Registry 合并、通用 Activity | 具体业务规则 |

**严格分层**：

- 业务逻辑写在**你的独立 App 仓库**中。
- 通过 **`flowfoundry-sdk-bom`** 引入 `flowfoundry-sdk` + `flowfoundry-sdk-client`，**禁止**依赖 `flowfoundry-core`。
- **禁止**业务前端直连平台 `:8081/api/*`；须经 App BFF → SDK Client。
- **不要**把业务 Activity 写进 `flowfoundry-core/`。

### 1.1 插件模式（可选）

若业务**不需要**独立 `:8082` Worker、iframe 壳或 BFF，可把同一套 `Router` + `WorkerExtension` + registry 打成**插件包**，由平台 K8s runner 托管。交付物与联调步骤见 [plugin-development-guide.md](./plugin-development-guide.md)。Worker App 模式与插件模式可并存于官方示例，但联调时勿同时 poll 同一 task queue。

---

## 2. 适用场景与编排延迟预期

> 架构背景见 [business-orchestration-architecture.md §4.4](./business-orchestration-architecture.md#44-编排延迟与场景边界)；实现细节见 [detailed-design.md §3.4](./detailed-design.md#34-temporal-workflow-runtime)。

### 2.1 FlowFoundry 适合什么

- **耐久业务流程**：外呼 / 催收多轮、审批、人工任务、跨天 Timer、Signal 唤醒。
- **业务 Activity 耗时远大于编排**：单步外呼、导入号码、调用第三方 API 通常为 **秒级及以上**。
- **需要运行记录、版本治理、审计**：画布发布固定版本，Runs 视图可查节点轨迹。

### 2.2 不适合什么（含 realtime workflow）

FlowFoundry 经 **Temporal** 执行画布：每个 Service Task 对应一次 **Activity 调度**，Worker 通过 **poll Task Queue** 取任务。该模型 **不是** 进程内直调，**无法** 保证节点间 **&lt; 1ms** 的切换。

| 需求 | 是否适合画布 + 解释器 | 说明 |
|------|----------------------|------|
| 节点间损耗 **&lt; 1ms** | **否** | poll + Workflow Task 决策为毫秒～十毫秒级 |
| 画布 **大量节点**、纯内存、极速串行 | **否** | N 个节点 ≈ N 次编排往返，本地常见 **40–70ms × N**（仅编排税） |
| 单节点业务 **秒级以上** | **是** | 编排税可忽略 |
| 等人 / 等外部系统 / 长周期 | **是** | Temporal 核心能力 |

**典型延迟构成（本地开发，未调优 Worker）**：

```text
相邻两个 Service Task 之间（不含业务逻辑）：
  Activity poll（Schedule-to-Start）  ~15–30ms
  Workflow Task 决策                   ~10–50ms
  ─────────────────────────────────────────────
  编排税合计                           ~40–70ms / 节点
```

生产环境可通过 Temporal Worker 调优（poller 数量、Poller Autoscaling、Worker 与集群同区）压低，但仍是 **毫秒级**，达不到 realtime 的 **亚毫秒** 要求。

### 2.3 高节点数、低延迟需求时怎么做

1. **合并画布节点**：多个细粒度步骤封装进 **一个 Activity**（循环、批处理在 Activity 内完成）。
2. **Gateway 回边** 表达多轮业务，而不是为每一步拆一个 Service Task（参见 [loop-design.md](./loop-design.md)）。
3. **FDE 手写 Temporal Workflow**：复杂控制流在代码里合并，仅对真正需要副作用的边界调 Activity。
4. **不要用 FlowFoundry 画布** 承载实时风控链、游戏 tick、设备硬实时编排等场景。

**数量级估算**：10 个纯桩 Service Task、本地未调优时，仅 Temporal 编排累计约 **0.5–1s**；同一逻辑若用内存状态机执行通常 **&lt; 1ms**。设计流程时应用此量级判断是否在正确轨道上。

---

## 3. 架构一图流

```text
┌─────────────────────────────────────────────────────────────┐
│  业务前端 / iframe 壳（:8082 同源）                            │
│  只请求 /app/api/flowfoundry/*                               │
└───────────────────────────┬─────────────────────────────────┘
                            │ App BFF
                            ▼
┌─────────────────────────────────────────────────────────────┐
│  你的 App 后端                                                │
│  flowfoundry-sdk-client → flowfoundry-core :8081             │
│  flowfoundry-sdk Worker → Temporal Activity                   │
└───────────────────────────┬─────────────────────────────────┘
                            │ Temporal（解释器调度 Activity）
                            ▼
┌─────────────────────────────────────────────────────────────┐
│  flowfoundry-core：FlowInterpreterWorkflowImpl               │
└─────────────────────────────────────────────────────────────┘
```

建模器 iframe 仍从平台加载 `embed.html`，但其 **`apiBase` 必须指向 App BFF**，不得指向 `:8081/api`。

**三件套的职责**：

| Artifact | 谁维护 | 你的关系 |
|----------|--------|----------|
| `flowfoundry-core` | 平台团队 | 本地 / 测试 / 生产**已部署**；你不对其源码负责 |
| `flowfoundry-sdk-bom` | 平台团队发布至 GitHub Packages | **import** 统一版本 |
| `flowfoundry-sdk` | 平台团队发布 | Worker 扩展点、`@EnableFlowFoundryWorker` |
| `flowfoundry-sdk-client` | 平台团队发布 | **平台 HTTP 唯一通道**（App 后端调用） |
| 你的 App 仓库 | 业务团队 | Activity、Registry、Worker、BFF、可选 iframe 壳 |

**参考实现**：本仓库 `examples/ai-collection-strategy/`（AI 催收多轮外呼）；亦可复制为独立仓库起点。

---

## 4. 新建业务 App 仓库

### 4.1 推荐起点

从官方示例复制结构：

```bash
# 从平台仓库复制示例骨架
cp -r flowfoundry/examples/ai-collection-strategy my-scenario-app
cd my-scenario-app
# 改 groupId、包名、application.name、registry namespace
```

### 4.2 标准目录

```text
my-scenario-app/
├── pom.xml
├── config/activities-registry.yaml
├── Dockerfile
└── src/main/java/.../
    ├── MyScenarioApplication.java      # @EnableFlowFoundryWorker
    ├── MyActivities.java / Impl / Stub
    ├── MyActivityRouter.java
    └── MyWorkerExtension.java
```

### 4.3 Maven 依赖（BOM + SDK + Client）

在 `pom.xml` 中 **import BOM**，再声明 SDK 与 Client（版本由 BOM 管理）：

```xml
<properties>
  <flowfoundry.version>1.0.4</flowfoundry.version>
</properties>

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
</dependencies>

<!-- repositories：从 GitHub Packages 解析，见 flowfoundry-sdk-design.md §7 -->
```

> `flowfoundry.version` 须与已部署平台 **minor 版本**一致。发布坐标：`https://maven.pkg.github.com/Tinet-AaronAn/flowfoundry`

### 4.4 application.yml

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
    base-path: /app/api/flowfoundry

platform:
  activity-registry:
    path: ${ACTIVITY_REGISTRY_PATH:classpath:activities-registry.yaml}
```

**namespace 与 task queue** 在 `activities-registry.yaml` 中声明。`flowfoundry.platform.api-key` **仅服务端**，不得写入前端。

启动类使用 `@EnableFlowFoundryWorker`；启用 BFF 时增加 `@EnableFlowFoundryPlatformClient`（Phase 1 实现后的注解名，以 SDK 为准）。

### 4.5 Namespace 前置条件（必读）

Namespace 是平台侧管理的隔离单位（Workflow 存储、Temporal 物理隔离、Activity Registry 归属三者同名）。**App 不能自主注册、随意新增平台 namespace**；开发与联调前须先满足下列条件。

#### 谁可以创建 namespace

| 方式 | 谁操作 | 说明 |
|------|--------|------|
| 平台后台 **Namespaces** 页 / Admin API | 平台管理员 | **正式入口**。`POST /api/admin/namespaces` 需 admin API Key |
| 平台启动 bootstrap | 平台进程（`:8081`） | 见下文「平台 bootstrap」；仅保证当前加载的 Registry 对应 namespace 已入库 |
| App Worker / SDK | — | **无**自助创建能力。Worker 只上报 DeploymentContract（Redis 心跳），不写平台 namespace 表 |

#### 开发前检查清单

1. **在平台登记 namespace**（生产 / 共享环境）：管理员在 http://127.0.0.1:8081/ → **Namespaces** 新建，ID 与后续 `activities-registry.yaml` 的 `namespace` **完全一致**。
2. **配置 API Key 权限**：非 admin Key 只能访问 `platform_api_key_namespace` 中授权的 namespace；App 的 `flowfoundry.platform.api-key` 须能访问该 namespace。admin Key（如本地 `local-admin-key`）可访问全部。
3. **App 侧声明同一名字**：`activities-registry.yaml` 的 `namespace` / `defaultTaskQueue`，以及 `flowfoundry.platform.namespace`（或 `FLOWFOUNDRY_NAMESPACE`）与平台登记值一致。
4. **Temporal 物理 namespace**：生产环境还需在 Temporal 集群注册同名 namespace（见 [production-deployment.md](./production-deployment.md)）；本地 Docker 栈通常已由脚本处理示例 namespace。

#### 平台 bootstrap 是什么

指 **flowfoundry-core（平台 `:8081`）启动完成后**自动执行的初始化，不是 App 启动逻辑。

实现类：`NamespaceBootstrapRunner`（监听 `ApplicationReadyEvent`）。它读取平台当前加载的业务 `activities-registry.yaml` 的 `namespace`，若平台库中尚无该记录，则调用 `ensureRegistered` 写入一条。

因此本地联调时，若用 `ACTIVITY_REGISTRY_PATH=.../your-registry.yaml` 重启平台，常会「跟着 Registry 自动出现」对应 namespace，不必每次手点「新建」。这是**平台侧便利**，不代表 App 可以随意扩容 namespace。

```text
正式环境推荐顺序：
  管理员创建 Namespace → 授权 API Key → App 按同名写 Registry / 起 Worker

本地联调常见顺序：
  写好 activities-registry.yaml → ACTIVITY_REGISTRY_PATH=... redeploy 平台
  → 平台 bootstrap 自动 ensureRegistered → 再启动 App Worker
```

**不要混淆**：

| 动作 | 是否创建平台 namespace |
|------|----------------------|
| 在 yaml 里写 `namespace: my-scenario` | 否（仅声明） |
| App 启动并发布 DeploymentContract | 否（只告诉平台「我在该 namespace 上干活」） |
| 平台 bootstrap / 管理员后台创建 | 是 |

统一模型细节见 [service-urls.md](./service-urls.md#namespace统一模型)。

### 4.6 前端与 BFF 约束

| 场景 | 正确做法 |
|------|----------|
| 拉取 public-config | `GET /app/api/flowfoundry/platform/public-config`（BFF 代理） |
| 建模器 `apiBase` | 同源 BFF 前缀，如 `/app/api/flowfoundry` |
| 外部系统按已保存版本启动 | 平台 `POST /api/workflows/{workflowId}/versions/{version}/run`（API Key + Namespace；仅 `active`） |
| 业务系统触发 Run（自有封装） | 调 App 自有 API → 内部 `FlowFoundryPlatformClient.startWorkflowVersion()` 或 `runFlow()` |
| 平台 API Key | 环境变量注入 App，**不下发浏览器** |

**按已保存版本启动（对外推荐）**：

```http
POST /api/workflows/{workflowId}/versions/{version}/run
X-Api-Key: <key>
X-Platform-Namespace: <namespace>
Content-Type: application/json

{ "input": { }, "businessKey": "optional", "runWorkflowId": "optional-temporal-id" }
```

约束：definition 与 version 均为 `active`；`runSource` 固定为 `production`。建模器联调仍用 `POST /api/flows/run`（可带完整 DSL + `web-modeler`）。

**SDK 公开 API 入口**（Worker）：

| 类型 | 类 |
|------|-----|
| 启动注解 | `@EnableFlowFoundryWorker` |
| Activity 路由 | `BusinessActivityRouter`、`DualModeActivityHandler` |
| Worker 注册 | `TemporalWorkerExtension` |
| 幂等 | `IdempotentActivityExecutor` |

**SDK 公开 API 入口**（Client）：

| 类型 | 类 |
|------|-----|
| 客户端 | `FlowFoundryPlatformClient` |
| 配置 | `FlowFoundryPlatformProperties`、`@EnableFlowFoundryPlatformClient` |
| BFF（可选） | SDK 内置 `FlowFoundryPlatformBffController` |

---

## 5. 标准开发流程（推荐顺序）

### 阶段 A：环境与基线

1. **平台侧基础设施**（在 flowfoundry 平台仓库或共享 Docker 环境）：

   ```bash
   ./scripts/local-dev.sh up          # Temporal + Redis + Postgres
   ./scripts/redeploy-worker.sh       # 平台 :8081
   ```

2. **确认 Namespace 已就绪**（见 [§4.5](#45-namespace-前置条件必读)）：

   - **生产 / 共享环境**：管理员先在平台 **Namespaces** 页创建目标 namespace，并为 App 使用的 API Key 授权该 namespace。
   - **本地联调**：下一步用 `ACTIVITY_REGISTRY_PATH` 指向你的 Registry 后重启平台即可；平台 bootstrap 会按 yaml 中的 `namespace` 自动 `ensureRegistered`。也可先在后台手动新建，再启动 App。

3. **指向你的 Registry**（平台加载业务 Activity 定义）：

   ```bash
   ACTIVITY_REGISTRY_PATH=file:/absolute/path/to/my-scenario-app/config/activities-registry.yaml \
     ./scripts/redeploy-worker.sh
   ```

4. **启动你的 Worker**（在 App 仓库；`FLOWFOUNDRY_NAMESPACE` / Registry `namespace` 须与平台登记一致）：

   ```bash
   mvn -DskipTests package
   java -jar target/my-scenario-app-*.jar --server.port=8082
   ```

5. **联调入口**：http://127.0.0.1:8081/（建模器，右上角切换到你的 namespace）；Temporal UI：http://127.0.0.1:8080/

### 阶段 B：业务能力设计

在写代码前，先为每个 **Service Task 节点** 定义一张「能力卡片」：

| 字段 | 说明 |
|------|------|
| `id` | Registry 中的 `activityType`（kebab-case，如 `execute-call-round`） |
| 业务名称 | 画布显示名 |
| 输入 | 结构化字段（类型、是否必填） |
| 输出 | 供下游节点 / 网关条件使用 |
| 超时与重试 | 可恢复 vs 不可恢复错误 |
| 幂等 | `keyPattern` + TTL |
| 副作用 | 是否调外部系统、是否可 stub |

**设计原则**：

- 一个 Activity = 一个稳定业务能力（不要一个节点里塞整条子流程）。
- 长等待（外呼回调、打标完成）用 **Service Task + 轮询/wait Activity**，不要用画布循环模拟。
- 技术异常在 Activity 内处理；画布只表达少量业务分支（如「需主管复核 → Human Task」）。

### 阶段 C：注册 Activity（Registry）

在 App 仓库的 `config/activities-registry.yaml` 中声明：

```yaml
version: "1.0"
namespace: your-scenario
defaultTaskQueue: your-scenario

activities:
  - id: load-campaign
    name: 加载活动
    taskQueue: your-scenario
    timeout: 60s
    retry:
      maximumAttempts: 5
    input:
      - { name: campaignId, type: string, required: true }
    output:
      - { name: totalContacts, type: integer }
    idempotency:
      keyPattern: "{campaignId}:load-campaign"
      ttl: 24h
```

**注意**：

- 平台 core 已提供 `script-runtime`、`human-task`，运行时与业务 Registry **合并**展示，无需重复注册。
- 修改 Registry 后须让平台重新加载（`redeploy-worker.sh` 或设置 `ACTIVITY_REGISTRY_PATH` 后重启 :8081）；若改了 Activity 实现则重启你的 Worker。

### 阶段 D：实现 Activity 代码

按以下结构组织（复制 `examples/ai-collection-strategy` 即可）：

```text
YourScenarioApplication.java      # main，@EnableFlowFoundryWorker
YourActivities.java               # @ActivityInterface，@ActivityMethod(name = "registry-id")
YourActivitiesImpl.java           # 生产实现（@Component，真实副作用）
YourActivitiesStub.java           # 联调桩（长等待立即返回、无外部调用）
YourActivityRouter.java           # implements BusinessActivityRouter，extends DualModeActivityHandler
YourWorkerExtension.java          # implements TemporalWorkerExtension
model/                            # 入出参 POJO
service/                          # 可选：领域服务
```

**SDK 公开 API 入口**（均来自 `flowfoundry-sdk` / `flowfoundry-sdk-client`）：

| 类型 | 类 |
|------|-----|
| 启动注解 | `@EnableFlowFoundryWorker` |
| Activity 路由 | `BusinessActivityRouter`、`DualModeActivityHandler` |
| Worker 注册 | `TemporalWorkerExtension` |
| 幂等 | `IdempotentActivityExecutor` |
| 平台 HTTP | `FlowFoundryPlatformClient` |
| 联调上下文 | `ActivityExecutionContext`、`RunSource` |

**Router 要点**（`AiCollectionActivityRouter` 为范本）：

1. `supports(activityType)` 返回本模块支持的 id 集合。
2. `execute(activityType, input)` 用 `switch` 分发到接口方法。
3. 通过 `selectActivities(input)` 在 **Impl / Stub** 间切换。

**Temporal 接口命名**：`@ActivityMethod(name = "...")` 必须与 Registry 的 `id` **完全一致**。

### 阶段 E：构建与本地运行

```bash
# 在 App 仓库
mvn -DskipTests package
java -jar target/my-scenario-app-*.jar --server.port=8082

# 健康检查
curl --noproxy '*' http://127.0.0.1:8082/actuator/health
```

`pom.xml` 将 `config/activities-registry.yaml` 打进 classpath（见示例 `<build><resources>`）。

### 阶段 F：在建模器上编排流程

1. 打开 http://127.0.0.1:8081/ ，进入 **Modeler** 视图。
2. 创建 Workflow，从 Palette 拖入节点（见 [entity-naming.md](./entity-naming.md) §2.1）。
3. **保存 / 发布**版本。
4. 在 **Runtime** 视图点击 **Run** 做联调。

**编译校验**：Service Task 的 `activityType` 必须在 Registry 中存在，否则 compile 失败。

### 阶段 G：联调与 RunSource

| 运行方式 | runSource | Activity 行为 |
|----------|-----------|---------------|
| 建模器 Runtime **Run** | `web-modeler` | Workflow 真实执行；Activity 走 **Stub** |
| 对外 `POST /api/flows/run` | `production` | 始终 **Impl** |

**排查路径**：

- Worker 日志：App 进程 stdout / 容器日志
- Temporal UI：http://127.0.0.1:8080/
- 平台健康：`curl --noproxy '*' http://127.0.0.1:8081/actuator/health`

### 阶段 H：测试

| 层级 | 做法 |
|------|------|
| 单元测试 | Router 分发、Impl 业务逻辑（参考 `AiCollectionActivityRouterTest`） |
| 场景冒烟 | App 仓库内 `mvn test`；可选 `./scripts/smoke-test.sh` 对接 staging 平台 |
| 解释器 E2E | 平台仓库 `./scripts/runtime-test.sh`（验证动态 FlowInterpreter） |
| 建模器 E2E | 平台仓库 `npm run test:e2e`（`:4173`，**不替代** 8081 联调） |

### 阶段 I：部署

- **Worker 镜像**：App 仓库 `Dockerfile` 构建，部署到 K8s（与平台 Worker 分离）。
- **Registry**：ConfigMap 或 `file:` 挂载到**平台 Pod** 与 **Worker Pod**（路径一致）。
- **生产 RunSource**：`production`；Stub 仅用于建模器联调。

详见 [production-deployment.md](./production-deployment.md) 与 [flowfoundry-sdk-design.md §7](./flowfoundry-sdk-design.md#7-ci--发布)。

---

## 6. 日常改代码 checklist

**改 App 仓库（Activity / Registry / Worker）**：

```bash
mvn -DskipTests package && java -jar target/*.jar ...
curl --noproxy '*' http://127.0.0.1:8082/actuator/health   # UP
```

**改 Registry yaml**：

```bash
# 在平台侧重启或 redeploy，使 :8081 重新加载
ACTIVITY_REGISTRY_PATH=file:/path/to/your/config/activities-registry.yaml \
  ./scripts/redeploy-worker.sh
```

**改平台 core（仅平台团队）**：在 flowfoundry 平台仓库执行 `redeploy-worker.sh`，并确认 SDK 版本与 App `pom.xml` 对齐。

---

## 7. 新增 Activity 速查清单

- [ ] 平台已登记目标 namespace，且 API Key 已授权（见 [§4.5](#45-namespace-前置条件必读)）
- [ ] 在 `config/activities-registry.yaml` 增加条目（`namespace` 与平台登记一致）
- [ ] 在 `XxxActivities` 接口增加 `@ActivityMethod(name = "同一 id")`
- [ ] 在 `XxxActivitiesImpl` / `XxxActivitiesStub` 实现方法
- [ ] 在 `XxxActivityRouter.supports` / `execute` 中注册分发
- [ ] 补充单元测试
- [ ] 平台侧重载 Registry；重启 Worker
- [ ] 建模器切换到该 namespace 后，Service Task 下拉验证 + Runtime Run 跑通

---

## 8. 两种交付路径

### 路径 1：画布驱动（主流）

```text
Registry → 建模器编排 → 编译 ExecutionPlan → FlowInterpreterWorkflow 执行
```

开发者主要交付 **Registry + Activity**；流程结构由画布维护并存入平台 PostgreSQL。

### 路径 2：代码驱动（FDE / 深度定制）

```text
手写 Workflow Interface/Impl + Activity
  → 测试与 Review
  → 登记 Registry（供后续画布复用）
  → WorkerExtension 注册
```

两条路径共享 **Activity Registry** 与 **DeploymentContract**（namespace、task queue），见 [detailed-design.md §3.7](./detailed-design.md#37-双入口的统一契约)。

---

## 9. 常见误区

| 误区 | 正确做法 |
|------|----------|
| 在 `pom.xml` 依赖 `flowfoundry-core` | import **`flowfoundry-sdk-bom`**，依赖 sdk + sdk-client |
| 前端 `fetch('http://:8081/api/...')` | 前端只调 **App BFF**；后端用 **`FlowFoundryPlatformClient`** |
| 在 `flowfoundry-core` 写业务 Activity | 写在你的 **App 仓库** |
| 改 Registry 后只重启 Worker | 平台 :8081 也须加载新 Registry |
| 使用 `@EnableFlowFoundry` 启动业务 Worker | 使用 **`@EnableFlowFoundryWorker`** |
| SDK 版本与平台不一致 | BOM 的 `flowfoundry.version` 对齐平台 minor 版本 |
| App 在 yaml 写新 `namespace` 就当已开通 | 须先由**平台管理员创建**（或本地靠平台 bootstrap）；App **不能**自助注册 |
| Worker 启动 = 平台 namespace 已登记 | Worker 只发 DeploymentContract；namespace 表由平台管理 |
| 用未授权该 namespace 的 API Key 调平台 | 为 Key 授权目标 namespace，或使用 admin Key |
| 画布拆大量细粒度节点追求极速串行 | 见 [§2](#2-适用场景与编排延迟预期)：poll 模型无法保证节点间 &lt; 1ms；应合并 Activity 或用手写 Workflow |

---

## 10. 扩展阅读：节点如何映射到 Temporal

| 画布元素 | 运行时 |
|----------|--------|
| Service Task | `ACTIVITY`，Registry `activityType` → Router |
| Human Task | `ACTIVITY` + `human-task`；managed 时 Workflow 等 Signal |
| Script Task | `ACTIVITY` + `script-runtime`（**平台 Worker** 执行） |
| Gateway | 解释器按 Safe FEEL 选边 |
| Intermediate Event (timer) | `TimerEvaluator` + `Workflow.newTimer` |
| Child Workflow | Temporal Child Workflow |

完整对照表见 [entity-naming.md](./entity-naming.md)、[timer-design.md](./timer-design.md)、[child-workflow-design.md](./child-workflow-design.md)。

---

## 11. 在本仓库内开发示例（平台贡献者）

若你正在维护 **flowfoundry 平台仓库**内的官方示例，开发与联调方式与独立仓库相同，区别仅在于：

- 示例位于 `examples/ai-collection-strategy/`。
- 使用 `./scripts/redeploy-app.sh` 一键构建并启动 :8082。
- 示例 `pom.xml` 仅依赖 `flowfoundry-sdk` + `flowfoundry-sdk-client`（版本由 BOM 管理）。

```bash
./scripts/redeploy-worker.sh
SCENARIO=ai-collection-strategy ./scripts/redeploy-app.sh
```

---

## 12. 下一步

1. 阅读 [flowfoundry-sdk-design.md](./flowfoundry-sdk-design.md) 了解 SDK 分层与 BFF 边界
2. 若只需 Worker、不需独立 App：阅读 [plugin-development-guide.md](./plugin-development-guide.md)
3. 通读示例：`examples/ai-collection-strategy/`
4. 复制示例为你的独立 App 仓库，按 §7 清单新增试点 Activity
5. 本地跑通：建模器 Run → Temporal UI 看 History

有问题先查 [local-development.md](./local-development.md) 与 [service-urls.md](./service-urls.md)；平台行为细节以 [detailed-design.md](./detailed-design.md) 为准。
