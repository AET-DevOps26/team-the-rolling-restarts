# 06 — CI/CD

The system must include a **working CI/CD pipeline implemented with GitHub Actions**.
The pipeline must reflect the actual lifecycle of the system and be reliable enough to
be treated as **part of the system**.

## Continuous Integration

- Must **build and test all services**.
- Must perform **static analysis or linting** where appropriate.
- Must validate the codebase **before** integration into `main` and **fail** when the
  system is not in a correct or stable state.

## Continuous Deployment

- Must **automatically deploy to Kubernetes after merge to `main`**.
- Deployment must be **reproducible and maintainable**.

## Configuration

- Must make correct use of **secrets** and **environment-specific variables**.
- **Hardcoded tokens must be avoided.**

## Summary table

| Aspect | Requirement |
|--------|-------------|
| Tooling | GitHub Actions |
| CI tasks | Build and test all services; perform static analysis/linting |
| CD tasks | Automatically deploy to Kubernetes on merge to main |
| Configuration | Must use secrets and support environment-specific variables |

> Related: PR/merge trigger flow in [02-development-workflow.md](02-development-workflow.md);
> what "test all services" means in [08-testing.md](08-testing.md).
