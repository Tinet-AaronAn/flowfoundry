# FlowFoundry UI E2E Test Plan

## 测试框架

- 使用 `@playwright/test` 驱动真实 Chromium。
- 使用 `http-server` 托管 `worker/src/main/resources/static`，保持和 Spring Boot 静态资源路径一致。
- E2E 测试 mock `/api/activities`、`/api/flows/compile`、`/api/flows/run`、`/api/flows/runs/**`，聚焦 UI 层交互、DSL 生成和请求载荷。

## 覆盖范围

- 主流程：页面加载、Workflow 列表切换、流程画布切换、节点选择、顶部节点菜单、追加节点、删除、Undo / Redo。
- 节点类型：Start / End / Task / Service / User / Manual / Send / Receive / Script / Business Rule / Gateway / Timer / Boundary。
- 组件组合：Participant 多泳道约束、Participant resize、Sub-process 容器、Participant + Sub-process 嵌套组合。
- 运行链路：View DSL、Compile、Run、Debug 模拟。
- 负向校验：启用 Participant 后节点不能位于所有 Participant 之外。

## 执行命令

```bash
npm run test:e2e
npm run test:e2e:headed
```
