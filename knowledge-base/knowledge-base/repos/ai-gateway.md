---
title: "ai-gateway Repo"
category: repos
tags: [repo, ai-gateway, python, fastapi, openrouter, llm, proxy, kafka, grpc]
owner: "@backend"
last_updated: "2026-04-02"
source: repo
repo_url: "https://github.com/ruh-ai/ai-gateway"
---

# ai-gateway

GitHub: `github.com/ruh-ai/ai-gateway`

Python 3.11 AI service proxy built with FastAPI + Uvicorn. Managed with uv (Hatchling build). Proxies requests to multiple AI/data providers: OpenRouter (LLM), Exa (web search), Apollo (sales enrichment), Deepgram (STT), ElevenLabs/Cartesia/Inworld (TTS), and PiAPI (video generation). Publishes usage events to Kafka (`ai-usage-events` topic). Validates credits via gRPC call to PaymentService.CheckRcuLimit. Stores Apollo credits in MongoDB.

## Directory Structure

```
ai-gateway/
├── main.py                          # FastAPI entry point
├── pyproject.toml
├── Makefile
├── Dockerfile
├── docker-compose.test.yml
├── run_local.sh
├── generate_grpc_code.py
├── app/
│   ├── api/router.py                # All API endpoints
│   ├── core/
│   │   ├── config.py                # Pydantic Settings
│   │   ├── constants.py             # Enums, platform keys
│   │   └── logging_utils.py
│   ├── middleware/
│   │   └── rcu_check_middleware.py   # RCU credit validation
│   ├── models/schemas.py            # Pydantic models
│   ├── services/
│   │   ├── openrouter_service.py    # LLM proxy (OpenRouter)
│   │   ├── exa_service.py          # Web search proxy (Exa)
│   │   ├── apollo_service.py       # Sales enrichment proxy (Apollo)
│   │   ├── deepgram_service.py     # STT proxy (HTTP + WebSocket)
│   │   ├── elevenlabs_service.py   # TTS proxy (ElevenLabs)
│   │   ├── cartesia_service.py     # TTS proxy (Cartesia)
│   │   ├── inworld_service.py      # TTS streaming proxy (Inworld)
│   │   ├── piapi_service.py        # Video generation (PiAPI)
│   │   ├── kafka_producer.py       # Usage event publisher
│   │   ├── mongodb_service.py      # Apollo credits CRUD
│   │   ├── grpc_client.py          # gRPC channel singleton
│   │   └── langfuse_service.py     # LLM observability (Langfuse)
│   ├── helper/                      # Logging, subscription helpers
│   ├── utils/                       # Tracing, metrics, retry, exceptions
│   └── grpc_/                       # Auto-generated gRPC stubs
├── config/telemetry.py
├── helm/                            # Helm values (dev/qa/main)
├── tests/                           # Full test suite
├── smoke/                           # Post-deploy smoke tests
└── .github/workflows/               # CI, build-deploy, post-deploy, regression
```

## Entry Point

`main.py` — FastAPI application on port 8000 (configurable via `PORT` env var). Swagger docs at `/docs`. Registers all routes from `app/api/router.py`, configures RCU credit-check middleware, and initializes Kafka producer + gRPC channel on startup.

## Local Development

```bash
# Prerequisites: Python 3.11+, uv, Docker (for Kafka, MongoDB)
git clone https://github.com/ruh-ai/ai-gateway
cd ai-gateway
cp .env.example .env                   # Fill in provider API keys, Kafka, MongoDB
./run_local.sh                         # uv sync + gRPC generation + start server
```

Manual start:

```bash
uv sync
python generate_grpc_code.py           # requires proto repo access
uv run uvicorn main:app --host 0.0.0.0 --port 8000 --reload
```

## Key Files to Read

| File | Purpose |
|------|---------|
| `main.py` | FastAPI app bootstrap |
| `app/api/router.py` | All API endpoint definitions |
| `app/core/config.py` | All configuration / env vars |
| `app/middleware/rcu_check_middleware.py` | Credit validation middleware |
| `app/services/openrouter_service.py` | LLM proxy (primary service) |
| `app/services/kafka_producer.py` | Usage event publishing |
| `app/services/grpc_client.py` | gRPC client for payment service |
| `app/models/schemas.py` | Request/response Pydantic models |

## Testing

```bash
make test-unit                         # Unit tests
make test-integration                  # Integration tests
make lint                              # Linting
make typecheck                         # Type checking
```

## See Also

- [services/ai-gateway/overview.md](../services/ai-gateway/overview.md) — Service-level docs
- [repos/agent-gateway.md](agent-gateway.md) — API gateway (primary HTTP client)
- [repos/agent-platform-v2.md](agent-platform-v2.md) — Agent platform (uses LLM proxy)
