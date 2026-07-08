# Child Workflow 设计（实现基线）

> **状态**：实现基线 v1.0 | 2026-07-07  
> 画布 `kind=workflow`（Palette：**Child Workflow**）→ DSL `CHILD_WORKFLOW` → Temporal **Child Workflow**（同为 `FlowInterpreterWorkflow`）。

---

## 1. 定位：与 Activity / Sub-process 的区别

| | **Service Task（Activity）** | **Sub-process** | **Child Workflow** |
|---|------------------------------|-----------------|-------------------|
| 画布 `kind` | `serviceTask` 等 | `subProcess` | `workflow` |
| 语义 | 调用单个业务能力 | 结构分组 / 折叠展示 | 调用另一条完整流程 |
| 执行 | Router → 一个 Activity | 节点**展开**编进父 ExecutionPlan | **独立** Temporal Child Workflow |
| 版本 | 随父流程 | 随父流程 | 可独立版本、独立发布 |
| 变量 | 父流程同一 `VariableStore` | 展开后同一作用域 | **隔离**；靠 input/output mapping |
| Palette | Activities | Structural（不参与 DSL 运行） | Child Workflows |

**选型原则：**

- 同版本、同发布单元内的「画图分组」→ **Sub-process**
- 跨流程复用、独立治理、独立 History（身份核验、完整外呼子流程等）→ **Child Workflow**

---

## 2. 配置结构

### 2.1 画布 / DSL

```json
{
  "id": "Call_Child",
  "kind": "CHILD_WORKFLOW",
  "config": {
    "childWorkflowId": "workflow_k3m9x2p1",
    "childWorkflowVersion": "1.0.0",
    "childWorkflowName": "客户身份核验",
    "childTaskQueue": "",
    "flowFoundryChildWorkflow": {
      "childWorkflowId": "workflow_k3m9x2p1",
      "childWorkflowVersion": "1.0.0",
      "name": "客户身份核验"
    },
    "childWorkflowDefinition": { "...": "所选版本的完整 FlowDefinition DSL" },
    "childExecutionPlan": { "...": "编译后的 ExecutionPlan（仅编译产物）" }
  },
  "inputMapping": {
    "campaignId": "$.input.campaignId",
    "roundNumber": "$.vars.roundNumber"
  },
  "outputMapping": {
    "verifyStatus": "status"
  }
}
```

| 字段 | 说明 |
|------|------|
| `childWorkflowId` | 引用的已发布流程 ID（建模器 Workflow 列表） |
| `childWorkflowVersion` | 引用版本；`buildDsl()` 时解析为对应版本 DSL |
| `childWorkflowName` | 展示名 |
| `childTaskQueue` | 子 Workflow 使用的 Temporal task queue；空则回退节点 `taskQueue` / Registry 默认 |
| `flowFoundryChildWorkflow` | 编译期元数据摘要 |
| `childWorkflowDefinition` | **内嵌**子流程 DSL（建模器 `buildDsl()` 写入） |
| `childExecutionPlan` | `FlowCompiler` 将内嵌 DSL 编译后的运行计划 |

### 2.2 建模器属性面板

- **Workflow**：下拉选择已保存流程（**不可选当前正在编辑的流程**）
- **Version**：子流程版本
- **Child Task Queue**：子 Worker 队列
- **Input / Output Mapping**：在 Runtime 区域配置（见 [variable-design.md §8.1](./variable-design.md#81-子流程child_workflow)）

---

## 3. 编译

`FlowCompiler.compileChildWorkflowConfig`：

1. 要求 `childWorkflowId` **或** `childWorkflowDefinition` 至少一项非空
2. 若存在 `childWorkflowDefinition` → 递归 `compile()` → 写入 `childExecutionPlan`
3. 节点 `kind` 升为 `CHILD_WORKFLOW`；trace 中 `activityType = child-workflow`

**重要**：Runtime **不**按 `childWorkflowId` 单独去库拉定义；必须有编译进 ExecutionPlan 的 **`childExecutionPlan`**。仅有 ID、无内嵌定义时，运行到该节点会失败。

建模器在 `buildDsl()` / `runtimeConfig()` 中通过 `childWorkflowDefinition(node)` 嵌入子流程：

- 从 `state.workflows` 按 `childWorkflowId` + `childWorkflowVersion` 查找
- `seenWorkflowIds` 防止嵌套时**循环引用**同一流程

---

## 4. 运行时执行

```text
父 FlowInterpreterWorkflow 走到 CHILD_WORKFLOW 节点
  → childInput = inputMapping 结果（见 §5）
  → childBusinessKey = 父 businessKey + "/" + 节点 id
  → Workflow.newChildWorkflowStub(FlowInterpreterWorkflow)
       .setWorkflowId(WorkflowRunId.forChildWorkflow(...))
       .setTaskQueue(childTaskQueue)
  → child.run(childExecutionPlan, childBusinessKey, childInput, runSource)
  → 父流程阻塞直至子流程结束（同步 Child Workflow）
  → 返回 InterpreterState
  → applyOutput(outputMapping) 写回父 vars
  → 沿出边继续
```

实现：`FlowInterpreterEngine` → `FlowInterpreterWorkflowImpl.executeChildWorkflow`。

### 4.1 Temporal Workflow ID

| 环境 | 父流程 ID | 子流程 ID |
|------|-----------|-----------|
| 生产 `runSource=production` | `workflow_{flowId}_{uuid}` | `workflow_child_{childFlowId}_{businessKey}` |
| 建模器联调 `web-modeler` | `workflow_test_{flowId}_{uuid}` | `workflow_test_child_{childFlowId}_{businessKey}` |

其中子流程 `businessKey` = `{父 businessKey}/{子节点 id}`。

### 4.2 子流程返回值

子 `run()` 返回 **`InterpreterState`**（`flowId`、`version`、`status`、`variables`、`lastResult` 等）。父节点 `outputMapping` 的 **source** 从该对象解析（支持 record 字段路径）。

---

## 5. 变量传递

```text
childInput = buildInput(父 VariableStore, inputMapping, inputMappingMode)
若 childInput 为空 → childInput = 父 vars（不含 $.input）
子 VariableStore.input = childInput
子流程 vars 与父隔离
父 applyOutput(子 InterpreterState, outputMapping)
```

| 方向 | 行为 |
|------|------|
| **父 → 子** | `inputMapping` 显式映射；**无 mapping 时默认只传父 `vars`，不传父 `input`** |
| **子 → 父** | `outputMapping` 从子 `InterpreterState` 回写父 `vars` |
| **隔离** | 子流程运行期修改不会自动可见于父流程 |

示例：父 `input.campaignId` 须进子流程时：

```json
"inputMapping": {
  "campaignId": "$.input.campaignId"
}
```

---

## 6. v1 限制与注意事项

| # | 限制 | 说明 |
|---|------|------|
| 1 | **编译期内嵌** | 子流程版本在 compile / 发布时锁定；非运行时按 ID 拉「最新版」 |
| 2 | **同步等待** | 父节点阻塞至子流程结束；无内置 async / fire-and-forget |
| 3 | **子流程须已保存** | 建模器 Workflow 列表中须存在被引用流程，否则 DSL 无 `childWorkflowDefinition` |
| 4 | **禁止自引用与环** | 不能选当前流程；嵌套编译用 `seenWorkflowIds` 防环 |
| 5 | **默认不传父 input** | 无 mapping 时仅传 `vars` |
| 6 | **子流程内人工任务** | 子 Workflow 有独立 `workflowId`；完成人工任务须对**子** run 调 API |
| 7 | **Task Queue 可达** | `childTaskQueue` 对应 Worker 须注册子流程所需 Activity |
| 8 | **无节点级 Loop** | Loop 仅支持 `ACTIVITY`；Child Workflow 节点不可配 `flowFoundryLoop` |
| 9 | **出边** | 不受 Activity「单出边」约束（与 Start / Activity 不同） |

---

## 7. 与 Sub-process 对照（再述）

```text
Sub-process（subProcess）
  → 纯结构；内部节点展开进父 ExecutionPlan
  → 同一 Interpreter、同一变量作用域（展开后）

Child Workflow（workflow）
  → 独立 ExecutionPlan + 独立 Temporal Workflow History
  → 适合「客户身份核验」「完整外呼子流程」等可复用、可独立治理流程
```

---

## 8. 建模器与测试

- `modeler-dsl-runtime.js`：`childWorkflowDefinition()`、`executionNodeKind` → `CHILD_WORKFLOW`
- `e2e/modeler-runtime.spec.js`：父子流程 DSL 内嵌校验
- `FlowCompilerTest.compilesWorkflowNodeAsChildWorkflow`

---

## 9. 相关文档

- [entity-naming.md](./entity-naming.md) — `CHILD_WORKFLOW` 跨层对照
- [variable-design.md](./variable-design.md) — input/output mapping 与 `VariableStore`
- [detailed-design.md](./detailed-design.md) — §4.5 Sub-process vs §4.6 Child Workflow
- [workflow-development-guide.md](./workflow-development-guide.md) — 业务开发入口

---

*文档版本：v1.0 实现基线*
