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

# Make a JSON API call and echo the response body. Args: METHOD URL TOKEN [DATA]
api_json() {
  local method="$1" url="$2" tok="$3" data="${4:-}"
  local args=(-s --connect-timeout 5 --max-time 15 -X "$method")
  [ -n "$tok" ] && args+=(-H "Authorization: Bearer $tok")
  [ -n "$data" ] && args+=(-H 'Content-Type: application/json' -d "$data")
  curl $curl_extra "${args[@]}" "$url" 2>/dev/null || true
}

# Register (idempotent) then log in a user; echo their JWT. Arg: USERNAME
register_login() {
  local uname="$1"
  curl $curl_extra -so /dev/null --connect-timeout 5 --max-time 10 -X POST "$base_url/api/users/auth/register" \
    -H 'Content-Type: application/json' \
    -d "{\"username\":\"$uname\",\"email\":\"$uname@test.com\",\"password\":\"password123\",\"name\":\"$uname\"}" 2>/dev/null || true
  api_json POST "$base_url/api/users/auth/login" "" \
    "{\"username\":\"$uname\",\"password\":\"password123\"}" | jq -r '.token // empty' 2>/dev/null || true
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

token=$(echo "$login_body" | jq -r '.token // empty' 2>/dev/null || true)
echo ""

echo "=== RSS Source Lifecycle ==="
if [ -n "$token" ]; then
  source_code=$(http -X POST "$base_url/api/content/sources" \
    -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $token" \
    -d '{"name":"Süddeutsche Zeitung","rssUrl":"https://rss.sueddeutsche.de/alles"}')
  if [ "$source_code" = "201" ] || [ "$source_code" = "200" ]; then
    check "Create RSS source returns 201 (or 200 if exists)" "ok" "ok"
  else
    check "Create RSS source returns 201 (or 200 if exists)" "ok" "$source_code"
  fi

  sources_body=$(curl $curl_extra -sf --connect-timeout 5 --max-time 10 "$base_url/api/content/sources" 2>/dev/null || true)
  has_sz=$(echo "$sources_body" | jq -r '[.[] | select(.name == "Süddeutsche Zeitung")] | length' 2>/dev/null || echo 0)
  check "Source 'Süddeutsche Zeitung' exists in list" "1" "$has_sz"
else
  check "Create RSS source (skipped - no token)" "ok" "skipped"
  check "Source exists in list (skipped - no token)" "1" "skipped"
fi
echo ""

echo "=== Protected Endpoints ==="
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

echo "=== Shared Source Subscriber Lifecycle ==="
# Two users subscribe to the SAME source, then both unsubscribe. The source is shared
# (one fetch for all subscribers) and must only be deleted once the last subscriber leaves,
# while disappearing from each user's enabled sources as they unsubscribe.
buzz_url="https://www.buzzfeed.com/tech.xml"
tokenA=$(register_login "smokeA$$")
tokenB=$(register_login "smokeB$$")

if [ -n "$tokenA" ] && [ -n "$tokenB" ]; then
  # User A creates the shared source (count starts at 0 — creation does not subscribe).
  create_body=$(api_json POST "$base_url/api/content/sources" "$tokenA" \
    "{\"name\":\"BuzzFeed Tech\",\"rssUrl\":\"$buzz_url\"}")
  source_id=$(echo "$create_body" | jq -r '.id // empty' 2>/dev/null || true)
  # Fall back to the list if it already existed from a prior run.
  if [ -z "$source_id" ]; then
    source_id=$(api_json GET "$base_url/api/content/sources" "" \
      | jq -r --arg u "$buzz_url" 'map(select(.rssUrl == $u))[0].id // empty' 2>/dev/null || true)
  fi
  check "Shared source created (has id)" "true" "$([ -n "$source_id" ] && echo true || echo false)"

  # Both users subscribe to the same source.
  api_json POST "$base_url/api/users/users/me/subscriptions/$source_id" "$tokenA" >/dev/null
  api_json POST "$base_url/api/users/users/me/subscriptions/$source_id" "$tokenB" >/dev/null

  count=$(api_json GET "$base_url/api/content/sources/$source_id" "" | jq -r '.subscriberCount // empty' 2>/dev/null || true)
  check "Subscriber count is 2 after both subscribe" "2" "$count"
  a_has=$(api_json GET "$base_url/api/users/users/me/settings" "$tokenA" \
    | jq -r --arg s "$source_id" '(.enabledSourceIds // []) | index($s) != null' 2>/dev/null || true)
  check "Source appears in user A's enabled sources" "true" "$a_has"

  # User A unsubscribes — the source must survive because user B is still subscribed.
  api_json DELETE "$base_url/api/users/users/me/subscriptions/$source_id" "$tokenA" >/dev/null
  count=$(api_json GET "$base_url/api/content/sources/$source_id" "" | jq -r '.subscriberCount // empty' 2>/dev/null || true)
  check "Subscriber count is 1 after user A unsubscribes" "1" "$count"
  check "Source still exists while user B subscribed" "200" "$(http "$base_url/api/content/sources/$source_id")"
  a_gone=$(api_json GET "$base_url/api/users/users/me/settings" "$tokenA" \
    | jq -r --arg s "$source_id" '(.enabledSourceIds // []) | index($s) == null' 2>/dev/null || true)
  check "Source removed from user A's enabled sources" "true" "$a_gone"

  # User B unsubscribes — last subscriber gone, so the source must be auto-deleted.
  api_json DELETE "$base_url/api/users/users/me/subscriptions/$source_id" "$tokenB" >/dev/null
  check "Source auto-deleted after last unsubscribe (404)" "404" "$(http "$base_url/api/content/sources/$source_id")"
  in_list=$(api_json GET "$base_url/api/content/sources" "" \
    | jq -r --arg s "$source_id" 'map(select(.id == $s)) | length' 2>/dev/null || true)
  check "Source no longer in list" "0" "$in_list"
  b_gone=$(api_json GET "$base_url/api/users/users/me/settings" "$tokenB" \
    | jq -r --arg s "$source_id" '(.enabledSourceIds // []) | index($s) == null' 2>/dev/null || true)
  check "Source removed from user B's enabled sources" "true" "$b_gone"
else
  check "Shared source subscriber lifecycle (skipped - no tokens)" "ok" "skipped"
fi
echo ""

total=$((pass + fail))
if [ "$fail" -eq 0 ]; then
  printf '\033[32mAll %d checks passed.\033[0m\n' "$total"
else
  printf '\033[31m%d/%d checks failed.\033[0m\n' "$fail" "$total"
  exit 1
fi
