# FlowFoundry Modeler Frontend

静态资源位于 `flowfoundry-core/src/main/resources/static/`，由场景可执行 JAR（如 `flowfoundry-app/modules/ai-collection-strategy`）打包发布。`index.html` 只保留页面结构，样式和行为按职责拆分到 `assets/` 下。

本地联调地址：http://127.0.0.1:8081/（见 [docs/service-urls.md](../../../../../../docs/service-urls.md)）

## 目录约定

- `css/modeler.css`：全局布局、画布、节点、Participant / Sub-process 容器和属性面板样式。
- `js/modeler-state.js`：Palette、全局状态、默认示例模型和基础构造函数。
- `js/modeler-render.js`：导航、Palette、画布、节点、连线、属性面板渲染。
- `js/modeler-actions.js`：节点和连线属性更新、条件配置、节点类型切换。
- `js/modeler-canvas.js`：删除、拖拽、容器 resize、Participant 归属、自动布局、Undo / Redo。
- `js/modeler-storage.js`：本地 Workflow Definition 存储、版本管理、列表管理。
- `js/modeler-dsl-runtime.js`：Flow DSL / BPMN JSON 构建、编译运行、Web 联调 Run。
- `js/modeler-bootstrap.js`：API helper、JSON 面板、导入导出、快捷键和启动初始化。

## 后续演进

当画布交互继续复杂化时，可以在这个边界上迁移到 React + React Flow。迁移前应保持 DSL 构建、Participant 归属、Sub-process 容器语义这些核心规则独立于具体 UI 框架。
