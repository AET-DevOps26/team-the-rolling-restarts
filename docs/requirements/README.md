# Project Requirements (Course Brief)

These are the **fixed grading/process requirements** for the DevOps course project
(the meta-requirements about *how* the system must be built, deployed, and operated).
They are distinct from the application domain spec — for the news-aggregator problem
itself see [`docs/source/PROBLEM_STATEMENT.md`](../source/PROBLEM_STATEMENT.md).

The brief is split into isolated chunks so a single requirement area can be checked
without rereading the whole document. Jump straight to the relevant file:

| # | File | What it covers |
|---|------|----------------|
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

## Requirement status at a glance

The mandatory, gradeable requirements live in chunks 00–10. Chunk 11 is guidance.
When verifying compliance, chunks **02, 05, 06, 07, 08, 09, 10** are the ones with the
most concrete pass/fail checks.
