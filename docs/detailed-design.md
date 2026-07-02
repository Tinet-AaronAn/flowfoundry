# FlowFoundry 详细设计

本文档描述 FlowFoundry 平台的实现级设计，包括标识符规范、Workflow 持久化、REST API，以及分层职责、节点抽象、Temporal 映射等细节。

高层架构目标与背景判断见 [business-orchestration-architecture.md](./business-orchestration-architecture.md)。

本地开发与建模器联调（打包、重启 8081、常见误区）见 [local-development.md](./local-development.md)。

---

## 1. 标识符与版本规范

### 1.1 短 ID 生成

平台自生成的唯一短 ID 规则：

- 长度：**8 位**
- 字符集：**小写字母** `a-z` **+ 数字** `0-9`
- 生成方式：**随机生成**，写入 `platform_id_registry` 表，主键冲突时重试

实现：`ShortIdGenerator`（后端）、`PlatformIdGenerator`（带前缀分配）。

### 1.2 各实体 ID 定义


| 实体                                     | 格式                  | 示例                     |
| -------------------------------------- | ------------------- | ---------------------- |
| Workflow                               | `workflow_{短id}`    | `workflow_k3m9x2p1`    |
| Event（Start/End/Intermediate/Boundary） | `event_{短id}`       | `event_a7b2c4d9`       |
| Sub-process                            | `subprocess_{短id}`  | `subprocess_q1w8e5r2`  |
| Task（含 Service/Human/Script 等任务节点）      | `task_{短id}`        | `task_z6y4t8u3`        |
| Gateway                                | `gateway_{短id}`     | `gateway_h5j2k9l0`     |
| Participant                            | `participant_{短id}` | `participant_m3n7p1q4` |


所有带前缀的 ID 在分配时登记到 `platform_id_registry`，确保全局不重复。

前端画布创建节点时调用 `POST /api/workflows/ids`，按节点类型映射 kind（`event` / `task` / `gateway` / `subprocess` / `participant`）。API 不可用时回退到本地临时 ID。

### 1.3 Workflow 版本号

- 初始版本：`1.0.0`
- 递增规则：在**末位 patch** 上加 1（`1.0.0` → `1.0.1` → `1.0.2` …）
- 同一 Workflow 下版本号**不可重复**
- 已发布版本的 `model_json` **不可变**；修改内容应创建新版本或更新 draft 版本

实现：`VersionNumbering`。

---



## 2. Workflow 持久化



### 2.1 数据库（PostgreSQL）

Flyway 迁移：`worker/src/main/resources/db/migration/V1__workflow_storage.sql`

**workflow_definition** — Workflow 元数据


| 列                           | 说明                             |
| --------------------------- | ------------------------------ |
| `id`                        | `workflow_{短id}`，主键            |
| `name`                      | 显示名称                           |
| `status`                    | `draft` / `active` / `retired` |
| `current_version`           | 当前指向的版本号                       |
| `created_at` / `updated_at` | 时间戳                            |


**workflow_version** — 多版本模型


| 列                           | 说明           |
| --------------------------- | ------------ |
| `workflow_id` + `version`   | 联合主键         |
| `status`                    | 该版本状态        |
| `model_json`                | 画布完整 JSON 模型 |
| `created_at` / `updated_at` | 时间戳          |


删除 Workflow 时级联删除所有版本。

**platform_id_registry** — 全局 ID 登记


| 列            | 说明                                |
| ------------ | --------------------------------- |
| `id`         | 完整带前缀 ID，主键                       |
| `kind`       | `workflow` / `event` / `task` / … |
| `created_at` | 分配时间                              |




### 2.2 服务层

包：`com.example.platform.workflow`

- `WorkflowService`：列表、详情、创建、保存版本、新建版本、更新元数据、删除、分配 ID
- `WorkflowMapper`：Entity ↔ DTO
- `WorkflowModelFactory`：空模型模板



### 2.3 REST API

基路径：`/api/workflows`


| 方法     | 路径                                       | 说明                                 |
| ------ | ---------------------------------------- | ---------------------------------- |
| GET    | `/api/workflows`                         | 列表，支持 `keyword`、`status` 过滤        |
| GET    | `/api/workflows/{id}`                    | 详情（含所有版本摘要）                        |
| GET    | `/api/workflows/{id}/versions/{version}` | 单版本详情                              |
| POST   | `/api/workflows`                         | 创建 Workflow（自动分配 ID，初始版本 `1.0.0`）  |
| PUT    | `/api/workflows/{id}/versions/{version}` | 保存指定版本的模型                          |
| POST   | `/api/workflows/{id}/versions`           | 基于已有版本创建新版本                        |
| PATCH  | `/api/workflows/{id}`                    | 更新名称、状态、当前活跃版本                     |
| DELETE | `/api/workflows/{id}`                    | 删除 Workflow                        |
| POST   | `/api/workflows/ids`                     | 分配节点 ID，body: `{ "kind": "task" }` |
| GET    | `/api/workflows/ids/kinds`               | 支持的 kind 列表                        |


前端 `modeler-storage.js` 优先调用上述 API；失败时回退 `localStorage`。

---



## 3. 分层职责



### 3.1 业务流程画布

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

#### 3.1.1 建模器视图分工

前端左侧导航将**设计**与**运行**分离，避免 Modeler 画布被运行控件挤压：

| 视图 | 用途 | 主要内容 |
| --- | --- | --- |
| **Workflows** | Workflow 列表与版本管理 | 搜索、创建、保存版本、跳转 Runs |
| **Modeler** | 流程设计 | Palette、画布、属性面板（仅设计属性）；Compile / View DSL / View Compiled Plan |
| **Simulation** | 运行与模拟 | 只读流程图、节点高亮、Runtime 输入、Run / Query / Complete Human Task、本地模拟（Step / Reset） |
| **Runs** | 运行实例历史 | 本地记录的 execution 列表、Query、打开 Modeler / Simulation |

约定：

- **Properties 面板**只展示设计期属性（General、Assignment、条件、Script Ref 等），不含 Runtime 输入、Workflow ID。
- **顶栏 `#message`** 展示全局操作反馈（编译成功、运行失败等）。
- **Simulation** 中 Run 后自动跳转并高亮当前节点；Query State 按 `currentNodeId` / `waitingHumanTaskNodeId` 高亮。
- 本地模拟不调用 Temporal，用于验证路径、默认分支与 FEEL 条件。

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



### 3.2 Flow DSL

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

### 3.3 Flow Compiler / Interpreter

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
human_task 节点（画布元素：Human Task，DSL kind 仍为 userTask）
  -> Compiler 写入 config.flowFoundryHumanTask.mode

mode = managed（默认）
  -> Workflow 进入 WAITING_HUMAN_TASK
  -> Workflow.await 等待 completeHumanTask Signal（POST /api/flows/runs/{id}/human-task）
  -> 未来可扩展：Activity 创建 human_task 待办记录，供业务系统审批页展示

mode = offline
  -> 仅表达线下人工步骤，不暂停 Workflow
  -> 写入 humanTask.{nodeId}.outcome = offline 后继续推进
  -> 不创建可分配待办，适合电话确认、纸质审批等系统外动作
```

历史 `manualTask` 节点仍可编译，默认映射为 `mode: offline`。Palette 不再提供 Manual Task，统一用 Human Task + Offline 模式表达。

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
  -> Script Task 调用 Decision Activity
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

Script Task 用于执行业务脚本与复杂规则。它不在 Workflow 内执行 JS，而是通过 Activity 调用外部 Node.js DMN / JS 服务：

```text
Script Task
  -> Temporal Workflow 调用 Decision Activity
  -> Decision Activity 调用 Node.js 脚本服务
  -> Node.js 服务根据 scriptRef（decisionRef）找到 JS 脚本
  -> 输入脚本需要的参数
  -> JS 脚本执行业务逻辑
  -> 返回结构化 decisionResult
  -> Workflow 写入 vars
  -> 后续 edge.condition 使用 Safe FEEL AST 判断走向
```

> 画布不再单独提供 Business Rule Task；历史 `businessRuleTask` 节点仍可按 Script Task 语义编译运行。

示例节点：

```json
{
  "id": "decideNextAction",
  "kind": "scriptTask",
  "activityType": "dmn-decision",
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

### 3.4 Temporal Workflow Runtime

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



### 3.5 Activity / Connector 层

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



### 3.6 FDE + Coding Agent 代码生成入口

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

### 3.7 双入口的统一契约

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

## 4. 节点抽象原则

平台节点不应该过细。

不推荐把流程画布设计成通用编程语言的可视化版本。如果业务人员需要在画布上写大量 if/else/for/异常分支，说明节点抽象太低。

本方案不追求复刻 Dify 的完整节点体系，尤其不需要把 HTTP 请求、IF/ELSE、FOR 循环、异常分支、变量赋值等细粒度控制节点作为主要能力暴露给业务人员。更合适的方向是吸收 QuantumBPM / BPMN 的简洁流程设计理念：画布只保留高层业务动作、少量业务判断、等待、人工任务和子流程。

换句话说，Dify 更适合作为交互体验参考，QuantumBPM / BPMN 更适合作为流程抽象简洁性的参考。

推荐节点分为以下几类：

### 4.0 画布 Palette 分类

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


| Palette 分类      | 用途                     | 典型元素                                                    | 是否直接执行   | 运行时映射                                    |
| --------------- | ---------------------- | ------------------------------------------------------- | -------- | ---------------------------------------- |
| Events          | 表达流程开始、结束、流程中等待 | Start Event、End Event、Intermediate Timer | Intermediate 执行；Boundary 仅文档/历史图 | Workflow start/end、Timer、Signal / Update |
| Activities      | 表达当前流程中的业务动作           | Service Task、Human Task、Receive Task、Script Task         | 是        | Activity、Human Task、Decision Activity    |
| Gateways        | 表达流程分支、合流和事件竞争         | Exclusive、Parallel、Inclusive、Event-based                | 否，决定路由   | Interpreter 选边、并发分支、事件等待                 |
| Structural      | 提升流程图可读性和维护性           | Sub-process、Participant / Pool                          | 否，主要组织结构 | 内部 scope、画布分组、权限/责任边界                    |
| Child Workflows | 调用已发布的可复用流程            | Child Workflow 节点                                       | 是        | Temporal Child Workflow                  |
| Annotations     | 补充说明，不影响执行             | Text Annotation、Group、Comment                           | 否        | 文档、审计辅助信息                                |


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

#### 5.0.1 Event 类型语义、约束和用例

Events 用来表达流程生命周期和事件等待语义。FlowFoundry 将 Events 分为四类；**Palette 当前仅提供 Start / End / Intermediate Timer**，Boundary Event 保留设计说明供后续实现，不从 Palette 拖放新节点。

| Event 类型           | 中文理解    | 用途                                     | Palette / MVP | Temporal / Interpreter 映射                                                   |
| ------------------ | ------- | -------------------------------------- | ------------- | --------------------------------------------------------------------------- |
| Start Event        | 流程入口    | 创建新的流程实例，表示流程从这里开始                     | ✅             | Execution Plan 的 `startNodeId`；未来 Message / Timer / Signal Start 可映射为不同启动入口 |
| End Event          | 流程结束    | 表示某条路径完成，流程或分支到达终点                     | ✅             | Interpreter 到达 END 节点后完成当前执行路径                                              |
| Intermediate Timer | 流程中定时等待 | 执行到这里后暂停等待一段时间，再继续后续路径    | ✅（`intermediateCatchEvent`） | `Workflow.sleep(duration)`                                                |
| Boundary Event     | 任务边界事件  | 挂在某个 Task 边界上，在 Task 执行期间监听超时、消息、错误等事件 | ❌ 暂不提供（历史图可保留） | 未实现；目标为 Task 执行期间的 Timer / Signal 监听                                        |


这四类 Event 的关键区别是生命周期不同：

```text
Start Event
  -> 流程实例创建入口。

End Event
  -> 流程路径结束点。

Intermediate Timer
  -> 流程已经运行到某一步，主动停下来等待一段时间。

Boundary Event（后续）
  -> 某个 Task 正在执行时，同时监听一个事件；当前未实现。
```

典型使用案例：

```text
Start Event
  - 用户提交外呼任务后创建流程实例。
  - 后续可扩展 Message Start：收到外部系统消息后创建流程实例。
  - 后续可扩展 Timer Start：每天 09:00 自动创建批处理流程实例。
  - 后续可扩展 Signal Start：收到全局业务信号后创建流程实例。

End Event
  - 外呼任务完成并生成报告。
  - 客户取消触达，流程提前结束。
  - 风险复核拒绝，流程进入失败结束状态。

Intermediate Timer
  - 本轮外呼结束后等待 1 天再进入下一轮。
  - 轮次间隔、延迟重试前的固定等待。

Boundary Event（后续，Palette 未开放）
  - Human Task 超过 2 小时未处理，触发主管升级路径。
  - 服务任务执行期间收到外部撤销信号，切换到补偿或取消路径。
```

建模约束建议：

- Start Event 不应和 Intermediate Timer 混用：流程入口必须使用 Start Event，流程中定时等待使用 Intermediate Timer。
- 同一顶层流程里不允许出现多个同类型 Start Event。
- End Event 可以有多个，用来表达不同业务结束语义，但每条可达路径都应能到达某个 End Event。
- Intermediate Timer 作为独立流程节点，不表达「某个 Task 超时」；任务执行期间的超时监听属于 Boundary Event（后续版本）。
- Boundary Event 必须绑定到具体 Task；MVP 不从 Palette 创建，避免与 Intermediate Timer 混淆。



### 4.1 业务动作节点

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

### 4.2 判断节点

判断节点只表达高层业务判断。

示例：

- 是否需要主管复核。
- 客户是否满足继续触达条件。
- 模型评分是否大于阈值。
- 是否命中风险规则。

复杂规则可以封装在 Activity 或规则服务里，画布只使用结果。

#### 5.2.1 Gateway 类型选择

QuantumBPM / BPMN 中常见的 Gateway 不只是“判断节点”，它们表达的是不同的流程分支语义。平台可以吸收这些语义，但不应该把所有 BPMN 复杂度一次性暴露给业务人员。

推荐理解如下：


| Gateway 类型          | 中文理解          | 语义                              | 典型场景                               | Temporal / Interpreter 映射                         |
| ------------------- | ------------- | ------------------------------- | ---------------------------------- | ------------------------------------------------- |
| Exclusive Gateway   | 排他网关 / 单选分支   | 多条路径中只选择一条                      | 是否继续下一轮、是否需要主管复核、客户分层后选择一种处理方式     | 条件按顺序求值，命中第一条；未命中走 default                        |
| Parallel Gateway    | 并行网关 / 全部同时执行 | 不看条件，所有分支都执行；通常需要再汇合            | 同时生成报表、发送通知、写审计日志；一轮结束后并行做多个独立动作   | 启动多个并发分支；可用 Child Workflow 或 Promise/Async，等待全部完成 |
| Inclusive Gateway   | 包容网关 / 多选分支   | 根据条件选择零条、一条或多条路径；通常需要等待已启动分支汇合  | 同一客户可能同时需要短信、企微、人工跟进；命中多个风险规则时同时处理 | 条件逐条求值，启动所有命中分支；汇合时只等待实际启动的分支                     |
| Event-based Gateway | 事件网关 / 等谁先来   | 不按变量判断，而是等待多个外部事件，哪个事件先发生就走哪条路径 | 等客户回复、等超时、等人工取消、等外部系统回调，先发生者决定后续流程 | 使用 Signal / Update / Timer 竞争；先触发的事件决定下一节点        |


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



### 4.3 等待节点

等待节点用于表达业务等待。

示例：

- 等待 1 天。
- 等待客户回复。
- 等待外呼结果。
- 等待人工审批。

在 Temporal 中分别映射为 Timer、Signal、Update 或长轮询 Activity。

### 4.4 人工节点

人工节点表达需要人参与的业务动作。

示例：

- 主管复核。
- 销售确认。
- 风控审批。
- 人工补充信息。

Temporal Workflow 可以暂停等待 Signal / Update。实际待办、权限、通知和表单由业务平台负责。

### 4.5 子流程节点

子流程节点首先是结构化建模手段，用来让一个复杂流程更容易阅读、维护和评审。它把当前流程内部的一组相关节点折叠成一个有业务名称的局部流程，但不一定意味着要启动一个独立 Workflow。

示例：

- 执行一轮外呼。
- 本轮结果后处理。
- 风险复核处理。

Sub-process 更适合表达“当前流程内部的局部复杂步骤”。它通常和父流程一起发布、一起版本化，执行层可以先把它展开为普通节点，或者编译成 Interpreter 内部 scope。

#### 5.5.1 Structural 元素选择

QuantumBPM / BPMN 中的 Sub-process、Participant 属于更偏结构化的建模元素。它们的主要目标是让流程图更容易理解，而不是引入新的业务执行能力。

这些 structural 元素不是“做一件事”的普通 Task，而是在表达流程内部结构、协作边界或一组任务的组织方式。

推荐理解如下：


| Structural 元素 | 中文理解         | 语义                            | 典型场景                            | Temporal / 平台映射                          |
| ------------- | ------------ | ----------------------------- | ------------------------------- | ---------------------------------------- |
| Sub-process   | 内嵌子流程 / 结构分组 | 当前流程内部的一组步骤，有明确入口、出口和顺序关系     | 把“执行一轮外呼”展开成准备名单、发起外呼、等待结果、汇总结果 | 可在同一个 Interpreter 中展开执行；复杂时也可编译成内部 scope |
| Participant   | 参与者 / Pool   | 表示流程协作中的组织、系统或角色边界，不代表一个可执行步骤 | 客户、销售系统、外呼平台、运营团队分别作为参与者        | 主要用于建模和权限/责任边界；不直接映射为 Activity           |


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

#### 5.5.2 Sub-process 的使用方式和设计

Sub-process 在 FlowFoundry 中应被设计为“当前流程内部的结构分组”。它的价值是把一段局部复杂流程收纳到一个有业务名称的容器里，让流程评审、维护和沟通更清晰。

推荐使用场景：

- 一段局部步骤属于同一个业务阶段，例如“执行一轮外呼”“本轮结果后处理”“风险复核处理”。
- 内部节点和父流程强绑定，通常一起发布、一起版本化。
- 不需要跨多个流程复用，也不需要独立运行历史和独立生命周期。

画布层设计：

- Sub-process 是可调整大小的容器，内部节点仍然是普通流程节点。
- 节点进入容器后可以记录 `parentId` 或 `subProcessId`，用于保存结构关系、导入导出和后续折叠展示。
- 移动 Sub-process 时，可以带动内部节点一起移动，保持相对布局和连线关系。
- Sub-process 本身默认不参与 Sequence Flow 连线，连线仍发生在内部的运行节点之间。

DSL / Execution Plan 设计：

- 第一阶段可以把 Sub-process 视为纯结构元素，不输出为运行节点。
- 编译时将内部节点展开到父流程的 Execution Plan 中，执行顺序仍由内部节点和 Sequence Flow 决定。
- 后续如果需要更强的局部作用域，可以把 Sub-process 编译为 Interpreter 内部 scope，但仍不等同于 Temporal Child Workflow。

需要和 Child Workflow 明确区分：

```text
Sub-process
  -> 当前流程内部的一段结构化步骤
  -> 和父流程一起发布、一起版本化
  -> 执行层可展开或作为内部 scope

Child Workflow / Call Activity
  -> 调用另一个独立流程
  -> 被调用流程独立发布、独立版本化、独立治理
  -> 运行时映射为 Temporal Child Workflow
```



#### 5.5.3 Participant 的使用方式和设计

Participant 在 FlowFoundry 中应被设计为“参与方 / Pool / 泳道边界”。它表达组织、系统、角色或外部参与方，而不是表达一个要被执行的步骤。

推荐使用场景：

- 标注流程中有哪些参与方，例如“运营团队”“外呼平台”“风控系统”“客户”。
- 把任务放入不同参与方泳道，让业务人员看清每一步由谁负责。
- 为后续权限、责任归属、审计统计和跨系统协作视图提供基础元数据。

画布层设计：

- Participant 是可调整大小的泳道容器，有自己的 `id`、`name`、`participantRef` 和边界范围。
- Participant 支持在画布上直接向右、向下或右下角拉伸，用于容纳更多节点。
- 一个流程可以有多个 Participant，用来表达多个组织、系统、角色或外部参与方。
- 一旦画布启用 Participant，除 Participant 本身外，所有节点都必须位于某个 Participant 内；这会让跨参与方协作边界保持明确。
- 当节点被拖入 Participant 区域时，节点保存 `participantId`；如果启用了 Participant，节点不允许被新增或拖动到所有 Participant 之外。
- 移动 Participant 时，带动归属于它的节点一起移动，保持泳道内布局。
- Participant 本身不提供连接点，不参与 Sequence Flow。

模型建议：

```json
{
  "id": "Participant_Operation",
  "kind": "participant",
  "name": "运营团队",
  "config": {
    "participantRef": "operation-team"
  },
  "x": 80,
  "y": 120,
  "width": 720,
  "height": 180
}
```

普通任务可以保存责任归属：

```json
{
  "id": "Task_ManualConfirm",
  "kind": "userTask",
  "name": "Manual list confirmation",
  "config": {
    "flowFoundryHumanTask": { "mode": "managed" },
    "flowFoundryAssignmentDefinition": { "candidateGroups": "call-supervisor" }
  },
  "participantId": "Participant_Operation"
}
```

线下人工步骤示例（不暂停流程）：

```json
{
  "id": "Task_PhoneConfirm",
  "kind": "userTask",
  "name": "Phone confirmation",
  "config": {
    "flowFoundryHumanTask": { "mode": "offline" }
  }
}
```

DSL / Execution Plan 设计：

- Participant 不进入运行节点列表，不直接映射为 Temporal Activity。
- 普通节点的 `participantId` 可以作为责任元数据写入节点 `config`，供权限、审计、运行态展示使用。
- 编译前需要校验 Participant 约束：只要流程存在 Participant，所有非 Participant 节点必须归属到某个 Participant；否则拒绝生成 Execution Plan。
- 执行推进仍由 Task、Gateway、Event、Sequence Flow 决定，Participant 不改变控制流语义。

本平台推荐策略：

- 第一阶段：支持普通 Sub-process 的概念，但执行层可以先展开为普通节点。
- Participant 主要用于画布展示、权限和责任边界，不参与 Execution Plan 的节点推进。
- 仅在当前流程内部复用的局部步骤优先用 Sub-process。
- 需要跨流程复用、独立治理和独立版本生命周期时，使用 Child Workflow / Call Activity，而不是 Sub-process。



### 4.6 流程复用节点：Child Workflow

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

### 4.7 Task 类型语义与支持策略

QuantumBPM / BPMN 中会区分多种 Task 类型。本平台**收敛画布元素**，执行层保持简单。

#### 4.7.1 画布 Task 与执行映射

| 画布元素 | 业务语义 | Execution Plan | 运行时 |
| --- | --- | --- | --- |
| Generic Task | 草图占位 | `ACTIVITY`（需指定 activityType 才可编译） | Temporal Activity |
| Service Task | 系统自动执行 | `ACTIVITY` | 从 Activity Registry 选 `activityType`，经 Router 调用 |
| **Human Task** | 人工步骤（统一入口） | `HUMAN_TASK` | 见下方 `mode` |
| Send Task | 主动发消息 | `ACTIVITY`（如 `send-message`） | 同 Service Task |
| Receive Task | 等待外部消息 | `DECISION`（路由占位） | 目标：Signal / Update（待完整实现） |
| Script Task | 脚本 / DMN 决策 | `ACTIVITY`（`dmn-decision`） | Decision Activity → Node.js |
| Workflow | 调用子流程 | `CHILD_WORKFLOW` | Temporal Child Workflow |

> Business Rule Task 已合并入 Script Task；历史 `businessRuleTask` 仍按 Script Task 编译。

#### 4.7.2 Human Task 与 `flowFoundryHumanTask.mode`

画布只保留一个 **Human Task**（Palette 标签；DSL `kind` 仍为 `userTask`）。**Manual Task 已从 Palette 移除**，线下人工用同一节点 + `mode: offline` 表达。

```text
mode = managed（默认）
  -> 系统内可管理的人工待办
  -> Workflow 暂停，等待 POST /api/flows/runs/{workflowId}/human-task
  -> 属性面板可配置 candidateGroups / assignee
  -> 未来：创建 human_task 记录供审批台展示

mode = offline
  -> 线下人工，系统不跟踪待办
  -> Workflow 不暂停，写入 outcome=offline 后继续
  -> 适合标注「此处需人工电话确认」等流程外动作
```

Compiler 保证每个 `HUMAN_TASK` 节点写入：

```json
"flowFoundryHumanTask": { "mode": "managed" }
```

历史 `manualTask` 导入时默认 `mode: offline`，保证旧图可运行。

#### 4.7.3 Service Task 与 Activity Registry

业务人员不直接面对 Temporal Activity 名称的技术细节，而是：

1. 开发者在 **Activity Registry**（`activities-registry.yaml`）注册能力：`id`、`name`、`taskQueue`、`timeout`、幂等等。
2. 建模器加载 `/api/activities`，在 Service Task 属性里**下拉选择**已注册 `activityType`。
3. Compile 时校验 `activityType` 存在于 Registry；Run 时 Interpreter 通过 `dynamic-activity-router` 分发。

Send Task 不必单独实现：在 Palette 可保留语义化图标，或统一为 Service Task + `send-message` 等 activityType。

#### 4.7.4 MVP Palette 支持范围

```text
必须支持（Palette 可见）：
- Service Task
- Human Task（managed / offline）
- Receive Task
- Intermediate Timer
- Script Task
- Generic Task（草图）
- Gateway / Sub-process / Participant / Child Workflow / Annotation

不在 Palette 提供：
- Manual Task（合并入 Human Task offline）
- Business Rule Task（合并入 Script Task）
- Boundary Event（后续版本；历史图节点仍可读）
- Send Task（可选保留；执行等同 Service Task）
```

执行层归纳：

```text
自动业务动作     -> Activity（Service / Send / Script）
人工步骤         -> Human Task（managed 等 Signal；offline 自动通过）
外部消息等待     -> Receive Task -> Signal / Update（演进中）
定时等待         -> Intermediate Timer -> Workflow.sleep
分支             -> Gateway + Safe FEEL / DMN
```

这能保留 BPMN 的业务可读性，同时避免把完整 BPMN 复杂度一次性搬进平台。


## 5. Temporal 映射关系

推荐映射如下：

```text
Canvas JSON          -> 画布编辑态数据
Flow DSL             -> 流程定义契约
Execution Plan       -> Temporal Interpreter 的运行输入
Flow Run             -> Temporal Workflow Execution
业务节点             -> Activity
流程复用节点         -> Child Workflow
等待时间             -> Workflow Timer（Intermediate Timer）
人工操作             -> Human Task（managed: Signal / Update；offline: 自动继续）
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



## 6. Activity Registry

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

## 7. 异常处理策略

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



## 8. 幂等与副作用

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

## 9. 运行态与可观测性

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

## 10. 权限与治理

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