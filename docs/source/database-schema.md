# Database Schema

MongoDB is document-oriented and schemaless at the database level, but every collection has a
concrete, enforced-in-code shape via Spring Data MongoDB `@Document` entities. This page is that
schema, kept in sync with `services/spring/{user,content}-service/.../model/`. See also the
[Analysis Object Model](INITIAL_SYSTEM_STRUCTURE.md#31-analysis-object-model) diagram — a
conceptual, analysis-level model of the original problem domain (broader than what's implemented
so far) — and the [Current Object Model](CURRENT_SYSTEM_STRUCTURE.md#current-object-model), a UML
class diagram of exactly the collections documented below. This page is the field-level detail
underneath that current-state diagram.

## Databases

Per the project's `CLAUDE.md`, `user-service` and `content-service` each own a separate MongoDB
logical database on the shared `mongo:8` instance (`infra/docker-compose.yaml`) — no service
reads another service's collections directly. Cross-service references are plain string
ids resolved over REST, not native Mongo references (no `@DBRef` is used anywhere in either
service).

## `user-service` database

### `users` collection

Backed by `User.java` (`rolling_restarts.user.model`).

| Field | Type | Index | Notes |
| --- | --- | --- | --- |
| `_id` | `String` | primary key (`@Id`) | Mongo `ObjectId` string |
| `username` | `String` | unique (`@Indexed(unique = true)`) | |
| `email` | `String` | unique (`@Indexed(unique = true)`) | |
| `passwordHash` | `String` | — | Hashed, never plaintext |
| `name` | `String` | — | Display name |
| `avatarInitials` | `String` | — | Derived initials for the avatar UI |
| `createdAt` | `Instant` | — | Defaults to creation time |
| `settings` | `UserSettings` (embedded) | — | See below |

**Embedded: `UserSettings`** (not a separate collection — nested inline in `users`)

| Field | Type | Notes |
| --- | --- | --- |
| `selectedTopicIds` | `List<String>` | References `content-service`'s `topics._id` |
| `enabledSourceIds` | `List<String>` | References `content-service`'s `sources._id` |
| `savedArticleIds` | `List<String>` | References `content-service`'s `articles._id` |

**Repository:** `UserRepository` — derived queries only, no `@Query` annotations:
`findByUsername`, `findByEmail`, `existsByUsername`, `existsByEmail` (all leverage the unique
indexes above).

> **Known gap:** unlike `content-service` (below), `user-service`'s
> `application.properties` does not set `spring.data.mongodb.auto-index-creation`, and nothing
> else creates these indexes manually (no init script, no `MongoTemplate` index setup). Spring
> Data MongoDB's auto-index-creation defaults to `false`, so on a fresh deployment the
> `username`/`email` uniqueness in the table above is enforced by the registration flow's
> `existsByUsername`/`existsByEmail` checks at the application level, not necessarily by a real
> unique index at the database level. Confirmed by reading `application.properties` directly —
> not yet fixed in code.

## `content-service` database

### `topics` collection

Backed by `Topic.java` (`rolling_restarts.content.model`).

| Field | Type | Index | Notes |
| --- | --- | --- | --- |
| `_id` | `String` | primary key (`@Id`) | |
| `name` | `String` | — | |
| `color` | `String` | — | UI accent color |

**Repository:** `TopicRepository` — no custom query methods.

### `sources` collection

Backed by `Source.java` (`rolling_restarts.content.model`).

| Field | Type | Index | Notes |
| --- | --- | --- | --- |
| `_id` | `String` | primary key (`@Id`) | **Deterministic**, not a random `ObjectId` — a UUIDv3 derived from the normalized RSS URL (`Source.idForRssUrl()`), so re-adding a previously-deleted feed reuses the same id and keeps its old articles associated |
| `name` | `String` | — | |
| `rssUrl` | `String` | unique (`@Indexed(unique = true)`) | |
| `initials` | `String` | — | |
| `active` | `boolean` | — | Default `true` |
| `subscriberCount` | `int` | — | Default `0`; source is auto-removed when this hits `0` |
| `lastFetchedAt` | `Instant` | — | |
| `fetchStatus` | `FetchStatus` (embedded enum) | — | `PENDING` \| `SUCCESS` \| `FAILED`, default `PENDING` |
| `fetchError` | `String` | — | Short error message when `fetchStatus = FAILED` |
| `createdAt` | `Instant` | — | Defaults to creation time |

**Repository:** `SourceRepository` — `findByRssUrl`, `findByActiveTrue`, `existsByRssUrl` (all
derived, no `@Query`).

### `articles` collection

Backed by `Article.java` (`rolling_restarts.content.model`).

| Field | Type | Index | Notes |
| --- | --- | --- | --- |
| `_id` | `String` | primary key (`@Id`) | |
| `headline` | `String` | text (`@TextIndexed`) | Part of the combined text index with `snippet` |
| `snippet` | `String` | text (`@TextIndexed`) | |
| `imageUrl` | `String` | — | |
| `body` | `List<String>` | — | Article body paragraphs |
| `sourceId` | `String` | `@Indexed` | References `sources._id` |
| `topicId` | `String` | `@Indexed` | References `topics._id` |
| `author` | `String` | — | |
| `publishedAt` | `Instant` | — | |
| `readingMinutes` | `int` | — | |
| `fetchedAt` | `Instant` | — | Defaults to fetch time |
| `externalUrl` | `String` | unique (`@Indexed(unique = true)`) | Dedupe key — prevents re-ingesting the same article twice |

**Repository:** `ArticleRepository` — `findByIdIn`, `existsByExternalUrl`, `findByExternalUrlIn`
(all derived, no `@Query`).

**Text search:** `ArticleService` (`rolling_restarts.content.service`) issues the `?q=` search
directly via `MongoTemplate`/`TextQuery`/`TextCriteria` (not a repository method), matching
against the combined `headline`+`snippet` text index above, further filtered by `sourceId`/
`topicId` and paginated.

`content-service`'s `application.properties` explicitly sets
`spring.data.mongodb.auto-index-creation=true` so this text index (and the other `@Indexed`
fields on this page) actually get created on startup — Spring Data MongoDB disables index
auto-creation by default, and without this the text search fails outright with
`MongoCommandException: text index required for $text query (IndexNotFound)`. See
[docs/internal/07-gotchas.md](../internal/07-gotchas.md) for the incident this was found from.

## Cross-collection relationship summary

```text
users.settings.selectedTopicIds  ──▶ topics._id            (content-service)
users.settings.enabledSourceIds  ──▶ sources._id           (content-service)
users.settings.savedArticleIds   ──▶ articles._id          (content-service)
articles.sourceId                ──▶ sources._id           (content-service, same DB)
articles.topicId                 ──▶ topics._id            (content-service, same DB)
```

None of these are enforced at the database level (no `@DBRef`, no foreign keys — MongoDB doesn't
support cross-document referential integrity natively); they're plain string ids resolved by the
owning service's REST API at read time.
