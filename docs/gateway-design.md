# Gateway 通用设计（实现基线）

> **状态**：实现基线 v1.0 | 2026-07-04  
> 替代 [parallel-gateway-design.md](./parallel-gateway-design.md) 评审稿，作为 Parallel / Inclusive / Event-based / Exclusive 的统一规范。

---

## 1. 范围

| Gateway | 画布 `kind` | `gatewayKind` | v1 实现 |
|---------|-------------|---------------|---------|
| Exclusive | `exclusiveGateway` | `exclusive` | ✅ |
| Parallel | `parallelGateway` | `parallel` | ✅ |
| Inclusive | `inclusiveGateway` | `inclusive` | ✅ |
| Event-based | `eventBasedGateway` | `eventBased` | ✅（timer + signal） |

**Activity 约束**（已实现）：`ACTIVITY` 节点仅允许 **1 条出边**，且出边 **不得带 FEEL**；分支必须经 Gateway。

---

## 2. 拓扑：Split / Join 分离

Parallel、Inclusive **禁止**「多入且多出」。通过入/出边数量推导 `gatewayRole`：

| `gatewayRole` | 入边 | 出边 | 语义 |
|---------------|------|------|------|
| `split` | 1 | ≥ 2 | 分叉 |
| `join` | ≥ 2 | 1 | 汇合 |
| — | ≥ 2 且 ≥ 2 | — | **Compiler error** |

Exclusive / Event-based **仅使用 split 形态**（1 入 ≥ 2 出）。Exclusive 的 join 形态（多入 1 出）第一版不单独建模。

Event-based Split：每条出边 **首节点必须是 `INTERMEDIATE_EVENT`**（timer 或 signal）。

---

## 3. Exclusive Gateway

- Split：按出边 **priority** 顺序求值 FEEL，首条命中胜出；否则 default。
- 无 fork/join barrier。

---

## 4. Parallel Gateway

### 4.1 Split

- **忽略**出边 FEEL（Compiler 强制 default）。
- 所有出边 **同时** fork；Compiler 校验 split/join 配对，**禁止嵌套 Parallel**。

### 4.2 Join

- 等待 **全部** split 出边对应的分支到达。
- 释放后 **`lastResult = null`**（已确认）。
- **`vars` 合并**：并行分支写同一键 → **last-write-wins**；Compiler 对同一并行区域内 **重叠 outputMapping → error**（已确认）。

### 4.3 失败与取消（已确认）

| 阶段 | 策略 |
|------|------|
| **PG-v1** | 任一分支 Activity 失败 → 整体 `FAILED`；**不主动 cancel** 其他分支 Activity（软取消） |
| **PG-v2** | Registry 中 `cancellable: true` 的 Activity 在失败时接收 Temporal cancellation |

### 4.4 重叠 outputMapping（已确认）

并行 / 包容区域内，多个 Activity 的 `outputMapping` 写入同一 `vars` 键 → **Compiler error**（非 warn）。

### 4.5 嵌套 Parallel（已确认）

**第一版禁止嵌套**；Compiler 检测 split→join 之间再次出现 parallel split 时报错。

---

## 12. 评审结论（均已确认 2026-07-04）

| # | 项 | 结论 |
|---|-----|------|
| 1 | Join 后 `lastResult` | `null` |
| 2 | 重叠 outputMapping | **compile error** |
| 3 | 嵌套 Parallel | **第一版禁止** |
| 4 | 分支取消 | **v1 软取消**；**v2** 对 `cancellable: true` Activity 硬 cancel |

## 5. Inclusive Gateway

### 5.1 Split

- 按 priority 求值 FEEL，**激活 0~N 条**出边（true 的才 fork）。
- 全部 false 时走 default（若有）。
- 将 **激活出边 target 列表** 写入 join 节点 config（`activatedBranchTargets`，运行时填充）或运行时 barrier 状态。

### 5.2 Join

- 只等待 Split 时 **实际激活** 的分支（非全部出边）。
- `lastResult = null`；vars LWW；重叠 mapping → error。

### 5.3 「实际激活分支」

Split 时 FEEL 为 true 而出 fork 的路径；未激活的路径 Join **不等待**。

---

## 6. Event-based Gateway

### 6.1 与 Intermediate Event 的关系

| 元素 | 职责 |
|------|------|
| **Event-based Gateway** | 分叉 + **事件竞态**（谁先发生走谁） |
| **Intermediate Event** | 单路径上的 **等待点**（timer / signal）；timer 支持 `duration` / `date` + 变量 — 见 [timer-design.md](./timer-design.md) |

标准结构：Gateway 出边 **直连** Intermediate Catch Event，再连后续 Task。

### 6.2 运行时

- 对每个出边首 Event 注册等待（Timer / Signal）。
- `Workflow.await` 直到 **任一** 触发。
- 仅沿 **胜出路径** 继续；其余路径不执行。
- v1 不实现 BPMN 级联 cancel 未触发路径上的 Activity（因尚未启动）。

### 6.3 Signal

- Workflow `@SignalMethod receiveFlowSignal(signalName, payload)`。
- Intermediate Event `eventSubtype=signal` + `config.signalName`。
- Event Gateway 出边首节点为 signal/timer 事件。

---

## 7. Compiler 校验摘要

| ID | 规则 | 级别 |
|----|------|------|
| G1 | Parallel/Inclusive 多入多出 | error |
| G2 | Parallel split 出边非 default | error |
| G3 | Parallel split 无配对 join / 未闭合区域 | error |
| G4 | 嵌套 Parallel（split 至 join 间再遇 parallel split） | error |
| G5 | Join 入边数 ≠ 配对 split 出边数（Parallel） | error |
| G6 | 并行区域内 Activity outputMapping 键重叠 | error |
| G7 | Event-based 出边首节点非 INTERMEDIATE_EVENT | error |
| G8 | Activity 多出边 / 出边带 FEEL | error |

编译产物写入 Gateway `config`：`gatewayRole`, `pairedSplitId`, `pairedJoinId`, `expectedBranchCount`（Parallel join）。

---

## 8. 解释器架构

```text
FlowInterpreterWorkflowImpl（Temporal）
  → FlowInterpreterEngine（确定性遍历 + fork/join）
  → InterpreterCallbacks（Activity / Timer / Signal / Child / HumanTask）
```

- **测试**：`SyncInterpreterCallbacks` 同步执行，无 Temporal。
- **生产**：`TemporalInterpreterCallbacks` + `Async.function` 并行分支。

---

## 9. 测试矩阵

见 `FlowCompilerGatewayTest`、`GatewayTopologyValidatorTest`、`FlowInterpreterEngineTest`。

---

## 10. 相关文档

- [loop-design.md](./loop-design.md) — Activity Standard / Multi-Instance Loop
- [timer-design.md](./timer-design.md) — Timer Definition（duration / date / 变量）

---

*文档版本：v1.0 实现基线*
