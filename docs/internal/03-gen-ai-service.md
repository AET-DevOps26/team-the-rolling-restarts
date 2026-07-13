# GenAI Service (`services/gen-ai/`)

Python, FastAPI + LangChain, port 8000. Managed with a local `.venv` (present
in the working tree — exclude it from any `find`/`grep` over this directory).

## Current state

- `app/main.py` — `/health` plus `POST /summarize`, `/explain`, `/sentiment`, `/qa`
  (routers at service root; gateway exposes `/api/ai/*`). Lifespan handler for
  startup/shutdown. Unified error handlers registered
  (`{timestamp, code, message, details, path}`).
- `app/config.py` — Logos defaults (`llm_provider=logos`, `openai/gpt-oss-120b`,
  Logos base URL, Ollama URL, `internal_api_url` for gateway content reads).
- `app/llm/provider.py` — `get_chat_model()` factory branches on `logos` vs
  `ollama` (no network at import time).
- `app/services/content.py` — `get_article_text()` fetches articles via httpx
  from `{INTERNAL_API_URL}/api/content/articles/{id}`; 404 → `ArticleNotFoundError`,
  other non-2xx or network failures → `UpstreamServiceError` (502).
- `app/routers/summarize.py` — summarization endpoint with length/style prompts.
- `app/routers/explain.py` — simplified explanations by knowledge level.
- `app/routers/sentiment.py` — sentiment + bias via structured output (JSON fallback).
- `app/routers/qa.py` — single-article grounded Q&A.
- `tests/` — `test_health.py`, `test_summarize.py`, `test_explain.py`,
  `test_sentiment.py`, `test_qa.py`, `test_content.py`, `test_errors.py`
  (offline, mocked LLM + content).

## Generated client

- `services/gen-ai/generated/personalised_news_aggregator_api_client/` — optional
  Python client from `api/openapi.yaml` via `./api/scripts/gen-all.sh`. Not
  committed; content fetch currently uses httpx against the gateway (see
  `app/services/content.py`).

## Re-verify

```sh
grep -n "@app\.\|APIRouter\|include_router" services/gen-ai/app/main.py
find services/gen-ai -name "*.py" -not -path "*/.venv/*" -not -path "*/generated/*"
find services/gen-ai/tests -name "test_*.py"
grep -n "get_chat_model\|ollama\|logos" -ri services/gen-ai/app --include="*.py"
cd services/gen-ai && pip install -e ".[dev]" && pytest
```
