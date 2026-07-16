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
- ✅ ≥3 microservices with distinct responsibilities — `user-service` (auth/profiles), `content-service` (RSS/articles), and `services/gen-ai` (summarize/explain/sentiment/qa) count as the three, **per course clarification obtained via Artemis** confirming gen-ai counts toward this even though it isn't Spring Boot. `api-gateway` remains routing/JWT-validation infrastructure, not a business-domain service, and still doesn't count on its own. Note: the literal text in `03-system-architecture.md` ("The server side must be implemented in Spring Boot and must consist of at least three microservices") reads as Spring-specific taken alone — this clarification resolves that ambiguity in our favor; re-verify if it's ever challenged. No third Spring service is needed as a result — the previously-planned Interaction/Bookmarks service (see below) is no longer required.
- ✅ Documented API contract — `api/openapi.yaml`, generated code-first (`docs/source/openapi-workflow.md`)
- ⚠️ Documented DB schema — MongoDB is schemaless; no dedicated schema doc beyond entity classes, no migration tool

## 04 — GenAI Component

- ✅ Separate Python service, containerised — `services/gen-ai/` (FastAPI, LangChain), own `Dockerfile`
- ✅ Real user-facing use case — `POST /summarize`, `/explain`, `/sentiment`, `/qa` implemented (gateway: `/api/ai/*`); fetches article text from content-service when `articleId` is supplied; **web client article page** exposes Summary / Explain / Sentiment / Q&A widgets calling `/api/ai/*` via server actions (end-to-end user flow)
- ⚠️ Gateway exposes `/api/ai/**` publicly (`permitAll` in `SecurityConfig`) so the web client can call GenAI without JWT; Swagger UI aggregates gen-ai's `/openapi.json` at `/api/ai/openapi.json`. **Follow-up:** no rate limit or request-size cap on these routes yet — they front a paid Logos LLM; add gateway throttling and/or auth before production abuse.
- ⚠️ Cloud + local model support — code supports both (`get_chat_model()` branches on `LLM_PROVIDER=logos|ollama`). **Confirmed live on Kubernetes**: `logos` (`https://logos.aet.cit.tum.de/v1`, TUM-network-only) works there; gen-ai's LLM endpoints (`/summarize`, `/explain`, `/sentiment`, `/qa`) all work end-to-end. **Azure VM**: `logos` is unreachable off the TUM network, so `infra/docker-compose.azure.yaml` now runs a self-hosted Ollama (model from the `AZURE_OLLAMA_MODEL` repo variable, default `llama3.2:1b`) and `deploy-azure.yml` hardcodes `LLM_PROVIDER=ollama` there instead — hit and fixed a stale-repo-variable config bug on first live attempt (2026-07-16), then **confirmed live end-to-end on a fresh deploy** (AI endpoints and Grafana monitoring manually exercised and working). See `docs/internal/07-gotchas.md` and `docs/internal/06-observability.md`.
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
- ✅ Auto-deploy to Kubernetes on merge to main — `.github/workflows/deploy_kubernetes.yml` (triggers on the build workflow completing for `main`, runs `helm upgrade --install --create-namespace`). Manual `workflow_dispatch` redeploys the last successful build on whichever branch it's run against (real images + CI gate, not a placeholder fallback). Recovers automatically from either app namespace being wiped: the workflow applies `infra/k8s/namespaces/*.yml` (with the Rancher `field.cattle.io/projectId` project-association label) whenever `kubectl get namespace` comes back missing, which restores working RBAC and quota immediately — confirmed live, no manual Rancher-side step needed — see `docs/internal/06-observability.md` and `docs/internal/07-gotchas.md`.
- ✅ Auto-deploy monitoring-only changes — `.github/workflows/deploy_monitoring.yml`, path-filtered to `infra/grafana/**`/Helm monitoring templates/raw-manifest equivalents, so a dashboard tweak doesn't need the full app build pipeline
- ✅ Secrets used via GitHub Actions secrets, not hardcoded — confirmed in deploy workflows (including `LLM_API_KEY`, previously missing from the Kubernetes deploy's secrets wiring — fixed this round, see `docs/internal/05-ci-cd-workflows.md`)
- ✅ Docs auto-published — `.github/workflows/publish_docs.yml` (PlantUML + MkDocs + Redoc → GitHub Pages)

## 07 — Observability

- ✅ Metrics collection — real Prometheus (bundled in `grafana/otel-lgtm`), fed via OTLP push
  from all 3 Spring services + gen-ai, plus a classic scrape path for liveness (`up{job=...}`)
- ✅ GenAI service instrumented — OpenTelemetry SDK in `services/gen-ai` (FastAPI traces + custom
  LLM metrics via OTLP to `grafana-lgtm`), plus a `/metrics` scrape endpoint
- ✅ web-client instrumented — `@vercel/otel` in `web-client/instrumentation.ts` exports OTel
  traces via OTLP; unlike the other 3 services it has no scrapable metrics endpoint of its own, so
  its RED metrics come from Tempo's metrics-generator (`traces_spanmetrics_*`) instead. All 4 app
  services now export traces.
- ✅ Grafana dashboards exported as `.json` — `infra/grafana/dashboards/{service-overview,
  red-metrics-classic,genai-overview,webclient-overview}.json`, all filed under the "Service
  Health" folder
- ✅ Alert rules — `infra/grafana/provisioning/alerting/rules.yaml` (slow response time, service
  down). Satisfies the "at least one meaningful alert rule" requirement literally.
  ✅ **Notification delivery is wired up** — `infra/grafana/provisioning/alerting/
  contactpoints.yaml` provisions an email contact point + notification policy; SMTP delivery
  confirmed live end-to-end (a real email arrives) on both docker-compose and the Kubernetes
  cluster. Recipient list + SMTP credentials come from env vars/secrets, not hardcoded, so they
  can be replaced on any redeploy — see `docs/source/monitoring.md`.
- ✅ Log aggregation — application logs from all 4 services reach Loki via OTLP;
  `infra/scripts/smoke-test.sh` cross-checks its own requests appear there (real per-request log
  lines come from a new `RequestLoggingFilter` on all 3 Spring services, added for exactly this)
- ✅ Deployed to Kubernetes/Helm, not just docker-compose — Prometheus scrapes per-pod
  (`kubernetes_sd_configs`, role: pod) via a dedicated `grafana-lgtm` ServiceAccount +
  namespaced Role/RoleBinding, so `rate()` panels/alerts stay correct across replicas
- ✅ Spring memory limit raised 260Mi → 400Mi after it caused a real live OOMKill during
  `make smoke-test-k8s` (user-service crashed mid-login, cascading "no token" skips through the
  rest of the suite). Re-verified: `helm upgrade` applied, all 3 pods rolled out clean, 34/34
  smoke checks now pass
- ✅ `kubernetes-test` namespace retired — manual/dev deploys and the CD-managed release now share
  one namespace with a merged **4 CPU / 6144Mi** quota. Resources redistributed so
  api-gateway/user-service/content-service/gen-ai/web-client can each run 3 replicas simultaneously
  (headroom for a future HPA); mongodb's limit restored 260Mi → 512Mi (the 260Mi cut had caused a
  live OOMKill during first-boot init, permanently breaking Mongo auth for dependent services) —
  see `docs/internal/06-observability.md` and `docs/source/monitoring.md`
- ⚠️ No per-pod resource-usage metrics in Kubernetes — cluster RBAC doesn't allow the
  cluster-scoped access the cAdvisor scrape approach needs (verified, not just assumed)
- ⚠️ Security fix applied along the way: `/actuator/prometheus` was briefly reachable
  unauthenticated from the public internet via nginx/ingress before the public routes were
  narrowed to `/actuator/health` only — see `docs/internal/06-observability.md`
- ⚠️ `infra/helm/files/grafana/*` is a manually-copied duplicate of `infra/grafana/*` (no CI check
  keeps them in sync) — see `docs/internal/06-observability.md`

## 08 — Testing

- ✅ Spring unit tests, real assertions, run in CI — api-gateway (5 files, e.g. `SubscriberScopeTest.java`), user-service (6 files, e.g. `AuthControllerTest.java`), content-service (7 files, e.g. `ArticleControllerTest.java`)
- ✅ GenAI unit tests — `services/gen-ai/tests/test_health.py`, `test_summarize.py`, `test_explain.py`, `test_sentiment.py`, `test_qa.py`, `test_observability.py`, `test_metrics.py`, `test_upstream_errors.py`, `test_content.py` (offline mocked pytest)
- ⚠️ Client-side tests — a real Vitest runner is configured (`package.json`'s `"test": "vitest run"`), with 5 `*.test.ts` files (`lib/api/client.test.ts`, `lib/format/{time,html,color,rss-url}.test.ts`) covering utility/data-layer logic. No component/page-level (`*.test.tsx`) or E2E coverage yet — still a real gap, just not a "zero test runner" one anymore.

## 09 — Engineering Artefacts

- ✅ Subsystem Decomposition diagram — `docs/source/diagrams/architecture-component-diagram.puml` (+ rendered PNG)
- ✅ Use Case diagram — `docs/source/diagrams/use-case.puml` (+ rendered PNG)
- ✅ Analysis Object Model — `docs/source/diagrams/analysis-object-model.puml` (+ rendered PNG)
- ✅ OpenAPI/Swagger UI exposed — `springdoc-openapi-starter-webmvc-ui` in all 3 Spring services; api-gateway aggregates all specs via `springdoc.swagger-ui.urls[...]`

## 10 — Deliverables (README completeness)

- ✅ Architecture info in root `README.md` (service table + project layout)
- ⚠️ Setup/quick-start instructions — exist in `docs/source/index.md` and the `Makefile`, but are **not surfaced in root `README.md`** itself
- ❌ CI/CD instructions in README — not present (exists implicitly in workflow files only)
- ✅ Monitoring instructions in README — `## Monitoring` section added, links to `docs/source/monitoring.md`
- ❌ Student/contributor responsibilities section in README — team ownership rules live in `docs/requirements/01-team-and-collaboration.md` but aren't reflected in `README.md`

## Team & Process (01, 02) — not verifiable from code

Registration data, PR/review discipline, and Artemis-channel communication are
process requirements tied to how the team actually works, not to what's in the
tree at a point in time. Check these against actual GitHub PR history / Artemis
activity rather than this file.

---

## Where the biggest gaps are right now

1. ~~**Only 2 of the required ≥3 Spring microservices count**~~ — resolved: per course
   clarification via Artemis, gen-ai counts as one of the ≥3 microservices alongside
   `user-service`/`content-service`, so no third Spring service (the previously-planned
   Interaction/Bookmarks service) is needed after all. See §03 above.
2. ~~**GenAI secondary endpoints**~~ — done: `/summarize`, `/explain`, `/sentiment`, `/qa` are all
   implemented, with web-client widgets wired to each via server actions.
3. ~~**No Prometheus/Grafana dashboards or alerts**~~ — done, and expanded further: a real
   Prometheus (bundled in `grafana/otel-lgtm`), 4 exported dashboards (Service Overview, RED
   Metrics classic, GenAI Overview, Web Client Overview — the latter two added this round), and
   alert rules are wired up and deployed to both docker-compose and Kubernetes/Helm, plus log
   export to Loki from all 4 services (web-client traces added this round too, via `@vercel/otel`).
   See `docs/internal/06-observability.md` and `docs/source/monitoring.md` for details and
   remaining known gaps (below).
4. ~~**No client tests**~~ — resolved: web-client has a real Vitest setup (`package.json`'s
   `"test": "vitest run"`) with real test files (e.g. `lib/api/client.test.ts`). GenAI still has
   its own separate, already-passing pytest suite.
5. ~~**Alerting rules exist but don't notify anyone**~~ — resolved: an email contact point +
   notification policy are now provisioned (`infra/grafana/provisioning/alerting/
   contactpoints.yaml`), with SMTP delivery confirmed live end-to-end (real email arrival, not
   just config rendering) on both docker-compose and Kubernetes. Recipient list/SMTP credentials
   are env-var/secret-driven, replaceable on any redeploy without touching provisioning files.
6. ~~**The Azure VM deployment's gen-ai LLM calls don't work**~~ — code-level fix merged in:
   `logos` (`https://logos.aet.cit.tum.de/v1`) is still TUM-network-only and unreachable from
   Azure's public cloud, but the Azure compose override now runs a self-hosted Ollama container
   (model set by the `AZURE_OLLAMA_MODEL` repo variable, default `llama3.2:1b`, sized for the VM)
   and `deploy-azure.yml` hardcodes `LLM_PROVIDER=ollama` there instead. First real deploy attempt
   (2026-07-16) got everything else healthy (mongo, user-service, content-service, api-gateway,
   web-client) but hit a second config bug: a stale `LLM_PROVIDER=openai`/`LLM_MODEL=gpt-4o-mini`
   repo variable pair was silently shadowing the intended ollama default and broke the model pull.
   Fixed by decoupling Azure onto its own `AZURE_OLLAMA_MODEL` variable, then **confirmed live
   end-to-end on a fresh Azure deploy** — gen-ai's AI endpoints and Grafana monitoring manually
   exercised and working. Kubernetes continues to work fully (same network as Logos). See
   `docs/internal/07-gotchas.md`.
7. **No TLS on the Azure VM** — the auth cookie's `Secure` flag broke login persistence there
   until fixed with an explicit `COOKIE_SECURE` env var (see `docs/internal/06-observability.md`);
   real TLS (tracked as issue #90) would let that resolve more cleanly and match Kubernetes, which
   already has TLS via cert-manager.
8. **Root README is thin** — quick-start, CI/CD, and responsibilities sections should still be
   pulled up from docs/Makefile into `README.md` itself. A monitoring section has since been
   added (see §10 below).
9. **Article search is broken live** — fully implemented (real MongoDB `@TextIndexed` fields,
   `TextCriteria` query, full frontend wiring), but fails with `text index required for $text
   query (IndexNotFound)` since Spring Data MongoDB's `auto-index-creation` defaults to `false`
   and nothing creates the index another way. Small fix, not yet done — see
   `docs/internal/07-gotchas.md`.
