---
title: "Redis Streams Events"
category: data
tags: [data, events, redis-streams]
owner: "@backend"
last_updated: "2026-03-31"
source: manual
---

# Redis Streams Event Contracts

## Stream Naming

All streams prefixed with environment: `{env}:` where env is `dev`, `qa`, or `main`.

## Request Streams

### Agent Chat Request

Stream: `{env}:agent:chat:requests`

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

### Stop Signal

Stream: `{env}:agent:stop:requests`

```json
{
  "conversation_id": "conv_789",
  "request_id": "req_abc"
}
```

## Response Streams

Stream: `{env}:agent:chat:responses:{conversation_id}-{request_id}`

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

## Consumer Groups

Workers join the consumer group `{env}:agent:chat:requests:group`. Redis distributes messages across workers automatically.

## Error Handling

- Failed messages retried up to configurable max
- After max retries → Dead Letter Queue (DLQ)
- DLQ stream: `{env}:agent:chat:requests:dlq`

## See Also

- [architecture/communication.md](../../architecture/communication.md) — Communication patterns
- [services/agent-platform/api.md](../../services/agent-platform/api.md) — Service API
