---
title: "Agent Platform — API"
category: services
tags: [agent-platform, api, redis-streams]
owner: "@backend"
last_updated: "2026-04-02"
source: repo
---

# Agent Platform — API (Redis Streams Interface)

The agent-platform has no REST API. All communication is via Redis Streams.

## Request Streams (Consumed)

### Agent Chat Request

Stream: `{env}:agent:chat:requests`

```json
{
  "organisation_id": "org_123",
  "agent_id": "agent_456",
  "conversation_id": "conv_789",
  "request_id": "req_abc",
  "message": "Write a Python function to sort a list",
  "user_id": "user_def",
  "attachments": [],
  "metadata": {}
}
```

### Workflow Generation Request

Stream: `{env}:workflow:requests`

### Workflow Chat Request

Stream: `{env}:workflow:chat:requests`

### Memory Storage Request

Stream: `{env}:memory:requests`

### Stop Signal

Stream: `{env}:agent:stop:requests`

Used to cancel a running agent mid-execution. The agent checks for stop signals between tool calls and LLM steps.

## Response Streams (Produced)

### Agent Chat Response

Stream: `{env}:agent:chat:responses:{conversation_id}-{request_id}`

One stream per conversation turn. Events are published in order:

| Event Type | Payload | Description |
|-----------|---------|-------------|
| `STREAM_START` | `{}` | Marks the beginning of a response |
| `MESSAGE_START` | `{}` | LLM started generating |
| `MESSAGE` | `{ content: "..." }` | Text delta (streaming token) |
| `MESSAGE_END` | `{}` | LLM finished this message segment |
| `TOOL_START` | `{ tool_name: "...", args: {...} }` | Agent invoking a tool |
| `TOOL_STREAM` | `{ content: "..." }` | Tool output delta |
| `TOOL_END` | `{ result: "..." }` | Tool execution complete |
| `THINKING_START` | `{}` | Agent reasoning (if exposed) |
| `THINKING` | `{ content: "..." }` | Reasoning delta |
| `THINKING_END` | `{}` | Reasoning complete |
| `DELEGATING_START` | `{ subagent: "..." }` | Delegating to subagent |
| `DELEGATING_END` | `{}` | Subagent finished |
| `STREAM_END` | `{}` | Entire response complete |

### Workflow Response

Stream: `{env}:workflow:responses:{request_id}`

## Health Check (HTTP)

The only HTTP endpoint:

```
GET :6000/health → 200 OK
```

Used by Kubernetes liveness/readiness probes only.

## See Also

- [data/events/redis-streams.md](../../data/events/redis-streams.md) — Full event schema
- [architecture/communication.md](../../architecture/communication.md) — Communication patterns
