from __future__ import annotations

from unittest.mock import AsyncMock, MagicMock, patch

import httpx
import pytest

from app.errors import ArticleNotFoundError, UpstreamServiceError
from app.services.content import get_article_text


@pytest.mark.asyncio
async def test_get_article_text_raises_not_found_for_404() -> None:
    response = MagicMock()
    response.status_code = 404

    with patch("app.services.content.httpx.AsyncClient") as client_cls:
        client = AsyncMock()
        client.__aenter__.return_value = client
        client.get.return_value = response
        client_cls.return_value = client

        with pytest.raises(ArticleNotFoundError, match="Article not found: missing-id"):
            await get_article_text("missing-id")


@pytest.mark.asyncio
async def test_get_article_text_raises_upstream_error_for_5xx() -> None:
    response = MagicMock()
    response.status_code = 500

    with patch("app.services.content.httpx.AsyncClient") as client_cls:
        client = AsyncMock()
        client.__aenter__.return_value = client
        client.get.return_value = response
        client_cls.return_value = client

        with pytest.raises(UpstreamServiceError, match="Content service unavailable") as exc_info:
            await get_article_text("article-500")

        assert exc_info.value.details == ["upstream status: 500"]


@pytest.mark.asyncio
async def test_get_article_text_raises_upstream_error_for_request_failure() -> None:
    with patch("app.services.content.httpx.AsyncClient") as client_cls:
        client = AsyncMock()
        client.__aenter__.return_value = client
        client.get.side_effect = httpx.ConnectError("connection refused")
        client_cls.return_value = client

        with pytest.raises(UpstreamServiceError, match="Content service unavailable"):
            await get_article_text("article-down")
