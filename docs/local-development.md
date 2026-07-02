# 本地开发指南

本文说明在本仓库日常改代码、联调建模器、验证 Worker 的标准流程。**静态页面与 Java 代码均打包进 Spring Boot JAR**，没有热更新；改完代码后必须重新打包并重启 `:8081` 上的 Worker，浏览器刷新才会看到变化。

---

## 测试入口（固定）

| 用途 | 地址 |
|------|------|
| **建模器 / 联调主页面** | http://127.0.0.1:8081/ |
| Worker 健康检查 | http://127.0.0.1:8081/actuator/health |
| Temporal UI（Docker 全栈时） | http://127.0.0.1:8080 |
| Temporal UI（`local-dev.sh` 时） | http://127.0.0.1:8233 |
| Playwright E2E（独立静态服务） | http://127.0.0.1:4173（`npm run test:e2e` 自动拉起） |

人工测试建模器时，**始终使用 8081**，不要用 E2E 的 4173 端口。

---

## 代码位置

| 类型 | 路径 |
|------|------|
| 建模器 HTML | `worker/src/main/resources/static/index.html` |
| 前端 JS | `worker/src/main/resources/static/assets/js/` |
| 前端 CSS | `worker/src/main/resources/static/assets/css/` |
| 后端 API / 服务 | `worker/src/main/java/com/example/platform/` |
| 数据库迁移 | `worker/src/main/resources/db/migration/` |
| E2E 用例 | `e2e/` |

---

## 首次启动本地环境

依赖：JDK 17、Maven、Temporal CLI、Redis（或复用本机已有 Redis / Postgres / Temporal）。

```bash
chmod +x scripts/local-dev.sh scripts/check-progress.sh scripts/redeploy-worker.sh
./scripts/local-dev.sh up       # Redis + Temporal + 首次构建并启动 Worker
./scripts/check-progress.sh     # 期望 ALL_GREEN
```

浏览器打开：**http://127.0.0.1:8081/**

若本机已有 `quantumbpm-platform-*` 等容器占用 `7233` / `6379` / `5432`，`local-dev.sh` 会复用已在运行的服务，只要 Worker 能连上即可。PostgreSQL 需存在库 `flowfoundry` 与用户 `flowfoundry`（见 `deploy/postgres/init-flowfoundry-db.sql`）。

---

## 改代码后的标准流程（必做）

每次修改 `worker/` 下**任意**前端静态资源或 Java 代码后：

```bash
./scripts/redeploy-worker.sh
```

脚本会依次：

1. `mvn -DskipTests package` 打包（静态资源写入 JAR）
2. 停止占用 `8081` 的进程（含 `flowfoundry-worker-local` Docker 容器，若存在）
3. 启动新的 `java -jar` Worker
4. 等待 `/actuator/health` 为 UP

完成后在浏览器**强制刷新**（或普通刷新）：**http://127.0.0.1:8081/**

等价手动命令：

```bash
cd worker && mvn -q -DskipTests package
# 停止 8081 监听进程后：
java -jar target/call-campaign-worker-1.0.0-SNAPSHOT.jar \
  --server.port=8081 \
  --temporal.host=127.0.0.1:7233 \
  --temporal.namespace=call-campaign \
  --spring.data.redis.host=127.0.0.1 \
  --spring.data.redis.port=6379
```

也可通过 `local-dev.sh` 子命令：

```bash
./scripts/local-dev.sh redeploy
```

---

## 常见误区

| 误区 | 说明 |
|------|------|
| 只改 `static/` 不重启 | 页面来自 JAR 内 classpath，必须 `package` + 重启 |
| 只跑 `npm run test:e2e` 就当联调完成 | E2E 用 `http-server` 读磁盘静态文件，**不经过** 8081 Worker |
| 改完代码让用户自己部署 | 协作者 / Agent 改完应**自行** `redeploy-worker.sh` 并告知刷新 8081 |
| 日常 UI 开发用 Docker 重建 | 全栈 Docker 适合集成验证；**日常建模器迭代用 `redeploy-worker.sh` + Java 进程** |
| `local-dev.sh up` 后改代码 | `up` 若检测到 8081 已健康会**跳过**重启，改代码后必须用 `redeploy` |

---

## Docker 全栈（集成 / 冒烟）

完整 Postgres + Temporal + Redis + Worker 容器化验证：

```bash
./scripts/docker-stack.sh up
./scripts/check-progress.sh
./scripts/smoke-test.sh
```

仅当修改了 Dockerfile、compose 环境变量或需要容器内集成测试时，才需要 `docker-stack.sh rebuild`。日常前端改动仍优先 `redeploy-worker.sh`。

---

## E2E 测试

```bash
npm run test:e2e
```

Playwright 对 `worker/src/main/resources/static` 起临时静态服务（`:4173`），**不会**自动更新 8081。若用例通过但 8081 页面旧，说明未执行 `redeploy-worker.sh`。

---

## 日志与排错

| 文件 / 命令 | 用途 |
|-------------|------|
| `.local/run/worker.log` | Java Worker 标准输出 |
| `./scripts/local-dev.sh status` | 同 `check-progress.sh` |
| `curl --noproxy '*' http://127.0.0.1:8081/actuator/health` | 快速健康检查（本机有 HTTP 代理时需 `--noproxy '*'`） |

---

## 协作者 / AI Agent 约定

见仓库根目录 [AGENTS.md](../AGENTS.md)：修改 Worker 或静态资源后，**必须**执行 `./scripts/redeploy-worker.sh`（或等价步骤），验证健康检查后，再通知测试人员刷新 **http://127.0.0.1:8081/**。
