# Gotchas — Read This First

Non-obvious things that file names alone don't tell you. Each has cost real
investigation time once already; re-verify before repeating that work.

## Frontend is fully backend-integrated (this gotcha is stale — kept as a historical marker)

**No longer true, as of the `feat/web-client-backend-integration` work merged into this branch's
history.** `web-client/src/lib/mock/` doesn't exist anymore; every page goes through
`web-client/src/lib/api/client.ts` (`apiFetch`, `"server-only"`) and the generated client
(`web-client/src/generated/api.ts`). If you're reading an old note (memory, a stale PR comment)
claiming the frontend is mock-driven, it predates this integration — verify against the code, not
the note. See [02-web-client.md](02-web-client.md).

```sh
grep -rln "generated/api\|apiFetch\b" web-client/src --include="*.tsx" --include="*.ts" | wc -l   # should be several, not 0
```

## GenAI service has real LLM endpoints (this gotcha is stale — kept as a historical marker)

**No longer true.** `services/gen-ai/app/routers/` has `summarize.py`, `explain.py`,
`sentiment.py`, and `qa.py`, each invoking a real LLM via `app/llm/provider.py` (`ollama` or
`logos`, see below for the gotcha in *that*). Full OTel instrumentation (traces + custom
`gen_ai_llm_*` metrics) too. See [03-gen-ai-service.md](03-gen-ai-service.md).

## gen-ai's LLM provider is only ever `ollama` or `logos` — anything else crashes, not falls back

`app/llm/provider.py`'s `get_chat_model()` raises `ValueError(f"Unknown LLM provider: ...")` for
any other `LLM_PROVIDER` value — there is no generic "openai" provider implemented, despite that
being a natural-sounding value to configure. Bit twice: a GH Actions variable was set to
`LLM_PROVIDER=openai` (matching `Makefile`'s own `azure-cicd-help` suggested command, itself
wrong) and broke every LLM endpoint on the Azure deployment for weeks before being caught live via
gen-ai's own logs (`ValueError: Unknown LLM provider: 'openai'`). Also: `logos`
(`https://logos.aet.cit.tum.de/v1`) is **TUM-network-only** — it works from the Kubernetes cluster
(same network) but is unreachable from Azure's public cloud VM, so "just set it to logos
everywhere" isn't a fix for Azure either. See `docs/internal/06-observability.md`'s incident log
and `docs/internal/05-ci-cd-workflows.md` for the current (still-unresolved on Azure) state.

```sh
grep -n "if settings.llm_provider" services/gen-ai/app/llm/provider.py   # only ollama/logos branch
```

## `services/spring-api/` is dead local clutter, not a 4th service

Gitignored (`**/generated` pattern), untracked, contains
`openapi-generator`'s generic default templates (`DummyApi`, `TestApi`).
Left over from an experiment with server-stub generation that the real
pipeline (`api/scripts/gen-all.sh`) explicitly avoids ("no server stubs are
generated from the spec"). Ignore it; don't count it as a microservice or
investigate it as real code.

## `web-client`'s pact/contract test setup is dead config

`web-client/jest.pact.config.js` exists but `jest`/`ts-jest` aren't in
`package.json` and no `tests/pact/` directory exists. Similarly, CI's
`contract-test` job in `ci.yml` only checks for `./scripts/run-pact.sh`,
which doesn't exist — the job always no-ops and passes. **There is currently
no real consumer-driven contract testing**, despite the scaffolding
suggesting there is. See [05-ci-cd-workflows.md](05-ci-cd-workflows.md).

## The ≥3-microservices count includes gen-ai, per course clarification — don't assume "Spring-only" from the literal requirement text

`docs/requirements/03-system-architecture.md`'s literal wording ("The server side must be
implemented in Spring Boot and must consist of at least three microservices") reads as
Spring-specific on its own. A clarification obtained via Artemis confirmed gen-ai counts toward
this requirement too, so `user-service` + `content-service` + `gen-ai` already satisfy it — no
third Spring service is needed. `api-gateway` still doesn't count on its own (routing/JWT
infrastructure, not a business-domain service). See `docs/requirements/STATUS.md` §03 for the full
reasoning, and [01-spring-services.md](01-spring-services.md) for the technical breakdown.

## Observability uses OTel + `grafana-lgtm`, not standalone Prometheus

Functionally similar (Mimir inside LGTM is Prometheus-compatible) but the
course brief's letter says "Prometheus must be used." See
[06-observability.md](06-observability.md).

## Team is 2 people, not 3

Third member left the project. See `.github/CODEOWNERS` for current
ownership: `brscn2` (web-client + gen-ai), `YRC99` (services/spring + gen-ai).

## Root README is much thinner than the actual documentation

Substantial docs exist under `docs/source/` (OpenAPI workflow, deployment
testing, secrets reference, Azure/K8s CD pipelines, per-infra READMEs), but
none of it is linked from or summarized in the root `README.md` — the first
thing anyone opens. Quick-start, CI/CD, and monitoring instructions currently
only live in `docs/source/index.md` and the `Makefile`.

## Deployed instances: both verified live, but neither is fully working

The Kubernetes deployment (`rolling-restarts.stud.k8s.aet.cit.tum.de`, the course's Rancher-managed
cluster) and the Azure VM deployment (via `deploy-azure.yml`) have both been exercised live and
end-to-end this branch — this is no longer an unverified-from-a-sandbox assumption. Current
status: **Kubernetes works fully**, including gen-ai's LLM calls (after fixing a missing
`LLM_API_KEY` wire-through) and the full monitoring stack — only alerting notification delivery is
still deliberately unwired (deferred pending an Artemis clarification, see
`docs/source/monitoring.md`). **The Azure VM works for everything except gen-ai's LLM-backed
endpoints** — see the `logos`/`ollama` gotcha above. Don't assume either target's *current* state
without re-checking; both have moved multiple times in one session.

## `NEXT_PUBLIC_`-prefixed env vars get inlined into the image at build time — even in server-only code

Next.js replaces any `process.env.NEXT_PUBLIC_*` reference with a literal string **at build time**,
in every bundle it touches — including code marked `"server-only"` that never runs in the browser.
`web-client`'s API base URL used to be `NEXT_PUBLIC_API_BASE_URL` for exactly this reason (habit,
not necessity), which meant no per-deployment-target runtime override could ever take effect: CI
baked in a stale Kubernetes hostname, and setting a different value in `docker-compose.yaml`'s
`environment:` block at deploy time was silently ignored. Fixed by renaming to plain `API_BASE_URL`
(no prefix) so it's read dynamically at container runtime instead. **Lesson: don't reach for
`NEXT_PUBLIC_` out of habit — only use it for values that must genuinely reach browser-executed
code.** See `docs/internal/06-observability.md`'s incident log.

## A cookie's `secure` flag should never be keyed off `NODE_ENV`

`web-client/src/lib/actions/auth.ts` used to set the auth cookie's `secure` flag from
`process.env.NODE_ENV === "production"`. That's the wrong signal — it conflates "production build"
with "served over HTTPS," which aren't the same thing on the Azure VM target (production build,
but plain HTTP, no TLS at all). A browser will accept a `Secure`-flagged cookie over HTTP but never
send it back on a later request, so every protected route silently bounced back to `/login`
regardless of a valid session. Now keyed off an explicit `COOKIE_SECURE` env var set per
deployment target instead (`true` for Kubernetes, which has real TLS; unset/`false` for the Azure
VM). Tracked as issue #90 for the proper fix (add real TLS to the Azure VM target).

## Two independent Docker-install mechanisms on the Azure VM path can conflict

`make azure-vm-docker` (part of `azure-cicd-setup`, the CI/CD one-time setup) installs Docker via
`get.docker.com`'s official script (`containerd.io`). The Ansible `docker` role (the *manual*
`make deploy-azure` path) used to apt-install Ubuntu archive's `docker.io`/`docker-compose-v2`
instead, which pulls in Ubuntu's own `containerd` package — the two `containerd` packages conflict
at the package-manager level, so whichever one runs second on a given VM fails outright
(`containerd.io : Conflicts: containerd`). Both now install Docker the same way. If you ever add a
third Docker-provisioning path, make it agree with `get.docker.com` too.

## `infra/grafana/` sync to the Azure VM lives in *two* separate, unrelated mechanisms

The Ansible `app` role and the GitHub Actions `deploy-azure.yml` workflow are two independent ways
to get the app running on the same Azure VM, and **each had to be fixed separately** to actually
copy `infra/grafana/`'s dashboards/provisioning/prometheus-scrape-config onto the VM —
`docker-compose.yaml`'s `grafana-lgtm` bind mounts had nothing to mount otherwise, and Docker's
fallback for a missing bind-mount source (silently auto-creating it as an empty directory) broke
the container. If you add a *third* way to deploy to that VM, remember this file tree needs
syncing there too — nothing enforces it generically.

## gen-ai's custom OTel instruments must be created after `setup_observability()`, not before

`app/main.py` used to import the routers (`from app.routers import explain, qa, sentiment,
summarize`) at module top level, before calling `setup_observability(app)`. Each router
transitively imports `app/llm/invoke.py`, which creates its tracer/meter and all its instruments
(`gen_ai.llm.requests`, `.latency`, `.errors`, `.{prompt,completion}_tokens`) **at module import
time** — so they were binding to OpenTelemetry's default no-op providers instead of the real ones
`setup_observability()` configures moments later. Confirmed live: `gen_ai_llm_requests_total`
never reached Prometheus no matter how long you waited or how many LLM calls were made, leaving
the "GenAI Overview" Grafana dashboard permanently empty — while FastAPI's own auto-instrumented
spans worked fine the whole time (that instrumentation lives *inside* `setup_observability()`
itself, so it was never affected). Fixed by moving the router import to after
`setup_observability(app)` runs. **Still unresolved**: the custom `llm.invoke` trace span from the
same module doesn't appear even after this fix, unlike the metrics — confirmed via direct trace
inspection (Tempo). Doesn't block the dashboard (reads metrics, not traces), but worth
understanding if anyone relies on trace-level LLM visibility.

```sh
grep -n "from app.routers import" services/gen-ai/app/main.py   # should be AFTER setup_observability(app)
```

## Article search is fully implemented but broken live — missing MongoDB text index, not a stub

`web-client`'s search bar (`components/layout/topbar-search.tsx`) and `content-service`'s
`ArticleController`/`ArticleService` are a real, complete implementation: `@TextIndexed` on
`Article.headline`/`snippet`, a proper `TextCriteria`/`TextQuery` search combined with
source/topic filtering, pagination, everything wired end-to-end from the UI down. It fails live
with `MongoCommandException: text index required for $text query (IndexNotFound)` — Spring Data
MongoDB's `auto-index-creation` defaults to `false` and nothing in `content-service` overrides it
or creates the index another way (no migration script, no manual `createIndex` call), so
`@TextIndexed` alone never actually creates the index on a real deployment. Confirmed live via
`api-gateway`'s `/api/content/articles?q=...` returning 500. **Fix is small** (enable
`spring.data.mongodb.auto-index-creation=true`, or create the text index explicitly at startup)
but not yet done — don't assume this is an intentional gap or a stub feature.

```sh
grep -rn "auto-index-creation" services/spring/content-service/src/main/resources/   # currently empty
```

## `web-client` couldn't reach `grafana-lgtm` locally — two separate Docker Compose networks, not an OTel bug

`infra/docker-compose.yaml` declares two bridge networks, `frontend` and `backend`; a service can
only resolve another by container name if they share at least one. `web-client` was declared only
on `frontend`, while `grafana-lgtm` (and every other backend service) is only on `backend` —
`api-gateway` is the sole exception, deliberately dual-homed on both since it's the one frontend
service that also talks to backend services directly. This meant `web-client`'s OTel exporter could
never reach `grafana-lgtm:4318` locally: `getaddrinfo ENOTFOUND grafana-lgtm` from inside the
container, confirmed via a raw Node.js `http.request` test. Everything else about the setup was
already correct (env vars, `instrumentation.ts`'s `register()` executing and reaching
`registerOTel()`, `@vercel/otel`'s default OTLP protocol) — it looked like an OTel/Next.js
configuration problem for a long time before the actual network topology was checked. First fixed
by adding `web-client` to the `backend` network too — but that gave the one container most exposed
to untrusted input a direct path to `mongodb` and every other internal service, unnecessarily
widening its blast radius. Moved the fix to the other side instead: `grafana-lgtm` now sits on
*both* `frontend` and `backend` (the actual telemetry sink, not the container receiving untrusted
input), which restores segmentation while still letting every service reach it. **Kubernetes was
never affected** — there's no equivalent network partitioning between pods/Services there, which is
why "it worked on k9s before" was a real, confusing signal pointing away from docker-compose-specific
causes.

```sh
grep -A2 "networks:" infra/docker-compose.yaml   # grafana-lgtm should list both frontend and backend
```

## The `grafana/otel-lgtm` base image defaults to anonymous Admin access — no login required

`run-grafana.sh` inside the image sets `GF_AUTH_ANONYMOUS_ENABLED="${GF_AUTH_ANONYMOUS_ENABLED:-true}"`
and, when anonymous auth is enabled, `GF_AUTH_ANONYMOUS_ORG_ROLE="${GF_AUTH_ANONYMOUS_ORG_ROLE:-Admin}"`.
Without an explicit override, **anyone who can reach the Grafana port gets full admin access with
no login at all** — confirmed live: `curl .../monitoring/api/search` returned real dashboard data
with zero auth before this was set. Setting `GF_SECURITY_ADMIN_PASSWORD` alone does nothing to
close this — the password is irrelevant if anonymous requests are already treated as an
authenticated admin. Fixed by explicitly setting `GF_AUTH_ANONYMOUS_ENABLED: "false"` everywhere
grafana-lgtm is deployed (docker-compose, Helm, raw k8s manifests).

```sh
grep -rn "GF_AUTH_ANONYMOUS_ENABLED" infra/   # should appear in all three deployment paths, set to "false"
```

## Grafana's admin password only applies on first init — same class of gotcha as MongoDB's root password

Just like MongoDB only applies `MONGO_INITDB_ROOT_PASSWORD` on first init (see the rotation note in
`secrets-values.example.yaml`), `GF_SECURITY_ADMIN_PASSWORD` only seeds the `admin` user's password
the first time Grafana boots against an empty `/data` volume. If the `grafana-lgtm-data`
volume/PVC already exists from before, changing this env var and restarting the container does
**not** change the existing admin password — confirmed live: the new password was rejected and the
old default (`admin`) still worked until the password was rotated explicitly. To rotate it on an
existing install without wiping dashboards/history:

```sh
docker exec <grafana-lgtm-container> sh -c \
  'cd /otel-lgtm/grafana && GF_PATHS_HOME=/data/grafana GF_PATHS_DATA=/data/grafana/data GF_PATHS_PLUGINS=/data/grafana/plugins ./bin/grafana cli admin reset-admin-password <new-password>'
```

(swap `docker exec` for `kubectl exec -n monitoring-rolling-restarts deploy/grafana-lgtm --` on
Kubernetes).

## A manually-wiped Kubernetes namespace does not fully self-heal

`ResourceQuota` reappears automatically on a fresh namespace (a cluster-level admission policy,
confirmed live), and `helm upgrade --install --create-namespace` recreates the bare namespace
object — but **RBAC access does not come back**. This course's cluster is Rancher-managed; access
is tied to a Keycloak-group/Rancher-project association that breaks the moment a namespace is
deleted directly via `kubectl` (bypassing Rancher's own lifecycle), and recreating the namespace at
the raw Kubernetes API level doesn't restore that association. Confirmed live: the very next
`helm upgrade` after a manual wipe failed with `secrets is forbidden ... in the namespace
"deployment"` — Helm couldn't even query its own release state. Recovery needs going through
Rancher (`rancher.ase.cit.tum.de`) directly; nothing in this repo's git/CI/Helm can do it. Full
details in `docs/internal/06-observability.md` and `docs/internal/04-infra-and-deploy.md`.
