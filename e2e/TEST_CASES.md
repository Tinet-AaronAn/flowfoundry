# FlowFoundry E2E 测试用例清单

> 维护说明：新增用例时请同步更新本表；ID 按模块前缀递增，避免重复。
>
> - 执行全部测试：`npm run test:e2e`
> - 有界面调试：`npm run test:e2e:headed`
> - 公共 helper：`e2e/helpers/modeler.js`（含 `mockWorkflowBackend` / `mockBackendWithWorkflow`）
> - 测试计划概览：`e2e/README.md`

**当前统计**：6 个 spec 文件 · **72** 条用例（含 17 条参数化节点类型用例）

| 模块前缀 | 文件 | 用例数 | 说明 |
|---------|------|--------|------|
| MF | `modeler-main-flow.spec.js` | 8 | 建模器主流程与工具栏 |
| CV | `modeler-canvas.spec.js` | 22 | 画布视口、连线、节点、面板 |
| CT | `modeler-containers.spec.js` | 10 | Participant / Sub-process 容器 |
| NT | `modeler-node-types.spec.js` | 18 | BPMN 节点类型与 DSL 配置 |
| RT | `modeler-runtime.spec.js` | 4 | 编译、运行、调试、子流程 |
| WF | `modeler-workflow.spec.js` | 10 | Workflow API 持久化、版本、平台 ID |

---

## 本次对话新增需求与回归覆盖摘要

| 类别 | 需求 / 曾发现问题 | 对应用例 |
|------|-------------------|----------|
| Workflow 存储 | PostgreSQL 落库、REST CRUD、列表/搜索/过滤 | WF-01 ~ WF-06, WF-08 |
| 多版本管理 | 初始 `1.0.0`、patch 递增、保存/新建版本 | WF-02, WF-03, WF-04 |
| 平台 ID 规范 | `workflow_`/`task_`/`event_`/`gateway_` 等 8 位短 ID | WF-02, WF-07 |
| Workflow 列表 UI | Actions 按钮换行、状态下拉固定 148px | WF-09, WF-10 |
| 视口改造 | `translate+scale`、进入画布自动 Fit、缩放 20%~150% | CV-19, CV-20, CV-01（已调整） |
| 画布 Chrome | 缩放/小地图固定在 `.canvas-chrome` 层 | CV-21 |
| 连线样式 | 虚线 + `edge-flow` 流动动画 | CV-22 |
| 容器约束 | Participant 须**完整包含**节点，搭边不算归属 | CT-09 |
| Annotation 绑定 | 放入 Participant 后随容器拖动 | CT-10 |
| 默认容器尺寸 | Participant 2520×1040、Sub-process 840×520（模型坐标） | CT-02, CT-08（断言已改为模型维度） |

---

## MF — 主流程（`modeler-main-flow.spec.js`）

| ID | 用例名称 | 描述 | 关键断言 |
|----|----------|------|----------|
| MF-01 | loads modeler, selects a node from its body, and shows the floating toolbar | 打开建模器，点击节点主体选中，显示浮动工具栏 | `#propType` 为 `serviceTask`；工具栏含 Append task |
| MF-02 | selects a node when clicking the node body, not only connection handles | 点击节点文字区域可选中；点击空白画布可取消选中 | 选中后 `.node.selected` 存在；点画布后选中清除 |
| MF-03 | keeps node property inputs focused while typing | 在右侧属性面板连续输入节点名称时，输入框保持焦点 | 输入框 value 与节点标签同步；`toBeFocused()` |
| MF-04 | switches process edge routing between rounded orthogonal and curved | 在流程属性中切换边路由：圆角正交 ↔ 曲线 | SVG path 含 `Q` / `C`；`model.process.edgeRouting` 为 `curved`；DSL `flow` 不含 `edgeRouting` |
| MF-05 | shows edge name instead of FEEL condition when a name is set | 边设置了名称时，画布上显示名称而非 FEEL 表达式 | `#edges text` 为 `Approved Path`，不含 `${amount > 1000}` |
| MF-06 | shows gateway name below the gateway node | 网关名称标签显示在菱形图标下方并水平居中 | `gateway-label` 的 y 坐标大于 `gateway-shape` 底部 |
| MF-07 | appends a task from the floating toolbar, then supports undo and redo | 从浮动工具栏向后追加 Task，并验证 Undo / Redo | 节点数 +1；Undo 恢复；Redo 再次追加 |
| MF-08 | can view DSL and export model JSON from the toolbar | 工具栏 View DSL 与 Export 可正常打开 JSON 面板 | DSL 含 `nodes`/`edges`；Export 含 `model`/`dsl`/`bpmn` |

**前置**：`mockBackend` + `openFreshModelerWithOutboundDemo`（E2E 夹具流程）。MF-05 额外 `importModel` 自定义边标签模型。

---

## CV — 画布交互（`modeler-canvas.spec.js`）

### 视口（`FlowFoundry canvas viewport`）

| ID | 用例名称 | 描述 | 关键断言 |
|----|----------|------|----------|
| CV-01 | zooms in and out from viewport controls | 点击 Zoom In / Zoom Out 调整缩放比例 | `state.scale` 先增大后减小；`#zoomLevel` 文本变化 |
| CV-02 | fits the full diagram into the viewport | 先 Zoom In 再 Fit View 将整个流程缩放到可视区域 | `state.scale` 相对 Zoom In 后改变 |
| CV-03 | locks and unlocks viewport navigation | 锁定视口后禁用缩放/适配；解锁后恢复 | 按钮 disabled；`#message` 含 locked/unlocked |
| CV-04 | pans the canvas by dragging empty space | 在画布空白处拖拽平移视口 | `panX`/`panY` 变化超过阈值 |
| CV-05 | navigates via minimap click | 点击小地图改变当前视口位置 | `panX`/`panY` 变化；`#minimapViewport` 可见 |
| CV-19 | clamps zoom between 20% and 150% | 连续缩放触达上下限 | `state.scale` ∈ [0.2, 1.5]；`#zoomLevel` 为 20% / 150% |
| CV-20 | auto fits diagram when switching to modeler view | 从 Workflow 列表切回建模器自动 Fit View | `state.scale` ≠ 1 |
| CV-21 | keeps viewport controls inside canvas-chrome overlay | 缩放与小地图位于 `.canvas-chrome` 固定层 | `#zoomInBtn` 等在 `.canvas-chrome` 内 |
| CV-22 | renders sequence flows with dashed animated edge lines | 顺序流为虚线并带流动动画 | `stroke-dasharray` 含 `8`；`animation-name: edge-flow` |

### 连线与边（`FlowFoundry canvas connections and edges`）

| ID | 用例名称 | 描述 | 关键断言 |
|----|----------|------|----------|
| CV-06 | creates a sequence flow by dragging connection handles | 从源节点连接点拖拽到目标节点连接点创建顺序流 | `#edges path.edge-hit` 数量 +1；DSL 含对应 `from`/`to` |
| CV-07 | selects and deletes a sequence flow | 选中顺序流后通过 Delete 按钮删除 | 边数量 -1；`#message` 含 deleted |
| CV-08 | shows edge endpoint handles when a sequence flow is selected | 选中顺序流后显示源/目标端点拖拽手柄 | `.edge-endpoint-handle` 数量为 2；title 正确 |
| CV-09 | updates edge condition from the properties panel | 在边属性面板切换为 FEEL 并编辑条件表达式 | DSL 中 `condition` 为 `${approved == true}` |

### 节点（`FlowFoundry canvas node interactions`）

| ID | 用例名称 | 描述 | 关键断言 |
|----|----------|------|----------|
| CV-10 | drags a node and persists the new coordinates in DSL | 拖拽节点后模型坐标更新 | `state.model` 中 `Task_A.x/y` 增大 |
| CV-11 | deletes a selected node from the toolbar | 工具栏 Delete 删除选中节点 | 节点 DOM 消失；`#message` 含 deleted |
| CV-12 | deletes the selected node with the keyboard | 按 Delete 键删除选中节点 | 节点 DOM 消失 |
| CV-13 | morphs a service task into a human task from the floating toolbar | Service Task 变形为 Human Task | `#propType` 为 `humanTask`；节点名变为 Human Task |
| CV-14 | drops a palette service task onto the canvas | 从 Palette 拖放 Service Task 到画布 | 节点数 +1；`#propType` 为 `serviceTask` |
| CV-15 | creates and edits a text annotation on the canvas | 拖放 Text Annotation 并编辑文本 | `annotation-editor` 有值；`documentation` 写入模型 |

### 面板与导航（`FlowFoundry canvas chrome and navigation`）

| ID | 用例名称 | 描述 | 关键断言 |
|----|----------|------|----------|
| CV-16 | collapses and expands the palette panel | Palette 折叠与展开 | `#workbench` 切换 `palette-collapsed` |
| CV-17 | switches between workflow list and modeler canvas | Workflow 列表与建模器画布视图切换 | `#workflowListView` / `#modelerView` active |
| CV-18 | imports a model through the toolbar prompt and round-trips through export | 通过 Import 对话框导入，再 Export 往返比对 | Export 中 `model.nodes` id 与导入一致 |
| CV-23 | collapses and expands the nav and properties panels | 左侧菜单与右侧属性面板折叠与展开 | 进入 Modeler 时属性面板默认收起；可手动展开/折叠 |
| CV-24 | starts with properties collapsed when entering modeler | 首次及再次进入 Modeler 时属性面板默认隐藏 | `#app` 含 `properties-collapsed`；从 Workflows 切回仍收起 |
| CV-25 | auto-expands properties when selecting a node or edge | 选中节点或连线时自动展开属性面板 | 收起后点击节点/边，`#app` 不再含 `properties-collapsed` |

**前置**：视口/连线/节点类用例多数 `importModel(simpleConnectionWorkflow)` 或 `connectedWorkflow()`；面板类用例使用 E2E 夹具或 `importModel` 注入模型。

---

## CT — 容器与参与方（`modeler-containers.spec.js`）

| ID | 用例名称 | 描述 | 关键断言 |
|----|----------|------|----------|
| CT-01 | renders participant with a left label lane and right content area | Participant 渲染左侧标签泳道与右侧内容区 | `.participant-label`、`.participant-content-area`、`.participant-caption` |
| CT-02 | creates participant with the larger default pool size | 从 Palette 拖放 Participant，验证默认大尺寸（模型坐标） | 宽约 2520、高约 1040 |
| CT-03 | resizes participant and keeps contained node ownership valid | 拖拽 SE 手柄放大 Participant，子节点归属不变 | `width` 增大；DSL 中节点均有 `flowFoundryParticipant` |
| CT-04 | auto layout preserves participant and sub-process containment | Auto Layout 后保持 Participant / Sub-process 归属关系 | `participantId`、`parentSubProcessId`、边连通性 |
| CT-05 | rejects DSL when participant mode has nodes outside all participants, then passes after dragging inside | Participant 模式下节点在外部时 View DSL 报错；拖入泳道后通过 | 先含 `Violations`；修复后 DSL 可生成 |
| CT-06 | sub-process remains structural while inner runtime nodes compile inside participant | Sub-process 为结构容器，内部运行时节点出现在 DSL 中 | DSL 无 `Sub_Process` 容器节点；含 `Sub_Start`/`Sub_Task` |
| CT-07 | allows deleting start and end nodes inside a sub-process | 可删除子流程内部的 Start / End 节点 | 对应节点 DOM 消失 |
| CT-08 | creates an empty sub-process container without default start or end nodes | 拖放 Sub-process 创建空容器，不含默认起止节点 | 默认约 840×520；无 Start/End 节点 |
| CT-09 | rejects partial participant overlap until the node is fully contained | 节点仅搭边在泳道上时不归属；完全进入后写入 `participantId` | 搭边时无 `participantId`；居中后归属正确 |
| CT-10 | moves annotation together when participant container is dragged | Annotation 绑定 Participant，拖动泳道时同步位移 | 相对偏移保持不变 |

**前置**：`importModel(participantWorkflow())` 或 `invalidParticipantWorkflow()`（CT-05）。数据定义见 `e2e/helpers/modeler.js`。

---

## NT — 节点类型（`modeler-node-types.spec.js`）

### 参数化渲染与选中（17 条，`renders and selects <kind>`）

| ID | kind（BPMN 类型） | 画布标签 | 描述 |
|----|-------------------|----------|------|
| NT-01 | `startEvent` | Start | 开始事件可渲染并选中 |
| NT-02 | `task` | Generic Task | 通用任务 |
| NT-03 | `serviceTask` | Service Task | 服务任务 |
| NT-04 | `humanTask` | Human Task | 人工任务（managed / offline） |
| NT-05 | `humanTask` | Human Task Offline | 线下人工步骤（offline 模式） |
| NT-08 | `scriptTask` | Script Task | 脚本任务 |
| NT-09 | `scriptTask` | Script Task | Node.js 脚本 |
| NT-10 | `workflow` | Workflow Task | 子工作流调用 |
| NT-11 | `exclusiveGateway` | Exclusive Gateway | 排他网关 |
| NT-12 | `parallelGateway` | Parallel Gateway | 并行网关 |
| NT-13 | `inclusiveGateway` | Inclusive Gateway | 包容网关 |
| NT-14 | `eventBasedGateway` | Event Gateway | 事件网关 |
| NT-15 | `intermediateCatchEvent` | Timer Event | 定时中间事件 |
| NT-17 | `endEvent` | End | 结束事件 |

**共同断言**：`#propType` 含对应 `kind`；`.node.selected` 含对应标签。

### 配置持久化

| ID | 用例名称 | 描述 | 关键断言 |
|----|----------|------|----------|
| NT-18 | preserves node-specific configuration in generated DSL | 各节点专属配置正确写入 DSL | Receive `signalName`、Script `script`、`activityType`、Workflow `childWorkflowId`、Timer `duration`、Participant 归属 |

**前置**：`importModel(nodeTypeMatrixWorkflow())`——在单个 Participant 泳道内排列全部节点类型。

---

## RT — 运行与调试（`modeler-runtime.spec.js`）

| ID | 用例名称 | 描述 | 关键断言 |
|----|----------|------|----------|
| RT-01 | compiles and runs a participant-owned workflow through mocked APIs | Modeler Compile；Runtime Run 走 mock API，带 `runSource=web-modeler` | `compileRequests`/`runRequests` 有记录；`runSource` 为 `web-modeler` |
| RT-03 | queries workflow state and completes human task through mocked APIs | 填写 Workflow ID 查询运行状态 | 返回 JSON 中 `status` 为 `RUNNING` |
| RT-04 | exports workflow nodes as child workflow definitions | Workflow 节点导出子流程定义到 DSL | `flowFoundryChildWorkflow` 与 `childWorkflowDefinition` 一致 |

**前置**：RT-01/04 使用 `participantWorkflow()` 或自定义父子流程模型；RT-03 使用 `openFreshModeler` + mock API。

---

## RN — 运行实例（`modeler-runs.spec.js`）

| ID | 用例名称 | 描述 | 关键断言 |
|----|----------|------|----------|
| RN-01 | keeps runtime controls out of the properties panel | Runtime 控件不在 Properties，而在 Runtime 视图 | `#simulationView` 中 `#runInput` 可见；`#propertiesPanel` 无输入 JSON |
| RN-02 | records a run and lists it on the Runs page | 在 Runtime 中 Run 后记录实例并在 Runs 页展示 | `#runsTable` 含 `workflowId`；Runs 视图无 Properties |

**前置**：`openFreshModeler` + mock API。

---

## WF — Workflow API 与持久化（`modeler-workflow.spec.js`）

| ID | 用例名称 | 描述 | 关键断言 |
|----|----------|------|----------|
| WF-01 | detects workflow API and lists workflows from backend | 启动时探测 `/api/workflows` 并渲染列表 | GET 记录存在；无 API fallback 提示 |
| WF-02 | creates workflow via API with workflow_ prefixed id and version 1.0.0 | 新建 Workflow 走 POST API | `workflow_{8位}`；`currentVersion` 为 `1.0.0` |
| WF-03 | saves current version model through PUT API | Save Current Version 持久化模型 | PUT `/versions/{version}`；节点名称写入 store |
| WF-04 | creates a new version with patch increment | New Version 创建 `1.0.1` | `versions` 含新版本；POST `/versions` |
| WF-05 | renames workflow through PATCH API | Rename 更新名称 | PATCH；列表与 store 名称一致 |
| WF-06 | deletes workflow through DELETE API | Delete 删除 Workflow | DELETE；store 中移除 |
| WF-07 | allocates platform IDs with correct prefix when dropping palette nodes | Palette 拖放节点分配 `task_`/`event_`/`gateway_` | `POST /ids`；ID 符合平台规范 |
| WF-08 | filters workflow list by keyword and status | 搜索框与状态下拉过滤 | 关键字/状态过滤结果正确 |
| WF-09 | keeps workflow action buttons wrapped without horizontal overflow | Actions 区域 `flex-wrap` 且无横向溢出 | `scrollWidth ≤ clientWidth` |
| WF-10 | keeps status filter dropdown at fixed 148px width | 状态下拉宽度固定 | `boundingBox.width === 148` |

**前置**：`mockBackendWithWorkflow` + `openFreshApp`；内存 mock 模拟 PostgreSQL 版 REST 契约（见 `e2e/helpers/modeler.js` 中 `mockWorkflowBackend`）。

---

## 新增用例 checklist

在提交新 E2E 用例前，请确认：

1. [ ] 用例放入语义最贴近的 spec 文件（或新建 `modeler-*.spec.js` 并更新本表模块前缀）
2. [ ] 在 `e2e/TEST_CASES.md` 追加一行，分配不重复的 ID
3. [ ] 复用 `e2e/helpers/modeler.js` 中的 helper，避免硬编码 locale 文案
4. [ ] 固定数据用 `importModel()` 注入，仅「Import 对话框」类用例使用 `page.once('dialog')`
5. [ ] 本地执行 `npm run test:e2e` 通过后再提交

### 建议的模块前缀（扩展用）

| 前缀 | 建议用途 |
|------|----------|
| MF | 建模器全局流程、工具栏、属性面板 |
| CV | 画布视口、连线、节点交互、Palette |
| CT | Participant、Sub-process、布局与归属 |
| NT | BPMN 元素类型与 DSL 字段 |
| RT | Compile / Run / Debug / Temporal 集成 |
| WF | Workflow Definition Repository、版本、平台 ID |
| I18 | 多语言切换（若未来需要） |
| A11y | 无障碍与键盘导航（若未来需要） |
