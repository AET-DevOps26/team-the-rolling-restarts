# Observability

OpenTelemetry + Grafana's all-in-one `otel-lgtm` image (real Prometheus + Grafana + Tempo +
Loki + Pyroscope, one container) — not a standalone Prometheus deployment, but the image
genuinely bundles Prometheus (verified by pulling `grafana/otel-lgtm` and inspecting
`/otel-lgtm/prometheus/prometheus`), satisfying "Prometheus must be used"
(`docs/requirements/07-observability.md`) literally, not just in spirit.

## What's wired up

- All 3 Spring services push metrics + traces via OTLP to `grafana-lgtm:4318`
  (`management.otlp.metrics.export.url`, `management.opentelemetry.tracing.export.otlp.endpoint`)
  — the bundled OTel Collector forwards these straight into the bundled Prometheus.
- All 3 Spring services also expose `/actuator/prometheus` (classic scrape), and gen-ai exposes
  `/metrics` — scraped directly by the bundled Prometheus, giving both a real `up{job=...}` signal
  (OTLP-pushed metrics don't populate it) and the actual data behind the RED dashboard/p95 alert
  (see below on why the dashboard reads the *scrape*, not the OTLP push).
- **The classic-scrape config now diverges by environment, deliberately.** In docker-compose
  (`infra/grafana/prometheus.yaml`) it's `static_configs` pointing at each Docker service's DNS
  name — fine, since docker-compose only ever runs 1 replica per service. In Kubernetes
  (`infra/helm/files/grafana/prometheus.yaml` and the embedded copy in
  `infra/k8s/configmaps/grafana-lgtm-config.yml`) the same static-DNS approach was found — during
  the final whole-branch review — to break under the real 2-replica production config: scraping a
  Service's ClusterIP gets load-balanced by kube-proxy to a random backing pod each time, so one
  Prometheus time series silently interleaves two different pods' independent counters, corrupting
  every `rate()` (Request Rate, duration percentiles, the p95 alert). Fixed by switching the two
  Kubernetes copies to per-pod discovery: `kubernetes_sd_configs` (`role: pod`,
  `namespaces.own_namespace: true`) relabeled on each Deployment's `app` pod label, giving every
  replica its own `instance` label. This needs a namespaced `Role`/`RoleBinding` granting
  `get/list/watch` on `pods` (verified feasible against the real cluster:
  `kubectl auth can-i list pods --namespace=kubernetes-test` → yes) — a much smaller,
  namespace-scoped ask than the cluster-wide RBAC the cAdvisor idea needed (see Known gaps below),
  so it isn't subject to the same infeasibility.
- **Verified empirically: the OTLP metrics push does not double-count against the classic scrape.**
  The final review flagged this as a risk since both paths land in the same Prometheus. Checked
  directly against a live docker-compose stack (`http://localhost:9090/api/v1/query` inside the
  `grafana-lgtm` container): OTLP-pushed Spring metrics land as `http_server_requests_milliseconds_*`
  with **no `instance` label at all**, and gen-ai's OTLP push lands as
  `http_server_duration_milliseconds_*`. The dashboard/alert queries filter on
  `__name__=~"http_server_requests_seconds_...|http_request_duration_seconds_..."` (note: `seconds`,
  not `milliseconds`) *and* `instance=~"$instance"` — both the metric name and the instance-label
  requirement exclude the OTLP-push series, so there's no overlap in practice. Not a coincidence to
  rely on blindly going forward, but confirmed correct as implemented.
- `services/gen-ai` exports OpenTelemetry traces and custom LLM metrics via OTLP (unchanged from
  before this work).
- Dashboard: `infra/grafana/dashboards/service-overview.json` (adapted from the image's own
  bundled RED-metrics dashboard), provisioned automatically in both docker-compose and
  Kubernetes/Helm. Request Rate, Error Rate, and Duration percentiles all group by `job` with an
  explicit `{{job}}` legend (fixed from a bare `sum()`/`__auto` that collapsed every service into
  one blended, unlabeled line whenever the `$job` variable was at its default "All"). The Duration
  Heatmap's Y axis uses a log2 scale, not linear — Micrometer/Prometheus's default histogram
  buckets span ~1ms to ~10s, so a linear axis compressed nearly every sub-100ms bucket into an
  unreadable sliver at the bottom.
- Alerts: `infra/grafana/provisioning/alerting/rules.yaml` — slow response time (p95 > 1s) and
  service down (`up == 0`).
- Log export is wired up and was verified end-to-end (not just configured): all 4 services
  forward application logs to Loki via OTLP. The 3 Spring services needed three new pieces —
  the `opentelemetry-logback-appender-1.0` dependency, a `logback-spring.xml`, and a small
  `OtelLogbackAppenderInstaller` `@Component` that calls `OpenTelemetryAppender.install(...)`
  against the **Spring-managed `OpenTelemetry` bean**. This last point was a real bug found
  during verification: installing against `GlobalOpenTelemetry.get()` instead returns a no-op
  instance and silently drops every log record (Spring Boot's `OtlpLoggingAutoConfiguration`
  builds the exporter but never registers it as the global instance). gen-ai attaches its OTel
  `LoggingHandler` (`services/gen-ai/app/observability.py`) to the root logger **and** explicitly
  to `uvicorn` / `uvicorn.error` / `uvicorn.access`, because uvicorn's default logging config
  disables propagation to root on those loggers — a root-only handler would miss virtually all
  request traffic.
- `RequestLoggingFilter` added to all 3 Spring services (`config/RequestLoggingFilter.java`),
  logging `"{method} {requestURI}"` at INFO for every request. Spring Boot emits no per-request
  access-log line by default, so this exists purely to give `infra/scripts/smoke-test.sh`'s Loki
  cross-check something to grep for.
- `infra/scripts/smoke-test.sh` cross-checks its own requests against Loki after running,
  skipping gracefully wherever Grafana isn't reachable (VM/k8s without a tunnel/port-forward).
  Verified against a real running docker-compose stack: 35/35 checks passing, including the
  Loki cross-check.
- Deployed to Kubernetes/Helm as well as docker-compose (`infra/helm/templates/monitoring.yaml`,
  `infra/k8s/deployments/grafana-lgtm-deployment.yml`).
- Security fix applied: `/actuator/prometheus` is permitted (unauthenticated) in each service's
  SecurityConfig so Prometheus can scrape it internally, but that initially also made it
  reachable unauthenticated from the public internet, because `/actuator/**` was publicly routed
  through `infra/nginx/default.conf` (docker-compose/Azure VM) and both `infra/k8s/ingress.yml`
  and `infra/helm/templates/ingress.yaml` (Kubernetes). Fixed by narrowing all 3 public routes to
  `/actuator/health` only — internal Prometheus scraping is unaffected since it hits each service
  directly over the internal network, never through those proxies.

## Known gaps

- No per-pod resource-usage (CPU/memory) panel in Kubernetes — the cAdvisor/kubelet-scrape
  approach needs cluster-scoped RBAC (`nodes/metrics`, `nodes/proxy`) that this course cluster
  doesn't grant to namespaced tenants (verified: `kubectl auth can-i create clusterrole` → `no`,
  checked against the real cluster, not assumed).
- **REMOVED — the `otel-lgtm` image's own bundled "RED Metrics (native histogram)" dashboard.**
  It would always stay empty: verified directly against the live Prometheus that our services
  expose 2,622 classic bucketed histogram series (`_bucket`/`_count`/`_sum` — from Micrometer's
  Prometheus registry for the 3 Spring services and `prometheus-fastapi-instrumentator` for
  gen-ai) and **zero** true Prometheus native-histogram series. Populating it would require
  reconfiguring Micrometer's Prometheus registry (and gen-ai's instrumentator) to emit that
  exposition format — a separate, non-trivial change across all 4 services, not worth it since
  our own "Service Overview" dashboard already covers RED metrics with the classic histograms we
  actually emit. Removed by overriding the image's own
  `/otel-lgtm/grafana/conf/provisioning/dashboards/grafana-dashboards.yaml` (same override pattern
  already used for `prometheus.yaml`) with a trimmed copy that keeps its other two providers — "RED
  Metrics (classic histogram)" and "JVM Metrics" — and drops only the native-histogram one. See
  `infra/grafana/provisioning/dashboards/default-dashboards-override.yaml` (docker-compose),
  `infra/helm/files/grafana/provisioning/dashboards/default-dashboards-override.yaml` +
  `infra/helm/templates/monitoring.yaml` (Helm), and the `default-dashboards-override.yaml` key in
  `infra/k8s/configmaps/grafana-lgtm-config.yml` + the matching mount in
  `infra/k8s/deployments/grafana-lgtm-deployment.yml` (raw k8s). Verified live against
  docker-compose: dashboard list dropped from 4 to 3, all other panels unaffected, `make
  smoke-test` still 35/35.
- **FORKED — the `otel-lgtm` image's bundled "RED Metrics (classic histogram)" dashboard.** Its
  Request Rate and Error Rate panels had the exact same bug our own Service Overview dashboard
  had: `sum(...)` with no `by (job)` grouping and `legendFormat: __auto`, so with the `$job`
  variable defaulted to "All" every service collapsed into one blended line whose legend showed
  the raw PromQL query text instead of a service name (reported directly by a user browsing this
  dashboard). Since it's baked into the image, not a file we control, fixing it required forking a
  copy into this repo (same override-the-image-path pattern used to remove the native-histogram
  dashboard above): `infra/grafana/dashboards/red-metrics-classic.json` is a copy of the image's
  original with `by (job)` grouping + `{{job}}` legends added to Request Rate/Error Rate, `by (le,
  job)` + `{{job}} 95th`/`{{job}} 50th` added to Duration percentiles, and the Duration Heatmap's
  Y-axis switched from linear to log2 (same fixes as Service Overview). The
  `default-dashboards-override.yaml` provider for this dashboard now points at our own mounted
  copy (`/otel-lgtm/grafana-dashboards-custom/red-metrics-classic.json`) instead of the image's
  internal path; "JVM Metrics" is untouched and still points at the image's own file. Also applied
  to Service Overview's own Duration Heatmap: added a `{{le}}` legend (was unset, rendering as the
  raw label set e.g. `{le="+Inf"}` in the tooltip). Verified live: dashboard list still 3 entries,
  fixed queries confirmed via the Grafana API, `make smoke-test` still 35/35 (warm).
- **Under investigation, not yet resolved — a random-UUID legend reported in "JVM Metrics."** A
  user reported seeing a UUID (e.g. `28ee8462-0835-48c9-9d4f-cf016016d91b`) as a graph legend in
  this dashboard. Ruled out so far: our services' classic-scrape `instance` label (readable, e.g.
  `user-service:8081`); our services' OTLP-push metrics, which carry **no** `instance` or
  `service_instance_id` label at all (confirmed directly against Prometheus — just `job` +
  `service_name`); and the bundled OTel Collector's own self-monitoring job (`otelcol-contrib`,
  whose `instance` genuinely is a random UUID) — its metric names don't overlap with any panel in
  this dashboard, and the dashboard's own `$job`/`$instance` variables are already scoped to
  JVM-only metric names, not the unrestricted `.+` that "All" implies elsewhere. The UUID may have
  come from an earlier container incarnation during this session (identifiers like this can be
  regenerated per-process). Needs a fresh repro with the exact panel name + legend text before a
  fix can be targeted — do not guess further without that.
- **FIXED — the Spring services' memory limit was raised from 260Mi to 400Mi after it caused a
  real, reproducible OOMKill.** Originally measured idle-only on the pre-instrumentation image
  `2dedb24` (api-gateway 276–290Mi, user-service 268–285Mi, content-service 254–269Mi) and left as
  an open/deferred decision. That deferral held until a live `make smoke-test-k8s` run against the
  fully-instrumented images actually OOMKilled `user-service` (`kubectl describe pod` showed
  `lastState.terminated.reason: OOMKilled`, exit code 137) mid-test, right after it served the
  suite's `POST /auth/register` and `POST /auth/login` calls — pushing it just over the 260Mi
  limit. The ~70s restart (RSA keypair reload + Spring Boot boot) window then made every
  subsequent smoke-test call that needed a JWT fail with connection-refused/timeout, so the
  script's `[ -n "$token" ]` guards cascaded into `skip "... (no token)"` across RSS source
  lifecycle, protected endpoints, service-scope enforcement, and the shared-source lifecycle
  sections — most of the suite reading as "skipped" from one dead pod, not independent failures.
  Fixed by raising the *limit* (not the request) to **400Mi** for api-gateway, user-service, and
  content-service in both `infra/helm/values.yaml` and the raw `infra/k8s/deployments/*.yml`
  manifests. Re-verified live: `helm upgrade` applied cleanly, all three pods rolled out with no
  further OOMKill, and a fresh `make smoke-test-k8s` passed 34/34 (the one skip is the expected
  Loki check — Grafana isn't reachable without a port-forward on this target). Post-fix idle
  usage: api-gateway/user-service ~258Mi, content-service ~265Mi, all comfortably under 400Mi.
- **RESOLVED — the `kubernetes-test` namespace was retired; manual/dev deploys and the CD-managed
  release now share a single namespace**, which merged what used to be two separate 2 CPU /
  3000Mi `ResourceQuota`s into one **4 CPU / 6144Mi** quota (confirmed live via `kubectl describe
  resourcequota`: it enforces only `limits.cpu`/`limits.memory`, not `requests`). This resolves
  the earlier "doesn't fit at 2 replicas with monitoring co-located" gap noted below — the full
  distribution across all services, sized so **api-gateway/user-service/content-service/gen-ai/
  web-client can each run 3 replicas simultaneously** (headroom for a future HPA) alongside the
  `mongodb`/`grafana-lgtm` singletons, is documented in `docs/source/monitoring.md`'s "Kubernetes
  resource budget" section. As part of this redistribution, **mongodb's limit was restored from
  260Mi to 512Mi** — the 260Mi limit had caused a live OOMKill during first-boot init (WiredTiger
  cache warmup + root user creation), interrupting init before the root user existed and leaving
  every dependent service permanently failing auth (`UserNotFound`) even after Mongo itself
  recovered; **gen-ai's limit was raised from 130Mi to 180Mi** (FastAPI, LangChain, the OTel SDK,
  and the `prometheus-fastapi-instrumentator` scrape endpoint together need a bit more than the
  prior cut left it — but not much more: gen-ai never runs a model locally in any deployment
  target. Both `ChatOllama` and `ChatOpenAI` in `app/llm/provider.py` are thin HTTP clients;
  Ollama itself only ever runs as a separate container gated behind docker-compose's `local-llm`
  profile and doesn't exist in Kubernetes at all, so live idle usage measured at only 24-27% of an
  already-generous 260Mi test limit); and **api-gateway/user-service/content-service's CPU limit
  was raised back from 170m to 220m** to reduce throttling risk under the new OTel
  instrumentation's per-request work. Slimming the JVM footprint (SerialGC / lower
  `MaxRAMPercentage` / native image) remains unexplored, should more headroom ever be needed as
  replica counts or an HPA are turned on.
- **FIXED — `grafana-lgtm` OOMKilled live (exit 137) after ~28 minutes, while its dashboards were
  being actively browsed via `kubectl port-forward` following a `make helm-deploy`.** Idle usage
  alone was already 467Mi (73% of its 640Mi limit) with nobody viewing Grafana at all; opening a
  dashboard adds real load on top of that baseline, since every panel fires a PromQL range query
  that Prometheus — bundled in the same container as Grafana/Tempo/Loki/Pyroscope — has to
  evaluate and decompress from its TSDB, and only 173Mi (27%) of headroom wasn't enough to absorb
  that. Fixed by raising the limit to **900Mi** (and the CPU limit from 350m to 450m, since PromQL
  evaluation is CPU-bound too) in both `infra/helm/values.yaml` and
  `infra/k8s/deployments/grafana-lgtm-deployment.yml`, funded by the gen-ai/api-gateway trims
  above as part of the same namespace-consolidation redistribution. A later idle reading at 900Mi
  showed 58% (530Mi) and a later active-browsing reading showed 80% (725Mi) — consistent with
  900Mi covering both idle and active use with real margin.
- **ONGOING — a follow-up round of live `kubectl top pod` measurements after the above fixes
  landed found two more adjustments needed**, applied to `infra/helm/values.yaml` and every raw
  `infra/k8s/deployments/*.yml` manifest:
  - **api-gateway's 300Mi trim (from the redistribution above) was too aggressive** — measured
    live at 85-90% of 300Mi at idle. Reverted to **330Mi**.
  - **user-service needed to grow from 400Mi to 460Mi.** Three separate idle `kubectl top pod`
    readings, taken minutes apart, showed it climbing steadily — 70-72% -> 78-81% -> 82-86% of
    400Mi — while content-service, on the identical 400Mi limit with a similar codebase shape,
    stayed flat around 75-77% the whole time. The cause of user-service's specific growth pattern
    hasn't been root-caused yet (worth investigating: JWT/auth-path caching, connection pool
    behavior) — 460Mi restores headroom as a mitigation, not a fix, and this is the one line item
    in the resource budget most likely to need another look.
  - Funded by trimming **gen-ai to 130Mi** (measured live at a stable 34-39% of 180Mi across
    multiple readings — comfortable margin to give up) and **web-client back down to 200Mi** (the
    230Mi bump from the fix above turned out more generous than needed once measured live at
    41-63%). Net result: total memory usage actually rose slightly (5942Mi -> 5972Mi of 6144Mi,
    margin down from 3.3% to 2.8%), funding the user-service increase mostly out of gen-ai/
    web-client's spare headroom rather than growing the whole namespace's footprint.
- **DONE — `grafana-lgtm` moved into its own dedicated namespace** (`rolling-restarts-monitoring`),
  separate from the app workloads, adopting several general Kubernetes-monitoring practices that
  weren't in place before:
  - **Namespace isolation**: the app namespace's `ResourceQuota` was correspondingly split —
    confirmed live via `kubectl describe resourcequota` in both namespaces — from a single
    4000m/6144Mi to **3500m/5244Mi (app) + 500m/900Mi (monitoring)**, the same total pool, not
    additional capacity. grafana-lgtm's limit was resized from 900Mi/450m to **850Mi/400m** to fit
    under its namespace's own quota with margin, rather than consuming the entire thing.
  - **Cross-namespace RBAC**: the `Role`/`RoleBinding` granting Prometheus `get/list/watch` on
    `pods` had to move to the app namespace (where the pods actually are), with the
    `RoleBinding`'s `subject` explicitly naming the `ServiceAccount`'s namespace
    (`rolling-restarts-monitoring`) — verified this doesn't require a `ClusterRole`/
    `ClusterRoleBinding` (still blocked on this cluster: `kubectl auth can-i create clusterrole`
    still returns `no`), since a namespaced `RoleBinding` can reference a subject from a different
    namespace natively.
  - **Persistence added for the first time**: a `PersistentVolumeClaim` mounted at `/data`.
    Previously `grafana-lgtm` had *no* persistence at all — every pod restart silently lost all
    dashboards, metrics history, logs, traces, and profiles. The mount path was verified by
    inspecting the image directly (`docker run --entrypoint sh grafana/otel-lgtm@sha256:... -c
    'grep storage.tsdb.path run-prometheus.sh'`, etc.), not assumed: Prometheus, Grafana, Loki,
    Tempo, and Pyroscope all persist under this single `/data` root.
  - **Label/annotation-based auto-discovery**: replaced 4 hardcoded per-service `job_name` blocks
    in `prometheus.yaml` with a single generic job keyed on a `monitoring: "true"` pod label plus
    `prometheus.io/port`/`prometheus.io/path` annotations (set by
    `infra/helm/templates/deployment.yaml` on any workload with a `metricsPath` value). Onboarding
    a future service needs only those label/annotations, not a `prometheus.yaml` edit.
  - **Version-info metric added**: a static `app_build_info` gauge (value 1, `service`/`version`
    labels) on every service — `BuildInfoMetrics.java` (new, identical across all 3 Spring
    services, backed by a new `buildInfo()` Gradle task each) and a `prometheus_client.Gauge` in
    gen-ai's `main.py`.
  - **Explicitly not adopted** (see `docs/source/monitoring.md`'s rationale): ServiceMonitor/
    PodMonitor CRDs and `PrometheusRule` (both require the Prometheus Operator, which this stack's
    bundled-image architecture doesn't use — adopting them means replacing the whole monitoring
    stack, out of scope here); Grafana/Prometheus auth or TLS (already addressed at the network
    layer — no ingress route for grafana-lgtm, reachable only via `kubectl port-forward`); a
    `kube_pod_container_status_restarts_total`-based alert (needs deploying `kube-state-metrics`,
    a new workload with its own cost — flagged as a follow-up, not folded in silently).
  - **New CI/CD workflow**: `.github/workflows/deploy_monitoring.yml`, path-filtered to
    monitoring-related files only, using `helm upgrade --reuse-values` so it never needs the app
    secrets or image-values `deploy_kubernetes.yml` requires. Shares that workflow's `deploy-k8s`
    concurrency group to prevent two `helm upgrade` calls racing on the same release.
- `infra/helm/files/grafana/*` is a manually-copied duplicate of most of `infra/grafana/*` —
  Helm's `.Files.Get` can't escape the chart root (`infra/helm/`), so the config files had to be
  copied in rather than referenced directly. Nothing in CI enforces the two stay in sync; if
  `infra/grafana/{dashboards,provisioning}/*` changes, remember to re-copy into
  `infra/helm/files/grafana/`. **Exception: `prometheus.yaml` is not a byte-for-byte copy** — see
  above; the Kubernetes copies (`infra/helm/files/grafana/prometheus.yaml` and the embedded block
  in `infra/k8s/configmaps/grafana-lgtm-config.yml`) intentionally diverge from
  `infra/grafana/prometheus.yaml` (docker-compose) in their `scrape_configs`, but must stay
  identical to *each other*.
- `infra/k8s/configmaps/grafana-lgtm-config.yml` is likewise hand-maintained, not generated —
  its header comment says exactly which files it must track.
- `grafana-lgtm` runs under a dedicated `grafana-lgtm` ServiceAccount with a namespaced
  `Role`/`RoleBinding` (`get/list/watch` on `pods` only) so Prometheus can do per-pod service
  discovery — `infra/k8s/rbac/grafana-lgtm-rbac.yml` (raw manifests) and
  `infra/helm/templates/monitoring-rbac.yaml` (Helm), following the same pattern.
- The "Service Overview" dashboard's provisioning provider sets `folder: "Service Health"`
  (`infra/grafana/provisioning/dashboards/custom-dashboards.yaml`) so it's filed alongside the 2
  alert rules there instead of Grafana's root folder — otherwise the "Service Health" folder holds
  only alert rules and looks empty when browsing **Dashboards** (alerts and dashboards share one
  folder namespace, but the Dashboards browser only lists dashboards).

## Re-verify

```sh
grep -n "MANAGEMENT_OTLP_METRICS_EXPORT_URL\|MANAGEMENT_OPENTELEMETRY_TRACING_EXPORT_OTLP_ENDPOINT" infra/docker-compose.yaml
grep -n "micrometer-registry-prometheus" services/spring/*/build.gradle
grep -n "prometheus-fastapi-instrumentator" services/gen-ai/pyproject.toml
cat infra/grafana/provisioning/alerting/rules.yaml
grep -rln "OtelLogbackAppenderInstaller\|RequestLoggingFilter" services/spring/*/src/main/java
grep -n "actuator" infra/nginx/default.conf infra/k8s/ingress.yml infra/helm/templates/ingress.yaml
diff -rq infra/grafana/dashboards infra/helm/files/grafana/dashboards
diff -rq infra/grafana/provisioning infra/helm/files/grafana/provisioning
diff <(sed -n '/scrape_configs:/,$p' infra/helm/files/grafana/prometheus.yaml) \
     <(sed -n '/scrape_configs:/,/^---/p' infra/k8s/configmaps/grafana-lgtm-config.yml | sed 's/^    //' | sed '/^---$/d')
kubectl auth can-i list pods --namespace=<namespace>
kubectl get deployment grafana-lgtm -n <namespace>
kubectl top pod -n <namespace>
```
