import logging
from contextlib import asynccontextmanager
from typing import AsyncIterator

from fastapi import FastAPI

from app.config import settings
from app.errors import register_exception_handlers
from app.observability import setup_observability
from prometheus_client import Gauge
from prometheus_fastapi_instrumentator import Instrumentator

logging.basicConfig(
    level=settings.log_level,
    format="%(asctime)s - %(name)s - %(levelname)s - %(message)s",
)
logger = logging.getLogger(__name__)

# Static gauge (always 1, version as a label) for correlating metric/behavior changes with a
# specific release — mirrors the Spring services' app_build_info metric (BuildInfoMetrics.java).
Gauge("app_build_info", "Deployed build version", ["service", "version"]).labels(
    service=settings.app_name, version=settings.app_version
).set(1)


@asynccontextmanager
async def lifespan(_app: FastAPI) -> AsyncIterator[None]:
    logger.info("Starting %s v%s", settings.app_name, settings.app_version)
    yield
    logger.info("Shutting down %s", settings.app_name)


app = FastAPI(
    title=settings.app_name,
    version=settings.app_version,
    description="GenAI microservice for article summarization, explanations, and sentiment analysis",
    lifespan=lifespan,
)

register_exception_handlers(app)
setup_observability(app)
Instrumentator().instrument(app).expose(app)

# Imported here, not at module top level: each router transitively imports app/llm/invoke.py,
# which creates its OTel tracer/meter and all its instruments (counters, histogram) at import
# time. Importing them before setup_observability() runs bound those instruments to
# OpenTelemetry's default no-op providers instead of the real ones configured above — confirmed
# live, this is exactly why gen_ai_llm_requests_total (and the other custom counters/histogram)
# never reached Prometheus no matter how long you waited or how many LLM calls were made; the
# GenAI Overview dashboard read empty as a result. Moving the import here fixes the metrics.
# NOTE: the custom "llm.invoke" *span* from the same module still doesn't appear in traces even
# after this fix (FastAPI's own auto-instrumented spans for the same request do) — a separate,
# not-yet-understood gap, tracked but not blocking since the dashboard reads metrics, not traces.
from app.routers import explain, qa, sentiment, summarize  # noqa: E402

app.include_router(summarize.router)
app.include_router(explain.router)
app.include_router(sentiment.router)
app.include_router(qa.router)


@app.get("/health")
async def health_check() -> dict[str, str]:
    return {"status": "ok"}
