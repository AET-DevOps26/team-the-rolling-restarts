import { registerOTel } from "@vercel/otel";
import { PeriodicExportingMetricReader } from "@opentelemetry/sdk-metrics";
import { OTLPMetricExporter } from "@opentelemetry/exporter-metrics-otlp-http";

// Mirrors services/gen-ai/app/observability.py's setup_observability(): a no-op unless an OTLP
// endpoint is actually configured, and Node-only (the OTel SDK components below don't run in the
// Edge runtime, which also calls this same register() function).
export function register() {
  if (process.env.NEXT_RUNTIME !== "nodejs" || !process.env.OTEL_EXPORTER_OTLP_ENDPOINT) {
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
