# Kubernetes Manifests

These raw manifests mirror the Helm chart in [infra/helm/helm.md](../helm/helm.md). Keep them around for direct cluster debugging, quick manifest inspection, and one-off application of the workloads.

## Default Workflow

Use the `infra/k8s` directory as the working directory and apply everything recursively from there:

```bash
cd infra/k8s
kubectl apply -R -f .
```

This is the default path for the raw manifests in this repository because it keeps the deployment, service, and ingress files together and lets kubectl recurse through the directory layout.

To remove the same resources again, use the same recursive pattern:

```bash
kubectl delete -R -f .
```

## What is here

- [deployments/](deployments) contains one deployment manifest per workload.
- [services/](services) contains one service manifest per workload.
- [ingress.yml](ingress.yml) defines the external routing rules for the three hosts.

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

Push images to the registry before applying the manifests or Helm chart. The repository uses the GitHub Actions workflow in [`.github/workflows/upload_images.yml`](../../.github/workflows/upload_images.yml) for that step.

## When to use the raw manifests

Use the raw manifests when you want to:

- apply a single workload without templating,
- compare the rendered Helm output against the original Kubernetes YAML,
- debug a cluster issue quickly without involving chart rendering,
- validate a service or ingress change in isolation.

Use the Helm chart when you want reusable installs, upgrades, shared configuration, and the chart-local Makefile targets.

## Cluster Notes

If you need to adjust your current kubectl context, do that outside the manifests themselves. The repository does not assume a namespace in these files.

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
- The ingress routes `app.rolling-restarts.stud.k8s.aet.cit.tum.de` to the web client, `api.rolling-restarts.stud.k8s.aet.cit.tum.de` to the API gateway, and `ai.rolling-restarts.stud.k8s.aet.cit.tum.de` to the GenAI service.
- The ingress secret is `rolling-restarts-tls`.

## Relationship To Helm

The Helm chart in [infra/helm/helm.md](../helm/helm.md) uses the same service names, ports, hostnames, and ingress secret. If the raw manifests and chart diverge, update this file and the chart together.
