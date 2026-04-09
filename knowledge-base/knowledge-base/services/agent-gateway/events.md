---
title: "Agent Gateway — Events"
category: services
tags: [agent-gateway, events, kafka, sse]
owner: "@backend"
last_updated: "2026-04-09"
source: repo
---

# Agent Gateway — Events

The agent-gateway uses **Kafka** as the primary messaging layer for chat and **Redis** for caching, sessions, and bot config.

---

## Kafka (Chat — Primary)

### Produced Topics

| Topic | Key | Description |
|-------|-----|-------------|
| `agent_chat_requests` | `conversation_id` | User chat messages published for agent-platform to consume |

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

### Consumed Topics

| Topic | Key | Description |
|-------|-----|-------------|
| `agent_chat_responses` | `request_id` | Streamed response events from agent-platform |

Each gateway instance runs its own independent consumer (unique group ID per instance, e.g., `api-gateway-{hostname}`) so every instance sees all response messages. Messages are routed to active SSE connections by `request_id`.

### Chat Lifecycle

1. Client sends `POST /api/v1/agents/chat/stream`
2. Gateway publishes to Kafka topic `agent_chat_requests`
3. Agent-platform picks up from consumer group `agent-processors`
4. Agent-platform publishes response events to `agent_chat_responses` (key: `request_id`)
5. Gateway's background Kafka consumer reads all response messages
6. Gateway matches `request_id` to active SSE connection and enqueues event
7. Gateway forwards each event as SSE to the client
8. On `STREAM_END`, the request_id is deregistered

---

## SSE Event Types

Events flowing through Kafka response topic and forwarded to clients via SSE:

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

## Kafka Topics (Other)

Additional Kafka topics beyond the core chat flow:

| Topic | Direction | Description |
|-------|-----------|-------------|
| `agent_session_deletion_requests` | Produce | Session cleanup requests |
| `agent_task_requests` | Produce | Task execution requests |
| `orchestration_team_*` | Both | Multi-agent orchestration events |
| `human_input_*` | Both | Human-in-the-loop approval events |
| `workflow-responses` | Consume | Workflow execution results |

---

## Event Flow Diagram

```
                      Kafka                          SSE
Client ──POST──► Gateway ──produce──► agent_chat_requests
                                           │
                                     agent-platform
                                           │
                 Gateway ◄──consume── agent_chat_responses
Client ◄──SSE──┘
```

## See Also

- [services/agent-gateway/api.md](api.md) — SSE endpoint details
- [services/agent-gateway/overview.md](overview.md) — Service overview
- [services/agent-platform/events.md](../agent-platform/events.md) — Agent-platform event handling
- [data/events/kafka-events.md](../../data/events/kafka-events.md) — Kafka event contracts
