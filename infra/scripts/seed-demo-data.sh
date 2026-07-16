#!/usr/bin/env bash
# Idempotently registers a fixed demo account, subscribes it to a couple of known-good RSS
# feeds, and prints its credentials — so there's always a ready-to-use login with actual
# content for a live demonstration. Safe to re-run on every deploy:
#   - register: 409 (already registered) counts as success, same as
#     infra/scripts/smoke-test.sh's register_login.
#   - source create: 200 (already exists, returns the existing one) or 201.
#   - subscribe: always 200, and idempotent server-side (does not double-count).
#
# Keep the demo credentials and source list in sync with the equivalent inline calls in
# infra/ansible/roles/app/tasks/main.yml and .github/workflows/deploy-azure.yml's
# remote-deploy.sh (those can't source this file — one runs via Ansible's uri module on
# the VM, the other is a raw bash heredoc with no filesystem access to this repo) and
# .github/workflows/deploy_kubernetes.yml's ephemeral-pod step.
set -u

# Username/password are overridable (DEMO_USERNAME/DEMO_PASSWORD env vars, e.g. from
# infra/.env) so they can be repo-secret-controlled in CI; email/name stay fixed since
# they're just profile fields, not credentials.
DEMO_USERNAME="${DEMO_USERNAME:-demo}"
DEMO_EMAIL="${DEMO_USERNAME}@example.com"
DEMO_PASSWORD="${DEMO_PASSWORD:-Demo12345!}"
DEMO_NAME="Demo User"

# name|rssUrl pairs the demo user ends up subscribed to. All verified reachable
# (HTTP 200, XML content-type) before adding.
DEMO_SOURCES=(
  "Süddeutsche Zeitung|https://rss.sueddeutsche.de/alles"
  "Die Linke Presse|https://www.die-linke.de/start/presse/feed.rss"
  "Fox News Politics|https://moxie.foxnews.com/google-publisher/politics.xml"
  "NYT World|https://rss.nytimes.com/services/xml/rss/nyt/World.xml"
  "NYT Europe|https://rss.nytimes.com/services/xml/rss/nyt/Europe.xml"
  "NYT Technology|https://rss.nytimes.com/services/xml/rss/nyt/Technology.xml"
  "NYT Science|https://rss.nytimes.com/services/xml/rss/nyt/Science.xml"
)

base_url="${1:?Usage: $0 [--insecure] <base-url>}"
curl_extra=""

if [ "$base_url" = "--insecure" ]; then
  curl_extra="-k"
  base_url="${2:?Usage: $0 [--insecure] <base-url>}"
fi

echo "Seeding demo user at $base_url ..."

# The app may not be reachable the instant this runs (e.g. right after `docker compose
# up -d` returns, before health checks pass), so retry with the same 12x10s budget used
# elsewhere in this repo for post-deploy checks (see deploy-azure.yml's reverse-proxy wait).
code=""
for i in $(seq 1 12); do
  code=$(curl $curl_extra -s -o /dev/null -w '%{http_code}' --connect-timeout 5 --max-time 10 \
    -X POST "$base_url/api/users/auth/register" \
    -H 'Content-Type: application/json' \
    -d "{\"username\":\"$DEMO_USERNAME\",\"email\":\"$DEMO_EMAIL\",\"password\":\"$DEMO_PASSWORD\",\"name\":\"$DEMO_NAME\"}" \
    2>/dev/null)
  if [ "$code" = "201" ] || [ "$code" = "409" ]; then
    break
  fi
  echo "  waiting for the app to accept registrations ($i/12, last response: ${code:-none})..."
  sleep 10
done

if [ "$code" != "201" ] && [ "$code" != "409" ]; then
  echo "Failed to seed demo user (last response: ${code:-none})." >&2
  exit 1
fi
if [ "$code" = "201" ]; then
  echo "Demo user created."
else
  echo "Demo user already exists (fine, idempotent)."
fi

token=$(curl $curl_extra -s --connect-timeout 5 --max-time 10 -X POST "$base_url/api/users/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"$DEMO_USERNAME\",\"password\":\"$DEMO_PASSWORD\"}" 2>/dev/null \
  | jq -r '.token // empty' 2>/dev/null)

if [ -z "$token" ]; then
  echo "Warning: could not log in as demo user to seed sources; the account exists but has no subscriptions." >&2
  echo ""
  echo "Demo login: username=$DEMO_USERNAME password=$DEMO_PASSWORD"
  exit 0
fi

for entry in "${DEMO_SOURCES[@]}"; do
  name="${entry%%|*}"
  rss_url="${entry#*|}"

  source_id=$(curl $curl_extra -s --connect-timeout 5 --max-time 10 -X POST "$base_url/api/content/sources" \
    -H 'Content-Type: application/json' -H "Authorization: Bearer $token" \
    -d "{\"name\":\"$name\",\"rssUrl\":\"$rss_url\"}" 2>/dev/null | jq -r '.id // empty' 2>/dev/null)

  if [ -n "$source_id" ]; then
    curl $curl_extra -s -o /dev/null --connect-timeout 5 --max-time 10 -X POST \
      "$base_url/api/users/users/me/subscriptions/$source_id" \
      -H "Authorization: Bearer $token" 2>/dev/null
    echo "  subscribed to: $name"
  else
    echo "  warning: could not create or find source: $name" >&2
  fi
done

echo ""
echo "Demo login: username=$DEMO_USERNAME password=$DEMO_PASSWORD"
