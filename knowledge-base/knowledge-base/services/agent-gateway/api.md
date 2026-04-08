---
title: "Agent Gateway — API"
category: services
tags: [agent-gateway, api, rest, sse, fastapi, jwt, api-key]
owner: "@backend"
last_updated: "2026-04-02"
source: repo
---

# Agent Gateway — API

Base URL: `http://localhost:8001`
All endpoints prefixed with `/api/v1`
Framework: FastAPI (Python 3.13)

## Authentication

Two auth modes:

1. **JWT Bearer Token** — `Authorization: Bearer <token>`. Validated against Redis sessions. Used by the web app.
2. **API Key** — `x-api-key: <key>` header. Used by external integrations and SDR platforms. Dedicated `/api-key` endpoint variants.

---

## Agent Management (`/agents`)

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/agents/chat/stream` | JWT | SSE streaming chat |
| `POST` | `/agents/chat/stream/api-key` | API key | SSE streaming chat |
| `POST` | `/agents/chat/stream/resume` | JWT | Resume a disconnected SSE stream |
| `POST` | `/agents/chat/stream/stop` | JWT | Stop an active stream |
| `POST` | `/agents/chat/stream/browser-pause` | JWT | Pause browser automation mid-stream |

### POST /agents/chat/stream

Primary chat endpoint. Publishes user message to Redis Stream `{env}:agent:chat:requests`, then reads from the per-request response stream `{env}:agent:chat:responses:{conv_id}-{req_id}` and forwards chunks as SSE.

**SSE Event Types:**

| Event | Description |
|-------|-------------|
| `stream_start` | Stream initialized |
| `message_start` | New message began |
| `chunk` | Text delta |
| `tool_use_start` | Tool invocation started |
| `tool_chunk` | Partial tool output |
| `tool_result` | Tool execution complete |
| `text_result` | Final text block |
| `message_end` | Message complete |
| `stream_end` | Stream finished normally |
| `stream_stop` | Stream stopped by user |
| `message_asset` | File/asset created during message |

---

## Communication (`/communication`)

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/communication/conversation` | JWT | Create conversation |
| `POST` | `/communication/conversation/api-key` | API key | Create conversation |
| `GET` | `/communication/conversation/by-unique-identifier/{id}` | JWT | Find by unique ID |
| `GET` | `/communication/conversation/{id}` | JWT | Get conversation |
| `DELETE` | `/communication/conversation/{id}` | JWT | Delete conversation |
| `PATCH` | `/communication/conversation/{id}` | JWT | Update conversation |
| `PATCH` | `/communication/conversation/{id}/archive` | JWT | Archive/unarchive |
| `PATCH` | `/communication/conversation/{id}/read` | JWT | Mark read/unread |
| `DELETE` | `/communication/conversations/agent/{agent_id}` | JWT | Delete all conversations for agent |
| `GET` | `/communication/conversations` | JWT | List conversations (paginated) |
| `GET` | `/communication/messages/{conversation_id}` | JWT | List messages (paginated) |

All conversation/message operations are proxied via gRPC to communication-service (MongoDB-backed).

---

## AI Services (`/ai-services`)

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/ai-services/chat-suggestions` | JWT | Generate chat suggestions |
| `POST` | `/ai-services/generate-instructions` | JWT | Generate/enhance agent instructions |
| `POST` | `/ai-services/improve-user-prompt` | JWT | Improve user prompt |

Proxied via HTTP to AI Gateway (OpenRouter-backed).

---

## Webhooks (`/webhook`)

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/webhook/telegram?bot_id=...` | None (Telegram signature) | Inbound Telegram webhook |
| `POST` | `/webhook/send-platform-message` | API key | Outbound platform message |

### POST /webhook/telegram

Receives Telegram updates, parses the message, creates or retrieves a conversation via gRPC, publishes the user message to Redis Streams, reads the agent response, and sends it back to the Telegram user.

---

## Bot Management (`/bots`)

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/bots/start` | JWT | Start Telegram bot |
| `POST` | `/bots/stop` | JWT | Stop Telegram bot |
| `GET` | `/bots/{bot_id}` | JWT | Get bot details |
| `GET` | `/bots` | JWT | List all bots |
| `POST` | `/bots/start/slack` | JWT | Start Slack app |
| `POST` | `/bots/stop/slack` | JWT | Stop Slack app |

Bot configs are written to Redis. A separate grammY/Bolt.js listener service picks up changes and manages the actual bot connections.

---

## File Conversion (`/file-conversion`)

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/file-conversion/convert` | JWT | Convert markdown to PDF/DOCX |
| `POST` | `/file-conversion/convert/api-key` | API key | Convert markdown to PDF/DOCX |
| `POST` | `/file-conversion/upload-next-version` | JWT | GCS file versioning |

Proxied via HTTP to file-conversion service.

---

## Error Handling

All error responses follow a consistent JSON structure:

```json
{
  "detail": "Error description",
  "status_code": 401
}
```

Standard HTTP status codes: 400 (bad request), 401 (unauthorized), 403 (forbidden), 404 (not found), 500 (internal error).

## See Also

- [services/agent-gateway/overview.md](overview.md) — Service overview
- [services/agent-gateway/events.md](events.md) — Redis Streams + SSE event details
- [services/agent-gateway/database.md](database.md) — Data models passed through
- [data/events/redis-streams.md](../../data/events/redis-streams.md) — Redis Streams contracts
