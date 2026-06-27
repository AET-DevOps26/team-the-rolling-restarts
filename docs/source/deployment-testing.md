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

### Connect MongoDB Compass (local)

The Docker Compose stack exposes MongoDB on port 27017. Use this URI in MongoDB Compass or `mongosh` (requires the stack to be running):

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
 | ---------- | ---------- | --------------- |
| `GET http://localhost:8080/actuator/health` | `200 {"status":"UP"}` | API gateway is running |
| `GET http://localhost:8081/actuator/health` | `200 {"status":"UP"}` | User service + MongoDB connection |
| `GET http://localhost:8082/actuator/health` | `200 {"status":"UP"}` | Content service + MongoDB connection |
| `GET http://localhost:8080/` | `200` HTML page | Reverse proxy serves the web client at `/` |
| `GET http://localhost:8080/swagger-ui.html` | `302` redirect to Swagger UI | OpenAPI docs accessible |
| `POST http://localhost:8080/api/users/auth/register` | `201` with valid body, `400` with empty body | User registration + validation |
| `POST http://localhost:8080/api/users/auth/login` | `200 {"token":"..."}` with valid creds, `401` with wrong password | Login + JWT issuance |
| `GET http://localhost:8080/api/content/sources` | `200 [...]` | Content service accessible through gateway |
| `GET http://localhost:8080/api/content/topics` | `200 [...]` | Topics endpoint |
| `GET http://localhost:8080/api/content/articles` | `200 {"content":[...]}` | Paginated articles |
| `GET http://localhost:8000/health` | `200` | GenAI service (direct port, dev only) |

### Smoke test

```bash
make smoke-test
```

Hits health endpoints, content retrieval, registration (valid + invalid), and login (valid credentials + wrong password). Safe to run repeatedly — uses a unique username per run.

### Integration tests

```bash
make compose-test
```

### Tear down (local)

```bash
make compose-down                   # stops containers and removes volumes
```

---

## 3. Azure VM Deployment

The VM deploys pre-built container images — no compilation happens on the VM.
There are two deployment paths:

- **CI/CD pipeline** (recommended): GitHub Actions builds, pushes to ACR, and deploys via `az vm run-command` — no SSH needed. See [Azure CD Pipeline](cicd-azure-deploy.md) for full setup.
- **Manual (Ansible)**: Build and push images yourself, then deploy via Ansible over SSH. See [Azure VM Deployment](azure-vm-deployment.md) for the runbook.

### Push images

Images are built and pushed automatically by CI:

- **Azure CD pipeline** (`deploy-azure.yml`): pushes to ACR on merge to `main` or manual dispatch.
- **GHCR pipeline** (`upload_images.yml`): pushes to GHCR on every push (used by the Helm/K8s path).

**Manual push** (for testing feature branches or when CI is unavailable):

```bash
# Push to the org registry (requires write:packages permission — see GHCR setup below)
make push-images IMAGE_TAG=<tag-name>

# Or push to your personal registry (no extra permissions needed)
make push-images IMAGE_TAG=<tag-name> REGISTRY=ghcr.io/<github-username>/rolling-restarts
```

`IMAGE_TAG` defaults to the current commit SHA if not specified.

#### GHCR setup (for manual pushes)

Authenticate the GitHub CLI with the `write:packages` scope:

```bash
gh auth login --scopes write:packages,read:packages
gh auth token | docker login ghcr.io -u <github-username> --password-stdin
```

To allow the VM to pull GHCR images without credentials, make the packages public:
**github.com/orgs/\<org\>/packages** → each package → **Package settings** → **Change visibility** → **Public**.

### Deploy (manual / Ansible path)

```bash
cp infra/ansible/group_vars/all.yml.example infra/ansible/group_vars/all.yml
# Edit all.yml with real values

# Full deploy (terraform + ansible)
make deploy-azure IMAGE_TAG=<tag-name>

# Or with a personal registry
make deploy-azure IMAGE_TAG=<tag-name> REGISTRY=ghcr.io/<github-username>/rolling-restarts
```

Or run each step individually:

```bash
make terraform-apply                # provision the VM
make ansible-inventory              # generate inventory from Terraform outputs
make ansible-deploy IMAGE_TAG=<tag-name>  # deploy images to the VM
```

### Troubleshooting

**SSH "Host key verification failed"** — `make ansible-inventory` automatically adds the VM's host key to `~/.ssh/known_hosts`, so this should not happen in the normal `make deploy-azure` flow. If it does (e.g. the VM was re-provisioned with the same IP), clear the stale key and re-run:

```bash
VM_IP=$(cd infra/terraform/azure-vm && terraform output -raw vm_public_ip)
ssh-keygen -R $VM_IP
make ansible-inventory
```

**Azure CLI token expired** — re-authenticate before `terraform apply`:

```bash
az logout
az login
```

**Health check times out** — the VM pulls pre-built images, so startup should be fast. If the health check still fails, SSH in and check:

```bash
ssh azureuser@$VM_IP "sudo docker compose -f /opt/rolling-restarts/docker-compose.yaml -f /opt/rolling-restarts/docker-compose.prod.yaml logs --tail=20"
```

**Images not found** — make sure you ran `make push-images` with the same `IMAGE_TAG` and `REGISTRY` before deploying. If using a private registry, set `registry_token` in `group_vars/all.yml`.

### Verify VM deployment

#### VM smoke test

```bash
make smoke-test-vm
```

Runs the same checks as `make smoke-test` (health, routing, registration, login) against the VM. The VM IP is read automatically from Terraform output.

#### VM manual checks

For deeper debugging or one-off verification:

```bash
VM_IP=$(cd infra/terraform/azure-vm && terraform output -raw vm_public_ip)

# SSH and check service status
ssh azureuser@$VM_IP "sudo systemctl status rolling-restarts"
ssh azureuser@$VM_IP "sudo docker ps"

# Test endpoints — everything goes through the reverse proxy on port 80 (the only public port).
# Backend service ports (8081/8082/8000), MongoDB (27017) and Grafana (3001) are bound to the
# VM's loopback by docker-compose.prod.yaml, so they are NOT reachable on the public IP — use
# an SSH tunnel for those (see the MongoDB Compass section).
curl -s http://$VM_IP/                              # web client (served via reverse proxy)
curl -s http://$VM_IP/actuator/health               # gateway health
curl -s http://$VM_IP/api/content/sources           # content routing
curl -s http://$VM_IP/api/content/articles          # articles

# Registration + login
curl -s -X POST http://$VM_IP/api/users/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"vmtest","email":"vm@test.com","password":"password123","name":"VM Test"}'

curl -s -X POST http://$VM_IP/api/users/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"vmtest","password":"password123"}'
```

#### Connect MongoDB Compass (VM)

MongoDB's port (27017) is not exposed to the internet — connect via SSH tunnel instead. MongoDB Compass has built-in support for this:

1. Open Compass → **New Connection** → **Advanced Connection Options** → **Proxy/SSH** tab.
2. Select **SSH with Identity File** and fill in:
   - **SSH Hostname**: `<VM_IP>`
   - **SSH Port**: `22`
   - **SSH Username**: `azureuser`
   - **SSH Identity File**: `~/.ssh/id_ed25519`
3. Use this connection string:

   ```text
   mongodb://root:<MONGO_ROOT_PASSWORD>@localhost:27017/?authSource=admin
   ```

   Replace `<MONGO_ROOT_PASSWORD>` with the value from `infra/ansible/group_vars/all.yml`.

The two databases are `users` (user-service) and `content` (content-service).

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

> **Image values are applied automatically.** Every `helm-*` Make target overlays
> `infra/helm/image-values.yaml` last (`-f values.yaml … -f image-values.yaml`), so you don't pass
> it explicitly. CI regenerates this file on each push (`upload_images.yml`); for a local deploy run
> `make helm-setup` once to seed it from `image-values.example.yaml`. Any service **not** overridden
> there falls back to `global.registry/<imageName>:<global.tag>` from `values.yaml`. The file must
> exist, or Helm errors on the missing `-f` argument.

### Verify K8s deployment

#### K8s smoke test

```bash
make smoke-test-k8s
```

Runs the same checks as `make smoke-test` (health, routing, registration, login) against the K8s ingress. Override the host with `make smoke-test-k8s K8S_HOST=your-host.example.com`.

#### K8s manual checks

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

# Check logs if something fails
kubectl logs -l app=api-gateway --tail=50
kubectl logs -l app=user-service --tail=50
kubectl logs -l app=content-service --tail=50
```

#### Connect MongoDB Compass (K8s)

MongoDB is only accessible inside the cluster. Use `kubectl port-forward` to tunnel the connection to your machine:

```bash
kubectl port-forward svc/mongodb 27017:27017
```

Then connect Compass with:

```text
mongodb://root:<MONGO_ROOT_PASSWORD>@localhost:27017/?authSource=admin
```

Replace `<MONGO_ROOT_PASSWORD>` with the value from `infra/helm/secrets-values.yaml`. Keep the port-forward running while using Compass.
The services use separate databases: `users` (user-service) and `content` (content-service).

### Verify Helm-specific resources

```bash
# PDBs should exist when replicas > 1 (prod)
kubectl get pdb

# Service accounts should exist
kubectl get sa | grep -E "web-client|api-gateway|user-service|content-service|gen-ai"

# Startup probes should prevent premature restarts
kubectl describe pod -l app=api-gateway | grep -A3 "Startup:"
```

### Tear down (K8s)

```bash
make helm-destroy
```

---

## Endpoint Reference

All endpoints are reached through the single entry point (the nginx reverse proxy on `APP_PORT` —
port 8080 locally, port 80 on the VM, or the ingress in K8s). The entry point routes `/api`, `/actuator`,
`/swagger-ui`, and `/v3/api-docs` to the API gateway, and everything else (`/`) to the web client.
The paths below are the gateway routes; `/` at the edge returns the web-client UI, not the gateway
root.

### Public Endpoints (no auth required)

| Method | Path | Response |
| ------ | ---- | -------- |
| GET | `/` (edge) | Web-client HTML page |
| GET | `/actuator/health` | `{"status": "UP"}` (gateway health) |
| GET | `/swagger-ui.html` | Swagger UI redirect |
| GET | `/api/content/sources` | List of RSS sources |
| GET | `/api/content/topics` | List of topics |
| GET | `/api/content/articles` | Paginated articles |
| GET | `/api/content/articles/{id}` | Single article |
| POST | `/api/users/auth/register` | Register new user |
| POST | `/api/users/auth/login` | Authenticate and obtain JWT |

### Protected Endpoints (require JWT Bearer token)

| Method | Path | Description |
| ------ | ---- | ----------- |
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
