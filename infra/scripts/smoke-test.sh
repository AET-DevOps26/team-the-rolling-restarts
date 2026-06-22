#!/usr/bin/env bash
set -u

base_url="${1:?Usage: $0 [--insecure] <base-url>}"
curl_extra=""

if [ "$base_url" = "--insecure" ]; then
  curl_extra="-k"
  base_url="${2:?Usage: $0 [--insecure] <base-url>}"
fi

pass=0; fail=0

check() {
  local desc="$1" expected="$2" actual="$3"
  if [ "$actual" = "$expected" ]; then
    printf '  \033[32m✓\033[0m %s (got %s)\n' "$desc" "$actual"
    pass=$((pass + 1))
  else
    printf '  \033[31m✗\033[0m %s (expected %s, got %s)\n' "$desc" "$expected" "$actual"
    fail=$((fail + 1))
  fi
}

http() {
  curl $curl_extra -so /dev/null -w '%{http_code}' --connect-timeout 5 --max-time 10 "$@" 2>/dev/null
}

echo "=== Reverse Proxy + Health ==="
check "Web Client via /" "200" "$(http "$base_url/")"
check "API Gateway health" "200" "$(http "$base_url/actuator/health")"
echo ""

echo "=== API Routing ==="
check "GET /api/content/sources" "200" "$(http "$base_url/api/content/sources")"
check "GET /api/content/articles" "200" "$(http "$base_url/api/content/articles")"
check "GET /api/content/topics" "200" "$(http "$base_url/api/content/topics")"
echo ""

echo "=== Registration ==="
check "Invalid body returns 400" "400" "$(http -X POST "$base_url/api/users/auth/register" \
  -H 'Content-Type: application/json' \
  -d '{"username":"","email":"bad","password":"short"}')"

reg_code=$(http -X POST "$base_url/api/users/auth/register" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"smoke$$\",\"email\":\"smoke$$@test.com\",\"password\":\"password123\",\"name\":\"Smoke Test\"}")
if [ "$reg_code" = "201" ] || [ "$reg_code" = "409" ]; then
  check "Valid body returns 201 (or 409 re-run)" "ok" "ok"
else
  check "Valid body returns 201 (or 409 re-run)" "ok" "$reg_code"
fi
echo ""

echo "=== Login ==="
login_code=$(http -X POST "$base_url/api/users/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"smoke$$\",\"password\":\"password123\"}")
check "Login with registered user returns 200" "200" "$login_code"

login_body=$(curl $curl_extra -sf --connect-timeout 5 --max-time 10 -X POST "$base_url/api/users/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"smoke$$\",\"password\":\"password123\"}" 2>/dev/null || true)
has_token=$(echo "$login_body" | jq -r 'has("token")' 2>/dev/null || echo false)
check "Login response contains token" "true" "$has_token"

check "Login with wrong password returns 401" "401" "$(http -X POST "$base_url/api/users/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"smoke$$\",\"password\":\"wrongpassword\"}")"
echo ""

echo "=== Protected Endpoints ==="
token=$(echo "$login_body" | jq -r '.token // empty' 2>/dev/null || true)
check "GET /users/me without token returns 401" "401" "$(http "$base_url/api/users/users/me")"
if [ -n "$token" ]; then
  check "GET /users/me with token returns 200" "200" "$(http "$base_url/api/users/users/me" \
    -H "Authorization: Bearer $token")"
  me_body=$(curl $curl_extra -sf --connect-timeout 5 --max-time 10 "$base_url/api/users/users/me" \
    -H "Authorization: Bearer $token" 2>/dev/null || true)
  me_username=$(echo "$me_body" | jq -r '.username // empty' 2>/dev/null || true)
  check "GET /users/me returns correct username" "smoke$$" "$me_username"
else
  check "GET /users/me with token returns 200" "200" "skipped (no token)"
  check "GET /users/me returns correct username" "smoke$$" "skipped (no token)"
fi
echo ""

total=$((pass + fail))
if [ "$fail" -eq 0 ]; then
  printf '\033[32mAll %d checks passed.\033[0m\n' "$total"
else
  printf '\033[31m%d/%d checks failed.\033[0m\n' "$fail" "$total"
  exit 1
fi
