#!/usr/bin/env bash
set -euo pipefail

# Code-first OpenAPI pipeline.
#
# The Spring services are the source of truth. springdoc derives an OpenAPI document from each
# service's controllers (exported by OpenApiDocGenerationTest), which we merge into the single
# public contract api/openapi.yaml, then generate the downstream CONSUMER clients from it:
#   - Python client for the gen-ai service
#   - TypeScript types for the web client
#
# No server stubs are generated from the spec (that would invert the direction); the spec is an
# output of the code, not an input to it.
#
# Run from the repository root.

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repo_root"

# 1. Export each service's spec from springdoc (cleanTest forces a fresh dump; no live DB needed).
(cd services/spring && ./gradlew \
  :user-service:cleanTest :user-service:test \
  :content-service:cleanTest :content-service:test \
  --tests "*OpenApiDocGenerationTest")

# 2. Merge the service specs into the public contract, applying the gateway route prefixes.
python3 api/scripts/merge-openapi.py --output api/openapi.yaml \
  services/spring/user-service/build/openapi/user-service.json:/api/users \
  services/spring/content-service/build/openapi/content-service.json:/api/content

# 3. Generate consumer clients from the produced contract.
#    Clean first so a renamed package (driven by the spec title) doesn't leave stale dirs.
rm -rf services/gen-ai/generated
openapi-python-client generate --path api/openapi.yaml \
  --output-path services/gen-ai/generated --overwrite

npx openapi-typescript api/openapi.yaml -o web-client/src/generated/api.ts
