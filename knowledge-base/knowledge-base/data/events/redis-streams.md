---
title: "Redis Streams Events"
category: data
tags: [data, events, redis-streams]
owner: "@backend"
last_updated: "2026-04-09"
source: manual
---

# Redis Streams Event Contracts

Redis Streams are used for non-chat flows: workflow requests, memory storage, and stop signals. Agent chat requests and responses have moved to Kafka — see [kafka-events.md](kafka-events.md).

## Stream Naming

All streams prefixed with environment: `{env}:` where env is `dev`, `qa`, or `main`.

## Request Streams

### Stop Signal

Stream: `{env}:agent:stop:requests`

```json
{
  "conversation_id": "conv_789",
  "request_id": "req_abc"
}
```

### Workflow Requests

| Stream | Purpose |
|--------|---------|
| `{env}:workflow:requests` | Workflow generation requests |
| `{env}:workflow:chat:requests` | Workflow conversation requests |
| `{env}:memory:requests` | Memory storage requests |

## Response Streams

| Stream | Purpose |
|--------|---------|
| `{env}:workflow:responses:{request_id}` | Workflow generation responses |

## See Also

- [data/events/kafka-events.md](kafka-events.md) — Agent chat request/response event schemas (Kafka)
- [architecture/communication.md](../../architecture/communication.md) — Communication patterns
- [services/agent-platform/api.md](../../services/agent-platform/api.md) — Service API
