from __future__ import annotations

from typing import Any

import pytest
from fastapi.testclient import TestClient
from langchain_core.messages import AIMessage

from app.main import app
from app.services.content import ArticleText


class FakeChatModel:
    def invoke(self, _messages: list[Any]) -> AIMessage:
        return AIMessage(content="This is a simplified explanation.")


@pytest.fixture
def client(monkeypatch: pytest.MonkeyPatch) -> TestClient:
    monkeypatch.setattr(
        "app.routers.explain.get_chat_model",
        lambda: FakeChatModel(),
    )
    return TestClient(app)


def test_explain_with_text_returns_explanation(client: TestClient) -> None:
    response = client.post(
        "/explain",
        json={"text": "Complex article about quantum computing.", "knowledgeLevel": "beginner"},
    )

    assert response.status_code == 200
    body = response.json()
    assert body["explanation"] == "This is a simplified explanation."
    assert body["knowledgeLevel"] == "beginner"
    assert body["model"] == "openai/gpt-oss-120b"
    assert body["provider"] == "logos"


def test_explain_with_article_id_fetches_content(
    client: TestClient, monkeypatch: pytest.MonkeyPatch
) -> None:
    async def fake_get_article_text(article_id: str) -> ArticleText:
        assert article_id == "article-456"
        return ArticleText(
            headline="Quantum Breakthrough",
            text="Scientists achieved a new milestone in qubit stability.",
        )

    monkeypatch.setattr("app.routers.explain.get_article_text", fake_get_article_text)

    response = client.post(
        "/explain",
        json={"articleId": "article-456", "knowledgeLevel": "child"},
    )

    assert response.status_code == 200
    body = response.json()
    assert body["explanation"] == "This is a simplified explanation."
    assert body["knowledgeLevel"] == "child"
    assert body["model"] == "openai/gpt-oss-120b"
    assert body["provider"] == "logos"


@pytest.mark.parametrize(
    "payload",
    [
        {"knowledgeLevel": "beginner"},
        {"articleId": "a1", "text": "duplicate sources", "knowledgeLevel": "beginner"},
    ],
)
def test_explain_rejects_invalid_input(client: TestClient, payload: dict[str, str]) -> None:
    response = client.post("/explain", json=payload)

    assert response.status_code == 400
    body = response.json()
    assert body["code"] == 400
    assert "message" in body
    assert isinstance(body["details"], list)
    assert body["path"] == "/explain"
    assert body["timestamp"].endswith("Z")
