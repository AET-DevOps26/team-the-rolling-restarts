from __future__ import annotations

from langchain_core.language_models.chat_models import BaseChatModel

from app.config import settings


def get_chat_model() -> BaseChatModel:
    if settings.llm_provider == "ollama":
        from langchain_ollama import ChatOllama

        return ChatOllama(
            base_url=settings.ollama_base_url,
            model=settings.llm_model,
            temperature=settings.llm_temperature,
        )

    if settings.llm_provider == "logos":
        from langchain_openai import ChatOpenAI

        return ChatOpenAI(
            base_url=settings.llm_base_url,
            api_key=settings.llm_api_key,
            model=settings.llm_model,
            temperature=settings.llm_temperature,
            timeout=settings.llm_timeout_seconds,
        )

    raise ValueError(f"Unknown LLM provider: {settings.llm_provider!r}")
