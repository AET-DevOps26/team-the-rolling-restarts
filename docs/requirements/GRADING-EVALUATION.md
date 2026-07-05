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

### Functional System — **Basic**

Backend services (`api-gateway`, `user-service`, `content-service`) build,
have real tests, and expose a documented REST contract. But there is no
evidence of an actual working end-to-end user flow: the entire `web-client` UI
(dashboard, feed, saved, settings, article pages) renders from
`web-client/src/lib/mock/*`, not from the generated API client
(`web-client/src/generated/api.ts`) or any live backend call — no `fetch`,
`axios`, or `NEXT_PUBLIC_API`/`process.env` usage was found anywhere in
`web-client/src`. `services/gen-ai` exposes only `/health`. So each piece
builds in isolation, but nothing demonstrates them working together yet.

- **Evidence:** `web-client/src/lib/mock/{articles,sources,topics,user}.ts`; `web-client/src/generated/api.ts` (exists, unused); `services/gen-ai/app/main.py` (health check only)
- **Re-verify:** `grep -rln "generated/personalised_news_aggregator_api_client\|fetch(\|axios\|NEXT_PUBLIC_API" web-client/src --include="*.tsx" --include="*.ts"` — empty output means still disconnected. Also recheck `services/gen-ai/app/main.py` for new routes beyond `/health`.

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

### User-Facing Value — **Basic**

The UI is substantially scaffolded — dashboard, saved articles, settings,
article detail, login/signup/forgot-password all exist as real pages, which is
more than a stub. But since none of it is wired to live data or to the
GenAI-generated summaries the problem statement centers on
(`docs/source/PROBLEM_STATEMENT.md`), it currently reads as a UI prototype
rather than a working product. This is the same underlying gap as Functional
System, viewed from the "does it solve the user's problem yet" angle.

- **Evidence:** `web-client/src/app/(app)/{dashboard,saved,settings,article}` (real pages); no summary/AI text found in UI copy beyond static strings (`web-client/src/components/settings/notifications-section.tsx` mentions "summary" only as static UI label)
- **Re-verify:** `find web-client/src/app -iname "page.tsx"` to confirm page inventory; `grep -rn "summar" web-client/src --include="*.tsx" -i` to check if AI-generated content has been wired into any page yet.

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
and tracing infra are already present, not just metrics). But the actual
*required deliverables* are missing: no exported Grafana dashboard `.json`
anywhere in the repo, no alert rule file, and `services/gen-ai` has zero
instrumentation. This matches the rubric's Basic tier almost exactly: "basic
monitoring setup present but not useful for understanding system behaviour"
— nothing renders the metrics into an actual dashboard yet.

- **Evidence:** `infra/docker-compose.yaml` (`grafana-lgtm` service); `services/spring/*/build.gradle` (`spring-boot-starter-opentelemetry`); no `*dashboard*.json` or alert-rule file found anywhere
- **Re-verify:** `find . -iname "*dashboard*.json" -o -iname "*alert*rule*" | grep -v node_modules | grep -v .venv`; `grep -rn "prometheus_client\|opentelemetry" services/gen-ai --include="*.py"`.
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
`ArticleControllerTest.java`). But `services/gen-ai/tests/` contains only
`__init__.py` (CI tolerates pytest exit code 5 / "no tests collected" as a
pass), and `web-client` has no test runner configured at all (no
jest/vitest/playwright in `package.json`, no `test` script, no `*.test.tsx`
files). Since the rubric explicitly expects coverage of server, GenAI, *and*
client, 2 of 3 required areas have zero coverage.

- **Evidence:** `services/spring/*/src/test/java/**`; `services/gen-ai/tests/__init__.py`; `web-client/package.json` (`scripts` has no `test` key)
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
- **Advanced Observability** (tracing, log aggregation, custom metrics): not
  claimed yet, but unusually close — `grafana-lgtm` already bundles Tempo and
  Loki. See the Runtime and Observability bonus note above.
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
