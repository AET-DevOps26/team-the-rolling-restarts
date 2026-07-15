#!/usr/bin/env bash
# Shared secrets guard for git hooks. Reuses the same gitleaks image and .gitleaks.toml
# allowlist as `make security-scan`'s gitleaks check, just scoped narrower (staged diff /
# pushed commits only) so it's fast enough to run on every commit and push.
#
# Usage:
#   gitleaks-check.sh --staged   pre-commit: scan the staged diff.
#                                 Wired from .pre-commit-config.yaml.
#   gitleaks-check.sh --push     pre-push: scan the commit range(s) being pushed, read from
#                                 stdin as one "<local ref> <local sha> <remote ref> <remote
#                                 sha>" line per ref, exactly as git passes to a pre-push hook.
#                                 Wired from scripts/git-hooks/pre-push.
set -eu

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ZERO="0000000000000000000000000000000000000000"
IMAGE="zricethezav/gitleaks:latest"

if ! command -v docker > /dev/null 2>&1; then
  echo "error: docker is required for the gitleaks git hook (no local gitleaks install needed, but Docker must be running)." >&2
  exit 1
fi

run_gitleaks() {
  docker run --rm -v "${REPO_ROOT}:/repo" -w /repo "$IMAGE" "$@"
}

case "${1:-}" in
  --staged)
    run_gitleaks protect --staged --redact -v
    ;;

  --push)
    status=0
    while read -r local_ref local_sha _remote_ref remote_sha; do
      [ "$local_sha" = "$ZERO" ] && continue   # deleting a branch — nothing to scan

      # A missing/invalid revision range makes gitleaks silently report "0 commits scanned,
      # no leaks" and exit 0 (fails open) instead of erroring — so treat remote_sha as usable
      # only if it actually resolves to a commit we have locally (covers new branches and the
      # rarer case of a remote_sha this clone doesn't have, e.g. a shallow clone or a pruned ref).
      if [ "$remote_sha" != "$ZERO" ] && git -C "$REPO_ROOT" cat-file -e "${remote_sha}^{commit}" 2>/dev/null; then
        range="${remote_sha}..${local_sha}"
      else
        # No usable remote history to diff against. Fall back to the merge-base with whichever
        # integration branch is known locally, so we scan only this branch's own commits
        # instead of re-scanning history already on dev/main. If neither is available locally,
        # scan the full history reachable from local_sha.
        base=$(git -C "$REPO_ROOT" merge-base "$local_sha" origin/dev 2>/dev/null \
            || git -C "$REPO_ROOT" merge-base "$local_sha" origin/main 2>/dev/null \
            || true)
        range="${base:+${base}..}${local_sha}"
      fi

      echo "[pre-push] Scanning ${local_ref} (${range}) for secrets..."
      run_gitleaks git . --log-opts="${range}" --redact -v || status=1
    done
    exit "$status"
    ;;

  *)
    echo "usage: $0 --staged|--push" >&2
    exit 2
    ;;
esac
