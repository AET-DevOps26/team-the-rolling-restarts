from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, model_validator


class SummarizeRequest(BaseModel):
    articleId: str | None = None
    text: str | None = None
    length: Literal["short", "medium", "long"] = "short"
    style: Literal["plain", "bullets"] = "plain"

    @model_validator(mode="after")
    def validate_exactly_one_source(self) -> SummarizeRequest:
        has_article = self.articleId is not None
        has_text = self.text is not None
        if has_article == has_text:
            raise ValueError("Exactly one of articleId or text must be provided")
        return self


class SummarizeResponse(BaseModel):
    summary: str
    model: str
    provider: str
