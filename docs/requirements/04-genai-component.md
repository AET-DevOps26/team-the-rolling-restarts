# 04 — GenAI Component

## Deployment

- Implemented as a **separate service in Python**.
- Deployed as a **modular microservice**, containerised **independently**, networked
  with the server, and integrated through a **defined interface**.

## Functionality (must be real, not decorative)

- Must fulfil a **real user-facing use case**, e.g. summarisation, generation, question
  answering, or a similarly meaningful feature accessible through the application
  workflow.
- It is **not sufficient** to include a GenAI service that exists technically but is not
  connected to an actual user-facing capability.

## Model support (both required where feasible)

- **Cloud-based** models — e.g. the OpenAI API.
- **Local** models — e.g. GPT4All or LLaMA.
- No sophisticated model research is required, but the **service architecture must be
  able to work with both remote and local inference** options where feasible.

## Optional advanced bonus

- A full **retrieval-augmented generation (RAG)** setup using a **vector database**
  such as Weaviate.

## Summary table

| Aspect | Requirement |
|--------|-------------|
| Language | Python |
| Deployment | Modular microservice, containerised and networked with the server |
| Functionality | Real user-facing use case (summarisation, generation, Q&A, …) |
| Model support | Cloud-based (e.g. OpenAI API) **and** local (e.g. GPT4All, LLaMA) |
| Optional bonus | Full RAG architecture using a vector DB such as Weaviate |
