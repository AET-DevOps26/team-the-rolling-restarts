# Observability

The system must expose basic but meaningful operational visibility. This means that monitoring should not stop at "Prometheus is installed" or "Grafana is running." Instead, the monitored data must allow someone to understand whether the system is behaving correctly or incorrectly.

Prometheus must be used for metrics collection. At minimum, the project must track request count, latency, and error rate. These metrics should cover the core runtime behaviour of the system, especially on the server side and, where relevant, the GenAI component. Grafana must be used for visualisation, and dashboards must reflect key system metrics. These dashboards must be submitted as exported .json files. At least one meaningful alert rule must be configured, for example for service downtime or slow response time.

| Tool | Requirements |
| --- | --- |
| Prometheus | Metrics collection for at least request count, latency, and error rate |
| Grafana | Dashboards must reflect key system metrics (server, GenAI); must be submitted as .json |
| Alerts | At least one meaningful alert rule, e.g. service down or slow response time |
