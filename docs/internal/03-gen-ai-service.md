# GenAI Service (`services/gen-ai/`)

Python, FastAPI + LangChain, port 8000. Managed with a local `.venv` (present
in the working tree — exclude it from any `find`/`grep` over this directory).

## Current state

- `app/main.py` — **only route is `/health`**, plus `startup`/`shutdown`
  event handlers. No summarization, Q&A, or any LangChain call is wired up
  yet, despite `services/gen-ai/README.md` describing those as the service's
  purpose.
- `app/config.py` — `pydantic_settings.BaseSettings` with `llm_provider`
  (default `"openai"`), `llm_api_key`, `llm_model` (default `"gpt-4o-mini"`).
  The field exists but **nothing branches on `llm_provider`** — no local
  model (GPT4All/LLaMA/Ollama) code path exists.
- `app/__init__.py` — empty
- No vector DB / RAG dependency anywhere (`pyproject.toml` has
  `langchain`, `langchain-openai`, `fastapi` — no `weaviate`/`chromadb`/`faiss`)

## Generated client

- `services/gen-ai/generated/personalised_news_aggregator_api_client/` — a
  full Python client generated from `api/openapi.yaml` via
  `openapi-python-client` (see `api/scripts/gen-all.sh` step 3). Covers
  articles, auth, sources, topics, users. **Exists but the service doesn't
  call it from anywhere yet** (no imports found in `app/`) — consistent with
  there being no real feature implemented.

## Tests

- `services/gen-ai/tests/` contains only `__init__.py` — no actual pytest
  files. CI (`.github/workflows/ci.yml`) explicitly tolerates pytest's "no
  tests collected" exit code 5 as a pass, so this doesn't fail the pipeline.

## Re-verify

```
grep -n "@app\.\|APIRouter" services/gen-ai/app/main.py
find services/gen-ai -name "*.py" -not -path "*/.venv/*" -not -path "*/generated/*"
find services/gen-ai/tests -name "test_*.py"
grep -n "gpt4all\|llama\|ollama" -ri services/gen-ai --include="*.py"
```
