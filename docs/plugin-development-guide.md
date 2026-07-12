# FlowFoundry 插件开发指南

本文面向**业务 Activity / typed workflow 开发者**：说明如何把现有 SDK Worker 代码打成**插件包**，由平台托管运行（无需独立部署 `:8082` Worker）。

> **架构背景**见 [plugin-runtime-design.md](./plugin-runtime-design.md)。本地联调见 [local-development.md §插件运行时模式](./local-development.md#插件运行时模式p2-可选)。

**官方示例**：`examples/ai-collection-strategy/`（同一套业务代码，既可 `redeploy-app.sh` 跑 Worker，也可 `-Pplugin` 打插件包）。

---

## 1. 插件 vs 独立 Worker App

| 维度 | 独立 Worker App（`:8082`） | 插件包（Plugin） |
|------|---------------------------|------------------|
| 交付物 | 可执行 Spring Boot JAR + 部署 | `*-plugin.jar`（无启动类） |
| 运行方式 | 本机 / K8s 自管进程 | 平台在 K8s 创建 runner Deployment |
| 适用 | 需要 iframe 壳、BFF、自定义 HTTP | 只需 Activity + 可选 typed workflow |
| 建模器联调 | `redeploy-worker.sh` + `redeploy-app.sh` | `plugin-runtime-dev.sh` + 页面上传启动 |
| 业务代码 | `Router` + `WorkerExtension` + registry yaml | **相同**，额外加描述符 yaml |

两种方式可并存；插件模式联调时**不要**同时启动 `:8082` 业务 Worker（Temporal 会出现重复 poller）。

---

## 2. 插件包内容

标准 JAR 结构：

```text
my-business-1.0.4-plugin.jar
├── META-INF/flowfoundry-plugin.yaml    # 插件描述符（必需）
├── activities-registry.yaml            # 与 App 模式相同（必需）
└── com/yourcompany/...                 # Router / WorkerExtension / Activity / typed workflow
```

**不要**打进插件包的内容：

- `*Application.java`（Spring Boot 启动类）
- `application.yml`（Temporal / 平台地址由 runner 环境变量注入）
- 静态资源 / iframe 页面（仍走独立 App 部署）

---

## 3. 描述符 `META-INF/flowfoundry-plugin.yaml`

```yaml
plugin:
  id: my-business                    # 全局唯一，建议与 namespace 对齐
  version: 1.0.4                     # 语义化版本；同 id 可多版本共存
  name: 我的业务插件
  description: 简短说明
  basePackages:                      # runner 受限 component-scan
    - com.yourcompany.mybusiness
  requires:
    sdkVersion: ">=1.0.4"
  temporal:
    namespace: my-business           # 必须是平台已登记的 namespace
  capabilities:
    typedWorkflows: false            # 若注册了手写 WorkflowImpl，设为 true
  runtime:
    replicas: 1                      # 默认副本数；启动后可在页面/API 调整
    resources:
      memory: 1Gi
      cpu: "1"
```

`taskQueue` 从 `activities-registry.yaml` 的 `defaultTaskQueue` 读取，**不要**在描述符重复声明。

---

## 4. Maven 打包（推荐做法）

参照 `examples/ai-collection-strategy/pom.xml`：

1. **资源**：`config/activities-registry.yaml` 打进 jar；`src/plugin/` 下放描述符（`-Pplugin` profile 合并进资源）。
2. **附加 jar**：`maven-jar-plugin` 增加 `classifier=plugin` 的 execution，排除启动类、`application.yml`、`static/**`。
3. **构建命令**：

```bash
mvn -pl examples/ai-collection-strategy -Pplugin -am package -DskipTests
# 产物：examples/ai-collection-strategy/target/ai-collection-strategy-demo-1.0.4-plugin.jar

# 或官方脚本
./scripts/build-ai-collection-plugin.sh
```

业务代码仍依赖 `flowfoundry-sdk`；`flowfoundry-sdk`、`temporal-sdk`、Spring 由 **runner 镜像**提供。若插件携带与 runner 冲突的第三方库，需 maven-shade relocate（见设计文档 §3）。

---

## 5. 业务代码要求（与 App 模式相同）

| 组件 | 说明 |
|------|------|
| `activities-registry.yaml` | `namespace`、`defaultTaskQueue`、`activities[]` |
| `BusinessActivityRouter` | `activityType` → 实现 Bean |
| `TemporalWorkerExtension` | 向 Worker 注册 activity / typed workflow |
| Activity 实现类 | `@Component`，由 `basePackages` 扫描 |

**Namespace 前置**：插件 `temporal.namespace` 须在平台 **Namespaces** 页或 Admin API 中已存在（本地可由平台 bootstrap 自动登记）。详见 [workflow-development-guide.md §4.5](./workflow-development-guide.md#45-namespace-前置条件必读)。

**typed workflow**：若 `capabilities.typedWorkflows: true`，stop/reload 前须确认 replay 安全；页面会弹出强提示。

---

## 6. 上传与生命周期

### 6.1 管理 API（平台管理员，`X-API-Key`）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/admin/plugins` | multipart 上传 `file=@*.jar` |
| GET | `/api/admin/plugins` | 列表 |
| GET | `/api/admin/plugins/{id}/{version}` | 详情 |
| POST | `/api/admin/plugins/{id}/{version}/start` | 启动 |
| POST | `/api/admin/plugins/{id}/{version}/stop` | 停止 |
| PUT | `/api/admin/plugins/{id}/scale` | `{"replicas":N}` |
| POST | `/api/admin/plugins/{id}/{version}/reload` | 升级/回滚 |
| DELETE | `/api/admin/plugins/{id}/{version}` | 删除（非 RUNNING） |
| GET | `/api/admin/plugins/{id}/{version}/logs?tail=500` | Pod 日志尾部 |

本地默认 Key：`local-admin-key`（见 `application-flowfoundry-platform.yml`）。

### 6.2 建模器页面

平台侧栏 **插件**（`modeler-plugins.js`）：拖拽上传、启停、扩缩容、重载、日志、删除；Activity 面板显示插件来源徽章。

上传成功后 Activity Registry 立即合并；**启动**后 K8s runner Pod 才开始 poll task queue。

---

## 7. 本地联调步骤

**前提**：Docker Desktop / OrbStack **已启用 Kubernetes**；基础设施已 `up`。

```bash
./scripts/local-dev.sh infra
./scripts/plugin-runtime-dev.sh          # 构建 runner 镜像 + 以插件运行时模式部署 :8081
./scripts/build-ai-collection-plugin.sh  # 构建示例插件 jar

# 方式 A：建模器 → 插件 → 拖拽上传 → 启动
# 方式 B：API
PLUGIN_JAR=$(./scripts/build-ai-collection-plugin.sh)
curl -X POST -H "X-API-Key: local-admin-key" -F "file=@$PLUGIN_JAR" \
  http://127.0.0.1:8081/api/admin/plugins
curl -X POST -H "X-API-Key: local-admin-key" \
  http://127.0.0.1:8081/api/admin/plugins/ai-collection-strategy/1.0.4/start

kubectl get deploy,pods -n flowfoundry-plugins
curl -H "X-API-Key: local-admin-key" \
  http://127.0.0.1:8081/api/admin/plugins/ai-collection-strategy/1.0.4
```

要点：

- 插件模式平台加载 `activities-registry-platform-plugin.yaml`（无内置业务 Activity）；registry 来自已上传插件。
- Runner Pod 经 `host.docker.internal` 访问本机 Temporal（`:7233`）、平台（`:8081`）、Redis。
- 改插件代码后：重新 `mvn -Pplugin package` → 上传新版本 → **重载** 或 stop/start。
- 改平台 / 建模器后：`FLOWFOUNDRY_PLUGIN_RUNTIME_ENABLED=true ./scripts/redeploy-worker.sh`，浏览器强制刷新。

环境变量见 `application-flowfoundry-platform.yml` → `flowfoundry.plugins.runtime.*`。

---

## 8. 验收清单

- [ ] 上传 jar 校验通过（描述符、SDK 版本、namespace、activity id 无冲突）
- [ ] 建模器 Activity 面板可见本插件 activity，带来源标注
- [ ] 启动后 `kubectl get pods -n flowfoundry-plugins` 为 Running
- [ ] 插件详情 API 中 `activityPollers >= 1`、`runtimeHealthy: true`
- [ ] 在建模器 Run 或 `runtime-test.sh` 能调度到本插件 activity
- [ ] stop 后 poller 归零；restart 后恢复

---

## 9. 相关文档

| 文档 | 用途 |
|------|------|
| [plugin-runtime-design.md](./plugin-runtime-design.md) | 控制面 / K8s 运行时 / 状态机 / 安全 |
| [local-development.md](./local-development.md) | redeploy、插件模式脚本 |
| [service-urls.md](./service-urls.md) | 端口与 API 路径 |
| [workflow-development-guide.md](./workflow-development-guide.md) | Activity / Registry 通用开发 |
| [flowfoundry-sdk-design.md](./flowfoundry-sdk-design.md) | SDK 模块与 `flowfoundry-plugin-runner` |
