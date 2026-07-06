# 生产部署

本文说明 FlowFoundry 在 **Kubernetes / 生产环境** 的部署方式。生产部署由运维或 CI/CD 流水线执行，**不涉及开发者在本地重新构建 Docker 镜像**。

本地改代码与联调见 [local-development.md](./local-development.md)。

---

## 与本地调试的区别

| 项 | 本地调试 | 生产部署 |
|----|----------|----------|
| 文档 | [local-development.md](./local-development.md) | 本文 |
| 应用运行 | 宿主机 `java -jar`（快速 redeploy） | K8s Deployment |
| 镜像 | 不需要（或仅 CI 构建） | **由 CI 构建并推送到镜像仓库** |
| 基础设施 | 本地 Docker Compose | 集群内 Helm / 托管服务 |
| 日常操作 | `redeploy-worker.sh` | `helm upgrade` / `kubectl apply` |

---

## 前置条件

- 可访问的 Kubernetes 集群
- `kubectl`、`helm` 已配置且指向目标集群
- **容器镜像已由 CI 推送到仓库**（生产节点拉取现成镜像，不在生产环境 `docker build`）
- 已准备 Secret（数据库密码、Redis、Temporal、License 等）

---

## 部署组件

| 组件 | 方式 | 配置参考 |
|------|------|----------|
| Temporal | Helm | `deploy/helm/temporal/values-production.yaml` |
| FlowFoundry 平台（企业版） | Helm | `deploy/helm/flowfoundry/values-production.yaml` |
| 业务 Worker（如 AI 催收） | K8s Manifest | `deploy/k8s/call-campaign-worker.yaml` |
| 命名空间 | Manifest | `deploy/k8s/namespaces.yaml` |
| Secret 模板 | Manifest | `deploy/k8s/secrets.example.yaml` |

---

## 标准部署步骤

以下步骤假设镜像版本、Registry 凭证已由 CI/CD 写入 values 或 manifest。**不要在生产机器上执行 `mvn package` 或 `docker build`。**

### 1. 创建命名空间

```bash
kubectl apply -f deploy/k8s/namespaces.yaml
```

### 2. 配置并应用 Secret

复制 `deploy/k8s/secrets.example.yaml`，填入真实值后应用：

```bash
kubectl apply -f deploy/k8s/secrets.example.yaml
```

### 3. 安装 Temporal

```bash
helm repo add temporalio https://go.temporal.io/helm-charts
helm repo update

helm upgrade --install temporal temporalio/temporal \
  -n temporal \
  -f deploy/helm/temporal/values-production.yaml
```

创建业务 namespace：

```bash
kubectl exec -n temporal deploy/temporal-admintools -- \
  tctl --namespace call-campaign namespace register
```

### 4. 部署 Activity Worker

确保 `deploy/k8s/call-campaign-worker.yaml` 中的 **image** 指向 CI 已推送的镜像标签，例如：

```yaml
image: your-registry/flowfoundry-app:1.0.0
```

应用 Activity Registry ConfigMap 与 Worker Deployment：

```bash
kubectl create configmap activities-registry \
  --from-file=activities-registry.yaml=flowfoundry-app/modules/ai-collection-strategy/config/activities-registry.yaml \
  -n bpm --dry-run=client -o yaml | kubectl apply -f -

kubectl apply -f deploy/k8s/call-campaign-worker.yaml
```

### 5. 部署 FlowFoundry 企业平台（可选）

在 `deploy/helm/flowfoundry/values-production.yaml` 中配置 License、OIDC、数据库连接等，然后：

```bash
helm upgrade --install flowfoundry <enterprise-chart> \
  -n bpm \
  -f deploy/helm/flowfoundry/values-production.yaml
```

---

## 发布新版本（滚动升级）

生产发版由 CI/CD 完成镜像构建与推送后，运维仅更新镜像 tag 并滚动重启：

```bash
# 示例：更新 Worker 镜像
kubectl set image deployment/call-campaign-worker \
  worker=your-registry/flowfoundry-app:NEW_TAG \
  -n bpm

kubectl rollout status deployment/call-campaign-worker -n bpm
```

Helm 管理的组件：

```bash
helm upgrade flowfoundry <enterprise-chart> -n bpm \
  -f deploy/helm/flowfoundry/values-production.yaml \
  --set image.tag=NEW_TAG
```

---

## 健康检查与验证

```bash
kubectl get pods -n bpm
kubectl get pods -n temporal
kubectl logs -n bpm deploy/call-campaign-worker --tail=50
```

确认 Worker 已连接 Temporal、数据库迁移（Flyway）成功、Task Queue `ai-collection-strategy` 有 poller。

---

## 参考脚本

`deploy/scripts/install-production.sh` 汇总了上述步骤的示例命令，供首次装机参考。其中 **本地 `docker build` 段落仅作开发环境辅助**；生产环境应改为使用 CI 产出镜像，并更新 manifest 中的 `image` 字段后再 `kubectl apply`。

---

## 相关文档

- [service-urls.md](./service-urls.md) — 端口与路径对照（本地）
- [project-structure.md](./project-structure.md) — 仓库结构
- [local-development.md](./local-development.md) — 本地调试
