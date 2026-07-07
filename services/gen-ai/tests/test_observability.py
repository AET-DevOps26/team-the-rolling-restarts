from __future__ import annotations

import os

import pytest
from fastapi import FastAPI
from fastapi.testclient import TestClient
from langchain_core.messages import AIMessage

from app.main import app
from app.observability import setup_observability


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
