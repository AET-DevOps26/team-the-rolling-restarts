from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, model_validator

from app.schemas_common import validate_exactly_one_source


class SummarizeRequest(BaseModel):
    articleId: str | None = None
    text: str | None = None
    length: Literal["short", "medium", "long"] = "short"
    style: Literal["plain", "bullets"] = "plain"

    @model_validator(mode="after")
    def validate_exactly_one_source(self) -> SummarizeRequest:
        validate_exactly_one_source(article_id=self.articleId, text=self.text)
        return self


class SummarizeResponse(BaseModel):
    summary: str
    model: str
    provider: str


class ExplainRequest(BaseModel):
    articleId: str | None = None
    text: str | None = None
    knowledgeLevel: Literal["child", "beginner", "intermediate", "expert"] = "beginner"

    @model_validator(mode="after")
    def validate_exactly_one_source(self) -> ExplainRequest:
        validate_exactly_one_source(article_id=self.articleId, text=self.text)
        return self


class ExplainResponse(BaseModel):
    explanation: str
    knowledgeLevel: str
    model: str
    provider: str


class SentimentRequest(BaseModel):
    articleId: str | None = None
    text: str | None = None

    @model_validator(mode="after")
    def validate_exactly_one_source(self) -> SentimentRequest:
        validate_exactly_one_source(article_id=self.articleId, text=self.text)
        return self


class SentimentResponse(BaseModel):
    sentiment: Literal["positive", "neutral", "negative"]
    score: float
    bias: Literal["left", "center", "right", "unclear"] | None = None
    rationale: str
    model: str
    provider: str


class QaRequest(BaseModel):
    articleId: str | None = None
    text: str | None = None
    question: str

    @model_validator(mode="after")
    def validate_sources_and_question(self) -> QaRequest:
        validate_exactly_one_source(article_id=self.articleId, text=self.text)
        if not self.question.strip():
            raise ValueError("question must be a non-empty string")
        return self


class QaResponse(BaseModel):
    answer: str
    model: str
    provider: str
