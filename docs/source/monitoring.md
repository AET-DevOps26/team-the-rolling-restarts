# Monitoring

Metrics, dashboards, and alerts run on `grafana/otel-lgtm` — a single image bundling real
Prometheus, Grafana, Tempo (traces), Loki (logs), and Pyroscope (profiles). Same image, same
configuration files, in both docker-compose and Kubernetes/Helm.

## Reaching the monitoring stack

The stack is **deliberately not exposed publicly** in any target — no ingress route, no published
admin UI on the internet (the AET fair-use policy forbids open admin UIs, and Grafana here runs
with anonymous Admin access). You reach it over a local port instead.

### docker-compose (local)

Grafana is published straight to your host:

```sh
# from the repo root, with the stack up (make compose-up)
open http://localhost:3001          # or whatever LGTM_GRAFANA_PORT is set to in infra/.env
```

- Port: host `3001` → container `3000`, overridable via `LGTM_GRAFANA_PORT` in `infra/.env`
  (`infra/.env.example` ships it as `3001`).
- Auth: none needed — the `grafana/otel-lgtm` image enables anonymous **Admin** access by
  default. `admin` / `admin` also works if a login is ever prompted (this is what
  `infra/scripts/smoke-test.sh` uses for its Loki datasource-proxy query).

### Kubernetes (Helm or raw manifests)

There is **no ingress** for `grafana-lgtm` — reach it with a port-forward to the ClusterIP
Service:

```sh
# pick your namespace (kubernetes-test, your dev namespace, prod, …)
kubectl port-forward svc/grafana-lgtm 3001:3000 -n <namespace>
# then open:
open http://localhost:3001
```

If you're not sure it's running:

```sh
kubectl get pods,svc -n <namespace> -l app=grafana-lgtm
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
kubectl port-forward "$(kubectl get pod -n <namespace> -l app=grafana-lgtm -o name)" \
  9090:9090 -n <namespace>
# then browse http://localhost:9090
```

### OTLP ingest endpoints (for reference)

Services push metrics/traces/logs to the collector inside the same container, not to Grafana's
port:

- OTLP/HTTP: `http://grafana-lgtm:4318` (docker-compose service DNS / Kubernetes Service name)
- OTLP/gRPC: `grafana-lgtm:4317`

You don't normally call these by hand — they're wired into every service's env
(`MANAGEMENT_OTLP_METRICS_EXPORT_URL` etc. for Spring, `OTEL_EXPORTER_OTLP_ENDPOINT` for gen-ai).

## What's provisioned

- **Datasources**: Prometheus, Tempo, Loki, Pyroscope — all pre-wired by the image itself with
  exemplar/trace correlation.
- **Dashboards**: `infra/grafana/dashboards/service-overview.json` ("Service Overview") — request
  rate, error rate, and duration percentiles per service, filterable by the `job` template
  variable. The image's own bundled "RED Metrics (classic histogram)" and "JVM Metrics" dashboards
  are also still available alongside it. The image's third bundled dashboard, "RED Metrics (native
  histogram)", is intentionally removed — our services only ever emit classic bucketed Prometheus
  histograms, not true native histograms, so it would always be empty (see
  `docs/internal/06-observability.md`'s Known gaps).
- **Alerts**: `infra/grafana/provisioning/alerting/rules.yaml` — slow response time (p95 request
  duration > 1s for 5 minutes) and service down (`up == 0` for 2 minutes), both under the
  "Service Health" folder. Note: that folder only holds these 2 alert rules, no dashboards — if
  you browse **Dashboards → Service Health** it'll look empty; the rules themselves are under
  **Alerting → Alert rules → Service Health**.

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

## Kubernetes resource budget

`grafana-lgtm` runs inside the same quota-constrained namespace as every other service (2 CPU /
3000Mi hard limit, verified empirically against the live cluster at the real 2-replicas-per-service
production configuration). To make room for it, every other service's resource limits were
trimmed in both `infra/helm/values.yaml` and the raw `infra/k8s/deployments/*.yml` manifests.
Even with that trimming, the final margin is small — roughly 210m CPU / 60Mi memory across the
whole namespace. Treat this as something to check, not fire-and-forget: run
`kubectl top pod -n <namespace>` after deploying, and adjust `monitoring.resources` (or the other
services' trimmed limits) if usage is running close to the edge.
