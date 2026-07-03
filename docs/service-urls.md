# 本地服务地址（权威对照表）

改文档或脚本时**以本表为准**，避免写错端口或已删除目录。

## 必记三条


| 用途                       | 地址                                               | 说明                                                              |
| ------------------------ | ------------------------------------------------ | --------------------------------------------------------------- |
| **建模器 / API 联调**         | [http://127.0.0.1:8081/](http://127.0.0.1:8081/) | 场景 JAR（默认 `ai-collection-strategy`），改代码后必须 `redeploy-worker.sh` |
| **Temporal UI（Docker）**  | [http://127.0.0.1:8080/](http://127.0.0.1:8080/) | 容器 `flowfoundry-temporal-ui` 或 `docker-stack.sh`                |
| **Temporal UI（CLI dev）** | [http://127.0.0.1:8233/](http://127.0.0.1:8233/) | 仅当使用 `temporal server start-dev` 时                              |


> **常见踩坑**：本机若已有 Docker Temporal（7233），**不要用 8233** 打开 UI——8233 可能无服务或 502。先看 Docker：`docker ps \| grep temporal-ui`，有则用 **8080**。

## 其他地址


| 用途                         | 地址                                                                             |
| -------------------------- | ------------------------------------------------------------------------------ |
| FlowFoundry 健康检查           | [http://127.0.0.1:8081/actuator/health](http://127.0.0.1:8081/actuator/health) |
| Temporal gRPC              | 127.0.0.1:7233                                                                 |
| Temporal namespace（当前业务模块） | `call-campaign`                                                                |
| Task queue（当前业务模块）         | `ai-collection-strategy`                                                       |
| Redis                      | 127.0.0.1:6379                                                                 |
| PostgreSQL                 | localhost:5432 / 库 `flowfoundry`                                               |
| Playwright E2E 静态页         | [http://127.0.0.1:4173（**不是**联调入口）](http://127.0.0.1:4173（**不是**联调入口）)         |
| QuantumBPM 参考 DevServer    | [http://127.0.0.1:9060（可选，非本产品）](http://127.0.0.1:9060（可选，非本产品）)               |




## 代码与配置路径


| 内容                 | 路径                                                                                                     |
| ------------------ | ------------------------------------------------------------------------------------------------------ |
| 平台内核               | `flowfoundry-core/`（`com.tinet.flowfoundary.*`）                                                        |
| 平台独立启动类            | `flowfoundry-core/.../boot/FlowFoundryCoreApplication.java`                                            |
| 场景启动类（催收示例）        | `flowfoundry-app/modules/ai-collection-strategy/.../AiCollectionStrategyApplication.java`              |
| 场景 JAR 产物（催收）      | `flowfoundry-app/modules/ai-collection-strategy/target/ai-collection-strategy-demo-1.0.0-SNAPSHOT.jar` |
| 平台共享配置             | `flowfoundry-core/src/main/resources/application-flowfoundry-platform.yml`                             |
| 业务聚合               | `flowfoundry-app/`（`packaging=pom`，无 `src/`）                                                           |
| 业务场景目录             | `flowfoundry-app/modules/`                                                                             |
| 建模器静态资源            | `flowfoundry-core/src/main/resources/static/`                                                          |
| 数据库迁移              | `flowfoundry-core/src/main/resources/db/migration/`                                                    |
| Activity 注册表（催收示例） | `flowfoundry-app/modules/ai-collection-strategy/config/activities-registry.yaml`                       |
| 业务 Activity 实现     | `flowfoundry-app/modules/ai-collection-strategy/src/.../aicollection/`                                 |


本地 redeploy 默认场景为 `ai-collection-strategy`，可通过环境变量 `SCENARIO` 切换（需对应 `flowfoundry-app/modules/<SCENARIO>/` 存在）。

## 构建与重启

```bash
mvn -pl flowfoundry-app/modules/ai-collection-strategy -am -DskipTests package   # 根目录执行
./scripts/redeploy-worker.sh                       # 重启 8081
./scripts/runtime-test.sh                          # FlowInterpreter E2E
```



## 健康检查

```bash
./scripts/check-progress.sh
curl --noproxy '*' http://127.0.0.1:8081/actuator/health
```

本机有 HTTP 代理时，`curl` 访问 127.0.0.1 必须加 `--noproxy '*'`。