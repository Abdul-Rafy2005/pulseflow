# PulseFlow — Product Requirements Document (PRD)

Version 1.0 · Owner: [Abdul Rafy] · Status: Draft for build

---

## 1. Vision

PulseFlow is a backend analytics platform that ingests user-activity events from
client applications, processes them asynchronously via RabbitMQ, persists them in
PostgreSQL, caches hot statistics in Redis, and exposes both a REST API and a
live-updating React dashboard for analytics consumption.

**One-line pitch:** *Applications send us events, and we turn them into real-time
analytics.*

**Why it exists:** Most portfolio backend projects are CRUD-with-a-database. PulseFlow
demonstrates event-driven architecture, async processing, caching strategy, and
real-time delivery — the skills that separate "can build an API" from "understands
backend systems."

---

## 2. Users / Personas

### 2.1 Admin (human)
- Logs in via JWT-based auth
- Views analytics dashboards (summary, daily, top events, top users, realtime)
- Views raw event list and individual event detail
- Monitors queue health and Redis health
- Views system statistics and audit logs

### 2.2 Client Application (machine, not human)
- Any external app (e.g. an e-commerce site, a video platform) that sends event
  payloads to PulseFlow via `POST /events`
- Does not authenticate the same way admins do (see §9.2 for API-key vs JWT decision)
- Fire-and-forget: expects a fast `202 Accepted`, not a slow synchronous response

---

## 3. Supported Event Types

```
LOGIN, LOGOUT, REGISTER, SEARCH, PAGE_VIEW, BUTTON_CLICK,
PURCHASE, VIDEO_PLAY, LIKE, COMMENT, SHARE, DOWNLOAD
```

Stored as a Postgres `ENUM` or a validated `VARCHAR` with an application-level
`EventType` Java enum (enum is preferred — see AGENTS file for rationale). New
event types must be addable without a schema migration for the *metadata* they
carry (hence the JSON metadata column, §6.2).

---

## 4. High-Level Architecture

```
                Client App
                     │
                     ▼  POST /events
          Spring Boot REST API  ──── validates payload, returns 202 immediately
                     │
                     ▼
           RabbitMQ Producer  ──── publishes EventMessage to exchange
                     │
                     ▼
              RabbitMQ Queue (events.queue)
                     │
                     ▼
          Analytics Consumer  ──── @RabbitListener, idempotent, retries on failure
          ┌──────────┴──────────┐
          ▼                     ▼
     PostgreSQL             Redis
   (durable record)     (hot counters/cache)
          │                     │
          └──────────┬──────────┘
                     ▼
          Analytics REST API  ──── reads from Redis first, falls back to Postgres
                     │
                     ▼
         WebSocket Broadcaster ──── pushes live updates on new events
                     │
                     ▼
             React Dashboard (subscribes via STOMP/WebSocket + polls REST for history)
```

**Key architectural decisions:**
- Ingestion (`POST /events`) is decoupled from processing. The API never touches
  Postgres/Redis synchronously — it only validates and publishes to RabbitMQ.
- The consumer is the only writer to Postgres/Redis for event data. This keeps
  write logic in one place and makes retry/failure handling centralized.
- Redis is a cache/accelerator, never the source of truth. Postgres can always
  rebuild what's in Redis.
- WebSocket push is additive — the dashboard must still function (via polling)
  if the WebSocket connection drops.

---

## 5. Modules

Each module is a Spring Boot package with a single responsibility. See AGENTS.md
for the physical folder layout this maps to.

| Module | Responsibility |
|---|---|
| `auth` | Register/login, JWT issuance, password hashing, role checks |
| `events` | Receive events, validate, publish to queue, expose event read APIs |
| `queue` | RabbitMQ config: exchange, queue, bindings, DLQ, retry policy |
| `analytics` | Consumer logic, aggregation, Redis read/write, analytics read APIs |
| `dashboard` | WebSocket config + broadcast service (backend half of the dashboard) |
| `monitoring` | Health checks, queue status, Redis status, audit logging |
| `config` | Cross-cutting Spring config (security, CORS, Swagger, JSON, exception mapping) |

---

## 6. Data Model

### 6.1 `users`
| Column | Type | Notes |
|---|---|---|
| id | BIGSERIAL PK | |
| username | VARCHAR(50) UNIQUE NOT NULL | |
| email | VARCHAR(255) UNIQUE NOT NULL | |
| password | VARCHAR(255) NOT NULL | BCrypt hash, never plaintext |
| role | VARCHAR(20) NOT NULL | `ADMIN` (v1 has no other role, but keep the column) |
| created_at | TIMESTAMP NOT NULL DEFAULT now() | |

### 6.2 `events` (the biggest, most important table)
| Column | Type | Notes |
|---|---|---|
| id | BIGSERIAL PK | |
| event_type | VARCHAR(30) NOT NULL | one of §3, validated at API layer |
| user_id | BIGINT NULL | the *client app's* user id — not a FK to our `users` table |
| source | VARCHAR(100) NULL | which client app sent it, e.g. `"netflix-clone"` |
| metadata | JSONB NULL | free-form: `{"browser":"Chrome","country":"PK","device":"Desktop"}` |
| received_at | TIMESTAMP NOT NULL DEFAULT now() | when the API accepted it |
| processed_at | TIMESTAMP NULL | when the consumer finished processing |
| status | VARCHAR(20) NOT NULL DEFAULT 'PENDING' | `PENDING` \| `PROCESSED` \| `FAILED` |

Indexes: `event_type`, `received_at`, `status`, GIN index on `metadata` for
future filtering.

### 6.3 `failed_events`
| Column | Type | Notes |
|---|---|---|
| id | BIGSERIAL PK | |
| event_id | BIGINT NOT NULL REFERENCES events(id) | |
| reason | TEXT NOT NULL | exception message / stack summary |
| retry_count | INT NOT NULL DEFAULT 0 | |
| created_at | TIMESTAMP NOT NULL DEFAULT now() | |

### 6.4 `event_statistics` (optional pre-aggregation, v1.1)
| Column | Type | Notes |
|---|---|---|
| id | BIGSERIAL PK | |
| stat_date | DATE NOT NULL UNIQUE | |
| total_events | BIGINT NOT NULL DEFAULT 0 | |
| total_logins | BIGINT NOT NULL DEFAULT 0 | |
| total_purchases | BIGINT NOT NULL DEFAULT 0 | |

### 6.5 `audit_logs`
| Column | Type | Notes |
|---|---|---|
| id | BIGSERIAL PK | |
| admin_id | BIGINT NOT NULL REFERENCES users(id) | |
| action | VARCHAR(100) NOT NULL | e.g. `ADMIN_LOGIN`, `EVENT_DELETED`, `SETTINGS_CHANGED` |
| details | TEXT NULL | |
| created_at | TIMESTAMP NOT NULL DEFAULT now() | |

---

## 7. Redis Design

Redis is **not** a database — it's a fast, disposable accelerator. Every key must
be reconstructible from Postgres.

| Key pattern | Type | TTL | Purpose |
|---|---|---|---|
| `stats:today:events` | STRING (counter) | until midnight | total events today |
| `stats:today:active_users` | SET / HLL | until midnight | unique users seen today |
| `stats:today:top_searches` | SORTED SET | 24h | search term → count |
| `stats:events_per_minute` | STRING (counter) | 60s rolling | processing rate |
| `stats:top_countries` | SORTED SET | 24h | country → count |
| `stats:most_viewed_page` | SORTED SET | 24h | page → view count |
| `queue:size:cache` | STRING | 5s | last known queue depth (avoid hammering RabbitMQ mgmt API) |

Redis keys are namespaced with `stats:` / `queue:` prefixes to keep it organized
as more caches get added.

---

## 8. RabbitMQ Design

**v1 (keep simple):**
```
Exchange: pulseflow.events.exchange (type: topic)
   └─ Routing key: event.created
        └─ Queue: events.queue
              └─ Consumer: AnalyticsConsumer
```

**Failure handling:**
- On consumer exception → message is nacked and retried up to `N` times
  (configurable, default 3) with exponential backoff.
- After max retries → message routed to `events.dlq` (dead-letter queue) and a
  row is written to `failed_events`.
- DLQ is inspectable via `GET /queue/status` and (optionally, v1.1) a manual
  replay endpoint.

**v2 (future, do not build yet):** separate exchanges per event category, fanout
for multi-consumer scenarios (e.g. a fraud-detection consumer alongside
analytics).

---

## 9. API Specification

All endpoints return JSON. Errors follow a consistent shape:
```json
{ "timestamp": "...", "status": 400, "error": "Bad Request", "message": "...", "path": "/events" }
```

### 9.1 Authentication
| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/auth/register` | none | Create admin account |
| POST | `/auth/login` | none | Returns JWT |
| GET | `/auth/profile` | JWT | Current user info |

### 9.2 Events
| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/events` | API key (client apps) | Accepts event, returns `202 Accepted` immediately, publishes to queue |
| GET | `/events` | JWT (admin) | Paginated, filterable (`eventType`, `status`, `dateFrom`, `dateTo`), sortable |
| GET | `/events/{id}` | JWT (admin) | Single event detail |

> **Decision needed before coding:** client apps authenticate with a simple
> `X-API-Key` header (simplest, common in ingestion APIs), while admins use JWT
> bearer tokens for everything else. Document the chosen key(s) in `.env`, never
> commit them.

**Example request body — `POST /events`:**
```json
{
  "eventType": "VIDEO_PLAY",
  "userId": 23,
  "source": "netflix-clone",
  "metadata": { "page": "/movies/interstellar", "device": "Desktop" }
}
```

### 9.3 Analytics
| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/analytics/summary` | JWT | Today's totals (Redis-first) |
| GET | `/analytics/daily` | JWT | Time-series, last N days (Postgres) |
| GET | `/analytics/top-events` | JWT | Ranked event types |
| GET | `/analytics/top-users` | JWT | Ranked by activity |
| GET | `/analytics/realtime` | JWT | Snapshot for dashboard init (before WebSocket takes over) |

### 9.4 System
| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/health` | none | Liveness/readiness |
| GET | `/queue/status` | JWT | Queue depth, consumer count, DLQ size |
| GET | `/redis/status` | JWT | Connection status, key count, memory usage |

### 9.5 WebSocket
| Endpoint | Protocol | Description |
|---|---|---|
| `/ws` | STOMP over SockJS | Client subscribes to `/topic/events` for live event stream and `/topic/stats` for live counters |

---

## 10. Dashboard (Frontend Requirements)

**Layout — live-updating cards + a stream:**
- Today's Events (counter, animates on change)
- Active Users (counter)
- Queue Size (counter, color shifts if backlog grows)
- Processing Rate (events/sec)
- Top Event (label)
- Recent Events (scrolling live list, newest on top, capped at ~50 rows)

**Behavior:**
- On load: `GET /analytics/realtime` for initial snapshot.
- Then: subscribe to WebSocket topics; every processed event pushes an update.
- If WebSocket disconnects: fall back to polling `/analytics/summary` every 5s
  and show a small "reconnecting" indicator — dashboard must never go blank.
- Historical charts (daily trend, top events bar chart) load from
  `/analytics/daily` and `/analytics/top-events` — these are not real-time,
  refresh on demand or every 60s.

---

## 11. Non-Functional Requirements

| Category | Requirement |
|---|---|
| **Security** | JWT auth for admin routes, API key for ingestion, BCrypt password hashing, no secrets in source control, input validation on every endpoint |
| **Performance** | `POST /events` must respond in <100ms (it only validates + publishes, no DB write) |
| **Scalability** | Consumer should be safely scalable to multiple instances (idempotent processing, no in-memory-only state) |
| **Resilience** | Failed messages must never be silently dropped — always land in DLQ + `failed_events` |
| **Observability** | Structured logging (JSON logs in prod), correlation/event ID in every log line related to that event |
| **API docs** | Swagger/OpenAPI auto-generated and served at `/swagger-ui.html` |
| **Testing** | Unit tests for service layer, integration test for the full ingest → queue → consume → persist path (Testcontainers recommended) |

---

## 12. Tech Stack

| Layer | Choice |
|---|---|
| Language | Java 21 LTS |
| Framework | Spring Boot 3.x (Web, Validation, Data JPA, AMQP, Security, WebSocket) |
| Build | Maven |
| Database | PostgreSQL 16 (Docker) |
| Cache | Redis 7 (Docker) |
| Message Broker | RabbitMQ 3.x with management plugin (Docker) |
| Frontend | React (Vite), STOMP.js / SockJS client, a charting lib (Recharts) |
| Auth | Spring Security + JJWT (or Nimbus) |
| Docs | springdoc-openapi (Swagger UI) |
| Containerization | Docker Compose (postgres, redis, rabbitmq, backend, frontend) |

---

## 13. Build Phases (recommended order)

1. **Foundation** — Spring Boot project skeleton, Docker Compose (Postgres,
   Redis, RabbitMQ), health check endpoint, Swagger wired up.
2. **Auth module** — register/login/profile, JWT filter, role guard.
3. **Events module (write path only)** — `POST /events` → validates → publishes
   to RabbitMQ. No consumer yet; confirm messages land in the queue via the
   RabbitMQ management UI.
4. **Analytics module (consumer + read path)** — consumer writes to Postgres,
   updates Redis counters, `GET /events`, `GET /analytics/*` endpoints working
   against real data.
5. **Failure handling** — DLQ, `failed_events` table, retry policy.
6. **Monitoring module** — `/queue/status`, `/redis/status`, audit logging.
7. **WebSocket + dashboard backend** — broadcast service pushes on each
   processed event.
8. **React dashboard** — static cards wired to REST first, then WebSocket
   live updates layered in.
9. **Polish** — pagination, filtering, rate limiting, tests, README with
   architecture diagram, deployment.

Each phase should be independently demoable — don't move to the next phase
until the current one works end-to-end.

---

## 14. Out of Scope (v1)

- Multi-tenant support (multiple client orgs with isolated data)
- Fraud/anomaly detection consumers
- Configurable alerting (email/Slack on queue backlog)
- Horizontal auto-scaling infra (this is a portfolio project, not a SaaS)
- Non-admin user roles

---

## 15. Success Criteria

- All endpoints in §9 implemented and documented in Swagger.
- Dashboard updates in real time when events are POSTed (demoable with a
  simple script that fires 50 events in a loop).
- A failed event (simulate by throwing in the consumer) correctly lands in the
  DLQ and `failed_events` table, not lost.
- `docker-compose up` brings up the entire stack with zero manual steps.
- README explains architecture with the diagram from §4.
