# Requirements Status Checklist

> **Internal — not for publication.** Lives outside `docs/source/` on purpose
> so MkDocs never publishes it (see `docs/source/mkdocs.yml`'s `docs_dir`).

Tracks how the current codebase measures up against the course requirements in
this folder (`00`–`11`). Status is based on what's actually in the repo, not on
intent — re-verify before relying on this for grading prep, since it will drift
as work continues.

Legend: ✅ Done · ⚠️ Partial · ❌ Missing

## 03 — System Architecture

- ✅ Client/server/DB/GenAI separation — `web-client/`, `services/spring/*`, MongoDB, `services/gen-ai/`
- ❌ ≥3 Spring microservices with distinct responsibilities — only **2 qualify**: `user-service` (auth/profiles), `content-service` (RSS/articles). `api-gateway` is routing/JWT-validation infrastructure, not a business-domain service, so it doesn't count toward the "≥3 microservices" requirement. A third service is needed — see decision below.
- ✅ Documented API contract — `api/openapi.yaml`, generated code-first (`docs/source/openapi-workflow.md`)
- ⚠️ Documented DB schema — MongoDB is schemaless; no dedicated schema doc beyond entity classes, no migration tool

## 04 — GenAI Component

- ✅ Separate Python service, containerised — `services/gen-ai/` (FastAPI, LangChain), own `Dockerfile`
- ✅ Real user-facing use case — `POST /summarize` implemented (gateway: `/api/ai/summarize`); fetches article text from content-service when `articleId` is supplied; **web client article page** exposes Summary / Explain / Sentiment / Q&A widgets calling `/api/ai/*` via server actions (end-to-end user flow once explain/sentiment/qa land in gen-ai)
- ⚠️ Gateway exposes `/api/ai/**` publicly (`permitAll` in `SecurityConfig`) so the web client can call GenAI without JWT; Swagger UI aggregates gen-ai's `/openapi.json` at `/api/ai/openapi.json`
- ⚠️ Cloud + local model support — Logos cloud + Ollama local wired via env across compose/helm/k8s (`LLM_PROVIDER=logos|ollama`, compose profile `local-llm`); provider factory (`get_chat_model()`) in gen-ai (PR1)
- ❌ RAG / vector DB (optional bonus) — not started

## 05 — Environment & Deployment

- ✅ Every component has its own Dockerfile — `services/spring/{api-gateway,user-service,content-service}/Dockerfile`, `services/gen-ai/Dockerfile`, `web-client/Dockerfile`
- ✅ Database via official image (no custom Dockerfile needed) — `mongo:8` in `infra/docker-compose.yaml`
- ✅ `docker-compose.yaml` + `docker-compose.dev.yaml` wire up all components end-to-end
- ✅ ≤3-command local setup with sane defaults — `cp infra/.env.example infra/.env` + `make compose-up` (documented in `docs/source/index.md`; **not yet mirrored in root `README.md`**)
- ✅ Kubernetes deployable via Helm — `infra/helm/` real chart (`Chart.yaml` + `templates/`)
- ✅ Kubernetes deployable via raw manifests too — `infra/k8s/deployments/*.yml`, `infra/k8s/services/*.yml`
- ✅ Cloud environment (Azure) — `infra/terraform/azure-vm/`, `infra/ansible/`, `.github/workflows/deploy-azure.yml`
- ✅ Course infrastructure (Rancher) — Rancher is the course's managed distribution of the same standard Kubernetes API, so the existing `infra/helm/` chart and `infra/k8s/` manifests already satisfy this path; no Rancher-specific config is needed unless the course cluster requires something bespoke (e.g. a specific ingress class or a Rancher project namespace) — confirm that against the course's cluster docs
- ✅ No hardcoded secrets in production profiles — `application-production.properties` (all 3 Spring services) carry no credentials; Helm `values.yaml` references K8s Secrets
- ⚠️ Dev-only secrets committed in plaintext — `infra/docker-compose.dev.yaml` has a real RSA JWT keypair; `application-dev.properties` hardcodes `mongodb://root:secret@...` (acceptable as dev-only per `CLAUDE.md` convention, but worth rotating if this repo is ever made public)

## 06 — CI/CD

- ✅ GitHub Actions CI on every PR — `.github/workflows/ci.yml`: builds + tests all 5 services (Gradle, pytest, npm), plus OpenAPI contract-drift check, Terraform validate, Helm lint, contract tests
- ⚠️ Static analysis/linting — CI runs ESLint (web-client), Helm lint, and OpenAPI contract lint, but there is still no explicit Java linter (Checkstyle/Spotless) or Python linter (ruff/flake8) job — not yet full "static analysis" coverage across all services
- ✅ Local security/quality scanning — `make security-scan` (`infra/scripts/security-scan.sh`) runs gitleaks, hadolint, kics, zizmor, typos, npm audit, a CODEOWNERS coverage check, and trivy/dockle against locally-built images, in parallel, writing SARIF 2.1.0 to `output/`; `make score` views results in the `guestlecture` TUI. Local/manual only — not invoked from any `.github/workflows/*.yml`, so it doesn't close the CI static-analysis gap above. See `docs/source/security-scanning.md`.
- ⚠️ Pre-commit/pre-push secret scanning — shared `infra/scripts/gitleaks-check.sh` blocks commits (staged-diff scan) and pushes (scans every commit range being pushed) that introduce secrets, same tool/config as above. Only active once a contributor runs `pre-commit install` + `make install-hooks`; nothing enforces that on a fresh clone, and both are bypassable with `--no-verify` — so it's a local guard rail, not a guarantee.
- ✅ Auto-build & push images — `.github/workflows/upload_images.yml` (multi-arch, GHCR)
- ✅ Auto-deploy to Kubernetes on merge to main — `.github/workflows/deploy_kubernetes.yml` (triggers on the build workflow completing for `main`, runs `helm upgrade --install`)
- ✅ Secrets used via GitHub Actions secrets, not hardcoded — confirmed in deploy workflows
- ✅ Docs auto-published — `.github/workflows/publish_docs.yml` (PlantUML + MkDocs + Redoc → GitHub Pages)

## 07 — Observability

- ⚠️ Metrics collection — implemented via **OpenTelemetry + `grafana-lgtm`** (all-in-one Grafana/Loki/Tempo/Mimir image), not a standalone Prometheus deployment; Spring services depend on `spring-boot-starter-opentelemetry`; **this differs from the letter of the requirement ("Prometheus must be used") even though it satisfies the spirit** (request count/latency/error rate should be derivable from OTel metrics in Mimir/Prometheus-compatible storage) — worth flagging with the tutor if in doubt
- ❌ GenAI service instrumented — no metrics/tracing instrumentation found in `services/gen-ai`
- ❌ Grafana dashboards exported as `.json` — none found in the repo
- ❌ Alert rules — none found (no Prometheus alerting rules, no Grafana alert YAML/JSON)

## 08 — Testing

- ✅ Spring unit tests, real assertions, run in CI — api-gateway (5 files, e.g. `SubscriberScopeTest.java`), user-service (6 files, e.g. `AuthControllerTest.java`), content-service (7 files, e.g. `ArticleControllerTest.java`)
- ✅ GenAI unit tests — `services/gen-ai/tests/test_health.py`, `test_summarize.py` (offline mocked pytest)
- ❌ Client-side tests — no `*.test.tsx`/`__tests__`, no test runner configured; `web-client/package.json` has no `test` script

## 09 — Engineering Artefacts

- ✅ Subsystem Decomposition diagram — `docs/source/diagrams/architecture-component-diagram.puml` (+ rendered PNG)
- ✅ Use Case diagram — `docs/source/diagrams/use-case.puml` (+ rendered PNG)
- ✅ Analysis Object Model — `docs/source/diagrams/analysis-object-model.puml` (+ rendered PNG)
- ✅ OpenAPI/Swagger UI exposed — `springdoc-openapi-starter-webmvc-ui` in all 3 Spring services; api-gateway aggregates all specs via `springdoc.swagger-ui.urls[...]`

## 10 — Deliverables (README completeness)

- ✅ Architecture info in root `README.md` (service table + project layout)
- ⚠️ Setup/quick-start instructions — exist in `docs/source/index.md` and the `Makefile`, but are **not surfaced in root `README.md`** itself
- ❌ CI/CD instructions in README — not present (exists implicitly in workflow files only)
- ❌ Monitoring instructions in README — not present
- ❌ Student/contributor responsibilities section in README — team ownership rules live in `docs/requirements/01-team-and-collaboration.md` but aren't reflected in `README.md`

## Team & Process (01, 02) — not verifiable from code

Registration data, PR/review discipline, and Artemis-channel communication are
process requirements tied to how the team actually works, not to what's in the
tree at a point in time. Check these against actual GitHub PR history / Artemis
activity rather than this file.

---

## Where the biggest gaps are right now

1. **Only 2 of the required ≥3 Spring microservices count** — `api-gateway`
   doesn't qualify as a business-domain microservice. Decision: add a third
   service, **Interaction/Bookmarks** (owns view/bookmark/ignore events; exposes
   `POST /interactions`, `GET /users/{id}/bookmarks`, `GET /users/{id}/history`).
   Chosen over a Notification or Recommendation service because it's
   self-contained (no changes needed to existing services), maps directly to
   problem-statement bullets ("tracks user interactions", "bookmarking") that
   currently have zero implementation, and is the cheapest legitimate option
   given the approaching deadline. Not yet scaffolded.
2. **GenAI secondary endpoints** — `/summarize` works; `/explain`, `/sentiment`, `/qa`
   still pending gen-ai PR2. The web client widgets are wired and ready.
3. **No Prometheus/Grafana dashboards or alerts** — OTel + `grafana-lgtm` gives a
   plausible metrics backbone, but the *deliverables* (exported dashboard `.json`,
   an alert rule) don't exist yet.
4. **No client tests, no GenAI tests** — Spring is the only side with real test
   coverage.
5. **Root README is thin** — quick-start, CI/CD, monitoring, and responsibilities
   sections should be pulled up from docs/Makefile into `README.md` itself.
