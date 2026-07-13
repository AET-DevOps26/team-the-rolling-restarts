from __future__ import annotations

from typing import Any

import httpx
import pytest
from fastapi.testclient import TestClient
from langchain_core.messages import AIMessage

from app.main import app


class RecordingChatModel:
    def __init__(self) -> None:
        self.received: list[Any] = []

    def invoke(self, messages: list[Any]) -> AIMessage:
        self.received = messages
        return AIMessage(content="Summary text.")


def _article_client(payload: dict[str, Any]) -> type:
    class _Client:
        async def __aenter__(self) -> "_Client":
            return self

        async def __aexit__(self, *_args: object) -> None:
            return None

        async def get(self, url: str) -> httpx.Response:
            return httpx.Response(200, json=payload, request=httpx.Request("GET", url))

    return _Client


def _prompt_text(model: RecordingChatModel) -> str:
    return "\n".join(str(message.content) for message in model.received)


def test_summarize_falls_back_to_snippet_when_body_empty(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    model = RecordingChatModel()
    monkeypatch.setattr("app.routers.summarize.get_chat_model", lambda: model)
    monkeypatch.setattr(
        "app.services.content.httpx.AsyncClient",
        _article_client({"headline": "Head", "body": [], "snippet": "The real snippet text."}),
    )

    response = TestClient(app).post(
        "/summarize",
        json={"articleId": "a1", "length": "short", "style": "plain"},
    )

    assert response.status_code == 200
    assert "The real snippet text." in _prompt_text(model)


def test_summarize_prefers_body_over_snippet(monkeypatch: pytest.MonkeyPatch) -> None:
    model = RecordingChatModel()
    monkeypatch.setattr("app.routers.summarize.get_chat_model", lambda: model)
    monkeypatch.setattr(
        "app.services.content.httpx.AsyncClient",
        _article_client(
            {"headline": "Head", "body": ["Body paragraph."], "snippet": "ignored snippet"}
        ),
    )

    response = TestClient(app).post(
        "/summarize",
        json={"articleId": "a1", "length": "short", "style": "plain"},
    )

    assert response.status_code == 200
    prompt = _prompt_text(model)
    assert "Body paragraph." in prompt
    assert "ignored snippet" not in prompt
