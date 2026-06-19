# Deployment Testing Guide

How to verify that changes aren't breaking anything across all deployment targets.

---

## 1. Local Pre-flight (always run first)

```bash
# From project root — install OpenAPI tooling and generate clients (same as CI)
npm ci
python -m pip install --upgrade pip
python -m pip install openapi-python-client

# Lint the spec, then generate all clients (Spring, Python, TypeScript)
npx @redocly/cli@2.30.3 lint api/openapi.yaml
./api/scripts/gen-all.sh              # generates services/spring/generated/, services/gen-ai/generated/, web-client/src/generated/

# From services/spring/
cd services/spring
./gradlew build                       # must pass: compiles all modules, runs all tests
cd ../..

# Helm chart
helm lint infra/helm                  # must pass: no chart errors

# Terraform (if infra changes)
cd infra/terraform/azure-vm
terraform init -backend=false
terraform validate                    # must pass: valid HCL
terraform fmt -check                  # must pass: consistent formatting
```

---

## 2. Docker Compose (Local)

### Start the stack

```bash
# From project root
cp infra/.env.example infra/.env    # fill in required values
docker compose --env-file infra/.env \
  -f infra/docker-compose.yaml \
  -f infra/docker-compose.dev.yaml \
  up --build
```

### Verify endpoints

Wait for all health checks to pass (`docker compose ps` — all services should be `healthy`).

| Endpoint | Expected | What it tests |
|----------|----------|---------------|
| `GET http://localhost:8080/actuator/health` | `200 {"status":"UP"}` | API gateway is running |
| `GET http://localhost:8081/actuator/health` | `200 {"status":"UP"}` | User service + PostgreSQL connection |
| `GET http://localhost:8082/actuator/health` | `200 {"status":"UP"}` | Content service + MongoDB connection |
| `GET http://localhost:8080/` | `200 {"message":"Hello, World!"}` | Gateway routing works |
| `GET http://localhost:8080/swagger-ui.html` | `302` redirect to Swagger UI | OpenAPI docs accessible |
| `POST http://localhost:8080/api/users/auth/register` | `201` with valid body, `400` with empty body | User registration + validation |
| `GET http://localhost:8080/api/content/sources` | `200 [...]` | Content service accessible through gateway |
| `GET http://localhost:8080/api/content/topics` | `200 [...]` | Topics endpoint |
| `GET http://localhost:8080/api/content/articles` | `200 {"content":[...]}` | Paginated articles |
| `GET http://localhost:3000` | `200` HTML page | Web client serving |
| `GET http://localhost:8000/health` | `200` | GenAI service |

### Quick smoke test script

```bash
echo "=== API Gateway ==="
curl -s http://localhost:8080/actuator/health | jq .status
curl -s http://localhost:8080/ | jq .message

echo "=== User Service ==="
curl -s http://localhost:8081/actuator/health | jq .status

echo "=== Content Service ==="
curl -s http://localhost:8082/actuator/health | jq .status
curl -s http://localhost:8080/api/content/sources | jq length
curl -s http://localhost:8080/api/content/articles | jq .totalElements

echo "=== Registration (validation test) ==="
# Should return 400 with validation errors
curl -s -X POST http://localhost:8080/api/users/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"","email":"bad","password":"short"}' | jq .code

# Should return 201
curl -s -X POST http://localhost:8080/api/users/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser","email":"test@example.com","password":"password123","name":"Test User"}' | jq .username

echo "=== CORS (should return Access-Control-Allow-Origin) ==="
curl -s -I -X OPTIONS http://localhost:8080/api/content/articles \
  -H "Origin: http://localhost:3000" \
  -H "Access-Control-Request-Method: GET" | grep -i access-control
```

### Run integration tests

```bash
docker compose --env-file infra/.env \
  -f infra/docker-compose.yaml \
  -f infra/docker-compose.test.yaml \
  --profile test run --rm spring-test
```

### Tear down

```bash
docker compose --env-file infra/.env -f infra/docker-compose.yaml down -v
```

---

## 3. Azure VM Deployment

### Deploy

```bash
# From project root (or use the Makefile targets)
cd infra/terraform/azure-vm
terraform apply

# Generate Ansible inventory from Terraform outputs
cd ../../..
./infra/scripts/generate-ansible-inventory.sh

# Configure group_vars
cp infra/ansible/group_vars/all.yml.example infra/ansible/group_vars/all.yml
# Edit all.yml with real values

# Run playbook
cd infra/ansible
ansible-playbook playbooks/deploy.yml
```

### Verify

```bash
VM_IP=$(cd infra/terraform/azure-vm && terraform output -raw vm_public_ip)

# SSH and check service status
ssh azureuser@$VM_IP "sudo systemctl status rolling-restarts"
ssh azureuser@$VM_IP "sudo docker ps"

# Test endpoints (replace 3000 with your app port)
curl -s http://$VM_IP:3000                          # web client
curl -s http://$VM_IP:8080/actuator/health          # gateway health
curl -s http://$VM_IP:8080/                         # hello world
curl -s http://$VM_IP:8080/api/content/sources      # content routing
curl -s http://$VM_IP:8080/api/content/articles     # articles

# Registration test
curl -s -X POST http://$VM_IP:8080/api/users/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"vmtest","email":"vm@test.com","password":"password123","name":"VM Test"}'
```

### Tear down (to save credits)

```bash
cd infra/terraform/azure-vm
terraform destroy
```

---

## 4. Kubernetes / Helm Deployment

### Deploy

```bash
cd infra/helm

# Create secrets values file
cp secrets-values.example.yaml secrets-values.yaml
# Edit with real database credentials

# Install or upgrade
make helm-deploy          # or: helm upgrade --install newsgenai . -f values.yaml -f secrets-values.yaml

# For production (2 replicas, letsencrypt-prod):
make helm-deploy ENV=prod
```

### Verify

```bash
# Check pods are running
kubectl get pods -l app.kubernetes.io/part-of=newsGenAI

# Check all pods reach Ready state
kubectl wait --for=condition=Ready pod -l app.kubernetes.io/part-of=newsGenAI --timeout=300s

# Check services
kubectl get svc

# Port-forward for local testing (if no ingress)
kubectl port-forward svc/api-gateway 8080:8080 &
kubectl port-forward svc/web-client 3000:3000 &

# Test endpoints via ingress (if configured)
API_HOST="api.rolling-restarts.stud.k8s.aet.cit.tum.de"
WEB_HOST="app.rolling-restarts.stud.k8s.aet.cit.tum.de"

curl -s https://$API_HOST/actuator/health | jq .status
curl -s https://$API_HOST/ | jq .message
curl -s https://$API_HOST/api/content/sources | jq length
curl -s https://$API_HOST/api/content/articles | jq .totalElements
curl -s https://$WEB_HOST -o /dev/null -w "%{http_code}"

# Registration test
curl -s -X POST https://$API_HOST/api/users/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"k8stest","email":"k8s@test.com","password":"password123","name":"K8s Test"}'

# Check logs if something fails
kubectl logs -l app=api-gateway --tail=50
kubectl logs -l app=user-service --tail=50
kubectl logs -l app=content-service --tail=50
```

### Verify Helm-specific resources

```bash
# PDBs should exist when replicas > 1 (prod)
kubectl get pdb

# Service accounts should exist
kubectl get sa | grep -E "web-client|api-gateway|user-service|content-service|gen-ai"

# Startup probes should prevent premature restarts
kubectl describe pod -l app=api-gateway | grep -A3 "Startup:"
```

### Tear down

```bash
make helm-destroy         # or: helm uninstall newsgenai
```

---

## Endpoint Reference

All endpoints accessible through the API gateway (port 8080 locally, or via ingress in K8s).

### Public Endpoints (no auth required)

| Method | Path | Response |
|--------|------|----------|
| GET | `/` | `{"message": "Hello, World!"}` |
| GET | `/test` | `{"message": "Hello, World!\nTest!"}` |
| GET | `/actuator/health` | `{"status": "UP"}` |
| GET | `/swagger-ui.html` | Swagger UI redirect |
| GET | `/api/content/sources` | List of RSS sources |
| GET | `/api/content/topics` | List of topics |
| GET | `/api/content/articles` | Paginated articles |
| GET | `/api/content/articles/{id}` | Single article |
| POST | `/api/users/auth/register` | Register new user |

### Protected Endpoints (require JWT Bearer token)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/users/users/me` | Current user profile |
| PUT | `/api/users/users/me` | Update profile |
| GET | `/api/users/users/me/settings` | User settings |
| PUT | `/api/users/users/me/settings` | Update settings |
| POST | `/api/content/sources` | Add RSS source |
| DELETE | `/api/content/sources/{id}` | Remove source |
| POST | `/api/content/articles/saved` | Batch-get saved articles |

### Error Response Format

All services return errors in a consistent format:

```json
{
  "timestamp": "2024-01-15T10:30:00Z",
  "code": 400,
  "message": "Validation failed",
  "details": ["username: must not be blank", "password: size must be at least 8"],
  "path": "/auth/register"
}
```
