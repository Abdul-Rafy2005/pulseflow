# CLAUDE.md — Agent Instructions for PulseFlow

This file tells any AI coding agent (Claude Code, Cursor, etc.) how to work in
this repo. Read this fully before writing code. The full product spec lives in
`PRD.md` — read that too before starting a new module. This file is about
*how* to build; PRD.md is about *what* to build.

---

## 1. Project Summary

PulseFlow is an event-driven analytics backend: Spring Boot REST API →
RabbitMQ → consumer → PostgreSQL + Redis → WebSocket → React dashboard.
Full details, data model, and API contracts are in `PRD.md`. Do not invent
new endpoints, tables, or event types without checking PRD.md first — if
something's missing there, ask or propose an addition rather than silently
deciding.

---

## 2. Tech Stack (do not substitute without asking)

- Java 21 LTS, Spring Boot 3.x, Maven
- PostgreSQL 16, Redis 7, RabbitMQ 3.x (management plugin) — all via Docker Compose
- React (Vite) + STOMP.js/SockJS + Recharts for the frontend
- Spring Security + JWT for admin auth; API key header for event ingestion
- springdoc-openapi for Swagger

---

## 3. Folder Structure

```
pulseflow/
├── backend/
│   └── src/main/java/com/pulseflow/
│       ├── auth/          # controllers, services, JWT filter, DTOs
│       ├── events/         # event ingestion + read APIs
│       ├── queue/          # RabbitMQ config: exchanges, queues, bindings, DLQ
│       ├── analytics/      # consumer, aggregation, Redis, analytics read APIs
│       ├── dashboard/      # WebSocket config + broadcast service
│       ├── monitoring/     # health, queue/redis status, audit logging
│       ├── config/         # security config, CORS, Swagger, global exception handler
│       └── common/         # shared DTOs, exceptions, utils (only truly shared code)
├── frontend/
│   └── src/
├── docs/
│   ├── architecture.png
│   ├── api.md
│   └── database.png
├── docker-compose.yml
├── PRD.md
├── CLAUDE.md
└── README.md
```

**Rule:** each package in §3 owns its own controllers/services/DTOs. Don't
create a generic `controllers/` or `services/` folder at the top level — this
project is organized by feature/module, not by layer.

---

## 4. Module Build Order

Follow the phase order in `PRD.md` §13. Do not start the WebSocket/dashboard
work before the consumer and analytics read-path are working end-to-end. Do
not build the React dashboard against mocked data for more than a first pass
— wire it to the real API as soon as the analytics endpoints exist.

At the end of each phase, the agent should be able to demonstrate the phase
working (curl commands or a short script) before moving on.

---

## 5. Coding Conventions

- **Style:** standard Java conventions, 4-space indent, no wildcard imports.
- **Layering:** Controller → Service → Repository. Controllers must be thin —
  no business logic, only request/response mapping and validation triggering.
- **DTOs, not entities, at the API boundary.** Never return a JPA entity
  directly from a controller.
- **Validation:** use `jakarta.validation` annotations (`@NotNull`,
  `@Pattern`, etc.) on request DTOs. Reject invalid `eventType` values with a
  400, not a silent fallback.
- **Exceptions:** use a `@ControllerAdvice` global exception handler (in
  `config/`). Don't scatter try/catch-and-return-500 logic across controllers.
- **Enums:** `EventType` is a Java enum, not a free-text string, anywhere in
  application code. Only at the DB layer is it stored as VARCHAR.
- **Immutability:** prefer records for DTOs where possible (Java 21 supports
  this well).
- **Logging:** use SLF4J, structured where possible. Every log line touching
  a specific event should include its event ID. Never log secrets, tokens, or
  passwords.
- **Config:** all environment-specific values (DB URL, Redis host, RabbitMQ
  host, JWT secret, API keys) go in `application.yml` + environment variables
  — never hardcoded, never committed as real secrets. Use `.env.example` to
  document required vars without real values.

---

## 6. RabbitMQ / Async Rules

- `POST /events` must **never** write directly to Postgres or Redis. It
  validates and publishes only. If you find yourself adding a repository call
  in the events controller/service for the write path, stop — that logic
  belongs in the consumer.
- The consumer must be idempotent — assume messages can be redelivered.
- On processing failure: retry with backoff up to the configured max, then
  route to DLQ and write a `failed_events` row. Never let an exception in the
  consumer silently drop a message.
- Any new queue/exchange/routing key must be defined in `queue/` config
  classes, not inline in service code.

---

## 7. Redis Rules

- Redis is a cache, not a source of truth. Every value in Redis must be
  derivable from Postgres. If you're about to store something in Redis that
  has no Postgres equivalent, reconsider the design or add the equivalent
  Postgres table.
- Key naming: always namespaced (`stats:...`, `queue:...`) per PRD.md §7. No
  bare keys.
- Set explicit TTLs on all cache keys — nothing lives in Redis forever.

---

## 8. Testing Expectations

- Every service class gets unit tests (JUnit 5 + Mockito) covering the happy
  path and at least one failure path.
- The event ingest → queue → consume → persist flow needs at least one
  integration test. Use Testcontainers for Postgres/RabbitMQ/Redis rather
  than mocking them at the integration level.
- Don't skip tests for a module and promise to "add them later" — write them
  as part of finishing that phase.

---

## 9. Definition of Done (per module)

A module is NOT done until:
1. Code compiles and the relevant Docker Compose services run cleanly.
2. Swagger reflects the new endpoints with correct request/response schemas.
3. Unit tests exist and pass.
4. Manual test (curl/script) demonstrates the flow described in PRD.md for
   that module.
5. No secrets, API keys, or credentials committed to git.
6. README or `docs/api.md` updated if new endpoints were added.

---

## 10. Things the Agent Should NOT Do

- Don't add new third-party dependencies without checking if something in the
  existing stack already covers the need.
- Don't change the module boundaries in §3 without discussing it first.
- Don't build the "v2" ideas mentioned in PRD.md §8/§14 (multiple exchanges,
  multi-tenancy, alerting, etc.) — they're explicitly out of scope for v1.
- Don't add authentication complexity beyond JWT (admin) + API key (client
  apps) — no OAuth, no multi-role hierarchy, unless PRD.md is updated first.
- Don't silently change the database schema in PRD.md §6 — if a column needs
  to change, update PRD.md in the same change.
- Don't commit `.env` files with real secrets — only `.env.example`.

---

## 11. Commands Reference

```bash
# Start infra (Postgres, Redis, RabbitMQ)
docker compose up -d postgres redis rabbitmq

# Start full stack (once backend/frontend are containerized)
docker compose up -d

# Backend (dev, outside Docker)
cd backend && ./mvnw spring-boot:run

# Backend tests
cd backend && ./mvnw test

# Frontend
cd frontend && npm install && npm run dev

# RabbitMQ management UI
http://localhost:15672  (default guest/guest — change in non-local envs)
```

---

## 12. When In Doubt

If a requirement is ambiguous or missing from `PRD.md`, the agent should:
1. Make the most reasonable assumption consistent with the existing
   architecture and state it clearly in the PR/commit description, **or**
2. Ask a single clarifying question if the ambiguity would send the
   implementation in a fundamentally different direction (e.g., data model
   changes, new external dependency).

Do not silently over-engineer beyond what PRD.md v1 asks for — ship the
simplest version described in §13's phases first; polish comes last.
