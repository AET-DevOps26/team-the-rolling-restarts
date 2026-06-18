.PHONY: help helm-lint helm-template helm-install helm-upgrade helm-deploy helm-destroy helm-setup

HELM ?= helm
RELEASE ?= newsgenai
CHART ?= .
ENV ?= dev
SECRETS ?=

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
	  '' \
	  'Set ENV=prod for production TLS (default: staging)' \
	  'Set SECRETS=-f secrets-values.yaml to inject credentials'

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