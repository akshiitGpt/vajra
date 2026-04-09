---
title: "Communication Patterns"
category: architecture
tags: [architecture, communication, protocols, kafka, redis, websocket]
owner: "@team-lead"
last_updated: "2026-04-09"
source: manual
---

# Communication Patterns

## Overview

Services in the Ruh AI platform communicate using four patterns:

| Pattern | Protocol | Used For |
|---------|----------|----------|
| **Async messaging (chat)** | Kafka | Agent chat requests/responses (primary data path) |
| **Async messaging (other)** | Redis Streams | Workflow, memory, stop signals |
| **Synchronous calls** | REST/HTTP | Config fetches, tool execution, service-to-service |
| **Real-time streaming** | WebSocket | Agent Gateway ↔ Frontend (chat + file events) |

## Kafka (Chat — Primary)

The primary transport for agent chat requests and responses between API Gateway and Agent Platform.

| Topic | Producer | Consumer | Key |
|-------|----------|----------|-----|
| `agent_chat_requests` | Agent Gateway | Agent Platform (consumer group: `agent-processors`) | `conversation_id` |
| `agent_chat_responses` | Agent Platform | Agent Gateway (per-instance consumer) | `request_id` |
| `agent_chat_requests_dlq` | Agent Platform | Ops / alerting | — |

No env-prefix on Kafka topics — environment isolation is handled at the Kafka cluster/namespace level.

### Response Event Types

Events flow through `agent_chat_responses` in this order:

```
STREAM_START → MESSAGE_START → MESSAGE* → MESSAGE_END →
  [TOOL_START → TOOL_STREAM* → TOOL_END]* →
  [THINKING_START → THINKING* → THINKING_END] →
  [DELEGATING_START → DELEGATING_END] →
STREAM_END
```

See: [data/events/kafka-events.md](../data/events/kafka-events.md)

## Redis Streams (Non-Chat)

Used for workflow, memory, and stop signal flows. All streams are prefixed with the environment: `{env}:` (dev, qa, main).

### Request Streams (consumed by Agent Platform)

| Stream | Purpose |
|--------|---------|
| `{env}:workflow:requests` | Workflow generation requests |
| `{env}:workflow:chat:requests` | Workflow conversation requests |
| `{env}:memory:requests` | Memory storage requests |
| `{env}:agent:stop:requests` | Stop signals for running agents |

### Response Streams (produced by Agent Platform)

| Stream | Purpose |
|--------|---------|
| `{env}:workflow:responses:{request_id}` | Workflow generation responses |

See: [data/events/redis-streams.md](../data/events/redis-streams.md)

## WebSocket (Agent Gateway ↔ Frontend)

Used for real-time bidirectional chat in the ruhclaw agent gateway.

### Client → Server

```json
{ "type": "message", "content": "user message text" }
```

### Server → Client Events

| Event | Purpose |
|-------|---------|
| `chunk` | Streaming text delta from the AI |
| `tool_call_start` | Agent started invoking a tool |
| `tool_call_update` | Partial tool output |
| `tool_call_result` | Final tool result (success/error) |
| `file_change` | File created/modified/deleted in workspace |
| `done` | Agent turn complete |
| `error` | Error message |

See: [data/events/websocket-events.md](../data/events/websocket-events.md)

## REST/HTTP (Service-to-Service)

Synchronous calls for configuration, tool execution, and service coordination.

| Caller | Callee | Endpoint Pattern | Purpose |
|--------|--------|-----------------|---------|
| Agent Platform | API Gateway | `GET /agents/{id}` | Fetch agent config |
| Agent Platform | AI Gateway | `POST /chat/completions` | LLM inference |
| Agent Platform | MCP Gateway | `POST /execute` | External tool execution |
| Agent Platform | Communication Service | `POST /send` | Cross-platform delivery |
| Agent Platform | Daytona | `POST /workspaces` | Sandbox provisioning |
| Agent Gateway | Docker Socket | Container API | Container lifecycle |

## Kafka (Analytics)

One-way event publishing for analytics. Agent Platform produces; analytics consumers read.

| Topic | Purpose |
|-------|---------|
| `analytics-service-agent-activity-events` | Tool calls, errors, session data |
| `token-usage-events` | LLM token consumption metrics |

See: [data/events/kafka-events.md](../data/events/kafka-events.md) for all Kafka topic contracts (chat + analytics)

## See Also

- [architecture/data-flow.md](data-flow.md) — Full data lifecycle
- [data/events/](../data/events/) — Event schema details
- [workflows/agent-chat-flow.md](../workflows/agent-chat-flow.md) — How these patterns combine in a real request
