.PHONY: help helm-lint helm-template helm-install helm-upgrade helm-deploy helm-destroy helm-setup helm-secrets ensure-monitoring-namespace

HELM ?= helm
RELEASE ?= newsgenai
CHART ?= .
ENV ?= dev
MONGO_USER ?= root
# Must match monitoring.namespace in values.yaml. grafana-lgtm deploys into its own namespace,
# which Helm's --create-namespace does NOT auto-create (that flag only covers Helm's own
# install-target namespace, not other namespaces referenced by templates via an explicit
# metadata.namespace) — so it must exist before install/upgrade runs, or those fail outright.
MONITORING_NAMESPACE ?= rolling-restarts-monitoring

# Auto-detect secrets-values.yaml if present, unless overridden
ifneq ($(wildcard secrets-values.yaml),)
SECRETS ?= -f secrets-values.yaml
else
SECRETS ?=
endif

ifeq ($(ENV),prod)
VALUES_FILES ?= -f values.yaml -f values-prod.yaml $(SECRETS) -f image-values.yaml
else
# Manual/dev deploys layer values-dev.yaml (a `dev.`-prefixed host) so they don't collide with the
# CD-managed production release on the shared ingress.
VALUES_FILES ?= -f values.yaml -f values-dev.yaml $(SECRETS) -f image-values.yaml
endif

# Override the ingress host: make helm-deploy HELM_HOST=my-host.example.com
ifdef HELM_HOST
HOST_SET = --set host=$(HELM_HOST)
else
HOST_SET =
endif

help:
	@printf '%s\n' \
	  'Available Helm targets:' \
	  '  make helm-lint                - lint the chart' \
	  '  make helm-template            - render the chart locally' \
	  '  make helm-install             - install the release' \
	  '  make helm-upgrade             - upgrade an existing release' \
	  '  make helm-deploy              - upgrade or install in one step' \
	  '  make helm-destroy             - uninstall the release' \
	  '  make helm-secrets             - generate secrets-values.yaml (RSA key, random Mongo password, service secret)' \
	  '' \
	  'Default ENV=dev: staging TLS + a dev.-prefixed host (values-dev.yaml) so a manual deploy' \
	  'does not collide with the CD release on the shared ingress. Set ENV=prod for the prod host + TLS.' \
	  'Override the ingress host: HELM_HOST=my-host.example.com' \
	  'secrets-values.yaml is auto-included if present (override: SECRETS=)' \
	  'helm-install/helm-upgrade/helm-deploy idempotently create the monitoring namespace first' \
	  '(MONITORING_NAMESPACE, default rolling-restarts-monitoring) if it does not already exist.'

## Lint the chart before rendering or installing.
helm-lint:
	$(HELM) lint $(CHART) $(VALUES_FILES) $(HOST_SET)
helm-setup:
	cp image-values.example.yaml image-values.yaml

## Render the chart locally without touching the cluster.
helm-template:
	$(HELM) template $(RELEASE) $(CHART) $(VALUES_FILES) $(HOST_SET)

## Idempotent: only creates the namespace if it doesn't already exist. Never touches anything
## already inside it (existing PVC data, running pods, etc. are left exactly as they are) — this
## is a plain `kubectl apply` of just the Namespace object, not a recreate.
ensure-monitoring-namespace:
	kubectl create namespace $(MONITORING_NAMESPACE) --dry-run=client -o yaml | kubectl apply -f -

## Install the chart into a namespace.
helm-install: ensure-monitoring-namespace
	$(HELM) install $(RELEASE) $(CHART) $(VALUES_FILES) $(HOST_SET)

## Upgrade an existing release.
helm-upgrade: ensure-monitoring-namespace
	$(HELM) upgrade $(RELEASE) $(CHART) $(VALUES_FILES) $(HOST_SET)

## Install or upgrade in one command.
helm-deploy: ensure-monitoring-namespace
	$(HELM) upgrade --install $(RELEASE) $(CHART) $(VALUES_FILES) $(HOST_SET)

## Remove the release from the cluster.
helm-destroy:
	$(HELM) uninstall $(RELEASE)

## Generate a working secrets-values.yaml with all five required secrets: Mongo credentials,
## RSA key pair for JWT signing, and a service client secret. Refuses to overwrite.
## Override the Mongo user with MONGO_USER=...
helm-secrets:
	@if [ -f secrets-values.yaml ]; then \
	  echo "secrets-values.yaml already exists — remove it first to regenerate."; exit 1; \
	fi
	@umask 077; tmp=$$(mktemp); \
	openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$$tmp" 2>/dev/null; \
	{ \
	  echo "mongodb:"; \
	  echo "  rootUsername: $(MONGO_USER)"; \
	  echo "  rootPassword: $$(openssl rand -hex 16)"; \
	  echo "userService:"; \
	  echo "  jwtKeys:"; \
	  echo "    publicKey: |"; \
	  openssl pkey -in "$$tmp" -pubout | sed 's/^/      /'; \
	  echo "    privateKey: |"; \
	  sed 's/^/      /' "$$tmp"; \
	  echo "  serviceClientSecret: $$(openssl rand -hex 32)"; \
	} > secrets-values.yaml; \
	rm -f "$$tmp"; \
	echo "Wrote secrets-values.yaml (gitignored). Mongo user: $(MONGO_USER)."