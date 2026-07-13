from __future__ import annotations

import json
import re
from typing import Any, Literal

from fastapi import APIRouter
from langchain_core.messages import HumanMessage, SystemMessage
from pydantic import BaseModel, Field

from app.config import settings
from app.errors import UpstreamLLMError
from app.llm.invoke import invoke_chat_model
from app.llm.provider import get_chat_model
from app.schemas import SentimentRequest, SentimentResponse
from app.services.content import get_article_text

router = APIRouter()


class SentimentResult(BaseModel):
    sentiment: Literal["positive", "neutral", "negative"]
    score: float = Field(ge=-1.0, le=1.0)
    bias: Literal["left", "center", "right", "unclear"] | None = None
    rationale: str


def _build_source_text(*, headline: str, text: str) -> str:
    parts = [part.strip() for part in (headline, text) if part and part.strip()]
    return "\n\n".join(parts)


def _build_messages(source_text: str, *, json_only: bool = False) -> list[SystemMessage | HumanMessage]:
    if json_only:
        system_prompt = (
            "You are a news sentiment and bias analysis assistant. Analyze the article "
            "and respond with ONLY valid JSON (no markdown, no extra text) using this shape: "
            '{"sentiment": "positive|neutral|negative", "score": <float from -1.0 to 1.0>, '
            '"bias": "left|center|right|unclear" or null, "rationale": "<brief explanation>"}'
        )
    else:
        system_prompt = (
            "You are a news sentiment and bias analysis assistant. "
            "Assess overall sentiment (positive, neutral, or negative) with a score from "
            "-1.0 (most negative) to 1.0 (most positive), estimate political bias "
            "(left, center, right, unclear, or null if not applicable), and explain briefly."
        )
    return [
        SystemMessage(content=system_prompt),
        HumanMessage(content=source_text),
    ]


def _coerce_sentiment_result(raw: Any) -> SentimentResult:
    if isinstance(raw, SentimentResult):
        return raw
    if isinstance(raw, dict):
        return SentimentResult.model_validate(raw)
    raise ValueError("Unexpected sentiment result type")


def _parse_sentiment_json(content: str) -> dict[str, Any]:
    stripped = content.strip()
    try:
        parsed = json.loads(stripped)
    except json.JSONDecodeError:
        match = re.search(r"\{.*\}", stripped, re.DOTALL)
        if match is None:
            raise ValueError("No JSON object found in model response") from None
        parsed = json.loads(match.group())
    if not isinstance(parsed, dict):
        raise ValueError("Sentiment JSON must be an object")
    return parsed


def _analyze_sentiment(model: Any, source_text: str) -> SentimentResult:
    try:
        structured = model.with_structured_output(SentimentResult)
        result = invoke_chat_model(
            structured,
            _build_messages(source_text),
            endpoint="/sentiment",
            provider=settings.llm_provider,
        )
        return _coerce_sentiment_result(result)
    except Exception:
        pass

    try:
        result = invoke_chat_model(
            model,
            _build_messages(source_text, json_only=True),
            endpoint="/sentiment",
            provider=settings.llm_provider,
        )
        content = result.content if isinstance(result.content, str) else str(result.content)
        parsed = _parse_sentiment_json(content)
        return SentimentResult.model_validate(parsed)
    except Exception as exc:
        raise UpstreamLLMError("Failed to parse sentiment analysis") from exc


@router.post("/sentiment", response_model=SentimentResponse)
async def sentiment(request: SentimentRequest) -> SentimentResponse:
    if request.articleId is not None:
        article = await get_article_text(request.articleId)
        source_text = _build_source_text(headline=article.headline, text=article.text)
    else:
        source_text = request.text or ""

    model = get_chat_model()
    try:
        analysis = _analyze_sentiment(model, source_text)
    except UpstreamLLMError:
        raise
    except Exception as exc:  # noqa: BLE001 - map provider failures to unified API errors
        raise UpstreamLLMError("Failed to generate sentiment analysis") from exc

    return SentimentResponse(
        sentiment=analysis.sentiment,
        score=analysis.score,
        bias=analysis.bias,
        rationale=analysis.rationale,
        model=settings.llm_model,
        provider=settings.llm_provider,
    )
