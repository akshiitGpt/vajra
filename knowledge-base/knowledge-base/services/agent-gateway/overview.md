---
title: "Agent Gateway — Overview"
category: services
tags: [agent-gateway, python, fastapi, api-gateway, redis-streams, sse, grpc]
owner: "@backend"
last_updated: "2026-04-02"
source: repo
---

# Agent Gateway

Type: API Gateway
Repo: [repos/agent-gateway.md](../../repos/agent-gateway.md)
Language: Python 3.13
Framework: FastAPI + Uvicorn
Package Manager: Poetry

## What It Does

The agent-gateway (internally `developer-api-gateway`) is the **central HTTP entry point** for developers and external platforms to interact with Ruh AI's agent ecosystem. It does NOT run agents — it routes requests to upstream microservices via gRPC and HTTP.

Key responsibilities:
1. **Chat Streaming** — Accepts user messages via REST, publishes to Redis Streams, reads agent responses and forwards as Server-Sent Events (SSE)
2. **Conversation/Message CRUD** — Full CRUD via gRPC calls to communication-service
3. **Bot Lifecycle** — Start/stop Telegram bots and Slack apps via Redis (separate listener service picks up changes)
4. **Webhook Processing** — Receives inbound Telegram webhooks, creates conversations, publishes to Redis Streams
5. **AI Services Proxy** — Proxies to AI Gateway for chat suggestions, title generation, instruction generation
6. **File Conversion** — Proxies markdown-to-PDF/DOCX conversion to file-conversion service
7. **Authentication** — JWT Bearer token auth with Redis session validation, API key auth, SDR platform auth

## How It Works

```
Client (browser / API key)
  │
  ├── POST /api/v1/agents/chat/stream
  │     → Publish message to Redis Stream: {env}:agent:chat:requests
  │     → Read from response stream: {env}:agent:chat:responses:{conv_id}-{req_id}
  │     → Forward chunks as SSE to client
  │
  ├── GET/POST /api/v1/communication/*
  │     → gRPC calls to communication-service (MongoDB-backed)
  │
  ├── POST /api/v1/webhook/telegram
  │     → Parse webhook → Create/get conversation → Publish to Redis Streams
  │     → Read response → Send back to Telegram
  │
  └── POST /api/v1/ai-services/*
        → HTTP proxy to AI Gateway (OpenRouter)
```

## Key Design Choices

1. **No database** — All persistence goes through gRPC to communication-service (MongoDB)
2. **Redis Streams + SSE** — Chat uses Redis Streams for inter-service messaging, SSE for client delivery
3. **gRPC for upstream** — Calls communication-service, user-service, agent-service via gRPC (protos compiled at build time)
4. **Platform abstraction** — Telegram and SMS providers implement a common `BasePlatformProvider` interface
5. **Bot lifecycle via Redis** — Bot configs are written to Redis; a separate grammY/Bolt.js listener service picks up changes

## Deployment

- Port: 8001
- Kubernetes: Helm chart, HPA, GKE
- CI/CD: GitHub Actions (lint, test, build, deploy with approval gates)
- Environments: dev, qa, main

## See Also

- [services/agent-gateway/api.md](api.md) — REST + SSE endpoints
- [services/agent-gateway/database.md](database.md) — Data access patterns
- [services/agent-gateway/events.md](events.md) — Redis Streams + Kafka events
- [services/agent-gateway/dependencies.md](dependencies.md) — Upstream dependencies
- [workflows/agent-chat-flow.md](../../workflows/agent-chat-flow.md) — End-to-end chat flow
