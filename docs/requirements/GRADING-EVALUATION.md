# Grading Self-Assessment (Best-Effort, Repo-Only)

> **Internal — not for publication.** Same non-public placement rationale as
> [12-grading-structure.md](12-grading-structure.md): lives outside
> `docs/source/`, never built into the MkDocs site.

**As of commit `6c388d9` (2026-07-04).** This assessment only uses what's
verifiable by reading the repo — no access to Artemis, GitHub PR/commit
history, Actions run history, or the live cluster. Ratings use the tiers from
[12-grading-structure.md](12-grading-structure.md) (Excellent / Good / Basic /
Poor). Treat every rating here as a hypothesis to re-check, not a verdict.

**How to keep this current:** each section below ends with a **Re-verify**
line — the exact command(s) to rerun to see if the finding still holds. If a
command's output changes, update that section's rating and evidence instead
of re-auditing the whole repo. Bump the commit hash and date at the top
whenever you do a fresh pass. Full details behind every finding here also live
in [STATUS.md](STATUS.md) — this doc maps those findings onto the grading
rubric's categories rather than the requirement chunks.

---

## System

### Functional System — **Good** (partial end-to-end)

Backend services build and expose REST + GenAI routes; the web client loads live
articles and renders **AI widgets** on the article detail page (`ArticleAiPanel`)
that call `/api/ai/*` through Next.js server actions. All four GenAI endpoints
(`/summarize`, `/explain`, `/sentiment`, `/qa`) work end-to-end.

- **Evidence:** `web-client/src/lib/api/reads.ts`, `web-client/src/lib/api/ai.ts`, `web-client/src/app/(app)/article/[id]/ai-actions.ts`, `web-client/src/components/article/ai/`; `services/gen-ai/app/routers/{summarize,explain,sentiment,qa}.py`
- **Re-verify:** open an article with the stack running; exercise Summary, Explain, Sentiment, and Q&A widgets and confirm responses.

### Architecture Quality — **Good** (borderline)

Backend service boundaries that do exist are clean: distinct responsibilities,
independent Dockerfiles, an OpenAPI contract as the interface definition. Two
things pull this down from Excellent: (1) only 2 of the 3 required Spring
microservices are business-domain services — `api-gateway` is routing/JWT
infra, tracked as an open item in `STATUS.md` §03; (2) the frontend doesn't
exercise the defined interface yet (see Functional System above), so the
"well-defined interfaces" exist on paper but aren't proven in practice.

- **Evidence:** `services/spring/settings.gradle`; `api/openapi.yaml`; `docs/requirements/STATUS.md` §03
- **Re-verify:** `cat services/spring/settings.gradle` — check module count/names; re-run the Functional System check above.

### User-Facing Value — **Good** (GenAI visible on article page)

The UI is substantially built — dashboard, saved, settings, article detail,
login/signup — and the **article detail page** surfaces GenAI: users can
request summaries, explanations, sentiment analysis, and Q&A from
`ArticleAiPanel`. Remaining gap: broader product polish (third microservice, RAG) is still open.

- **Evidence:** `web-client/src/components/article/ai/article-ai-panel.tsx`; `web-client/src/app/(app)/article/[id]/page.tsx`
- **Re-verify:** `grep -rn "ArticleAiPanel\|summarizeArticleAction" web-client/src --include="*.tsx"`; exercise all four widgets against a running stack.

---

## DevOps & Infrastructure

### Build and Deployment — **Good, likely Excellent** (unconfirmed reliability)

The pipeline structurally matches the Excellent tier: CI builds+tests all 5
services, runs an OpenAPI contract-drift check, Terraform validate, Helm lint,
and contract tests; a separate workflow builds multi-arch images to GHCR; CD
auto-deploys via Helm on `workflow_run` completion for `main`; a parallel Azure
VM path also exists. What can't be confirmed from the repo alone is whether
these runs are actually *reliable* (rubric: "work reliably") — that requires
Actions run history, which isn't visible here.

- **Evidence:** `.github/workflows/{ci.yml,upload_images.yml,deploy_kubernetes.yml,deploy-azure.yml}`
- **Re-verify:** check the Actions tab for recent run success rate on `main`; `gh run list --branch main --limit 20` if `gh` is authenticated.

### Runtime and Observability — **Basic**

A real metrics backbone exists (OpenTelemetry in all 3 Spring services +
`grafana-lgtm` all-in-one, which bundles Grafana/Loki/Tempo/Mimir — i.e. logs
and tracing infra are already present, not just metrics). `services/gen-ai` now
exports OTLP traces (FastAPI auto-instrumentation) and custom LLM metrics
(request count, latency, errors, token usage when available) to the same
collector. The remaining gaps are the *required deliverables*: no exported
Grafana dashboard `.json` anywhere in the repo and no alert rule file. This
matches the rubric's Basic tier: monitoring backbone is present and gen-ai is
instrumented, but nothing renders the metrics into an exported dashboard yet.

- **Evidence:** `infra/docker-compose.yaml` (`grafana-lgtm` service); `services/spring/*/build.gradle` (`spring-boot-starter-opentelemetry`); `services/gen-ai/app/observability.py`, `app/llm/invoke.py`; no `*dashboard*.json` or alert-rule file found anywhere
- **Re-verify:** `find . -iname "*dashboard*.json" -o -iname "*alert*rule*" | grep -v node_modules | grep -v .venv`; `grep -rn "opentelemetry" services/gen-ai --include="*.py"`.
- **Bonus angle:** the LGTM stack already includes Tempo (tracing) and Loki (log aggregation) — exporting a couple of dashboards and one alert rule would clear the baseline requirement *and* put "Advanced Observability" bonus (tracing, log aggregation) within easy reach, since the infra is already deployed.

### Environment and Reproducibility — **Good**

Full containerization (every component has its own Dockerfile), a working
`docker-compose.yaml` + `docker-compose.dev.yaml`, and a genuine 2-command
quick start (`cp infra/.env.example infra/.env && make compose-up`). Held back
from Excellent only because that quick start lives in `docs/source/index.md`
and the `Makefile`, not in the root `README.md` — the first place an
evaluator is likely to look.

- **Evidence:** `infra/docker-compose.yaml`, `infra/docker-compose.dev.yaml`, `infra/.env.example`, `docs/source/index.md`
- **Re-verify:** `grep -n "compose up\|compose-up" README.md` — currently empty; non-empty means this has been fixed.

---

## Engineering Process

### Testing Strategy — **Basic**

Spring services have real, meaningful tests (18 files across the 3 services
with actual assertions, e.g. `AuthControllerTest.java`,
`ArticleControllerTest.java`). `services/gen-ai/tests/` now has eight test
modules (health, all four endpoints, observability, metrics, upstream errors).
`web-client` has no test runner configured at all (no
jest/vitest/playwright in `package.json`, no `test` script, no `*.test.tsx`
files). Since the rubric explicitly expects coverage of server, GenAI, *and*
client, the client side still has zero coverage.

- **Evidence:** `services/spring/*/src/test/java/**`; `services/gen-ai/tests/test_*.py`; `web-client/package.json` (`scripts` has no `test` key)
- **Re-verify:** `find services/gen-ai/tests -name "test_*.py"`; `grep -n '"test"' web-client/package.json`; `find web-client -iname "*.test.*" -not -path "*/node_modules/*"`.

### Engineering Artefacts — **Good**

All three mandatory UML diagrams exist as PlantUML source with rendered PNGs,
regenerated in CI on every publish
(`.github/workflows/publish_docs.yml`): Subsystem Decomposition, Use Case, and
Analysis Object Model. Not verified: whether the diagrams still match the
*current* implementation (e.g. do they already show a 3rd domain microservice
that doesn't exist yet, or omit one that does) — diagrams drift silently since
nothing enforces diagram/code consistency.

- **Evidence:** `docs/source/diagrams/{architecture-component-diagram,use-case,analysis-object-model}.puml`
- **Re-verify:** open the three `.puml` files and diff their described components against `services/spring/settings.gradle` + `services/gen-ai` + `web-client` — do this **after** the Interaction/Bookmarks service (or whatever 3rd microservice is chosen) actually lands, since the diagrams will need updating then regardless.

### Documentation — **Good**

The underlying content is more thorough than it first appears — MkDocs hosts
guides for OpenAPI workflow, deployment testing, secrets reference, Azure VM
deployment, both CD pipelines, plus per-infra READMEs (Ansible, Terraform,
Helm, k8s). The gap is that the root `README.md` — the actual entry point —
doesn't surface any of it: no quick-start, no CI/CD summary, no monitoring
pointer, no student-responsibilities section (that only exists internally in
`docs/requirements/01-team-and-collaboration.md`, which is explicitly
non-public). Rubric-wise this is "usable but incomplete" from the front door,
even though the deep content would arguably support "Excellent" if surfaced.

- **Evidence:** `docs/source/*.md` (guide inventory); `README.md` (44 lines, no responsibilities/CI/monitoring sections)
- **Re-verify:** `wc -l README.md`; `grep -n "Responsibilit\|CI/CD\|Monitoring\|Prometheus\|Grafana" README.md`.

---

## Fail-condition check (rubric's explicit failure triggers)

- *"Contributions are not transparently documented (Artemis + GitHub)"* — **not
  deducible from repo alone**; check GitHub PR authorship/review history and
  Artemis directly.
- *"Team members cannot clearly explain their own subsystem"* — **not
  deducible**; only observable at the actual oral exam/presentation.
- *"No working end-to-end system is demonstrated"* — **this is the one real
  risk visible from the repo today.** Nothing currently proves the pieces work
  together: the frontend runs on mock data, GenAI has no feature beyond
  `/health`. This doesn't mean the system *can't* be demonstrated end-to-end —
  it means that proof doesn't exist in the repo yet. Closing the
  frontend↔backend wiring and shipping one real GenAI endpoint would remove
  this risk entirely.

## Bonus categories — best-effort read

- **Advanced DevOps** (autoscaling, self-healing): not present — no
  `HorizontalPodAutoscaler` found in `infra/helm` or `infra/k8s` (only a
  `PodDisruptionBudget` exists, which is availability, not autoscaling).
  Re-verify: `grep -rl "HorizontalPodAutoscaler" infra/`.
- **Advanced Observability** (tracing, log aggregation, custom metrics): partially
  present — `grafana-lgtm` bundles Tempo and Loki; Spring services and gen-ai
  export OTLP traces/metrics. Still missing exported dashboards and alert rules.
  See the Runtime and Observability bonus note above.
- **Advanced AI** (RAG, vector DB): not started — no vector DB dependency
  found anywhere in `services/gen-ai`.
- **Additional justified improvements**: `make security-scan`
  (`infra/scripts/security-scan.sh`) — a ten-scan local security/quality suite
  (gitleaks, hadolint, kics, zizmor, typos, npm audit, a CODEOWNERS coverage
  check, trivy fs/image, dockle) with SARIF 2.1.0 output and a `make score` TUI
  viewer. Worth noting this is local/manual tooling, not a CI gate — it
  doesn't itself satisfy the baseline "CI must perform static analysis"
  requirement (see `STATUS.md` §06), so its value here is as an extension
  beyond the baseline rather than closing that gap. The gitleaks half of this
  suite is additionally wired into both a `pre-commit` hook (staged-diff scan
  on every commit) and a `pre-push` hook (scans every commit being pushed) —
  a real guard rail, though one that depends on each contributor having run
  `pre-commit install` / `make install-hooks` and isn't a CI-enforced gate.
- **System Excellence**: premature to claim while the baseline gaps above are
  open.

## What this document cannot tell you

Planning/task distribution, month-to-month progress, team collaboration and
communication quality, responsiveness to tutor feedback, actual reachability
and stability of the deployed instance (`rolling-restarts.stud.k8s.aet.cit.tum.de`
is configured in `infra/helm/values.yaml` but this sandbox has no network
egress to confirm it's live), and anything about individual readiness for the
oral exam. All of that has to be judged by the team directly against
[12-grading-structure.md](12-grading-structure.md).
