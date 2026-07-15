# Environment and Deployment

All components must be fully containerised and runnable locally using a compose-based setup. This includes the client, the server-side services, the GenAI service, and the database. Each component must therefore have its own Dockerfile. The local setup must support end-to-end system execution through a docker-compose.yml file.

The local setup must be simple. The system must be runnable in three or fewer commands, for example by building and starting through docker compose up. The setup must provide sane defaults, which means that students should not rely on long manual configuration instructions or complex environment preparation steps. A new user must be able to start the system without reverse-engineering the project.

The same system must also be deployable to Kubernetes. Deployment may be implemented either through Helm charts or raw Kubernetes manifests. The project must support deployment on the course infrastructure via Rancher and also on one cloud environment, which in your current version is Azure. Configuration must be externalised using environment variables, Secrets, and similar mechanisms. Hardcoded credentials, hardcoded environment-dependent values, or manual configuration in the code are not acceptable.

| Aspect | Requirement |
| --- | --- |
| Containerisation | All components (server, client, GenAI, DB) must have their own Dockerfile |
| Local orchestration | docker-compose.yml must run the system end-to-end locally |
| Setup | Runnable in three or fewer commands; no complex manual ENV setup |
| Kubernetes | Deployable using Helm or raw manifests |
| Environments | Local infrastructure (Rancher) and a cloud option (Azure) |
