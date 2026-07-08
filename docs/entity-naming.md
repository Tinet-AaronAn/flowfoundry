# FlowFoundry 实体命名与分层定义

本文档是**画布 → Flow DSL → FlowFoundry 语义层 → Temporal** 四层命名的权威对照表。  
Temporal 层命名**固定不动**；上层命名以本文为准。

相关文档：

- [detailed-design.md](./detailed-design.md) — 节点语义、Palette 分类、扩展字段细节
- [business-orchestration-architecture.md](./business-orchestration-architecture.md) — 架构摘要

---

## 1. 四层总览

```text
┌─────────────────────────────────────────────────────────────────┐
│  L1 画布层 Canvas Model（model_json，含 UI 坐标/样式）            │
│      字段 kind / name / config / participantId …                 │
└────────────────────────────┬────────────────────────────────────┘
                             │ buildDsl() 过滤非运行节点、归一化 kind
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│  L2 Flow DSL（REST /api/flows/compile 契约）                     │
│      dslVersion + flow + nodes[].kind(UPPER) + edges             │
└────────────────────────────┬────────────────────────────────────┘
                             │ FlowCompiler.compile()
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│  L3 FlowFoundry 语义层（ExecutionPlan / NodeKind / Interpreter） │
│      6 种 NodeKind；边上 Safe FEEL 条件；config 扩展               │
└────────────────────────────┬────────────────────────────────────┘
                             │ FlowInterpreterWorkflowImpl
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│  L4 Temporal 层（★ 不改动）                                       │
│      Workflow / Activity / Child Workflow / Timer / Signal       │
└─────────────────────────────────────────────────────────────────┘
```

**命名原则**

| 原则 | 说明 |
|------|------|
| 每层有独立词汇 | 画布用 BPMN 风格 `kind`；DSL/语义层用 `NodeKind` 枚举 |
| 字段名按层固定 | 画布/DSL 边用 `from`/`to`；BPMN 导出用 `sourceRef`/`targetRef` |
| 平台扩展统一前缀 | 画布 `config` 内平台字段以 `flowFoundry` 开头 |
| Temporal 零重命名 | 不修改 Workflow 类名、Activity type、Task Queue 约定 |

---

## 2. L1 画布层（Canvas Model）

持久化在 `workflow_version.model_json`。节点核心字段：

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | string | 节点 ID，平台分配带前缀短 ID（见 §7） |
| `kind` | string | **画布元素类型**（camelCase，BPMN 风格） |
| `name` | string | 画布显示名（业务可读） |
| `config` | object | 元素配置 + `flowFoundry*` 扩展 |
| `participantId` | string? | 所属泳道 Participant |
| `activityType` | string? | Service/Script Task 绑定的 Registry 类型 |
| `x`, `y`, `width`, `height` | number | 布局（DSL 不包含） |

流程级 `process` 元数据（仅存于画布 `model_json`，**不进入 Flow DSL**）：

| 字段 | 说明 |
|------|------|
| `edgeRouting` | 连线画法：`orthogonal`（圆角正交，默认）\| `curved`（曲线） |
| `isExecutable` | BPMN 兼容标记，画布编辑用 |

边（Sequence Flow）：

| 字段 | 说明 |
|------|------|
| `id` | 边 ID |
| `from` / `to` | 源/目标节点 ID |
| `condition` | 边上条件，`default` 表示默认分支 |

### 2.1 Palette 分类与画布元素

| Palette 分组 | 画布 label（EN） | `kind`（规范值） | 参与 DSL 编译 | 说明 |
|--------------|------------------|------------------|---------------|------|
| **Events** | Start Event | `startEvent` | ✅ | 流程唯一入口 |
| | End Event | `endEvent` | ✅ | 路径终点，可多个 |
| | Intermediate Event | `intermediateEvent` | ✅ | 流程中事件等待；MVP 默认 subtype=`timer` |
| | Generic Task | `task` | ❌ | 草图占位，不参与 DSL 编译 |
| | Service Task | `serviceTask` | ✅ | 调用 Activity Registry 业务动作 |
| | Human Task | `humanTask` | ✅ | 人工任务 |
| | Script Task | `scriptTask` | ✅ | 脚本/决策，编译为 `ACTIVITY` + `script-runtime` |
| **Child Workflows** | Child Workflow | `workflow` | ✅ | 调用已发布子流程 |
| **Gateways** | Exclusive Gateway | `exclusiveGateway` | ✅ | 排他分支（v1 仅 split，无 join） |
| | Parallel Gateway | `parallelGateway` | ✅ | 并行分支；split 须配合同类型 join |
| | Inclusive Gateway | `inclusiveGateway` | ✅ | 包容分支；split 须配合同类型 join |
| | Event-based Gateway | `eventBasedGateway` | ✅ | 事件竞争（v1 仅 split，无 join） |
| **Structural** | Sub-process | `subProcess` | ❌ | 纯结构容器，内部节点展开编译 |
| | Participant | `participant` | ❌ | 泳道/参与方边界 |
| **Annotations** | Text Annotation | `textAnnotation` | ❌ | 文档注释 |

### 2.2 画布 `config` 平台扩展字段

| 键 | 适用 `kind` | 用途 |
|----|-------------|------|
| `flowFoundryHumanTask` | `humanTask` | `{ mode: "managed" }`（legacy `offline` 归一化为 `managed`） |
| `flowFoundryAssignmentDefinition` | `humanTask` | `{ candidateGroups, assignee }` |
| `flowFoundryTaskDefinition` | `serviceTask` | 设计期元数据（type/retries），非运行时必需 |
| `taskHeaders` | `serviceTask`、`scriptTask`、`humanTask` 等 Activity 节点 | 静态键值元数据；编译进 `ExecutionNode.config`，运行时经 `_config.taskHeaders` 传给 Activity |
| `flowFoundryChildWorkflow` | `workflow` | DSL 编译时写入；引用 `{ childWorkflowId, childWorkflowVersion, name }` — 见 [child-workflow-design.md](./child-workflow-design.md) |
| `flowFoundryParticipant` | `participant` | `{ participantRef }` |
| `flowFoundryParticipant`（DSL） | 任意运行节点 | 编译时从 `participantId` 注入泳道元数据 |
| `subtype` | `intermediateEvent` | 事件子类型：`timer`（MVP）、未来 `message` / `signal` |
| `startEventSubtype` | `startEvent` | `none`（默认）\| `timer`（Temporal Schedule）— 见 [timer-design.md](./timer-design.md) |
| `timerDefinition` | `intermediateEvent`（subtype=timer） | `{ type: duration\|date, value, timezone?, pastTargetStrategy? }` |
| `timerDefinition` | `startEvent`（startEventSubtype=timer） | `{ type: cycle\|date, value, timezone? }` — Schedule 用字面量 |
| `childWorkflowId` / `childWorkflowVersion` / `childWorkflowName` | `workflow` | 画布编辑期字段，编译进 `flowFoundryChildWorkflow` |
| `decisionRef` / `decisionVersion` | `scriptTask` | 脚本引用（节点级字段也会出现在 DSL 顶层） |
| `flowFoundryLoop` | `ACTIVITY` | `{ mode, condition?, collection?, elementVar?, indexVar?, iterationVar?, maxIterations?, sequential? }` — 见 [loop-design.md](./loop-design.md) |

---

## 3. L2 Flow DSL

由建模器 `buildDsl()` 生成，经 `POST /api/flows/compile` 提交。  
**与画布的区别**：去掉坐标/UI；非运行节点已过滤；`kind` 已升为语义层枚举。

### 3.1 文档根结构

```json
{
  "dslVersion": "1.0",
  "flow": {
    "id": "workflow_k3m9x2p1",
    "name": "外呼主流程",
    "version": "1.0.0"
  },
  "inputs": { },
  "variables": { },
  "nodes": [ ],
  "edges": [ { "from": "", "to": "", "condition": "default" } ]
}
```

| 字段 | 说明 |
|------|------|
| `flow.id` / `flow.name` / `flow.version` | 流程标识；**不含**画布 UI 字段（如 `edgeRouting`） |
| 节点类型字段名 | **`kind`**（UPPER_SNAKE 枚举字符串） |
| 边端点 | **`from` / `to`** |

### 3.2 DSL 节点 `kind`（= L3 NodeKind wire 值）

| DSL `kind` | 来源画布 `kind` | 运行时行为摘要 |
|------------|-----------------|----------------|
| `START` | `startEvent` | 入口；`startEventSubtype=timer` 时由 Temporal Schedule 触发，节点本身不 sleep |
| `END` | `endEvent` | 到达后结束当前路径 |
| `ACTIVITY` | `serviceTask`, `scriptTask`, `humanTask` | 经 `dynamic-activity-router` 调 Activity；Human Task 使用 `activityType: human-task` |
| `INTERMEDIATE_EVENT` | `intermediateEvent` | 按 `config.eventSubtype` 等待（timer → `TimerEvaluator` + Temporal Timer） |
| `GATEWAY` | 四种 `*Gateway` | 按 `config.gatewayKind` 路由（MVP 仅 `exclusive` 完整支持） |
| `CHILD_WORKFLOW` | `workflow` | 启动子 `FlowInterpreterWorkflow`（Temporal Child Workflow）— 见 [child-workflow-design.md](./child-workflow-design.md) |

### 3.3 DSL 节点 `config` 扩展

| 键 | 适用 DSL `kind` | 说明 |
|----|-----------------|------|
| `gatewayKind` | `GATEWAY` | `exclusive` \| `parallel` \| `inclusive` \| `eventBased` |
| `eventSubtype` | `INTERMEDIATE_EVENT` | `timer`（MVP）、未来 `message` / `signal` |
| `startEventSubtype` | `START` | `none` \| `timer` |
| `timerDefinition` | `INTERMEDIATE_EVENT`（timer） | `duration` / `date`；`duration` 类型可额外归一化 `duration` 字段 |
| `timerDefinition` | `START`（timer） | `cycle` / `date` — 见 [timer-design.md](./timer-design.md) |
| `duration` | `INTERMEDIATE_EVENT`（timer） | 遗留 / 归一化后的相对等待时长（`type=duration`） |
| `flowFoundryHumanTask` | `ACTIVITY`（`activityType: human-task`） | 人工任务模式 |
| `taskHeaders` | `ACTIVITY` | 静态 Task Headers；Activity 从 `_config.taskHeaders` 读取 |
| `flowFoundryChildWorkflow` | `CHILD_WORKFLOW` | 子流程引用元数据 |
| `childWorkflowDefinition` / `childExecutionPlan` | `CHILD_WORKFLOW` | 内嵌子 DSL / 编译产物 — 见 [child-workflow-design.md](./child-workflow-design.md) |

### 3.4 Service Task vs Script Task（画布细分，DSL 合并）

| 画布 `kind` | DSL `kind` | 区分方式 |
|-------------|------------|----------|
| `serviceTask` | `ACTIVITY` | `activityType` = Registry 业务类型 |
| `scriptTask` | `ACTIVITY` | `activityType` = `script-runtime`（平台 core） |
| `humanTask` | `ACTIVITY` | `activityType` = `human-task`（平台 core） |

---

## 4. L3 FlowFoundry 语义层

Java 包：`com.tinet.flowfoundry.interpreter.model.*`、`com.tinet.flowfoundry.flow.*`

### 4.1 核心类型

| 类型 | 职责 |
|------|------|
| `FlowDefinition` | DSL 反序列化载体（`FlowNode`, `FlowEdge`, `FlowMetadata`） |
| `FlowCompiler` | DSL → `ExecutionPlan` 静态校验与编译 |
| `ExecutionPlan` | 不可变运行计划（`flowId`, `version`, `startNodeId`, `nodes`, `edges`） |
| `ExecutionNode` | 单节点：`id`, `NodeKind kind`, `activityType`, `config`, mappings… |
| `ExecutionEdge` | 出边 + 编译后的 Safe FEEL 条件 AST |
| `FlowInterpreterWorkflow` | 唯一通用 Temporal Workflow 接口 |
| `FlowInterpreterWorkflowImpl` | 按 `NodeKind` 解释执行 |
| `InterpreterStatus` | `RUNNING`, `WAITING_HUMAN_TASK`, `COMPLETED`, `FAILED` |

### 4.2 NodeKind 枚举（权威）

```java
START | END | ACTIVITY | GATEWAY | INTERMEDIATE_EVENT | CHILD_WORKFLOW
```

| NodeKind | Interpreter 行为 |
|----------|------------------|
| `START` | 无操作，选下一边；**仅允许 1 条出边**（分支经 Gateway） |
| `END` | 标记 COMPLETED |
| `ACTIVITY` | `executeActivity` → router（含 `script-runtime`、`human-task` 与业务 Registry 类型） |
| `GATEWAY` | 按 `gatewayKind` 选边 |
| `INTERMEDIATE_EVENT` | 按 `eventSubtype` 等待（timer → `TimerEvaluator` + Temporal Timer） |
| `CHILD_WORKFLOW` | Child Workflow stub（同步等待子 `FlowInterpreterWorkflow` 完成）— 见 [child-workflow-design.md](./child-workflow-design.md) |

Human Task 编译为 `ACTIVITY` + `activityType: human-task`；managed 模式下 Interpreter 在 Activity 后 `Workflow.await` 等待 `completeHumanTask` Signal。

---

## 5. L4 Temporal 层（固定，不改动）

| Temporal 概念 | FlowFoundry 用法 |
|---------------|------------------|
| **Workflow 类型** | `FlowInterpreterWorkflow` |
| **Router Activity** | `dynamic-activity-router` |
| **Child Workflow** | 同类型 `FlowInterpreterWorkflow` |
| **定时等待** | `Workflow.newTimer`（`INTERMEDIATE_EVENT` + `eventSubtype=timer`；见 [timer-design.md](./timer-design.md)） |
| **Human Task** | `Workflow.await` + Signal `completeHumanTask` |

---

## 6. 跨层对照主表

| 业务概念 | 画布 label | 画布 `kind` | DSL `kind` | NodeKind | Temporal |
|----------|-----------|-------------|------------|----------|----------|
| 开始 | Start Event | `startEvent` | `START` | `START` | `startNodeId` |
| 结束 | End Event | `endEvent` | `END` | `END` | 路径完成 |
| 业务动作 | Service Task | `serviceTask` | `ACTIVITY` | `ACTIVITY` | Activity via router |
| 脚本/决策 | Script Task | `scriptTask` | `ACTIVITY` | `ACTIVITY` | Platform `script-runtime` → Node.js |
| 人工任务 | Human Task | `humanTask` | `ACTIVITY` | `ACTIVITY` | Platform `human-task` + Signal |
| 流程中事件 | Intermediate Event | `intermediateEvent` | `INTERMEDIATE_EVENT` | `INTERMEDIATE_EVENT` | Temporal Timer / signal（按 subtype；见 [timer-design.md](./timer-design.md)） |
| 排他网关 | Exclusive Gateway | `exclusiveGateway` | `GATEWAY` | `GATEWAY` | 条件选边 |
| 并行网关 | Parallel Gateway | `parallelGateway` | `GATEWAY` | `GATEWAY` | 按 `gatewayKind=parallel` 路由 |
| 包容网关 | Inclusive Gateway | `inclusiveGateway` | `GATEWAY` | `GATEWAY` | 按 `gatewayKind=inclusive` 路由 |
| 事件网关 | Event-based Gateway | `eventBasedGateway` | `GATEWAY` | `GATEWAY` | 按 `gatewayKind=eventBased` 路由 |
| 子流程 | Child Workflow | `workflow` | `CHILD_WORKFLOW` | `CHILD_WORKFLOW` | Temporal Child Workflow（见 [child-workflow-design.md](./child-workflow-design.md)） |
| 子图容器 | Sub-process | `subProcess` | — | — | 内部节点展开 |
| 泳道 | Participant | `participant` | — | — | 元数据 |
| 注释 | Text Annotation | `textAnnotation` | — | — | — |
| 草图任务 | Generic Task | `task` | — | — | 不参与 DSL 编译 |

Gateway 子类型通过 `config.gatewayKind` 保留：`exclusive` / `parallel` / `inclusive` / `eventBased`。

Intermediate Event 子类型通过 `config.eventSubtype` 保留：`timer`（MVP）/ 未来 `message` / `signal`。Intermediate Timer 支持 `duration` / `date`；周期调度使用 Start Event `startEventSubtype=timer` + `cycle`，见 [timer-design.md](./timer-design.md)。

---

## 7. 平台 ID 前缀（画布实体）

`POST /api/workflows/ids` 的 `kind` 参数 → ID 前缀：

| API `kind` | ID 前缀 | 对应画布元素 |
|------------|---------|--------------|
| `workflow` | `workflow_` | 流程定义（非画布节点） |
| `event` | `event_` | Start / End / Intermediate Event |
| `task` | `task_` | Service / Human / Script / Generic Task |
| `gateway` | `gateway_` | 各 Gateway |
| `subprocess` | `subprocess_` | Sub-process |
| `participant` | `participant_` | Participant |

**设计期定义 ID**（`workflow_{8位短id}`，如 `workflow_k3m9x2p1`）与 **Temporal 运行实例 ID** 不同：

| 来源 | 运行实例 Workflow ID 格式 |
|------|---------------------------|
| Web Modeler 测试 Run | `workflow_test_{flowId}_{uuid}` |
| 生产 / 对外 API | `workflow_{flowId}_{uuid}` |
| 子流程（测试） | `workflow_test_child_{childFlowId}_{businessKey}` |
| 子流程（生产） | `workflow_child_{childFlowId}_{businessKey}` |

---

## 8. 代码锚点

| 层 | 主要文件 |
|----|----------|
| 画布 → DSL | `modeler-dsl-runtime.js` |
| Palette | `modeler-state.js` |
| DSL 编译 | `FlowCompiler.java` |
| 语义模型 | `NodeKind.java`, `ExecutionNode.java`, `ExecutionPlan.java` |
| 解释执行 | `FlowInterpreterWorkflowImpl.java` |
| E2E | `e2e/modeler-node-types.spec.js` |
