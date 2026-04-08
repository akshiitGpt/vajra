---
title: "Architecture Overview"
category: architecture
tags: [overview, architecture, ruh-ai]
owner: "@team-lead"
last_updated: "2026-04-02"
source: repo
---

# Architecture Overview

## What is Ruh AI?

Ruh AI is an AI agent platform that enables users to create, configure, and interact with AI assistants. Each agent can execute code, browse the web, manage files, call external tools (Gmail, Jira, Slack), and maintain long-term memory across conversations.

## Platform Components

The platform is composed of five core services:

| Service | Role | Tech |
|---------|------|------|
| **Agent Platform** | Executes AI agents — the brain | Python, LangGraph, Redis Streams |
| **Agent Gateway** | HTTP entry point — routes to upstream services | Python, FastAPI, gRPC, Redis Streams + SSE |
| **AI Gateway** | Proxies LLM/search/TTS/STT/video requests | Python, FastAPI, OpenRouter, Exa, Deepgram, ElevenLabs |
| **Communication Service** | Conversation and message persistence | Python, gRPC, MongoDB/Beanie |
| **File Conversion** | Markdown to PDF/DOCX conversion | Python, FastAPI, Playwright, GCS |

Plus shared infrastructure:
- **Redis** — Streams for async messaging, sessions, caching
- **MongoDB** — Conversations, messages, checkpoints
- **PostgreSQL** — Alternative checkpoint backend
- **Qdrant** — Vector memory (Mem0)
- **Kafka** — Usage events (ai-gateway), activity events (agent-platform)
- **Google Cloud Storage** — File storage and converted documents
- **SigNoz** — OpenTelemetry observability

## How It Works (Simplified)

```
User (web/mobile/API)
  │
  ▼
Agent Gateway (FastAPI :8001, JWT/API key auth)
  │
  ├──▶ Redis Streams ──▶ Agent Platform (Python worker)
  │       ◀── responses ──┘   │
  │       (forwarded as SSE)   ├── LangGraph supervisor agent
  │                            ├── Subagents (KB, web, code, files, MCP)
  │                            ├── LLM calls via AI Gateway
  │                            └── Memory via Qdrant (Mem0)
  │
  ├──▶ Communication Service (gRPC :50055)
  │       └── MongoDB (conversations, messages)
  │
  ├──▶ AI Gateway (:8000)
  │       └── OpenRouter, Exa, Deepgram, ElevenLabs, PiAPI
  │
  ├──▶ File Conversion (:8080)
  │       └── GCS → Markdown → PDF/DOCX → GCS
  │
  └──▶ Webhooks (Telegram, SMS via Twilio)
```

## Key Design Decisions

1. **Event-driven, not request-response** — Agent Platform has no HTTP API. All communication flows through Redis Streams, enabling horizontal scaling via consumer groups.

2. **Gateway pattern** — Agent Gateway is the single HTTP entry point. It handles auth, routes to upstream services via gRPC/HTTP, and converts Redis Streams responses to SSE for clients.

3. **Multi-agent architecture** — A supervisor agent delegates to specialized subagents (knowledge base, web search, code execution, file management, MCP tools).

4. **Provider-agnostic AI proxy** — AI Gateway abstracts not just LLM providers but also search, speech, and video APIs. All usage is tracked via Kafka for billing.

5. **Persistence via gRPC** — The gateway has no database. All conversation/message persistence goes through the communication-service via gRPC, keeping concerns separated.

6. **Long-term memory** — Qdrant-backed Mem0 stores user and agent memories as vector embeddings, retrievable across conversations.

## See Also

- [architecture/service-map.md](service-map.md) — Detailed service relationships
- [architecture/communication.md](communication.md) — How services talk to each other
- [architecture/data-flow.md](data-flow.md) — Data lifecycle
- [workflows/agent-chat-flow.md](../workflows/agent-chat-flow.md) — End-to-end chat request
