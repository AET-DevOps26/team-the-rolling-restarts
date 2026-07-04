# 02 — Development Workflow

## Repository

- The project must be developed in a **GitHub mono-repository**.
- The system is one integrated deliverable: client, server, GenAI service, deployment
  files, CI/CD workflows, and documentation are versioned **together**.

## Branching & pull requests

- Every **feature or bugfix** must be developed in a **dedicated feature branch**.
- **Direct commits to `main` are not acceptable** as a normal workflow.
- A pull request must be **opened, reviewed, and approved** before merging into `main`.
- Team members must **peer-review** each other's work. Review is a normal, mandatory
  step before merging.

## CI/CD trigger flow

- The **CI pipeline runs automatically on every pull request** and must at minimum
  **build the relevant services and run the automated tests**.
- On **merge to `main`**, the **CD pipeline automatically deploys** the system to a
  Kubernetes environment.

Intended loop:

```
feature branch → CI validation → PR review → merge to main → automatic deploy
```

## Summary table

| Aspect | Requirement |
|--------|-------------|
| Repository | GitHub mono-repo |
| Branching | Each feature or bugfix developed in a feature branch |
| Pull Requests | Mandatory before merge into main |
| Code review | Peer review and approval by team members required |
| CI checks | Automated tests and validation on every PR |
| CD behaviour | Automatic deployment to Kubernetes on merge to main |

> Detailed CI/CD implementation requirements are in [06-ci-cd.md](06-ci-cd.md).
