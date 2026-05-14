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
| `LLM_PROVIDER` | LLM provider (openai, anthropic) | `openai` |
| `LLM_API_KEY` | API key for the LLM provider | - |
| `LLM_MODEL` | Model to use | `gpt-4o-mini` |
| `LOG_LEVEL` | Logging level | `INFO` |
