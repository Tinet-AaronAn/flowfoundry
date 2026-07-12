# 本地调试

本文说明在本仓库日常改代码、联调建模器、验证 FlowFoundry 的标准流程。**静态页面与 Java 代码均打包进 Spring Boot JAR**，没有热更新；改完代码后必须重新打包并重启 `:8081` 上的应用。

生产环境部署见 [production-deployment.md](./production-deployment.md)。服务地址权威表见 [service-urls.md](./service-urls.md)。

---

## 架构（与生产一致）

本地调试采用 **Docker 基础设施 + 宿主机双进程**（平台与 Worker 分离，同生产 K8s 模型）：

| 组件 | 运行方式 | 端口 | 角色 |
|------|----------|------|------|
| PostgreSQL / Redis / Temporal | Docker | 5432 / 6379 / 7233 | 基础设施 |
| Temporal UI | Docker | 8080 | 运维 UI |
| **flowfoundry-core** | 宿主机 `java -jar` | **8081** | 平台 HTTP（建模器、API、Registry） |
| **示例 Worker App** | 宿主机 `java -jar` | **8082** | `examples/ai-collection-strategy`：Temporal Worker + iframe 业务壳 |

- 平台启动：`./scripts/redeploy-worker.sh`（默认加载 `SCENARIO=ai-collection-strategy` 的业务 Registry）
- Worker 启动：`./scripts/redeploy-app.sh`（`flowfoundry.run-mode=worker`，不提供 `/api/*`）

---

## 测试入口

| 用途 | 地址 |
|------|------|
| **建模器 / 联调主页面** | http://127.0.0.1:8081/ |
| **业务 iframe 壳** | http://127.0.0.1:8082/app/workflow-admin.html |
| 健康检查 | `:8081` 与 `:8082` 的 `/actuator/health` |
| **Temporal UI** | http://127.0.0.1:8080/ |
| Playwright E2E（独立静态服务） | http://127.0.0.1:4173（`npm run test:e2e` 自动拉起） |

人工测试建模器时，**始终使用 8081**，不要用 E2E 的 4173 端口。

在 **Runtime（运行联调）** 视图点击 **Run** 时，前端会发送 `runSource=web-modeler` 与请求头 `X-FlowFoundry-Client: web-modeler`，Temporal 执行真实 Workflow，Activity 走桩实现（无外部副作用）。

---

## 代码位置

| 类型 | 路径 |
|------|------|
| 建模器 HTML | `flowfoundry-core/src/main/resources/static/index.html` |
| 前端 JS / CSS | `flowfoundry-core/src/main/resources/static/assets/` |
| 平台 API / 解释器 | `flowfoundry-core/src/main/java/com/tinet/flowfoundry/` |
| 可运行入口 | `examples/ai-collection-strategy/` |
| 业务 Activity | `examples/ai-collection-strategy/` |
| 插件 runner | `flowfoundry-sdk/flowfoundry-plugin-runner/` |
| 数据库迁移 | `flowfoundry-core/src/main/resources/db/migration/` |
| E2E 用例 | `e2e/` |

---

## 首次启动 / 完整重启

依赖：JDK 17、Maven、Docker（OrbStack）、Temporal CLI（`check-progress` 与健康检查用）。

```bash
chmod +x scripts/local-dev.sh scripts/check-progress.sh scripts/redeploy-worker.sh

# 停掉旧栈（若有）并重新拉起
./scripts/local-dev.sh down
./scripts/local-dev.sh up
./scripts/check-progress.sh     # 期望 ALL_GREEN
```

`local-dev.sh up` 会依次：

1. `docker compose up -d` 启动 Postgres、Redis、Temporal、Temporal UI
2. 若存在 Docker worker 容器则停止（避免与宿主机抢 8081）
3. `mvn package` 并启动宿主机 JAR（等价于 `redeploy-worker.sh`）

浏览器打开：**http://127.0.0.1:8081/**

---

## 改代码后的标准流程（必做）

每次修改 `flowfoundry-core/`、`flowfoundry-sdk/` 或 `examples/` 下任意文件后：

```bash
./scripts/redeploy-worker.sh    # 平台 :8081（含业务 Activity Registry）
./scripts/redeploy-app.sh       # Worker :8082（仅当改了业务 Activity / Worker 代码）
```

`redeploy-*.sh` 通过 `scripts/java-daemon.py` 双 fork 启动 Java，进程会挂到 launchd（PPID=1），**不会**随 Cursor Agent 临时 shell 退出而消失。若端口仍不可用，在本机终端执行 `./scripts/redeploy-all.sh` 或 `./scripts/check-progress.sh` 排查。

脚本会 `mvn package`、释放对应端口、重启 JAR，并等待 `/actuator/health` 为 UP。

完成后在浏览器**强制刷新**：**http://127.0.0.1:8081/**（建模器与 Registry 以平台为准）

---

## 常用命令

| 命令 | 说明 |
|------|------|
| `./scripts/local-dev.sh up` | Docker 基础设施 + 宿主机应用 |
| `./scripts/local-dev.sh down` | 停止宿主机应用 + `docker compose down` |
| `./scripts/local-dev.sh infra` | 仅启动 Docker 基础设施 |
| `./scripts/local-dev.sh status` | 运行 `check-progress.sh` |
| `./scripts/redeploy-worker.sh` | 重启平台 :8081（含业务 Registry） |
| `./scripts/redeploy-app.sh` | 重启业务 Worker :8082 |
| `./scripts/plugin-runtime-dev.sh` | 构建 runner 镜像并以 **插件运行时模式** 重启平台（见下节） |
| `./scripts/build-ai-collection-plugin.sh` | 构建官方示例插件 jar（`-plugin` classifier） |
| `./scripts/build-plugin-runner-image.sh` | 构建 `flowfoundry-plugin-runner:local` 镜像 |

---

## 插件运行时模式（P2/P3，可选）

与「平台 :8081 + 业务 Worker :8082」并存。启用后业务 Activity / typed workflow 由 **K8s 内 plugin runner Pod** 承载，平台通过 `KubernetesRuntime` 对账 Deployment。

**插件开发完整指南**：[plugin-development-guide.md](./plugin-development-guide.md)（打包、描述符、上传、验收）。

**前提**：本机 Docker Desktop / OrbStack 已启用 Kubernetes；Temporal / Redis 等基础设施已 `up`。

```bash
./scripts/local-dev.sh infra          # 若尚未启动
./scripts/plugin-runtime-dev.sh       # 构建 runner 镜像 + 以 FLOWFOUNDRY_PLUGIN_RUNTIME_ENABLED=true 部署 :8081
./scripts/build-ai-collection-plugin.sh

# 上传并启动（API Key 默认 local-admin-key）
PLUGIN_JAR=$(./scripts/build-ai-collection-plugin.sh)
curl -X POST -H "X-API-Key: local-admin-key" -F "file=@$PLUGIN_JAR" \
  http://127.0.0.1:8081/api/admin/plugins
curl -X POST -H "X-API-Key: local-admin-key" \
  http://127.0.0.1:8081/api/admin/plugins/ai-collection-strategy/1.0.4/start

# 观察
kubectl get deploy,pods -n flowfoundry-plugins
curl -H "X-API-Key: local-admin-key" \
  http://127.0.0.1:8081/api/admin/plugins/ai-collection-strategy/1.0.4
```

要点：

- 插件模式使用 `activities-registry-platform-plugin.yaml`（无内置业务 Activity）；业务 registry 来自已上传插件。
- Runner Pod 通过 `host.docker.internal` 访问本机 Temporal（`:7233`）、平台（`:8081`）与 Redis。
- 扩缩容：`PUT /api/admin/plugins/{id}/scale`，body `{"replicas":N}`。
- 管理页面：平台侧栏 **Plugins**（`modeler-plugins.js`）支持上传、启停、扩缩容、重载、日志查看。
- 若同时运行 `:8082` 业务 Worker，Temporal 上会出现重复 poller；联调插件时建议**不启动** `redeploy-app.sh`。

环境变量见 `application-flowfoundry-platform.yml` 中 `flowfoundry.plugins.runtime.*`。

## Docker 全栈（仅集成验证）

需要验证 **worker 容器化** 或 compose 环境变量时，才使用全栈 Docker（**不适合日常 UI 迭代**）：

```bash
./scripts/docker-stack.sh up        # 含 worker 容器，首次较慢
./scripts/runtime-test.sh           # FlowInterpreter E2E
./scripts/smoke-test.sh             # Demo 内置流程冒烟
```

日常建模器开发请始终用 `./scripts/local-dev.sh` + `./scripts/redeploy-worker.sh`。

---

## E2E 测试

```bash
npm run test:e2e
```

Playwright 对 `flowfoundry-core/src/main/resources/static` 起临时静态服务（`:4173`），**不会**自动更新 8081。

---

## 日志与排错

| 文件 / 命令 | 用途 |
|-------------|------|
| `.local/run/platform.log` | 平台（:8081）标准输出 |
| `.local/run/app.log` | Worker（:8082）标准输出 |
| `docker compose -f deploy/docker-compose.local.yml logs -f temporal` | Temporal 容器日志 |
| `./scripts/check-progress.sh` | 编译 + 依赖服务 + 8081 健康 |
| `curl --noproxy '*' http://127.0.0.1:8081/actuator/health` | 快速健康检查 |

### 常见错误

| 现象 | 处理 |
|------|------|
| `Connection to localhost:5432 refused` | 运行 `./scripts/local-dev.sh infra` 或 `up` |
| 8081 被占用 | `./scripts/redeploy-worker.sh` 会自动释放；或 `local-dev.sh down` |
| Temporal UI 502 | 确认 Docker Temporal 已起，使用 **8080** 而非 8233 |
| 改 `static/` 刷新无效 | 必须 `redeploy-worker.sh`，页面来自 JAR |

---

## 协作者 / AI Agent 约定

见 [AGENTS.md](../AGENTS.md)：修改平台或业务场景代码后，**必须**执行 `./scripts/redeploy-worker.sh`，验证健康检查后，再通知测试人员刷新 **http://127.0.0.1:8081/**。
