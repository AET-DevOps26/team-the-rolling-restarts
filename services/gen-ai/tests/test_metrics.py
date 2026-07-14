from __future__ import annotations

import importlib
from typing import Any

import pytest
from langchain_core.messages import AIMessage, HumanMessage
from opentelemetry import metrics
from opentelemetry.sdk.metrics import MeterProvider
from opentelemetry.sdk.metrics.export import InMemoryMetricReader


def _request_count(reader: InMemoryMetricReader, *, endpoint: str, provider: str) -> int:
    metrics_data = reader.get_metrics_data()
    assert metrics_data is not None
    for resource_metrics in metrics_data.resource_metrics:
        for scope_metrics in resource_metrics.scope_metrics:
            for metric in scope_metrics.metrics:
                if metric.name != "gen_ai.llm.requests":
                    continue
                total = 0
                for data_point in metric.data.data_points:
                    attrs = dict(data_point.attributes)
                    if attrs.get("endpoint") == endpoint and attrs.get("provider") == provider:
                        total += int(data_point.value)
                return total
    return 0


def test_invoke_chat_model_records_request_metrics() -> None:
    reader = InMemoryMetricReader()
    metrics.set_meter_provider(MeterProvider(metric_readers=[reader]))
    import app.llm.invoke as invoke_module

    importlib.reload(invoke_module)
    from app.llm.invoke import invoke_chat_model

    class StubModel:
        def invoke(self, _messages: list[HumanMessage]) -> AIMessage:
            return AIMessage(content="ok")

    invoke_chat_model(
        StubModel(),  # type: ignore[arg-type]
        [HumanMessage(content="hello")],
        endpoint="/explain",
        provider="logos",
    )
    assert _request_count(reader, endpoint="/explain", provider="logos") == 1

    invoke_chat_model(
        StubModel(),  # type: ignore[arg-type]
        [HumanMessage(content="hello")],
        endpoint="/sentiment",
        provider="logos",
        invoke_fn=lambda: AIMessage(content='{"sentiment":"neutral","score":0,"rationale":"ok"}'),
    )
    assert _request_count(reader, endpoint="/sentiment", provider="logos") == 1
