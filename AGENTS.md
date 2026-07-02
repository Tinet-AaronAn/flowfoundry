# 协作者与 AI Agent 须知

## 本地建模器联调（8081）

- **测试页面**：http://127.0.0.1:8081/
- **前端与后端代码**：均在 `worker/` 下，打包进 Spring Boot JAR，**无热更新**。

## 改代码后必须重新部署

修改 `worker/` 内任意文件（含 `src/main/resources/static/` 下的 HTML/JS/CSS）后，**在结束任务前**必须：

1. 运行 `./scripts/redeploy-worker.sh`（或 `./scripts/local-dev.sh redeploy`）
2. 确认 `curl --noproxy '*' http://127.0.0.1:8081/actuator/health` 返回 UP
3. 告知测试人员：**请刷新 http://127.0.0.1:8081/**

**禁止**只提交代码更改而不重启本地 Worker。

## 不要做的事

- 不要假设改 `static/` 后刷新浏览器即可生效（必须 `mvn package` + 重启）
- 不要把 `npm run test:e2e`（`:4173`）当作人工联调地址
- 不要在日常 UI 迭代中默认走 Docker 重建（除非用户明确要求或改动涉及 compose/Dockerfile）
- 不要让用户自行打包、部署或重启——由改代码的一方负责 redeploy

## 详细文档

- [docs/local-development.md](docs/local-development.md)
