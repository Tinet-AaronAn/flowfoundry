# Timer Definition 设计（实现基线）

> **状态**：实现基线 v1.1 | 2026-07-07  
> 与 [gateway-design.md](./gateway-design.md) 互补：Event-based Gateway 用多个 Intermediate Event 竞争；本文定义 **Timer 节点** 的等待与 **Timer Start** 调度语义。

---

## 1. 目标与原则

| 原则 | 说明 |
|------|------|
| **长等待归 Workflow** | 分钟级以上等待使用 Intermediate Event（Timer），不在 Activity 内 `sleep` |
| **周期调度归 Start** | BPMN `timeCycle` 语义映射到 **Timer Start Event** + Temporal **Schedule** |
| **流程内重复** | Gateway 回边 + Intermediate `duration` / `date` Timer |
| **运行时可配置** | Intermediate 的 `value`、`timezone` 支持 `${...}` 变量 |
| **确定性** | Intermediate：Workflow 内 `Workflow.currentTimeMillis()` 求 delay，再 `Workflow.newTimer` |

**节点分工**：

| 节点 | Timer 类型 | 运行时 |
|------|-----------|--------|
| **Start Event**（`startEventSubtype=timer`） | `cycle` / `date` | Temporal **Schedule** 周期性或一次性启动流程 |
| **Intermediate Event**（`eventSubtype=timer`） | `duration` / `date` | `TimerEvaluator` + `Workflow.newTimer` |

**适用画布 `kind`**：

- **Start**：`startEvent`
- **Intermediate**：`intermediateEvent`（主路径）、`timerEvent` / `intermediateCatchEvent` / `boundaryEvent`

---

## 2. 配置结构

### 2.1 Intermediate Event — `config.timerDefinition`

```json
{
  "eventSubtype": "timer",
  "timerDefinition": {
    "type": "duration",
    "value": "5m"
  }
}
```

```json
{
  "eventSubtype": "timer",
  "timerDefinition": {
    "type": "date",
    "value": "${slot.fixedTime}",
    "timezone": "${slot.timezone}",
    "pastTargetStrategy": "fireImmediately"
  }
}
```

| 字段 | 必填 | 适用 `type` | 说明 |
|------|------|-------------|------|
| `type` | 否（默认 `duration`） | Intermediate | `duration` \| `date`（**不支持 `cycle`**） |
| `value` | 是 | 全部 | 字面量或 `${...}` 表达式；见 §3 |
| `timezone` | 否 | `date` | IANA 时区；可 `${slot.timezone}` |
| `pastTargetStrategy` | 否 | `date` | 目标已过时：`fireImmediately`（默认）\| `skip` \| `fail` |

### 2.2 Timer Start — `config.startEventSubtype` + `config.timerDefinition`

```json
{
  "startEventSubtype": "timer",
  "timerDefinition": {
    "type": "cycle",
    "value": "R/PT1H"
  }
}
```

```json
{
  "startEventSubtype": "timer",
  "timerDefinition": {
    "type": "date",
    "value": "2026-07-08T18:00:00",
    "timezone": "Asia/Shanghai"
  }
}
```

| 字段 | 说明 |
|------|------|
| `startEventSubtype` | `none`（默认，手动触发）\| `timer`（Temporal Schedule） |
| `timerDefinition.type` | `cycle` \| `date`（**不支持 `duration`**） |
| `timerDefinition.value` | **字面量**（Schedule 创建时不支持 `${...}` 变量） |
| `timerDefinition.timezone` | 仅 `date`；IANA 时区 |

流程 **激活（active）** 时，建模器调用 `POST /api/workflows/{id}/timer-schedule/sync` 同步 Schedule；**停用（retired）** 或删除流程时暂停/删除 Schedule。

### 2.3 遗留字段 `duration`

早期 DSL / 测试可能只有扁平字段：

```json
{ "eventSubtype": "timer", "duration": "100ms" }
```

Runtime 仍兼容：`TimerEvaluator` 在无 `timerDefinition` 时回退读取 `config.duration`，视为 `type=duration`。

### 2.4 DSL 归一化（`buildDsl` / `runtimeConfig`）

| 节点 | `timerDefinition.type` | 编译进 ExecutionPlan |
|------|------------------------|----------------------|
| Intermediate | `duration` | 额外写入 `config.duration`（归一化后） |
| Intermediate | `date` | 保留完整 `timerDefinition` |
| Start | `cycle` / `date` | 保留 `startEventSubtype` + `timerDefinition` |

`${...}` 变量 **不会** 在 DSL 阶段被替换成默认值。

---

## 3. Timer 类型与 `value` 解析

解析入口：`com.tinet.flowfoundry.interpreter.runtime.TimerEvaluator`（Intermediate）、`CycleTimerExpression` + `StartTimerScheduleMapper`（Timer Start）。

### 3.1 `duration` — 相对等待（仅 Intermediate）

`delayMs = parseDuration(resolvedValue)`。

| 格式 | 示例 |
|------|------|
| 简写 | `100ms`、`30s`、`5m`、`2h` |
| ISO-8601 Duration | `PT1M`、`PT1H30M` |

`delayMs <= 0` 时不调用 Temporal Timer，立即继续。

### 3.2 `date` — 绝对时间点

**Intermediate**：`delayMs = targetEpochMs - nowEpochMs`（支持变量）。

**Timer Start**：映射为 Schedule 的 `startAt`（一次性触发）；激活时目标时间须在未来。

**`pastTargetStrategy`（Intermediate，`delayMs <= 0` 时）**：

| 策略 | 行为 |
|------|------|
| `fireImmediately`（默认） | 不等待，继续 |
| `skip` | 与 `fireImmediately` 相同（v1） |
| `fail` | 抛出 `IllegalStateException` |

### 3.3 `cycle` — 周期（仅 Timer Start）

BPMN `timeCycle` 表达式，如 `R/PT1H`、`R3/PT10M`、`R/2026-07-08T09:00:00/PT1H`。

- **解析**：`CycleTimerExpression.parse`
- **运行时**：`StartTimerScheduleMapper` → Temporal `ScheduleSpec`（interval + 可选 `startAt`）
- **Intermediate 禁止**：编译期 `TimerDefinitionRules.validateIntermediate` 与运行期 `TimerEvaluator` 均拒绝 `cycle`

流程内需要重复执行时，使用 **Gateway 回边 + `duration`/`date` Intermediate Timer**。

---

## 4. 变量解析（Intermediate）

`value`、`timezone` 支持 FlowFoundry 变量语法（与 [variable-design.md](./variable-design.md) 一致）。

**Timer Start Schedule 不支持变量**（激活时须为字面量 cycle/date）。

---

## 5. Interpreter 与 Temporal 映射

### 5.1 Intermediate Event

```text
INTERMEDIATE_EVENT（eventSubtype: timer）
  -> TimerEvaluator.evaluate(node, variables, nowEpochMs)
  -> delayMs
  -> Workflow.newTimer(Duration.ofMillis(delayMs))   // delayMs > 0
  -> 继续下一节点
```

### 5.2 Timer Start Event

```text
START（startEventSubtype: timer）
  -> 编译通过；执行计划 START 节点不 sleep
  -> 流程激活时 StartTimerScheduleService.syncFromDefinition
  -> Temporal Schedule -> FlowInterpreterWorkflow（每次触发新实例）
```

| 组件 | 职责 |
|------|------|
| `TimerDefinitionRules` | Start / Intermediate 类型校验 |
| `CycleTimerExpression` | 解析 `R/...` cycle 表达式 |
| `StartTimerScheduleMapper` | cycle/date → `ScheduleSpec` |
| `StartTimerScheduleService` | create/update/pause/delete Schedule |
| `WorkflowTimerScheduleController` | `POST .../timer-schedule/sync`、`/pause` |

Schedule ID：`flowfoundry-timer-start-{workflowId}`。

### 5.3 Event-based Gateway

出边首节点须为 `INTERMEDIATE_EVENT`（timer 或 signal）。多条 timer 分支并行等待，先完成者获胜。见 [gateway-design.md](./gateway-design.md)。

### 5.4 web-modeler 联调（Stub）

`runSource=web-modeler` 且 `X-FlowFoundry-Client: web-modeler` 时，`executeTimer` **跳过** `Workflow.newTimer`。

---

## 6. 建模器

**Intermediate — Timer Definition**（`modeler-render.js` `timerSection`）：

- **Type**：`duration` / `date`（无 `cycle`）
- **Timezone**、**Past Target Strategy**：仅 `date`

**Start Event**（`startTimerSection`）：

- **Subtype**：`none` / `timer`
- **Timer Type**（timer 时）：`cycle` / `date`
- 激活流程时自动 sync Schedule（`modeler-storage.js`）

---

## 7. 与 Activity sleep 的边界

| 场景 | 推荐 |
|------|------|
| 等 30s 重试间隔 | Intermediate `duration` Timer 或 Gateway 回边 |
| 等下一轮外呼时刻 | Intermediate `date` + `${slot.fixedTime}` |
| 周期性自动启动流程 | Start `cycle` Timer + Temporal Schedule |
| Activity 内 `Thread.sleep` | **禁止** |

---

## 8. 测试

| 测试类 | 覆盖 |
|--------|------|
| `TimerEvaluatorTest` | duration / date、变量、cycle 拒绝 |
| `CycleTimerExpressionTest` | `R/PT1H` 解析 |
| `StartTimerScheduleMapperTest` | cycle → ScheduleSpec |
| `FlowCompilerTest` | Intermediate cycle 拒绝、Timer Start 编译 |

---

## 9. 相关文档

- [entity-naming.md](./entity-naming.md) — `startEventSubtype`、`timerDefinition` 字段表
- [gateway-design.md](./gateway-design.md) — Gateway 与 Event 竞争
- [variable-design.md](./variable-design.md) — 变量路径

---

*文档版本：v1.1 实现基线*
