# GenAI Service

Python microservice for AI-powered features: article summarization, explanations, sentiment analysis, and recommendation signals.

## Stack

- Python 3.12
- FastAPI
- LangChain
- Pydantic

## Local Development

### Prerequisites

- Python 3.12+
- pip

### Setup

```bash
cd services/gen-ai

# Create virtual environment
python -m venv .venv
source .venv/bin/activate  # On Windows: .venv\Scripts\activate

# Install dependencies
pip install -e ".[dev]"

# Configure environment
cp .env.example .env
# Edit .env with your LLM API key

# Run the service
uvicorn app.main:app --reload --reload-exclude .venv
```

The service will be available at http://localhost:8000

- Health check: http://localhost:8000/health
- API docs: http://localhost:8000/docs

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
| `LLM_PROVIDER` | LLM provider (`logos` for cloud, `ollama` for local) | `logos` |
| `LLM_BASE_URL` | Logos OpenAI-compatible endpoint | `https://logos.aet.cit.tum.de/v1` |
| `LLM_API_KEY` | Logos API key (`lg-...`; TUM network / eduVPN only) | - |
| `LLM_MODEL` | Model to use | `openai/gpt-oss-120b` |
| `INTERNAL_API_URL` | Gateway URL for fetching articles | `http://api-gateway:8080` |
| `OLLAMA_BASE_URL` | Ollama API URL (when `LLM_PROVIDER=ollama`) | `http://ollama:11434` |
| `LOG_LEVEL` | Logging level | `INFO` |

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
