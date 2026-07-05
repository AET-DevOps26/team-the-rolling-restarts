#!/usr/bin/env bash
# Blocks a commit if the staged diff contains secrets. Reuses the same
# gitleaks image and .gitleaks.toml allowlist as `make security-scan`'s
# run_gitleaks, but scans only the staged changes (fast enough for every
# commit) instead of the whole working tree.
set -eu

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

if ! command -v docker > /dev/null 2>&1; then
  echo "error: docker is required for the gitleaks pre-commit hook (no local gitleaks install needed, but Docker must be running)." >&2
  exit 1
fi

docker run --rm \
    -v "${REPO_ROOT}:/repo" \
    -w /repo \
    zricethezav/gitleaks:latest \
    protect --staged --redact -v
