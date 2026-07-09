# 本地服务地址（权威对照表）

改文档或脚本时**以本表为准**。生产环境地址由集群 Ingress / Service 决定，见 [production-deployment.md](./production-deployment.md)。

## 必记三条

| 用途 | 地址 | 说明 |
|------|------|------|
| **平台管理（flowfoundry-core）** | http://127.0.0.1:8081/ | 建模器、Workflow API、Activity Registry（含业务 yaml）；`redeploy-worker.sh` |
| **业务 Worker（flowfoundry-app）** | http://127.0.0.1:8082/ | Temporal Worker + iframe 壳；**无** `/api/*`；`redeploy-app.sh` |
| **iframe 嵌入建模器** | http://127.0.0.1:8081/modeler/embed.html | 由 :8082 业务壳 iframe 引用 |
| **业务壳页面** | http://127.0.0.1:8082/app/workflow-admin.html | 催收场景演示 |
| **Temporal UI** | http://127.0.0.1:8080/ | Docker 容器 `temporal-ui` |
| **健康检查** | http://127.0.0.1:8081/actuator/health 、`:8082` | `curl` 需加 `--noproxy '*'` |

本地调试架构：Docker 跑 Postgres / Redis / Temporal，应用在宿主机。详见 [local-development.md](./local-development.md)。

## 其他地址

| 用途 | 地址 |
|------|------|
| Temporal gRPC | 127.0.0.1:7233 |
| Temporal namespace（示例业务模块，物理隔离） | `call-campaign` |
| Temporal namespace（系统管理，目标设计） | `flowfoundry-system` |
| Task queue（示例业务模块） | `ai-collection-strategy` |
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

## 两个「namespace」概念（务必区分）

FlowFoundry 有两个都叫 namespace、但语义完全不同的概念，二者**完全解耦**，详见 [detailed-design.md §11](./detailed-design.md#11-namespace-体系设计目标设计)：

| 概念 | 含义 | 取值来源 |
|------|------|----------|
| **逻辑 namespace**（用户可见的一等概念） | workflow 定义归属、run logs、API Keys 的统一隔离/RBAC 单位 | `workflow_definition.namespace`，请求头 `X-Platform-Namespace`（右上角 Namespace 选择器） |
| **Temporal namespace**（物理，内部） | workflow 执行、run history 的物理隔离边界 | 使用方部署契约声明（`temporal.namespace`），值由 app 自定 |

> 「tenant / tenantId」是逻辑 namespace 的旧称，已统一为 **namespace**（后端 `X-Tenant-Id` 仅作弃用读兼容）。

**目标隔离模型**：`flowfoundry-system`（core 管理 + 建模器调试运行的固定 Temporal namespace） + 每个 flowfoundry-app 使用方各自独立的业务 Temporal namespace（互不可见）。

### 逻辑 namespace（一等概念）

- **统一作用域**：workflows、run logs、API Keys 均按右上角选中的**单个 namespace** 过滤（管理员可在 API Keys / 审计日志切换「所有 Namespace」）。
- **请求头（规范）**：`X-Platform-Namespace: <namespace>`；兼容旧客户端：`X-Tenant-Id`（弃用，同值读兼容）。
- **API Key 授权**：非管理员 Key 在 `platform_api_key_namespace` 中配置可访问的 namespace 列表。
- **建模器**：顶栏 Namespace 下拉切换当前 namespace；新建 Workflow 归属当前选中 namespace；Workflow 列表展示 namespace 列。
- **上下文 API**：`GET /api/workflows/context` 返回 `{ namespace, allowedNamespaces, namespaceHeader }`。

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
