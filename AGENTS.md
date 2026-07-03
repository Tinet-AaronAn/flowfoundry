# 协作者与 AI Agent 须知

## 本地建模器联调（8081）

- **测试页面**：http://127.0.0.1:8081/
- **Temporal UI**：http://127.0.0.1:8080/（Docker）或 http://127.0.0.1:8233/（`temporal server start-dev`）— 详见 [docs/service-urls.md](docs/service-urls.md)
- **平台代码**：`flowfoundry-core/`（含 `static/` 建模器前端）
- **可运行 JAR**：`flowfoundry-app/modules/<场景>/`（如 `ai-collection-strategy`）
- **业务聚合**：`flowfoundry-app/`（`packaging=pom`，无 `src/`）
- 打包进 Spring Boot JAR，**无热更新**

## 严格分层

- **flowfoundry-core**：建模器 + API + 解释器 + 扩展点接口；**新增业务不改 core**
- **flowfoundry-app/modules/**：各业务场景（独立 `main`、Activity、注册表、Worker 扩展）

## 改代码后必须重新部署

修改 `flowfoundry-core/`、`flowfoundry-app/` 或 `flowfoundry-app/modules/` 内任意文件后，**在结束任务前**必须：

1. 运行 `./scripts/redeploy-worker.sh`（或 `./scripts/local-dev.sh redeploy`）
2. 确认 `curl --noproxy '*' http://127.0.0.1:8081/actuator/health` 返回 UP
3. 告知测试人员：**请刷新 http://127.0.0.1:8081/**

**禁止**只提交代码更改而不重启本地应用。

## 不要做的事

- 不要假设改 `static/` 后刷新浏览器即可生效（必须 `mvn package` + 重启）
- 不要在文档里写已删除路径：`worker/`、`registry/`（根目录）、`bpmn/`、`demos/`
- 不要把 Temporal UI 统一写成 8233（Docker 栈用 **8080**）
- 不要把 `npm run test:e2e`（`:4173`）当作人工联调地址
- 不要在日常 UI 迭代中默认走 Docker 重建（除非用户明确要求或改动涉及 compose/Dockerfile）
- 不要让用户自行打包、部署或重启——由改代码的一方负责 redeploy
- 不要把业务逻辑、示例流程、业务注册表写进 `flowfoundry-core/`

## 详细文档

- [docs/service-urls.md](docs/service-urls.md) — **服务地址与路径权威表**
- [docs/local-development.md](docs/local-development.md)
- [docs/project-structure.md](docs/project-structure.md)

## Git 远程仓库

本仓库的 **canonical remote** 如下（提交 / 推送前必须先确认已配置）：

| 项 | 值 |
|----|-----|
| Remote 名 | `origin` |
| URL | `git@github.com:Tinet-AaronAn/Flow-Foundary.git` |
| 默认基线分支 | `main` |

**Agent 或协作者在执行 `git push` 前：**

1. 运行 `git remote -v`；若看不到 `origin`，执行：
   ```bash
   git remote add origin git@github.com:Tinet-AaronAn/Flow-Foundary.git
   ```
2. 若 `origin` 已存在但 URL 不对，执行：
   ```bash
   git remote set-url origin git@github.com:Tinet-AaronAn/Flow-Foundary.git
   ```
3. 推送当前分支：`git push -u origin HEAD`

**禁止**在未配置 remote 的情况下只告知用户「请自行 push」——应先按上表补全 `origin` 再推送。新 clone 可直接：

```bash
git clone git@github.com:Tinet-AaronAn/Flow-Foundary.git
```
