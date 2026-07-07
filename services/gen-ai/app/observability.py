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
    logging.getLogger().addHandler(LoggingHandler(logger_provider=logger_provider))

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
