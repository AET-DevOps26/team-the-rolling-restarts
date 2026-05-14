#!/usr/bin/env bash
set -euo pipefail

openapi-generator-cli generate -i api/openapi.yaml -g spring \
  -o services/spring-api/generated 

#openapi-python-client generate --path api/openapi.yaml \
#  --output-path services/py-recommender/client \
#  --config api/scripts/py-config.json

#npx openapi-typescript api/openapi.yaml -o web-client/src/api.ts

npx @redocly/cli lint api/openapi.yaml
#npx prism mock api/openapi.yaml