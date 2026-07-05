# Security Scanning

Local security/quality scanning via `infra/scripts/security-scan.sh`, exposed as a single
Makefile entrypoint. Not wired into CI — run it manually before a release or when touching
infra/Dockerfiles. Secret scanning specifically also runs automatically as a pre-commit and
pre-push hook — see [Pre-commit and pre-push secret scanning](#pre-commit-and-pre-push-secret-scanning)
below.

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

## Pre-commit and pre-push secret scanning

`infra/scripts/gitleaks-check.sh` is a shared wrapper around the same `zricethezav/gitleaks`
image and `.gitleaks.toml` allowlist as the `make security-scan` gitleaks check above, scoped
narrower so it's fast enough to run on every commit/push:

| Hook | Wired from | Runs | Scope |
| ---- | ---------- | ---- | ----- |
| pre-commit | `.pre-commit-config.yaml`'s `gitleaks` hook | `gitleaks-check.sh --staged` (`gitleaks protect --staged`) | The staged diff |
| pre-push | `scripts/git-hooks/pre-push` | `gitleaks-check.sh --push` (`gitleaks git --log-opts=...`) | Every commit being pushed, one `git log <remote-sha>..<local-sha>` range per ref (falls back to a merge-base or full-history scan for a new branch, or if the remote commit isn't available locally) |

Either one blocks the commit/push if it finds a secret.

Enable both once per clone:

```bash
pre-commit install     # pre-commit hook
make install-hooks     # pre-push hook (also regenerates the OpenAPI contract — see openapi-workflow.md)
```

See [OpenAPI Workflow — Git hooks](openapi-workflow.md#git-hooks). Caveats:

- Both only run if that install step has been run locally — nothing enforces that today, so a
  fresh clone (or a commit/push made with `--no-verify`) isn't protected.
- Neither is a CI gate — a secret that slips past both local hooks (not installed, or bypassed)
  isn't caught until/unless someone runs `make security-scan`'s full gitleaks pass over the whole
  repo.

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
