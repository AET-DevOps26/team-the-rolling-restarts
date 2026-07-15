from __future__ import annotations

from typing import Any

import pytest
from fastapi.testclient import TestClient
from langchain_core.messages import AIMessage

from app.main import app
from app.services.content import ArticleText


class FakeStructuredChatModel:
    def with_structured_output(self, _schema: Any) -> FakeStructuredChatModel:
        return self

    def invoke(self, _messages: list[Any]) -> dict[str, Any]:
        return {
            "sentiment": "positive",
            "score": 0.75,
            "bias": "center",
            "rationale": "The tone is optimistic about policy outcomes.",
        }


class MalformedJsonChatModel:
    def with_structured_output(self, _schema: Any) -> MalformedJsonChatModel:
        raise NotImplementedError("structured output not supported")

    def invoke(self, _messages: list[Any]) -> AIMessage:
        return AIMessage(content="not valid json {{{")


@pytest.fixture
def client(monkeypatch: pytest.MonkeyPatch) -> TestClient:
    monkeypatch.setattr(
        "app.routers.sentiment.get_chat_model",
        lambda: FakeStructuredChatModel(),
    )
    return TestClient(app)


def test_sentiment_with_text_returns_analysis(client: TestClient) -> None:
    response = client.post(
        "/sentiment",
        json={"text": "A hopeful article about renewable energy growth."},
    )

    assert response.status_code == 200
    body = response.json()
    assert body["sentiment"] == "positive"
    assert body["score"] == 0.75
    assert body["bias"] == "center"
    assert body["rationale"] == "The tone is optimistic about policy outcomes."
    assert body["model"] == "openai/gpt-oss-120b"
    assert body["provider"] == "logos"


def test_sentiment_with_article_id_fetches_content(
    client: TestClient, monkeypatch: pytest.MonkeyPatch
) -> None:
    async def fake_get_article_text(article_id: str) -> ArticleText:
        assert article_id == "article-321"
        return ArticleText(
            headline="Energy Outlook",
            text="Renewable capacity grew faster than expected.",
        )

    monkeypatch.setattr("app.routers.sentiment.get_article_text", fake_get_article_text)

    response = client.post("/sentiment", json={"articleId": "article-321"})

    assert response.status_code == 200
    body = response.json()
    assert body["sentiment"] == "positive"
    assert body["score"] == 0.75
    assert body["bias"] == "center"
    assert body["model"] == "openai/gpt-oss-120b"
    assert body["provider"] == "logos"


@pytest.mark.parametrize(
    "payload",
    [
        {},
        {"articleId": "a1", "text": "duplicate sources"},
    ],
)
def test_sentiment_rejects_invalid_input(client: TestClient, payload: dict[str, str]) -> None:
    response = client.post("/sentiment", json=payload)

    assert response.status_code == 400
    body = response.json()
    assert body["code"] == 400
    assert "message" in body
    assert isinstance(body["details"], list)
    assert body["path"] == "/sentiment"
    assert body["timestamp"].endswith("Z")


def test_sentiment_malformed_json_returns_unified_error(
    client: TestClient, monkeypatch: pytest.MonkeyPatch
) -> None:
    monkeypatch.setattr(
        "app.routers.sentiment.get_chat_model",
        lambda: MalformedJsonChatModel(),
    )

    response = client.post(
        "/sentiment",
        json={"text": "Some article with unclear tone."},
    )

    assert response.status_code == 502
    body = response.json()
    assert body["code"] == 502
    assert "message" in body
    assert isinstance(body["details"], list)
    assert body["path"] == "/sentiment"
    assert body["timestamp"].endswith("Z")
