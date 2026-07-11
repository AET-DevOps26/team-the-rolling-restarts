import logging
from contextlib import asynccontextmanager
from typing import AsyncIterator

from fastapi import FastAPI

from app.config import settings
from app.errors import register_exception_handlers
from app.observability import setup_observability
from prometheus_client import Gauge
from prometheus_fastapi_instrumentator import Instrumentator

from app.routers import explain, qa, sentiment, summarize

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
app.include_router(summarize.router)
app.include_router(explain.router)
app.include_router(sentiment.router)
app.include_router(qa.router)


@app.get("/health")
async def health_check() -> dict[str, str]:
    return {"status": "ok"}
