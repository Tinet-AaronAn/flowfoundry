# Timer Definition 设计（实现基线）

> **状态**：实现基线 v1.0 | 2026-07-07  
> 与 [gateway-design.md](./gateway-design.md) 互补：Event-based Gateway 用多个 Intermediate Event 竞争；本文定义 **单个 Timer 节点** 的等待语义。

---

## 1. 目标与原则

| 原则 | 说明 |
|------|------|
| **长等待归 Workflow** | 分钟级以上等待使用 Intermediate Event（Timer），不在 Activity 内 `sleep` |
| **BPMN 对齐** | 建模器暴露 `duration` / `date` / `cycle` 三种 Timer Definition 类型 |
| **运行时可配置** | `value`、`timezone` 支持 `${...}` 变量，便于按业务 slot 动态排期 |
| **确定性** | Temporal Workflow 内用 `Workflow.currentTimeMillis()` 求 delay，再 `Workflow.newTimer` |

**适用节点**（画布 `kind`）：

- `intermediateEvent`（主路径）
- `timerEvent` / `intermediateCatchEvent` / `boundaryEvent`（属性面板同样暴露 Timer Definition；Boundary 拖放后续开放）

语义层统一为 **`INTERMEDIATE_EVENT`** + `config.eventSubtype = timer`。

---

## 2. 配置结构

写入画布 / DSL 节点 `config.timerDefinition`：

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
| `type` | 否（默认 `duration`） | 全部 | `duration` \| `date` \| `cycle` |
| `value` | 是 | 全部 | 字面量或 `${...}` 表达式；见 §3 |
| `timezone` | 否 | `date` | IANA 时区，如 `Asia/Shanghai`；可 `${slot.timezone}`；缺省为 JVM 默认时区 |
| `pastTargetStrategy` | 否 | `date` | 目标时间已过时：`fireImmediately`（默认）\| `skip` \| `fail` |

### 2.1 遗留字段 `duration`

早期 DSL / 测试可能只有扁平字段：

```json
{ "eventSubtype": "timer", "duration": "100ms" }
```

Runtime 仍兼容：`TimerEvaluator` 在无 `timerDefinition` 时回退读取 `config.duration`，视为 `type=duration`。

### 2.2 DSL 归一化（`buildDsl` / `runtimeConfig`）

| `timerDefinition.type` | 编译进 ExecutionPlan | 说明 |
|------------------------|----------------------|------|
| `duration` | 额外写入 `config.duration`（归一化后） | 便于旧逻辑与测试读取 |
| `date` | **保留完整** `timerDefinition` | 含 `timezone`、`pastTargetStrategy` |
| `cycle` | 保留 `timerDefinition` | Runtime 暂不支持（见 §3.3） |

`${...}` 变量 **不会** 在 DSL 阶段被替换成默认值（例如不再把 `${waitMs}` 改成 `1m`）。

---

## 3. Timer 类型与 `value` 解析

解析入口：`com.tinet.flowfoundry.interpreter.runtime.TimerEvaluator`。

执行时先对 `value`（及 `timezone`）做 **变量解析**（§4），再按 `type` 计算 `delayMs`。

### 3.1 `duration` — 相对等待

`delayMs = parseDuration(resolvedValue)`。

支持的格式：

| 格式 | 示例 | 说明 |
|------|------|------|
| 简写 | `100ms`、`30s`、`5m`、`2h` | 与历史行为一致 |
| ISO-8601 Duration | `PT1M`、`PT1H30M` | `java.time.Duration.parse` |

`delayMs <= 0` 时不调用 Temporal Timer，立即继续。

### 3.2 `date` — 绝对时间点

`delayMs = targetEpochMs - nowEpochMs`（`now` 在 Workflow 内为 `Workflow.currentTimeMillis()`）。

**`value` 解析为 epoch 毫秒的优先级：**

1. 变量结果为 **Number** → 直接作为 epoch ms  
2. 纯数字字符串 → `Long.parseLong`  
3. **ISO Instant** → `Instant.parse`，如 `2026-07-08T10:00:00Z`  
4. **本地日期时间** → `LocalDateTime.parse` + `timezone`（或 JVM 默认）→ `ZonedDateTime` → epoch ms  

典型业务配置（外呼下一轮固定时刻）：

```json
{
  "slot": {
    "fixedTime": "2026-07-08T18:00:00",
    "timezone": "Asia/Shanghai"
  }
}
```

```json
{
  "timerDefinition": {
    "type": "date",
    "value": "${slot.fixedTime}",
    "timezone": "${slot.timezone}",
    "pastTargetStrategy": "fireImmediately"
  }
}
```

**`pastTargetStrategy`（`delayMs <= 0` 时）：**

| 策略 | 行为 |
|------|------|
| `fireImmediately`（默认） | `delayMs = 0`，不等待，继续后续节点 |
| `skip` | 与 `fireImmediately` 相同（v1 均立即继续；语义位保留供 trace/指标扩展） |
| `fail` | 抛出 `IllegalStateException`，Workflow 失败 |

### 3.3 `cycle` — 周期（未实现）

建模器可选 `cycle`，Runtime **v1 不支持**：执行到该节点时抛出 `UnsupportedOperationException`。  
周期调度请用 **Gateway 回边 + `date`/`duration` Timer** 或后续版本实现。

---

## 4. 变量解析

`value`、`timezone` 均支持 FlowFoundry 变量语法（与 [variable-design.md](./variable-design.md) 一致）：

| 写法 | 行为 |
|------|------|
| `${slot.fixedTime}` | 整段为单变量 → `VariableStore.resolve` 的原始类型保留（Number / String 等） |
| `${prefix}m` | 模板替换 → 拼接后再按 `duration` 解析 |
| `Asia/Shanghai` | 无 `${` → 字面量 |

路径规则同 `VariableStore`：`slot.fixedTime`、`$.vars.round`、`$.input.maxRounds` 等。

**注意**：Timer 节点 **不向 `vars` 写入** 变量；仅在 **进入节点时读取** 当前 `VariableStore` 解析 `value`。上游 Activity 的 `outputMapping` 应先写好 `slot.fixedTime` 再进入 Timer。

---

## 5. Interpreter 与 Temporal 映射

```text
INTERMEDIATE_EVENT（eventSubtype: timer）
  -> TimerEvaluator.evaluate(node, variables, nowEpochMs)
  -> delayMs
  -> Workflow.newTimer(Duration.ofMillis(delayMs))   // delayMs > 0
  -> 继续下一节点
```

| 组件 | 职责 |
|------|------|
| `TimerEvaluator` | 读 `timerDefinition` / `duration`，变量解析，算 `delayMs` |
| `FlowInterpreterEngine` | `executeIntermediateEvent` 调用 `port.executeTimer` |
| `FlowInterpreterWorkflowImpl` | Temporal `Workflow.newTimer`；Event Gateway race 时 `waitForEventNode` 同样走 `TimerEvaluator` |

### 5.1 Event-based Gateway

出边首节点须为 `INTERMEDIATE_EVENT`（timer 或 signal）。多条 timer 分支在 `raceEventBranches` 中 **并行等待**，先完成者获胜；获胜分支随后再走一次 `executeIntermediateEvent`（与 Gateway 设计一致，见 [gateway-design.md](./gateway-design.md)）。

`date` 类型在 race 中按各自解析后的 `delayMs` 竞争；已过期且 `fireImmediately` 的分支 `delayMs=0`，更容易胜出。

### 5.2 web-modeler 联调（Stub）

当 `runSource=web-modeler` 且请求头 `X-FlowFoundry-Client: web-modeler` 同时成立时，`executeTimer` **跳过** `Workflow.newTimer`（与历史 `Workflow.sleep` 跳过行为一致），便于画布快速跑通。

---

## 6. 建模器

属性面板 **Timer Definition**：

- **Type**：`duration` / `date` / `cycle`
- **Value**：占位符提示随类型变化（如 `1m` vs `2026-07-08T10:00:00 / ${slot.fixedTime}`）
- **Timezone**、**Past Target Strategy**：仅 `type=date` 时显示

实现：`modeler-render.js`（面板）、`modeler-actions.js`（`updateTimer`）、`modeler-dsl-runtime.js`（`runtimeConfig` 归一化）。

---

## 7. 与 Activity sleep 的边界

| 场景 | 推荐 |
|------|------|
| 等 30s 重试间隔 | `duration` Timer 或 Gateway 回边上的短 Timer |
| 等下一轮外呼时刻（slot + 时区） | `date` Timer + `${slot.fixedTime}` |
| Activity 内 `Thread.sleep` | **禁止**（占用 Worker、无持久化 Timer） |

---

## 8. 测试

| 测试类 | 覆盖 |
|--------|------|
| `TimerEvaluatorTest` | duration / date、变量、timezone、pastTargetStrategy、模板拼接 |
| `FlowInterpreterEngineTest` | `${waitMs}` duration 变量、Event Gateway timer race |

---

## 9. 相关文档

- [entity-naming.md](./entity-naming.md) — `timerDefinition` 字段表与跨层对照
- [variable-design.md](./variable-design.md) — `VariableStore` 路径与节点读写边界
- [gateway-design.md](./gateway-design.md) — Event-based Gateway 与 Timer 竞争
- [detailed-design.md](./detailed-design.md) — Events 总览与 Temporal 映射

---

*文档版本：v1.0 实现基线*
