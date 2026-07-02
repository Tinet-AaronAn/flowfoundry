# FlowFoundry UI E2E Test Plan

## 测试框架

- 使用 `@playwright/test` 驱动真实 Chromium。
- 使用 `http-server` 托管 `worker/src/main/resources/static`，保持和 Spring Boot 静态资源路径一致。
- E2E 测试 mock `/api/activities`、`/api/flows/*`；Workflow 持久化类用例额外 mock `/api/workflows/**`（见 `mockWorkflowBackend`）。
- 测试固定使用英文 locale（`flowfoundry-locale=en`），通过 `#navModeler` 等稳定选择器避免 i18n 耦合。

## 覆盖范围

完整用例清单（分模块表格、ID、描述、断言）见 **[TEST_CASES.md](./TEST_CASES.md)**。

### 主流程 (`modeler-main-flow.spec.js`)

- 页面加载、节点选择、浮动工具栏
- 属性面板输入焦点保持
- 边路由切换（orthogonal / curved）
- 边标签、网关标签布局
- 工具栏追加节点、Undo / Redo
- View DSL、Export

### 节点类型 (`modeler-node-types.spec.js`)

- Start / End / Task / Service / Human / Send / Receive / Script / Workflow / Gateway / Intermediate Timer
- Human Task `managed` / `offline` 模式写入 DSL

### 容器与参与方 (`modeler-containers.spec.js`)

- Participant 泳道布局、默认尺寸、resize
- Auto Layout 保持嵌套归属
- Participant 模式负向校验与拖拽修复
- Sub-process 结构语义、内部节点删除、空容器创建

### 画布交互 (`modeler-canvas.spec.js`)

- 视口：Zoom In/Out、Fit View、锁定、画布平移、小地图导航
- 连线：拖拽连接点创建 Sequence Flow、选中删除、端点重连、条件编辑
- 节点：拖拽改坐标、工具栏/键盘删除、Task 类型变形、Palette 拖放、Text Annotation 编辑
- 面板：Palette 折叠/展开、Workflow 列表与画布切换、Import/Export 往返

### 运行链路 (`modeler-runtime.spec.js`)

- Compile / Run（mock API）
- Debug 完整模拟、状态查询
- Child Workflow 定义导出

### Workflow API (`modeler-workflow.spec.js`)

- Workflow 列表探测与 CRUD（mock `/api/workflows`）
- 多版本保存与 patch 递增
- 平台 ID 分配（`workflow_` / `task_` / `event_` / `gateway_`）
- 列表搜索、状态过滤、Actions 布局

## 执行命令

```bash
npm run test:e2e
npm run test:e2e:headed
```
