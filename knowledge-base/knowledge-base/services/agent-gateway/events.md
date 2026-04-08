---
title: "Agent Gateway — Events"
category: services
tags: [agent-gateway, events, redis-streams, sse, kafka]
owner: "@backend"
last_updated: "2026-04-02"
source: repo
---

# Agent Gateway — Events

The agent-gateway uses **Redis Streams** as the primary messaging layer for chat and **Kafka** as a secondary event bus.

---

## Redis Streams

### Produced Streams

| Stream | Description |
|--------|-------------|
| `{env}:agent:chat:requests` | User chat messages published for agent-platform to consume |

**Payload structure** (chat request):
```json
{
  "conversation_id": "conv_123",
  "request_id": "req_456",
  "user_id": "user_789",
  "org_id": "org_abc",
  "agent_id": "agent_def",
  "message": "User message text",
  "platform": "WEB",
  "chat_type": "AGENT"
}
```

### Consumed Streams

| Stream | Description |
|--------|-------------|
| `{env}:agent:chat:responses:{conv_id}-{req_id}` | Per-request response stream from agent-platform |

Each response stream is scoped to a single request. The gateway reads from it and forwards events as SSE to the client.

### Consumer Groups

| Group | Service | Stream |
|-------|---------|--------|
| `agent-processors` | agent-platform | `{env}:agent:chat:requests` |
| `api-gateway-consumers` | agent-gateway | `{env}:agent:chat:responses:*` |

### Stream Lifecycle

1. Client sends `POST /api/v1/agents/chat/stream`
2. Gateway publishes to `{env}:agent:chat:requests`
3. Agent-platform picks up from consumer group `agent-processors`
4. Agent-platform writes response chunks to `{env}:agent:chat:responses:{conv_id}-{req_id}`
5. Gateway reads from response stream via consumer group `api-gateway-consumers`
6. Gateway forwards each chunk as an SSE event to the client
7. On `stream_end`, the response stream is cleaned up

---

## SSE Event Types

Events flowing through Redis response streams and forwarded to clients via SSE:

| Event | Description | Key Fields |
|-------|-------------|------------|
| `stream_start` | Stream initialized | `conversation_id`, `request_id` |
| `message_start` | New assistant message began | `message_id` |
| `chunk` | Text delta | `content` (string) |
| `tool_use_start` | Tool invocation started | `tool_name`, `tool_id` |
| `tool_chunk` | Partial tool output | `content` (string) |
| `tool_result` | Tool execution complete | `tool_id`, `result`, `error` |
| `text_result` | Final text block | `content` (string) |
| `message_end` | Message complete | `usage_info` |
| `stream_end` | Stream finished normally | — |
| `stream_stop` | Stream stopped by user | — |
| `message_asset` | File/asset created | `asset_url`, `asset_type` |

### SSE Wire Format

```
event: chunk
data: {"content": "Here is the "}

event: chunk
data: {"content": "answer to your question."}

event: tool_use_start
data: {"tool_name": "web_search", "tool_id": "tool_1"}

event: tool_result
data: {"tool_id": "tool_1", "result": "Search results..."}

event: stream_end
data: {}
```

---

## Kafka Topics (Secondary)

Kafka is configured but secondary to Redis Streams for the core chat flow. Topics include:

| Topic | Direction | Description |
|-------|-----------|-------------|
| `agent_chat_requests` | Produce | Chat requests (mirror of Redis stream) |
| `agent_chat_responses` | Consume | Chat responses (mirror of Redis stream) |
| `agent_session_deletion_requests` | Produce | Session cleanup requests |
| `agent_task_requests` | Produce | Task execution requests |
| `orchestration_team_*` | Both | Multi-agent orchestration events |
| `human_input_*` | Both | Human-in-the-loop approval events |
| `workflow-responses` | Consume | Workflow execution results |

Kafka is used for:
- Event durability and replay
- Cross-service event fan-out beyond the chat flow
- Multi-agent orchestration coordination
- Workflow and task management

---

## Event Flow Diagram

```
                    Redis Streams                    SSE
Client ──POST──► Gateway ──publish──► agent:chat:requests
                                           │
                                     agent-platform
                                           │
                 Gateway ◄──read──── agent:chat:responses:{id}
Client ◄──SSE──┘
```

## See Also

- [services/agent-gateway/api.md](api.md) — SSE endpoint details
- [services/agent-gateway/overview.md](overview.md) — Service overview
- [services/agent-platform/events.md](../agent-platform/events.md) — Agent-platform event handling
- [data/events/redis-streams.md](../../data/events/redis-streams.md) — Redis Streams contracts
