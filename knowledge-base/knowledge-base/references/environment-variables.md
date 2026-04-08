---
title: "Environment Variables"
category: references
tags: [reference, env-vars, configuration]
owner: "@devops"
last_updated: "2026-04-02"
source: repo
---

# Environment Variables

## Agent Platform

### Core

| Variable | Required | Description |
|----------|----------|-------------|
| `ENV` | Yes | Environment: `dev`, `qa`, `main` — prefixes all Redis streams |
| `MODEL_PROVIDER` | Yes | LLM provider: `requesty`, `openrouter`, `openai` |
| `CHECKPOINTER_BACKEND` | No | `mongo` (default) or `pg` |

### LLM Providers

| Variable | Required | Description |
|----------|----------|-------------|
| `OPENROUTER_API_KEY` | Yes (if openrouter) | OpenRouter API key |
| `OPENAI_API_KEY` | Conditional | OpenAI API key |
| `ANTHROPIC_API_KEY` | Conditional | Anthropic API key |
| `REQUESTY_API_KEY` | Conditional | Requesty API key |
| `AI_GATEWAY_BASE_URL` | Yes | AI Gateway proxy URL |
| `AI_GATEWAY_API_KEY` | Yes | AI Gateway auth key |

### Subagent Models

| Variable | Description |
|----------|-------------|
| `KNOWLEDGE_BASE_SUBAGENT_MODEL` | Model for KB search subagent |
| `WEB_SEARCH_SUBAGENT_MODEL` | Model for web search subagent |
| `MCP_SUBAGENT_MODEL` | Model for MCP tool subagent |
| `CODE_EXECUTOR_SUBAGENT_MODEL` | Model for code execution subagent |
| `FILE_MANAGER_SUBAGENT_MODEL` | Model for file management subagent |
| `SUMMARISATION_PROVIDER` / `SUMMARISATION_MODEL` | Summarization provider/model |

### Infrastructure

| Variable | Required | Description |
|----------|----------|-------------|
| `REDIS_HOST` | Yes | Redis host |
| `REDIS_PORT` | Yes | Redis port |
| `REDIS_DB` | No | Redis database number |
| `REDIS_PASSWORD` | Conditional | Redis auth password |
| `REDIS_URI` | Conditional | Full Redis URI (alternative to host/port) |
| `REDIS_USE_TLS` | No | Enable TLS for Redis |
| `MONGO_DB_URL` | Yes (if mongo) | MongoDB connection string |
| `POSTGRES_CHECKPOINTER_URL` | Conditional | PostgreSQL connection string |
| `QDRANT_HOST` | Yes | Qdrant vector DB host |
| `QDRANT_PORT` | Yes | Qdrant port |
| `QDRANT_API_KEY` | Conditional | Qdrant auth key |
| `QDRANT_COLLECTION_NAME` | No | Vector collection name |
| `KAFKA_BOOTSTRAP_SERVERS` | Yes | Kafka broker addresses |
| `KAFKA_AGENT_ACTIVITY_EVENTS_TOPIC` | No | Activity events topic name |

### Service URLs

| Variable | Required | Description |
|----------|----------|-------------|
| `API_GATEWAY_URL` | Yes | External API Gateway URL |
| `API_GATEWAY_KEY` | Yes | API Gateway auth key |
| `MCP_GATEWAY_URL` | Yes | MCP tool gateway URL |
| `MCP_API_KEY` | Yes | MCP Gateway auth key |
| `SCHEDULER_API_BASE_URL` | No | Scheduler service URL |
| `COMMUNICATION_CHANNEL_MANAGER_URL` | No | Communication service URL |
| `WORKFLOW_API_GATEWAY_URL` | No | Workflow execution service URL |
| `DAYTONA_API_URL` | No | Daytona sandbox API URL |
| `DAYTONA_API_KEY` | No | Daytona API key |
| `BROWSER_USE_API_KEY` | No | Browser-use Cloud API key |
| `GCS_BUCKET_NAME` | No | Google Cloud Storage bucket |

### Feature Flags

| Variable | Description |
|----------|-------------|
| `CODE_EXECUTION_ENABLED` | Enable code execution subagent |
| `BROWSER_USE_ENABLED` | Enable browser automation |
| `OTEL_ENABLED` | Enable OpenTelemetry |

### Observability

| Variable | Required | Description |
|----------|----------|-------------|
| `SIGNOZ_ENDPOINT` | Conditional | SigNoz gRPC endpoint |
| `LOG_LEVEL` | No | Logging level (default: `info`) |

## Agent Gateway

### Core

| Variable | Required | Description |
|----------|----------|-------------|
| `ENV` | Yes | Environment: `dev`, `qa`, `main` |
| `APP_NAME` | No | Application name |
| `DEBUG` | No | Debug mode |
| `API_V1_STR` | No | API version prefix |

### Auth

| Variable | Required | Description |
|----------|----------|-------------|
| `JWT_SECRET_KEY` | Yes | JWT signing secret |
| `JWT_ALGORITHM` | No | JWT algorithm (default: HS256) |
| `ACCESS_TOKEN_EXPIRE_MINUTES` | No | JWT access token TTL |
| `REFRESH_TOKEN_EXPIRE_DAYS` | No | Refresh token TTL |

### Redis

| Variable | Required | Description |
|----------|----------|-------------|
| `REDIS_HOST` | Yes | Redis host |
| `REDIS_PORT` | Yes | Redis port |
| `REDIS_DB` | No | Redis database |
| `REDIS_PASSWORD` | Conditional | Redis auth |
| `REDIS_URI` | Conditional | Full URI (alternative) |
| `REDIS_USE_TLS` | No | Enable TLS |
| `CACHE_ENABLED` | No | Enable Redis caching |
| `CACHE_TTL_MESSAGES` / `CACHE_TTL_CONVERSATION` | No | Cache TTLs |

### Upstream Services

| Variable | Required | Description |
|----------|----------|-------------|
| `USER_SERVICE_HOST` / `USER_SERVICE_PORT` | Yes | User service gRPC (default: user_service:50052) |
| `COMMUNICATION_SERVICE_HOST` / `COMMUNICATION_SERVICE_PORT` | Yes | Comms gRPC (default: admin_service:50055) |
| `AGENT_SERVICE_HOST` / `AGENT_SERVICE_PORT` | Yes | Agent service gRPC (default: :50057) |
| `AI_GATEWAY_BASE_URL` | Yes | AI Gateway HTTP (default: http://localhost:5511/v1) |
| `AI_GATEWAY_API_KEY` | Yes | AI Gateway auth key |
| `FILE_CONVERSION_SERVICE_URL` | Yes | File conversion service URL |
| `API_GATEWAY_URL` | Yes | Main API Gateway URL |
| `TELEGRAM_API_BASE_URL` | No | Telegram API service URL |

### Platform

| Variable | Required | Description |
|----------|----------|-------------|
| `TWILIO_ACCOUNT_SID` / `TWILIO_AUTH_TOKEN` / `TWILIO_PHONE_NUMBER` | No | SMS via Twilio |
| `CORS_ORIGINS` | No | Allowed CORS origins |

### Proto Generation

| Variable | Required | Description |
|----------|----------|-------------|
| `REPO_URL` | Build-time | Proto definitions repo URL |
| `GIT_TOKEN` | Build-time | Git PAT for proto repo |

## Communication Service

| Variable | Default | Description |
|----------|---------|-------------|
| `GRPC_PORT` | 50055 | gRPC server port |
| `MONGO_HOST` | localhost | MongoDB host |
| `MONGO_PORT` | 27017 | MongoDB port |
| `MONGO_DB_NAME` | communication-service | Database name |
| `MONGO_USERNAME` / `MONGO_PASSWORD` | (empty) | MongoDB auth |
| `REPO_URL` | (empty) | Proto repo for gRPC generation |
| `GIT_TOKEN` | (empty) | Git PAT for proto repo |
| `OTEL_ENABLED` | true | Enable OpenTelemetry |
| `SIGNOZ_ENDPOINT` | http://localhost:4317 | SigNoz OTLP endpoint |

## AI Gateway

### Core

| Variable | Default | Description |
|----------|---------|-------------|
| `HOST` | 0.0.0.0 | Bind address |
| `PORT` | 8000 | Server port |
| `ENV` | dev | Environment |

### Server Auth Keys (one per platform)

| Variable | Description |
|----------|-------------|
| `SERVER_AUTH_AGENT_PLATFORM` | Auth key for agent-platform |
| `SERVER_AUTH_VOICE_PLATFORM` | Auth key for voice-platform |
| `SERVER_AUTH_WORKFLOW_PLATFORM` | Auth key for workflow-platform |
| `SERVER_AUTH_KNOWLEDGE_BASE_PLATFORM` | Auth key for KB platform |
| `SERVER_AUTH_SDR_BACKEND` | Auth key for SDR backend |

### Provider Keys

| Variable | Description |
|----------|-------------|
| `OPENROUTER_BASE_URL` | OpenRouter API URL |
| `OPENROUTER_*_KEY` | Per-platform OpenRouter keys (AGENT_PLATFORM, VOICE_PLATFORM, etc.) |
| `EXA_API_BASE_URL` / `EXA_*_KEY` | Exa search config |
| `APOLLO_BASE_URL` / `APOLLO_*_KEY` | Apollo enrichment config |
| `DEEPGRAM_*` | Deepgram STT (base URL, WS URL, keys) |
| `ELEVENLABS_*` / `CARTESIA_*` / `INWORLD_*` | TTS provider configs |
| `PIAPI_API_KEY` / `PIAPI_BASE_URL` | Video generation (PiAPI/Kling) |

### Infrastructure

| Variable | Description |
|----------|-------------|
| `MONGODB_URI` / `MONGODB_DB_NAME` | MongoDB (Apollo credits) |
| `KAFKA_BOOTSTRAP_SERVERS` / `KAFKA_USAGE_TOPIC` | Kafka usage events |
| `GRPC_SERVER_ADDRESS` | Payment service gRPC |
| `ENABLE_RCU_CHECK` | Enable RCU credit middleware (default: true) |
| `LANGFUSE_*` | Langfuse LLM observability |
| `SIGNOZ_ENDPOINT` | SigNoz OTLP endpoint |

## File Conversion Service

| Variable | Required | Description |
|----------|----------|-------------|
| `SERVER_AUTH_KEY` | Yes | Shared secret for `X-Server-Auth-Key` header |
| `SERVICE_URL` | No | Used by convert.sh helper (default: http://localhost:8080) |

## See Also

- [services/agent-platform/dependencies.md](../services/agent-platform/dependencies.md) — What uses each var
- [infra/environments.md](../infra/environments.md) — Environment config
