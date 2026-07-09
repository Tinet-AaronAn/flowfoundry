# 本地服务地址（权威对照表）

改文档或脚本时**以本表为准**。生产环境地址由集群 Ingress / Service 决定，见 [production-deployment.md](./production-deployment.md)。

## 必记三条

| 用途 | 地址 | 说明 |
|------|------|------|
| **平台管理（flowfoundry-core）** | http://127.0.0.1:8081/ | 建模器、Workflow API、Activity Registry（含业务 yaml）；`redeploy-worker.sh` |
| **业务 Worker（flowfoundry-app）** | http://127.0.0.1:8082/ | Temporal Worker + iframe 壳 + **App BFF**；**无**直连平台的 `/api/*` |
| **App BFF（平台 API 代理）** | http://127.0.0.1:8082/app/api/flowfoundry/ | 业务前端唯一平台 API 入口；后端经 `flowfoundry-sdk-client` 转发至 :8081 |
| **iframe 嵌入建模器** | http://127.0.0.1:8081/modeler/embed.html | 由 :8082 业务壳 iframe 引用 |
| **业务壳页面** | http://127.0.0.1:8082/app/workflow-admin.html | 催收场景演示 |
| **Temporal UI** | http://127.0.0.1:8080/ | Docker 容器 `temporal-ui` |
| **健康检查** | http://127.0.0.1:8081/actuator/health 、`:8082` | `curl` 需加 `--noproxy '*'` |

本地调试架构：Docker 跑 Postgres / Redis / Temporal，应用在宿主机。详见 [local-development.md](./local-development.md)。

## 其他地址

| 用途 | 地址 |
|------|------|
| Temporal gRPC | 127.0.0.1:7233 |
| Temporal namespace（示例业务 App） | `ai-collection-strategy` |
| Task queue（来自 Activity Registry `defaultTaskQueue`） | `ai-collection-strategy` |
| PostgreSQL | 127.0.0.1:5432 / 库 `flowfoundry` / 用户 `flowfoundry` |
| Redis | 127.0.0.1:6379 |
| Playwright E2E 静态页 | http://127.0.0.1:4173（**不是**联调入口） |
| QuantumBPM 参考 DevServer | http://127.0.0.1:9060（可选，`docker compose --profile full`） |

## 代码与配置路径

| 内容 | 路径 |
|------|------|
| 平台内核 | `flowfoundry-core/` |
| 场景启动类（催收示例） | `flowfoundry-app/modules/ai-collection-strategy/.../AiCollectionStrategyApplication.java` |
| 场景 JAR 产物 | `flowfoundry-app/modules/ai-collection-strategy/target/ai-collection-strategy-demo-1.0.3.jar` |
| 平台共享配置 | `flowfoundry-core/src/main/resources/application-flowfoundry-platform.yml` |
| 建模器静态资源 | `flowfoundry-core/src/main/resources/static/` |
| 前端 SDK | `flowfoundry-core/.../static/assets/js/flowfoundry-modeler-sdk.js` |
| 平台 SDK 注解 | `EnableFlowFoundry`（平台）、`EnableFlowFoundryWorker`（业务 Worker） |
| 数据库迁移 | `flowfoundry-core/src/main/resources/db/migration/` |
| Activity 注册表（催收） | `flowfoundry-app/modules/ai-collection-strategy/config/activities-registry.yaml` |

## Namespace（统一模型）

**一个 namespace = FlowFoundry 逻辑隔离 + Temporal 物理隔离 + Activity Registry 归属**，三者同名、一一对应。

| 维度 | 说明 |
|------|------|
| **Workflow 存储** | `workflow_definition.namespace` |
| **Run / Temporal** | Workflow 在哪个 namespace 创建，Run 就在同一 Temporal namespace |
| **Activity Registry** | `activities-registry.yaml` 的 `namespace` + `defaultTaskQueue` |
| **Worker** | 轮询 Registry 的 `namespace` + `defaultTaskQueue`（App 不再单独配 `temporal.task-queue`） |
| **请求头** | `X-Platform-Namespace`（建模器右上角切换） |

**Activity 可见性**：当前 namespace 的 Service Task 下拉 = **Core 全局**（`script-runtime`、`human-task`）+ **本 namespace 业务 Activity**。切换 namespace 会重新加载 Activity 列表。

**Core Activity 例外**：`script-runtime` / `human-task` 由平台 Worker 在 `flowfoundry-platform` 队列执行，可在任意 namespace 的 workflow 中使用。

本地示例 App namespace：`ai-collection-strategy`（见 `activities-registry.yaml`）。

### 逻辑 namespace 操作

- **统一作用域**：workflows、run logs、API Keys 均按右上角选中的**单个 namespace** 过滤。
- **请求头（规范）**：`X-Platform-Namespace: <namespace>`；兼容旧客户端：`X-Tenant-Id`。
- **API Key 授权**：非管理员 Key 在 `platform_api_key_namespace` 中配置可访问的 namespace 列表。
- **建模器**：顶栏 Namespace 下拉切换当前 namespace；新建 Workflow 归属当前选中 namespace。
- **上下文 API**：`GET /api/workflows/context` 返回 `{ namespace, allowedNamespaces, namespaceHeader }`。
- **按已保存版本启动（对外）**：`POST /api/workflows/{workflowId}/versions/{version}/run`（需 API Key + Namespace；仅 `active`；`runSource=production`）。

### Namespace 如何产生（App 开发者）

| 方式 | 说明 |
|------|------|
| **管理员创建（正式）** | 平台 **Namespaces** 页或 Admin API；App **不能**自助注册 |
| **平台 bootstrap（本地便利）** | 平台 `:8081` 启动时，`NamespaceBootstrapRunner` 按当前加载的业务 Registry 的 `namespace` 做 `ensureRegistered` |
| **App Worker 上报** | 仅 Redis DeploymentContract（`namespace` + `taskQueue`），**不**写入平台 namespace 表 |

App 开发前置与检查清单见 [workflow-development-guide.md §3.5](./workflow-development-guide.md#35-namespace-前置条件必读)。

## 本地调试命令

```bash
./scripts/local-dev.sh up          # Docker 基础设施 + 平台 :8081 + 业务 :8082
./scripts/redeploy-worker.sh         # 仅重启平台 :8081
./scripts/redeploy-app.sh          # 仅重启业务 :8082
./scripts/check-progress.sh        # 健康检查
```

## 健康检查

```bash
./scripts/check-progress.sh
curl --noproxy '*' http://127.0.0.1:8081/actuator/health
```
