# 本地调试

本文说明在本仓库日常改代码、联调建模器、验证 FlowFoundry 的标准流程。**静态页面与 Java 代码均打包进 Spring Boot JAR**，没有热更新；改完代码后必须重新打包并重启 `:8081` 上的应用。

生产环境部署见 [production-deployment.md](./production-deployment.md)。服务地址权威表见 [service-urls.md](./service-urls.md)。

---

## 架构（推荐）

本地调试采用 **Docker 基础设施 + 宿主机应用**：

| 组件 | 运行方式 | 端口 |
|------|----------|------|
| PostgreSQL | Docker | 5432 |
| Redis | Docker | 6379 |
| Temporal | Docker | 7233 |
| Temporal UI | Docker | 8080 |
| FlowFoundry（core + app 场景 JAR） | **宿主机** `java -jar` | 8081 |

`flowfoundry-core` 与 `flowfoundry-app` 打成一个 fat JAR（默认 `ai-collection-strategy-demo`），在宿主机运行以便改代码后快速 `redeploy`（约 10–15 秒），无需每次重建 Docker 镜像。

---

## 测试入口

| 用途 | 地址 |
|------|------|
| **建模器 / 联调主页面** | http://127.0.0.1:8081/ |
| 健康检查 | http://127.0.0.1:8081/actuator/health |
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
| 可运行入口 | `flowfoundry-app/modules/ai-collection-strategy/` |
| 业务 Activity | `flowfoundry-app/modules/` |
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

每次修改 `flowfoundry-core/`、`flowfoundry-app/` 或 `flowfoundry-app/modules/` 下任意文件后：

```bash
./scripts/redeploy-worker.sh
# 或：./scripts/local-dev.sh redeploy
```

脚本会 `mvn package`、释放 8081、重启 JAR，并等待 `/actuator/health` 为 UP。

完成后在浏览器**强制刷新**：**http://127.0.0.1:8081/**

---

## 常用命令

| 命令 | 说明 |
|------|------|
| `./scripts/local-dev.sh up` | Docker 基础设施 + 宿主机应用 |
| `./scripts/local-dev.sh down` | 停止宿主机应用 + `docker compose down` |
| `./scripts/local-dev.sh infra` | 仅启动 Docker 基础设施 |
| `./scripts/local-dev.sh status` | 运行 `check-progress.sh` |
| `./scripts/redeploy-worker.sh` | 改代码后重启应用（基础设施保持运行） |

---

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
| `.local/run/worker.log` | 宿主机 Java 应用标准输出 |
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
