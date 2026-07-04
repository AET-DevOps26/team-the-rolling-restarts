# Internal Repo Map (Agent-Facing)

> **Internal — not for publication.** Same rationale as
> [`docs/requirements/`](../requirements/README.md): this lives outside
> `docs/source/` (the MkDocs `docs_dir`) so it is never built into the
> published docs site. Written for fast orientation by an AI assistant (or a
> teammate) picking the repo back up — not a course deliverable.

Each file below is scoped so a single question can be answered by reading
one file, not the whole repo. Re-verify before trusting a claim here for
anything load-bearing — this snapshot is **as of commit `2db4a5c`
(2026-07-04)** and will drift as the repo changes.

| File | What it covers |
| --- | --- |
| [01-spring-services.md](01-spring-services.md) | The 3 Spring modules: packages, controllers, config, ports, test inventory |
| [02-web-client.md](02-web-client.md) | Next.js app structure, pages/components, **mock-data-only** status, dead test config |
| [03-gen-ai-service.md](03-gen-ai-service.md) | FastAPI service: current `/health`-only state, config, generated client |
| [04-infra-and-deploy.md](04-infra-and-deploy.md) | docker-compose variants, Helm chart, raw k8s manifests, Terraform/Ansible |
| [05-ci-cd-workflows.md](05-ci-cd-workflows.md) | Every `.github/workflows/*.yml`: trigger, jobs, what each actually does (and doesn't) |
| [06-observability.md](06-observability.md) | OpenTelemetry + `grafana-lgtm` setup; what metrics exist vs. what's missing |
| [07-gotchas.md](07-gotchas.md) | Non-obvious findings that aren't visible just from file names — read this first when in doubt |

For course-requirement compliance (not repo structure), see
[`docs/requirements/STATUS.md`](../requirements/STATUS.md) and
[`docs/requirements/GRADING-EVALUATION.md`](../requirements/GRADING-EVALUATION.md)
instead — this folder is about *what the code is*, those are about *how it
measures up*.
