#!/usr/bin/env bash
set -euo pipefail

openapi-generator-cli generate -i api/openapi.yaml -g spring \
  -o services/server/generated --skip-validate-spec

openapi-python-client --path api/openapi.yaml \
  --output services/py-recommender/client --config api/scripts/py-config.json

npx openapi-typescript api/openapi.yaml -o web-client/src/api.ts