import { registerOTel } from "@vercel/otel";
import { PeriodicExportingMetricReader } from "@opentelemetry/sdk-metrics";
import { OTLPMetricExporter } from "@opentelemetry/exporter-metrics-otlp-http";

// Mirrors services/gen-ai/app/observability.py's setup_observability(): a no-op unless an OTLP
// endpoint is actually configured, and Node-only (the OTel SDK components below don't run in the
// Edge runtime, which also calls this same register() function).
export function register() {
  // OTLPMetricExporter() also honors the signal-specific OTEL_EXPORTER_OTLP_METRICS_ENDPOINT, so
  // checking only the general endpoint var would incorrectly skip all instrumentation (traces
  // included) for a deployment that only sets the metrics-specific one. Every current deployment
  // target sets the general var, so this is currently a no-op change — it's here so a future
  // signal-specific OTLP setup doesn't silently lose instrumentation.
  const hasOtlpEndpoint =
    process.env.OTEL_EXPORTER_OTLP_ENDPOINT || process.env.OTEL_EXPORTER_OTLP_METRICS_ENDPOINT;
  if (process.env.NEXT_RUNTIME !== "nodejs" || !hasOtlpEndpoint) {
    return;
  }

  registerOTel({
    serviceName: process.env.OTEL_SERVICE_NAME ?? "web-client",
    // "auto" respects OTEL_EXPORTER_OTLP_ENDPOINT for traces automatically; metrics have no such
    // "auto" option in @vercel/otel, so the reader/exporter pair below is built explicitly —
    // OTLPMetricExporter() with no args still reads the same standard OTLP env vars.
    traceExporter: "auto",
    metricReaders: [new PeriodicExportingMetricReader({ exporter: new OTLPMetricExporter() })],
  });
}
