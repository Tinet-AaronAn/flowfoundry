# 协作者与 AI Agent 须知

## 本地建模器联调（8081 平台 + 8082 业务 Worker）

与生产一致：**平台与 Worker 分离**，业务模块不另起一套 core HTTP 服务。

| 角色 | 端口 | 说明 |
|------|------|------|
| **flowfoundry-core（平台）** | **8081** | 建模器、Workflow API、Activity Registry（含业务 yaml）、API Keys |
| **示例 Worker（可选）** | **8082** | `examples/ai-collection-strategy`：Temporal Worker + iframe 壳 + App BFF |
| **插件 runner（可选）** | K8s Pod | 平台托管插件 Worker；见 `plugin-runtime-dev.sh` |
| **Temporal UI** | 8080 | 见 [docs/service-urls.md](docs/service-urls.md) |

- 平台管理页：http://127.0.0.1:8081/
- 业务 iframe 壳：http://127.0.0.1:8082/app/workflow-admin.html（嵌入 :8081 建模器）
- 平台 JAR：`flowfoundry-core/target/flowfoundry-core-1.0.4-exec.jar`
- 业务 Worker JAR：`examples/ai-collection-strategy/target/*.jar`（或独立 App 仓库）

## 严格分层

- **flowfoundry-core**：建模器 + API + 解释器 + 扩展点；`flowfoundry.run-mode=platform`
- **examples/ai-collection-strategy/**：官方示例；独立 App 仓库使用 `flowfoundry-sdk` + `flowfoundry-sdk-client`

## 改代码后必须重新部署

修改 `flowfoundry-core/`、`flowfoundry-sdk/` 或 `examples/` 内任意文件后，**在结束任务前**必须：

1. 确保 `./scripts/local-dev.sh infra` 或 `up` 已拉起 Docker 基础设施
2. 运行 `./scripts/redeploy-worker.sh`（平台 :8081）；若改业务 Worker 则 `./scripts/redeploy-app.sh`（:8082）；若插件模式则 `FLOWFOUNDRY_PLUGIN_RUNTIME_ENABLED=true ./scripts/redeploy-worker.sh` 或 `./scripts/plugin-runtime-dev.sh`
3. 确认 `curl --noproxy '*' http://127.0.0.1:8081/actuator/health` 与 `:8082` 均返回 UP
4. 告知测试人员：**平台请刷新 http://127.0.0.1:8081/**；iframe 业务壳见 http://127.0.0.1:8082/app/workflow-admin.html

**禁止**只提交代码更改而不重启本地应用。

## 不要做的事

- 不要假设改 `static/` 后刷新浏览器即可生效（必须 `mvn package` + 重启）
- 不要在业务模块使用 `@EnableFlowFoundry`（会启动第二套平台 API）
- 不要在文档里写已删除路径：`worker/`、`registry/`（根目录）、`bpmn/`、`demos/`
- 不要把 Temporal UI 统一写成 8233（Docker 栈用 **8080**）
- 不要把 `npm run test:e2e`（`:4173`）当作人工联调地址
- 本地调试使用 `./scripts/local-dev.sh`（Docker 基础设施 + 宿主机 JAR），不要在日常 UI 迭代中走 `docker-stack.sh rebuild`
- 不要让用户自行打包、部署或重启——由改代码的一方负责 redeploy
- 不要把业务逻辑、示例流程、业务注册表写进 `flowfoundry-core/`

## 详细文档

- [docs/service-urls.md](docs/service-urls.md) — **服务地址与路径权威表**
- [docs/local-development.md](docs/local-development.md) — 本地调试
- [docs/plugin-development-guide.md](docs/plugin-development-guide.md) — **插件开发**
- [docs/plugin-runtime-design.md](docs/plugin-runtime-design.md) — 插件运行时设计
- [docs/production-deployment.md](docs/production-deployment.md) — 生产部署
- [docs/project-structure.md](docs/project-structure.md)

## Git 远程仓库

本仓库的 **canonical remote** 如下（提交 / 推送前必须先确认已配置）：

| 项 | 值 |
|----|-----|
| Remote 名 | `origin` |
| URL | `git@github.com:Tinet-AaronAn/flowfoundry.git` |
| 默认基线分支 | `main` |

**Agent 或协作者在执行 `git push` 前：**

1. 运行 `git remote -v`；若看不到 `origin`，执行：
   ```bash
   git remote add origin git@github.com:Tinet-AaronAn/flowfoundry.git
   ```
2. 若 `origin` 已存在但 URL 不对，执行：
   ```bash
   git remote set-url origin git@github.com:Tinet-AaronAn/flowfoundry.git
   ```
3. 推送当前分支：`git push -u origin HEAD`

**禁止**在未配置 remote 的情况下只告知用户「请自行 push」——应先按上表补全 `origin` 再推送。新 clone 可直接：

```bash
git clone git@github.com:Tinet-AaronAn/flowfoundry.git
```
