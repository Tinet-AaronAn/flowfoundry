# FlowFoundry 流程软件开发指南

本文面向 **flowfoundry-app 业务场景开发者**：说明如何用 FlowFoundry 平台交付一套可编排、可运行、可联调的工作流软件。

**适用读者**：在 `flowfoundry-app/modules/` 下新增或维护业务场景的后端工程师；需要与建模器、Temporal 联调的 FDE / 全栈开发者。

**相关文档**（按需深入）：

| 文档 | 用途 |
|------|------|
| [project-structure.md](./project-structure.md) | 仓库目录与分层 |
| [local-development.md](./local-development.md) | 本地环境、redeploy、排错 |
| [service-urls.md](./service-urls.md) | 端口与路径权威表 |
| [entity-naming.md](./entity-naming.md) | 画布 → DSL → 解释器命名对照 |
| [detailed-design.md](./detailed-design.md) | API、持久化、节点语义 |
| [business-orchestration-architecture.md](./business-orchestration-architecture.md) | 平台定位与架构背景 |
| [flowfoundry-app/modules/ai-collection-strategy/README.md](../flowfoundry-app/modules/ai-collection-strategy/README.md) | 催收 Demo 目录说明 |

---

## 1. 你要交付什么

FlowFoundry 上的「流程软件」通常包含四块，缺一不可：

```text
① Activity Registry   — 声明「画布能选哪些业务能力」
② Activity 实现       — 真实逻辑 + 联调桩 + Router 路由
③ Worker 扩展         — 向 Temporal Worker 注册 Workflow/Activity
④ 流程定义（可选）     — 建模器画布 / Flow DSL，或手写 Temporal Workflow
```

**分工原则**（平台设计边界）：

| 角色 | 负责 | 不负责 |
|------|------|--------|
| 业务 / FDE（画布） | 节点连线、参数映射、分支、人工任务配置 | HTTP 重试、幂等、第三方 SDK |
| 开发者（Activity） | 外部系统调用、幂等、错误归一化、结构化入出参 | 改平台解释器、改 core 通用节点 |
| 平台（flowfoundry-core） | 编译、解释器、建模器、Registry 合并、通用 Activity | 具体业务规则 |

**严格分层**：业务逻辑只写在 `flowfoundry-app/modules/<场景>/`；**不要**把业务 Activity、示例流程写进 `flowfoundry-core/`。

---

## 2. 架构一图流

```text
┌─────────────────────────────────────────────────────────────┐
│  建模器 http://127.0.0.1:8081/                               │
│  画布 model_json → Flow DSL → ExecutionPlan                  │
└───────────────────────────┬─────────────────────────────────┘
                            │ POST /api/flows/run
                            ▼
┌─────────────────────────────────────────────────────────────┐
│  flowfoundry-core：FlowInterpreterWorkflowImpl               │
│  按 ExecutionPlan 调度 Activity / Timer / Gateway / 子流程    │
└───────────────────────────┬─────────────────────────────────┘
                            │ dynamic-activity-router
                            ▼
┌─────────────────────────────────────────────────────────────┐
│  flowfoundry-app/modules/<场景>/                             │
│  XxxActivityRouter → XxxActivitiesImpl | XxxActivitiesStub   │
└───────────────────────────┬─────────────────────────────────┘
                            │ Temporal Activity
                            ▼
                     外部系统 / DB / 消息 / 人工待办
```

**参考实现**：`flowfoundry-app/modules/ai-collection-strategy/`（AI 催收多轮外呼）。

---

## 3. 标准开发流程（推荐顺序）

### 阶段 A：环境与基线

1. **首次启动**（见 [local-development.md](./local-development.md)）：

   ```bash
   chmod +x scripts/local-dev.sh scripts/redeploy-worker.sh scripts/check-progress.sh
   ./scripts/local-dev.sh up
   ./scripts/check-progress.sh    # 期望 ALL_GREEN
   ```

2. **固定联调入口**：http://127.0.0.1:8081/（不是 E2E 的 `:4173`）。

3. **Temporal UI**（Docker 栈）：http://127.0.0.1:8080/

4. 熟悉当前示例场景的配置：
   - Namespace：`call-campaign`
   - Task Queue：`ai-collection-strategy`
   - Registry：`flowfoundry-app/modules/ai-collection-strategy/config/activities-registry.yaml`

### 阶段 B：业务能力设计

在写代码前，先为每个 **Service Task 节点** 定义一张「能力卡片」：

| 字段 | 说明 |
|------|------|
| `id` | Registry 中的 `activityType`（kebab-case，如 `execute-call-round`） |
| 业务名称 | 画布显示名 |
| 输入 | 结构化字段（类型、是否必填） |
| 输出 | 供下游节点 / 网关条件使用 |
| 超时与重试 | 可恢复 vs 不可恢复错误 |
| 幂等 | `keyPattern` + TTL |
| 副作用 | 是否调外部系统、是否可 stub |

**设计原则**：

- 一个 Activity = 一个稳定业务能力（不要一个节点里塞整条子流程）。
- 长等待（外呼回调、打标完成）用 **Service Task + 轮询/wait Activity**，不要用画布循环模拟。
- 技术异常在 Activity 内处理；画布只表达少量业务分支（如「需主管复核 → Human Task」）。

### 阶段 C：注册 Activity（Registry）

在业务模块的 `config/activities-registry.yaml` 中声明（示例见 ai-collection-strategy）。

```yaml
version: "1.0"
namespace: your-scenario          # 业务域标识
defaultTaskQueue: your-scenario   # 与 application.yml 中 temporal.task-queue 一致

activities:
  - id: load-campaign
    name: 加载活动
    description: 校验配置并加载批次
    taskQueue: your-scenario
    timeout: 60s
    retry:
      maximumAttempts: 5
      nonRetryableErrors:
        - CampaignNotFoundException
    input:
      - { name: campaignId, type: string, required: true }
    output:
      - { name: totalContacts, type: integer }
    idempotency:
      keyPattern: "{campaignId}:load-campaign"
      ttl: 24h
```

**注意**：

- 平台 core 已提供 `script-runtime`、`human-task`（见 `flowfoundry-core/.../core-activities-registry.yaml`），运行时与业务 Registry **合并**展示，无需重复注册。
- 修改 Registry 后必须 `./scripts/redeploy-worker.sh`，建模器才会在下拉框看到新 Activity。

### 阶段 D：实现 Activity 代码

在 `flowfoundry-app/modules/<场景>/src/main/java/.../` 按以下结构组织（复制 ai-collection-strategy 即可）：

```text
YourScenarioApplication.java      # main，Import 平台 Configuration
YourActivities.java               # @ActivityInterface，@ActivityMethod(name = "registry-id")
YourActivitiesImpl.java           # 生产实现（@Component，真实副作用）
YourActivitiesStub.java           # 联调桩（长等待立即返回、无外部调用）
YourActivityRouter.java           # implements BusinessActivityRouter，extends DualModeActivityHandler
YourWorkerExtension.java          # implements TemporalWorkerExtension
model/                            # 入出参 POJO
service/                          # 可选：领域服务
```

**Router 要点**（`AiCollectionActivityRouter` 为范本）：

1. `supports(activityType)` 返回本模块支持的 id 集合。
2. `execute(activityType, input)` 用 `switch` 分发到接口方法。
3. 通过 `selectActivities(input)` 在 **Impl / Stub** 间切换（继承 `DualModeActivityHandler`）。

**Temporal 接口命名**：`@ActivityMethod(name = "...")` 必须与 Registry 的 `id` **完全一致**。

**Worker 扩展**：在 `register(Worker worker)` 中注册本场景需要的 Workflow 实现类与 Activity 实现（若除动态解释器外还有手写 Workflow）。

### 阶段 E：场景模块与配置

**新建场景**（复制 `ai-collection-strategy` 目录）：

1. 创建 `flowfoundry-app/modules/<your-scenario>/`（含 `pom.xml`、`main`、`config/`、`src/`）。
2. 在 `flowfoundry-app/pom.xml` 的 `<modules>` 中注册。
3. `application.yml` 引入平台配置并覆盖 Temporal：

   ```yaml
   spring:
     config:
       import: classpath:application-flowfoundry-platform.yml
     application:
       name: your-scenario

   temporal:
     namespace: ${TEMPORAL_NAMESPACE:your-namespace}
     task-queue: ${TEMPORAL_TASK_QUEUE:your-scenario}

   platform:
     activity-registry:
       path: ${ACTIVITY_REGISTRY_PATH:classpath:activities-registry.yaml}
   ```

4. `pom.xml` 将 `config/activities-registry.yaml` 打进 classpath（见示例模块 `<build><resources>`）。

**构建**：

```bash
mvn -pl flowfoundry-app/modules/your-scenario -am -DskipTests package
SCENARIO=your-scenario ./scripts/redeploy-worker.sh
```

### 阶段 F：在建模器上编排流程

1. 打开 http://127.0.0.1:8081/ ，进入 **Modeler** 视图。
2. 创建 Workflow，从 Palette 拖入节点（见 [entity-naming.md](./entity-naming.md) §2.1）：
   - **Service Task**：属性里选择 Registry 中的 `activityType`，配置输入映射。
   - **Human Task**：`flowFoundryHumanTask.mode` = `managed`（暂停等 Signal）或 `offline`（仅标注）。
   - **Script Task**：平台 `script-runtime`，用于复杂业务规则计算；结果写入变量后，由 Gateway 出边上的 FEEL 条件选路。
   - **Gateway**：边上写 Safe FEEL 条件。
   - **Intermediate Event**：定时等待（Timer）。
   - **Child Workflow**：引用已发布的子流程。
3. **保存 / 发布**版本（`model_json` 不可变；改动画布应升 patch 版本）。
4. 在 **Runtime** 视图点击 **Run** 做联调。

**编译校验**：Service Task 的 `activityType` 必须在 Registry 中存在，否则 compile 失败。

### 阶段 G：联调与 RunSource

| 运行方式 | runSource | Activity 行为 |
|----------|-----------|---------------|
| 建模器 Runtime **Run** | `web-modeler` + 请求头 `X-FlowFoundry-Client: web-modeler` | Workflow 真实执行；Activity 走 **Stub**（无外部副作用） |
| 对外 `POST /api/flows/run` | 强制 `production` | 始终 **Impl**（忽略客户端 stub 标记） |

Stub 适用场景：长轮询 Activity（`wait-*-completion`）、依赖外部回调的步骤——联调时立即返回成功态，便于跑通全链路。

**排查路径**：

- 应用日志：`.local/run/worker.log`
- Temporal UI：http://127.0.0.1:8080/ 查看 Workflow History
- 健康检查：`curl --noproxy '*' http://127.0.0.1:8081/actuator/health`

### 阶段 H：测试

| 层级 | 做法 |
|------|------|
| 单元测试 | Router 分发、Impl 业务逻辑（参考 `AiCollectionActivityRouterTest`） |
| 平台测试 | `mvn -pl flowfoundry-core test`（改 core 时） |
| 解释器 E2E | `./scripts/runtime-test.sh`（动态 FlowInterpreter 流程） |
| 场景冒烟 | `./scripts/smoke-test.sh`（手写 Demo Workflow，若有） |
| 建模器 E2E | `npm run test:e2e`（静态 `:4173`，**不替代** 8081 联调） |

### 阶段 I：部署

- **本地调试**：`./scripts/local-dev.sh up` + `./scripts/redeploy-worker.sh`，见 [local-development.md](./local-development.md)。
- **生产部署**：K8s / Helm，镜像由 CI 推送，见 [production-deployment.md](./production-deployment.md)。
- 生产环境使用 **production** RunSource；Stub 仅用于建模器联调。

---

## 4. 日常改代码 checklist

每次修改 `flowfoundry-core/`、`flowfoundry-app/` 或 `modules/` 下**任意** Java 或 `static/` 前端后：

```bash
./scripts/redeploy-worker.sh
# 或：SCENARIO=your-scenario ./scripts/redeploy-worker.sh
curl --noproxy '*' http://127.0.0.1:8081/actuator/health   # 应为 UP
```

然后在浏览器**强制刷新** http://127.0.0.1:8081/

> 静态资源打包在 JAR 内，**没有热更新**。只刷新浏览器不会生效。

---

## 5. 新增 Activity 速查清单

- [ ] 在 `config/activities-registry.yaml` 增加条目（id、input、output、timeout、retry、idempotency）
- [ ] 在 `XxxActivities` 接口增加 `@ActivityMethod(name = "同一 id")`
- [ ] 在 `XxxActivitiesImpl` / `XxxActivitiesStub` 实现方法
- [ ] 在 `XxxActivityRouter.supports` / `execute` 中注册分发
- [ ] 补充单元测试
- [ ] `./scripts/redeploy-worker.sh`
- [ ] 建模器 Service Task 下拉验证 + Runtime Run 跑通
- [ ] （可选）Temporal UI 确认 Activity 调度与重试符合预期

---

## 6. 两种交付路径

### 路径 1：画布驱动（主流）

适合标准化、可配置、需业务人员参与编排的流程。

```text
Registry → 建模器编排 → 编译 ExecutionPlan → FlowInterpreterWorkflow 执行
```

开发者主要交付 **Registry + Activity**；流程结构由画布维护并存入 PostgreSQL。

### 路径 2：代码驱动（FDE / 深度定制）

适合复杂定制、强 Temporal 原生语义、或画布表达力不足的场景。

```text
手写 Workflow Interface/Impl + Activity
  → 测试与 Review
  → 登记 Registry（供后续画布复用）
  → WorkerExtension 注册
```

两条路径共享 **Activity Registry** 与 **Deployment Contract**（namespace、task queue、配置），见 [business-orchestration-architecture.md](./business-orchestration-architecture.md) §3.7。

---

## 7. 常见误区

| 误区 | 正确做法 |
|------|----------|
| 在 `flowfoundry-core` 写业务 Activity | 只写 `flowfoundry-app/modules/<场景>/` |
| 改 Registry 后不 redeploy | 必须 package + 重启 8081 |
| 用 `npm run test:e2e` 代替 8081 联调 | E2E 不经过 Spring Boot / Temporal |
| 画布上写 HTTP 重试、幂等逻辑 | 放进 Activity Impl |
| 对外 API 期望走 Stub | 仅建模器 Run 双条件触发 Stub |
| Temporal UI 用 8233（Docker 环境） | Docker 栈用 **8080**（见 [service-urls.md](./service-urls.md)） |
| Generic Task 直接 Run | 须改为 Service / Human / Script Task |

---

## 8. 扩展阅读：节点如何映射到 Temporal

| 画布元素 | 运行时 |
|----------|--------|
| Service Task | `ACTIVITY`，Registry `activityType` → Router |
| Human Task | `ACTIVITY` + `human-task`；managed 时 Workflow 等 Signal |
| Script Task | `ACTIVITY` + `script-runtime` |
| Gateway | 解释器按 Safe FEEL 选边 |
| Intermediate Event (timer) | `Workflow.sleep`（web-modeler 联调可跳过 sleep） |
| Child Workflow | Temporal Child Workflow |

完整对照表见 [entity-naming.md](./entity-naming.md) 与 [detailed-design.md](./detailed-design.md) §4.7。

---

## 9. 获取代码结构帮助（graphify，可选）

仓库可维护 `graphify-out/` 知识图谱，用于查跨模块依赖与架构关系：

```bash
# 首次或大范围变更后
/graphify .

# 日常改代码后（仅 AST，无 API 成本）
graphify update .

# 查询
graphify query "CallCampaignActivitiesImpl 连接了哪些模块？"
graphify path "FlowController" "FlowInterpreterWorkflowImpl"
```

Cursor 项目若已安装 `.cursor/rules/graphify.mdc`，Agent 会优先用图谱 orient 再读代码。详见 graphify 文档与 `graphify-out/GRAPH_REPORT.md`。

---

## 10. 下一步

1. 通读示例模块：`flowfoundry-app/modules/ai-collection-strategy/`
2. 本地跑通：建模器 Run → Temporal UI 看 History → `./scripts/runtime-test.sh`
3. 按 §5 清单新增一个试点 Activity
4. 需要新场景时复制模块目录并在 `flowfoundry-app/pom.xml` 注册

有问题先查 [local-development.md](./local-development.md) 与 [service-urls.md](./service-urls.md)；平台行为细节以 [detailed-design.md](./detailed-design.md) 为准。
