---
title: "Agent Platform — Events"
category: services
tags: [agent-platform, events, kafka, redis-streams]
owner: "@backend"
last_updated: "2026-04-09"
source: repo
---

# Agent Platform — Events

## Events Consumed

### Kafka (Chat)

| Topic | Consumer Group | Action |
|-------|---------------|--------|
| `agent_chat_requests` | `agent-processors` | Execute agent graph, stream response |

### Redis Streams

| Stream | Event | Action |
|--------|-------|--------|
| `{env}:workflow:requests` | Workflow generation | Generate multi-step workflow |
| `{env}:workflow:chat:requests` | Workflow chat | Execute workflow conversation |
| `{env}:memory:requests` | Memory store | Persist memory to Qdrant |
| `{env}:agent:stop:requests` | Stop signal | Cancel running agent execution |

## Events Produced

### Kafka (Chat Responses)

Streamed response events published per-request. See [api.md](api.md) for the full event type list.

| Topic | Key | Description |
|-------|-----|-------------|
| `agent_chat_responses` | `request_id` | Streamed chat response events |
| `agent_chat_requests_dlq` | — | Failed requests after max retries |

### Kafka (Analytics)

| Topic | Event | Data |
|-------|-------|------|
| `analytics-service-agent-activity-events` | Agent activity | Tool calls (name, success/error, MCP details), stream errors |

Analytics events are fire-and-forget — they do not block agent execution.

## Error Handling

- Failed Kafka chat messages are retried up to a configurable limit
- After max retries, messages are forwarded to the DLQ topic (`agent_chat_requests_dlq`)
- Failed Redis Stream messages (workflow, memory) are retried with their own DLQ
- Kafka analytics publish failures are logged but do not affect agent execution

## See Also

- [data/events/kafka-events.md](../../data/events/kafka-events.md) — Kafka event schemas (chat + analytics)
- [data/events/redis-streams.md](../../data/events/redis-streams.md) — Redis Streams event schemas (non-chat)
- [architecture/communication.md](../../architecture/communication.md) — Communication patterns
