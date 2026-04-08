---
title: "Agent Platform — Events"
category: services
tags: [agent-platform, events, kafka, redis-streams]
owner: "@backend"
last_updated: "2026-04-02"
source: repo
---

# Agent Platform — Events

## Events Consumed

### Redis Streams

| Stream | Event | Action |
|--------|-------|--------|
| `{env}:agent:chat:requests` | Chat message | Execute agent graph, stream response |
| `{env}:workflow:requests` | Workflow generation | Generate multi-step workflow |
| `{env}:workflow:chat:requests` | Workflow chat | Execute workflow conversation |
| `{env}:memory:requests` | Memory store | Persist memory to Qdrant |
| `{env}:agent:stop:requests` | Stop signal | Cancel running agent execution |

## Events Produced

### Redis Streams (Responses)

Published to per-conversation response streams. See [api.md](api.md) for the full event type list.

Stream: `{env}:agent:chat:responses:{conversation_id}-{request_id}`

### Kafka (Analytics)

| Topic | Event | Data |
|-------|-------|------|
| `analytics-service-agent-activity-events` | Agent activity | Tool calls (name, success/error, MCP details), stream errors |

Kafka events are fire-and-forget — they do not block agent execution.

## Error Handling

- Failed Redis Stream messages are retried up to a configurable limit
- After max retries, messages go to a Dead Letter Queue (DLQ)
- Kafka publish failures are logged but do not affect agent execution

## See Also

- [data/events/redis-streams.md](../../data/events/redis-streams.md) — Full stream event schemas
- [data/events/kafka-events.md](../../data/events/kafka-events.md) — Kafka event schemas
- [architecture/communication.md](../../architecture/communication.md) — Communication patterns
