#!/usr/bin/env bash
set -euo pipefail

openapi-generator-cli generate -i api/openapi.yaml -g spring -o services/spring-api/generated

openapi-python-client generate --path api/openapi.yaml --output-path services/gen-ai/generated --overwrite

npx openapi-typescript api/openapi.yaml -o web-client/src/generated/api.ts