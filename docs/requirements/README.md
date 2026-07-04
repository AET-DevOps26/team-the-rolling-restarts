# Project Requirements (Course Brief)

These are the **fixed grading/process requirements** for the DevOps course project
(the meta-requirements about *how* the system must be built, deployed, and operated).
They are distinct from the application domain spec — for the news-aggregator problem
itself see [`docs/source/PROBLEM_STATEMENT.md`](../source/PROBLEM_STATEMENT.md).

The brief is split into isolated chunks so a single requirement area can be checked
without rereading the whole document. Jump straight to the relevant file:

| # | File | What it covers |
| --- | --- | --- |
| 00 | [overview.md](00-overview.md) | Objective, project type, required system elements, deadline |
| 01 | [team-and-collaboration.md](01-team-and-collaboration.md) | Team size, registration data, subsystem ownership, contribution tracking, communication |
| 02 | [development-workflow.md](02-development-workflow.md) | Mono-repo, feature branches, mandatory PRs + review, CI-on-PR / CD-on-merge flow |
| 03 | [system-architecture.md](03-system-architecture.md) | Client, ≥3 Spring microservices, database + documented schema |
| 04 | [genai-component.md](04-genai-component.md) | Python service, real user-facing use case, cloud + local models, RAG bonus |
| 05 | [environment-and-deployment.md](05-environment-and-deployment.md) | Dockerfiles, docker-compose, ≤3-command setup, Kubernetes, Rancher + Azure |
| 06 | [ci-cd.md](06-ci-cd.md) | GitHub Actions: build/test/lint on PR, auto-deploy to k8s on merge, secrets |
| 07 | [observability.md](07-observability.md) | Prometheus (count/latency/error rate), Grafana `.json` dashboards, ≥1 alert |
| 08 | [testing.md](08-testing.md) | Server + GenAI unit tests, client workflow tests, all run in CI |
| 09 | [engineering-artefacts.md](09-engineering-artefacts.md) | Architecture description, mandatory UML diagrams, OpenAPI/Swagger UI |
| 10 | [deliverables.md](10-deliverables.md) | Final submission checklist, README contents, presentation + oral exam |
| 11 | [pitfalls-and-team-culture.md](11-pitfalls-and-team-culture.md) | Failure patterns and recommended practices (advice, not hard requirements) |
| 12 | [grading-structure.md](12-grading-structure.md) | Grading weights, oral exam, aggregated grade, final presentation, rubric tables — **internal, non-public** |

## Requirement status at a glance

The mandatory, gradeable requirements live in chunks 00–10. Chunk 11 is guidance.
When verifying compliance, chunks **02, 05, 06, 07, 08, 09, 10** are the ones with the
most concrete pass/fail checks.

See [STATUS.md](STATUS.md) for a file-referenced checklist of what's actually
implemented in the repo today versus what's still open, per requirement area.

See [GRADING-EVALUATION.md](GRADING-EVALUATION.md) for a best-effort,
repo-only self-assessment against the grading rubric in chunk 12 — **also
internal/non-public**, with re-verification pointers so it can be refreshed
without re-reading the whole repo.

## Non-public documents

`12-grading-structure.md`, `STATUS.md`, and `GRADING-EVALUATION.md` contain
course-internal grading details and a candid self-assessment. They are kept
outside `docs/source/` on purpose so MkDocs never publishes them — see the
notes at the top of each file before moving or restructuring this folder.

## Keeping STATUS.md / GRADING-EVALUATION.md in sync

Chunks `00`–`12` are the fixed brief and rarely change. `STATUS.md` and
`GRADING-EVALUATION.md` are the opposite — they're snapshots of the repo and
go stale the moment the repo moves. **Whoever closes a gap tracked in either
file — human or agent — should flip that item's status in the same
change**, rather than leaving it for a future full re-audit (e.g. shipping
the 3rd microservice, wiring the frontend to the backend, adding a GenAI
endpoint, or exporting a Grafana dashboard should all update the relevant
line). Each has its own re-verify commands for exactly this purpose. See
also [`docs/internal/README.md`](../internal/README.md#keeping-this-in-sync)
for the equivalent rule on the repo-map docs.
