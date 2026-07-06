# Security Findings Script Implementation Plan

> **Historical — superseded.** This was the pre-implementation plan; the
> shipped script has since diverged from it in several ways (scanners run in
> parallel rather than sequentially, `IMAGE_CHANNEL`/published-image scanning
> was dropped, trivy/dockle now scan only locally-built images). For current
> behavior see [Security Scanning](../security-scanning.md) or read
> `infra/scripts/security-scan.sh` directly. Kept for historical context only.
>
> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Write `infra/scripts/security-scan.sh` — a single Bash script that runs nine security/quality tools via Docker, writes SARIF 2.1.0 output into `output/AET-DevOps26/team-the-rolling-restarts/`, and prints a terminal summary; exposed as `make security-scan`.

**Architecture:** One Bash script (~450 lines), one function per tool, runs sequentially. Each function catches its own failures and writes a placeholder SARIF note on error rather than aborting. trivy and dockle scan both locally-built images and published GHCR images; all other tools run against source only. A `print_summary` function at the end parses every output file with `jq` and prints per-tool result counts.

**Tech Stack:** Bash, Docker (all nine tools run as containers), `jq` (summary parsing — pre-installed on ubuntu/WSL), `python3 -m json.tool` (ad hoc SARIF validation in tests).

## Global Constraints

- All tool invocations use Docker — no local tool installs assumed or required.
- Output directory: `output/AET-DevOps26/team-the-rolling-restarts/` (created by the script if absent).
- All output files are SARIF 2.1.0 JSON (`.json` extension, even when the tool natively produces `.sarif`).
- Exactly ONE Makefile target (`security-scan`) — no per-tool targets.
- Script follows `infra/scripts/smoke-test.sh` conventions: `#!/usr/bin/env bash`, `set -u`, colored output helpers, no pipefail (manual error capture per command).
- Local image tag pattern: `rolling-restarts/<service>:local` (e.g. `rolling-restarts/api-gateway:local`).
- Published image pattern: `ghcr.io/aet-devops26/team-the-rolling-restarts/<service>:<channel>` where channel = `main`|`dev`|`wip` derived from current git branch, overridable via `IMAGE_CHANNEL=<tag>`.
- kics natively produces `<name>.sarif`; script renames it to `<name>.json` before it becomes an output file.
- `output/` is not gitignored — generated SARIF files can be committed if desired.

---

## File Map

| Action | Path | Purpose |
|--------|------|---------|
| Create | `infra/scripts/security-scan.sh` | The entire script |
| Modify | `Makefile` | Add `security-scan` to `.PHONY` and `help`, add target |

---

## Task 1: Script skeleton + Makefile target

**Files:**
- Create: `infra/scripts/security-scan.sh`
- Modify: `Makefile` (`.PHONY` line, `help` block, new target near the `smoke-test` group)

**Interfaces:**
- Produces: `make security-scan` runs, creates output dir, exits 0

- [ ] **Step 1: Add `security-scan` to `.PHONY` and `help` in `Makefile`**

In `Makefile` line 1, add `security-scan \` to the `.PHONY` list (after `smoke-test-k8s`).

In the `help` target, add a new section after the `Docker Compose (local):` block:

```makefile
  '' \
  'Security:' \
  '  make security-scan        - run all security/quality scanners and write SARIF to output/' \
  '  make security-scan IMAGE_CHANNEL=dev - scan against a specific published image channel' \
```

Add the target near the `smoke-test` block:

```makefile
security-scan:
	@infra/scripts/security-scan.sh
```

- [ ] **Step 2: Create the script skeleton**

Create `infra/scripts/security-scan.sh`:

```bash
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
```

- [ ] **Step 3: Make the script executable**

```bash
chmod +x infra/scripts/security-scan.sh
```

- [ ] **Step 4: Verify `make security-scan` runs and creates the output directory**

```bash
make security-scan
```

Expected: prints tool stub messages, exits 0. Then:

```bash
ls output/AET-DevOps26/team-the-rolling-restarts/
```

Expected: directory exists (possibly empty at this point).

- [ ] **Step 5: Commit**

```bash
git add infra/scripts/security-scan.sh Makefile
git commit -m "chore: scaffold security-scan script and Makefile target"
```

---

## Task 2: Source-scanning tools (gitleaks, hadolint, kics, zizmor, typos, codeowners, npm audit)

These seven tools scan source files only — no container images need to be built first.

**Files:**
- Modify: `infra/scripts/security-scan.sh` (replace seven stub functions)

**Interfaces:**
- Consumes: `OUTPUT_DIR`, `REPO_ROOT`, `sarif_error()`, `TOOL_STATUS`, `TOOL_RESULTS` from Task 1
- Produces: 11 SARIF `.json` files in `$OUTPUT_DIR` after this task

The five current Dockerfiles to scan with hadolint:
- `web-client/Dockerfile`
- `services/gen-ai/Dockerfile`
- `services/spring/api-gateway/Dockerfile`
- `services/spring/user-service/Dockerfile`
- `services/spring/content-service/Dockerfile`

The two `package.json` files for npm audit: `package.json` (repo root), `web-client/package.json`.

- [ ] **Step 1: Verify no output files exist yet**

```bash
ls output/AET-DevOps26/team-the-rolling-restarts/*.json 2>&1
```

Expected: `No such file or directory` or empty listing.

- [ ] **Step 2: Implement `run_codeowners_check`**

Replace the stub in `infra/scripts/security-scan.sh`:

```bash
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
```

- [ ] **Step 3: Implement `run_gitleaks`**

```bash
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
```

- [ ] **Step 4: Implement `run_hadolint`**

```bash
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
    # Flatten path to filename: replace / with _ and remove leading part
    local fname; fname="hadolint_$(echo "$df" | tr '/' '_' | sed 's/_Dockerfile$//'| sed 's/^_//')_Dockerfile.json"
    # Remove leading _ if path starts at repo root
    fname="${OUTPUT_DIR}/$(echo "$fname" | sed 's|^hadolint_\(.*\)|\1|' | sed 's|^|hadolint_|')"
    local out="${OUTPUT_DIR}/hadolint_$(echo "${df}" | sed 's|/|_|g').json"
    if [ ! -f "${REPO_ROOT}/${df}" ]; then
      sarif_error "$out" "Hadolint" "Dockerfile not found: ${df}"
      TOOL_STATUS["hadolint_${df}"]="failed"; TOOL_RESULTS["hadolint_${df}"]="?"
      fail "hadolint: ${df} not found"
      continue
    fi
    if docker run --rm \
        -v "${REPO_ROOT}/${df}:/Dockerfile:ro" \
        hadolint/hadolint:latest \
        hadolint -f sarif /Dockerfile > "$out" 2>/dev/null; then
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
```

- [ ] **Step 5: Implement `run_kics`**

```bash
run_kics() {
  local out="${OUTPUT_DIR}/kics_report.json"
  info "Running KICS (IaC security scanning)..."
  local tmpdir; tmpdir=$(mktemp -d)
  if docker run --rm \
      -v "${REPO_ROOT}:/repo:ro" \
      -v "${tmpdir}:/out" \
      checkmarx/kics:latest \
      scan -p /repo --report-formats sarif --output-path /out \
      --output-name kics_report --no-progress \
      --exclude-paths "/repo/.git,/repo/node_modules,/repo/web-client/node_modules" \
      2>/dev/null; then
    # kics writes kics_report.sarif; rename to .json to match reference naming
    if [ -f "${tmpdir}/kics_report.sarif" ]; then
      mv "${tmpdir}/kics_report.sarif" "$out"
      local count; count=$(sarif_count "$out")
      TOOL_STATUS[kics]="ok"; TOOL_RESULTS[kics]="$count"
      ok "kics — ${count} findings"
    else
      sarif_error "$out" "KICS" "kics ran but produced no output file."
      TOOL_STATUS[kics]="failed"; TOOL_RESULTS[kics]="?"
      fail "kics: no output file produced"
    fi
  else
    sarif_error "$out" "KICS" "kics scan failed to run."
    TOOL_STATUS[kics]="failed"; TOOL_RESULTS[kics]="?"
    fail "kics failed to run"
  fi
  rm -rf "$tmpdir"
}
```

- [ ] **Step 6: Implement `run_zizmor`**

```bash
run_zizmor() {
  local out="${OUTPUT_DIR}/zizmor_report.json"
  info "Running zizmor (GitHub Actions workflow security)..."
  if docker run --rm \
      -v "${REPO_ROOT}/.github/workflows:/workflows:ro" \
      ghcr.io/zizmorcore/zizmor:latest \
      --format sarif /workflows > "$out" 2>/dev/null; then
    local count; count=$(sarif_count "$out")
    TOOL_STATUS[zizmor]="ok"; TOOL_RESULTS[zizmor]="$count"
    ok "zizmor — ${count} findings"
  else
    sarif_error "$out" "zizmor" "zizmor scan failed to run."
    TOOL_STATUS[zizmor]="failed"; TOOL_RESULTS[zizmor]="?"
    fail "zizmor failed to run"
  fi
}
```

- [ ] **Step 7: Implement `run_typos`**

```bash
run_typos() {
  local out="${OUTPUT_DIR}/typos_report.json"
  info "Running typos (spell checking)..."
  if docker run --rm \
      -v "${REPO_ROOT}:/repo:ro" \
      -w /repo \
      python:3.12-slim \
      sh -c "pip install typos -q && typos --format sarif ." > "$out" 2>/dev/null; then
    local count; count=$(sarif_count "$out")
    TOOL_STATUS[typos]="ok"; TOOL_RESULTS[typos]="$count"
    ok "typos — ${count} findings"
  else
    sarif_error "$out" "typos" "typos scan failed to run."
    TOOL_STATUS[typos]="failed"; TOOL_RESULTS[typos]="?"
    fail "typos failed to run"
  fi
}
```

- [ ] **Step 8: Implement `run_npm_audit`**

npm audit has no native SARIF output; this function wraps it into a minimal SARIF shell.

```bash
run_npm_audit() {
  info "Running npm audit (dependency vulnerability check)..."
  local package_files=("package.json" "web-client/package.json")
  for pkg in "${package_files[@]}"; do
    # Flatten path: package.json -> npm_audit_package.json.json,
    #               web-client/package.json -> npm_audit_web-client_package.json.json
    local flat; flat=$(echo "$pkg" | tr '/' '_')
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
```

- [ ] **Step 9: Run the script and verify source-tool output files exist**

```bash
make security-scan 2>&1 | grep -E "✓|✗|→"
```

Then verify files were created:

```bash
ls output/AET-DevOps26/team-the-rolling-restarts/*.json | wc -l
```

Expected: `11` (1 codeowners + 1 gitleaks + 5 hadolint + 1 kics + 1 zizmor + 1 typos + 2 npm audit).

Validate each is parseable JSON:

```bash
for f in output/AET-DevOps26/team-the-rolling-restarts/*.json; do
  python3 -m json.tool "$f" > /dev/null && echo "OK: $f" || echo "FAIL: $f"
done
```

Expected: all print `OK`.

- [ ] **Step 10: Commit**

```bash
git add infra/scripts/security-scan.sh
git commit -m "feat(security): implement source-scanning tools (gitleaks, hadolint, kics, zizmor, typos, npm audit, codeowners)"
```

---

## Task 3: Local image builds + trivy + dockle (local)

Build each of the five service images locally and scan them with trivy and dockle.

**Files:**
- Modify: `infra/scripts/security-scan.sh` (replace `build_local_images`, `run_trivy` stub, `run_dockle` stub)

**Interfaces:**
- Consumes: `REPO_ROOT`, `OUTPUT_DIR`, `sarif_error()`, `sarif_count()`, `TOOL_STATUS`, `TOOL_RESULTS`
- Produces: 5 `trivy_image_<service>.json` + 5 `dockle_<service>.json` files

Local image names: `rolling-restarts/<service>:local` for services `web-client`, `api-gateway`, `user-service`, `content-service`, `gen-ai`.

Build contexts and Dockerfiles:
- `web-client`: context `./web-client`, file `./web-client/Dockerfile`, target `production`
- `api-gateway`: context `./services/spring`, file `./services/spring/api-gateway/Dockerfile`
- `user-service`: context `./services/spring`, file `./services/spring/user-service/Dockerfile`
- `content-service`: context `./services/spring`, file `./services/spring/content-service/Dockerfile`
- `gen-ai`: context `./services/gen-ai`, file `./services/gen-ai/Dockerfile`

Note: web-client build requires `web-client/src/generated/api.ts` (generated by `make generate`). If it's absent the build will fail — the script degrades gracefully: the docker build error is caught, a placeholder SARIF note is written, and scanning continues with the other four services.

- [ ] **Step 1: Verify no trivy/dockle output files exist yet**

```bash
ls output/AET-DevOps26/team-the-rolling-restarts/trivy_* 2>&1
ls output/AET-DevOps26/team-the-rolling-restarts/dockle_* 2>&1
```

Expected: `No such file or directory`.

- [ ] **Step 2: Implement `build_local_images`**

Replace the stub:

```bash
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
```

- [ ] **Step 3: Implement `run_trivy` with a local-image loop**

Replace the stub (the published-image loop is added in Task 4 — keep a `# TODO: published` comment as a placeholder):

```bash
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
    if docker run --rm \
        -v /var/run/docker.sock:/var/run/docker.sock \
        aquasec/trivy:latest \
        image -f sarif -o /dev/stdout --quiet "$img" > "$out" 2>/dev/null; then
      local count; count=$(sarif_count "$out")
      TOOL_STATUS["trivy_local_${svc}"]="ok"; TOOL_RESULTS["trivy_local_${svc}"]="$count"
      ok "trivy (local) ${svc} — ${count} findings"
    else
      sarif_error "$out" "Trivy" "trivy scan failed for local image ${svc}."
      TOOL_STATUS["trivy_local_${svc}"]="failed"; TOOL_RESULTS["trivy_local_${svc}"]="?"
      fail "trivy (local) failed for ${svc}"
    fi
  done

  # TODO: published images — implemented in Task 4
}
```

- [ ] **Step 4: Implement `run_dockle` with a local-image loop**

```bash
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
    if docker run --rm \
        -v /var/run/docker.sock:/var/run/docker.sock \
        goodwithtech/dockle:latest \
        -f sarif --exit-code 0 "$img" > "$out" 2>/dev/null; then
      local count; count=$(sarif_count "$out")
      TOOL_STATUS["dockle_local_${svc}"]="ok"; TOOL_RESULTS["dockle_local_${svc}"]="$count"
      ok "dockle (local) ${svc} — ${count} findings"
    else
      sarif_error "$out" "Dockle" "dockle scan failed for local image ${svc}."
      TOOL_STATUS["dockle_local_${svc}"]="failed"; TOOL_RESULTS["dockle_local_${svc}"]="?"
      fail "dockle (local) failed for ${svc}"
    fi
  done

  # TODO: published images — implemented in Task 4
}
```

- [ ] **Step 5: Run and verify local trivy/dockle output files appear**

```bash
make security-scan 2>&1 | grep -E "✓|✗|→" | grep -E "trivy|dockle|build"
```

Then:

```bash
ls output/AET-DevOps26/team-the-rolling-restarts/trivy_image_*.json \
   output/AET-DevOps26/team-the-rolling-restarts/dockle_*.json | grep -v published
```

Expected: 5 `trivy_image_<service>.json` + 5 `dockle_<service>.json` (10 files, none with `_published`).

Validate JSON:

```bash
for f in output/AET-DevOps26/team-the-rolling-restarts/trivy_image_*.json \
          output/AET-DevOps26/team-the-rolling-restarts/dockle_*.json; do
  python3 -m json.tool "$f" > /dev/null && echo "OK: $(basename $f)" || echo "FAIL: $(basename $f)"
done
```

- [ ] **Step 6: Commit**

```bash
git add infra/scripts/security-scan.sh
git commit -m "feat(security): build local images and scan with trivy and dockle"
```

---

## Task 4: Published image pull + trivy + dockle (published)

Pull the five service images from GHCR using `IMAGE_CHANNEL` and scan them.

**Files:**
- Modify: `infra/scripts/security-scan.sh` (implement `pull_published_images`, replace the `# TODO: published` loops in `run_trivy` and `run_dockle`)

**Interfaces:**
- Consumes: `IMAGE_CHANNEL`, `REGISTRY` (`ghcr.io/aet-devops26/team-the-rolling-restarts`), `sarif_error()`, `sarif_count()`, `TOOL_STATUS`, `TOOL_RESULTS`
- Produces: 5 `trivy_image_<service>_published.json` + 5 `dockle_<service>_published.json` files

- [ ] **Step 1: Verify no `_published` files exist yet**

```bash
ls output/AET-DevOps26/team-the-rolling-restarts/*_published.json 2>&1
```

Expected: `No such file or directory`.

- [ ] **Step 2: Implement `pull_published_images`**

Replace the stub:

```bash
pull_published_images() {
  info "Pulling published images from GHCR (channel: ${IMAGE_CHANNEL})..."
  local services=(web-client api-gateway user-service content-service gen-ai)
  for svc in "${services[@]}"; do
    local img="${REGISTRY}/${svc}:${IMAGE_CHANNEL}"
    if docker pull --quiet "$img" > /dev/null 2>&1; then
      ok "Pulled ${img}"
    else
      fail "Could not pull ${img} (channel '${IMAGE_CHANNEL}' may not exist yet for ${svc})"
    fi
  done
}
```

- [ ] **Step 3: Replace `# TODO: published` in `run_trivy` with the published-image loop**

Remove the `# TODO: published images — implemented in Task 4` comment and add after the local loop:

```bash
  # --- Published images ---
  for svc in "${services[@]}"; do
    local img="${REGISTRY}/${svc}:${IMAGE_CHANNEL}"
    local out="${OUTPUT_DIR}/trivy_image_${svc}_published.json"
    if ! docker image inspect "$img" > /dev/null 2>&1; then
      sarif_error "$out" "Trivy" "Published image ${img} not available (pull may have failed)."
      TOOL_STATUS["trivy_pub_${svc}"]="failed"; TOOL_RESULTS["trivy_pub_${svc}"]="?"
      fail "trivy (published): ${svc} image not available"
      continue
    fi
    if docker run --rm \
        -v /var/run/docker.sock:/var/run/docker.sock \
        aquasec/trivy:latest \
        image -f sarif -o /dev/stdout --quiet "$img" > "$out" 2>/dev/null; then
      local count; count=$(sarif_count "$out")
      TOOL_STATUS["trivy_pub_${svc}"]="ok"; TOOL_RESULTS["trivy_pub_${svc}"]="$count"
      ok "trivy (published/${IMAGE_CHANNEL}) ${svc} — ${count} findings"
    else
      sarif_error "$out" "Trivy" "trivy scan failed for published image ${svc}."
      TOOL_STATUS["trivy_pub_${svc}"]="failed"; TOOL_RESULTS["trivy_pub_${svc}"]="?"
      fail "trivy (published) failed for ${svc}"
    fi
  done
```

- [ ] **Step 4: Replace `# TODO: published` in `run_dockle` with the published-image loop**

```bash
  # --- Published images ---
  for svc in "${services[@]}"; do
    local img="${REGISTRY}/${svc}:${IMAGE_CHANNEL}"
    local out="${OUTPUT_DIR}/dockle_${svc}_published.json"
    if ! docker image inspect "$img" > /dev/null 2>&1; then
      sarif_error "$out" "Dockle" "Published image ${img} not available (pull may have failed)."
      TOOL_STATUS["dockle_pub_${svc}"]="failed"; TOOL_RESULTS["dockle_pub_${svc}"]="?"
      fail "dockle (published): ${svc} image not available"
      continue
    fi
    if docker run --rm \
        -v /var/run/docker.sock:/var/run/docker.sock \
        goodwithtech/dockle:latest \
        -f sarif --exit-code 0 "$img" > "$out" 2>/dev/null; then
      local count; count=$(sarif_count "$out")
      TOOL_STATUS["dockle_pub_${svc}"]="ok"; TOOL_RESULTS["dockle_pub_${svc}"]="$count"
      ok "dockle (published/${IMAGE_CHANNEL}) ${svc} — ${count} findings"
    else
      sarif_error "$out" "Dockle" "dockle scan failed for published image ${svc}."
      TOOL_STATUS["dockle_pub_${svc}"]="failed"; TOOL_RESULTS["dockle_pub_${svc}"]="?"
      fail "dockle (published) failed for ${svc}"
    fi
  done
```

- [ ] **Step 5: Run and verify published output files appear**

```bash
make security-scan 2>&1 | grep -E "published"
```

Then:

```bash
ls output/AET-DevOps26/team-the-rolling-restarts/*_published.json | wc -l
```

Expected: `10` (5 trivy + 5 dockle).

Also test the `IMAGE_CHANNEL` override:

```bash
make security-scan IMAGE_CHANNEL=main 2>&1 | grep -E "published|channel"
```

Expected: output shows `channel: main`.

- [ ] **Step 6: Commit**

```bash
git add infra/scripts/security-scan.sh
git commit -m "feat(security): add published GHCR image scanning with IMAGE_CHANNEL override"
```

---

## Task 5: Summary

Print a results table at the end that shows per-tool status and finding counts, and add `output/AET-DevOps26/` to `.gitignore` so generated SARIF files aren't accidentally committed.

**Files:**
- Modify: `infra/scripts/security-scan.sh` (replace `print_summary` stub)
- Modify: `.gitignore` (add `output/AET-DevOps26/`)

- [ ] **Step 1: Check current final output of `make security-scan`**

```bash
make security-scan 2>&1 | tail -5
```

Expected: ends with the `print_summary: not yet implemented` message.

- [ ] **Step 2: Implement `print_summary`**

Replace the stub:

```bash
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
  echo "View with: docker run -it --rm -v \"\$(pwd)/output/AET-DevOps26:/data\" ghcr.io/pstoeckle/guestlecture:v0.1.3 /data"
  echo ""
}
```

- [ ] **Step 3: Add `output/AET-DevOps26/` to `.gitignore`**

Add at the end of `.gitignore`:

```
# Generated security scan SARIF output (make security-scan)
output/AET-DevOps26/
```

- [ ] **Step 4: Run full scan and verify summary prints**

```bash
make security-scan 2>&1 | tail -20
```

Expected: summary table with all output files listed, total findings count, and the `docker run` hint at the end.

- [ ] **Step 5: Verify the TUI viewer works against the output**

```bash
ls output/AET-DevOps26/team-the-rolling-restarts/*.json | wc -l
```

Expected: `21` (11 source + 5 trivy local + 5 dockle local + 5 trivy published + 5 dockle published = 31... wait let me recount:
- 1 codeowners
- 1 gitleaks
- 5 hadolint
- 1 kics
- 1 zizmor
- 1 typos
- 2 npm audit
= 12 source files
- 5 trivy local + 5 trivy published = 10
- 5 dockle local + 5 dockle published = 10
= **32 total files**).

Confirm: `32`.

- [ ] **Step 6: Commit**

```bash
git add infra/scripts/security-scan.sh .gitignore
git commit -m "feat(security): add summary table and gitignore generated output"
```

---

## Self-Review

**Spec coverage check:**

| Spec requirement | Covered by |
|---|---|
| All nine tools | Task 2 (7 source tools) + Tasks 3-4 (trivy/dockle) |
| SARIF 2.1.0 output | Each function writes/captures SARIF; `sarif_error()` writes compliant placeholder |
| Filenames match reference exactly (local scans) | Task 2 hadolint/kics naming, Task 3 trivy/dockle naming |
| Published images with `_published` suffix | Task 4 |
| `IMAGE_CHANNEL` override | Task 1 (variable), Task 4 (usage) |
| Graceful degradation (placeholder SARIF on failure) | `sarif_error()` in Task 1, used in every function |
| Terminal summary | Task 5 |
| Single `make security-scan` target | Task 1 |
| Output dir created automatically | Task 1 (`mkdir -p`) |
| kics `.sarif` → `.json` rename | Task 2 `run_kics` |
| Docker socket mount for trivy/dockle | Tasks 3-4 |
| npm audit custom SARIF with vuln count | Task 2 `run_npm_audit` |
| Codeowners custom SARIF | Task 2 `run_codeowners_check` |
| typos via `python:3.12-slim` | Task 2 `run_typos` |

**Placeholder scan:** No TBD/TODO remaining in the plan. The `# TODO: published` comments in Tasks 3-4 are inter-task implementation stubs explicitly handled by Task 4.

**Type consistency:** `sarif_error(file, tool, message)`, `sarif_count(file)` and the `TOOL_STATUS`/`TOOL_RESULTS` associative arrays are defined in Task 1 and used consistently in Tasks 2-5. The `$REGISTRY` variable is defined in Task 1's skeleton and used in Task 4 without redefinition.

**File count in Task 5 Step 5:** corrected to 32 total (12 source + 10 trivy + 10 dockle).
