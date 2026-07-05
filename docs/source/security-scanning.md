# Security Scanning

Local security/quality scanning via `infra/scripts/security-scan.sh`, exposed as a single
Makefile entrypoint. Not wired into CI — run it manually before a release or when touching
infra/Dockerfiles. Secret scanning specifically also runs automatically as a pre-commit hook —
see [Pre-commit secret scanning](#pre-commit-secret-scanning) below.

## Usage

```bash
make security-scan   # run all scanners, write SARIF to output/AET-DevOps26/team-the-rolling-restarts/
make score            # view the results in the guestlecture TUI
```

`make security-scan` builds local images for the five services (tagged `rolling-restarts/<service>:local`)
and runs ten scans in parallel via Docker — no local tool installs required:

| Scan | Tool | Target |
| ---- | ---- | ------ |
| Secret scanning | gitleaks | Working tree |
| Filesystem vulnerabilities | trivy (fs mode) | Working tree |
| Image vulnerabilities | trivy (image mode) | Each locally-built image |
| Image best practices | dockle | Each locally-built image |
| Dockerfile linting | hadolint | Every `Dockerfile` |
| IaC scanning | kics | Terraform/Ansible/Helm/Compose |
| GitHub Actions scanning | zizmor | `.github/workflows/*.yml` |
| Typo checking | typos | Working tree |
| Dependency audit | npm audit | `web-client/package-lock.json` |
| Governance check | custom script | `.github/CODEOWNERS` coverage |

Each scan writes one SARIF 2.1.0 file; a failure in one scanner doesn't abort the others — it's
recorded as a scan error in its own output file instead. Ctrl+C during a run force-removes any
in-flight scanner containers (labeled per-run) rather than leaving them orphaned.

The terminal summary reports three states per scanner: `FAIL` (the scanner itself didn't run),
`FOUND` (it ran and reported ≥1 finding), `OK` (it ran clean).

## Output

Results land in `output/AET-DevOps26/team-the-rolling-restarts/` (gitignored, wiped at the start
of every run). `make score` mounts that folder into
[`guestlecture`](https://github.com/pstoeckle/guestlecture), a read-only SARIF viewer TUI — it
generates nothing itself, so `make security-scan` must be run first.

## Pre-commit secret scanning

`.pre-commit-config.yaml`'s `gitleaks` hook (`infra/scripts/pre-commit-gitleaks.sh`) runs
`gitleaks protect --staged` — the same `zricethezav/gitleaks` image and `.gitleaks.toml`
allowlist as the `make security-scan` gitleaks check above, but scoped to the staged diff instead
of the whole working tree, so it's fast enough to run on every commit. A commit is blocked if it
introduces a secret.

Enable it once per clone with `pre-commit install` (see
[OpenAPI Workflow — Git hooks](openapi-workflow.md#git-hooks)). Caveats:

- It only runs if `pre-commit install` has been run locally — nothing enforces that today, so a
  fresh clone (or a commit made with `--no-verify`) isn't protected.
- It isn't a CI gate either — a secret that slips past a local commit (hook not installed, or
  bypassed) isn't caught until/unless someone runs `make security-scan`'s full gitleaks pass over
  the whole repo.

## Secret scanning allowlist

Gitleaks path exclusions live in `.gitleaks.toml` (repo root), not a CLI flag — `--ignore-path`
isn't a real gitleaks option. The `output/` directory (scan results, which can themselves contain
matched secrets in context snippets) is allowlisted there.

## Notes

- Historical design/plan docs for this script live under `docs/source/plans/` and
  `docs/source/specs/` — they describe an earlier iteration (sequential runs, an `IMAGE_CHANNEL`
  override for scanning published GHCR images) that no longer matches the shipped script. This
  page is the source of truth for current behavior.
- See [Secrets & Environment Variables](secrets-reference.md) for how `SERVICE_CLIENT_SECRET` and
  other credentials referenced above are configured.
