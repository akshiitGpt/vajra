---
title: "agent-gateway Repo"
category: repos
tags: [repo, agent-gateway, python, fastapi, grpc, kafka, sse]
owner: "@backend"
last_updated: "2026-04-02"
source: repo
repo_url: "https://github.com/ruh-ai/agent-gateway"
---

# agent-gateway

GitHub: `github.com/ruh-ai/agent-gateway`

Python 3.13 API Gateway built with FastAPI + Uvicorn. Routes HTTP requests to backend microservices via gRPC (communication-service, user-service, agent-service). Handles real-time chat streaming via Kafka + SSE. Publishes events to Kafka.

## Directory Structure

```
agent-gateway/
├── app/
│   ├── main.py                          # FastAPI app, middleware, startup/shutdown
│   ├── api/routers/
│   │   ├── __init__.py                  # API key validation helper
│   │   ├── agent_routes.py              # /agents/* (chat/stream, stop, pause, resume)
│   │   ├── communication_routes.py      # /communication/* (conversations, messages)
│   │   ├── ai_services_routes.py        # /ai-services/* (suggestions, instructions)
│   │   ├── webhook_routes.py            # /webhook/* (telegram, send-platform-message)
│   │   ├── bot_routes.py               # /bots/* (telegram/slack bot lifecycle)
│   │   └── file_conversion_routes.py    # /file-conversion/* (convert, upload)
│   ├── core/
│   │   ├── config.py                    # Pydantic Settings
│   │   ├── constants.py                 # RedisStreamEnum, SSEEventType
│   │   ├── auth_guard.py               # JWT + Redis session validation
│   │   ├── security.py                 # Token creation, API key validation
│   │   ├── logging.py                  # structlog setup
│   │   └── telemetry.py               # OpenTelemetry/SigNoz
│   ├── services/
│   │   ├── chat_stream_service.py       # Core SSE streaming (Kafka consumer)
│   │   ├── kafka_response_consumer.py   # Background Kafka consumer for agent_chat_responses
│   │   ├── communication_service.py     # gRPC client for communication-service
│   │   ├── ai_services_service.py       # HTTP client for AI Gateway
│   │   ├── webhook_service.py           # Telegram/SMS webhook processing
│   │   ├── bot_service.py              # Telegram/Slack bot lifecycle (Redis-backed)
│   │   ├── background_tasks.py          # Async message persistence, title generation
│   │   ├── file_conversion_service.py   # HTTP client for file-conversion service
│   │   ├── user_service.py             # gRPC client for user-service
│   │   └── agent_grpc_service.py       # gRPC client for agent-service
│   ├── schemas/                         # Pydantic request/response models
│   ├── platforms/                       # Platform provider abstraction (Telegram, SMS)
│   ├── utils/redis/                     # Redis sync + async clients, cache
│   ├── scripts/generate_grpc.py         # Clone proto repo + compile
│   └── grpc_/                          # Generated protobuf code
├── tests/                               # Unit, integration, contract, security tests
├── smoke/                               # Post-deploy smoke tests
├── helm/                                # Helm chart values (dev, qa, main)
├── Dockerfile
├── docker-compose.test.yml
├── pyproject.toml
├── Makefile
└── .github/workflows/                   # CI, build-deploy, post-deploy, regression
```

## Entry Point

`app/main.py` — FastAPI application on port 8001. Swagger docs available at `/docs`. Registers all routers, configures CORS/auth middleware, and runs startup/shutdown lifecycle hooks for gRPC channels, Redis connections, and Kafka producers.

## Local Development

```bash
# Prerequisites: Python 3.13+, Poetry, Docker (for Redis)
git clone https://github.com/ruh-ai/agent-gateway
cd agent-gateway
poetry install --no-root
cp .env.example .env                   # Fill in service URLs, JWT secret, Redis, Kafka
poetry run python -m app.scripts.generate_grpc  # requires REPO_URL + GIT_TOKEN
poetry run uvicorn app.main:app --host 0.0.0.0 --port 8001 --reload
```

## Key Files to Read

| File | Purpose |
|------|---------|
| `app/main.py` | FastAPI app bootstrap, middleware, lifecycle |
| `app/api/routers/agent_routes.py` | Chat streaming and agent control endpoints |
| `app/services/chat_stream_service.py` | Core SSE streaming via Kafka |
| `app/services/kafka_response_consumer.py` | Background Kafka consumer for chat responses |
| `app/services/communication_service.py` | gRPC client for communication-service |
| `app/core/config.py` | All configuration / env vars |
| `app/core/auth_guard.py` | JWT + session authentication |
| `app/core/constants.py` | Enums: RedisStreamEnum, SSEEventType |
| `app/services/bot_service.py` | Telegram/Slack bot lifecycle management |

## Testing

```bash
make test-unit                         # Unit tests
make test-integration                  # Integration tests
make lint                              # Linting
make typecheck                         # Type checking
```

## See Also

- [services/agent-gateway/overview.md](../services/agent-gateway/overview.md) — Service-level docs
- [repos/agent-platform-v2.md](agent-platform-v2.md) — Agent execution engine (downstream)
- [repos/communication-service.md](communication-service.md) — gRPC messaging backend
- [repos/ai-gateway.md](ai-gateway.md) — LLM/AI proxy (downstream)
- [repos/file-conversion.md](file-conversion.md) — File conversion service (downstream)
