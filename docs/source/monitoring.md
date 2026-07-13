# Monitoring

Metrics, dashboards, and alerts run on `grafana/otel-lgtm` — a single image bundling real
Prometheus, Grafana, Tempo (traces), Loki (logs), and Pyroscope (profiles). Same image, same
configuration files, in both docker-compose and Kubernetes/Helm.

## Reaching the monitoring stack

Grafana is now routed through the same reverse proxy / ingress as the rest of the app, under
`/monitoring`, on every deployment target — no port-forward or direct-port access needed anymore
(though the direct port still works too; see below). It requires a real login: the base
`grafana/otel-lgtm` image defaults to anonymous **Admin** access with no login at all
(`GF_AUTH_ANONYMOUS_ENABLED=true`, org role `Admin`), which is explicitly disabled everywhere this
stack is deployed.

- **docker-compose (local)**: `http://localhost:${APP_PORT:-8080}/monitoring/`
- **Kubernetes**: `https://<ingress host>/monitoring/` (e.g.
  `https://rolling-restarts.stud.k8s.aet.cit.tum.de/monitoring/` on the Helm target, or the raw
  `k8s.rolling-restarts.stud.k8s.aet.cit.tum.de` host on the raw-manifests target)
- **Azure VM**: `http://<vm-ip>/monitoring/` (no TLS on that target yet — see the cookie-`Secure`
  gotcha in `docs/internal/07-gotchas.md` for the related, still-open TLS gap)

Login: `admin` / whatever `GRAFANA_ADMIN_PASSWORD` (docker-compose/Ansible) or
`monitoring.adminPassword` (Helm secrets-values.yaml) / the `GRAFANA_ADMIN_PASSWORD` repo secret
(CI) is set to. **Rotating it just means changing that value and redeploying** — an
initContainer (Kubernetes) / one-shot service (docker-compose) runs `grafana cli admin
reset-admin-password` on every deploy, before Grafana itself starts, so the deployed value is
always the actual source of truth (it doesn't rely on Grafana's own first-boot-only application of
`GF_SECURITY_ADMIN_PASSWORD`, which would otherwise silently do nothing on an already-initialized
volume — see `docs/internal/07-gotchas.md` for the full story, including two sharp edges hit
getting this right on the real cluster).

Grafana is also still reachable directly, bypassing the proxy/ingress entirely, which is handy for
ad-hoc access or scripts (`infra/scripts/smoke-test.sh`'s Loki check uses this path):

```sh
# docker-compose — published straight to the host, overridable via LGTM_GRAFANA_PORT in infra/.env
open http://localhost:3001

# Kubernetes — port-forward to the ClusterIP Service in its own namespace
kubectl port-forward svc/grafana-lgtm 3001:3000 -n monitoring-rolling-restarts
open http://localhost:3001
```

If you're not sure it's running (Kubernetes):

```sh
kubectl get pods,svc -n monitoring-rolling-restarts -l app=grafana-lgtm
```

### Once you're in Grafana

Everything below is provisioned automatically — nothing to import by hand:

- **Dashboard** — left nav → *Dashboards* → **Service Overview** (request rate / error rate /
  duration percentiles per service; use the `job` dropdown at the top to filter to a service).
- **Alerts** — left nav → *Alerting* → *Alert rules* → **Service Health** folder (the two rules
  below).
- **Logs** — left nav → *Explore* → pick the **Loki** datasource → e.g. query
  `{service_name="api-gateway"}` (all four services forward logs here).
- **Traces** — *Explore* → **Tempo** datasource. **Profiles** — *Explore* → **Pyroscope**.

### Reaching Prometheus directly (ad-hoc PromQL)

The bundled Prometheus listens on `9090` inside the container. It's normally used through
Grafana's *Explore*, but you can hit its own UI/API directly:

```sh
# docker-compose: 9090 isn't published to the host, so go through the container
docker exec rolling-restarts-grafana-lgtm-1 \
  curl -s 'http://localhost:9090/api/v1/query?query=up'

# Kubernetes: the Service only exposes 3000/4317/4318, so port-forward to the POD's 9090
kubectl port-forward "$(kubectl get pod -n monitoring-rolling-restarts -l app=grafana-lgtm -o name)" \
  9090:9090 -n monitoring-rolling-restarts
# then browse http://localhost:9090
```

### OTLP ingest endpoints (for reference)

Services push metrics/traces/logs to the collector inside the same container, not to Grafana's
port:

- docker-compose: `http://grafana-lgtm:4318` (OTLP/HTTP), `grafana-lgtm:4317` (OTLP/gRPC) — bare
  service DNS, since everything shares one Docker network.
- Kubernetes: `http://grafana-lgtm.{{ .Values.monitoring.namespace }}:4318` /
  `grafana-lgtm.{{ .Values.monitoring.namespace }}:4317` — **must** be namespace-qualified, since
  `grafana-lgtm` now lives in its own namespace, separate from the app workloads pushing to it.
  The bare hostname only resolves within the same namespace; using it cross-namespace fails
  silently from the app's point of view (no error, no crash — the OTLP exporter just never
  reaches anything, confirmed live via `kubectl exec ... python3 -c
  "socket.gethostbyname('grafana-lgtm')"` failing while the namespace-qualified form resolved
  fine). `infra/helm/values.yaml` embeds this exact template expression as a literal string,
  rendered via `tpl` in `templates/deployment.yaml` rather than hardcoding the namespace name a
  second time; the raw `infra/k8s/deployments/*.yml` manifests hardcode the equivalent FQDN since
  they have no templating step.

You don't normally call these by hand — they're wired into every service's env
(`MANAGEMENT_OTLP_METRICS_EXPORT_URL` etc. for Spring, `OTEL_EXPORTER_OTLP_ENDPOINT` for gen-ai).
If you ever add a new service to this stack, remember the namespace-qualified form for Kubernetes
— this exact mistake (bare `grafana-lgtm`) silently broke OTLP push for all 4 existing services
for a while after the namespace split, undetected because classic-scrape metrics (a separate,
unrelated pull-based path) kept working and masked it.

## What's provisioned

- **Datasources**: Prometheus, Tempo, Loki, Pyroscope — all pre-wired by the image itself with
  exemplar/trace correlation.
- **Dashboards**: `infra/grafana/dashboards/service-overview.json` ("Service Overview") — request
  rate, error rate, and duration percentiles per service, filterable by the `job` template
  variable. Filed under the **"Service Health"** folder (Dashboards nav), alongside the alert
  rules below. `infra/grafana/dashboards/red-metrics-classic.json` ("RED Metrics (classic
  histogram)") is a forked copy of the image's own bundled dashboard with the same per-job legend
  fixes applied (its original had the identical blended-line/raw-query-as-legend bug this repo
  fixed in Service Overview). "JVM Metrics" is still the image's unmodified original. The image's
  third bundled dashboard, "RED Metrics (native histogram)", is intentionally removed — our
  services only ever emit classic bucketed Prometheus histograms, not true native histograms, so
  it would always be empty (see `docs/internal/06-observability.md`'s Known gaps).
  `infra/grafana/dashboards/genai-overview.json` ("GenAI Overview") reads gen-ai's custom LLM
  metrics (request/error rate, latency, token usage per `endpoint`/`provider`) — see
  `docs/internal/06-observability.md` for the exact metric names.
  `infra/grafana/dashboards/webclient-overview.json` ("Web Client Overview") reads
  `traces_spanmetrics_*`, RED-style metrics that Tempo's metrics-generator derives automatically
  from web-client's OTel traces (web-client has no scrapable Prometheus endpoint of its own — see
  "Traces" below). All 4 dashboards are filed under **"Service Health"**.
- **Alerts**: `infra/grafana/provisioning/alerting/rules.yaml` — slow response time (p95 request
  duration > 1s for 5 minutes) and service down (`up == 0` for 2 minutes), both under the
  "Service Health" folder alongside the Service Overview dashboard. The rules themselves are under
  **Alerting → Alert rules → Service Health**. These two already satisfy the course's "at least
  one meaningful alert rule" requirement (the requirement text names these exact two examples).
  **Not yet wired up: actual notification delivery.** `infra/grafana/provisioning/alerting/`
  only provisions `rules.yaml` — no contact point, no notification policy, and no SMTP config
  exist anywhere in this repo. A firing alert changes state (visible under **Alerting → Alert
  rules** if someone opens Grafana and looks) but nothing pages a person via email/Slack/webhook.
  Grafana's built-in default contact point exists implicitly, but with no SMTP server configured
  it has nowhere to deliver to.

### Planned follow-up (not yet implemented)

- **A third, more targeted alert: MongoDB dependency health.** The two current alerts only see
  "is the service's own HTTP port reachable" (`up`) and "is it slow" — neither distinguishes a
  service being down because *it* crashed from a service being up but unable to reach MongoDB
  (exactly what happened on the live `kubernetes-test` deploy: MongoDB was OOMKilled mid
  first-boot, never finished creating its `root` user, and every dependent service crash-looped
  on auth failures — `service down` would eventually catch this once restarts exhaust the pod's
  readiness budget, but a dedicated alert would catch it faster and name the actual cause).
  Leading approach to implement later: Spring Boot auto-configures Micrometer's
  `MongoMetricsCommandListener`/`MongoMetricsConnectionPoolListener` when a MongoDB driver +
  Micrometer are both on the classpath (already true for user-service and content-service),
  which exposes `mongodb_driver_commands_seconds_count{status="FAILED", ...}` and
  connection-pool-size metrics per service — worth confirming these are actually emitted by our
  current setup before writing the rule, then alerting on a sustained rate of `FAILED` commands
  or an absent/zero connection pool.
- **Real notification delivery.** Add a contact point (Slack webhook or email via SMTP) and a
  notification policy routing the "Service Health" folder's alerts to it, so firing alerts
  actually reach a person instead of only being visible if someone opens Grafana and checks.

In Kubernetes/Helm, these same provisioning files are not read directly from `infra/grafana/` —
Helm's `.Files.Get` can't escape the chart root (`infra/helm/`), so `infra/helm/files/grafana/`
holds a manually-copied duplicate of `infra/grafana/`'s dashboards/provisioning/prometheus config.
There is no CI check or lint rule keeping the two in sync: if you change anything under
`infra/grafana/`, remember to re-copy it into `infra/helm/files/grafana/` as well, or the
Kubernetes deployment will silently drift from docker-compose.

## How metrics get here

Two independent paths feed the same Prometheus:

1. **OTLP push** (api-gateway, user-service, content-service, gen-ai) — drives the dashboard and
   the slow-response alert. Spring services use `management.otlp.metrics.export.url` /
   `management.opentelemetry.tracing.export.otlp.endpoint`; gen-ai uses the existing
   `OTEL_EXPORTER_OTLP_ENDPOINT`.
2. **Classic scrape** (`infra/grafana/prometheus.yaml`'s `scrape_configs`) — Spring services'
   `/actuator/prometheus` and gen-ai's `/metrics`. This exists purely to give Prometheus a real
   `up{job=...}` signal for the service-down alert; OTLP-pushed metrics never populate `up`
   because that's only generated for pull/scrape targets.

## Traces

All 4 app services (api-gateway, user-service, content-service, gen-ai, **and now web-client**)
push OTel traces via OTLP to `grafana-lgtm`. web-client (Next.js) uses `@vercel/otel` in
`web-client/instrumentation.ts` — a no-op unless `OTEL_EXPORTER_OTLP_ENDPOINT` is set, gated to
the Node runtime only (`NEXT_RUNTIME === "nodejs"`, since Next.js calls `register()` in both the
Node and Edge runtimes and the OTel SDK components here don't run in Edge). Unlike the other 3
services, web-client has no `/actuator/prometheus`-style scrape endpoint of its own — its RED
metrics (Web Client Overview dashboard) instead come from `traces_spanmetrics_*`, span metrics
that Tempo's bundled metrics-generator derives automatically from any traces it receives. This
also means web-client's own outbound `fetch` calls (e.g. to api-gateway) show up as
`SPAN_KIND_CLIENT` spans, giving a rough view of downstream call health without any extra
instrumentation.

## Logs

All 4 services forward application logs to Loki over the same OTLP endpoint used for metrics/
traces — this was verified end-to-end against a real running docker-compose stack, including a
dedicated Loki cross-check in `infra/scripts/smoke-test.sh` (35/35 checks passing).

Spring services need three pieces, not just an auto-configuration flag: the
`opentelemetry-logback-appender-1.0` dependency, a `logback-spring.xml`, and a small
`OtelLogbackAppenderInstaller` `@Component` that calls `OpenTelemetryAppender.install(...)` at
startup — Spring Boot's `OtlpLoggingAutoConfiguration` only *builds* the OTLP log exporter, it
doesn't bridge Logback into it. That installer must bind against the **Spring-managed
`OpenTelemetry` bean**, not `GlobalOpenTelemetry.get()`: the latter returns a no-op instance and
silently drops every log record with no error, which is exactly the bug this rollout found and
fixed during verification.

gen-ai attaches an OTel `LoggingHandler` to the root logger in `app/observability.py`, and also
attaches it explicitly to the `uvicorn`, `uvicorn.error`, and `uvicorn.access` loggers. This
extra step matters because uvicorn's default logging config disables propagation to root on
those loggers specifically — a handler attached only to root would silently miss virtually all
request traffic.

All 3 Spring services also run a `RequestLoggingFilter` that logs `"{method} {requestURI}"` at
INFO for every request. It was added purely so there's something for the Loki cross-check to
grep for — Spring Boot doesn't emit a per-request access-log line on its own. This filter is the
mechanism that makes the `smoke-test.sh` Loki check work at all.

`infra/scripts/smoke-test.sh` queries Loki (via Grafana's datasource-proxy API) after running its
checks, confirming its own requests actually got logged — skipped automatically when Grafana
isn't reachable (true for the VM and Kubernetes targets without a tunnel/port-forward, false for
local docker-compose).

## Security note: public actuator exposure

Each service's `SecurityConfig` permits unauthenticated access to `/actuator/prometheus` so the
internal Prometheus scrape can reach it. Early in this rollout that also meant it was reachable
unauthenticated from the public internet, because `/actuator/**` was publicly routed through
`infra/nginx/default.conf` (docker-compose / Azure VM) and through both `infra/k8s/ingress.yml`
and `infra/helm/templates/ingress.yaml` (Kubernetes). This was fixed by narrowing all three
public routes to `/actuator/health` only. Internal Prometheus scraping is unaffected by this
change — it hits each service directly over the internal Docker/Kubernetes network and never
goes through any of those proxies.

## Known limitation

Per-pod CPU/memory usage isn't available as a dashboard panel in Kubernetes. It would need
Prometheus to scrape each node's kubelet cAdvisor endpoint, which requires cluster-scoped RBAC
(`nodes/metrics`, `nodes/proxy`). This was investigated and found infeasible on this course
cluster: `kubectl auth can-i create clusterrole` returns `no`, confirmed against the real
cluster rather than assumed from documentation.

## Monitoring runs in its own namespace

`grafana-lgtm` is deployed into a dedicated namespace (`monitoring.namespace` in
`infra/helm/values.yaml`, currently `monitoring-rolling-restarts`), separate from the app
workloads' namespace. It has its own `ResourceQuota`, isolating it from app-namespace pressure and
making its own upgrades/troubleshooting independent of the app release. This namespace must exist
*before* `helm upgrade --install` runs — Helm's `--create-namespace` only auto-creates its own
install-target namespace, not other namespaces referenced by templates via an explicit
`metadata.namespace`. `.github/workflows/deploy_kubernetes.yml` creates it idempotently
(`kubectl create namespace ... --dry-run=client -o yaml | kubectl apply -f -`) before deploying;
for a manual `make helm-deploy`, create it once yourself (`kubectl create namespace
monitoring-rolling-restarts`).

A second CI/CD workflow, `.github/workflows/deploy_monitoring.yml`, redeploys *only* when
monitoring-related files change (`infra/grafana/**`, `infra/helm/files/grafana/**`, the monitoring
Helm templates, or the raw-manifest equivalents) — path-filtered so a dashboard/alert-rule tweak
doesn't need to wait on the full app CI/build pipeline (irrelevant here anyway, since `grafana-lgtm`
runs an upstream public image this repo never builds). It uses `helm upgrade --reuse-values`
rather than the full secrets/image-values setup `deploy_kubernetes.yml` needs, so it never touches
mongo/JWT/service-client secrets or image tags — only the monitoring-related values. It shares that
workflow's `deploy-k8s` concurrency group so the two never run `helm upgrade` against the same
release simultaneously. Requires the release to already exist (the very first install must go
through `deploy_kubernetes.yml`).

Cross-namespace RBAC: Prometheus (inside `grafana-lgtm`) needs to discover pods over in the app
namespace, but its `ServiceAccount` lives in the monitoring namespace. The `Role`/`RoleBinding`
granting `get/list/watch` on `pods` are created *in the app namespace* (a `RoleBinding` must live
in the same namespace as the pods it grants access to), with the `RoleBinding`'s `subject`
explicitly naming the `ServiceAccount`'s namespace — a standard, fully-supported RBAC feature that
avoids needing a `ClusterRole`/`ClusterRoleBinding` (this cluster's RBAC does not permit creating
those: confirmed via `kubectl auth can-i create clusterrole` returning `no`).

**`make helm-destroy` doesn't touch it**: rendering every app-workload template (`deployment.yaml`,
`databases.yaml`, `service.yaml`, `serviceaccount.yaml`, `secrets.yaml`, `pdb.yaml`, and
`ingress.yaml`) is gated behind `appWorkloads.enabled` (`infra/helm/values.yaml`, default `true`).
`make helm-destroy` runs `helm upgrade --set appWorkloads.enabled=false` — re-rendering the release
with those resources removed (including `mongodb`'s data, same as a full uninstall always has) —
rather than `helm uninstall`, so `templates/monitoring.yaml`/`monitoring-rbac.yaml` (which carry no
such guard) keep rendering unconditionally and `grafana-lgtm` is left running. Use `make
helm-destroy-all` for the rare case you actually want to remove monitoring too (a real `helm
uninstall`, tearing down the whole release).

**Persistence**: `grafana-lgtm` has a `PersistentVolumeClaim` mounted at `/data` — verified by
inspecting the image directly (`docker run --entrypoint sh grafana/otel-lgtm@sha256:... -c
'grep storage.tsdb.path run-prometheus.sh'` etc.) rather than assumed: Prometheus
(`--storage.tsdb.path=/data/prometheus`), Grafana (`GF_PATHS_DATA=/data/grafana/data`), Loki
(`/data/loki`), Tempo (`/data/tempo/{wal,blocks}`), and Pyroscope (`/data/pyroscope`) all persist
under this single path. Before this, the stack had **no persistence at all** — every pod restart
silently lost all dashboards, metrics history, logs, traces, and profiles.

The docker-compose/Azure VM target had the identical gap — `grafana-lgtm`'s only volumes were the
read-only config bind mounts (`./grafana/...`), no `/data` mount at all, so every `make deploy-azure`
(`docker compose up -d --force-recreate`) silently reset Prometheus/Loki/Tempo/Pyroscope/Grafana's
own state, same as an unpersisted Kubernetes pod restart. Fixed the same way: a named
`grafana-lgtm-data` volume mounted at `/data` in `infra/docker-compose.yaml`. `--force-recreate`
only recreates containers, not volumes, so this data now survives every redeploy — while the
config bind mounts (dashboards/provisioning/prometheus.yaml) still get freshly re-synced from
`infra/grafana/` on every deploy via the Ansible `app` role, so dashboard/provisioning changes
still take effect without needing to wipe anything.

**Label/annotation-based auto-discovery**: `infra/helm/files/grafana/prometheus.yaml`'s
`scrape_configs` is a single generic job (previously one hardcoded `job_name` block per service).
Any pod in the app namespace carrying the `monitoring: "true"` label is scraped automatically,
with its port/path read from `prometheus.io/port`/`prometheus.io/path` annotations
(`infra/helm/templates/deployment.yaml` sets both on any workload with a `metricsPath` value in
`values.yaml`). `job` is relabeled from the pod's `app` label so existing dashboards/alerts
filtering `job=~"api-gateway|..."` keep working unchanged. Onboarding a new service means adding
the label + annotations to its pod template — no `prometheus.yaml` edit needed.

**Version visibility**: every service exposes a static `app_build_info` gauge (always `1`, with
`service`/`version` labels) — `BuildInfoMetrics.java` for the 3 Spring services (backed by
Spring Boot's `buildInfo()` Gradle task + auto-configured `BuildProperties` bean) and a
`prometheus_client.Gauge` set at startup in `services/gen-ai/app/main.py`. Lets a dashboard panel
or an ad hoc query correlate a metric/behavior change with the release that caused it.

**What we deliberately did *not* adopt from the general "Kubernetes monitoring best practices"
list**, and why:

- **ServiceMonitor/PodMonitor CRDs** — these are Prometheus Operator resources. This stack runs
  the bundled `grafana/otel-lgtm` image (its own embedded Prometheus, configured via a static
  `prometheus.yaml` file), not the Prometheus Operator. Adopting CRD-based discovery would mean
  replacing the entire monitoring architecture with a full `kube-prometheus-stack`-style
  installation — a much larger undertaking than this project's scope, and not something to do
  silently as a side effect of a namespace split.
- **SSO** — Grafana is now routed through the shared ingress at `/monitoring` (an ExternalName
  Service alias, since an Ingress can only target Services in its own namespace and `grafana-lgtm`
  lives in a separate one), with `GF_AUTH_ANONYMOUS_ENABLED=false` and a real admin password
  (`GF_SECURITY_ADMIN_PASSWORD` from a Secret) explicitly set on every deployment target — the
  previous "no ingress, anonymous-Admin is fine since only `kubectl port-forward` reaches it"
  tradeoff no longer holds now that it's reachable over the public ingress, so this was revisited
  rather than left as an oversight. Full SSO (OAuth/SAML) is still out of scope — a single shared
  admin login is enough for this project's team size.
- **A `PrometheusRule`-based "pod restart count > 5" alert** — the underlying metric
  (`kube_pod_container_status_restarts_total`) comes from `kube-state-metrics`, a separate
  exporter not currently deployed. Adding it is plausible (it can run with namespace-scoped RBAC
  rather than the `ClusterRole` this cluster's RBAC won't grant), but it's a new workload with its
  own resource cost, and out of scope for this pass — worth a dedicated follow-up rather than
  folding in silently. `PrometheusRule` CRDs have the same Operator-dependency issue as
  ServiceMonitor/PodMonitor above; the existing `rules.yaml`-based alert provisioning (see
  "Alerting" below) is the equivalent for this stack's architecture.

## Kubernetes resource budget

The cluster's separate `kubernetes-test` namespace was retired earlier — manual/dev deploys and
the CD-managed release share one app namespace. That namespace's `ResourceQuota` was later split
further to carve out the dedicated monitoring namespace above: what was a single **4 CPU /
6144Mi** quota is now **3500m CPU / 5244Mi memory** for the app namespace plus a separate **500m
CPU / 900Mi memory** for the monitoring namespace (`3500+500=4000m`, `5244+900=6144Mi` — the same
total pool, not additional capacity; confirmed live via `kubectl describe resourcequota` in both
namespaces). Both quotas enforce only `limits.cpu`/`limits.memory`, not `requests` — so requests
are free to size for realistic steady-state usage without touching either quota.

App-namespace resource limits are sized so that **api-gateway, user-service, content-service,
gen-ai, and web-client can each run 3 replicas simultaneously** (headroom for a future HPA),
alongside `mongodb` (`replicas: 1`, not horizontally replicated):

| Service | Replicas | Request (CPU/Mem) | Limit (CPU/Mem) | ×N limit CPU | ×N limit Mem |
| --- | --- | --- | --- | --- | --- |
| web-client | 3 | 20m / 90Mi | 100m / 200Mi | 300m | 600Mi |
| api-gateway | 3 | 60m / 180Mi | 220m / 330Mi | 660m | 990Mi |
| user-service | 3 | 60m / 210Mi | 220m / 460Mi | 660m | 1380Mi |
| content-service | 3 | 60m / 210Mi | 220m / 400Mi | 660m | 1200Mi |
| gen-ai | 3 | 30m / 80Mi | 100m / 130Mi | 300m | 390Mi |
| mongodb | 1 | 100m / 256Mi | 250m / 512Mi | 250m | 512Mi |
| **App total** | | | | **2830m / 3500m (81%)** | **5072Mi / 5244Mi (96.7%)** |
| grafana-lgtm | 1 | 100m / 480Mi | 400m / 850Mi | 400m | 850Mi |
| **Monitoring total** | | | | **400m / 500m (80%)** | **850Mi / 900Mi (94.4%)** |

Headroom: app namespace **670m CPU (19%)**, **172Mi memory (3.3%)**; monitoring namespace
**100m CPU (20%)**, **50Mi memory (5.6%)**. This table has gone through several live-data-driven
revisions already (see `docs/internal/06-observability.md` for the full incident history) — treat
the numbers as a snapshot, not a one-time decision:

- **mongodb is at 512Mi** (not the 260Mi it was briefly cut to) — that cut left almost no headroom
  above the 256Mi request and caused a live OOMKill during first-boot init (WiredTiger cache
  warmup + root user creation), which interrupted init before the root user existed and left every
  dependent service permanently failing auth even after Mongo itself recovered. Live idle usage
  measured at a stable 30% of this limit (~155Mi) — the 512Mi exists for headroom during that
  one-time init spike, not steady-state.
- **api-gateway is at 330Mi** — an intermediate 300Mi trim was measured live at 85-90% utilization
  at idle (too tight) and reverted.
- **user-service is at 460Mi, higher than content-service's 400Mi** despite near-identical code
  shape — live idle measurements across three separate snapshots showed user-service climbing
  steadily (70-72% -> 78-81% -> 82-86% of 400Mi) while content-service stayed flat around 75-77%
  on the same limit. The cause of the difference hasn't been root-caused yet (worth investigating:
  auth/JWT-path caching, connection pool behavior); 460Mi restores headroom in the meantime.
- **gen-ai is at 130Mi** — it never runs a model locally (both `ChatOllama` and `ChatOpenAI` in
  `app/llm/provider.py` are thin HTTP clients; the only place Ollama itself runs is a separate
  container behind docker-compose's `local-llm` profile, which doesn't exist in Kubernetes at
  all), and live idle usage measured a stable 34-39% across multiple readings — this consistent
  margin funds user-service's increase above.
- **grafana-lgtm is at 850Mi/400m**, in its own namespace with its own 900Mi/500m quota (see
  above) — it OOMKilled live (exit 137) after ~28 minutes while a dashboard was being actively
  browsed, back when it ran at 640Mi/350m co-located in the app namespace. Idle usage alone was
  already 467Mi (73% of that prior limit); viewing a dashboard adds real load on top, since every
  panel fires a PromQL range query that Prometheus (bundled in the same container as Grafana/
  Tempo/Loki/Pyroscope) has to evaluate and decompress from its TSDB. A later idle reading at a
  900Mi limit showed 58% (530Mi) and an active-browsing reading showed 80% (725Mi) — 850Mi keeps
  that same real margin while leaving some of the dedicated namespace's own quota spare.
- **web-client is at 200Mi** — an earlier 230Mi bump (after 2/3 pods measured at ~80% of a 180Mi
  limit) turned out more generous than needed once measured live (41-63% of 230Mi), so it was
  trimmed back to help fund user-service's increase.
- Requests stay below limits (roughly 35–65%) since they aren't quota-constrained, but are sized
  to be meaningful bases for CPU-utilization-based autoscaling later (a razor-thin request makes
  an HPA's target-utilization percentage nearly meaningless).

Treat the 2.8% memory margin as something to check continuously, not fire-and-forget: run
`kubectl top pod -n <namespace>` regularly and adjust the table above if usage is running close to
the edge, especially before actually turning on 3 replicas or an HPA. user-service's still-rising
trend is the one line item most likely to need another look.
