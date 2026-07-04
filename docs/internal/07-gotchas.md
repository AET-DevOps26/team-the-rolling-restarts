# Gotchas — Read This First

Non-obvious things that file names alone don't tell you. Each has cost real
investigation time once already; re-verify before repeating that work.

## Frontend is entirely mock-data-driven

Every `web-client` page (dashboard, feed, saved, settings, article) renders
from `web-client/src/lib/mock/*`. A generated API client exists
(`web-client/src/generated/api.ts`, produced from `api/openapi.yaml`) but
**nothing in `src/` imports it**, and there's no `fetch`/`axios`/
`NEXT_PUBLIC_API` usage anywhere. The client and backend are not integrated
yet, despite the UI looking complete. See
[02-web-client.md](02-web-client.md).

```sh
grep -rln "generated/api\|fetch(\|axios\|NEXT_PUBLIC_API" web-client/src --include="*.tsx" --include="*.ts"
```

## GenAI service is a shell — only `/health` exists

`services/gen-ai/app/main.py` has no summarization/Q&A/generation endpoint.
`llm_provider` config field exists but nothing branches on it — no local
model path, no RAG. Don't assume the README's description of the service
reflects what's implemented. See [03-gen-ai-service.md](03-gen-ai-service.md).

## `services/spring-api/` is dead local clutter, not a 4th service

Gitignored (`**/generated` pattern), untracked, contains
`openapi-generator`'s generic default templates (`DummyApi`, `TestApi`).
Left over from an experiment with server-stub generation that the real
pipeline (`api/scripts/gen-all.sh`) explicitly avoids ("no server stubs are
generated from the spec"). Ignore it; don't count it as a microservice or
investigate it as real code.

## `web-client`'s pact/contract test setup is dead config

`web-client/jest.pact.config.js` exists but `jest`/`ts-jest` aren't in
`package.json` and no `tests/pact/` directory exists. Similarly, CI's
`contract-test` job in `ci.yml` only checks for `./scripts/run-pact.sh`,
which doesn't exist — the job always no-ops and passes. **There is currently
no real consumer-driven contract testing**, despite the scaffolding
suggesting there is. See [05-ci-cd-workflows.md](05-ci-cd-workflows.md).

## Only 2 of the required ≥3 Spring microservices count

`api-gateway` is routing/JWT-validation infrastructure, not a
business-domain service — see `docs/requirements/STATUS.md` §03 for the
grading angle, and [01-spring-services.md](01-spring-services.md) for the
technical breakdown.

## Observability uses OTel + `grafana-lgtm`, not standalone Prometheus

Functionally similar (Mimir inside LGTM is Prometheus-compatible) but the
course brief's letter says "Prometheus must be used." See
[06-observability.md](06-observability.md).

## Team is 2 people, not 3

Third member left the project. See `.github/CODEOWNERS` for current
ownership: `brscn2` (web-client + gen-ai), `YRC99` (services/spring + gen-ai).

## Root README is much thinner than the actual documentation

Substantial docs exist under `docs/source/` (OpenAPI workflow, deployment
testing, secrets reference, Azure/K8s CD pipelines, per-infra READMEs), but
none of it is linked from or summarized in the root `README.md` — the first
thing anyone opens. Quick-start, CI/CD, and monitoring instructions currently
only live in `docs/source/index.md` and the `Makefile`.

## Deployed instance exists in config but reachability is unverified

`infra/helm/values.yaml` configures ingress host
`rolling-restarts.stud.k8s.aet.cit.tum.de` (the course's Rancher-managed
cluster). This sandbox has no network egress, so its actual live status
can't be confirmed from here — check manually before relying on "there's a
working deployed instance" as a grading-readiness assumption.
