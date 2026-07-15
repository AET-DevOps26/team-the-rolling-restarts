from __future__ import annotations

import logging
import os
from typing import TYPE_CHECKING

from opentelemetry import metrics, trace
from opentelemetry.exporter.otlp.proto.http.metric_exporter import OTLPMetricExporter
from opentelemetry.exporter.otlp.proto.http.trace_exporter import OTLPSpanExporter
from opentelemetry.instrumentation.fastapi import FastAPIInstrumentor
from opentelemetry.sdk.metrics import MeterProvider
from opentelemetry.sdk.metrics.export import PeriodicExportingMetricReader
from opentelemetry.sdk.resources import Resource
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import BatchSpanProcessor
from opentelemetry.sdk._logs import LoggerProvider, LoggingHandler
from opentelemetry.sdk._logs.export import BatchLogRecordProcessor
from opentelemetry.exporter.otlp.proto.http._log_exporter import OTLPLogExporter

if TYPE_CHECKING:
    from fastapi import FastAPI

logger = logging.getLogger(__name__)

_DEFAULT_SERVICE_NAME = "gen-ai"


def _build_resource() -> Resource:
    attributes: dict[str, str] = {}
    raw_attributes = os.environ.get("OTEL_RESOURCE_ATTRIBUTES", "")
    for pair in raw_attributes.split(","):
        pair = pair.strip()
        if not pair or "=" not in pair:
            continue
        key, value = pair.split("=", 1)
        attributes[key.strip()] = value.strip()

    service_name = os.environ.get("OTEL_SERVICE_NAME", _DEFAULT_SERVICE_NAME)
    # Without an explicit service.instance.id, the OTel SDK generates a fresh random UUID per
    # process start, which our OTLP receiver promotes straight to Prometheus's `instance` label —
    # showing up as an unreadable UUID in any dashboard legend that formats on {{instance}}.
    # HOSTNAME is set by both Docker and Kubernetes for every container (the pod name in k8s),
    # giving a stable, readable value with no extra env var wiring needed.
    attributes.setdefault("service.instance.id", os.environ.get("HOSTNAME", service_name))
    return Resource.create({"service.name": service_name, **attributes})


def _otlp_endpoint_configured() -> bool:
    endpoint = os.environ.get("OTEL_EXPORTER_OTLP_ENDPOINT", "").strip()
    return bool(endpoint)


def setup_observability(app: FastAPI) -> bool:
    """Configure OTLP tracing/metrics when an endpoint is set; otherwise no-op."""
    if not _otlp_endpoint_configured():
        logger.info("OTEL_EXPORTER_OTLP_ENDPOINT unset; observability exporters disabled")
        FastAPIInstrumentor.instrument_app(app)
        return False

    resource = _build_resource()

    tracer_provider = TracerProvider(resource=resource)
    tracer_provider.add_span_processor(BatchSpanProcessor(OTLPSpanExporter()))
    trace.set_tracer_provider(tracer_provider)

    metric_reader = PeriodicExportingMetricReader(OTLPMetricExporter())
    meter_provider = MeterProvider(resource=resource, metric_readers=[metric_reader])
    metrics.set_meter_provider(meter_provider)

    logger_provider = LoggerProvider(resource=resource)
    logger_provider.add_log_record_processor(BatchLogRecordProcessor(OTLPLogExporter()))
    otlp_handler = LoggingHandler(logger_provider=logger_provider)
    logging.getLogger().addHandler(otlp_handler)
    # uvicorn's default logging config sets propagate=False on "uvicorn" and
    # "uvicorn.access" (and "uvicorn.error" ends up shielded too, since its parent
    # "uvicorn" doesn't propagate further) specifically so app-level root logger
    # config doesn't interfere with it. That also means nothing on those loggers
    # ever reaches a handler attached only to root - i.e. virtually all request
    # traffic logging - so the handler must be attached directly to them too.
    for uvicorn_logger_name in ("uvicorn", "uvicorn.error", "uvicorn.access"):
        logging.getLogger(uvicorn_logger_name).addHandler(otlp_handler)

    try:
        from opentelemetry.instrumentation.logging import LoggingInstrumentor

        # enable_log_auto_instrumentation=False: we already attach our own
        # resource-bound LoggingHandler above; the library's built-in
        # auto-instrumentation would otherwise add a second (no-op, since no
        # global logger provider is set) handler to the root logger.
        LoggingInstrumentor().instrument(
            set_logging_format=True, enable_log_auto_instrumentation=False
        )
    except Exception:  # noqa: BLE001 - logging instrumentation is optional
        logger.debug("Logging instrumentation unavailable; continuing without it")

    FastAPIInstrumentor.instrument_app(app)
    logger.info(
        "OpenTelemetry configured for service %s",
        os.environ.get("OTEL_SERVICE_NAME", _DEFAULT_SERVICE_NAME),
    )
    return True
