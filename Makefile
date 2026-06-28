.PHONY: help clean generate spring-build spring-openapi-docs install-hooks preflight \
       compose-up compose-down compose-ps compose-logs compose-test smoke-test smoke-test-vm smoke-test-k8s push-images \
       terraform-init terraform-plan terraform-apply terraform-destroy terraform-validate \
       ansible-inventory ansible-deploy deploy-azure azure-stop azure-start azure-nuke \
       azure-cicd-setup azure-provider-register azure-vm-docker azure-gh-vars azure-cicd-help \
       helm-lint helm-template helm-install helm-upgrade helm-deploy helm-destroy helm-setup helm-secrets \
       docs-serve

HELM_DIR    ?= infra/helm
COMPOSE_ENV  ?= infra/.env
COMPOSE_FILES = -f infra/docker-compose.yaml -f infra/docker-compose.dev.yaml
TF_DIR       = infra/terraform/azure-vm

# Read defaults from infra/.env (single source of truth).
# Command-line overrides (e.g. make push-images IMAGE_TAG=foo) take priority.
-include $(COMPOSE_ENV)

# Fall back to commit SHA if IMAGE_TAG is not set in .env
IMAGE_TAG ?= $(shell git rev-parse --short HEAD 2>/dev/null || echo latest)

export COMPOSE_UID ?= $(shell id -u)
export COMPOSE_GID ?= $(shell id -g)

help:
	@printf '%s\n' \
	  'Pre-flight & build:' \
	  '  make generate          - regenerate OpenAPI spec + consumer clients (Python, TypeScript)' \
	  '  make spring-build      - compile and test Spring services' \
	  '  make spring-openapi-docs - export each services OpenAPI spec to build/openapi/' \
	  '  make install-hooks     - install the pre-push hook that regenerates the OpenAPI contract' \
	  '  make clean             - remove build artifacts (handles root-owned Docker files)' \
	  '  make preflight         - full pre-flight: generate, build, lint helm, validate terraform' \
	  '' \
	  'Docker Compose (local):' \
	  '  make compose-up        - start the local dev stack (detached)' \
	  '  make compose-down      - tear down the stack and remove volumes' \
	  '  make compose-ps        - show service status' \
	  '  make compose-logs      - follow container logs' \
	  '  make compose-test      - run integration tests via Docker Compose' \
	  '  make smoke-test        - run endpoint smoke tests against localhost' \
	  '  make push-images       - build and push all images (PLATFORM=linux/arm64 for cross-arch)' \
	  '' \
	  'Azure VM:' \
	  '  make smoke-test-vm     - run smoke tests against the Azure VM' \
	  '  make terraform-apply   - provision Azure VM' \
	  '  make ansible-deploy    - configure VM and deploy app' \
	  '  make deploy-azure      - full Azure deploy (terraform + ansible)' \
	  '  make terraform-destroy - destroy Azure VM' \
	  '' \
	  'Azure CI/CD setup (for the GitHub Actions Deploy to Azure workflow):' \
	  '  make azure-cicd-setup  - one-time: register provider, provision, install Docker on VM, sync GH vars' \
	  '  make azure-vm-docker   - install Docker on the provisioned VM (no SSH)' \
	  '  make azure-gh-vars     - sync GitHub Actions variables from Terraform outputs' \
	  '  make azure-cicd-help   - print the remaining secret/SP commands you must run' \
	  '' \
	  'Kubernetes / Helm:' \
	  '  make helm-setup        - seed image-values.yaml from example (first time)' \
	  '  make helm-secrets      - generate secrets-values.yaml (RSA key, random Mongo password, service secret)' \
	  '  make helm-deploy       - install or upgrade the Helm release (ENV=prod for production TLS)' \
	  '  make helm-destroy      - uninstall the Helm release' \
	  '  make smoke-test-k8s    - run smoke tests against the K8s ingress' \
	  '' \
	  'Documentation:' \
	  '  make docs-serve        - serve MkDocs locally at http://localhost:8000' \
	  '' \
	  'Config is read from infra/.env. Override any var: make push-images IMAGE_TAG=my-tag'

# --- Clean & build ---

clean:
	@if find services/spring -path "*/build/*" ! -writable -print -quit 2>/dev/null | grep -q .; then \
	  echo "Non-writable build files detected — cleaning via Docker..."; \
	  docker run --rm -v "$(CURDIR)/services/spring:/workspace" gradle:jdk-25-and-25 \
	    sh -c "find /workspace -maxdepth 3 -name build -type d -exec rm -rf {} + 2>/dev/null"; \
	else \
	  cd services/spring && ./gradlew clean; \
	fi

generate:
	npm ci
	python -m pip install --upgrade pip
	python -m pip install openapi-python-client
	./api/scripts/gen-all.sh
	npx @redocly/cli@2.30.3 lint api/openapi.yaml

spring-build:
	cd services/spring && ./gradlew build

# Install the git pre-push hook that regenerates api/openapi.yaml from the services (code-first),
# so the committed contract never drifts and no manual `make generate` is needed before pushing.
install-hooks:
	@mkdir -p .git/hooks
	@ln -sf ../../scripts/git-hooks/pre-push .git/hooks/pre-push
	@chmod +x scripts/git-hooks/pre-push
	@echo "Installed pre-push hook: regenerates api/openapi.yaml from the Spring services on push."

# Generate each Spring service's OpenAPI spec from its springdoc endpoint (test-based, no live
# DB) and collect them into services/spring/build/openapi/ for inspection or CI artifact upload.
spring-openapi-docs:
	cd services/spring && ./gradlew :user-service:test :content-service:test :api-gateway:test \
	  --tests "*OpenApiDocGenerationTest"
	@mkdir -p services/spring/build/openapi
	@cp services/spring/*/build/openapi/*.json services/spring/build/openapi/
	@echo "OpenAPI specs written to services/spring/build/openapi/:" && ls services/spring/build/openapi/

preflight: generate spring-build helm-lint terraform-validate

# --- Docker Compose ---

compose-up:
	@test -f $(COMPOSE_ENV) || { echo "Error: $(COMPOSE_ENV) not found. Run: cp infra/.env.example infra/.env"; exit 1; }
	docker compose --env-file $(COMPOSE_ENV) $(COMPOSE_FILES) up --build -d

compose-down:
	docker compose --env-file $(COMPOSE_ENV) $(COMPOSE_FILES) down -v
	@if find services/spring -path "*/build/*" ! -writable -print -quit 2>/dev/null | grep -q .; then \
	  docker run --rm -v "$(CURDIR)/services/spring:/workspace" gradle:jdk-25-and-25 \
	    sh -c "chown -R $(COMPOSE_UID):$(COMPOSE_GID) /workspace/*/build 2>/dev/null"; \
	fi

compose-ps:
	docker compose --env-file $(COMPOSE_ENV) $(COMPOSE_FILES) ps

compose-logs:
	docker compose --env-file $(COMPOSE_ENV) $(COMPOSE_FILES) logs -f --tail=100

COMPOSE_TEST = docker compose --env-file $(COMPOSE_ENV) \
  -f infra/docker-compose.yaml \
  -f infra/docker-compose.test.yaml \
  -p rolling-restarts-test \
  --profile test

compose-test:
	$(COMPOSE_TEST) run --rm spring-test; \
	rc=$$?; \
	$(COMPOSE_TEST) down -v; \
	exit $$rc

smoke-test:
	@infra/scripts/smoke-test.sh "http://localhost:$(or $(APP_PORT),8080)"

VM_IP ?= $(shell cd $(TF_DIR) && terraform output -raw vm_public_ip 2>/dev/null)

smoke-test-vm:
	@if [ -z "$(VM_IP)" ]; then echo "Error: could not determine VM IP. Run make terraform-apply first."; exit 1; fi
	@echo "Targeting VM at $(VM_IP)"
	@echo ""
	@infra/scripts/smoke-test.sh "http://$(VM_IP)$(if $(filter-out 80,$(or $(APP_PORT),8080)),:$(APP_PORT),)"

K8S_BASE_HOST ?= rolling-restarts.stud.k8s.aet.cit.tum.de
ENV ?= dev
ifeq ($(ENV),prod)
K8S_HOST ?= $(K8S_BASE_HOST)
else
K8S_HOST ?= dev.$(K8S_BASE_HOST)
endif

smoke-test-k8s:
	@echo "Targeting K8s ingress at $(K8S_HOST)"
	@echo ""
	@infra/scripts/smoke-test.sh --insecure "https://$(K8S_HOST)"

# --- Images ---

# Cross-architecture builds: make push-images PLATFORM=linux/arm64
# Requires: docker buildx, QEMU (apt install qemu-user-static / docker run --privileged multiarch/qemu-user-static --reset)
PLATFORM ?=

SERVICES = web-client api-gateway user-service content-service gen-ai

push-images:
ifdef PLATFORM
	@echo "Cross-building for $(PLATFORM) — using docker buildx"
	docker buildx build --platform $(PLATFORM) --push \
	  --build-arg NEXT_PUBLIC_API_BASE_URL="" \
	  -t $(REGISTRY)/web-client:$(IMAGE_TAG) \
	  -f web-client/Dockerfile web-client
	docker buildx build --platform $(PLATFORM) --push \
	  -t $(REGISTRY)/api-gateway:$(IMAGE_TAG) \
	  -f services/spring/api-gateway/Dockerfile services/spring
	docker buildx build --platform $(PLATFORM) --push \
	  -t $(REGISTRY)/user-service:$(IMAGE_TAG) \
	  -f services/spring/user-service/Dockerfile services/spring
	docker buildx build --platform $(PLATFORM) --push \
	  -t $(REGISTRY)/content-service:$(IMAGE_TAG) \
	  -f services/spring/content-service/Dockerfile services/spring
	docker buildx build --platform $(PLATFORM) --push \
	  -t $(REGISTRY)/gen-ai:$(IMAGE_TAG) \
	  -f services/gen-ai/Dockerfile services/gen-ai
else
	docker compose --env-file $(COMPOSE_ENV) -f infra/docker-compose.yaml build
	docker compose --env-file $(COMPOSE_ENV) -f infra/docker-compose.yaml push
endif

# Resource group used by azure-nuke. Defaults to the Terraform default; override
# (make azure-nuke AZURE_RG=...) if you changed resource_group_name.
AZURE_RG ?= rg-rolling-restarts-dev

# --- Terraform ---

terraform-init:
	cd $(TF_DIR) && terraform init

terraform-plan terraform-apply terraform-destroy: terraform-init
	cd $(TF_DIR) && terraform $(subst terraform-,,$@)

terraform-validate: terraform-init
	cd $(TF_DIR) && terraform validate && terraform fmt -check

# --- Ansible ---

ansible-inventory:
	./infra/scripts/generate-ansible-inventory.sh

ANSIBLE_EXTRA ?=

ansible-deploy:
	cd infra/ansible && ansible-playbook playbooks/deploy.yml \
	  --extra-vars "image_tag=$(IMAGE_TAG) registry=$(REGISTRY) $(ANSIBLE_EXTRA)"

deploy-azure: terraform-apply ansible-inventory ansible-deploy

# --- Cost control -----------------------------------------------------------
# Pause the VM to stop compute billing (disk + static IP still incur a small
# cost). Resume later with azure-start. Requires existing Terraform state.
azure-stop:
	cd $(TF_DIR) && az vm deallocate \
		--resource-group "$$(terraform output -raw resource_group_name)" \
		--name "$$(terraform output -raw vm_name)"

azure-start:
	cd $(TF_DIR) && az vm start \
		--resource-group "$$(terraform output -raw resource_group_name)" \
		--name "$$(terraform output -raw vm_name)"

# Nuke everything by deleting the whole resource group (VM + ACR + networking).
# State-independent fallback for when Terraform state is unavailable; prefer
# `make terraform-destroy` for a clean teardown that keeps state in sync.
azure-nuke:
	az group delete --name "$(AZURE_RG)" --yes

# --- Azure CI/CD one-time setup ---------------------------------------------
# Prepares everything the "Deploy to Azure" GitHub Actions workflow assumes
# already exists (see docs/source/cicd-azure-deploy.md, "Prerequisites"):
#   1. registers the Microsoft.ContainerRegistry provider (once per subscription),
#   2. provisions the VM + ACR via Terraform,
#   3. installs Docker on the VM — the workflow itself does NOT do this, and a
#      fresh VM has no Docker (the classic "docker: command not found" failure),
#   4. syncs the GitHub Actions VARIABLES that are derived from Terraform outputs.
#
# Requires `az login` and `gh auth login` first. The sensitive SECRETS that only
# you can supply (AZURE_CREDENTIALS, LLM_API_KEY, MONGO_ROOT_*) are NOT set here —
# run `make azure-cicd-help` for the exact commands.
GH_REPO ?= $(shell gh repo view --json nameWithOwner --jq .nameWithOwner 2>/dev/null)
DEPLOY_DIR ?= /opt/rolling-restarts

azure-cicd-setup: azure-provider-register terraform-apply azure-vm-docker azure-gh-vars
	@echo ""
	@echo "Azure CI/CD infrastructure is ready."
	@echo "Final step — set the secrets once: make azure-cicd-help"

azure-provider-register:
	az provider register --namespace Microsoft.ContainerRegistry

# Install Docker + the Compose plugin on the VM over the Azure control plane
# (no SSH needed, same mechanism the deploy job uses). Idempotent: the installer
# is skipped when docker is already present, so it is safe to re-run.
azure-vm-docker:
	cd $(TF_DIR) && az vm run-command invoke \
		--resource-group "$$(terraform output -raw resource_group_name)" \
		--name "$$(terraform output -raw vm_name)" \
		--command-id RunShellScript \
		--scripts 'command -v docker >/dev/null 2>&1 || curl -fsSL https://get.docker.com | sh; systemctl enable --now docker; docker --version' \
		--query "value[0].message" -o tsv

# Sync the GitHub Actions variables that are derived from the provisioned infra.
# The remaining variables (LLM_PROVIDER, LLM_MODEL, MONGO_DATABASE) are config
# choices — set them once via the UI or `gh variable set`.
azure-gh-vars:
	@test -n "$(GH_REPO)" || { echo "GH_REPO is empty — run 'gh auth login' or pass GH_REPO=owner/repo"; exit 1; }
	cd $(TF_DIR) && \
	gh variable set ACR_NAME             --repo "$(GH_REPO)" --body "$$(terraform output -raw acr_name)" && \
	gh variable set ACR_LOGIN_SERVER     --repo "$(GH_REPO)" --body "$$(terraform output -raw acr_login_server)" && \
	gh variable set AZURE_RESOURCE_GROUP --repo "$(GH_REPO)" --body "$$(terraform output -raw resource_group_name)" && \
	gh variable set DEPLOY_DIR           --repo "$(GH_REPO)" --body "$(DEPLOY_DIR)" && \
	gh variable set NEXT_PUBLIC_API_BASE_URL --repo "$(GH_REPO)" --body "http://$$(terraform output -raw vm_public_ip)"

# Print the remaining manual steps: creating the service principal and setting the
# sensitive secrets. These take values only you have, so they are not automated.
azure-cicd-help:
	@printf '%s\n' \
	  'Remaining one-time secrets for the Deploy to Azure workflow (repo: $(GH_REPO)):' \
	  '' \
	  '1. Service principal -> AZURE_CREDENTIALS (Contributor covers AcrPush + VM run-command):' \
	  '   SUB=$$(az account show --query id -o tsv)' \
	  '   az ad sp create-for-rbac --name "rolling-restarts-deploy" \\' \
	  '     --role Contributor --scopes "/subscriptions/$$SUB" --sdk-auth > azure-creds.json' \
	  '   gh secret set AZURE_CREDENTIALS --repo $(GH_REPO) < azure-creds.json && rm azure-creds.json' \
	  '' \
	  '2. Application secrets:' \
	  '   printf %s "<mongo-user>" | gh secret set MONGO_ROOT_USERNAME --repo $(GH_REPO)' \
	  '   printf %s "<mongo-pass>" | gh secret set MONGO_ROOT_PASSWORD --repo $(GH_REPO)' \
	  '   printf %s "<llm-api-key>" | gh secret set LLM_API_KEY --repo $(GH_REPO) --env production' \
	  '' \
	  '3. Config variables not derived from Terraform (set once if defaults dont fit):' \
	  '   gh variable set LLM_PROVIDER  --repo $(GH_REPO) --body openai' \
	  '   gh variable set LLM_MODEL     --repo $(GH_REPO) --body gpt-4o-mini' \
	  '   gh variable set MONGO_DATABASE --repo $(GH_REPO) --body mydatabase' \
	  '' \
	  'Then trigger: gh workflow run deploy-azure.yml --repo $(GH_REPO)'

# --- Helm (delegates to infra/helm/Makefile) ---
helm-lint helm-template helm-install helm-upgrade helm-deploy helm-destroy helm-setup helm-secrets:
	$(MAKE) -C $(HELM_DIR) $@

# --- Documentation ---
docs-serve:
	@cp services/gen-ai/README.md docs/source/gen-ai-service.md
	mkdocs serve -f docs/source/mkdocs.yml
