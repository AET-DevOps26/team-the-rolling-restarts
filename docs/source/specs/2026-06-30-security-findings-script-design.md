# Security findings script — design

## Context

`ghcr.io/pstoeckle/guestlecture:v0.1.3` is a TUI ("DevOps Findings Analyzer") that renders SARIF
findings from a mounted folder. It is a pure viewer — it does not generate findings itself. The
reference data at
[`pstoeckle/guestlecture/output/AET-DevOps26/team-the-rolling-restarts`](https://github.com/pstoeckle/guestlecture/tree/main/output/AET-DevOps26/team-the-rolling-restarts)
shows what populates that folder: nine SARIF 2.1.0 reports, one or more per tool, covering secret
scanning, container image vulnerabilities, Dockerfile/IaC linting, dependency audits, and a couple
of small custom governance checks. None of these tools are wired into this repo's own CI or
pre-commit hooks today, and none are installed locally — only Docker is available.

This script reproduces that same toolchain locally so findings can be generated and inspected
without depending on an external, already-stale dataset (the reference data still references the
pre-restructuring `services/spring-api` layout) or a viewer that silently does nothing against an
empty folder.

## Goal

A single script, run via `make security-scan`, that:

1. Runs all nine tools from the reference dataset against the current working tree.
2. Writes SARIF output into `output/AET-DevOps26/team-the-rolling-restarts/`, using the same
   filenames as the reference data — so `docker run ... guestlecture:v0.1.3 /data` (mounting that
   folder) works against your own fresh results.
3. Prints a terminal summary (per-tool result counts, pass/fail) so the SARIF files aren't the only
   way to see what was found.
4. Degrades gracefully: one tool failing (image pull error, scan crash) does not abort the others.

## Tools and invocations

All run via Docker — no local tool installation required. Image, scan target, and the exact flag
used to produce SARIF, verified against each tool's `--help` output:

| Tool | Image | Target(s) | Invocation |
|---|---|---|---|
| gitleaks | `zricethezav/gitleaks` | whole repo (working tree) | `detect --source . --no-git -f sarif -r <out>` |
| trivy | `aquasec/trivy` | each built image **and** each published image (5 + 5) | `image -f sarif -o <out> <image>` |
| dockle | `goodwithtech/dockle` | each built image **and** each published image (5 + 5) | `-f sarif <image>` (stdout → file) |
| hadolint | `hadolint/hadolint` | each current Dockerfile (5) | `hadolint -f sarif <dockerfile>` (stdout → file) |
| kics | `checkmarx/kics` | whole repo | `scan -p . --report-formats sarif -o <dir> --output-name <name>` |
| zizmor | `ghcr.io/zizmorcore/zizmor` | `.github/workflows` | `--format sarif .github/workflows` (stdout → file) |
| npm audit | none (`node:lts-slim`) | `package.json`, `web-client/package.json` | `npm audit --json`, converted to a minimal hand-written SARIF shell (npm has no native SARIF output) |
| codeowners | none (pure bash) | repo root | custom check: does a `CODEOWNERS` file exist (root, `.github/`, or `docs/`)? emit a one-result SARIF pass/fail |
| typos | `python:3.12-slim` (`pip install typos`) | whole repo | `typos --format sarif .` (stdout → file) — no official typos Docker image exists, so this runs inside a throwaway Python container instead |

**Target paths reflect the current repo, not the stale reference data.** The reference Dockerfile
list (`services/spring-api/Dockerfile`) predates this branch's split into
`services/spring/{api-gateway,user-service,content-service}` — the script scans the five Dockerfiles
and five service images that exist today (web-client, api-gateway, user-service, content-service,
gen-ai).

## Images: both local and published

Trivy and dockle need built images, not source — and the script scans **two generations** of each
of the five service images:

1. **Local**: built fresh from the working tree (`docker build` per service, same Dockerfiles CI
   uses), so results reflect uncommitted local changes.
2. **Published**: pulled from GHCR, the same images the `build-and-package` workflow
   (`upload_images.yml`) actually pushes — i.e. what's really running on the Azure VM / K8s
   cluster today, including anything from that workflow's image layer/build process that a local
   `docker build` might not reproduce exactly (different base layer caching, buildx output, etc).

This catches drift between "what the code looks like right now" and "what's actually deployed."
Images are pulled from `ghcr.io/aet-devops26/team-the-rolling-restarts/<service>:<tag>` (confirmed
public, no `docker login` needed). The tag defaults to the **channel tag matching the current git
branch** — `main`, `dev`, or `wip` for anything else — mirroring `upload_images.yml`'s own
channel-selection logic exactly, and is overridable via `IMAGE_CHANNEL=<tag>` for checking a
different channel (e.g. `main` while working on a feature branch). If a given channel tag doesn't
exist yet for a service (e.g. it has never been pushed from this branch), that pull is skipped with
a `note`-level placeholder result rather than failing the script — same graceful-degradation
pattern as everything else.

## Output structure

```
output/AET-DevOps26/team-the-rolling-restarts/
  codeowners_report.json
  dockle_<service>.json                    (x5, local build)
  dockle_<service>_published.json          (x5, GHCR image)
  gitleaks_report.json
  hadolint_<dockerfile-path>.json          (x5)
  kics_report.json
  npm_audit_<package.json-path>.json       (x2)
  trivy_image_<service>.json               (x5, local build)
  trivy_image_<service>_published.json     (x5, GHCR image)
  typos_report.json
  zizmor_report.json
```

Filenames for the local-build scans match the reference data exactly (including its
path-flattening convention for hadolint and npm audit, e.g.
`hadolint_services_gen-ai_Dockerfile.json`), so the existing reference dataset's naming is fully
backward-compatible. The published-image scans are new files (`_published` suffix) not present in
the original reference set — purely additive, nothing renamed or removed. The script creates the
output directory if it doesn't exist — this is also the direct fix for the original problem (an
empty, unusable mount for the viewer).

## Error handling

Each tool runs in its own function, wrapped so a non-zero exit or Docker failure is caught,
recorded, and does not stop the script. At the end, a summary table prints:

- Per tool: ran successfully / failed to run, and a finding count (parsed from each SARIF file's
  `runs[].results[]` length) when it did run.
- A tool that fails to run still gets a placeholder SARIF file with a single `note`-level result
  saying so (mirroring the reference repo's own `npm_audit_*.json`, which records "We could NOT
  run npm audit for package.json" rather than omitting the file).

This means the output folder is always complete enough for the TUI viewer to open, even if some
scans failed in a given run.

## Script structure

`infra/scripts/security-scan.sh`, following `smoke-test.sh`'s conventions (`set -u`, small focused
bash functions, colored pass/fail markers, no external dependencies beyond Docker and `jq` for the
final summary parse).

```
security-scan.sh
  OUTPUT_DIR=output/AET-DevOps26/team-the-rolling-restarts
  IMAGE_CHANNEL ?= <current branch's channel: main/dev/wip>
  build_local_images()     # docker build the 5 service images locally
  pull_published_images()  # docker pull ghcr.io/.../<service>:$IMAGE_CHANNEL for the 5 services;
                            # skips a service gracefully (note-level result) if the tag is missing
  run_gitleaks()
  run_trivy()               # loops over local images, then published images (10 scans, 2 filename sets)
  run_dockle()               # loops over local images, then published images (10 scans, 2 filename sets)
  run_hadolint()             # loops over the 5 Dockerfiles
  run_kics()
  run_zizmor()
  run_npm_audit()            # loops over the 2 package.json files, hand-rolled SARIF wrapper
  run_codeowners_check()     # pure bash, no docker
  run_typos()
  print_summary()            # jq-parses each output file, prints counts + any failures
```

## Makefile integration

New `security-scan` target, added to the root `Makefile`'s `.PHONY` list and a new "Security"
section in `help`, alongside the existing `smoke-test`/`preflight` style:

```makefile
security-scan:
	@infra/scripts/security-scan.sh
```

## Out of scope

- Modifying or vendoring the `guestlecture` viewer itself — this script only produces input for it.
- Wiring these tools into this repo's CI — that's a separate, much larger decision (which tools
  should gate merges, threshold tuning, etc.) than "let me see findings locally."
- Historical/git-log scanning for gitleaks (only scans the current working tree, not full git
  history) — matches the reference data's apparent scope and keeps runtime reasonable.
