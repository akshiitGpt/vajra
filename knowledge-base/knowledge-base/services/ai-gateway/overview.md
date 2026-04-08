---
title: "AI Gateway — Overview"
category: services
tags: [ai-gateway, proxy, openrouter, llm, tts, stt, video, fastapi, kafka]
owner: "@backend"
last_updated: "2026-04-02"
source: repo
---

# AI Gateway

Type: API proxy gateway
Repo: [repos/ai-gateway.md](../../repos/ai-gateway.md)
Language: Python 3.11
Framework: FastAPI + Uvicorn
Package Manager: uv (Hatchling build)
Port: 8000 (configurable)

## What It Does

The AI Gateway is a **centralized proxy** between Ruh AI's internal services and external third-party APIs. It handles:

1. **LLM proxy** — Routes chat completions and embeddings to OpenRouter (OpenAI-compatible)
2. **Web search proxy** — Routes search/content requests to Exa API
3. **Sales enrichment proxy** — Routes people/company search to Apollo API
4. **Speech-to-text proxy** — Routes STT to Deepgram (HTTP + WebSocket)
5. **Text-to-speech proxy** — Routes TTS to ElevenLabs, Cartesia, and Inworld (HTTP + WebSocket)
6. **Video generation proxy** — Routes video generation to PiAPI (Kling models)
7. **Usage tracking** — Every proxied request emits a Kafka event for billing/analytics
8. **RCU credit checks** — Middleware validates organisation credits via gRPC before allowing requests
9. **Admin endpoints** — Credit management for OpenRouter and Apollo

## API Endpoints

### LLM (OpenRouter)

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/v1/chat/completions` | Chat completion (streaming + non-streaming) |
| `POST` | `/v1/embeddings` | Embeddings |

### Web Search (Exa)

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/v1/exa/search` | Web search |
| `POST` | `/v1/exa/contents` | Content extraction |

### Sales Enrichment (Apollo)

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/v1/people/match` | People enrichment (1 credit) |
| `POST` | `/v1/people/bulk_match` | Bulk people enrichment (10 credits) |
| `POST` | `/v1/mixed_people/search` | People search |
| `POST` | `/v1/mixed_companies/search` | Organization search |

### Speech-to-Text (Deepgram)

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/v1/stt/deepgram` | Batch STT |
| `WS` | `/v1/stt/deepgram` | Real-time streaming STT |

### Text-to-Speech

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/v1/tts/voice:stream` | Inworld TTS |
| `POST` | `/v1/tts/elevenlabs/text-to-speech/{voice_id}/stream` | ElevenLabs HTTP |
| `WS` | `/v1/tts/elevenlabs/text-to-speech/{voice_id}/multi-stream-input` | ElevenLabs WS |
| `POST` | `/v1/tts/cartesia/tts/bytes` | Cartesia HTTP |
| `WS` | `/v1/tts/cartesia/tts/websocket` | Cartesia WS |

### Video (PiAPI / Kling)

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/v1/video/generate` | Blocking generation (polls until done) |
| `POST` | `/v1/video/create` | Non-blocking task creation |
| `GET` | `/v1/video/status/{task_id}` | Check task status |

### Admin

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/v1/admin/credits` | Combined OpenRouter + Apollo credits |
| `GET` | `/v1/admin/openrouter-credits` | OpenRouter balance |
| `GET` | `/v1/admin/apollo-credits` | Apollo credits balance |
| `POST` | `/v1/admin/apollo-credits` | Add Apollo credits |

## Authentication

Two modes:

1. **Server Auth (internal services)**: `X-Server-Auth` header with per-platform pre-shared keys. Each platform (agent-platform, voice-platform, workflow-platform, etc.) has its own key. The gateway maps the key to platform-specific API keys for downstream services.

2. **External API Key**: `external-api-key` header. Gateway resolves user identity via the API Gateway service, normalizes headers (`x-user-id`, `x-organisation-id`, `x-source=external`), and uses a dedicated OpenRouter key.

## Database (MongoDB)

Minimal — single collection for Apollo credits:

- Database: `ruh_admin`, Collection: `apollo_credits`
- Single-document pattern: `{ _id: "apollo_credits", total_credits: 0, total_usage: 0 }`
- Credits managed via `$inc` operations

## Events Produced (Kafka)

Topic: `ai-usage-events` (configurable via `KAFKA_USAGE_TOPIC`)

Every proxied request emits a usage event:

```json
{
  "event_id": "uuid",
  "timestamp": "ISO 8601",
  "model": "provider/model-name",
  "user_id": "from x-user-id header",
  "organisation_id": "from x-organisation-id header",
  "conversation_id": "from x-conversation-id header",
  "source": "from x-source header",
  "deduct_rcu": "true|false",
  "usage": { "input_tokens": 0, "output_tokens": 0, "total_tokens": 0, "cost": 0.0 }
}
```

Events include OpenTelemetry trace context headers for distributed tracing. The service does NOT consume any events.

## RCU Middleware

When enabled (`ENABLE_RCU_CHECK=true` + `GRPC_ENABLED=true`), calls `PaymentService.CheckRcuLimit` via gRPC before processing requests to validate organisation/user credit limits.

## Key Environment Variables

| Variable | Description |
|----------|-------------|
| `PORT` | Server port (default: 8000) |
| `SERVER_AUTH_*` | Per-platform auth keys (AGENT_PLATFORM, VOICE_PLATFORM, etc.) |
| `OPENROUTER_BASE_URL` | OpenRouter API base URL |
| `OPENROUTER_*_KEY` | Per-platform OpenRouter API keys |
| `EXA_API_BASE_URL` / `EXA_*_KEY` | Exa search config |
| `APOLLO_BASE_URL` / `APOLLO_*_KEY` | Apollo enrichment config |
| `DEEPGRAM_*` | Deepgram STT config (base URL, WS URL, keys) |
| `ELEVENLABS_*` / `CARTESIA_*` / `INWORLD_*` | TTS provider configs |
| `PIAPI_API_KEY` / `PIAPI_BASE_URL` | Video generation config |
| `MONGODB_URI` / `MONGODB_DB_NAME` | MongoDB connection |
| `KAFKA_BOOTSTRAP_SERVERS` / `KAFKA_USAGE_TOPIC` | Kafka config |
| `GRPC_SERVER_ADDRESS` | Payment service gRPC address |
| `LANGFUSE_*` | Langfuse LLM observability |
| `SIGNOZ_ENDPOINT` | SigNoz OTLP endpoint |

## Deployment

- Docker: `python:3.11-slim`, gRPC stubs generated at build time
- Kubernetes: Helm chart, HPA, ClusterIP on port 80 → container 5511
- External secrets from GCP Secret Manager
- CI/CD: GitHub Actions (lint, test, build, deploy with approval gates)

## See Also

- [repos/ai-gateway.md](../../repos/ai-gateway.md) — Repo guide
- [architecture/service-map.md](../../architecture/service-map.md) — Where this fits
- [services/agent-platform/dependencies.md](../agent-platform/dependencies.md) — How agent-platform calls this
