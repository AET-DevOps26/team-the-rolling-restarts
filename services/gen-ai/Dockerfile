FROM python:3.12-slim

WORKDIR /app

COPY pyproject.toml README.md ./
RUN python -c 'import subprocess, tomllib; deps = tomllib.load(open("pyproject.toml","rb"))["project"]["dependencies"]; subprocess.check_call(["python","-m","pip","install","--no-cache-dir", *deps])'

# Copy the build context: the app sources, plus the generated OpenAPI client when it has been
# pre-generated into ./generated (by CI, the image workflow, or `make generate`). The client is
# installed only when present, so a bare local build without it still succeeds — gen-ai uses the
# generated client for typed cross-service calls.
COPY . .
RUN if [ -d generated ]; then pip install --no-cache-dir ./generated; fi
RUN pip install --no-cache-dir --no-deps .

# python:3.12-slim defaults to root (Dockle CIS-DI-0001); the app never writes to the filesystem
# (no local files/logs/cache — everything goes to stdout), so a plain non-root user is enough.
RUN useradd --no-create-home --uid 1000 appuser
USER appuser

EXPOSE 8000

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
