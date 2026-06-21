.PHONY: help clean generate spring-build preflight \
       compose-up compose-down compose-ps compose-logs compose-test smoke-test \
       terraform-init terraform-plan terraform-apply terraform-destroy terraform-validate \
       ansible-inventory ansible-deploy deploy-azure \
       helm-lint helm-template helm-install helm-upgrade helm-deploy helm-destroy

HELM_DIR    ?= infra/helm
COMPOSE_ENV ?= infra/.env
COMPOSE_FILES = -f infra/docker-compose.yaml -f infra/docker-compose.dev.yaml

export COMPOSE_UID ?= $(shell id -u)
export COMPOSE_GID ?= $(shell id -g)

help:
	@printf '%s\n' \
	  'Pre-flight & build:' \
	  '  make generate          - generate OpenAPI clients (Spring, Python, TypeScript)' \
	  '  make spring-build      - compile and test Spring services' \
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
	  '' \
	  'Azure VM:' \
	  '  make terraform-apply   - provision Azure VM' \
	  '  make ansible-deploy    - configure VM and deploy app' \
	  '  make deploy-azure      - full Azure deploy (terraform + ansible)' \
	  '  make terraform-destroy - destroy Azure VM' \
	  '' \
	  'Kubernetes / Helm:' \
	  '  make helm-deploy       - install or upgrade the Helm release' \
	  '  make helm-destroy      - uninstall the Helm release' \
	  '' \
	  'Pass ENV=prod SECRETS="-f secrets-values.yaml" to Helm targets for production.'

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
	npx @redocly/cli@2.30.3 lint api/openapi.yaml
	./api/scripts/gen-all.sh

spring-build:
	cd services/spring && ./gradlew build

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

compose-test:
	docker compose --env-file $(COMPOSE_ENV) \
	  -f infra/docker-compose.yaml \
	  -f infra/docker-compose.test.yaml \
	  --profile test run --rm spring-test; \
	rc=$$?; \
	docker compose --env-file $(COMPOSE_ENV) \
	  -f infra/docker-compose.yaml \
	  -f infra/docker-compose.test.yaml \
	  --profile test down; \
	exit $$rc

smoke-test:
	@pass=0; fail=0; \
	check() { \
	  desc="$$1"; expected="$$2"; actual="$$3"; \
	  if [ "$$actual" = "$$expected" ]; then \
	    printf '  \033[32m✓\033[0m %s (got %s)\n' "$$desc" "$$actual"; \
	    pass=$$((pass + 1)); \
	  else \
	    printf '  \033[31m✗\033[0m %s (expected %s, got %s)\n' "$$desc" "$$expected" "$$actual"; \
	    fail=$$((fail + 1)); \
	  fi; \
	}; \
	echo "=== Health Checks ==="; \
	check "API Gateway health" '"UP"' "$$(curl -sf http://localhost:8080/actuator/health 2>/dev/null | jq -r .status | jq -Rs 'rtrimstr("\n")')"; \
	check "User Service health" '"UP"' "$$(curl -sf http://localhost:8081/actuator/health 2>/dev/null | jq -r .status | jq -Rs 'rtrimstr("\n")')"; \
	check "Content Service health" '"UP"' "$$(curl -sf http://localhost:8082/actuator/health 2>/dev/null | jq -r .status | jq -Rs 'rtrimstr("\n")')"; \
	check "GenAI health" '"ok"' "$$(curl -sf http://localhost:8000/health 2>/dev/null | jq -r .status | jq -Rs 'rtrimstr("\n")')"; \
	echo ""; \
	echo "=== Gateway Routing ==="; \
	check "GET /" '"Hello, World!"' "$$(curl -sf http://localhost:8080/ 2>/dev/null | jq -r .message | jq -Rs 'rtrimstr("\n")')"; \
	check "GET /api/content/sources" "200" "$$(curl -so /dev/null -w '%{http_code}' http://localhost:8080/api/content/sources 2>/dev/null)"; \
	check "GET /api/content/articles" "200" "$$(curl -so /dev/null -w '%{http_code}' http://localhost:8080/api/content/articles 2>/dev/null)"; \
	check "GET /api/content/topics" "200" "$$(curl -so /dev/null -w '%{http_code}' http://localhost:8080/api/content/topics 2>/dev/null)"; \
	echo ""; \
	echo "=== Registration ==="; \
	check "Invalid body returns 400" "400" "$$(curl -so /dev/null -w '%{http_code}' -X POST http://localhost:8080/api/users/auth/register \
	  -H 'Content-Type: application/json' \
	  -d '{"username":"","email":"bad","password":"short"}' 2>/dev/null)"; \
	reg_code=$$(curl -so /dev/null -w '%{http_code}' -X POST http://localhost:8080/api/users/auth/register \
	  -H 'Content-Type: application/json' \
	  -d "{\"username\":\"smoke$$$$\",\"email\":\"smoke$$$$@test.com\",\"password\":\"password123\",\"name\":\"Smoke Test\"}" 2>/dev/null); \
	check "Valid body returns 201 (or 409 re-run)" "ok" "$$([ "$$reg_code" = "201" ] || [ "$$reg_code" = "409" ] && echo ok || echo $$reg_code)"; \
	echo ""; \
	echo "=== CORS ==="; \
	cors=$$(curl -sf -I -X OPTIONS http://localhost:8080/api/content/articles \
	  -H "Origin: http://localhost:3000" \
	  -H "Access-Control-Request-Method: GET" 2>/dev/null | grep -ci access-control); \
	check "CORS headers present" "yes" "$$([ "$$cors" -gt 0 ] 2>/dev/null && echo yes || echo no)"; \
	echo ""; \
	echo "=== Web Client ==="; \
	check "GET localhost:3000" "200" "$$(curl -so /dev/null -w '%{http_code}' http://localhost:3000 2>/dev/null)"; \
	echo ""; \
	total=$$((pass + fail)); \
	if [ $$fail -eq 0 ]; then \
	  printf '\033[32mAll %d checks passed.\033[0m\n' $$total; \
	else \
	  printf '\033[31m%d/%d checks failed.\033[0m\n' $$fail $$total; \
	  exit 1; \
	fi

# --- Terraform ---

terraform-init:
	cd infra/terraform/azure-vm && terraform init

terraform-plan: terraform-init
	cd infra/terraform/azure-vm && terraform plan

terraform-apply: terraform-init
	cd infra/terraform/azure-vm && terraform apply

terraform-destroy: terraform-init
	cd infra/terraform/azure-vm && terraform destroy

terraform-validate: terraform-init
	cd infra/terraform/azure-vm && terraform validate && terraform fmt -check

# --- Ansible ---

ansible-inventory:
	./infra/scripts/generate-ansible-inventory.sh

ansible-deploy:
	cd infra/ansible && ansible-playbook playbooks/deploy.yml

deploy-azure: terraform-apply ansible-inventory ansible-deploy

# --- Helm (delegates to infra/helm/Makefile) ---

helm-lint:
	$(MAKE) -C $(HELM_DIR) helm-lint

helm-template:
	$(MAKE) -C $(HELM_DIR) helm-template

helm-install:
	$(MAKE) -C $(HELM_DIR) helm-install

helm-upgrade:
	$(MAKE) -C $(HELM_DIR) helm-upgrade

helm-deploy:
	$(MAKE) -C $(HELM_DIR) helm-deploy

helm-destroy:
	$(MAKE) -C $(HELM_DIR) helm-destroy
