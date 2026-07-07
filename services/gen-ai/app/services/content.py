from __future__ import annotations

from dataclasses import dataclass

import httpx

from app.config import settings
from app.errors import ArticleNotFoundError


@dataclass(frozen=True)
class ArticleText:
    headline: str
    text: str


async def get_article_text(article_id: str) -> ArticleText:
    url = f"{settings.internal_api_url.rstrip('/')}/api/content/articles/{article_id}"

    async with httpx.AsyncClient() as client:
        response = await client.get(url)

    if response.status_code == 404:
        raise ArticleNotFoundError(f"Article not found: {article_id}")

    response.raise_for_status()
    data = response.json()

    body = data.get("body") or []
    paragraphs = [paragraph.strip() for paragraph in body if paragraph and paragraph.strip()]
    text = "\n\n".join(paragraphs)

    return ArticleText(
        headline=data.get("headline") or "",
        text=text,
    )
