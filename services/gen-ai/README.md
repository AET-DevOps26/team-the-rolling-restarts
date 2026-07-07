# GenAI Service

Python microservice for AI-powered features: article summarization, explanations, sentiment analysis, and recommendation signals.

## Stack

- Python 3.12
- FastAPI
- LangChain (Logos cloud + Ollama local)
- Pydantic

## Local Development

### Prerequisites

- Python 3.12+
- pip

### Setup

```bash
cd services/gen-ai

# Create virtual environment
python3.12 -m venv .venv
source .venv/bin/activate  # On Windows: .venv\Scripts\activate

# Install dependencies
pip install -e ".[dev]"

# Configure environment
cp .env.example .env
# Edit .env — set LLM_API_KEY when using Logos (TUM network / eduVPN only)

# Run the service
uvicorn app.main:app --reload --reload-exclude .venv
```

The service will be available at http://localhost:8000

- Health check: http://localhost:8000/health
- API docs: http://localhost:8000/docs

## API

Routes are defined at the **service root** (e.g. `/summarize`). The api-gateway exposes them under `/api/ai/*` with `StripPrefix=2`, so clients call `POST /api/ai/summarize`.

### `POST /summarize`

Summarize article text or a stored article by ID.

**Request body:**

```json
{
  "articleId": "optional-article-id",
  "text": "optional raw text (exactly one of articleId or text required)",
  "length": "short | medium | long",
  "style": "plain | bullets"
}
```

**Response:**

```json
{
  "summary": "...",
  "model": "openai/gpt-oss-120b",
  "provider": "logos"
}
```

When `articleId` is provided, the service fetches headline and body paragraphs from content-service via the api-gateway (`GET /api/content/articles/{id}` — public, no auth).

## LLM providers

Set `LLM_PROVIDER` to switch backends:

| Provider | Use case | Backend |
|----------|----------|---------|
| `logos` (default) | Course cloud LLM on TUM network / eduVPN | OpenAI-compatible `https://logos.aet.cit.tum.de/v1`, model `openai/gpt-oss-120b` |
| `ollama` | Local / off-campus (e.g. Azure VM) | Ollama at `OLLAMA_BASE_URL` |

Logos requires `LLM_API_KEY` (`lg-...` key). It is **not reachable from CI or off-campus** — tests mock the model.

## Docker

### Build

```bash
docker build -t gen-ai-service .
```

### Run

```bash
docker run -p 8000:8000 --env-file .env gen-ai-service
```

## Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `LLM_PROVIDER` | LLM backend: `logos` or `ollama` | `logos` |
| `LLM_BASE_URL` | OpenAI-compatible base URL (Logos) | `https://logos.aet.cit.tum.de/v1` |
| `LLM_API_KEY` | Bearer token for Logos (`lg-...`) | *(empty — required for Logos calls)* |
| `LLM_MODEL` | Model identifier | `openai/gpt-oss-120b` |
| `LLM_TEMPERATURE` | Sampling temperature | `0.2` |
| `LLM_TIMEOUT_SECONDS` | Logos request timeout (seconds) | `60` |
| `OLLAMA_BASE_URL` | Ollama HTTP base URL | `http://ollama:11434` |
| `INTERNAL_API_URL` | api-gateway base for content reads | `http://api-gateway:8080` |
| `LOG_LEVEL` | Logging level | `INFO` |

## Tests

```bash
pip install -e ".[dev]" && pytest
```

All tests run offline with mocked LLM and content fetchers.
