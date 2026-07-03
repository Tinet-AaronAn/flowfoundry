# 本地开发指南

本文说明在本仓库日常改代码、联调建模器、验证 FlowFoundry 的标准流程。**静态页面与 Java 代码均打包进 Spring Boot JAR**，没有热更新；改完代码后必须重新打包并重启 `:8081` 上的应用，浏览器刷新才会看到变化。

**服务地址与代码路径的权威对照**见 [service-urls.md](./service-urls.md)（改文档前请先查此表）。

---

## 测试入口（固定）

| 用途 | 地址 |
|------|------|
| **建模器 / 联调主页面** | http://127.0.0.1:8081/ |
| 健康检查 | http://127.0.0.1:8081/actuator/health |
| **Temporal UI（Docker）** | http://127.0.0.1:8080/ |
| Temporal UI（`temporal server start-dev`） | http://127.0.0.1:8233/ |
| Playwright E2E（独立静态服务） | http://127.0.0.1:4173（`npm run test:e2e` 自动拉起） |

人工测试建模器时，**始终使用 8081**，不要用 E2E 的 4173 端口。  
本机若 7233 已被 Docker Temporal 占用，Temporal UI 用 **8080**，不要用 8233。

---

## 代码位置

| 类型 | 路径 |
|------|------|
| 建模器 HTML | `flowfoundry-core/src/main/resources/static/index.html` |
| 前端 JS / CSS | `flowfoundry-core/src/main/resources/static/assets/` |
| 平台 API / 解释器 | `flowfoundry-core/src/main/java/com/tinet/flowfoundary/` |
| 可运行入口 | `flowfoundry-app/modules/ai-collection-strategy/`（`AiCollectionStrategyApplication`） |
| 业务 Activity | `flowfoundry-app/modules/`（如 `ai-collection-strategy`） |
| 数据库迁移 | `flowfoundry-core/src/main/resources/db/migration/` |
| E2E 用例 | `e2e/` |

---

## 首次启动本地环境

依赖：JDK 17、Maven、Temporal CLI、Redis（或复用本机已有 Redis / Postgres / Temporal）。

```bash
chmod +x scripts/local-dev.sh scripts/check-progress.sh scripts/redeploy-worker.sh
./scripts/local-dev.sh up       # Redis + Temporal + 首次构建并启动
./scripts/check-progress.sh     # 期望 ALL_GREEN
```

浏览器打开：**http://127.0.0.1:8081/**

在 **Runtime（运行联调）** 视图点击 **Run** 时，前端会发送 `runSource=web-modeler` 与请求头 `X-FlowFoundry-Client: web-modeler`，Temporal 执行真实 Workflow，Activity 走桩实现（无外部副作用）。直接调用 `POST /api/flows/run` 的对外集成始终走 `production` Activity，即使请求体携带 `runSource=web-modeler` 也会被忽略。

若本机已有 `quantumbpm-platform-*` 等容器占用 `7233` / `6379` / `5432`，`local-dev.sh` 会复用已在运行的服务，只要应用能连上即可。PostgreSQL 需存在库 `flowfoundry` 与用户 `flowfoundry`（见 `deploy/postgres/init-flowfoundry-db.sql`）。

---

## 改代码后的标准流程（必做）

每次修改 `flowfoundry-core/`、`flowfoundry-app/` 或 `flowfoundry-app/modules/` 下**任意**前端静态资源或 Java 代码后：

```bash
./scripts/redeploy-worker.sh
# 或指定场景：SCENARIO=ai-collection-strategy ./scripts/redeploy-worker.sh
```

脚本会依次：

1. 在仓库根目录执行 `mvn -pl flowfoundry-app/modules/ai-collection-strategy -am -DskipTests package`
2. 停止占用 `8081` 的进程（含 `flowfoundry-worker-local` Docker 容器，若存在）
3. 启动新的 `java -jar flowfoundry-app/modules/ai-collection-strategy/target/...`
4. 等待 `/actuator/health` 为 UP

完成后在浏览器**强制刷新**：**http://127.0.0.1:8081/**

等价手动命令见 [service-urls.md](./service-urls.md)。

也可通过 `local-dev.sh` 子命令：

```bash
./scripts/local-dev.sh redeploy
```

---

## Git 远程仓库

| 项 | 值 |
|----|-----|
| 仓库（SSH） | `git@github.com:Tinet-AaronAn/Flow-Foundary.git` |
| Remote 名 | `origin` |
| 基线分支 | `main` |

首次 clone：

```bash
git clone git@github.com:Tinet-AaronAn/Flow-Foundary.git
cd Flow-Foundary   # 或你的本地目录名
```

若本地目录是从其他方式初始化、没有 `origin`，补配置后再 push：

```bash
git remote add origin git@github.com:Tinet-AaronAn/Flow-Foundary.git
# 或：git remote set-url origin git@github.com:Tinet-AaronAn/Flow-Foundary.git
git push -u origin HEAD
```

协作者 / Agent 约定见 [AGENTS.md](../AGENTS.md) 中的「Git 远程仓库」小节。

---

## 常见误区

| 误区 | 说明 |
|------|------|
| 只改 `static/` 不重启 | 页面来自 JAR 内 classpath，必须 `package` + 重启 |
| 用 8233 开 Temporal UI | Docker Temporal 时 UI 在 **8080**；8233 仅 `start-dev` |
| 只跑 `npm run test:e2e` 就当联调完成 | E2E 用 `http-server` 读磁盘静态文件，**不经过** 8081 |
| 改完代码让用户自己部署 | 协作者 / Agent 改完应**自行** `redeploy-worker.sh` 并告知刷新 8081 |
| 日常 UI 开发用 Docker 重建 | 全栈 Docker 适合集成验证；**日常建模器迭代用 `redeploy-worker.sh`** |
| `local-dev.sh up` 后改代码 | `up` 若检测到 8081 已健康会**跳过**重启，改代码后必须用 `redeploy` |

---

## Docker 全栈（集成 / 冒烟）

```bash
./scripts/docker-stack.sh up
./scripts/check-progress.sh
./scripts/runtime-test.sh          # FlowInterpreter 动态流程 E2E（推荐）
./scripts/smoke-test.sh            # Demo 内置 CallCampaignWorkflow 冒烟
```

仅当修改了 Dockerfile、compose 环境变量或需要容器内集成测试时，才需要 `docker-stack.sh rebuild`。

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
| `.local/run/worker.log` | Java 应用标准输出 |
| `./scripts/check-progress.sh` | 编译 + 依赖服务 + 8081 健康 |
| `curl --noproxy '*' http://127.0.0.1:8081/actuator/health` | 快速健康检查 |

---

## 协作者 / AI Agent 约定

见 [AGENTS.md](../AGENTS.md)：修改平台或业务场景代码后，**必须**执行 `./scripts/redeploy-worker.sh`，验证健康检查后，再通知测试人员刷新 **http://127.0.0.1:8081/**。
