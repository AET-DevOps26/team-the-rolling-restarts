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
  `ollama` (no network at import time). **Only these two values are handled** — anything else
  (e.g. `LLM_PROVIDER=openai`) raises `ValueError` rather than falling back to anything; see
  [07-gotchas.md](07-gotchas.md). `logos` is TUM-network-only — reachable from the Kubernetes
  cluster, not from the Azure VM.
- `app/observability.py` — OTel traces + custom `gen_ai_llm_*` metrics (requests, errors, latency,
  prompt/completion tokens), tagged by `endpoint`/`provider`; see
  `docs/internal/06-observability.md` for the exact metric names and the "GenAI Overview" Grafana
  dashboard built from them.
- `app/services/content.py` — `get_article_text()` fetches articles via httpx
  from `{INTERNAL_API_URL}/api/content/articles/{id}`; 404 → `ArticleNotFoundError`.
- `app/routers/summarize.py` — summarization endpoint with length/style prompts.
- `app/routers/explain.py` — simplified explanations by knowledge level.
- `app/routers/sentiment.py` — sentiment + bias via structured output (JSON fallback).
- `app/routers/qa.py` — single-article grounded Q&A.
- `tests/` — `test_health.py`, `test_summarize.py`, `test_explain.py`,
  `test_sentiment.py`, `test_qa.py` (offline, mocked LLM + content).

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
