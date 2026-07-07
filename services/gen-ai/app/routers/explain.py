from __future__ import annotations

from fastapi import APIRouter
from langchain_core.messages import HumanMessage, SystemMessage

from app.config import settings
from app.errors import UpstreamLLMError
from app.llm.provider import get_chat_model
from app.schemas import ExplainRequest, ExplainResponse
from app.services.content import get_article_text

router = APIRouter()

KNOWLEDGE_LEVEL_INSTRUCTIONS = {
    "child": (
        "Explain like the reader is a curious child (about age 8). "
        "Use very simple words, short sentences, and relatable examples. Avoid jargon."
    ),
    "beginner": (
        "Explain for someone new to the topic. Use plain language and define "
        "any necessary terms simply."
    ),
    "intermediate": (
        "Explain for someone with general news literacy. You may use common "
        "terminology but stay clear and accessible."
    ),
    "expert": (
        "Explain for an informed expert reader. Be precise and analytical; "
        "technical terms are acceptable."
    ),
}


def _build_source_text(*, headline: str, text: str) -> str:
    parts = [part.strip() for part in (headline, text) if part and part.strip()]
    return "\n\n".join(parts)


@router.post("/explain", response_model=ExplainResponse)
async def explain(request: ExplainRequest) -> ExplainResponse:
    if request.articleId is not None:
        article = await get_article_text(request.articleId)
        source_text = _build_source_text(headline=article.headline, text=article.text)
    else:
        source_text = request.text or ""

    system_prompt = (
        "You are a news explanation assistant. "
        f"{KNOWLEDGE_LEVEL_INSTRUCTIONS[request.knowledgeLevel]}"
    )
    messages = [
        SystemMessage(content=system_prompt),
        HumanMessage(content=source_text),
    ]

    model = get_chat_model()
    try:
        result = model.invoke(messages)
    except Exception as exc:  # noqa: BLE001 - map provider failures to unified API errors
        raise UpstreamLLMError("Failed to generate explanation") from exc

    explanation = result.content if isinstance(result.content, str) else str(result.content)

    return ExplainResponse(
        explanation=explanation,
        knowledgeLevel=request.knowledgeLevel,
        model=settings.llm_model,
        provider=settings.llm_provider,
    )
