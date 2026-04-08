---
title: "Agent Gateway — Dependencies"
category: services
tags: [agent-gateway, dependencies, grpc, redis, kafka]
owner: "@backend"
last_updated: "2026-04-02"
source: repo
---

# Agent Gateway — Dependencies

## Upstream Service Dependencies

| Service | Protocol | Config Env Vars | Default |
|---------|----------|----------------|---------|
| User Service | gRPC | `USER_SERVICE_HOST`, `USER_SERVICE_PORT` | `user_service:50052` |
| Communication Service | gRPC | `COMMUNICATION_SERVICE_HOST`, `COMMUNICATION_SERVICE_PORT` | `admin_service:50055` |
| Agent Service | gRPC + HTTP | `AGENT_SERVICE_HOST`, `AGENT_SERVICE_PORT`, `AGENT_SERVICE_URL` | `:50057` |
| AI Gateway | HTTP | `AI_GATEWAY_BASE_URL` | `http://localhost:5511/v1` |
| File Conversion Service | HTTP | `FILE_CONVERSION_SERVICE_URL` | — |
| Main API Gateway | HTTP | `API_GATEWAY_URL` | — |
| Telegram API Service | HTTP | `TELEGRAM_API_BASE_URL` | — |

### What Each Service Provides

- **User Service** — User validation, profile data, org membership checks
- **Communication Service** — Conversation and message CRUD (MongoDB-backed). The gateway's sole data persistence layer.
- **Agent Service** — Agent configuration, validation, and metadata
- **AI Gateway** — Chat suggestions, instruction generation, prompt improvement (OpenRouter-backed)
- **File Conversion Service** — Markdown to PDF/DOCX conversion
- **Main API Gateway** — Cross-service calls for SDR platform auth
- **Telegram API Service** — Outbound Telegram message delivery

---

## Infrastructure Dependencies

| Component | Purpose | Config Env Vars |
|-----------|---------|----------------|
| Redis | Streams, sessions, bot configs, cache | `REDIS_HOST`, `REDIS_PORT`, `REDIS_URI` |
| Kafka | Event topics (secondary to Redis Streams) | `KAFKA_BOOTSTRAP_SERVERS` |
| SigNoz | OpenTelemetry traces and metrics | `OTEL_EXPORTER_OTLP_ENDPOINT`, `OTEL_SERVICE_NAME` |

---

## Key Environment Variables

| Variable | Purpose |
|----------|---------|
| `ENV` | Environment name (dev, qa, main) — used in Redis stream keys |
| `JWT_SECRET_KEY` | Secret for JWT token validation |
| `REDIS_HOST` | Redis hostname |
| `REDIS_PORT` | Redis port |
| `REDIS_URI` | Redis connection URI (alternative to host/port) |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka broker addresses |
| `CORS_ORIGINS` | Allowed CORS origins |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | OpenTelemetry collector endpoint |
| `OTEL_SERVICE_NAME` | Service name for traces |
| `USER_SERVICE_HOST` | User service gRPC host |
| `USER_SERVICE_PORT` | User service gRPC port |
| `COMMUNICATION_SERVICE_HOST` | Communication service gRPC host |
| `COMMUNICATION_SERVICE_PORT` | Communication service gRPC port |
| `AGENT_SERVICE_HOST` | Agent service gRPC host |
| `AGENT_SERVICE_PORT` | Agent service gRPC port |
| `AGENT_SERVICE_URL` | Agent service HTTP URL |
| `AI_GATEWAY_BASE_URL` | AI Gateway HTTP URL |
| `FILE_CONVERSION_SERVICE_URL` | File conversion service URL |
| `API_GATEWAY_URL` | Main API gateway URL |
| `TELEGRAM_API_BASE_URL` | Telegram API service URL |

---

## Failure Impact Analysis

| Dependency | Severity | Impact |
|------------|----------|--------|
| Redis | **Critical** | No chat streaming (streams), no authentication (sessions), no bot management |
| Communication Service | **Critical** | No conversation or message persistence — all CRUD fails |
| AI Gateway | Degraded | AI services unavailable (suggestions, instructions), but core chat still works |
| User Service | Degraded | No user validation — requests may proceed with partial data |
| Agent Service | Degraded | No agent validation — chat may fail for unresolved agents |
| Kafka | Degraded | Secondary event bus down, core chat unaffected (Redis Streams primary) |
| File Conversion | Degraded | PDF/DOCX export unavailable |
| SigNoz | Degraded | No observability, no functional impact |
| Telegram API | Degraded | Outbound Telegram messages fail, inbound webhooks still received |

### Graceful Degradation

The gateway is designed so that non-critical dependencies degrade gracefully:
- If AI Gateway is down, chat works but `/ai-services/*` endpoints return 503
- If Kafka is down, Redis Streams still handles the chat flow
- If SigNoz is down, the application logs locally but loses distributed tracing

---

## gRPC Proto Dependencies

Protos are compiled at build time. The gateway depends on proto definitions from:
- `communication-service` — conversation and message RPCs
- `user-service` — user validation RPCs
- `agent-service` — agent metadata RPCs

Proto compilation is part of the Poetry build step and CI pipeline.

## See Also

- [architecture/service-map.md](../../architecture/service-map.md) — Full dependency graph
- [services/agent-gateway/overview.md](overview.md) — Service overview
- [infra/environments.md](../../infra/environments.md) — Environment configuration
- [services/communication-service/overview.md](../communication-service/overview.md) — Primary data store
