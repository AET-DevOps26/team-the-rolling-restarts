from __future__ import annotations

import logging
from datetime import UTC, datetime
from typing import Any

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

logger = logging.getLogger(__name__)


class AppError(Exception):
    def __init__(
        self,
        message: str,
        code: int,
        details: list[str] | None = None,
    ) -> None:
        super().__init__(message)
        self.message = message
        self.code = code
        self.details = details or []


class BadRequestError(AppError):
    def __init__(self, message: str, details: list[str] | None = None) -> None:
        super().__init__(message, 400, details)


class NotFoundError(AppError):
    def __init__(self, message: str, details: list[str] | None = None) -> None:
        super().__init__(message, 404, details)


class ArticleNotFoundError(NotFoundError):
    pass


class UpstreamLLMError(AppError):
    def __init__(self, message: str, details: list[str] | None = None) -> None:
        super().__init__(message, 502, details)


class UpstreamServiceError(AppError):
    def __init__(self, message: str, details: list[str] | None = None) -> None:
        super().__init__(message, 502, details)


def build_error_body(
    *,
    code: int,
    message: str,
    path: str,
    details: list[str] | None = None,
) -> dict[str, Any]:
    return {
        "timestamp": datetime.now(UTC).isoformat().replace("+00:00", "Z"),
        "code": code,
        "message": message,
        "details": details or [],
        "path": path,
    }


def register_exception_handlers(app: FastAPI) -> None:
    @app.exception_handler(AppError)
    async def handle_app_error(request: Request, exc: AppError) -> JSONResponse:
        return JSONResponse(
            status_code=exc.code,
            content=build_error_body(
                code=exc.code,
                message=exc.message,
                path=request.url.path,
                details=exc.details,
            ),
        )

    @app.exception_handler(RequestValidationError)
    async def handle_validation_error(
        request: Request,
        exc: RequestValidationError,
    ) -> JSONResponse:
        details = [
            f"{'.'.join(str(part) for part in error.get('loc', ()))}: {error.get('msg', 'Invalid value')}"
            for error in exc.errors()
        ]
        return JSONResponse(
            status_code=400,
            content=build_error_body(
                code=400,
                message="Validation failed",
                path=request.url.path,
                details=details,
            ),
        )

    @app.exception_handler(Exception)
    async def handle_unhandled_exception(request: Request, exc: Exception) -> JSONResponse:
        logger.exception("Unhandled error on %s", request.url.path)
        return JSONResponse(
            status_code=500,
            content=build_error_body(
                code=500,
                message="Internal server error",
                path=request.url.path,
            ),
        )
