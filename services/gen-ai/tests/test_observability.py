from __future__ import annotations

import importlib
import os

import pytest
from fastapi import FastAPI
from fastapi.testclient import TestClient
from langchain_core.messages import AIMessage, HumanMessage
from opentelemetry import trace
from opentelemetry.sdk.trace.export import SimpleSpanProcessor
from opentelemetry.sdk.trace.export.in_memory_span_exporter import InMemorySpanExporter

from app.main import app
from app.observability import _build_resource, setup_observability


class FakeChatModel:
    def invoke(self, _messages: list[object]) -> AIMessage:
        return AIMessage(content="Observability smoke summary.")


@pytest.fixture
def client(monkeypatch: pytest.MonkeyPatch) -> TestClient:
    monkeypatch.setattr(
        "app.routers.summarize.get_chat_model",
        lambda: FakeChatModel(),
    )
    return TestClient(app)


def test_setup_observability_is_noop_without_endpoint(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.delenv("OTEL_EXPORTER_OTLP_ENDPOINT", raising=False)

    fresh_app = FastAPI()
    assert setup_observability(fresh_app) is False


def test_setup_observability_attaches_log_handler_when_endpoint_set(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("OTEL_EXPORTER_OTLP_ENDPOINT", "http://localhost:4318")

    import logging

    fresh_app = FastAPI()
    root_before = len(logging.getLogger().handlers)
    assert setup_observability(fresh_app) is True
    root_after = len(logging.getLogger().handlers)

    assert root_after == root_before + 1


def test_app_starts_without_otlp_endpoint(client: TestClient) -> None:
    assert os.environ.get("OTEL_EXPORTER_OTLP_ENDPOINT") is None

    health = client.get("/health")
    assert health.status_code == 200
    assert health.json() == {"status": "ok"}

    summarize = client.post(
        "/summarize",
        json={"text": "Article body for observability smoke test.", "length": "short", "style": "plain"},
    )
    assert summarize.status_code == 200
    assert summarize.json()["summary"] == "Observability smoke summary."


def test_metrics_endpoint_exposes_prometheus_format(client: TestClient) -> None:
    response = client.get("/metrics")

    assert response.status_code == 200
    assert "http_requests_total" in response.text or "http_request_duration_seconds" in response.text


def test_build_resource_defaults_service_instance_id_to_hostname(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.delenv("OTEL_RESOURCE_ATTRIBUTES", raising=False)
    monkeypatch.setenv("HOSTNAME", "gen-ai-abc123")

    resource = _build_resource()

    assert resource.attributes["service.instance.id"] == "gen-ai-abc123"


def test_build_resource_respects_explicit_service_instance_id(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("OTEL_RESOURCE_ATTRIBUTES", "service.instance.id=explicit-id")
    monkeypatch.setenv("HOSTNAME", "gen-ai-abc123")

    resource = _build_resource()

    assert resource.attributes["service.instance.id"] == "explicit-id"


def test_invoke_chat_model_emits_llm_invoke_span(monkeypatch: pytest.MonkeyPatch) -> None:
    # Regression test for a previously-suspected gap (see app/main.py's history): the custom
    # "llm.invoke" span appeared not to reach Tempo even after the fix that made the custom
    # *metrics* work (deferring the router import, and with it app.llm.invoke's module-level
    # tracer/meter creation, until after setup_observability() runs). Reproducing it directly
    # shows the span IS created and correctly parented under the current span — the OTel SDK's
    # `set_tracer_provider` can only succeed once per process, so this attaches an additional
    # span processor to whatever provider is already active (real, once setup_observability has
    # run with an endpoint configured — guaranteed here) instead of trying to replace it.
    monkeypatch.setenv("OTEL_EXPORTER_OTLP_ENDPOINT", "http://localhost:4318")
    setup_observability(FastAPI())

    tracer_provider = trace.get_tracer_provider()
    exporter = InMemorySpanExporter()
    tracer_provider.add_span_processor(SimpleSpanProcessor(exporter))

    import app.llm.invoke as invoke_module

    importlib.reload(invoke_module)  # rebind _tracer to the now-current tracer_provider

    class StubModel:
        def invoke(self, _messages: list[object]) -> AIMessage:
            return AIMessage(content="ok")

    invoke_module.invoke_chat_model(
        StubModel(),
        [HumanMessage(content="hi")],
        endpoint="/summarize",
        provider="logos",
    )

    spans = [s for s in exporter.get_finished_spans() if s.name == "llm.invoke"]
    assert len(spans) == 1
    assert spans[0].attributes["gen_ai.endpoint"] == "/summarize"
    assert spans[0].attributes["gen_ai.provider"] == "logos"
