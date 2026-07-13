from __future__ import annotations

from typing import Any

import pytest
from fastapi.testclient import TestClient
from langchain_core.messages import AIMessage

from app.errors import ArticleNotFoundError, UpstreamServiceError
from app.main import app
from app.services.content import ArticleText


class FakeChatModel:
    def invoke(self, _messages: list[Any]) -> AIMessage:
        return AIMessage(content="This is a canned summary.")


@pytest.fixture
def client(monkeypatch: pytest.MonkeyPatch) -> TestClient:
    monkeypatch.setattr(
        "app.routers.summarize.get_chat_model",
        lambda: FakeChatModel(),
    )
    return TestClient(app)


def test_summarize_with_text_returns_summary(client: TestClient) -> None:
    response = client.post(
        "/summarize",
        json={"text": "Long article body about climate policy.", "length": "short", "style": "plain"},
    )

    assert response.status_code == 200
    body = response.json()
    assert body["summary"] == "This is a canned summary."
    assert body["model"] == "openai/gpt-oss-120b"
    assert body["provider"] == "logos"


def test_summarize_with_article_id_fetches_content(
    client: TestClient, monkeypatch: pytest.MonkeyPatch
) -> None:
    async def fake_get_article_text(article_id: str) -> ArticleText:
        assert article_id == "article-123"
        return ArticleText(
            headline="Climate Summit",
            text="World leaders met to discuss emissions targets.",
        )

    monkeypatch.setattr("app.routers.summarize.get_article_text", fake_get_article_text)

    response = client.post(
        "/summarize",
        json={"articleId": "article-123", "length": "medium", "style": "bullets"},
    )

    assert response.status_code == 200
    body = response.json()
    assert body["summary"] == "This is a canned summary."
    assert body["model"] == "openai/gpt-oss-120b"
    assert body["provider"] == "logos"


@pytest.mark.parametrize(
    "payload",
    [
        {"length": "short", "style": "plain"},
        {"articleId": "a1", "text": "duplicate sources", "length": "short", "style": "plain"},
    ],
)
def test_summarize_rejects_invalid_input(client: TestClient, payload: dict[str, str]) -> None:
    response = client.post("/summarize", json=payload)

    assert response.status_code == 400
    body = response.json()
    assert body["code"] == 400
    assert "message" in body
    assert isinstance(body["details"], list)
    assert body["path"] == "/summarize"
    assert body["timestamp"].endswith("Z")


def test_summarize_article_not_found_returns_unified_error(
    client: TestClient, monkeypatch: pytest.MonkeyPatch
) -> None:
    async def missing_article(_article_id: str) -> ArticleText:
        raise ArticleNotFoundError("Article not found: missing-id")

    monkeypatch.setattr("app.routers.summarize.get_article_text", missing_article)

    response = client.post(
        "/summarize",
        json={"articleId": "missing-id", "length": "short", "style": "plain"},
    )

    assert response.status_code == 404
    body = response.json()
    assert body["code"] == 404
    assert body["message"] == "Article not found: missing-id"
    assert body["details"] == []
    assert body["path"] == "/summarize"
    assert body["timestamp"].endswith("Z")


def test_summarize_upstream_content_error_returns_unified_error(
    client: TestClient, monkeypatch: pytest.MonkeyPatch
) -> None:
    async def unavailable_article(_article_id: str) -> ArticleText:
        raise UpstreamServiceError(
            "Content service unavailable",
            details=["upstream status: 503"],
        )

    monkeypatch.setattr("app.routers.summarize.get_article_text", unavailable_article)

    response = client.post(
        "/summarize",
        json={"articleId": "article-503", "length": "short", "style": "plain"},
    )

    assert response.status_code == 502
    body = response.json()
    assert body["code"] == 502
    assert body["message"] == "Content service unavailable"
    assert body["details"] == ["upstream status: 503"]
    assert body["path"] == "/summarize"
    assert body["timestamp"].endswith("Z")
