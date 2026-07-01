# 面向业务的流程编排平台架构方案

## 1. 目标定位

本方案的目标是建设一个面向业务人员的高层流程编排平台：

- 业务人员在流程画布上组合业务能力，而不是编写代码逻辑。
- FDE 可以在 Codex 等 coding agent 加持下，根据业务场景直接生成可运行在 Temporal 上的 Workflow 和 Activity。
- 开发者在 Activity / Connector 中封装复杂工程逻辑。
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

- FlowFoundry：偏 BPMN、企业流程、审批和传统业务流程管理。
- Dify：业务人员和产品人员更熟悉，画布交互体验值得参考，但不需要复刻其 HTTP、IF/ELSE、循环等细粒度控制节点。
- 自研 React Flow 画布：可借鉴 Dify 的交互方式，同时吸收 FlowFoundry 的简洁流程设计理念，自己定义节点、DSL 和执行语义。

如果目标是让业务人员以更自然的方式编排业务能力，而不是学习 BPMN 规范，那么更适合采用 Dify 风格的交互体验，但节点抽象应更接近 FlowFoundry 的简洁业务流程设计，而不是 Dify 的细粒度工具链节点体系。

但 Temporal 的定位保持不变：Temporal 不负责画布，也不负责低代码界面，它只作为可靠执行内核。

### 2.1 Dify / n8n 的天花板与 FlowFoundry+Temporal 的启发

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

FlowFoundry+Temporal 这类方案对这些问题的解决思路是：

```text
BPMN / 简洁业务画布
  -> 表达高层业务流程语义

DMN / 决策表
  -> 把业务规则作为一等资产管理

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
- **规则治理**：复杂判断从画布分支中抽离到 DMN / Decision Activity，便于业务评审和测试。
- **技术复杂度下沉**：HTTP 调用、重试、分页、限流、错误码转换、幂等等逻辑封装进 Activity。
- **业务画布简洁**：画布保留高层业务节点、少量业务判断、人工任务、等待和子流程。

但 FlowFoundry+Temporal 思路也有代价：

- **没有 Dify / n8n 那样庞大的预置集成节点库**：业务能力需要通过 Activity / Connector 开发和注册。
- **建模纪律更高**：流程节点、变量、输入输出、版本、权限、审计都需要规范化。
- **平台建设成本更高**：需要实现 Flow DSL、Compiler / Interpreter、Activity Registry、运行态视图和治理能力。
- **运维复杂度更高**：Temporal、Worker、数据库、消息/信号、权限、监控都需要稳定运行。
- **短平快自动化不一定划算**：如果只是简单 API 串联，Dify / n8n 仍然可能更快。

因此本方案不是要复刻 Dify / n8n，也不是完整引入 FlowFoundry，而是吸收两边优点：

```text
借鉴 Dify / n8n 的易用画布体验
吸收 FlowFoundry 的简洁流程抽象和 DMN 思路
使用 Temporal 作为唯一可靠执行 runtime
用 Activity / Connector 承接工程复杂度
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

## 4. 分层职责

### 4.1 业务流程画布

画布面向业务人员，负责表达业务意图。

它应该提供：

- 拖拽式节点编排。
- 节点参数配置。
- 连线和分支配置。
- 流程草稿、发布、版本管理。
- 调试运行和执行结果查看。
- 节点输入输出变量选择。
- 节点说明、示例和使用约束。

它不应该暴露：

- Temporal Task Queue。
- Workflow / Activity 代码概念。
- Java / Python / TypeScript SDK 细节。
- 重试退避参数。
- HTTP 500、429 等技术错误处理细节。
- for / while / try-catch 等程序控制结构。

画布节点应该是业务能力，而不是代码语句。

示例：

```text
推荐节点：
- 客户分群
- 生成触达内容
- 发起一轮外呼
- 等待外呼回执
- 判断是否继续触达
- 主管复核
- 发送通知
- 生成总结报告

不推荐直接暴露为节点：
- for 循环
- try-catch
- HTTP 重试
- 分页拉取
- 幂等锁
- 错误码转换
- 数据库事务
```

### 4.2 Flow DSL

DSL 是 Domain-Specific Language，即领域特定语言。

在本方案中，DSL 指流程画布保存下来的流程定义格式，通常是一份 JSON。它描述流程如何连接、每个节点是什么类型、节点参数是什么、输入输出如何传递。

示例：

```json
{
  "flowId": "call-campaign-flow",
  "version": "1.0.0",
  "nodes": [
    {
      "id": "start",
      "type": "start"
    },
    {
      "id": "segment_customers",
      "type": "customer_segment",
      "config": {
        "segmentId": "vip-customers"
      }
    },
    {
      "id": "call_round",
      "type": "execute_call_round",
      "config": {
        "channel": "phone",
        "maxBatchSize": 1000
      }
    },
    {
      "id": "review",
      "type": "human_approval",
      "config": {
        "role": "supervisor"
      }
    }
  ],
  "edges": [
    { "source": "start", "target": "segment_customers" },
    { "source": "segment_customers", "target": "call_round" },
    { "source": "call_round", "target": "review", "condition": "remainingContacts > 100" }
  ]
}
```

DSL 是平台的核心契约。画布、后端校验、Temporal Interpreter、Activity Registry 都围绕 DSL 协作。

### 4.3 Flow Compiler / Interpreter

这一层负责把 Flow DSL 转换为 Temporal 可执行语义。

推荐把这一层拆成两个职责：

```text
Flow Compiler
  -> 运行前静态校验、规范化、生成 Execution Plan

Flow Interpreter
  -> 一个通用 Temporal Workflow，按 Execution Plan 解释执行
```

整体链路是：

```text
Canvas JSON
  -> Flow DSL
  -> Flow Compiler
      - 结构校验
      - 节点类型校验
      - 参数 schema 校验
      - 边和条件校验
      - 生成 Execution Plan
  -> Temporal Workflow: FlowInterpreterWorkflow
      - 读取固定版本的 Execution Plan
      - 按节点推进
      - 调用 Activity
      - 等待人工 / Timer / 外部事件
  -> Activity Worker
      - 按 activityType 执行业务逻辑
```

这里的关键点是：Compiler 不直接生成 Java / Go / TypeScript 代码，而是生成一个 Temporal 可解释的 `ExecutionPlan`。早期主方案优先采用解释型，而不是代码生成型：

- 不需要为每个流程生成和部署代码。
- 业务发布流程更快。
- 适合节点类型相对稳定、流程数量较多的场景。
- 更符合低代码/业务画布的使用体验。

#### Flow DSL 与 Execution Plan

Flow DSL 是画布保存下来的流程定义，可能包含坐标、UI 状态、节点展示属性等信息。Execution Plan 是运行时使用的规范化结构，应该去掉 UI 噪声，只保留执行需要的信息。

示例：

```json
{
  "flowId": "call-campaign",
  "version": "1.0.0",
  "startNodeId": "start",
  "nodes": {
    "call_round": {
      "kind": "activity",
      "activityType": "execute-call-round",
      "taskQueue": "call-campaign",
      "inputMapping": {
        "campaignId": "$.input.campaignId",
        "roundNumber": "$.vars.roundNumber"
      },
      "outputMapping": {
        "dialerTaskId": "$.vars.dialerTaskId"
      }
    }
  },
  "edges": {
    "call_round": [
      {
        "target": "review",
        "condition": "$.vars.remainingContacts > 100"
      },
      {
        "target": "finalize",
        "condition": "default"
      }
    ]
  }
}
```

Execution Plan 必须版本化、不可变。流程一旦发布，运行中的 Workflow Execution 应始终引用当时发布的那份 Execution Plan，避免流程定义变更导致 Temporal replay 出现不确定性。

#### Compiler 职责

Compiler 负责运行前的静态检查和规范化：

- 校验图结构是否合法。
- 检查只有一个 start 节点。
- 检查至少一个 end 节点。
- 检查所有 edge 的 source / target 存在。
- 检查不允许孤立节点。
- 检查节点 type 是否在 Node Registry 中存在。
- 检查 activityType 是否在 Activity Registry 中存在。
- 检查节点参数是否符合 schema。
- 检查条件表达式语法是否合法。
- 检查变量引用是否存在或可推导。
- 生成可执行的 Execution Plan。

Compiler 是业务画布和 Temporal 运行层之间的第一道质量门禁。不能通过 Compiler 校验的流程，不允许发布到运行环境。

#### Interpreter 职责

Interpreter 是一个通用 Temporal Workflow，例如：

```text
FlowInterpreterWorkflow.run(flowVersionId, businessKey, input)
```

它不关心具体业务，只认识有限的节点类型：

```text
start
end
activity
decision
human_task
timer
parallel
subflow
```

解释执行逻辑可以简化理解为：

```text
while flow not finished:
  load current node from Execution Plan
  execute node by kind
  write node result into workflow variables
  select next node by edge conditions
```

对应到节点类型：

```text
activity
  -> 调用 Temporal Activity

decision
  -> 执行确定性条件判断，或调用 Decision Activity

human_task
  -> 创建待办 Activity，然后等待 Signal / Update

timer
  -> 使用 Temporal Timer / Workflow.sleep

subflow
  -> 启动 Child Workflow

end
  -> 结束当前 Workflow Execution
```

Temporal Workflow 内必须保持确定性。Interpreter 不能直接查数据库、发 HTTP、读系统时间、调用随机数或访问外部服务。所有外部副作用都必须放到 Activity 中。

#### Activity 节点执行

Activity 节点通过 Activity Registry 映射到真正的业务能力：

```text
node.activityType = execute-call-round
  -> registry 找到 taskQueue、timeout、retry、inputSchema
  -> Temporal 调用对应 Activity
```

为了支持动态节点，Interpreter 可以使用 untyped activity stub 或统一 Activity Router。这样通用 Interpreter 不需要在代码中写死所有 Activity 方法。

#### 人工任务、Timer 与条件判断

人工任务：

```text
human_task 节点
  -> Activity 创建待办记录
  -> Workflow 等待 Signal / Update
  -> 用户在业务系统审批
  -> 后端 signal workflow
  -> Workflow 继续执行
```

Timer：

```text
timer 节点
  -> Workflow.sleep(duration)
```

长时间等待应该交给 Temporal Timer，不应该让 Activity 自己 sleep。

条件判断：

```text
简单条件
  -> Workflow 内执行 Safe FEEL AST，例如 remainingContacts > 100

复杂规则
  -> Business Rule Task 调用 Decision Activity
  -> Decision Activity 调用 Node.js DMN / JS 决策服务
  -> 返回 nextAction 或 decisionResult
```

边上的简单分支条件使用 Safe FEEL AST，而不是直接执行脚本。画布可以展示和编辑 FEEL 字符串，Compiler 负责将 FEEL 字符串解析、校验并编译成 AST，Execution Plan 中保存 AST 和展示用字符串。

示例：

```json
{
  "from": "decideNextAction",
  "to": "supervisorReview",
  "condition": {
    "language": "safe-feel-ast",
    "display": "nextAction = \"review\"",
    "ast": {
      "op": "=",
      "left": { "var": "vars.nextAction" },
      "right": "review"
    }
  }
}
```

Workflow Interpreter 运行时只执行 AST evaluator，不重新解析任意脚本，不执行 JS。Safe FEEL AST evaluator 必须满足：

- 纯内存计算。
- 同样输入永远得到同样输出。
- 不访问外部系统。
- 不读取当前时间。
- 不调用随机数。
- 不执行自定义函数或脚本。
- evaluator 版本与 Execution Plan 版本绑定。

Business Rule Task 用于复杂业务规则。它不在 Workflow 内执行 JS，而是通过 Activity 调用外部 Node.js DMN / JS 决策服务：

```text
Business Rule Task
  -> Temporal Workflow 调用 Decision Activity
  -> Decision Activity 调用 Node.js DMN 服务
  -> Node.js 服务根据 decisionRef 找到 JS 脚本
  -> 输入脚本需要的参数
  -> JS 脚本执行业务决策逻辑
  -> 返回结构化 decisionResult
  -> Workflow 写入 vars
  -> 后续 edge.condition 使用 Safe FEEL AST 判断走向
```

示例节点：

```json
{
  "id": "decideNextAction",
  "kind": "businessRuleTask",
  "decisionRef": "campaign-next-action",
  "decisionVersion": "1.0.3",
  "inputMapping": {
    "remainingContacts": "$.vars.remainingContacts",
    "roundNumber": "$.vars.roundNumber",
    "maxRounds": "$.vars.maxRounds"
  },
  "outputMapping": {
    "nextAction": "$.vars.nextAction",
    "needReview": "$.vars.needReview"
  }
}
```

Node.js DMN 服务请求示例：

```json
{
  "decisionRef": "campaign-next-action",
  "decisionVersion": "1.0.3",
  "inputs": {
    "remainingContacts": 153,
    "roundNumber": 2,
    "maxRounds": 3
  }
}
```

返回示例：

```json
{
  "decisionRef": "campaign-next-action",
  "decisionVersion": "1.0.3",
  "scriptHash": "sha256:xxxx",
  "result": {
    "nextAction": "review",
    "needReview": true,
    "reason": "remaining contacts exceed supervisor threshold"
  }
}
```

必须记录 `decisionRef`、`decisionVersion`、`scriptHash`、输入参数和输出结果，用于审计与版本追溯。Decision Activity 必须幂等，且 JS 脚本版本变化不能影响已启动流程实例的 replay。

#### MVP 节点范围

第一版只建议支持：

```text
start
end
activity
decision
human_task
timer
```

暂时不要做：

```text
for 循环
任意 HTTP 节点
复杂并行网关
动态子流程
异常边界事件
补偿事务
```

并行、子流程和更复杂的异常处理可以放到第二阶段。

### 4.4 Temporal Workflow Runtime

Temporal 是底层运行时，负责可靠执行。

Temporal 负责：

- 持久化 Workflow History。
- 调度 Workflow Task 和 Activity Task。
- Activity 超时与重试。
- Worker 宕机后的恢复。
- 长时间定时器。
- 人工等待。
- 外部事件唤醒。
- 查询流程状态。

Temporal 不负责：

- 画布渲染。
- 节点 UI。
- 业务参数表单。
- 节点市场。
- 业务权限模型。
- 业务人员可见的流程设计体验。

Workflow 内应该保持确定性。所有外部副作用都应该放在 Activity 中。

```text
适合放在 Workflow：
- 按图推进节点
- 判断下一条边
- 等待 Timer
- 等待 Signal
- 记录当前节点状态

适合放在 Activity：
- 调用 LLM
- 调用 HTTP API
- 查询或写入数据库
- 调用外呼平台
- 发送消息
- 生成文件
- 调用第三方工具
- 执行复杂异常处理
```

### 4.5 Activity / Connector 层

Activity 是开发者封装高代码能力的地方。

每个业务节点对应一个 Activity Type。Activity 内部可以包含复杂逻辑，但对画布只暴露清晰的业务输入输出。

示例：

```text
画布节点：发起一轮外呼

Activity 内部：
- 校验活动配置
- 查询待呼叫客户
- 拆分批次
- 调用外呼平台
- 处理平台限流
- 处理接口异常
- 做幂等控制
- 记录外部任务 ID
- 将外部错误码转换为业务错误
- 返回本轮任务结果
```

Activity 的设计原则：

- 一个 Activity 对应一个稳定业务能力。
- Activity 名称面向业务语义，而不是技术实现。
- Activity 入参出参必须结构化。
- Activity 内部负责幂等。
- Activity 内部负责外部系统错误归一化。
- Activity 内部可以使用普通工程代码，包括 if、for、try-catch、SDK、数据库和缓存。
- Activity 对业务画布隐藏技术复杂度。

### 4.6 FDE + Coding Agent 代码生成入口

除了业务人员通过画布编排外，平台还应支持 FDE 在 Codex 这类 coding agent 的辅助下，直接生成可运行在 Temporal 上的 Workflow 和 Activity。

这条路径面向以下场景：

- 客户现场或具体业务域有较强定制化需求。
- 流程逻辑已经超过通用画布节点的表达能力。
- 需要快速交付可运行的 Temporal Workflow。
- 需要把多个外部系统、私有 API、复杂异常处理封装成工程代码。
- 需要生成测试、部署配置、Activity Registry 和运行文档。

FDE 代码生成入口不替代业务画布，而是补足画布的边界。业务画布适合标准化、可配置、可复用的流程；FDE 代码生成适合复杂、非标准、需要工程落地的流程。

推荐生成物包括：

```text
- Temporal Workflow Interface
- Temporal Workflow Implementation
- Activity Interface
- Activity Implementation
- Activity Registry 条目
- 输入输出模型
- 单元测试和集成测试
- 本地运行脚本
- Worker 配置
- README / Runbook
```

FDE 可以用自然语言描述业务目标，例如：

```text
为某客户生成一个续费挽回流程：
1. 拉取即将到期客户
2. 按 ARR 和健康分分层
3. 对高价值客户创建 CSM 人工任务
4. 对中低价值客户生成邮件内容并发送
5. 等待 3 天后检查回复
6. 未回复则创建二次触达任务
7. 汇总执行结果
```

Coding agent 根据这个描述生成 Temporal 原生代码。生成后的代码需要经过：

- 编译检查。
- Temporal determinism 检查。
- Activity 副作用边界检查。
- Activity 幂等检查。
- 测试执行。
- Registry 校验。
- 代码 review。
- 发布审批。

这条路径的核心价值是让 FDE 把业务理解快速转化为可运行代码，同时仍然遵守平台约束。

### 4.7 双入口的统一契约

业务画布入口和 FDE 代码生成入口不能各自为政。它们需要共享统一契约：

```text
Activity Registry
  -> 定义可复用业务能力

Flow DSL / Generated Workflow Metadata
  -> 定义流程结构和版本

Runtime Event Model
  -> 定义运行态、节点状态、日志和审计事件

Deployment Contract
  -> 定义 Worker、Task Queue、Namespace、配置和密钥引用方式
```

对于业务画布生成的流程，Activity Registry 用来决定画布可用节点。对于 FDE 生成的代码，Activity Registry 用来登记新能力，让未来业务人员也可以在画布上复用。

推荐形成闭环：

```text
FDE 生成新 Activity
  -> 通过测试和 review
  -> 注册到 Activity Registry
  -> 成为画布可选节点
  -> 业务人员在后续流程中复用
```

这样平台可以同时支持快速定制交付和长期能力沉淀。

## 5. 节点抽象原则

平台节点不应该过细。

不推荐把流程画布设计成通用编程语言的可视化版本。如果业务人员需要在画布上写大量 if/else/for/异常分支，说明节点抽象太低。

本方案不追求复刻 Dify 的完整节点体系，尤其不需要把 HTTP 请求、IF/ELSE、FOR 循环、异常分支、变量赋值等细粒度控制节点作为主要能力暴露给业务人员。更合适的方向是吸收 FlowFoundry 的简洁流程设计理念：画布只保留高层业务动作、少量业务判断、等待、人工任务和子流程。

换句话说，Dify 更适合作为交互体验参考，FlowFoundry 更适合作为流程抽象简洁性的参考。

推荐节点分为以下几类：

### 5.0 画布 Palette 分类

画布 Palette 应该按照业务建模语义分类，而不是按照底层 Temporal API 或代码实现分类。推荐第一阶段采用以下顶层分类：

```text
Events
Activities
Gateways
Structural
Child Workflows
Annotations
```

推荐理解如下：

| Palette 分类 | 用途 | 典型元素 | 是否直接执行 | 运行时映射 |
| --- | --- | --- | --- | --- |
| Events | 表达流程开始、结束、等待或外部触发 | Start Event、End Event、Timer、Message / Signal Wait | 部分执行 | Workflow start/end、Timer、Signal / Update |
| Activities | 表达当前流程中的业务动作 | Service Task、User Task、Receive Task、Business Rule Task | 是 | Activity、Human Task、Decision Activity |
| Gateways | 表达流程分支、合流和事件竞争 | Exclusive、Parallel、Inclusive、Event-based | 否，决定路由 | Interpreter 选边、并发分支、事件等待 |
| Structural | 提升流程图可读性和维护性 | Sub-process、Participant / Pool | 否，主要组织结构 | 内部 scope、画布分组、权限/责任边界 |
| Child Workflows | 调用已发布的可复用流程 | Child Workflow 节点 | 是 | Temporal Child Workflow |
| Annotations | 补充说明，不影响执行 | Text Annotation、Group、Comment | 否 | 文档、审计辅助信息 |

几个边界要明确：

```text
Activities
  -> 当前流程中的一个业务动作，通常映射 Activity。

Structural
  -> 让当前流程更好读、更好维护，不应该引入独立运行时。

Child Workflows
  -> 当前流程调用另一个完整 Workflow，是独立的可执行节点类型。

Annotations
  -> 只解释流程，不参与执行。
```

因此，Child Workflow 不放在 Activities 或 Structural 下面，而是作为单独 Palette 分类。业务人员看到它时，应理解为“调用一个已经发布、可复用、可独立治理的流程”。

### 5.1 业务动作节点

业务动作节点是最重要的节点类型。

示例：

- 发起营销活动。
- 执行一轮外呼。
- 生成客户总结。
- 更新客户状态。
- 创建工单。
- 发送企微消息。
- 生成销售线索。

这些节点背后通常对应 Temporal Activity。

### 5.2 判断节点

判断节点只表达高层业务判断。

示例：

- 是否需要主管复核。
- 客户是否满足继续触达条件。
- 模型评分是否大于阈值。
- 是否命中风险规则。

复杂规则可以封装在 Activity 或规则服务里，画布只使用结果。

#### 5.2.1 Gateway 类型选择

FlowFoundry / BPMN 中常见的 Gateway 不只是“判断节点”，它们表达的是不同的流程分支语义。平台可以吸收这些语义，但不应该把所有 BPMN 复杂度一次性暴露给业务人员。

推荐理解如下：

| Gateway 类型 | 中文理解 | 语义 | 典型场景 | Temporal / Interpreter 映射 |
| --- | --- | --- | --- | --- |
| Exclusive Gateway | 排他网关 / 单选分支 | 多条路径中只选择一条 | 是否继续下一轮、是否需要主管复核、客户分层后选择一种处理方式 | 条件按顺序求值，命中第一条；未命中走 default |
| Parallel Gateway | 并行网关 / 全部同时执行 | 不看条件，所有分支都执行；通常需要再汇合 | 同时生成报表、发送通知、写审计日志；一轮结束后并行做多个独立动作 | 启动多个并发分支；可用 Child Workflow 或 Promise/Async，等待全部完成 |
| Inclusive Gateway | 包容网关 / 多选分支 | 根据条件选择零条、一条或多条路径；通常需要等待已启动分支汇合 | 同一客户可能同时需要短信、企微、人工跟进；命中多个风险规则时同时处理 | 条件逐条求值，启动所有命中分支；汇合时只等待实际启动的分支 |
| Event-based Gateway | 事件网关 / 等谁先来 | 不按变量判断，而是等待多个外部事件，哪个事件先发生就走哪条路径 | 等客户回复、等超时、等人工取消、等外部系统回调，先发生者决定后续流程 | 使用 Signal / Update / Timer 竞争；先触发的事件决定下一节点 |

最常用、也最适合第一阶段业务画布的是 Exclusive Gateway。它表达“二选一 / 多选一”的业务判断，和自定义 Flow DSL 中的 `decision` 节点最接近。

```text
是否继续下一轮？
  remainingContacts > 0 -> 等待后进入下一轮
  default               -> 结束
```

Parallel Gateway 和 Inclusive Gateway 都会引入并发分支与汇合语义。它们不是简单的 if/else，而是需要回答：

```text
分支是否同时启动？
是否必须全部完成？
失败一个分支时整体是否失败？
取消时是否级联取消？
汇合时等待哪些分支？
```

因此第一阶段不建议把复杂并行网关作为主要业务节点开放。可以先把稳定的并行业务能力封装成一个 Activity 或子流程，例如“本轮结果后处理”，内部再并行生成报表、推送指标和发送通知。

Event-based Gateway 的重点不是条件表达式，而是“等待多个事件中的第一个”。它适合人机协作和长等待场景，例如：

```text
等待客户响应
  客户回复消息 -> 进入意向客户处理
  超过 3 天    -> 创建二次触达任务
  人工取消     -> 结束流程
```

在 Temporal 中，这类语义可以通过 `Workflow.await`、Signal / Update 和 Timer 组合实现。自定义 Flow DSL 中可以先把它建模为等待节点加超时配置，而不是直接暴露完整 Event-based Gateway。

本平台推荐策略：

- 第一阶段：重点支持 Exclusive Gateway，对应 `decision` 节点和边条件。
- 第一阶段可弱化支持 Event-based Gateway：通过等待节点表达“等待事件 + 超时”。
- 第二阶段：再引入 Parallel Gateway / Inclusive Gateway，对应 `parallel`、`subflow` 或更明确的并发节点。
- 复杂规则不要写在 Gateway 上，应该放到 DMN / Decision Activity 中，Gateway 只消费决策结果变量。

### 5.3 等待节点

等待节点用于表达业务等待。

示例：

- 等待 1 天。
- 等待客户回复。
- 等待外呼结果。
- 等待人工审批。

在 Temporal 中分别映射为 Timer、Signal、Update 或长轮询 Activity。

### 5.4 人工节点

人工节点表达需要人参与的业务动作。

示例：

- 主管复核。
- 销售确认。
- 风控审批。
- 人工补充信息。

Temporal Workflow 可以暂停等待 Signal / Update。实际待办、权限、通知和表单由业务平台负责。

### 5.5 子流程节点

子流程节点首先是结构化建模手段，用来让一个复杂流程更容易阅读、维护和评审。它把当前流程内部的一组相关节点折叠成一个有业务名称的局部流程，但不一定意味着要启动一个独立 Workflow。

示例：

- 执行一轮外呼。
- 本轮结果后处理。
- 风险复核处理。

Sub-process 更适合表达“当前流程内部的局部复杂步骤”。它通常和父流程一起发布、一起版本化，执行层可以先把它展开为普通节点，或者编译成 Interpreter 内部 scope。

#### 5.5.1 Structural 元素选择

FlowFoundry / BPMN 中的 Sub-process、Participant 属于更偏结构化的建模元素。它们的主要目标是让流程图更容易理解，而不是引入新的业务执行能力。

这些 structural 元素不是“做一件事”的普通 Task，而是在表达流程内部结构、协作边界或一组任务的组织方式。

推荐理解如下：

| Structural 元素 | 中文理解 | 语义 | 典型场景 | Temporal / 平台映射 |
| --- | --- | --- | --- | --- |
| Sub-process | 内嵌子流程 / 结构分组 | 当前流程内部的一组步骤，有明确入口、出口和顺序关系 | 把“执行一轮外呼”展开成准备名单、发起外呼、等待结果、汇总结果 | 可在同一个 Interpreter 中展开执行；复杂时也可编译成内部 scope |
| Participant | 参与者 / Pool | 表示流程协作中的组织、系统或角色边界，不代表一个可执行步骤 | 客户、销售系统、外呼平台、运营团队分别作为参与者 | 主要用于建模和权限/责任边界；不直接映射为 Activity |

这两者的核心区别可以用一句话区分：

```text
Sub-process -> 当前流程内部展开或折叠一段流程
Participant -> 标注谁参与流程，不是执行节点
```

Sub-process 的目标是提升流程可读性：

```text
Sub-process
  -> 是当前流程的一部分
  -> 通常和父流程一起发布、一起版本化
  -> 适合表达局部复杂步骤
```

例如多轮外呼中：

```text
多轮外呼主流程
  -> Sub-process: 执行一轮外呼
       准备名单 -> 发起外呼 -> 等待回执 -> 汇总结果
```

Participant 更偏协作建模，不是执行语义。它可以帮助业务人员看清“谁负责什么”，也可以用于后续权限、泳道、责任归属和审计展示，但不应该直接变成 Temporal Activity。

本平台推荐策略：

- 第一阶段：支持普通 Sub-process 的概念，但执行层可以先展开为普通节点。
- Participant 主要用于画布展示、权限和责任边界，不参与 Execution Plan 的节点推进。
- 仅在当前流程内部复用的局部步骤优先用 Sub-process。

### 5.6 流程复用节点：Child Workflow

Child Workflow 不属于 structural 元素。它更接近一种可调用的执行节点：当前流程调用另一个独立发布、独立版本化、可复用的 Workflow。

它和 Activity 中的各种 Task 类似，都是“当前流程中的一个可执行步骤”。区别在于：

```text
Activity
  -> 调用一个业务能力
  -> 通常是单个外部副作用或一段高代码逻辑
  -> 历史和状态归属于当前 Workflow

Child Workflow
  -> 调用一个完整流程
  -> 被调用方有自己的流程结构、状态、版本和历史
  -> 适合沉淀跨流程复用的稳定业务流程
```

例如：

```text
多个营销流程
  -> Child Workflow: 客户身份核验流程
  -> Child Workflow: 风控审批流程
  -> Child Workflow: 完整外呼子流程
```

因此，Child Workflow 应该作为单独的节点类型或复用能力类型，而不是放在 structural 分类中。它在 DSL / Execution Plan 中可以对应 `subflow` 或 `child_workflow`，运行时映射为 Temporal Child Workflow。

### 5.7 Task 类型语义与支持策略

FlowFoundry / BPMN 中会区分 Generic Task、Service Task、User Task、Manual Task、Send Task、Receive Task、Script Task、Business Rule Task。这些 Task 类型不是为了增加画布复杂度，而是为了让流程图表达“这一步工作的业务性质”。

核心原则是：

```text
Task 类型不是技术分类，而是业务语义分类。
```

如果所有步骤都只叫 Task，流程图会变成：

```text
任务 -> 任务 -> 任务 -> 任务
```

业务人员和开发者都很难看出：

```text
哪一步是系统自动执行？
哪一步需要人审批？
哪一步在等待外部消息？
哪一步是发送通知？
哪一步只是规则判断？
哪一步是线下人工？
```

因此，本平台可以吸收 BPMN / FlowFoundry 的 Task 语义，但不必一开始完整实现所有 BPMN Task。

推荐理解如下：

```text
Generic Task
  -> 通用任务，占位或早期草图。语义最弱，不建议直接运行。

Service Task
  -> 系统自动执行的业务动作。映射到 Temporal Activity。

User Task
  -> 系统内人工待办。创建 human_task 记录，Workflow 等待 Signal / Update。

Manual Task
  -> 线下人工步骤。系统只表达这里有人工动作，不一定创建可管理待办。

Send Task
  -> 主动发送消息。可以作为 send-message Activity 的语义化包装。

Receive Task
  -> 等待外部消息。映射为 Workflow 等待 Signal / Update。

Script Task
  -> 简单变量计算或赋值。应谨慎支持，避免画布变成可视化编程环境。

Business Rule Task
  -> 执行业务规则或决策表。映射到 Decision Activity，并由 Activity 调用 Node.js DMN / JS 决策服务。
```

在本平台中的推荐映射：

```text
Generic Task        -> 草图占位，不直接运行
Service Task        -> Temporal Activity
User Task           -> Human Task + Signal / Update
Manual Task         -> 线下步骤 / 简化 Human Task
Send Task           -> Send Activity
Receive Task        -> Workflow 等待 Signal / Update
Script Task         -> Interpreter 内置小计算，或禁用
Business Rule Task  -> Decision Activity -> Node.js DMN / JS 决策服务
```

MVP 阶段建议支持：

```text
必须支持：
- Service Task
- User Task
- Receive Task / Wait Event
- Timer
- Business Rule Task
- Generic Task 作为草图占位

谨慎支持：
- Script Task

可以先不单独支持：
- Send Task
- Manual Task
```

原因是：

- Send Task 可以先作为 Service Task 的一种 `activityType`。
- Manual Task 可以先作为 User Task 的简化版本，或者仅作为注释 / 标记节点。
- Script Task 容易让业务人员在画布上写过程逻辑，破坏高层业务编排的简洁性。

因此，画布可以展示不同 Task 的业务语义，但执行层仍然保持简单：

```text
大部分自动任务
  -> Activity

人工等待
  -> Human Task + Signal / Update

消息等待
  -> Signal / Update

定时等待
  -> Timer

业务决策
  -> Decision Activity -> Node.js DMN / JS 决策服务
```

这能保留 FlowFoundry / BPMN 的清晰语义，同时避免把完整 BPMN 复杂度一次性搬进平台。

## 6. Temporal 映射关系

推荐映射如下：

```text
Canvas JSON          -> 画布编辑态数据
Flow DSL             -> 流程定义契约
Execution Plan       -> Temporal Interpreter 的运行输入
Flow Run             -> Temporal Workflow Execution
业务节点             -> Activity
流程复用节点         -> Child Workflow
等待时间             -> Workflow Timer
人工操作             -> Signal / Update
状态查询             -> Query / 外部读模型
节点执行日志         -> Activity 结果 + 平台事件表
流程版本             -> Flow DSL version + Execution Plan version
```

典型执行链路：

```text
用户发布流程
  -> 保存 Flow DSL
  -> Flow Compiler 校验节点、边、参数和条件
  -> 生成不可变 Execution Plan
  -> 绑定 Activity Registry
  -> 启动 Flow Interpreter Workflow
  -> Workflow 按 Execution Plan 调度 Activity
  -> Activity 执行业务动作
  -> Workflow 记录节点结果并推进下一节点
  -> 流程完成或等待人工/事件/定时器
```

## 7. Activity Registry

Activity Registry 是画布节点和后端 Activity 之间的契约。

它应该描述：

- Activity Type。
- 展示名称。
- 描述。
- 输入参数 schema。
- 输出参数 schema。
- 默认超时。
- 默认重试策略。
- 幂等 key 模板。
- 权限要求。
- 所属业务域。
- 版本。
- 是否可被业务人员直接使用。

示例：

```yaml
activities:
  - type: execute_call_round
    name: 发起一轮外呼
    description: 根据客户分群发起一轮外呼，并返回外呼任务 ID
    category: call_campaign
    taskQueue: call-campaign
    timeout: 300s
    retry:
      maximumAttempts: 5
    input:
      - name: campaignId
        type: string
        required: true
      - name: segmentId
        type: string
        required: true
    output:
      - name: dialerTaskId
        type: string
      - name: submittedCount
        type: integer
    idempotency:
      keyPattern: "{campaignId}:execute-call-round:{runId}"
      ttl: 72h
```

画布不应该直接扫描代码生成节点，而应该通过 Registry 暴露“可编排能力”。

## 8. 异常处理策略

异常处理不应该大量暴露在画布上。

推荐分层处理：

```text
技术异常：
由 Activity 内部处理，比如 HTTP 失败、限流、网络抖动、SDK 错误。

可恢复异常：
交给 Temporal Activity Retry，比如临时服务不可用。

业务异常：
转换为结构化业务结果，比如客户不存在、余额不足、审批拒绝。

需要人工介入：
返回特定业务状态，由 Workflow 进入人工节点。

不可恢复异常：
标记流程失败，进入平台告警和人工排查。
```

画布层只需要表达少量业务异常分支。

例如：

```text
外呼完成后：
- 有剩余客户 -> 继续下一轮
- 无剩余客户 -> 结束
- 需要主管复核 -> 进入复核节点
```

不要让业务人员处理：

```text
- 外呼平台 HTTP 429 怎么退避
- 请求超时是否重试
- Redis 幂等锁是否释放
- 第三方错误码如何转换
- Worker 崩溃后如何恢复
```

## 9. 幂等与副作用

Temporal 会重试 Activity，因此所有有副作用的 Activity 都必须设计幂等。

高风险副作用包括：

- 发起外呼。
- 扣费。
- 发送短信。
- 创建工单。
- 写入外部 CRM。
- 调用支付或订单系统。

建议每个 Activity 使用业务幂等 key。

示例：

```text
{tenantId}:{flowRunId}:{nodeId}:{activityType}
{campaignId}:execute-call-round:{roundNumber}
{customerId}:send-message:{messageTemplateId}:{flowRunId}
```

幂等逻辑应该由 Activity 基础框架统一封装，而不是每个业务节点重复实现。

## 10. 运行态与可观测性

业务人员需要看到的是业务运行态，而不是 Temporal 内部事件。

平台应提供：

- 当前流程运行到哪个节点。
- 每个节点输入输出。
- 节点成功、失败、等待、跳过状态。
- 人工待办状态。
- 可重试和不可重试错误。
- 业务错误原因。
- 流程耗时。
- 节点耗时。
- 外部系统调用摘要。

Temporal UI 适合开发者和运维排查，不适合作为业务人员的主要运行视图。

建议设计独立的 Flow Run 读模型：

```text
flow_run
flow_node_run
flow_event
human_task
activity_audit_log
```

Temporal Workflow 负责可靠执行，业务平台负责沉淀可读、可检索、可报表化的业务运行数据。

## 11. 权限与治理

流程平台需要治理能力，否则业务人员自由编排会带来风险。

建议提供：

- 节点级权限。
- 流程发布审批。
- 流程版本管理。
- 生产流程只允许发布版本运行。
- 节点参数 schema 校验。
- 敏感节点使用审批。
- 租户隔离。
- 执行配额。
- 审计日志。
- 回滚到历史版本。

Activity Registry 中应标记哪些节点可见、谁可用、是否高风险。

## 12. 与 Dify 的关系

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

## 13. 与 FlowFoundry 的关系

FlowFoundry 更适合 BPMN 标准流程、传统审批、人机流程和企业流程治理场景。它的设计优点之一是流程图相对简洁，强调流程节点、网关、人工任务和定时等待，而不是把大量技术控制细节暴露到画布上。

如果选择 Dify 风格的自研业务画布，则 FlowFoundry 与自研画布处在同一层，通常不需要同时使用。

但 FlowFoundry 的简洁流程抽象值得吸收：

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

## 14. 推荐落地路线

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

## 15. 当前项目可复用资产

当前仓库中的以下设计可以继续复用或参考：

- `worker/src/main/java/com/example/platform/worker/TemporalWorkerBootstrap.java`
  - Worker 启动和 Temporal SDK 接入方式。
- `worker/src/main/java/com/example/platform/callcampaign/CallCampaignActivities.java`
  - Activity 接口和 `@ActivityMethod` 命名方式。
- `worker/src/main/java/com/example/platform/callcampaign/CallCampaignActivitiesImpl.java`
  - Activity 实现层封装业务逻辑的方式。
- `worker/src/main/java/com/example/platform/idempotency/IdempotentActivityExecutor.java`
  - 统一幂等执行模板。
- `registry/activities-registry.yaml`
  - Activity Registry 的雏形。

后续可以把当前 BPMN / FlowFoundry 映射思路升级为：

```text
Flow DSL node.type
  -> Activity Registry activity.type
  -> Temporal @ActivityMethod(name = activity.type)
  -> Activity Implementation
```

## 16. 关键设计原则

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

## 17. 总结

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

