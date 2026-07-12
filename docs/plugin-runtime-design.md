# FlowFoundry 插件运行时设计（Plugin Runtime）

> **状态**：P3 已完成（2026-07-12）。前置阅读：[flowfoundry-sdk-design.md](./flowfoundry-sdk-design.md)、[business-orchestration-architecture.md](./business-orchestration-architecture.md)。

## 评审决策记录

| # | 议题 | 决定 |
|---|------|------|
| 1 | 插件运行模型 | **独立 Runner 进程**（每插件一个进程），core 只做控制面；不采用 core 进程内 ClassLoader 加载 |
| 2 | 插件能力边界 | **允许注册 typed workflow**（手写 `WorkflowImpl`），插件方承担 Temporal 版本化约束（见 §8） |
| 3 | 业务前端壳 + BFF | v1 **不纳入**插件体系，继续按现有 App 方式独立部署（见 §11 非目标） |
| 4 | 运行时实现 | **`KubernetesRuntime` 是唯一运行时**，不做 LocalProcessRuntime。前提：core 生产必然多节点（高可用 + 高性能），本地进程模式的单节点所有权约束与此冲突；K8s 声明式对账天然兼容多节点控制面。本地联调用轻量 K8s（kind / docker-desktop） |
| 5 | 插件副本数 | **每插件 runner 副本数（replicas）可指定**：描述符给默认值，管理页面 / API 可随时调整，落到该插件 Deployment 的 `spec.replicas` |

---

## 1. 背景与目标

### 1.1 现状

当前业务方接入 FlowFoundry 需要维护一个完整的 Spring Boot App（参照 `examples/ai-collection-strategy`）：

```text
业务 App（独立部署，:8082）
├── BusinessActivityRouter 实现        # 真正的业务动作（核心价值）
├── TemporalWorkerExtension 实现       # 注册 typed workflow / activities
├── activities-registry.yaml           # Activity 元数据（namespace / taskQueue / 超时重试）
├── application.yml                    # Temporal host、平台地址、API Key
├── Spring Boot 启动类 + 打包 + 部署    # ← 纯粹的工程负担
└── （可选）iframe 业务壳 + BFF
```

其中只有前三项是业务研发真正要写的内容；启动类、打包、部署、配置对每个业务模块都是重复劳动。SDK 的 `TemporalWorkerBootstrap` 已经把 Worker 拉起逻辑标准化（读 registry 的 namespace/defaultTaskQueue → 建 WorkerFactory → 注册 `FlowInterpreterWorkflowImpl` + 所有 `TemporalWorkerExtension` → start），业务 App 里没有任何定制的启动代码。

### 1.2 目标

```text
业务研发：只写 activity 实现（+ 可选 typed workflow）+ registry yaml → 打成插件包
平台管理员：在平台页面上传插件 → 加载 → 启动 / 停止 / 升级 / 回滚
平台：托管插件的运行（进程生命周期、Temporal 连接、健康监控、审计）
```

- 业务方交付物从「可部署的应用」降级为「插件包」，不再需要独立部署环节
- 平台获得插件的完整生命周期控制：上传、校验、启动、停止、升级、回滚、健康监控
- 故障隔离：插件崩溃 / 内存泄漏 / 死循环不影响平台进程和其他插件

### 1.3 关键架构事实（方案可行性基础）

1. **流程编排在平台侧执行**：画布流程由 core 下发、`FlowInterpreterWorkflowImpl`（解释器）驱动，插件默认只提供 activity 执行体。停掉插件 Worker，activity 任务仅在 task queue 排队等待重试，插件恢复后自动续跑——**动态 activity 的启停天然安全**。
2. **typed workflow 是例外**：插件注册的手写 `WorkflowImpl` 在插件 Worker 进程内 replay，受 Temporal 非确定性约束，升级需遵守 §8 的版本化规则。
3. **Worker 配置已收敛**：namespace / taskQueue 来自 registry yaml，Temporal 集群连接可由平台的 `TemporalConnectionRegistry`（多集群管理）+ `platform_namespace.temporal_cluster_id` 推导，插件包无需感知 Temporal 地址。

---

## 2. 总体架构

```text
┌────────────── flowfoundry-core（平台，:8081，多节点部署）──────────────┐
│                                                                      │
│  插件管理页面 ──► PluginAdminController ──► PluginAdminService          │
│                                              │（生命周期状态机 + 审计）  │
│         platform_plugin 表（元数据/期望状态） ─┤                        │
│         插件包存储（本地目录 / 对象存储）       ─┤                        │
│                                              ▼                       │
│                        PluginRuntimeManager（SPI）                    │
│                        └── KubernetesRuntime（唯一实现）               │
└──────────────────────────────┬───────────────────────────────────────┘
                               │ K8s API：创建/删除/扩缩 Deployment、探活
        ┌──────────────────────┼──────────────────────┐
        ▼                      ▼                      ▼
  Deployment 插件A        Deployment 插件B        Deployment 插件C
  replicas=3              replicas=1              replicas=2
  （runner镜像+插件A.jar）      …                      …
        │                      │                      │
        └── Temporal Server（namespace / taskQueue 按 registry 隔离）──┘
```

- **core = 控制面（多节点）**：不执行任何插件代码，只负责存储、编排、监控。上传的 jar 永远不会进入平台 JVM 的 classpath。期望状态存 DB，对 K8s 的操作全部是声明式、幂等的——任何一个 core 节点执行对账结果相同，**多节点部署不存在插件进程所有权问题**。
- **plugin-runner = 数据面**：平台发布的通用宿主镜像（新 SDK 子模块 `flowfoundry-plugin-runner`），每插件一个 Deployment，Pod 数即 runner 副本数，进程级隔离 + 资源配额天然具备。
- **Temporal = 运行时总线**：平台解释器与插件 Worker 通过 namespace + task queue 协作，与现有 App 模式完全一致，**解释器和调度链路零改动**。同一插件的多个 Pod 拉同一 task queue，副本数即吞吐水平扩展。

### 2.1 flowfoundry-plugin-runner（新 SDK 子模块）

Runner 是现有「业务 App 骨架」的通用化产物：一个基于 `flowfoundry-sdk` 的 Spring Boot 可执行 JAR + 基础镜像，内容≈`application-flowfoundry-worker.yml` + `TemporalWorkerBootstrap` + 插件装载器，不含任何业务代码。所有插件共用同一个 runner 镜像，只是挂载的插件 jar 不同。

Pod 内启动命令（由 core 生成的 Deployment 模板固定）：

```bash
# initContainer 先从平台下载插件 jar（按 sha256 校验）到共享 volume
java -Dloader.path=/plugin/plugin.jar \
     -jar /app/flowfoundry-plugin-runner.jar \
     --flowfoundry.plugin.descriptor=classpath:META-INF/flowfoundry-plugin.yaml
```

- 采用 Spring Boot `PropertiesLauncher`（`loader.path`）把插件 jar 追加到 classpath——**插件内的 `@Component`（`BusinessActivityRouter`、`TemporalWorkerExtension`）与 registry yaml 无需任何改造即可被发现**，与现有 App 的编程模型 100% 兼容。
- Runner 读取插件描述符中的 `basePackages` 做受限 component-scan，只扫插件声明的包，避免误装载。
- Temporal host、平台地址等运行配置由 core 生成 Deployment 时通过环境变量注入（来源：`temporal_cluster` 表 + 平台配置），插件包内不出现环境相关配置。
- Runner 暴露 Pod 内管理端口：`/health`（Worker 存活 + task queue poller 状态，接 readiness/liveness probe）；优雅停止走 SIGTERM → `WorkerFactory.shutdown()` drain（`terminationGracePeriodSeconds` 按插件可配）。

### 2.2 PluginRuntimeManager SPI

core 内定义运行时抽象，隔离「怎么跑 runner」这件事（接口保留 SPI 形态，便于未来接入其他编排系统，但**当前只实现 `KubernetesRuntime`**）：

```java
public interface PluginRuntimeManager {
  void apply(PluginDeployment deployment);      // 声明式对账：创建/更新 Deployment（含 replicas）
  void delete(PluginDeployment deployment);     // 删除 Deployment（优雅 drain 由 SIGTERM + grace period 保证）
  RuntimeStatus probe(PluginDeployment deployment);  // readyReplicas / desiredReplicas / Pod 事件摘要
}
```

`KubernetesRuntime` 机制：每插件一个 Deployment（命名 `ff-plugin-<id>`），镜像 = runner 基础镜像，插件 jar 由 initContainer 从平台下载（按 sha256 校验）；探活走 readiness probe；扩缩容 = 改 `spec.replicas`。core 对 K8s 的访问用 in-cluster ServiceAccount（生产）或 kubeconfig（本地联调）。

注意接口是**声明式**的（apply / delete / probe），没有命令式的 start/stop——启停语义由 `desired_state` + 对账循环实现，这保证了多节点 core 并发操作的幂等性。

### 2.3 部署拓扑与扩容模型

**前提：core 生产必然多节点部署（高可用 + 高性能）**，这是砍掉本地进程运行时的直接原因——本地子进程的 PID、日志、探活都是单机语义，多节点会引入进程所有权问题；而 K8s 模式下期望状态在 DB、执行面在 K8s，任何 core 节点做对账都幂等，控制面天然多节点安全。

两个互相独立的扩容维度：

| 维度 | 由什么决定 | 扩容手段 |
|------|-----------|---------|
| 平台容量（API / 建模器 / 解释器调度） | core 实例数与规格 | core 近似无状态（状态在 DB 与 Temporal），加实例即可 |
| 插件执行容量（activity / workflow 吞吐） | **该插件的 runner 副本数 × 单副本并发参数** | 调该插件 Deployment 的 `replicas`（页面/API，见 §6），与 core 节点数无关 |

同一插件的多个 Pod 拉同一 task queue，Temporal 自动分摊任务——副本数即水平扩容，各插件独立设置互不影响（例如催收插件 `replicas=5`，低频插件 `replicas=1`）。

**本地开发**：用轻量 K8s（kind / minikube / docker-desktop 内置 K8s）承载 runner，`./scripts/local-dev.sh` 增加对应准备步骤；Temporal / MySQL 等基础设施仍走现有 Docker 栈。

---

## 3. 插件包格式

一个标准 JAR（Maven 直接 `mvn package` 产出），约定内容：

```text
my-plugin-1.2.0.jar
├── META-INF/flowfoundry-plugin.yaml    # 插件描述符（必需）
├── activities-registry.yaml            # 现有 registry 格式，原样复用（必需）
└── com/xxx/...                         # 业务类：Router / WorkerExtension / typed workflow / 服务
```

描述符 `META-INF/flowfoundry-plugin.yaml`：

```yaml
plugin:
  id: ai-collection-strategy            # 全局唯一，与 namespace 命名对齐
  version: 1.2.0                        # 语义化版本；同 id 可存多版本，同时只 RUNNING 一个
  name: AI 催收策略
  description: 外呼催收多轮策略编排
  basePackages:                         # runner 受限 component-scan 范围
    - com.tinet.flowfoundry.demo.aicollection
  requires:
    sdkVersion: ">=1.0.4"               # 与 runner 内嵌 SDK 的兼容性校验
  temporal:
    namespace: ai-collection-strategy   # 必须是平台已登记的 namespace（复用现有约束）
    # taskQueue 从 activities-registry.yaml 的 defaultTaskQueue 读取，不重复声明
  capabilities:
    typedWorkflows: true                # 声明注册了手写 WorkflowImpl（影响升级策略，见 §8）
  runtime:
    replicas: 2                         # 默认 runner 副本数；启动后可在页面/API 调整（见 §6）
    resources:                          # 生成 Deployment 的 requests/limits
      memory: 1Gi
      cpu: "1"
```

**依赖打包规则**：

- `flowfoundry-sdk`、`temporal-sdk`、Spring 等由 runner 提供，插件 pom 中声明 `provided`
- 业务自有第三方依赖用 maven-shade **打进插件 jar 并 relocate**，避免与 runner classpath 冲突
- 平台校验阶段扫描插件 jar，若发现与 runner 基线冲突的类（同 FQCN 不同实现）则校验失败并给出清单

---

## 4. 生命周期状态机

```text
              upload            validate           load(deploy)         start
  ┌─────────┐        ┌──────────┐        ┌────────┐         ┌─────────┐
  │UPLOADED │ ─────► │VALIDATED │ ─────► │ READY  │ ──────► │ RUNNING │
  └─────────┘        └──────────┘        └────────┘         └────┬────┘
       │ 校验失败          │                   ▲    stop（drain）  │
       ▼                  ▼                   └───────────────────┤
  ┌─────────┐                                                    │ readyReplicas 持续为 0
  │ FAILED  │ ◄──────────────────────────────────────────────────┤（超过阈值时长）
  └─────────┘                                  ┌─────────┐        │
                                               │ STOPPED │ ◄──────┘
                                               └─────────┘
```

| 迁移 | 动作 |
|------|------|
| UPLOADED → VALIDATED | 解析描述符；SDK 版本兼容检查；registry yaml 解析；namespace 已登记校验；activity id 与其他插件冲突检测；classpath 冲突扫描；sha256 落库 |
| VALIDATED → READY | 解包到 `${plugins.dir}/<id>/<version>/`；registry 合并进平台 Activity Registry（建模器左侧面板即刻可见新 activity 节点，标注来源插件） |
| READY → RUNNING | 置 `desired_state=RUNNING` → 对账循环 `apply()` 创建/更新 Deployment（replicas 取当前配置值）→ readyReplicas ≥ 1 且 Temporal `DescribeTaskQueue` 可见 poller 后置 RUNNING |
| RUNNING → STOPPED | 置 `desired_state=STOPPED` → `apply()` 将 replicas 缩到 0（或 `delete()`）；Pod 收到 SIGTERM 后 `WorkerFactory.shutdown()` 优雅 drain（在跑 activity 执行完、新任务留队列）；registry 中该插件的 activity 标记为「离线」但不移除（画布引用不失效） |
| 扩缩容 | RUNNING 态下改 replicas（页面/API）→ `apply()` 更新 `spec.replicas`，不经过状态迁移 |
| 升级 | 同 id 新版本走 UPLOADED→READY，然后旧版本 stop、新版本 start（短暂空窗任务在 task queue 排队；typed workflow 场景见 §8，P4 用 Worker Versioning 消除空窗） |
| Pod 异常退出 | 由 K8s `restartPolicy` 自动拉起；core 对账循环发现 `readyReplicas` 持续为 0（超过阈值时长）置 FAILED 并告警，`desired_state` 不变（修复后自动恢复） |

**对账循环（reconcile）**：core 定时（+ 状态变更时立即）比对 `platform_plugin` 的 `desired_state`/`replicas` 与 K8s 实际状态并 `apply()` 修正。操作幂等，多 core 节点并发对账安全，无需选主；core 重启自恢复也由同一循环覆盖（模式同 `TemporalClusterBootstrapRunner`，但无需认领进程）。

---

## 5. 数据模型

```sql
-- V13__platform_plugin.sql（示意）
CREATE TABLE platform_plugin (
    id            VARCHAR(64)  NOT NULL,           -- 描述符 plugin.id
    version       VARCHAR(32)  NOT NULL,
    display_name  VARCHAR(255) NOT NULL,
    description   VARCHAR(1024),
    namespace     VARCHAR(255) NOT NULL,           -- FK → platform_namespace
    task_queue    VARCHAR(255) NOT NULL,
    typed_workflows BOOLEAN    NOT NULL DEFAULT FALSE,
    state         VARCHAR(32)  NOT NULL,           -- UPLOADED/VALIDATED/READY/RUNNING/STOPPED/FAILED
    desired_state VARCHAR(32)  NOT NULL,           -- RUNNING/STOPPED（对账循环依据）
    replicas      INT          NOT NULL DEFAULT 1, -- 期望 runner 副本数（描述符默认值，页面/API 可改）
    jar_path      VARCHAR(512) NOT NULL,
    jar_sha256    VARCHAR(64)  NOT NULL,
    error_detail  TEXT,
    runtime_ref   VARCHAR(255),                    -- Deployment 名（ff-plugin-<id>）
    uploaded_by   VARCHAR(255) NOT NULL,
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id, version)
);
```

- 同 `id` 多版本共存；`desired_state=RUNNING` 的版本每个 id 至多一个（应用层约束）
- 插件包文件存 `${flowfoundry.plugins.dir}`（P1 本地目录；生产多节点 core 需共享存储或对象存储，路径语义不变，见 §12）
- 所有状态迁移写入现有审计体系（`AuditActions` 新增 `PLUGIN_UPLOAD / PLUGIN_START / PLUGIN_STOP / PLUGIN_DELETE` 等动作）

---

## 6. 平台 API 与页面

### 6.1 管理 API（平台管理员权限，复用现有管理端鉴权）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/admin/plugins` | multipart 上传插件包 → 自动校验，返回校验结果 |
| GET | `/api/admin/plugins` | 列表（含状态、版本、namespace、taskQueue、Worker 健康） |
| GET | `/api/admin/plugins/{id}/{version}` | 详情（描述符、sha256、错误信息、审计摘要） |
| POST | `/api/admin/plugins/{id}/{version}/start` | 启动（置 desired_state=RUNNING，触发对账） |
| POST | `/api/admin/plugins/{id}/{version}/stop` | 优雅停止（置 desired_state=STOPPED） |
| PUT | `/api/admin/plugins/{id}/scale` | **调整 runner 副本数** `{"replicas": 5}`（RUNNING 态立即生效，其他状态仅落库） |
| POST | `/api/admin/plugins/{id}/{version}/reload` | 停旧版 → 启本版（升级/回滚统一入口） |
| DELETE | `/api/admin/plugins/{id}/{version}` | 删除（仅 STOPPED/FAILED/READY 可删；RUNNING 拒绝） |
| GET | `/api/admin/plugins/{id}/{version}/logs?tail=500&pod=…` | Runner 日志尾部（经 K8s API 读 Pod 日志） |

> 业务开发者实操步骤见 [plugin-development-guide.md](./plugin-development-guide.md)。

上传也可走 CI 直连该接口（API Key 走现有平台 Key 体系 + 管理员角色），实现「业务仓库 CI 构建 → 自动推送插件包」。

### 6.2 页面（平台管理端新增「插件管理」Tab）

与在建的 Temporal 集群管理页并列（`modeler-temporal.js` 同层级新增 `modeler-plugins.js`）：

- 插件列表：id / 版本 / 状态徽章 / namespace / taskQueue / **副本数（ready/desired）** / Worker poller 数 / 最近错误
- 拖拽上传 + 校验结果展示（失败给出具体原因：SDK 版本、activity id 冲突、classpath 冲突清单）
- 启动 / 停止 / **扩缩容（改 replicas）** / 升级（选版本 reload）/ 删除，操作二次确认 + 审计
- 日志查看（按 Pod 选择、尾部滚动）
- 建模器 activity 面板中，插件提供的 activity 标注来源插件与在线状态

---

## 7. 健康监控

两条独立信号，避免单点误判：

1. **容器级**：`PluginRuntimeManager.probe()`——Deployment 的 `readyReplicas / desiredReplicas`（readiness probe 接 runner `/health`），附最近 Pod 事件摘要
2. **Temporal 侧**：平台定期对插件 taskQueue 调 `DescribeTaskQueue`（复用现有 `describeTaskQueueTimeoutSeconds` 配置），确认有 poller 在拉任务——这是「插件真的在干活」的最终判据

两者不一致（Pod ready 但没有 poller，或 ready 副本数不足）时置「亚健康」状态并在页面告警。

---

## 8. typed workflow 的版本化约束（重要）

允许插件注册手写 `WorkflowImpl` 带来两类风险，平台通过约束 + 工具化管理：

1. **停机窗口**：插件 stop 期间，运行中的 typed workflow 的 workflow task 无 Worker 可调度，执行暂停（不丢失，恢复后续跑）。升级空窗对 typed workflow 是「暂停」而非「排队重试」，页面在 stop/reload 前展示该 namespace 下运行中的 typed workflow 数量，管理员确认后执行。
2. **非确定性**：新版本插件 replay 旧执行历史时，若 workflow 代码不兼容会触发 NonDeterministicException。约束：
   - 插件描述符声明 `capabilities.typedWorkflows: true`，平台对此类插件的 reload 弹出强提示
   - 插件方必须遵守 Temporal 版本化规范（`Workflow.getVersion()` 分支旧逻辑；或保证变更仅限 activity 内部）
   - P4 引入 Worker Versioning（Build ID）：runner 启动时以 `插件id:版本` 作为 Build ID，旧执行钉在旧版本 Worker、新执行走新版本，实现蓝绿升级——K8s 模式下即同时跑新旧两个版本的 Deployment，天然支持

纯动态 activity 插件（`typedWorkflows: false`）不受上述约束，可随时启停升级。

---

## 9. 安全

上传 jar 等价于授予「在平台托管的进程内执行任意代码」的权限，边界必须明确：

- **信任模型**：插件来自组织内业务团队，平台不是多租户沙箱；插件上线必须走业务仓库的代码审查 + CI 构建，禁止手工构建的 jar 直接进生产
- **权限**：上传/启停/删除仅平台管理员角色；CI 推送使用专用 API Key，全部动作进审计日志
- **完整性**：sha256 落库并在页面展示；initContainer 下载插件 jar 后重新校验哈希，不匹配则 Pod 启动失败
- **隔离**：Pod 级隔离 + 描述符声明的 requests/limits（平台校验上限），互不挤占
- **网络**：runner 管理端口仅 Pod 内可达（探针访问）；runner 从平台拉取 jar 使用拉起时注入的一次性 token；NetworkPolicy 限制 runner 只能访问 Temporal 与授权的业务依赖
- **最小暴露**：runner 注入给插件的平台凭据（如 BFF 场景的 API Key）按 namespace 限定权限，复用现有 namespace 级 Key 体系

---

## 10. 分阶段实施

| 阶段 | 内容 | 交付物 |
|------|------|--------|
| **P1 插件包与校验** | 描述符 schema、`platform_plugin` 表（V13 migration，含 replicas）、上传/校验/列表 API、registry 合并展示 | 上传示例插件包，建模器能看到其 activity；尚不能运行 |
| **P2 Runner + KubernetesRuntime** | `flowfoundry-plugin-runner` 子模块 + 基础镜像、`PluginRuntimeManager` + `KubernetesRuntime`、对账循环、start/stop/scale、探活；本地联调走 kind/docker-desktop K8s | **已完成**：`examples/ai-collection-strategy` 插件包经 API 上传 + start 后在 K8s 跑通 Worker，可调副本数 |
| **P3 管理面完善** | 插件管理页面（含扩缩容）、Pod 日志查看、健康/亚健康告警、审计动作、升级（reload）流程 | **已完成**：`modeler-plugins.js` 管理页 + Activity 面板插件来源标注 |
| **P4 生产强化** | Worker Versioning（Build ID）蓝绿升级、对象存储、NetworkPolicy 模板、资源配额治理 | 生产可用 |

P2 的验收基准：现有 `scripts/redeploy-app.sh` 的联调路径可被「页面上传 + 启动插件」替代（本地开发场景两种方式并存，插件模式不强制）。

## 11. 非目标（v1）

- **业务前端壳 + BFF 不进插件**：iframe 页面与 BFF 是普通 HTTP 服务，与 Worker 部署耦合弱；塞进 runner 会引入 servlet 注册、静态资源、端口治理等一整类新问题。有前端诉求的业务仍按现有 App 模式部署（可以只部署前端 + BFF，Worker 交给插件托管）
- **多租户沙箱**：不做字节码级安全隔离，信任模型见 §9
- **插件间依赖/通信**：插件之间不提供直接调用机制，一切协作经流程编排（Temporal）
- **热替换（不停进程换代码）**：Runner 模式下升级 = 新进程替换旧进程，不做 JVM 内热替换

## 12. 开放问题

1. 多节点 core 下插件包存储：共享文件系统（NFS/PVC）还是对象存储（S3/OSS）？P1 先用本地目录 + 单节点假设，P2 上 K8s 前定稿（倾向对象存储，initContainer 直接从存储拉取可绕过 core）
2. runner 基础镜像的发布渠道与版本策略（是否与 SDK 版本一一对应）——P2 定稿
3. CI 直推插件包的发布流程（是否要求先推 staging 环境验证再提升）——属于流程规范，随 P3 一起定
4. 是否支持 HPA（按 task queue 积压自动扩缩）——P4 后评估，先只做手动 replicas
