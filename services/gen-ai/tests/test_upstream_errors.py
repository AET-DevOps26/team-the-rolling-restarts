from __future__ import annotations

from typing import Any

import httpx
import pytest
from fastapi.testclient import TestClient
from langchain_core.messages import AIMessage

from app.main import app


class FakeChatModel:
    def invoke(self, _messages: list[Any]) -> AIMessage:
        return AIMessage(content="Summary text.")


@pytest.fixture
def client(monkeypatch: pytest.MonkeyPatch) -> TestClient:
    monkeypatch.setattr(
        "app.routers.summarize.get_chat_model",
        lambda: FakeChatModel(),
    )
    return TestClient(app)


class FailingAsyncClient:
    async def __aenter__(self) -> FailingAsyncClient:
        return self

    async def __aexit__(self, *_args: object) -> None:
        return None

    async def get(self, url: str) -> httpx.Response:
        raise httpx.ConnectError("connection refused", request=httpx.Request("GET", url))


class ServerErrorAsyncClient:
    async def __aenter__(self) -> ServerErrorAsyncClient:
        return self

    async def __aexit__(self, *_args: object) -> None:
        return None

    async def get(self, url: str) -> httpx.Response:
        request = httpx.Request("GET", url)
        return httpx.Response(503, request=request)


def test_content_service_connection_error_returns_unified_502(
    client: TestClient,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setattr("app.services.content.httpx.AsyncClient", FailingAsyncClient)

    response = client.post(
        "/summarize",
        json={"articleId": "article-123", "length": "short", "style": "plain"},
    )

    assert response.status_code == 502
    body = response.json()
    assert body["code"] == 502
    assert body["message"] == "Failed to reach content service"
    assert body["details"] == []
    assert body["path"] == "/summarize"
    assert body["timestamp"].endswith("Z")


def test_content_service_5xx_returns_unified_502(
    client: TestClient,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setattr("app.services.content.httpx.AsyncClient", ServerErrorAsyncClient)

    response = client.post(
        "/summarize",
        json={"articleId": "article-123", "length": "short", "style": "plain"},
    )

    assert response.status_code == 502
    body = response.json()
    assert body["code"] == 502
    assert body["message"] == "Content service returned an error"
    assert body["path"] == "/summarize"


def test_empty_article_content_returns_unified_400(
    client: TestClient,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    from app.services.content import ArticleText

    async def empty_article(_article_id: str) -> ArticleText:
        return ArticleText(headline="", text="")

    monkeypatch.setattr("app.routers.summarize.get_article_text", empty_article)

    response = client.post(
        "/summarize",
        json={"articleId": "empty-article", "length": "short", "style": "plain"},
    )

    assert response.status_code == 400
    body = response.json()
    assert body["code"] == 400
    assert body["message"] == "article has no content to process"
    assert body["path"] == "/summarize"


@pytest.mark.parametrize(
    "payload",
    [
        {"text": "", "length": "short", "style": "plain"},
        {"text": "   ", "length": "short", "style": "plain"},
        {"articleId": "  ", "length": "short", "style": "plain"},
    ],
)
def test_summarize_rejects_blank_source(client: TestClient, payload: dict[str, str]) -> None:
    response = client.post("/summarize", json=payload)

    assert response.status_code == 400
    body = response.json()
    assert body["code"] == 400
    assert body["path"] == "/summarize"


def test_unknown_llm_provider_returns_unified_500(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    def unknown_provider() -> None:
        raise ValueError("Unknown LLM provider: 'invalid-provider'")

    monkeypatch.setattr("app.routers.summarize.get_chat_model", unknown_provider)
    client = TestClient(app, raise_server_exceptions=False)

    response = client.post(
        "/summarize",
        json={"text": "Some article text.", "length": "short", "style": "plain"},
    )

    assert response.status_code == 500
    body = response.json()
    assert body["code"] == 500
    assert body["message"] == "Internal server error"
    assert body["path"] == "/summarize"
