# Observability

**Not classic standalone Prometheus/Grafana** — the course brief says
"Prometheus must be used" (`docs/requirements/07-observability.md`), but this
repo uses **OpenTelemetry + Grafana's all-in-one `otel-lgtm` image** instead.
Functionally adjacent (Mimir inside LGTM is Prometheus-compatible) but worth
flagging to a tutor if the letter of the requirement matters.

## What's wired up

- All 3 Spring services depend on `spring-boot-starter-opentelemetry`
  (`services/spring/*/build.gradle`)
- `api-gateway` additionally has `micrometer-registry-influx`
  (`build.gradle:30`) — InfluxDB-format metrics registry, separate from the
  OTel path
- `grafana-lgtm` service in `infra/docker-compose.yaml`: image
  `grafana/otel-lgtm` — bundles **Grafana + Loki (logs) + Tempo (tracing) +
  Mimir (metrics)** in one container. Ports: Grafana UI on
  `${LGTM_GRAFANA_PORT:-3001}`, OTLP gRPC `4317`, OTLP HTTP `4318`.
- `services/gen-ai` has **zero** metrics/tracing instrumentation — no
  `prometheus_client`, no OTel SDK usage found.

## What's missing (required deliverables, not just "nice to have")

- No exported Grafana dashboard `.json` anywhere in the repo
- No alert rule file (Prometheus alerting rules or Grafana alert YAML/JSON)
- gen-ai instrumentation

## Why this matters beyond compliance

The LGTM stack already includes Tempo (tracing) and Loki (log aggregation) —
infrastructure that would normally count toward the "Advanced Observability"
bonus tier (`docs/requirements/12-grading-structure.md`). Exporting a
dashboard + writing one alert rule clears the baseline requirement *and*
gets most of the way to that bonus, since the hard infrastructure part is
already deployed and just unexploited.

## Re-verify

```sh
grep -rn "opentelemetry\|micrometer" services/spring/*/build.gradle
grep -n -A5 "grafana-lgtm:" infra/docker-compose.yaml
find . -iname "*dashboard*.json" -o -iname "*alert*rule*" | grep -v node_modules | grep -v .venv
grep -n "prometheus_client\|opentelemetry" services/gen-ai/pyproject.toml
```
