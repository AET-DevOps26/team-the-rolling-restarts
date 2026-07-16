FROM python:3.12-slim@sha256:c3d81d25b3154142b0b42eb1e61300024426268edeb5b5a26dd7ddf64d9daf28

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
# USER is the numeric UID, not the name: Kubernetes' runAsNonRoot admission check can't resolve
# a named user against the image's /etc/passwd (it deliberately doesn't inspect image contents)
# and refuses to start the container with "cannot verify user is non-root" — confirmed live.
RUN useradd --no-create-home --uid 1000 appuser
USER 1000

EXPOSE 8000

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
