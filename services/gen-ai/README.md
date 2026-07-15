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

### `POST /explain`

Simplified explanation of article text, tailored to a knowledge level.

**Request body:**

```json
{
  "articleId": "optional-article-id",
  "text": "optional raw text (exactly one of articleId or text required)",
  "knowledgeLevel": "child | beginner | intermediate | expert"
}
```

**Response:**

```json
{
  "explanation": "...",
  "knowledgeLevel": "beginner",
  "model": "openai/gpt-oss-120b",
  "provider": "logos"
}
```

### `POST /sentiment`

Sentiment and light political-bias analysis of article text.

**Request body:**

```json
{
  "articleId": "optional-article-id",
  "text": "optional raw text (exactly one of articleId or text required)"
}
```

**Response:**

```json
{
  "sentiment": "positive",
  "score": 0.75,
  "bias": "center",
  "rationale": "The tone is optimistic about policy outcomes.",
  "model": "openai/gpt-oss-120b",
  "provider": "logos"
}
```

`sentiment` is `positive`, `neutral`, or `negative`. `score` ranges from `-1.0` to `1.0`. `bias` is `left`, `center`, `right`, `unclear`, or `null`.

### `POST /qa`

Answer a question grounded in a single article's text (no RAG).

**Request body:**

```json
{
  "articleId": "optional-article-id",
  "text": "optional raw text (exactly one of articleId or text required)",
  "question": "What was discussed?"
}
```

**Response:**

```json
{
  "answer": "...",
  "model": "openai/gpt-oss-120b",
  "provider": "logos"
}
```

The model answers only from the provided article text; if the answer is not present, it says so.

## Error responses

All endpoints return a unified error body on failure:

```json
{
  "timestamp": "2026-07-07T12:00:00Z",
  "code": 502,
  "message": "Failed to reach content service",
  "details": [],
  "path": "/summarize"
}
```

Common status codes:

| Code | When |
|------|------|
| 400 | Validation failed, blank input, or fetched article has no content |
| 404 | Article ID not found in content-service |
| 502 | Upstream content-service or LLM provider failure |
| 500 | Unhandled internal error (e.g. misconfigured `LLM_PROVIDER`) |

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
| `LLM_PROVIDER` | LLM backend: `logos` (cloud) or `ollama` (local) | `logos` |
| `LLM_BASE_URL` | OpenAI-compatible base URL (Logos) | `https://logos.aet.cit.tum.de/v1` |
| `LLM_API_KEY` | Bearer token for Logos (`lg-...`; TUM network / eduVPN only) | *(empty — required for Logos calls)* |
| `LLM_MODEL` | Model identifier | `openai/gpt-oss-120b` |
| `LLM_TEMPERATURE` | Sampling temperature | `0.2` |
| `LLM_TIMEOUT_SECONDS` | Logos request timeout (seconds) | `60` |
| `OLLAMA_BASE_URL` | Ollama HTTP base URL (when `LLM_PROVIDER=ollama`) | `http://ollama:11434` |
| `INTERNAL_API_URL` | api-gateway base for content reads | `http://api-gateway:8080` |
| `LOG_LEVEL` | Logging level | `INFO` |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | OTLP HTTP collector URL (omit for offline/CI no-op) | *(unset locally; `http://grafana-lgtm:4318` in compose/helm/k8s)* |
| `OTEL_SERVICE_NAME` | OpenTelemetry service name | `gen-ai` |
| `OTEL_RESOURCE_ATTRIBUTES` | Extra resource attributes (`key=value,...`) | *(optional)* |

## Observability

When `OTEL_EXPORTER_OTLP_ENDPOINT` is set, the service exports:

- **Traces** — FastAPI auto-instrumentation (HTTP spans) via OTLP HTTP to the shared `grafana-lgtm` collector (Tempo).
- **Metrics** — custom LLM counters/histograms from `app/llm/invoke.py`:
  - `gen_ai.llm.requests` — invocation count (labels: `endpoint`, `provider`)
  - `gen_ai.llm.latency` — invocation duration in seconds
  - `gen_ai.llm.errors` — failed invocations
  - `gen_ai.llm.prompt_tokens` / `gen_ai.llm.completion_tokens` — when LangChain exposes token usage

If the OTLP endpoint is **unset** (local dev, CI), instrumentation is a no-op: the app starts and serves normally without contacting a collector.

## Running locally with Ollama

Use this path when off the TUM network (e.g. home dev, Azure VM) or for offline demos.

1. From the repo root, start the stack with the Ollama profile:

```bash
docker compose --env-file infra/.env \
  -f infra/docker-compose.yaml \
  -f infra/docker-compose.dev.yaml \
  --profile local-llm up --build
```

2. Pull a model into the Ollama container:

```bash
docker compose exec ollama ollama pull llama3.2
```

3. Set provider and model in `infra/.env`:

```bash
LLM_PROVIDER=ollama
LLM_MODEL=llama3.2
```

4. Restart gen-ai (or the whole stack) so it picks up the new env.

GenAI calls then route to the in-network Ollama service at `http://ollama:11434`.

## Tests

```bash
pip install -e ".[dev]" && pytest
```

All tests run offline with mocked LLM and content fetchers.
