# 07 — Observability

Monitoring must expose **basic but meaningful** operational visibility. It must **not**
stop at "Prometheus is installed" / "Grafana is running" — the monitored data must let
someone tell whether the system is behaving **correctly or incorrectly**.

## Prometheus (metrics)

- Used for metrics collection.
- Must track **at minimum**: **request count, latency, and error rate**.
- Metrics must cover the **core runtime behaviour**, especially the **server side** and,
  where relevant, the **GenAI component**.

## Grafana (visualisation)

- Used for visualisation; dashboards must reflect **key system metrics** (server, GenAI).
- Dashboards must be **submitted as exported `.json` files**.

## Alerts

- At least **one meaningful alert rule** must be configured — e.g. **service downtime**
  or **slow response time**.

## Summary table

| Tool | Requirements |
|------|--------------|
| Prometheus | Metrics for at least request count, latency, and error rate |
| Grafana | Dashboards reflecting key system metrics (server, GenAI); submitted as `.json` |
| Alerts | At least one meaningful alert rule (e.g. service down, slow response) |

> Anti-pattern to avoid: dashboards that show data with no link to real system
> behaviour. Monitor **what can break** (latency, failures, load). See
> [11-pitfalls-and-team-culture.md](11-pitfalls-and-team-culture.md).
