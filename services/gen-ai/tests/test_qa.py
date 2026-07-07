from __future__ import annotations

from typing import Any

import pytest
from fastapi.testclient import TestClient
from langchain_core.messages import AIMessage

from app.main import app
from app.services.content import ArticleText


class FakeChatModel:
    def invoke(self, _messages: list[Any]) -> AIMessage:
        return AIMessage(content="The article mentions emissions targets.")


@pytest.fixture
def client(monkeypatch: pytest.MonkeyPatch) -> TestClient:
    monkeypatch.setattr(
        "app.routers.qa.get_chat_model",
        lambda: FakeChatModel(),
    )
    return TestClient(app)


def test_qa_with_text_returns_answer(client: TestClient) -> None:
    response = client.post(
        "/qa",
        json={
            "text": "World leaders met to discuss emissions targets.",
            "question": "What was discussed?",
        },
    )

    assert response.status_code == 200
    body = response.json()
    assert body["answer"] == "The article mentions emissions targets."
    assert body["model"] == "openai/gpt-oss-120b"
    assert body["provider"] == "logos"


def test_qa_with_article_id_fetches_content(
    client: TestClient, monkeypatch: pytest.MonkeyPatch
) -> None:
    async def fake_get_article_text(article_id: str) -> ArticleText:
        assert article_id == "article-789"
        return ArticleText(
            headline="Climate Summit",
            text="World leaders met to discuss emissions targets.",
        )

    monkeypatch.setattr("app.routers.qa.get_article_text", fake_get_article_text)

    response = client.post(
        "/qa",
        json={"articleId": "article-789", "question": "What was discussed?"},
    )

    assert response.status_code == 200
    body = response.json()
    assert body["answer"] == "The article mentions emissions targets."
    assert body["model"] == "openai/gpt-oss-120b"
    assert body["provider"] == "logos"


@pytest.mark.parametrize(
    "payload",
    [
        {"text": "Some article text"},
        {"articleId": "a1", "text": "duplicate", "question": "What?"},
        {"text": "Some article text", "question": ""},
        {"text": "Some article text", "question": "   "},
    ],
)
def test_qa_rejects_invalid_input(client: TestClient, payload: dict[str, str]) -> None:
    response = client.post("/qa", json=payload)

    assert response.status_code == 400
    body = response.json()
    assert body["code"] == 400
    assert "message" in body
    assert isinstance(body["details"], list)
    assert body["path"] == "/qa"
    assert body["timestamp"].endswith("Z")
