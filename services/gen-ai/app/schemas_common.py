from __future__ import annotations


def validate_exactly_one_source(*, article_id: str | None, text: str | None) -> None:
    has_article = article_id is not None and article_id.strip() != ""
    has_text = text is not None and text.strip() != ""
    if has_article == has_text:
        raise ValueError("Exactly one of articleId or text must be provided")
