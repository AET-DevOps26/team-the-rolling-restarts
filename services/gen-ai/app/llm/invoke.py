from __future__ import annotations

import time
from collections.abc import Callable
from typing import Any

from langchain_core.messages import BaseMessage
from opentelemetry import metrics, trace

_meter = metrics.get_meter("gen-ai.llm")
_tracer = trace.get_tracer("gen-ai.llm")

_llm_requests = _meter.create_counter(
    "gen_ai.llm.requests",
    description="Number of LLM invocations",
)
_llm_latency = _meter.create_histogram(
    "gen_ai.llm.latency",
    unit="s",
    description="LLM invocation latency in seconds",
)
_llm_errors = _meter.create_counter(
    "gen_ai.llm.errors",
    description="Number of failed LLM invocations",
)
_prompt_tokens = _meter.create_counter(
    "gen_ai.llm.prompt_tokens",
    description="Prompt tokens consumed by LLM responses",
)
_completion_tokens = _meter.create_counter(
    "gen_ai.llm.completion_tokens",
    description="Completion tokens produced by LLM responses",
)


def _record_token_usage(result: Any, attributes: dict[str, str]) -> None:
    usage = getattr(result, "usage_metadata", None)
    if isinstance(usage, dict):
        prompt_tokens = usage.get("input_tokens") or usage.get("prompt_tokens")
        completion_tokens = usage.get("output_tokens") or usage.get("completion_tokens")
    else:
        response_metadata = getattr(result, "response_metadata", None)
        token_usage = response_metadata.get("token_usage", {}) if isinstance(response_metadata, dict) else {}
        prompt_tokens = token_usage.get("prompt_tokens") or token_usage.get("input_tokens")
        completion_tokens = token_usage.get("completion_tokens") or token_usage.get("output_tokens")

    if isinstance(prompt_tokens, int) and prompt_tokens > 0:
        _prompt_tokens.add(prompt_tokens, attributes)
    if isinstance(completion_tokens, int) and completion_tokens > 0:
        _completion_tokens.add(completion_tokens, attributes)


def invoke_chat_model(
    model: Any,
    messages: list[BaseMessage],
    *,
    endpoint: str,
    provider: str,
    invoke_fn: Callable[[], Any] | None = None,
    count_errors: bool = True,
) -> Any:
    """Invoke a chat model (or a structured-output-wrapped runnable) with shared
    observability for all LLM-backed endpoints. `model` only needs to support
    `.invoke(messages)`.

    `invoke_fn`, if given, is called instead of `model.invoke(messages)` — for callers whose
    actual invocation isn't a plain `model.invoke(messages)` call (e.g. sentiment's structured-
    output attempt, which calls `structured.invoke(messages)` on a wrapped runnable). `model`/
    `messages` are still required in that case, since they're unused for the call itself but
    nothing else about this function's observability behavior changes.

    `count_errors=False` skips the `gen_ai.llm.errors` increment on failure — for callers
    where this specific invocation is an anticipated first attempt with its own fallback (e.g.
    sentiment's structured-output attempt, expected to sometimes fail on providers without full
    structured-output support), so a routine fallback doesn't inflate the error rate metric.
    Latency/request counters and tracing are unaffected either way.
    """
    attributes = {"endpoint": endpoint, "provider": provider}
    start = time.perf_counter()
    result: Any

    with _tracer.start_as_current_span(
        "llm.invoke",
        attributes={"gen_ai.endpoint": endpoint, "gen_ai.provider": provider},
    ):
        try:
            result = invoke_fn() if invoke_fn is not None else model.invoke(messages)
        except Exception:
            if count_errors:
                _llm_errors.add(1, attributes)
            raise
        finally:
            elapsed = time.perf_counter() - start
            _llm_latency.record(elapsed, attributes)
            _llm_requests.add(1, attributes)

    _record_token_usage(result, attributes)
    return result
