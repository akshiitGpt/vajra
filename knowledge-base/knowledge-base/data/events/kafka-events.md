---
title: "Kafka Events"
category: data
tags: [data, events, kafka, analytics, chat]
owner: "@backend"
last_updated: "2026-04-09"
source: manual
---

# Kafka Event Contracts

## Topics

| Topic | Producer | Consumer | Purpose |
|-------|----------|----------|---------|
| `agent_chat_requests` | Agent Gateway | Agent Platform | Agent chat messages (primary) |
| `agent_chat_responses` | Agent Platform | Agent Gateway | Streamed chat response events (primary) |
| `agent_chat_requests_dlq` | Agent Platform | Ops / alerting | Failed chat requests after max retries |
| `analytics-service-agent-activity-events` | Agent Platform | Analytics service | Tool calls, errors, sessions |
| `token-usage-events` | Agent Platform | Billing / analytics | LLM token consumption |

## Agent Chat Request

Topic: `agent_chat_requests`

Message key: `conversation_id` (bytes)

```json
{
  "organisation_id": "org_123",
  "agent_id": "agent_456",
  "conversation_id": "conv_789",
  "request_id": "req_abc",
  "user_id": "user_def",
  "message": "User message text",
  "attachments": [],
  "metadata": {}
}
```

Consumer group: `agent-processors` (Agent Platform workers)

## Agent Chat Response

Topic: `agent_chat_responses`

Message key: `request_id` (bytes)

Kafka headers: `conversation_id`, `request_id`, `event_type` (for fast filtering without deserialization)

### Event Types (in order)

| Type | Payload | Description |
|------|---------|-------------|
| `STREAM_START` | `{}` | Response begins |
| `MESSAGE_START` | `{}` | LLM generation started |
| `MESSAGE` | `{ "content": "text delta" }` | Streaming token |
| `MESSAGE_END` | `{}` | LLM segment complete |
| `TOOL_START` | `{ "tool_name": "...", "args": {...} }` | Tool invocation |
| `TOOL_STREAM` | `{ "content": "..." }` | Tool output delta |
| `TOOL_END` | `{ "result": "..." }` | Tool complete |
| `THINKING_START` | `{}` | Reasoning begins |
| `THINKING` | `{ "content": "..." }` | Reasoning delta |
| `THINKING_END` | `{}` | Reasoning complete |
| `DELEGATING_START` | `{ "subagent": "..." }` | Delegating to subagent |
| `DELEGATING_END` | `{}` | Subagent done |
| `STREAM_END` | `{}` | Entire response complete |

### Event Flow

```
STREAM_START
  → MESSAGE_START → MESSAGE* → MESSAGE_END
  → [TOOL_START → TOOL_STREAM* → TOOL_END]*
  → [THINKING_START → THINKING* → THINKING_END]
  → [DELEGATING_START → ... → DELEGATING_END]
  → [MESSAGE_START → MESSAGE* → MESSAGE_END]*
STREAM_END
```

Consumer: each Agent Gateway instance runs its own independent consumer (unique group ID per instance) so every instance sees all response messages and routes to its active SSE connections.

## Agent Chat DLQ

Topic: `agent_chat_requests_dlq`

Messages that fail deserialization or dispatch after max retries are forwarded here with the original payload and error metadata.

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

- **Chat requests/responses**: at-least-once delivery. Requests keyed by `conversation_id`, responses by `request_id` — ordering is preserved within a partition (same key = same partition).
- **Analytics**: fire-and-forget — Kafka publish failures are logged but don't block agent execution.
- Consumers must handle duplicate events.

## See Also

- [data/events/redis-streams.md](redis-streams.md) — Redis Streams (non-chat: workflow, memory, stop signals)
- [services/agent-platform/events.md](../../services/agent-platform/events.md) — All events
- [data/pipelines/analytics.md](../pipelines/analytics.md) — Analytics pipeline
