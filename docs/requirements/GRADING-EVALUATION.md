# Grading Self-Assessment (Best-Effort, Repo + Live-Cluster Verified)

> **Internal — not for publication.** Same non-public placement rationale as
> [12-grading-structure.md](12-grading-structure.md): lives outside
> `docs/source/`, never built into the MkDocs site.

**As of commit `2ce6a32` (2026-07-13), branch `feat/observability-monitoring`.**
Unlike the previous pass (commit `6c388d9`, repo-only), this one draws on live
verification against both deployment targets (the Kubernetes cluster and the
Azure VM) done this session, plus a course clarification obtained via Artemis
resolving the microservice-count question. Since the prior `41ea71b` revision
of this document: Grafana's admin access was hardened (real login, wired into
the shared ingress at `/monitoring`, auto-rotating password), namespace
recreation with proper Rancher project association was added ahead of the
final-submission full-wipe, and alert notification delivery (email, SMTP) was
implemented and verified live end-to-end. Ratings use the tiers from
[12-grading-structure.md](12-grading-structure.md) (Excellent / Good / Basic /
Poor). Treat every rating here as a hypothesis to re-check, not a verdict —
things have moved substantially since the last pass and will keep moving.

**How to keep this current:** each section below ends with a **Re-verify**
line — the exact command(s) to rerun to see if the finding still holds. If a
command's output changes, update that section's rating and evidence instead
of re-auditing the whole repo. Bump the commit hash and date at the top
whenever you do a fresh pass. Full details behind every finding here also live
in [STATUS.md](STATUS.md) — this doc maps those findings onto the grading
rubric's categories rather than the requirement chunks.

---

## System

### Functional System — **Good, trending Excellent**

Backend services, web client, and gen-ai are fully integrated — no mock data
anywhere, all four GenAI endpoints (`/summarize`, `/explain`, `/sentiment`,
`/qa`) implemented and wired to the article page's AI widgets via server
actions. **Verified live end-to-end on both deployment targets**: Kubernetes
(signup, login, dashboards, article AI widgets, full monitoring stack) and,
as of 2026-07-16, Azure (same checklist plus Grafana monitoring) both confirmed
working against real deployments. Azure's gen-ai LLM calls needed two fixes
first — a self-hosted Ollama container (`infra/docker-compose.azure.yaml`,
since `logos` is TUM-network-only and unreachable from Azure), then a stale
repo variable pair that was silently overriding the ollama default and
breaking the model pull, fixed by giving Azure its own `AZURE_OLLAMA_MODEL`
variable. One thing keeps this short of a clean Excellent: the article
search feature (`?q=` on the dashboard) is fully implemented but fails live
with a MongoDB `IndexNotFound` error (`auto-index-creation` defaults to
`false`, no text index is ever actually created) — confirmed via a live 500
from `content-service`.

- **Evidence:** `web-client/src/lib/api/{reads,ai}.ts`, `web-client/src/app/(app)/article/[id]/ai-actions.ts`, `web-client/src/components/article/ai/`; `services/gen-ai/app/routers/{summarize,explain,sentiment,qa}.py`; `services/spring/content-service/src/main/java/rolling_restarts/content/service/ArticleService.java` (search, broken); `infra/docker-compose.azure.yaml` (Ollama); `docs/internal/06-observability.md` and `docs/internal/07-gotchas.md` for the live-verification incident log
- **Re-verify:** exercise the search bar against the live Kubernetes deployment; `kubectl logs -n deployment deploy/content-service | grep "IndexNotFound"` (search bug — only remaining gap; AI widgets and Azure's Ollama container are both confirmed working)

### Architecture Quality — **Excellent** (up from "Good, borderline")

Both concerns that previously held this back are resolved. (1) **≥3
microservices**: a course clarification obtained via Artemis confirmed gen-ai
counts toward this requirement, so `user-service` + `content-service` +
`gen-ai` satisfy it — no third Spring service was needed after all (the
previously-planned Interaction/Bookmarks service is dropped). (2) **The
frontend now genuinely exercises the defined interface**: real
`apiFetch`-based calls throughout, a generated client from the same OpenAPI
contract the backend publishes, and a CI job that fails the build if the
committed spec drifts from what the code actually generates. Service
boundaries remain clean: distinct responsibilities, independent Dockerfiles,
per-service OpenAPI docs aggregated at the gateway. Remaining minor gap: no
dedicated MongoDB schema document beyond the entity classes (schemaless DB,
so this is a documentation nicety more than a structural one).

- **Evidence:** `services/spring/settings.gradle`; `api/openapi.yaml`; `.github/workflows/ci.yml`'s `openapi-contract` job; `docs/requirements/STATUS.md` §03
- **Re-verify:** `cat services/spring/settings.gradle`; confirm the clarification is still reflected accurately if the course revisits it.

### User-Facing Value — **Good**

Substantially built UI (dashboard, feed with filtering, saved, settings,
article detail, login/signup) with GenAI surfaced directly on the article
page (summarize/explain/sentiment/qa). Article search exists in the UI and
looks complete end-to-end (search bar → URL query param → paginated,
filtered results) but is currently broken live (see Functional System above)
— a real, user-visible gap, not a design choice. RAG/personalization beyond
topic/source filtering remains unstarted (bonus territory, not baseline).

- **Evidence:** `web-client/src/components/layout/topbar-search.tsx`; `web-client/src/app/(app)/dashboard/page.tsx`; `web-client/src/components/article/ai/article-ai-panel.tsx`
- **Re-verify:** `grep -rn "ArticleAiPanel\|TopbarSearch" web-client/src --include="*.tsx"`; exercise both the AI widgets and search against the live deployment.

---

## DevOps & Infrastructure

### Build and Deployment — **Good, likely Excellent**

The pipeline structurally matches the Excellent tier, and this session added
real evidence of *reliability in practice*, not just structure: CI builds+tests
all 5 services plus an OpenAPI contract-drift check, Terraform validate, Helm
lint, and (still-stubbed) contract tests; a separate multi-arch image-build
workflow; two CD paths (Kubernetes via Helm, Azure VM via `az vm run-command`)
were both exercised live this session, including real failures caught and
fixed the same day (a Docker-install conflict, a missing config-sync step in
*two* independent mechanisms, a missing `LLM_API_KEY` secret wire-through, a
namespace-wipe recovery gap). That's evidence of the pipeline actually working
under real conditions, not just existing on paper — but it also means
reliability wasn't perfect: several of those were live incidents, not
theoretical risks, and the Azure VM path still has an open, unresolved
functional gap (gen-ai's LLM calls). Manual `workflow_dispatch` now also
recovers real images + a CI gate instead of falling back to placeholder image
references.

- **Evidence:** `.github/workflows/{ci.yml,upload_images.yml,deploy_kubernetes.yml,deploy_monitoring.yml,deploy-azure.yml}`; `docs/internal/06-observability.md`'s incident log for the specific live failures found and fixed this session
- **Re-verify:** `gh run list --workflow=deploy_kubernetes.yml --limit 10`; `gh run list --workflow=deploy-azure.yml --limit 10` — check recent success rate.

### Runtime and Observability — **Excellent** (up from "Good, trending Excellent")

The previous pass's central gap — "no exported Grafana dashboard `.json`
anywhere in the repo and no alert rule file" — is fully resolved and then
some: **4 exported dashboards** (Service Overview, RED Metrics classic, GenAI
Overview, Web Client Overview), all filed under a "Service Health" folder with
readable per-service legends (a real, previously-shipped bug where every
service blended into one unlabeled line was found and fixed along the way);
**2 alert rules** (slow response, service down) that now **actually notify
someone** — an email contact point + notification policy
(`infra/grafana/provisioning/alerting/contactpoints.yaml`), with SMTP
delivery confirmed live end-to-end (a real email arriving, not just
config rendering) on both docker-compose and the Kubernetes cluster; **log
aggregation** from all 4 services to Loki, cross-checked live; **traces**
from all 4 services now (web-client's OTel instrumentation was the last
piece, added this session). Metrics genuinely reflect system behaviour —
verified against real triggered traffic (LLM calls, HTTP requests), not just
checked for the presence of a metric name. The recipient list and SMTP
credentials are env-var/secret-driven rather than hardcoded, so they can be
replaced on any redeploy — including the full-from-scratch redeploy this
course's final submission requires.

- **Evidence:** `infra/grafana/dashboards/*.json`; `infra/grafana/provisioning/alerting/{rules,contactpoints}.yaml`; `docs/source/monitoring.md`; `docs/internal/06-observability.md` (extensive live-verification incident log — namespace split, OTLP cross-namespace DNS fix, per-pod scrape fix, dashboard folder fixes, the two new dashboards, real SMTP alert delivery)
- **Re-verify:** `find infra/grafana/dashboards -name "*.json" | wc -l` (expect 4); open Grafana and check the "Service Health" folder; check for the `email-alerts` contact point under Alerting → Contact points.
- **Bonus angle:** this already qualifies as **Advanced Observability** (tracing + log aggregation + custom metrics, all present and live-verified) — see Bonus categories below.

### Environment and Reproducibility — **Good**

Unchanged from the previous pass: full containerization, a working
`docker-compose.yaml` + `docker-compose.dev.yaml`, and a genuine 2-command
quick start. Still held back from Excellent because that quick start lives in
`docs/source/index.md` and the `Makefile`, not the root `README.md`.

- **Evidence:** `infra/docker-compose.yaml`, `infra/docker-compose.dev.yaml`, `infra/.env.example`, `docs/source/index.md`
- **Re-verify:** `grep -n "compose up\|compose-up" README.md` — currently empty; non-empty means this has been fixed.

---

## Engineering Process

### Testing Strategy — **Good** (up from "Basic")

All three required areas now have real coverage, correcting the previous
pass's claims for two of them: **Spring** has 18 test files with real
assertions across the 3 services (unchanged). **GenAI** has 9 real test files
(`test_health.py`, `test_summarize.py`, `test_explain.py`, `test_sentiment.py`,
`test_qa.py`, `test_observability.py`, `test_metrics.py`,
`test_upstream_errors.py`, `test_content.py`) — the previous pass's claim that
`services/gen-ai/tests/` "contains only `__init__.py`" was already wrong when
written, or went stale very quickly. **web-client** now has a real Vitest
runner (`"test": "vitest run"`) with 5 test files covering the server-only
fetch wrapper and formatting utilities — real, but narrow: no
component/page-level (`*.test.tsx`) or end-to-end coverage yet, so this stays
at Good rather than Excellent ("tests cover critical flows, edge cases, and
failures").

- **Evidence:** `services/spring/*/src/test/java/**`; `services/gen-ai/tests/test_*.py`; `web-client/package.json` (`"test": "vitest run"`); `web-client/src/lib/{api/client.test.ts,format/*.test.ts}`
- **Re-verify:** `find services/gen-ai/tests -name "test_*.py" | wc -l`; `grep -n '"test"' web-client/package.json`; `find web-client -iname "*.test.*" -not -path "*/node_modules/*" | wc -l`.

### Engineering Artefacts — **Good**

All three mandatory UML diagrams exist as PlantUML source with rendered PNGs,
regenerated in CI on every publish. The previous pass's drift concern
("diagrams might already show a 3rd microservice that doesn't exist, or omit
one that does") is largely moot now — since the "3rd microservice" resolution
was a *recognition* (gen-ai counts) rather than new code, no new component
needs adding to the diagrams on that front. Still not directly verified this
pass: whether the diagrams accurately reflect the *current* service
boundaries and data flows (e.g. web-client's new OTel instrumentation, the
monitoring namespace split) — diagram/code consistency still isn't enforced
by anything automated.

- **Evidence:** `docs/source/diagrams/{architecture-component-diagram,use-case,analysis-object-model}.puml`
- **Re-verify:** open the three `.puml` files and diff their described components against the current `services/spring/settings.gradle` + `services/gen-ai` + `web-client` + `infra/helm` structure.

### Documentation — **Good**

The underlying content is more thorough than it first appears, and grew
substantially this session — `docs/internal/` (agent-facing repo map) had
several sections that were badly stale (a "frontend is mock-driven" gotcha
describing a state from before the backend-integration work, a wrong
`LLM_PROVIDER` env-var table in `docs/source/gen-ai-service.md` that plausibly
*caused* a real production incident) and are now corrected; `docs/source/`
gained updated guides for the Azure VM runbook, both CD pipelines, monitoring,
and secrets reference reflecting this session's fixes. The persistent gap is
unchanged: the root `README.md` — the actual entry point — still doesn't
surface quick-start, CI/CD summary, or a student-responsibilities section
(only the monitoring section has been pulled up into it so far).

- **Evidence:** `docs/source/*.md` (guide inventory); `docs/internal/*.md` (this session's corrections); `README.md` (50 lines, monitoring section present, no responsibilities/CI-CD sections)
- **Re-verify:** `wc -l README.md`; `grep -n "Responsibilit\|CI/CD\|Monitoring\|Prometheus\|Grafana" README.md`.

---

## Fail-condition check (rubric's explicit failure triggers)

- *"Contributions are not transparently documented (Artemis + GitHub)"* — **not
  deducible from repo alone**; check GitHub PR authorship/review history and
  Artemis directly.
- *"Team members cannot clearly explain their own subsystem"* — **not
  deducible**; only observable at the actual oral exam/presentation.
- *"No working end-to-end system is demonstrated"* — **this risk is resolved**
  as of this pass. The previous pass flagged this as the one real risk visible
  from the repo ("the frontend runs on mock data, GenAI has no feature beyond
  `/health`") — both of those are now false, and the Kubernetes deployment has
  been verified live end-to-end this session (signup through to GenAI widgets
  and a working monitoring stack). The remaining gaps (Azure VM's LLM calls,
  article search) are real but narrow — they don't undermine the claim that a
  working, integrated system exists and can be demonstrated.

## Bonus categories — best-effort read

- **Advanced DevOps** (autoscaling, self-healing): not present — no
  `HorizontalPodAutoscaler` found in `infra/helm` or `infra/k8s` (only a
  `PodDisruptionBudget`, which is availability, not autoscaling). Re-verify:
  `grep -rl "HorizontalPodAutoscaler" infra/`.
- **Advanced Observability** (tracing, log aggregation, custom metrics): **now
  achieved**, not just partial. Tracing (all 4 services, including web-client
  added this session), log aggregation (Loki, all 4 services, live-verified),
  and custom metrics (gen-ai's LLM-specific counters/histograms, its own
  dashboard) are all present and confirmed working against real traffic — see
  the Runtime and Observability section above.
- **Advanced AI** (RAG, vector DB): not started — no vector DB dependency
  found anywhere in `services/gen-ai`.
- **Additional justified improvements**: `make security-scan`
  (`infra/scripts/security-scan.sh`) — unchanged from the previous pass (a
  ten-scan local security/quality suite, local/manual, not a CI gate — see
  `STATUS.md` §06). This session also demonstrated a real incident-response
  discipline worth noting on its own: several live production bugs (a
  Docker-install conflict, a namespace-config-sync gap present in *two*
  independent deploy mechanisms, a missing secret wire-through, a cookie
  security-flag bug, a Rancher RBAC/namespace-recovery gap) were each
  root-caused against the real running systems (not guessed) and fixed with
  verification, not just patched and assumed working.
- **System Excellence**: premature to claim while the baseline gaps above
  (search bug, Azure VM's LLM gap, root README) are open — but the gap
  between "premature" and "defensible" has narrowed substantially this pass
  (alerting notification delivery, previously on this list, is now resolved).

## What this document cannot tell you

Planning/task distribution, month-to-month progress, team collaboration and
communication quality, and responsiveness to tutor feedback all still have to
be judged directly against [12-grading-structure.md](12-grading-structure.md)
— none of that is visible from the repo. **Reachability and stability of the
deployed instances, unlike the previous pass, HAVE been confirmed this
session** — both `rolling-restarts.stud.k8s.aet.cit.tum.de` (Kubernetes) and
the Azure VM were exercised live, with the specific caveats recorded above
(Azure's LLM gap, the search bug) rather than left as unknowns. Individual
readiness for the oral exam remains unassessable from here.
