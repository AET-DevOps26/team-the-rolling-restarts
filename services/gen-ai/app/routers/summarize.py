from __future__ import annotations

from fastapi import APIRouter
from langchain_core.messages import HumanMessage, SystemMessage

from app.config import settings
from app.errors import UpstreamLLMError
from app.llm.provider import get_chat_model
from app.schemas import SummarizeRequest, SummarizeResponse
from app.services.content import get_article_text

router = APIRouter()

LENGTH_INSTRUCTIONS = {
    "short": "Keep the summary to 2-3 sentences.",
    "medium": "Write a summary of about one paragraph.",
    "long": "Write a detailed summary of several paragraphs.",
}

STYLE_INSTRUCTIONS = {
    "plain": "Use plain prose paragraphs.",
    "bullets": "Format the summary as bullet points.",
}


def _build_source_text(*, headline: str, text: str) -> str:
    parts = [part.strip() for part in (headline, text) if part and part.strip()]
    return "\n\n".join(parts)


@router.post("/summarize", response_model=SummarizeResponse)
async def summarize(request: SummarizeRequest) -> SummarizeResponse:
    if request.articleId is not None:
        article = await get_article_text(request.articleId)
        source_text = _build_source_text(headline=article.headline, text=article.text)
    else:
        source_text = request.text or ""

    system_prompt = (
        "You are a news summarization assistant. "
        f"{LENGTH_INSTRUCTIONS[request.length]} "
        f"{STYLE_INSTRUCTIONS[request.style]}"
    )
    messages = [
        SystemMessage(content=system_prompt),
        HumanMessage(content=source_text),
    ]

    model = get_chat_model()
    try:
        result = model.invoke(messages)
    except Exception as exc:  # noqa: BLE001 - map provider failures to unified API errors
        raise UpstreamLLMError("Failed to generate summary") from exc

    summary = result.content if isinstance(result.content, str) else str(result.content)

    return SummarizeResponse(
        summary=summary,
        model=settings.llm_model,
        provider=settings.llm_provider,
    )
