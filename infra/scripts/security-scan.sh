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

print_summary() {
  echo ""
  echo "════════════════════════════════════════════"
  echo " Security scan summary"
  echo "════════════════════════════════════════════"
  printf "%-40s %-8s %s\n" "Tool / Target" "Status" "Findings"
  echo "────────────────────────────────────────────"

  # Print one row per SARIF output file
  for f in "${OUTPUT_DIR}"/*.json; do
    [ -f "$f" ] || continue
    local name; name=$(basename "$f" .json)
    local count; count=$(sarif_count "$f")
    local status_icon="${GREEN}OK${RESET}"
    # Flag files that contain a SCAN_ERROR or CODEOWNERS_NOT_FOUND result
    local has_error; has_error=$(jq '[.runs[].results[].ruleId] | map(select(. == "SCAN_ERROR")) | length' "$f" 2>/dev/null || echo 0)
    [ "$has_error" -gt 0 ] && status_icon="${RED}ERR${RESET}"
    printf "%-40s ${status_icon}    %s\n" "$name" "$count"
  done

  echo "────────────────────────────────────────────"
  local total_files; total_files=$(ls "${OUTPUT_DIR}"/*.json 2>/dev/null | wc -l)
  local total_findings; total_findings=$(
    for f in "${OUTPUT_DIR}"/*.json; do
      jq '[.runs[].results | length] | add // 0' "$f" 2>/dev/null
    done | awk '{s+=$1} END {print s+0}'
  )
  printf "%-40s %-8s %s\n" "TOTAL (${total_files} files)" "" "${total_findings}"
  echo ""
  echo "Output directory: ${OUTPUT_DIR}"
  echo "View with: make score"
  echo ""
}

build_local_images() {
  info "Building local images for trivy/dockle scanning..."
  local -A builds=(
    ["web-client"]="./web-client|./web-client/Dockerfile|production"
    ["api-gateway"]="./services/spring|./services/spring/api-gateway/Dockerfile|"
    ["user-service"]="./services/spring|./services/spring/user-service/Dockerfile|"
    ["content-service"]="./services/spring|./services/spring/content-service/Dockerfile|"
    ["gen-ai"]="./services/gen-ai|./services/gen-ai/Dockerfile|"
  )
  for svc in web-client api-gateway user-service content-service gen-ai; do
    local spec="${builds[$svc]}"
    local ctx; ctx=$(echo "$spec" | cut -d'|' -f1)
    local file; file=$(echo "$spec" | cut -d'|' -f2)
    local target; target=$(echo "$spec" | cut -d'|' -f3)
    local tag="rolling-restarts/${svc}:local"
    local build_args=(-t "$tag" -f "$file" --quiet)
    [ -n "$target" ] && build_args+=(--target "$target")
    build_args+=("$ctx")
    if docker build "${build_args[@]}" > /dev/null 2>&1; then
      ok "Built ${tag}"
    else
      fail "Failed to build ${tag} (will skip trivy/dockle for this service)"
    fi
  done
}

run_trivy_fs() {
  local out="${OUTPUT_DIR}/trivy_fs_report.json"
  info "Running trivy fs (source filesystem vulnerability scanning)..."
  docker run --rm \
      -v "${REPO_ROOT}:/repo:ro" \
      aquasec/trivy:latest \
      fs -f sarif -o /dev/stdout --quiet \
      --skip-dirs .git,node_modules,web-client/node_modules,web-client/.next \
      /repo > "$out" 2>/dev/null || true
  if jq empty "$out" 2>/dev/null; then
    local count; count=$(sarif_count "$out")
    TOOL_STATUS[trivy_fs]="ok"; TOOL_RESULTS[trivy_fs]="$count"
    ok "trivy fs — ${count} findings"
  else
    sarif_error "$out" "Trivy" "trivy fs scan failed to run."
    TOOL_STATUS[trivy_fs]="failed"; TOOL_RESULTS[trivy_fs]="?"
    fail "trivy fs failed to run"
  fi
}

run_trivy() {
  info "Running trivy (container image vulnerability scanning)..."
  local services=(web-client api-gateway user-service content-service gen-ai)

  # --- Local images ---
  for svc in "${services[@]}"; do
    local img="rolling-restarts/${svc}:local"
    local out="${OUTPUT_DIR}/trivy_image_${svc}.json"
    if ! docker image inspect "$img" > /dev/null 2>&1; then
      sarif_error "$out" "Trivy" "Image ${img} not available (build may have failed)."
      TOOL_STATUS["trivy_local_${svc}"]="failed"; TOOL_RESULTS["trivy_local_${svc}"]="?"
      fail "trivy (local): ${svc} image not available"
      continue
    fi
    # trivy exits non-zero when vulnerabilities found; capture output regardless
    docker run --rm \
        -v /var/run/docker.sock:/var/run/docker.sock \
        aquasec/trivy:latest \
        image -f sarif -o /dev/stdout --quiet "$img" > "$out" 2>/dev/null || true
    if jq empty "$out" 2>/dev/null; then
      local count; count=$(sarif_count "$out")
      TOOL_STATUS["trivy_local_${svc}"]="ok"; TOOL_RESULTS["trivy_local_${svc}"]="$count"
      ok "trivy (local) ${svc} — ${count} findings"
    else
      sarif_error "$out" "Trivy" "trivy scan failed for local image ${svc}."
      TOOL_STATUS["trivy_local_${svc}"]="failed"; TOOL_RESULTS["trivy_local_${svc}"]="?"
      fail "trivy (local) failed for ${svc}"
    fi
  done

}

run_dockle() {
  info "Running dockle (container image best-practice check)..."
  local services=(web-client api-gateway user-service content-service gen-ai)

  # --- Local images ---
  for svc in "${services[@]}"; do
    local img="rolling-restarts/${svc}:local"
    local out="${OUTPUT_DIR}/dockle_${svc}.json"
    if ! docker image inspect "$img" > /dev/null 2>&1; then
      sarif_error "$out" "Dockle" "Image ${img} not available (build may have failed)."
      TOOL_STATUS["dockle_local_${svc}"]="failed"; TOOL_RESULTS["dockle_local_${svc}"]="?"
      fail "dockle (local): ${svc} image not available"
      continue
    fi
    # dockle may exit non-zero on findings; capture output regardless
    docker run --rm \
        -v /var/run/docker.sock:/var/run/docker.sock \
        goodwithtech/dockle:latest \
        -f sarif --exit-code 0 "$img" > "$out" 2>/dev/null || true
    if jq empty "$out" 2>/dev/null; then
      local count; count=$(sarif_count "$out")
      TOOL_STATUS["dockle_local_${svc}"]="ok"; TOOL_RESULTS["dockle_local_${svc}"]="$count"
      ok "dockle (local) ${svc} — ${count} findings"
    else
      sarif_error "$out" "Dockle" "dockle scan failed for local image ${svc}."
      TOOL_STATUS["dockle_local_${svc}"]="failed"; TOOL_RESULTS["dockle_local_${svc}"]="?"
      fail "dockle (local) failed for ${svc}"
    fi
  done

}

run_codeowners_check() {
  local out="${OUTPUT_DIR}/codeowners_report.json"
  info "Checking for CODEOWNERS file..."
  local found=0
  for path in CODEOWNERS .github/CODEOWNERS docs/CODEOWNERS; do
    [ -f "${REPO_ROOT}/${path}" ] && found=1 && break
  done
  if [ "$found" -eq 1 ]; then
    cat > "$out" <<SARIF
{
  "version": "2.1.0",
  "\$schema": "http://json.schemastore.org/sarif-2.1.0",
  "runs": [{
    "tool": {"driver": {"name": "CODEOWNERS Check", "version": "1.0.0",
      "shortDescription": {"text": "Checks for the presence of a CODEOWNERS file in the repository."}}},
    "results": []
  }]
}
SARIF
    TOOL_STATUS[codeowners]="ok"; TOOL_RESULTS[codeowners]=0
    ok "CODEOWNERS found — 0 findings"
  else
    cat > "$out" <<SARIF
{
  "version": "2.1.0",
  "\$schema": "http://json.schemastore.org/sarif-2.1.0",
  "runs": [{
    "tool": {"driver": {"name": "CODEOWNERS Check", "version": "1.0.0",
      "shortDescription": {"text": "Checks for the presence of a CODEOWNERS file in the repository."}}},
    "results": [{
      "ruleId": "CODEOWNERS_NOT_FOUND",
      "level": "error",
      "message": {"text": "No CODEOWNERS file found in the repository."}
    }]
  }]
}
SARIF
    TOOL_STATUS[codeowners]="ok"; TOOL_RESULTS[codeowners]=1
    fail "No CODEOWNERS file found — 1 finding"
  fi
}

run_gitleaks() {
  local out="${OUTPUT_DIR}/gitleaks_report.json"
  info "Running gitleaks (secret scanning)..."
  if docker run --rm \
      -v "${REPO_ROOT}:/repo" \
      zricethezav/gitleaks:latest \
      detect --source /repo --no-git -f sarif -r /repo/gitleaks_tmp.json \
      --exit-code 0 2>/dev/null; then
    mv "${REPO_ROOT}/gitleaks_tmp.json" "$out"
    local count; count=$(sarif_count "$out")
    TOOL_STATUS[gitleaks]="ok"; TOOL_RESULTS[gitleaks]="$count"
    ok "gitleaks — ${count} findings"
  else
    sarif_error "$out" "gitleaks" "gitleaks scan failed to run."
    TOOL_STATUS[gitleaks]="failed"; TOOL_RESULTS[gitleaks]="?"
    fail "gitleaks failed to run"
  fi
  rm -f "${REPO_ROOT}/gitleaks_tmp.json"
}

run_hadolint() {
  info "Running hadolint (Dockerfile linting)..."
  local dockerfiles=(
    "web-client/Dockerfile"
    "services/gen-ai/Dockerfile"
    "services/spring/api-gateway/Dockerfile"
    "services/spring/user-service/Dockerfile"
    "services/spring/content-service/Dockerfile"
  )
  for df in "${dockerfiles[@]}"; do
    local out="${OUTPUT_DIR}/hadolint_$(echo "${df}" | sed 's|/|_|g').json"
    if [ ! -f "${REPO_ROOT}/${df}" ]; then
      sarif_error "$out" "Hadolint" "Dockerfile not found: ${df}"
      TOOL_STATUS["hadolint_${df}"]="failed"; TOOL_RESULTS["hadolint_${df}"]="?"
      fail "hadolint: ${df} not found"
      continue
    fi
    # hadolint exits non-zero when findings exist; capture output regardless
    docker run --rm \
        -v "${REPO_ROOT}/${df}:/Dockerfile:ro" \
        hadolint/hadolint:latest \
        hadolint -f sarif /Dockerfile > "$out" 2>/dev/null || true
    if jq empty "$out" 2>/dev/null; then
      local count; count=$(sarif_count "$out")
      TOOL_STATUS["hadolint_${df}"]="ok"; TOOL_RESULTS["hadolint_${df}"]="$count"
      ok "hadolint ${df} — ${count} findings"
    else
      sarif_error "$out" "Hadolint" "hadolint failed for ${df}"
      TOOL_STATUS["hadolint_${df}"]="failed"; TOOL_RESULTS["hadolint_${df}"]="?"
      fail "hadolint failed for ${df}"
    fi
  done
}

run_kics() {
  local out="${OUTPUT_DIR}/kics_report.json"
  info "Running KICS (IaC security scanning)..."
  local tmpdir; tmpdir=$(mktemp -d)
  # kics exits non-zero when findings exist; run without checking exit code
  docker run --rm \
      -v "${REPO_ROOT}:/repo:ro" \
      -v "${tmpdir}:/out" \
      checkmarx/kics:latest \
      scan -p /repo --report-formats sarif --output-path /out \
      --output-name kics_report --no-progress \
      --exclude-paths "/repo/.git,/repo/node_modules,/repo/web-client/node_modules" \
      2>/dev/null || true
  # kics writes kics_report.sarif; rename to .json to match reference naming
  if [ -f "${tmpdir}/kics_report.sarif" ]; then
    mv "${tmpdir}/kics_report.sarif" "$out"
    local count; count=$(sarif_count "$out")
    TOOL_STATUS[kics]="ok"; TOOL_RESULTS[kics]="$count"
    ok "kics — ${count} findings"
  else
    sarif_error "$out" "KICS" "kics scan failed to run."
    TOOL_STATUS[kics]="failed"; TOOL_RESULTS[kics]="?"
    fail "kics failed to run"
  fi
  rm -rf "$tmpdir"
}

run_zizmor() {
  local out="${OUTPUT_DIR}/zizmor_report.json"
  info "Running zizmor (GitHub Actions workflow security)..."
  # zizmor requires individual file arguments, not a directory
  local workflow_files=()
  while IFS= read -r -d '' f; do
    workflow_files+=("/workflows/$(basename "$f")")
  done < <(find "${REPO_ROOT}/.github/workflows" -maxdepth 1 \( -name "*.yml" -o -name "*.yaml" \) -print0 2>/dev/null)
  if [ "${#workflow_files[@]}" -eq 0 ]; then
    sarif_error "$out" "zizmor" "No workflow files found in .github/workflows."
    TOOL_STATUS[zizmor]="failed"; TOOL_RESULTS[zizmor]="?"
    fail "zizmor: no workflow files found"
    return
  fi
  # zizmor exits non-zero when findings exist; capture output regardless
  docker run --rm \
      -v "${REPO_ROOT}/.github/workflows:/workflows:ro" \
      ghcr.io/zizmorcore/zizmor:latest \
      --format sarif "${workflow_files[@]}" > "$out" 2>/dev/null || true
  if jq empty "$out" 2>/dev/null; then
    local count; count=$(sarif_count "$out")
    TOOL_STATUS[zizmor]="ok"; TOOL_RESULTS[zizmor]="$count"
    ok "zizmor — ${count} findings"
  else
    sarif_error "$out" "zizmor" "zizmor scan failed to run."
    TOOL_STATUS[zizmor]="failed"; TOOL_RESULTS[zizmor]="?"
    fail "zizmor failed to run"
  fi
}

run_typos() {
  local out="${OUTPUT_DIR}/typos_report.json"
  info "Running typos (spell checking)..."
  # typos exits non-zero when typos are found; capture output regardless
  docker run --rm \
      -v "${REPO_ROOT}:/repo:ro" \
      -w /repo \
      python:3.12-slim \
      sh -c "pip install typos -q > /dev/null 2>&1 && typos --format sarif ." > "$out" 2>/dev/null || true
  if jq empty "$out" 2>/dev/null; then
    local count; count=$(sarif_count "$out")
    TOOL_STATUS[typos]="ok"; TOOL_RESULTS[typos]="$count"
    ok "typos — ${count} findings"
  else
    sarif_error "$out" "typos" "typos scan failed to run."
    TOOL_STATUS[typos]="failed"; TOOL_RESULTS[typos]="?"
    fail "typos failed to run"
  fi
}

run_npm_audit() {
  info "Running npm audit (dependency vulnerability check)..."
  local package_files=("package.json" "web-client/package.json")
  for pkg in "${package_files[@]}"; do
    # Flatten path: package.json -> npm_audit_package.json,
    #               web-client/package.json -> npm_audit_web-client_package.json
    local flat; flat=$(echo "${pkg%.json}" | tr '/' '_')
    local out="${OUTPUT_DIR}/npm_audit_${flat}.json"
    local pkg_dir; pkg_dir="${REPO_ROOT}/$(dirname "$pkg")"

    if [ ! -f "${REPO_ROOT}/${pkg}" ]; then
      sarif_error "$out" "npm audit" "We could NOT run npm audit for ${pkg}."
      TOOL_STATUS["npm_${pkg}"]="failed"; TOOL_RESULTS["npm_${pkg}"]="?"
      fail "npm audit: ${pkg} not found"
      continue
    fi

    local audit_json
    audit_json=$(docker run --rm \
      -v "${pkg_dir}:/app:ro" \
      -w /app \
      node:lts-slim \
      sh -c "npm audit --json 2>/dev/null || true" 2>/dev/null)

    if [ -z "$audit_json" ]; then
      sarif_error "$out" "npm audit" "We could NOT run npm audit for ${pkg}."
      TOOL_STATUS["npm_${pkg}"]="failed"; TOOL_RESULTS["npm_${pkg}"]="?"
      fail "npm audit: no output for ${pkg}"
      continue
    fi

    # Parse vulnerability counts from npm audit JSON output
    local total critical high moderate low info
    total=$(echo "$audit_json" | jq '.metadata.vulnerabilities.total // 0' 2>/dev/null || echo 0)
    critical=$(echo "$audit_json" | jq '.metadata.vulnerabilities.critical // 0' 2>/dev/null || echo 0)
    high=$(echo "$audit_json" | jq '.metadata.vulnerabilities.high // 0' 2>/dev/null || echo 0)

    local level="note"
    [ "$high" -gt 0 ] || [ "$critical" -gt 0 ] && level="error"
    [ "$total" -gt 0 ] && [ "$level" = "note" ] && level="warning"

    local msg
    if [ "$total" -eq 0 ]; then
      msg="No vulnerabilities found in ${pkg}."
    else
      msg="${total} vulnerabilities found in ${pkg} (critical: ${critical}, high: ${high})."
    fi

    cat > "$out" <<SARIF
{
  "version": "2.1.0",
  "\$schema": "http://json.schemastore.org/sarif-2.1.0",
  "runs": [{
    "tool": {"driver": {"name": "npm audit", "version": "1.0.0",
      "shortDescription": {"text": "Checks for vulnerabilities in npm dependencies."}}},
    "results": [{
      "ruleId": "NPM_AUDIT",
      "level": "${level}",
      "message": {"text": "${msg}"}
    }]
  }]
}
SARIF
    local count; count=$(sarif_count "$out")
    TOOL_STATUS["npm_${pkg}"]="ok"; TOOL_RESULTS["npm_${pkg}"]="$count"
    if [ "$total" -eq 0 ]; then ok "npm audit ${pkg} — 0 findings"
    else fail "npm audit ${pkg} — ${total} vulnerabilities"; fi
  done
}

# --- Main ---
rm -rf "${OUTPUT_DIR}"
mkdir -p "${OUTPUT_DIR}"
cd "${REPO_ROOT}"

echo ""
echo "Security scan — writing to ${OUTPUT_DIR}"
echo ""

build_local_images

trap 'echo ""; echo "Interrupted — killing scanners…"; kill 0; exit 130' INT TERM

echo "Running scanners in parallel…"
run_gitleaks         > /dev/null 2>&1 &
run_trivy_fs         > /dev/null 2>&1 &
run_trivy            > /dev/null 2>&1 &
run_dockle           > /dev/null 2>&1 &
run_hadolint         > /dev/null 2>&1 &
run_kics             > /dev/null 2>&1 &
run_zizmor           > /dev/null 2>&1 &
run_npm_audit        > /dev/null 2>&1 &
run_codeowners_check > /dev/null 2>&1 &
run_typos            > /dev/null 2>&1 &
wait

print_summary
