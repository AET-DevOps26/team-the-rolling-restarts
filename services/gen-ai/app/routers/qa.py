from __future__ import annotations

from fastapi import APIRouter
from langchain_core.messages import HumanMessage, SystemMessage

from app.config import settings
from app.errors import UpstreamLLMError
from app.llm.invoke import invoke_chat_model
from app.llm.provider import get_chat_model
from app.schemas import QaRequest, QaResponse
from app.services.content import get_article_text
from app.services.source_text import build_source_text, require_processable_source

router = APIRouter()


@router.post("/qa", response_model=QaResponse)
async def qa(request: QaRequest) -> QaResponse:
    if request.articleId is not None:
        article = await get_article_text(request.articleId)
        source_text = build_source_text(headline=article.headline, text=article.text)
    else:
        source_text = request.text or ""

    require_processable_source(source_text)

    system_prompt = (
        "You are a question-answering assistant. Answer the user's question using "
        "ONLY the provided article text. If the answer is not present in the text, "
        "say clearly that the article does not contain enough information to answer. "
        "Do not use outside knowledge or invent facts."
    )
    messages = [
        SystemMessage(content=system_prompt),
        HumanMessage(content=f"Article:\n{source_text}\n\nQuestion: {request.question.strip()}"),
    ]

    model = get_chat_model()
    try:
        result = invoke_chat_model(
            model,
            messages,
            endpoint="/qa",
            provider=settings.llm_provider,
        )
    except Exception as exc:  # noqa: BLE001 - map provider failures to unified API errors
        raise UpstreamLLMError("Failed to generate answer") from exc

    answer = result.content if isinstance(result.content, str) else str(result.content)

    return QaResponse(
        answer=answer,
        model=settings.llm_model,
        provider=settings.llm_provider,
    )
