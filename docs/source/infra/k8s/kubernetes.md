# Kubernetes Manifests

These raw manifests mirror the Helm chart in [infra/helm/helm.md](../helm/helm.md). Keep them around for direct cluster debugging, quick manifest inspection, and one-off application of the workloads.

## Default Workflow

Use the `infra/k8s` directory as the working directory. First create your real
`secrets.yml` from the tracked template (it is gitignored — never commit it),
create the monitoring namespace (one-time, not managed by these manifests — see "Cluster Notes"
below), replace the `<APP_NAMESPACE>` placeholder in `configmaps/grafana-lgtm-config.yml`'s
embedded `prometheus.yaml` with your actual app namespace, then apply everything recursively:

```bash
cd infra/k8s
cp secrets.yml.example secrets.yml   # then fill in real values (see the comments inside)
kubectl create namespace monitoring-rolling-restarts   # one-time
kubectl apply -R -f .
```

This is the default path for the raw manifests in this repository because it keeps the deployment, service, and ingress files together and lets kubectl recurse through the directory layout.

To remove the same resources again, use the same recursive pattern:

```bash
kubectl delete -R -f .
```

## What is here

- [deployments/](deployments) contains one deployment manifest per workload, plus `mongodb-deployment.yml` (the shared MongoDB Deployment and its PersistentVolumeClaim) and `grafana-lgtm-deployment.yml` (the monitoring stack).
- [services/](services) contains one service manifest per workload, including `mongodb-service.yml` and `grafana-lgtm-service.yml`.
- [configmaps/](configmaps) contains `grafana-lgtm-config.yml`, the Prometheus/dashboard/alerting config for the monitoring stack (hand-maintained — see the comment at the top of that file for what it must stay in sync with).
- [rbac/](rbac) contains `grafana-lgtm-rbac.yml` — a ServiceAccount + namespaced Role/RoleBinding granting `grafana-lgtm`'s Prometheus `get/list/watch` on `pods` only, needed for per-pod metrics scraping (see `docs/internal/06-observability.md`).
- [secrets.yml.example](https://github.com/AET-DevOps26/team-the-rolling-restarts/blob/main/infra/k8s/secrets.yml.example) is the tracked template for the `mongodb-credentials`, `mongodb-user-credentials`, `jwt-keys`, and `service-credentials` Secrets consumed by MongoDB and the Spring services. Copy it to `secrets.yml` (gitignored) and fill in real values — the file's comments explain each field. Use a sealed-secret or external secret store for any non-local deployment.
- [ingress.yml](https://github.com/AET-DevOps26/team-the-rolling-restarts/blob/main/infra/k8s/ingress.yml) defines the external routing rules.

## Stack Setup Notes

These are the setup details that matter for this stack and were present in the original guide.

Set the current kubectl context namespace when you want commands to target the same namespace repeatedly:

```bash
kubectl config set-context --current --namespace=<namespace-name>
```

If you have multiple kubeconfig files, merge them before working with the cluster:

```bash
KUBECONFIG=/home/<usr>/.kube/config:/home/<usr>/.kube/<second_config> kubectl config view --merge --flatten > /home/<usr>/.kube/merged-config
```

Use absolute paths when merging configs. Confirm the active configuration with:

```bash
kubectl config view
```

Push images to the registry before applying the manifests or Helm chart. The repository uses the GitHub Actions workflow in [`upload_images.yml`](https://github.com/AET-DevOps26/team-the-rolling-restarts/blob/main/.github/workflows/upload_images.yml) for that step.

## When to use the raw manifests

Use the raw manifests when you want to:

- apply a single workload without templating,
- compare the rendered Helm output against the original Kubernetes YAML,
- debug a cluster issue quickly without involving chart rendering,
- validate a service or ingress change in isolation.

Use the Helm chart when you want reusable installs, upgrades, shared configuration, and the chart-local Makefile targets.

## Cluster Notes

If you need to adjust your current kubectl context, do that outside the manifests themselves. The repository does not assume a namespace in these files — **except** `grafana-lgtm`, which now runs in its own dedicated namespace: its ServiceAccount, Deployment, Service, PVC, and ConfigMaps hardcode `namespace: monitoring-rolling-restarts` (that name must already exist — `kubectl create namespace monitoring-rolling-restarts` — before applying). This is deliberate: it isolates the monitoring stack's own `ResourceQuota` from the app workloads', and it's a fixed project-level name rather than a per-user choice, unlike the app namespace.

One consequence: `grafana-lgtm-rbac.yml`'s `Role`/`RoleBinding` have *no* namespace field (they rely on whatever `-n <app-namespace>` you apply the directory with, same as everything else) — a `RoleBinding` must live in the same namespace as the pods it grants access to, which is the app namespace, not `monitoring-rolling-restarts` where the `ServiceAccount` itself lives. The `RoleBinding`'s `subject` names that namespace explicitly instead.

Another: `configmaps/grafana-lgtm-config.yml`'s embedded `prometheus.yaml` has a literal `<APP_NAMESPACE>` placeholder in its `kubernetes_sd_configs` — replace it with your actual app namespace before applying. The Helm chart doesn't have this problem (that file is rendered with `tpl`, so `{{ .Release.Namespace }}` resolves automatically), but raw manifests have no templating step.

## Usage Guide

Apply only the deployments:

```bash
kubectl apply -f deployments
```

Apply only the services:

```bash
kubectl apply -f services
```

Apply only the ingress:

```bash
kubectl apply -f ingress.yml
```

Inspect the current resources:

```bash
kubectl get all
kubectl get ingress
```

## Resource Map

- `web-client` listens on port `3000`.
- `api-gateway` listens on port `8080`.
- `user-service` listens on port `8081`.
- `content-service` listens on port `8082`.
- `gen-ai` listens on port `8000`.
- `mongodb` listens on port `27017` (ClusterIP only — not exposed via ingress). user-service uses database `users`, content-service uses database `content`, both via the `*-credentials` Secrets.
- The ingress uses path-based routing on a single host (`rolling-restarts.stud.k8s.aet.cit.tum.de`): `/api/` and `/actuator/health` → API gateway, `/ai/` → GenAI service, `/` → web client. Only `/actuator/health` is publicly routed — `/actuator/prometheus` is reachable internally only (Prometheus scrapes pods directly, not through the ingress).
- The ingress secret is `rolling-restarts-tls`.

## Relationship To Helm

The Helm chart in [infra/helm/helm.md](../helm/helm.md) uses the same service names, ports, hostnames, and ingress secret. If the raw manifests and chart diverge, update this file and the chart together.
