# 09 — Engineering Artefacts

Teams must provide engineering artefacts that explain how the system is structured and
how it works. These must **reflect the actual implementation** and support
understanding, reproducibility, and evaluation.

## Required artefacts

- A **high-level architecture description**.
- **Decomposition** into subsystems and their interfaces.
- **UML-style diagrams (mandatory)** — specifically all three of:
  - **Subsystem Decomposition** diagram,
  - **Use Case** diagram,
  - **Analysis Object Model**.
- **API documentation** via **OpenAPI/Swagger**, exposing **Swagger UI** or an
  equivalent interface.

## Summary table

| Aspect | Requirement |
|--------|-------------|
| Architecture | High-level system description |
| Decomposition | Subsystems and interfaces |
| Architecture Diagrams | Mandatory UML: Subsystem Decomposition, Use Case, Analysis Object Model |
| API documentation | OpenAPI/Swagger docs; expose Swagger UI or equivalent |

> In this repo, OpenAPI is generated from the Spring controllers — see
> [`openapi-workflow.md`](../source/openapi-workflow.md).
