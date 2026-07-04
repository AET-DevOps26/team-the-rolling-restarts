# 03 — System Architecture

The system must be a set of **interacting but separated** components: client, server,
database, and a **separate** GenAI component (GenAI details in
[04-genai-component.md](04-genai-component.md)).

## Component responsibilities

- **Client** — provides a usable interface and communicates with the server over
  **REST**.
- **Server** — exposes **REST APIs**, coordinates business logic, and interacts with
  persistent storage.
- **Database** — supports persistent data storage and must have a **documented schema**.
- **GenAI** — runs as an **independent service** and communicates over a **defined
  interface**.

## Server side (hard constraints)

- Implemented in **Spring Boot (Java)**.
- Must consist of **at least three microservices**.
- Services need not be large, but must have **distinct responsibilities** and
  communicate in a **controlled and documented** way. Modular architecture required.

## Client side

- May be **React, Angular, or Vue.js**.
- Must provide a **usable and responsive** interface.
- Interacts with the server over **REST APIs**.

## Database

- May be **MySQL, PostgreSQL, or a similar** relational/persistent DB.
- Must run via **Docker** in local development.
- Must support **documented persistent storage** in the deployed setup.
- **Schema must be documented.**

## Summary table

| Component | Technology | Notes |
|-----------|-----------|-------|
| Client Side | React, Angular, Vue.js | Usable, responsive UI; talks to server over REST |
| Server Side | Spring Boot (Java) | REST APIs; **≥ 3 microservices**; modular architecture required |
| Database | MySQL / PostgreSQL / similar | Persistent storage; documented schema; runs via Docker |
