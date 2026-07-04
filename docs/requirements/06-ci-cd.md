# CI/CD

The system must include a working CI/CD pipeline implemented with GitHub Actions. The pipeline must reflect the actual lifecycle of the system and must be reliable enough that it can be treated as part of the system.

Continuous Integration must build and test all services. It must also perform static analysis or linting where appropriate. This means that the CI pipeline should validate the codebase before integration into the main branch and should fail when the system is not in a correct or stable state.

Continuous Deployment must automatically deploy to Kubernetes after merge to main. This deployment process must be reproducible and maintainable. The workflow must make correct use of secrets and environment-specific variables. Hardcoded tokens should be avoided.

| Aspect | Requirement |
| --- | --- |
| Tooling | GitHub Actions |
| CI tasks | Build and test all services; perform static analysis/linting |
| CD tasks | Automatically deploy to Kubernetes on merge to main |
| Configuration | Must use secrets and support environment-specific variables |
