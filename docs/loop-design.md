# Activity Loop 设计（实现基线）

> **状态**：实现基线 v1.0 | 2026-07-04  
> 与 [gateway-design.md](./gateway-design.md) 互补：Gateway 负责图级分支/汇合；Loop 负责 **同一 Activity 节点** 的重复执行。

---

## 1. 两种循环策略

| 策略 | 适用场景 | 建模方式 | v1 |
|------|----------|----------|-----|
| **图级循环**（推荐） | 多轮外呼、审批退回、跨节点状态机 | Exclusive Gateway + 回边 + `vars` | ✅ 已有 |
| **节点级 Loop** | 同一 Task 紧凑重复、BPMN Loop Characteristic | Activity 属性 `flowFoundryLoop` | ✅ 本文 |

**原则**：业务多轮编排优先用 **Gateway 回边**（可见、可评审）；节点 Loop 用于「同一 Activity 按条件/集合重复」且不想画回边的场景。

---

## 2. 范围（v1）

| 模式 | 画布 `loop` | `flowFoundryLoop.mode` | 语义 |
|------|-------------|------------------------|------|
| 无 | `none` | — | 执行一次（默认） |
| Standard Loop | `standardLoop` | `standard` | 执行后若 FEEL 条件为真则再执行 |
| Multi-Instance（顺序） | `multiInstance` | `multiInstance` | 对集合中每个元素顺序执行一次 |

**v1 限制**：

- 仅 **`ACTIVITY`**（含 `serviceTask` / `scriptTask` / `humanTask`）可配置 Loop
- Multi-Instance **仅顺序**（`sequential: true`）；并行 MI 留 v2
- Loop 与 Activity **单出边**约束兼容：全部迭代完成后沿唯一出边继续
- **`maxIterations`** 默认 `100`，上限 `10000`（含 Standard 总次数与 MI 最大元素数）

**v1 不做**：Loop on Gateway / Child Workflow / Intermediate Event；嵌套 Loop 专项校验（依赖 `maxIterations` 兜底）。

---

## 3. 配置结构

写入 `ExecutionNode.config.flowFoundryLoop`（DSL 经 `buildDsl()` → Compiler 校验）：

```json
{
  "flowFoundryLoop": {
    "mode": "standard",
    "condition": "${remaining > 0}",
    "maxIterations": 100,
    "iterationVar": "loop.iteration"
  }
}
```

```json
{
  "flowFoundryLoop": {
    "mode": "multiInstance",
    "collection": "$.vars.contacts",
    "elementVar": "loop.item",
    "indexVar": "loop.index",
    "maxIterations": 100,
    "sequential": true
  }
}
```

| 字段 | Standard | Multi-Instance | 说明 |
|------|----------|----------------|------|
| `mode` | ✅ | ✅ | `standard` \| `multiInstance` |
| `condition` | ✅ 必填 | — | Safe FEEL / 字符串表达式；**为真则继续下一轮** |
| `collection` | — | ✅ 必填 | 变量路径，解析为 List 或数组 |
| `elementVar` | — | 默认 `loop.item` | 当前元素写入 `vars` |
| `indexVar` | — | 默认 `loop.index` | 0-based 索引 |
| `iterationVar` | 默认 `loop.iteration` | — | 1-based 迭代计数 |
| `maxIterations` | 默认 100 | 默认 100 | 安全上限 |
| `sequential` | — | 默认 true | v1 必须为 true |

---

## 4. Standard Loop 语义

对齐 BPMN Standard Loop Characteristic：

1. **至少执行一次** Activity
2. 每次执行前设置 `iterationVar`（从 1 递增）
3. Activity 完成并应用 `outputMapping` 后，求值 `condition`
4. 若条件为 **true** 且未达 `maxIterations` → 再执行一轮
5. 若条件为 **false** 或已达 `maxIterations` → 沿唯一出边继续

```text
iteration = 0
repeat
  iteration++
  vars[iterationVar] = iteration
  execute Activity → apply outputMapping
until not condition OR iteration >= maxIterations
```

**Human Task**：每轮 Managed 模式均会等待 Signal；Offline 模式每轮自动继续。

---

## 5. Multi-Instance（顺序）语义

1. 解析 `collection` → `List`（空集合 → **0 次**执行，直接出边）
2. 取 `min(size, maxIterations)` 个元素
3. 对每个元素：设置 `indexVar`、`elementVar`，执行 Activity，应用 outputMapping
4. 全部完成后沿唯一出边继续

`lastResult` 为 **最后一轮** Activity 返回值（与无 Loop 时一致）。

---

## 6. 与 Gateway 回边对比

| | Gateway 回边 | Activity Loop |
|--|--------------|---------------|
| 可见性 | 图上可见循环路径 | 属性面板配置 |
| 跨节点 | 可含多个节点 | 仅单 Activity |
| 分支 | 每轮可走不同 Gateway | 固定重复同一 Task |
| 推荐 | 外呼多轮、复杂状态机 | 批量顺序处理、重试式重复 |

外呼多轮示例仍推荐 [detailed-design.md](./detailed-design.md) 中的 **Exclusive + 回边** 模式；Loop 文档不替代该模式。

---

## 7. Compiler

`FlowCompiler.compileNode` 对 `ACTIVITY`：

- `LoopDefinition.fromConfig(config).validate(nodeId)`
- 非 Activity 节点若带 `flowFoundryLoop` → **compile error**
- Multi-Instance `sequential: false` → **compile error**（v2）

---

## 8. Interpreter

`FlowInterpreterEngine.executeNode` 在 `ACTIVITY` / `HUMAN_TASK` 分支：

- 无 Loop → 单次 `executeActivityOnce`
- Standard / Multi-Instance → `executeActivityWithLoop` 包装

Loop 变量通过 `VariableStore.assign` 写入 `vars`（支持 `loop.item` 等点路径）。

---

## 9. 建模器

- 属性面板 **Loop** 区：`None` / `Multi-Instance` / `Standard`
- Standard：条件 FEEL、`maxIterations`
- Multi-Instance：`collection` 路径、`elementVar`、`indexVar`、`maxIterations`
- `modeler-dsl-runtime.js` 的 `runtimeConfig()` 将画布 `loop` + `config.flowFoundryLoop` 合并写入 DSL

---

## 10. 测试

- `FlowCompilerTest`：Loop 校验、非法节点类型
- `FlowInterpreterEngineTest`：Standard 多次执行、MI 顺序执行、空集合

---

## 11. 相关文档

- [gateway-design.md](./gateway-design.md) — 图级分支与 Parallel Join
- [entity-naming.md](./entity-naming.md) — `flowFoundryLoop` 字段表
- [business-orchestration-architecture.md](./business-orchestration-architecture.md) — 循环策略总览

---

*文档版本：v1.0 实现基线*
