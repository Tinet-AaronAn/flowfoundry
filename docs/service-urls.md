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
| Temporal namespace（当前业务模块） | `call-campaign` |
| Task queue（当前业务模块） | `ai-collection-strategy` |
| PostgreSQL | 127.0.0.1:5432 / 库 `flowfoundry` / 用户 `flowfoundry` |
| Redis | 127.0.0.1:6379 |
| Playwright E2E 静态页 | http://127.0.0.1:4173（**不是**联调入口） |
| QuantumBPM 参考 DevServer | http://127.0.0.1:9060（可选，`docker compose --profile full`） |

## 代码与配置路径

| 内容 | 路径 |
|------|------|
| 平台内核 | `flowfoundry-core/` |
| 场景启动类（催收示例） | `flowfoundry-app/modules/ai-collection-strategy/.../AiCollectionStrategyApplication.java` |
| 场景 JAR 产物 | `flowfoundry-app/modules/ai-collection-strategy/target/ai-collection-strategy-demo-1.0.0-SNAPSHOT.jar` |
| 平台共享配置 | `flowfoundry-core/src/main/resources/application-flowfoundry-platform.yml` |
| 建模器静态资源 | `flowfoundry-core/src/main/resources/static/` |
| 前端 SDK | `flowfoundry-core/.../static/assets/js/flowfoundry-modeler-sdk.js` |
| 平台 SDK 注解 | `EnableFlowFoundry`（平台）、`EnableFlowFoundryWorker`（业务 Worker） |
| 数据库迁移 | `flowfoundry-core/src/main/resources/db/migration/` |
| Activity 注册表（催收） | `flowfoundry-app/modules/ai-collection-strategy/config/activities-registry.yaml` |

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
