---
title: "Kafka Events"
category: data
tags: [data, events, kafka, analytics]
owner: "@backend"
last_updated: "2026-03-31"
source: manual
---

# Kafka Event Contracts

## Topics

| Topic | Producer | Consumer | Purpose |
|-------|----------|----------|---------|
| `analytics-service-agent-activity-events` | Agent Platform | Analytics service | Tool calls, errors, sessions |
| `token-usage-events` | Agent Platform | Billing / analytics | LLM token consumption |

## Agent Activity Event

Topic: `analytics-service-agent-activity-events`

```json
{
  "event_type": "tool_call | error | session_start | session_end",
  "organisation_id": "org_123",
  "agent_id": "agent_456",
  "conversation_id": "conv_789",
  "user_id": "user_def",
  "timestamp": "2026-03-31T12:00:00Z",
  "data": {
    "tool_name": "web_search",
    "model": "claude-sonnet-4",
    "duration_ms": 1500
  }
}
```

## Token Usage Event

Topic: `token-usage-events`

```json
{
  "organisation_id": "org_123",
  "agent_id": "agent_456",
  "model": "claude-sonnet-4",
  "provider": "openrouter",
  "input_tokens": 1500,
  "output_tokens": 800,
  "timestamp": "2026-03-31T12:00:00Z"
}
```

## Delivery Guarantees

- **At-least-once** — Consumers must handle duplicate events
- **Fire-and-forget from producer** — Kafka publish failures are logged but don't block agent execution

## See Also

- [services/agent-platform/events.md](../../services/agent-platform/events.md) — All events
- [data/pipelines/analytics.md](../pipelines/analytics.md) — Analytics pipeline
