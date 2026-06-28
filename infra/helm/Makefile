.PHONY: help helm-lint helm-template helm-install helm-upgrade helm-deploy helm-destroy helm-setup helm-secrets

HELM ?= helm
RELEASE ?= newsgenai
CHART ?= .
ENV ?= dev
MONGO_USER ?= root

# Auto-detect secrets-values.yaml if present, unless overridden
ifneq ($(wildcard secrets-values.yaml),)
SECRETS ?= -f secrets-values.yaml
else
SECRETS ?=
endif

ifeq ($(ENV),prod)
VALUES_FILES ?= -f values.yaml -f values-prod.yaml $(SECRETS) -f image-values.yaml
else
VALUES_FILES ?= -f values.yaml $(SECRETS) -f image-values.yaml
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
	  '  make helm-secrets             - generate secrets-values.yaml (RSA key + random Mongo password)' \
	  '' \
	  'Set ENV=prod for production TLS (default: staging)' \
	  'secrets-values.yaml is auto-included if present (override: SECRETS=)'

## Lint the chart before rendering or installing.
helm-lint:
	$(HELM) lint $(CHART) $(VALUES_FILES)
helm-setup:
	cp image-values.example.yaml image-values.yaml

## Render the chart locally without touching the cluster.
helm-template:
	$(HELM) template $(RELEASE) $(CHART) $(VALUES_FILES)

## Install the chart into a namespace.
helm-install:
	$(HELM) install $(RELEASE) $(CHART) $(VALUES_FILES)

## Upgrade an existing release.
helm-upgrade:
	$(HELM) upgrade $(RELEASE) $(CHART) $(VALUES_FILES)

## Install or upgrade in one command.
helm-deploy:
	$(HELM) upgrade --install $(RELEASE) $(CHART) $(VALUES_FILES)

## Remove the release from the cluster.
helm-destroy:
	$(HELM) uninstall $(RELEASE)

## Generate a working secrets-values.yaml: a fresh shared RSA key pair for JWT signing and a
## random Mongo root password. Refuses to overwrite an existing file (delete it to regenerate).
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
	} > secrets-values.yaml; \
	rm -f "$$tmp"; \
	echo "Wrote secrets-values.yaml (gitignored). Mongo user: $(MONGO_USER)."