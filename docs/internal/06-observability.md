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
  Kubernetes/Helm.
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
- The `otel-lgtm` image's own bundled **"RED Metrics (native histogram)" dashboard is expected to
  stay empty.** Verified directly against the live Prometheus: our services expose 2,622 classic
  bucketed histogram series (`_bucket`/`_count`/`_sum` — from Micrometer's Prometheus registry for
  the 3 Spring services and `prometheus-fastapi-instrumentator` for gen-ai) and **zero** true
  Prometheus native-histogram series (`histogram_quantile`-free single-series-per-metric exposition
  format). This dashboard is one of the image's own defaults, not something built by this branch —
  our "Service Overview" dashboard already covers RED metrics using the classic histograms we
  actually emit. Populating the native-histogram dashboard would require reconfiguring Micrometer's
  Prometheus registry (and gen-ai's instrumentator) to emit that exposition format, a separate,
  non-trivial change across all 4 services — decided not worth pursuing for now.
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
  Fits at the default **1 replica**: new total `limits.memory` across the namespace is 2340Mi of
  the 3000Mi quota (660Mi headroom), up from ~1920Mi before. Per the AET fair-use policy the
  *reserved* quantity is `requests` (team cap 4 vCPU / 6 GB across all namespaces), not `limits`,
  so this was "free" against the team budget — only `limits` changed, `requests` stayed at 200Mi.
  **Still open:** it does **not** fit at 2 replicas *with* the 640Mi monitoring bundle co-located
  (2 × 3 × 400Mi = 2400Mi Spring + 640Mi + the rest exceeds 3000Mi) — co-locating monitoring
  structurally requires 1 replica, so the replica-count-vs-monitoring tradeoff for
  `values-prod.yaml` (still 2 replicas, monitoring not deployed there) remains a team decision.
  Slimming the JVM footprint (SerialGC / lower `MaxRAMPercentage` / native image) also remains
  unexplored, should more headroom ever be needed.
- The Kubernetes resource budget for `grafana-lgtm` is tight (see `infra/helm/values.yaml`'s
  `monitoring.resources` and the trimmed limits on every other service) — every other service's
  Helm `values.yaml` and raw `infra/k8s/deployments/*.yml` limits were trimmed to make room for
  it inside the namespace's hard quota (2 CPU / 3000Mi `limits`, verified empirically against the
  live cluster: quota reported `limits.cpu 2`, `limits.memory 3000Mi`). At 1 replica the fit is
  comfortable; see the OOM item above for the 2-replica case.
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
