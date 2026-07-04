# 08 — Testing

Testing must **validate the behaviour** of the system, covering critical server-side
logic, relevant parts of the GenAI component, and important client-side workflows.

## Requirements

- **Unit tests are mandatory** for **critical server and GenAI logic**.
- **Client-side tests** should cover **core workflows and interactions**.
- **All tests must run automatically in the CI pipeline**.
- The pipeline is the **main enforcement point** for system stability and must
  **prevent broken changes from being merged**.

## Summary table

| Aspect | Requirement |
|--------|-------------|
| Unit Tests | Must cover critical server and GenAI logic |
| Client Tests | Should cover core workflows and interactions |
| CI Testing | All tests must run automatically in the CI pipeline |

> The testing suite must ship with **instructions for how to run it**
> (see [10-deliverables.md](10-deliverables.md)). CI wiring is in
> [06-ci-cd.md](06-ci-cd.md).
