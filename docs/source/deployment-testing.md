# Deployment Testing Guide

How to verify that changes aren't breaking anything across all deployment targets.
All commands run from the **project root**. Run `make help` to list every target.

---

## 1. Local Pre-flight (always run first)

```bash
make preflight
```

This runs, in order:

| Step | Target | What it does |
| ---- | ------ | ------------ |
| 1 | `make generate` | Install OpenAPI tooling, lint the spec, generate Spring/Python/TypeScript clients |
| 2 | `make spring-build` | Compile and test all Spring modules (`./gradlew build`) |
| 3 | `make helm-lint` | Lint the Helm chart |
| 4 | `make terraform-validate` | `terraform init` + `validate` + `fmt -check` |

Each step can also be run individually.

---

## 2. Docker Compose (Local)

### Start the stack

```bash
cp infra/.env.example infra/.env    # first time only — fill in required values
make compose-up                     # builds and starts all services (detached)
```

### Connect to MongoDB locally

Use this URI in MongoDB Compass or `mongosh` (requires the stack to be running):

```text
mongodb://root:secret@localhost:27017/?authSource=admin
```

The `?authSource=admin` is required — the root user is created in the `admin` database.
The services use separate databases: `users` (user-service) and `content` (content-service).

### Verify services are healthy

```bash
make compose-ps                     # all services should show "healthy"
make compose-logs                   # follow logs (Ctrl-C to stop)
```

### Endpoint checklist

| Endpoint | Expected | What it tests |
|----------|----------|---------------|
| `GET http://localhost:8080/actuator/health` | `200 {"status":"UP"}` | API gateway is running |
| `GET http://localhost:8081/actuator/health` | `200 {"status":"UP"}` | User service + MongoDB connection |
| `GET http://localhost:8082/actuator/health` | `200 {"status":"UP"}` | Content service + MongoDB connection |
| `GET http://localhost:8080/` | `200 {"message":"Hello, World!"}` | Gateway routing works |
| `GET http://localhost:8080/swagger-ui.html` | `302` redirect to Swagger UI | OpenAPI docs accessible |
| `POST http://localhost:8080/api/users/auth/register` | `201` with valid body, `400` with empty body | User registration + validation |
| `GET http://localhost:8080/api/content/sources` | `200 [...]` | Content service accessible through gateway |
| `GET http://localhost:8080/api/content/topics` | `200 [...]` | Topics endpoint |
| `GET http://localhost:8080/api/content/articles` | `200 {"content":[...]}` | Paginated articles |
| `GET http://localhost:3000` | `200` HTML page | Web client serving |
| `GET http://localhost:8000/health` | `200` | GenAI service |

### Smoke test

```bash
make smoke-test
```

Hits all health endpoints, tests content retrieval, registration (valid + invalid), and CORS headers. Safe to run repeatedly — uses a unique username per run.

### Integration tests

```bash
make compose-test
```

### Tear down

```bash
make compose-down                   # stops containers and removes volumes
```

---

## 3. Azure VM Deployment

### Deploy

```bash
cp infra/ansible/group_vars/all.yml.example infra/ansible/group_vars/all.yml
# Edit all.yml with real values

make deploy-azure                   # terraform apply → generate inventory → ansible playbook
```

Or run each step individually:

```bash
make terraform-apply                # provision the VM
make ansible-inventory              # generate inventory from Terraform outputs
make ansible-deploy                 # run the Ansible playbook
```

### Troubleshooting

**SSH "Host key verification failed"** — the new VM's host key isn't in your `known_hosts` yet:

```bash
VM_IP=$(cd infra/terraform/azure-vm && terraform output -raw vm_public_ip)
ssh-keyscan -H $VM_IP >> ~/.ssh/known_hosts
```

**Azure CLI token expired** — re-authenticate before `terraform apply`:

```bash
az logout
az login
```

**Health check times out** — the first deploy builds all Docker images from scratch on the VM, which can take 10-20 minutes on a `Standard_B2s_v2`. The playbook retries for up to 20 minutes. If it still fails, SSH in and check progress:

```bash
ssh azureuser@$VM_IP "sudo docker compose -f /opt/rolling-restarts/infra/docker-compose.yaml logs --tail=20"
```

### Verify

```bash
VM_IP=$(cd infra/terraform/azure-vm && terraform output -raw vm_public_ip)

# SSH and check service status
ssh azureuser@$VM_IP "sudo systemctl status rolling-restarts"
ssh azureuser@$VM_IP "sudo docker ps"

# Test endpoints
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
make terraform-destroy
```

---

## 4. Kubernetes / Helm Deployment

### Deploy

```bash
cp infra/helm/secrets-values.example.yaml infra/helm/secrets-values.yaml
# Edit with real database credentials (auto-detected by Makefile when present)

make helm-deploy                    # install or upgrade

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
HOST="rolling-restarts.stud.k8s.aet.cit.tum.de"

curl -s https://$HOST/actuator/health | jq .status
curl -s https://$HOST/api/content/sources | jq length
curl -s https://$HOST/api/content/articles | jq .totalElements
curl -s https://$HOST -o /dev/null -w "%{http_code}"

# Registration test
curl -s -X POST https://$HOST/api/users/auth/register \
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
make helm-destroy
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
