from __future__ import annotations

from app.errors import BadRequestError


def build_source_text(*, headline: str, text: str) -> str:
    parts = [part.strip() for part in (headline, text) if part and part.strip()]
    return "\n\n".join(parts)


def require_processable_source(source_text: str) -> None:
    if not source_text.strip():
        raise BadRequestError("article has no content to process")
