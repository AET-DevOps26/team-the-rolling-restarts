#!/usr/bin/env bash
# Runs nine security/quality scanners via Docker and writes SARIF 2.1.0 output
# to output/AET-DevOps26/team-the-rolling-restarts/ for review with the
# guestlecture TUI: docker run -it --rm -v $(pwd)/output/AET-DevOps26:/data \
#   ghcr.io/pstoeckle/guestlecture:v0.1.3 /data
set -u

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUTPUT_DIR="${REPO_ROOT}/output/AET-DevOps26/team-the-rolling-restarts"
REGISTRY="ghcr.io/aet-devops26/team-the-rolling-restarts"

# Derive the published image channel from the current git branch (matches upload_images.yml logic).
# Override: IMAGE_CHANNEL=main make security-scan
_branch="$(git -C "${REPO_ROOT}" branch --show-current 2>/dev/null || echo wip)"
case "$_branch" in
  main) _default_channel=main ;;
  dev)  _default_channel=dev  ;;
  *)    _default_channel=wip  ;;
esac
IMAGE_CHANNEL="${IMAGE_CHANNEL:-${_default_channel}}"

# Track overall pass/fail per tool for the summary
declare -A TOOL_STATUS   # "ok" | "failed"
declare -A TOOL_RESULTS  # finding count (from jq) or "?" on failure

GREEN='\033[0;32m'; RED='\033[0;31m'; YELLOW='\033[0;33m'; RESET='\033[0m'
ok()   { printf "  ${GREEN}✓${RESET} %s\n" "$*"; }
fail() { printf "  ${RED}✗${RESET} %s\n" "$*"; }
info() { printf "  ${YELLOW}→${RESET} %s\n" "$*"; }

# Write a minimal SARIF 2.1.0 note result to a file when a tool cannot run.
# Usage: sarif_error <output_file> <tool_name> <message>
sarif_error() {
  local file="$1" tool="$2" msg="$3"
  cat > "$file" <<SARIF
{
  "version": "2.1.0",
  "\$schema": "http://json.schemastore.org/sarif-2.1.0",
  "runs": [{
    "tool": {"driver": {"name": "${tool}", "version": "1.0.0"}},
    "results": [{
      "ruleId": "SCAN_ERROR",
      "level": "note",
      "message": {"text": "${msg}"}
    }]
  }]
}
SARIF
}

# Count results in a SARIF file via jq. Prints 0 if file missing or unparseable.
sarif_count() {
  local file="$1"
  jq '[.runs[].results | length] | add // 0' "$file" 2>/dev/null || echo "?"
}

# --- Placeholder functions (implemented in later tasks) ---
build_local_images()    { info "build_local_images: not yet implemented"; }
pull_published_images() { info "pull_published_images: not yet implemented"; }
run_gitleaks()          { info "run_gitleaks: not yet implemented"; }
run_trivy()             { info "run_trivy: not yet implemented"; }
run_dockle()            { info "run_dockle: not yet implemented"; }
run_hadolint()          { info "run_hadolint: not yet implemented"; }
run_kics()              { info "run_kics: not yet implemented"; }
run_zizmor()            { info "run_zizmor: not yet implemented"; }
run_npm_audit()         { info "run_npm_audit: not yet implemented"; }
run_codeowners_check()  { info "run_codeowners_check: not yet implemented"; }
run_typos()             { info "run_typos: not yet implemented"; }
print_summary()         { info "print_summary: not yet implemented"; }

# --- Main ---
mkdir -p "${OUTPUT_DIR}"
cd "${REPO_ROOT}"

echo ""
echo "Security scan — writing to ${OUTPUT_DIR}"
echo "Published image channel: ${IMAGE_CHANNEL} (override: IMAGE_CHANNEL=<tag> make security-scan)"
echo ""

build_local_images
pull_published_images
run_gitleaks
run_trivy
run_dockle
run_hadolint
run_kics
run_zizmor
run_npm_audit
run_codeowners_check
run_typos
print_summary
