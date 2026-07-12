# FlowFoundry 仓库目录说明

## 总览

```text
flowfoundry/                          # 平台仓库（本仓库）
├── pom.xml                           # Maven 父工程
├── flowfoundry-sdk/                  # SDK 父模块（packaging=pom）
│   ├── flowfoundry-sdk-bom/          #    版本 BOM → GitHub Packages
│   ├── flowfoundry-sdk/              #    Worker 运行时
│   ├── flowfoundry-sdk-client/       #    平台 HTTP 客户端 + BFF
│   └── flowfoundry-plugin-runner/    #    插件 runner 宿主（K8s Pod 镜像）
├── flowfoundry-core/                 # ★ 平台内核（建模器 + API + 解释器 + 插件控制面）
├── examples/                         # ★ 官方示例（ai-collection-strategy）
├── docs/
├── scripts/
├── deploy/
└── e2e/
```

> **架构演进**：业务 App 在**独立 Git 仓库**开发；import **`flowfoundry-sdk-bom`**，依赖 sdk + sdk-client；平台 HTTP **仅经 SDK Client 从 App 后端发出**。本仓库 `examples/` 保留 1 个官方示例。详见 [flowfoundry-sdk-design.md](flowfoundry-sdk-design.md)。

**依赖关系：**

```text
flowfoundry-sdk-client  ──depends──►  flowfoundry-sdk
       ▲                                      ▲
       │                                      │ depends
flowfoundry-core                           examples/ai-collection-strategy

独立业务 App 仓库
  import flowfoundry-sdk-bom
  depends flowfoundry-sdk + flowfoundry-sdk-client
  运行时：SDK Client → flowfoundry-core (:8081)；Worker → Temporal
```

本地联调：**http://127.0.0.1:8081/**（平台）；示例 Worker **http://127.0.0.1:8082/**（Worker + BFF + iframe 壳）

**服务地址权威表**：[service-urls.md](service-urls.md)

---

## flowfoundry-sdk/（SDK 父模块）

发布至 **GitHub Packages**：`https://maven.pkg.github.com/Tinet-AaronAn/flowfoundry`

| 子模块 | Artifact | 用途 |
|--------|----------|------|
| `flowfoundry-sdk-bom` | `com.tinet.flowfoundry:flowfoundry-sdk-bom` | BOM；业务 App `dependencyManagement` import |
| `flowfoundry-sdk` | `com.tinet.flowfoundry:flowfoundry-sdk` | Worker：`@EnableFlowFoundryWorker`、Router、Registry、幂等 |
| `flowfoundry-sdk-client` | `com.tinet.flowfoundry:flowfoundry-sdk-client` | `FlowFoundryPlatformClient`、BFF 控制器、共享 contract DTO |
| `flowfoundry-plugin-runner` | `com.tinet.flowfoundry:flowfoundry-plugin-runner` | 插件宿主 JAR + Docker 镜像；平台 K8s Deployment 使用 |

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
| `api/` | REST：`/api/flows/*`、`/api/workflows/*`、`/api/admin/plugins` |
| `plugin/` | 插件包校验、存储、生命周期、`PluginReconcileService` |
| `workflow/` | 流程定义 PostgreSQL 持久化 |
| `temporal/` | Temporal Worker 启动、平台解释器 Worker |
| `config/` | Spring 配置 |
| `boot/` | `FlowFoundryCoreApplication`（`run-mode=platform`） |
| `src/main/resources/static/` | **建模器** HTML/JS/CSS |

平台可独立启动：`mvn -pl flowfoundry-core -am package && java -jar flowfoundry-core/target/flowfoundry-core-*-exec.jar`

**依赖**：`flowfoundry-sdk`。

---

## examples/（官方示例）

`packaging=pom` 聚合 `ai-collection-strategy`。同一业务代码支持两种交付：

| 模式 | 产物 | 联调 |
|------|------|------|
| Worker App | `ai-collection-strategy-demo-1.0.4.jar` | `./scripts/redeploy-app.sh`（:8082） |
| 插件包 | `ai-collection-strategy-demo-1.0.4-plugin.jar` | `./scripts/plugin-runtime-dev.sh` + 页面上传 |

插件开发见 [plugin-development-guide.md](plugin-development-guide.md)。

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

构建 Worker：`mvn -pl examples/ai-collection-strategy -am package`  
构建插件：`mvn -pl examples/ai-collection-strategy -Pplugin -am package` 或 `./scripts/build-ai-collection-plugin.sh`

详见 [examples/ai-collection-strategy/README.md](../examples/ai-collection-strategy/README.md)。

---

## 独立业务 App 仓库（业务团队）

业务交付物**不在本仓库**。标准结构见 [workflow-development-guide.md](workflow-development-guide.md) §4 与 [flowfoundry-sdk-design.md §4](flowfoundry-sdk-design.md#4-独立业务-app-仓库标准结构)。

也可打成**插件包**由平台托管 Worker，见 [plugin-development-guide.md](plugin-development-guide.md)。

---

## 改代码指南

| 目标 | 改哪里 |
|------|--------|
| 解释器 / 编译器 / 画布 / 平台 API | `flowfoundry-core/` |
| SDK 公开 API / Worker 扩展点 / plugin-runner | `flowfoundry-sdk/` |
| 实体命名与分层定义 | [entity-naming.md](entity-naming.md) |
| **业务场景 / 流程软件开发** | [workflow-development-guide.md](workflow-development-guide.md)（独立 App 仓库） |
| **插件开发（平台托管 Worker）** | [plugin-development-guide.md](plugin-development-guide.md) |
| 官方示例 | `examples/<场景>/` |
| 本地联调 | `./scripts/redeploy-worker.sh`（:8081）+ `./scripts/redeploy-app.sh`（:8082 示例） |
