---
title: "Service Map"
category: architecture
tags: [architecture, services, dependencies]
owner: "@team-lead"
last_updated: "2026-04-02"
source: repo
---

# Service Map

## Core Services

| Service | Type | Language | Repo | Status |
|---------|------|----------|------|--------|
| **agent-platform-v2** | Background worker | Python 3.11 | [repo](../repos/agent-platform-v2.md) | Active |
| **agent-gateway** | API Gateway | Python 3.13 (FastAPI) | [repo](../repos/agent-gateway.md) | Active |
| **communication-service** | gRPC server | Python 3.11 (gRPC/Beanie) | [repo](../repos/communication-service.md) | Active |
| **ai-gateway** | API proxy | Python 3.11 (FastAPI) | [repo](../repos/ai-gateway.md) | Active |
| **file-conversion** | Conversion service | Python 3.11 (FastAPI) | [repo](../repos/file-conversion.md) | Active |

## Supporting Infrastructure

| Component | Purpose | Technology |
|-----------|---------|------------|
| Redis | Message broker + sessions + cache | Redis Streams, pub/sub, key-value |
| MongoDB | Conversations, messages, checkpoints | Motor/Beanie (comm-service), MongoDBSaver (agent-platform) |
| PostgreSQL | Alternative checkpoint backend | LangGraph checkpoint storage |
| Qdrant | Vector memory | Mem0 long-term memory |
| Kafka | Event streaming | Usage events (ai-gateway), activity events (agent-platform) |
| Google Cloud Storage | File storage | Generated files, converted documents |
| SigNoz | Observability | OpenTelemetry traces/metrics |

## Dependency Graph

```
                    ┌──────────────────┐
                    │  Agent Gateway   │
                    │  (FastAPI :8001) │
                    └──┬──┬──┬──┬──┬──┘
                       │  │  │  │  │
          ┌────────────┘  │  │  │  └────────────────┐
          │               │  │  │                    │
     Redis Streams    gRPC│  │  │gRPC           HTTP │
          │               │  │  │                    │
     ┌────▼──────┐  ┌─────▼──┘  └──▼──────┐  ┌──────▼──────┐
     │  Agent    │  │ Comms    │  │  User  │  │    File     │
     │ Platform  │  │ Service  │  │Service │  │ Conversion  │
     │  (Py)    │  │ (gRPC)   │  │(gRPC)  │  │  (:8080)    │
     └┬──┬──┬──┬┘  └────┬─────┘  └────────┘  └──────┬──────┘
      │  │  │  │         │                           │
      │  │  │  │      MongoDB                       GCS
      │  │  │  │
   ┌──┘  │  │  └──────────┐
   │     │  │              │
┌──▼──┐ ┌▼──▼───┐  ┌──────▼──────┐
│ AI  │ │  MCP  │  │   Daytona   │
│Gate │ │Gateway│  │  Sandboxes  │
│way  │ │       │  │             │
└──┬──┘ └───┬───┘  └─────────────┘
   │        │
   ▼        ▼
 OpenRouter  External Tools
 Exa, Apollo (Gmail, Jira,
 Deepgram    Slack, GitHub)
 ElevenLabs
```

## Service-to-Service Dependencies

### Agent Gateway depends on:

| Service | How | Purpose |
|---------|-----|---------|
| Redis | Streams (pub/sub) | Chat message routing (publish requests, read responses) |
| Redis | Key-value | JWT sessions, bot configs, cache |
| Communication Service | gRPC | Conversation/message CRUD (MongoDB-backed) |
| User Service | gRPC | User validation |
| Agent Service | gRPC + HTTP | Agent config and management |
| AI Gateway | HTTP | Chat suggestions, title generation, instructions |
| File Conversion | HTTP | Markdown to PDF/DOCX conversion |
| Telegram API Service | HTTP | Bot webhook management |
| Kafka | TCP | Event topics (secondary) |
| SigNoz | gRPC | OpenTelemetry traces/metrics |

### Agent Platform depends on:

| Service | How | Purpose |
|---------|-----|---------|
| Redis | Streams (consumer) | Receives chat/workflow requests |
| Redis | Streams (producer) | Sends streamed responses |
| Redis | Key-value | Transient state (stop signals, sources, files) |
| MongoDB | TCP | Conversation checkpoints |
| PostgreSQL | TCP | Alternative checkpoint backend |
| Qdrant | HTTP | Long-term memory (Mem0) |
| Kafka | TCP | Publishes activity events |
| AI Gateway | HTTP | LLM request routing |
| API Gateway | HTTP | Fetches agent configurations |
| MCP Gateway | HTTP | External tool execution |
| Communication Service | HTTP | Cross-platform message delivery |
| Daytona | HTTP | Cloud sandbox provisioning |
| GCS | HTTP | File storage |
| SigNoz | gRPC | OpenTelemetry traces/metrics |

### Communication Service depends on:

| Service | How | Purpose |
|---------|-----|---------|
| MongoDB | TCP | Conversation and message persistence |
| SigNoz | gRPC | OpenTelemetry traces/metrics |

### AI Gateway depends on:

| Service | How | Purpose |
|---------|-----|---------|
| OpenRouter | HTTP | Primary LLM routing |
| Exa | HTTP | Web search |
| Apollo | HTTP | Sales enrichment |
| Deepgram | HTTP + WS | Speech-to-text |
| ElevenLabs | HTTP + WS | Text-to-speech |
| Cartesia | HTTP + WS | Text-to-speech |
| Inworld | HTTP | Text-to-speech |
| PiAPI | HTTP | Video generation (Kling) |
| MongoDB | TCP | Apollo credits tracking |
| Kafka | TCP | Usage event publishing |
| Payment Service | gRPC | RCU credit validation |
| SigNoz | gRPC | OpenTelemetry traces/metrics |

### File Conversion depends on:

| Service | How | Purpose |
|---------|-----|---------|
| Google Cloud Storage | HTTP | Read source markdown, write converted output |

## See Also

- [architecture/communication.md](communication.md) — Protocol details
- [architecture/data-flow.md](data-flow.md) — How data moves
- [services/](../services/) — Per-service deep dives
