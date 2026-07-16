# Current System Structure

[Initial System Structure](INITIAL_SYSTEM_STRUCTURE.md) captures the original design. This page
is its companion: the same three mandatory UML diagram types (the engineering-artefacts
requirement requires them to "reflect the actual implementation"), redrawn against the system as
it's actually built today, verified directly against the code rather than transcribed from the
original plan. The initial diagrams are kept as-is — they document the original design intent,
which is worth preserving — this page documents the delta and the current state alongside them.

## Current Object Model

![Current object model](./diagrams/CurrentObjectModel.png)

The five collections that actually exist (`User`, embedded `UserSettings`, `Topic`, `Source`,
`Article` — see [Database Schema](database-schema.md) for the full field/index reference) plus
the relationships between them as implemented. GenAI outputs (summary/explanation/sentiment/Q&A)
are computed on request and never persisted, unlike the initial model's `Summary`/`Explanation`/
`SentimentAnalysis` entities. `Tag`, `Publisher`, `Folder`, `Bookmark` (as its own entity),
`Interaction`, `Recommendation`, `Notification`, and `TrendingTopic` from the initial model don't
exist in the current build. Source: [current-object-model.puml](./diagrams/current-object-model.puml).

## Current Use Cases

![Current use case diagram](./diagrams/CurrentUseCase.png)

Covers what's actually reachable end-to-end today: register/login, browse the feed, view an
article with all four real GenAI extensions (summary, explanation, sentiment — a categorical
`left`/`center`/`right`/`unclear` bias label, not a numeric score — and the Q&A endpoint, which
wasn't in the initial use case set at all), search & filter, save/view bookmarked articles (a flat
list, no folders), and manage topic/source preferences. Recommendations, trending topics (only a
client-side re-sort of already-fetched articles, not a real ranking), notifications (a frontend-only
mock, explicitly disclaimed in the UI as not yet wired to a backend), and automatic article
tagging (the `topicId` field exists and is filterable, but nothing ever sets it during ingestion)
were all in the initial use case set and are not implemented. Source:
[current-use-case.puml](./diagrams/current-use-case.puml).

## Current Component Diagram

![Current component diagram](./diagrams/CurrentArchitectureComponentDiagram.png)

The initial diagram planned a single monolithic "API Service"; the actual system is three
separate Spring Boot services (API Gateway, User Service as the OAuth2 Authorization Server,
Content Service) plus the GenAI service, each independently deployed, talking to two MongoDB
databases (not PostgreSQL) rather than one. The service-to-service subscribe/unsubscribe call
between User Service and Content Service uses a dedicated `client_credentials` machine token
(scope `source.write`) instead of forwarding the end user's JWT. The bundled `grafana-lgtm`
observability stack (metrics, traces, logs from all four services via OpenTelemetry) is new
relative to the initial plan; the initial plan's notification provider was never built. Source:
[current-architecture-component-diagram.puml](./diagrams/current-architecture-component-diagram.puml).
