import os

# Ensure tests never require a running OTLP collector (app.main configures OTel at import).
os.environ.pop("OTEL_EXPORTER_OTLP_ENDPOINT", None)
