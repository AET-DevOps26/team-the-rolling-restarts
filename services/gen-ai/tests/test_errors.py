from __future__ import annotations

from fastapi import FastAPI
from fastapi.testclient import TestClient

from app.errors import register_exception_handlers


def test_catch_all_handler_returns_unified_error_schema() -> None:
    app = FastAPI()
    register_exception_handlers(app)

    @app.get("/boom")
    async def boom() -> None:
        raise RuntimeError("unexpected failure")

    client = TestClient(app, raise_server_exceptions=False)
    response = client.get("/boom")

    assert response.status_code == 500
    body = response.json()
    assert body["code"] == 500
    assert body["message"] == "Internal server error"
    assert body["details"] == []
    assert body["path"] == "/boom"
    assert body["timestamp"].endswith("Z")
