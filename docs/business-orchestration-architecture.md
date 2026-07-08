# 面向业务的流程编排平台架构方案

## 1. 目标定位

本方案的目标是建设一个面向业务人员的高层流程编排平台：

- 业务人员在流程画布上组合业务能力，而不是编写代码逻辑。
- FDE 可以在 Codex 等 coding agent 加持下，根据业务场景直接生成可运行在 Temporal 上的 Workflow 和 Activity。
- 开发者在 Activity  中封装复杂工程逻辑。
- Temporal 作为底层 Workflow Runtime，负责可靠执行、状态持久化、重试、超时、恢复和长时间等待。

核心目标可以概括为：

```text
业务人员在更高维度编排
FDE 在 coding agent 辅助下快速生成可运行代码
开发者在 Activity 里封装复杂工程逻辑
Temporal 在底层保证流程可靠运行
```

本方案不把 Temporal 暴露给业务人员，也不要求业务人员理解 Workflow、Activity、Task Queue、重试策略、幂等、确定性等底层概念。对于 FDE 和开发者，平台应提供更高效的代码生成、校验、测试和部署路径，让他们可以直接产出 Temporal 原生代码，但仍遵守统一的节点契约和治理规则。

## 2. 背景判断

当前讨论过三类上层流程建模方案：

- QuantumBPM：偏 BPMN、企业流程、审批和传统业务流程管理，是本项目的重要参考产品。
- Dify：业务人员和产品人员更熟悉，画布交互体验值得参考，但不需要复刻其 HTTP、IF/ELSE、循环等细粒度控制节点。
- 自研 React Flow 画布：可借鉴 Dify 的交互方式，同时吸收 QuantumBPM / BPMN 的简洁流程设计理念，自己定义节点、DSL 和执行语义。

如果目标是让业务人员以更自然的方式编排业务能力，而不是学习 BPMN 规范，那么更适合采用 Dify 风格的交互体验，但节点抽象应更接近 QuantumBPM / BPMN 的简洁业务流程设计，而不是 Dify 的细粒度工具链节点体系。

但 Temporal 的定位保持不变：Temporal 不负责画布，也不负责低代码界面，它只作为可靠执行内核。

### 2.1 Dify / n8n 的天花板与 QuantumBPM + Temporal 的启发

Dify 和 n8n 虽然面向的业务域不同，但在流程画布形态上有类似问题：它们更偏“节点自动化”和“工具编排”，适合快速把能力串起来，却容易在长周期、强状态、多人参与和强治理场景下遇到天花板。

Dify / n8n 擅长的场景：

```text
短流程
快速试验
API / 工具调用
Webhook / 定时触发
LLM、知识库、SaaS、内部系统的快速串联
低代码方式替代脚本和胶水代码
```

这些能力很适合原型验证和轻量自动化，但如果把它们作为企业级业务流程运行平台，会逐渐暴露以下问题：

- **长周期状态不足**：适合秒级、分钟级自动化，不天然适合持续数小时、数天、数周的业务流程实例。
- **人机协同不够一等公民**：审批、领取、转派、补充资料、人工复核等能力通常需要额外拼装，而不是流程语义的一部分。
- **错误语义偏技术化**：更多是 retry、fallback、error branch，不擅长表达业务级错误、升级、补偿、人工介入等正式语义。
- **版本和迁移能力弱**：短流程直接编辑 workflow 问题不大，但长流程需要不可变版本和运行中实例迁移策略。
- **审计能力需要额外建设**：监管或关键业务流程需要回答“谁在什么时候基于什么输入做了什么决定”，这不是简单日志能替代的。
- **决策规则容易散落**：大量 if/else、条件分支、变量赋值会把业务规则散落在画布里，难以评审、测试和治理。
- **画布容易变成可视化编程环境**：HTTP 节点、循环节点、代码节点、变量节点越来越多，业务人员看到的是技术控制流，而不是业务流程。

QuantumBPM / BPMN + Temporal 这类方案对这些问题的解决思路是：

```text
BPMN / 简洁业务画布
  -> 表达高层业务流程语义

Script Task + Safe FEEL
  -> 复杂规则用 Node.js 高代码计算，分支用 FEEL 读变量选路

Temporal Runtime
  -> 承载长周期、可恢复、可重试、可等待的可靠执行

Activity / External Worker
  -> 封装技术细节、外部系统调用、异常处理和幂等
```

具体解决点：

- **长周期流程**：Temporal Workflow History 和 Timer 可以支撑长时间运行、等待和恢复。
- **人机协同**：人工任务可以映射为业务待办 + Signal / Update，流程可以可靠暂停和继续。
- **强审计**：流程定义版本、节点执行记录、人工操作、决策输入输出可以作为结构化事件沉淀。
- **版本治理**：Flow DSL / Execution Plan 发布后不可变，运行中实例引用固定版本。
- **规则治理**：复杂判断不在 Gateway 出边上直接写脚本，而是前置 **Script Task**（`script-runtime`）用 Node.js 计算并写入变量，再由出边 **Safe FEEL** 根据变量选路，便于业务评审和测试。
- **循环策略**：多轮业务编排（如外呼多轮）优先 **Gateway 回边 + 变量**；同一 Task 按条件或集合重复时使用 Activity **`flowFoundryLoop`**（Standard / Multi-Instance），详见 [loop-design.md](./loop-design.md)。
- **定时等待**：按 slot 固定时刻触发下一轮使用 Intermediate Event **`timerDefinition.type=date`** + `${slot.fixedTime}` / `${slot.timezone}`，详见 [timer-design.md](./timer-design.md)。
- **技术复杂度下沉**：HTTP 调用、重试、分页、限流、错误码转换、幂等等逻辑封装进 Activity。
- **业务画布简洁**：画布保留高层业务节点、少量业务判断、人工任务、等待和子流程。

但 QuantumBPM / BPMN + Temporal 思路也有代价：

- **没有 Dify / n8n 那样庞大的预置集成节点库**：业务能力需要通过 Activity / Connector 开发和注册。
- **建模纪律更高**：流程节点、变量、输入输出、版本、权限、审计都需要规范化。
- **平台建设成本更高**：需要实现 Flow DSL、Compiler / Interpreter、Activity Registry、运行态视图和治理能力。
- **运维复杂度更高**：Temporal、Worker、数据库、消息/信号、权限、监控都需要稳定运行。
- **短平快自动化不一定划算**：如果只是简单 API 串联，Dify / n8n 仍然可能更快。

因此本方案不是要复刻 Dify / n8n，也不是完整引入 QuantumBPM，而是吸收两边优点：

```text
借鉴 Dify / n8n 的易用画布体验
吸收 QuantumBPM / BPMN 的简洁流程抽象，分支条件统一为 Safe FEEL
使用 Temporal 作为唯一可靠执行 runtime
用 Activity  承接工程复杂度
```



## 3. 总体架构

推荐架构如下：

```text
┌────────────────────────────────────┐        ┌────────────────────────────────────┐
│ 入口 A：业务流程画布                │        │ 入口 B：FDE + Coding Agent          │
│ 简洁业务节点 + Dify 风格交互         │        │ Codex 生成 Workflow / Activity 代码 │
│ 节点、连线、参数、发布、调试         │        │ 生成测试、配置、Registry、部署脚本   │
└──────────────────┬─────────────────┘        └──────────────────┬─────────────────┘
                   │                                             │
                   ▼                                             ▼
┌────────────────────────────────────┐        ┌────────────────────────────────────┐
│ Flow DSL                            │        │ Temporal 原生代码包                  │
│ 流程定义 JSON                       │        │ Workflow / Activity / Tests / Config │
│ 描述节点、边、变量、输入输出、版本   │        │ 适合复杂和定制化场景                 │
└──────────────────┬─────────────────┘        └──────────────────┬─────────────────┘
                   │                                             │
                   ▼                                             │
┌────────────────────────────────────┐                           │
│ Flow Compiler / Interpreter         │                           │
│ 校验流程、解析 DAG、转换节点语义     │                           │
│ 将 DSL 映射到 Temporal 执行模型      │                           │
└──────────────────┬─────────────────┘                           │
                   │                                             │
                   └──────────────────────┬──────────────────────┘
                                          ▼
┌─────────────────────────────────────────────────────────────────┐
│ Temporal Workflow Runtime                                       │
│ Workflow History、Timer、Signal、Retry、恢复                     │
└──────────────────────────────────┬──────────────────────────────┘
                                   ▼
┌─────────────────────────────────────────────────────────────────┐
│ Activity / Connector 层                                         │
│ 高代码业务能力封装、外部系统调用、异常处理、幂等、补偿、结果归一化 │
└─────────────────────────────────────────────────────────────────┘
```

这意味着平台支持两种互补的生产方式：

```text
业务人员：通过画布组合已有能力，生成 Flow DSL，由通用解释器执行。
FDE：通过 Codex 等 coding agent 生成 Temporal 原生代码，用于复杂、定制、交付型场景。
```

两条路径最终都落到 Temporal Runtime，并共享 Activity Registry、运行态观测、权限治理、部署规范和审计体系。

## 4. 设计文档分层

本仓库将架构说明与实现细节拆分为两份文档：


| 文档                                                                                     | 内容                                                                   |
| -------------------------------------------------------------------------------------- | -------------------------------------------------------------------- |
| [business-orchestration-architecture.md](./business-orchestration-architecture.md)（本文） | 目标定位、背景判断、总体架构、产品关系、落地路线                                             |
| [detailed-design.md](./detailed-design.md)                                             | 标识符规范、Workflow 持久化与 API、分层职责细节、节点抽象、Temporal 映射、Registry、异常/幂等/观测/治理 |


下文仅保留各主题的**架构级摘要**；字段级、API、表结构与执行语义请参阅详细设计文档。

### 4.1 分层职责（摘要）

- **画布**：业务人员编排入口，表达意图，不暴露 Temporal 技术细节。
- **Flow DSL**：画布 JSON 契约，连接 UI、Compiler 与 Interpreter。
- **Compiler / Interpreter**：静态校验 → Execution Plan → 通用 Temporal Workflow 解释执行。
- **Temporal Runtime**：可靠执行、重试、Timer、Signal、持久化。
- **Activity** ：开发者封装副作用与外部系统。
- **FDE 代码生成**：复杂定制场景的第二条入口，与画布共享 Registry 与治理契约。



### 4.2 节点与执行（摘要）

- 节点抽象保持**高层业务能力**，避免画布变成可视化编程语言。
- MVP 执行节点（`NodeKind`）：`START` / `END` / `ACTIVITY` / `GATEWAY` / `INTERMEDIATE_EVENT` / `CHILD_WORKFLOW`。
- **Human Task** 统一画布入口（`humanTask`），编译为 `ACTIVITY` + `activityType: human-task`；通过 `flowFoundryHumanTask.mode` 区分 `managed`（等 Signal）与 `offline`（标注后自动继续）。
- **Intermediate Event** 在 Palette 提供；DSL/语义层为 `INTERMEDIATE_EVENT`，通过 `config.eventSubtype` 区分等待类型（MVP 默认 `timer`）；**Boundary Event** 暂不开放拖放。
- **Child Workflow** 在 Palette 独立分组（`Child Workflows`），画布 `kind=workflow`；运行与限制见 [child-workflow-design.md](./child-workflow-design.md)。
- 命名权威表见 [entity-naming.md](./entity-naming.md)。
- Modeler 负责设计；**Runtime** 视图负责 Web 联调 Run / Query / 节点高亮；**Runs** 记录执行实例。Web Run 走 Temporal + Activity stub；生产 API 走真实 Activity。
- 边上条件使用 **Safe FEEL AST**；复杂规则走 **Script Task + Node.js 脚本服务**。
- Workflow 内保持确定性；副作用全部在 Activity 中。



### 4.3 治理与运行态（摘要）

- **Activity Registry** 登记可复用能力与 Task Queue 映射。
- 统一异常语义、幂等策略、结构化运行事件与审计。
- 权限按角色控制画布编辑、发布、运行与节点可见性。

完整说明见 [detailed-design.md](./detailed-design.md) 第 3–10 章。

## 5. 与 Dify 的关系

Dify 可以作为产品体验参考，但不应作为节点体系的完整复制对象。

Dify 的价值在于：

- 用户熟悉。
- 画布体验适合 AI 应用和工具编排。
- React Flow 系画布交互成熟。
- 节点配置、调试、运行态反馈等产品体验值得借鉴。

但 Dify 的很多节点属于细粒度控制或工具调用能力，例如 HTTP、IF/ELSE、循环、变量赋值、代码执行等。这些能力不应该直接成为本平台面向业务人员的主要编排元素。它们更适合由 FDE 或开发者封装在 Activity 内部。

但不建议直接把 Dify Runtime 作为底层执行引擎。原因是本方案的底层运行时已经明确为 Temporal。

推荐方式：

```text
借鉴 Dify 的画布体验
借鉴 Dify 的调试和运行反馈体验
不复刻 Dify 的细粒度控制节点体系
不直接复用 Dify Runtime
但执行层使用自己的 Flow Interpreter + Temporal
```

如果复用 Dify 的 DSL，需要额外维护 Dify DSL 到 Temporal 的翻译层，并承担 Dify 内部格式变化带来的风险。

如果自定义 DSL，可以更好地贴合 Temporal 和自身业务域，也可以更好地控制节点粒度，避免把业务画布变成可视化编程环境。

## 6. 与 QuantumBPM 的关系

QuantumBPM 更适合 BPMN 标准流程、传统审批、人机流程和企业流程治理场景。它的设计优点之一是流程图相对简洁，强调流程节点、网关、人工任务和定时等待，而不是把大量技术控制细节暴露到画布上。

FlowFoundry 是当前自研项目代号，不是这里讨论的外部参考产品。QuantumBPM 与 FlowFoundry 自研画布处在同一产品层：前者用于参考和对照，后者是我们要建设的业务流程编排平台。

QuantumBPM 的简洁流程抽象值得 FlowFoundry 吸收：

```text
保留高层业务节点
保留必要业务判断
保留人工任务
保留等待和子流程
避免暴露 HTTP、for、try-catch、变量赋值等技术化细节
```

本方案更适合以下目标：

- 业务人员不熟悉 BPMN。
- 流程节点更像业务能力，而不是 BPMN 技术元素。
- 希望做 AI、工具、营销、运营类编排。
- 希望将异常处理、循环、重试等复杂逻辑封装在高代码 Activity 中。
- 希望 Temporal 作为稳定的下层运行时。



## 7. 推荐落地路线



### 阶段一：最小可用闭环

目标是跑通从画布到 Temporal 的完整链路。

范围：

- 自定义 Flow DSL。
- 简单 React Flow 画布。
- Start / End 节点。
- 业务 Activity 节点。
- 简单条件分支。
- Flow Interpreter Workflow。
- Activity Registry。
- 2 到 3 个真实业务 Activity。
- Flow Run 状态查询。
- FDE 使用 coding agent 生成一个可运行的 Temporal Workflow / Activity 样例。



### 阶段二：业务可用

目标是让业务人员可以配置并运行真实流程，同时让 FDE 可以基于模板快速生成定制化 Temporal 代码。

范围：

- 节点参数表单。
- 节点输入输出变量选择。
- 流程草稿和发布。
- 流程版本。
- 人工审批节点。
- Timer 等待节点。
- 节点运行日志。
- 失败节点重试。
- Activity 幂等基础框架。
- Workflow / Activity 代码生成模板。
- 生成代码的测试模板和本地运行脚本。
- 生成代码与 Activity Registry 的校验工具。



### 阶段三：平台化

目标是形成可治理、可扩展的内部流程平台。

范围：

- 节点市场。
- 权限模型。
- 多租户隔离。
- 流程审批。
- 执行配额。
- 审计日志。
- 子流程。
- 节点版本管理。
- 回滚和灰度发布。
- 运行报表。
- FDE 生成代码的发布审批和审计。
- 生成的新 Activity 自动进入节点市场候选区。



### 阶段四：智能化

目标是提升业务人员配置效率，并提升 FDE 的交付效率。

范围：

- 根据自然语言生成流程草稿。
- 根据业务目标推荐节点。
- 自动检查流程风险。
- 自动生成测试样例。
- 节点配置智能补全。
- 流程运行异常解释。
- 根据业务需求自动生成 Temporal Workflow / Activity 初稿。
- 自动检查生成代码是否违反 Temporal 确定性约束。
- 自动识别哪些逻辑应该上升为画布节点，哪些逻辑应该留在 Activity 内部。



## 8. 当前项目可复用资产

当前仓库中的以下设计可以继续复用或参考：

- `flowfoundry-core/.../temporal/TemporalWorkerBootstrap.java`
  - Worker 启动和 Temporal SDK 接入方式；业务通过 `TemporalWorkerExtension` 注册。
- `flowfoundry-app/modules/ai-collection-strategy/.../CallCampaignActivities.java`
  - Activity 接口和 `@ActivityMethod` 命名方式。
- `flowfoundry-app/modules/ai-collection-strategy/.../CallCampaignActivitiesImpl.java`
  - Activity 实现层封装业务逻辑的方式。
- `flowfoundry-core/.../idempotency/IdempotentActivityExecutor.java`
  - 统一幂等执行模板。
- `flowfoundry-app/modules/ai-collection-strategy/config/activities-registry.yaml`
  - Activity Registry 配置示例。

后续可以把当前 BPMN / QuantumBPM 风格映射思路升级为：

```text
Flow DSL node.type
  -> Activity Registry activity.type
  -> Temporal @ActivityMethod(name = activity.type)
  -> Activity Implementation
```



## 9. 关键设计原则

最终方案应坚持以下原则：

1. 业务画布只表达业务意图。
2. 技术复杂度封装到 Activity。
3. Temporal 永远作为底层可靠执行 runtime。
4. Workflow 负责调度，不负责外部副作用。
5. Activity 负责副作用、幂等、异常归一化。
6. DSL 是画布和执行层之间的核心契约。
7. Activity Registry 是业务节点和高代码能力之间的核心契约。
8. 业务人员不需要理解 Temporal。
9. FDE 可以使用 coding agent 生成 Temporal 原生代码，但生成物必须遵守平台契约。
10. FDE 生成的新能力应该沉淀回 Activity Registry 和节点市场。
11. 开发者不应该把低层技术细节泄漏给画布。
12. 运行态展示应面向业务，而不是直接暴露 Temporal History。



## 10. 总结

推荐建设一个 Dify 风格的业务流程编排平台，但不复用 Dify Runtime。上层使用业务友好的画布和自定义 Flow DSL，中层使用 Flow Interpreter 和 Activity Registry，下层使用 Temporal 作为 Workflow Runtime。同时，平台应支持 FDE 在 Codex 等 coding agent 辅助下生成 Temporal 原生 Workflow 和 Activity，用于复杂定制场景。

这种架构可以同时满足：

- 业务人员易用。
- FDE 交付高效。
- 开发者可控。
- 工程复杂度可封装。
- 流程执行可靠。
- 平台后续可治理、可扩展、可演进。

一句话总结：

```text
用 Dify 风格承载业务编排体验，用 Codex 等 coding agent 加速 FDE 交付，用 Activity 承载高代码业务能力，用 Temporal 承载可靠执行。
```

