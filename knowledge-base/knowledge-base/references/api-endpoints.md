---
title: "API Endpoints"
category: references
tags: [reference, api, endpoints]
owner: "@backend"
last_updated: "2026-04-02"
source: repo
---

# API Endpoints

## Agent Gateway — Port 8001

All endpoints prefixed with `/api/v1`. Auth: JWT Bearer or API key unless noted.

### Agent (Chat Streaming)

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/agents/chat/stream` | JWT | SSE streaming chat |
| `POST` | `/agents/chat/stream/api-key` | API key | SSE streaming chat |
| `POST` | `/agents/chat/stream/resume` | JWT | Resume disconnected stream |
| `POST` | `/agents/chat/stream/stop` | JWT | Stop streaming |
| `POST` | `/agents/chat/stream/browser-pause` | JWT | Pause browser automation |

### Communication (Conversations & Messages)

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/communication/conversation` | JWT | Create conversation |
| `POST` | `/communication/conversation/api-key` | API key | Create conversation |
| `GET` | `/communication/conversation/{id}` | JWT | Get conversation |
| `GET` | `/communication/conversation/by-unique-identifier/{uid}` | JWT | Find by unique ID |
| `DELETE` | `/communication/conversation/{id}` | JWT | Delete conversation |
| `PATCH` | `/communication/conversation/{id}` | JWT | Update conversation |
| `PATCH` | `/communication/conversation/{id}/archive` | JWT | Archive/unarchive |
| `PATCH` | `/communication/conversation/{id}/read` | JWT | Mark read/unread |
| `DELETE` | `/communication/conversations/agent/{agent_id}` | JWT | Delete by agent |
| `GET` | `/communication/conversations` | JWT | List conversations (paginated) |
| `GET` | `/communication/messages/{conversation_id}` | JWT | List messages (paginated) |

### AI Services

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/ai-services/chat-suggestions` | JWT | Generate response suggestions |
| `POST` | `/ai-services/generate-instructions` | JWT | Generate/enhance agent instructions |
| `POST` | `/ai-services/improve-user-prompt` | JWT | Improve user prompt |

### Webhooks

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/webhook/telegram?bot_id=...` | None | Telegram inbound webhook |
| `POST` | `/webhook/send-platform-message` | API key | Outbound platform message |

### Bot Management

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/bots/start` | JWT | Start Telegram bot |
| `POST` | `/bots/stop` | JWT | Stop Telegram bot |
| `GET` | `/bots/{bot_id}` | JWT | Get bot details |
| `GET` | `/bots` | JWT | List all bots |
| `POST` | `/bots/start/slack` | JWT | Start Slack app |
| `POST` | `/bots/stop/slack` | JWT | Stop Slack app |

### File Conversion

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/file-conversion/convert` | JWT | Convert markdown to PDF/DOCX |
| `POST` | `/file-conversion/convert/api-key` | API key | Convert (API key auth) |
| `POST` | `/file-conversion/upload-next-version` | JWT | GCS file versioning |

## Agent Platform — Port 6000

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/health` | Kubernetes health check (only endpoint) |

All other communication via Redis Streams. See [data/events/redis-streams.md](../data/events/redis-streams.md).

## AI Gateway — Port 8000

Auth: `X-Server-Auth` header (internal) or `external-api-key` header.

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/health` | Health check |
| `POST` | `/v1/chat/completions` | LLM chat completion (streaming + non-streaming) |
| `POST` | `/v1/embeddings` | Embeddings |
| `POST` | `/v1/exa/search` | Exa web search |
| `POST` | `/v1/exa/contents` | Exa content extraction |
| `POST` | `/v1/people/match` | Apollo people enrichment |
| `POST` | `/v1/people/bulk_match` | Apollo bulk enrichment |
| `POST` | `/v1/mixed_people/search` | Apollo people search |
| `POST` | `/v1/mixed_companies/search` | Apollo company search |
| `POST` | `/v1/stt/deepgram` | Deepgram batch STT |
| `WS` | `/v1/stt/deepgram` | Deepgram streaming STT |
| `POST` | `/v1/tts/voice:stream` | Inworld TTS |
| `POST` | `/v1/tts/elevenlabs/text-to-speech/{voice_id}/stream` | ElevenLabs TTS |
| `WS` | `/v1/tts/elevenlabs/text-to-speech/{voice_id}/multi-stream-input` | ElevenLabs WS |
| `POST` | `/v1/tts/cartesia/tts/bytes` | Cartesia TTS |
| `WS` | `/v1/tts/cartesia/tts/websocket` | Cartesia TTS WS |
| `POST` | `/v1/video/generate` | PiAPI video generation (blocking) |
| `POST` | `/v1/video/create` | PiAPI video (non-blocking) |
| `GET` | `/v1/video/status/{task_id}` | Video task status |
| `GET` | `/v1/admin/credits` | Combined credits |
| `GET` | `/v1/admin/openrouter-credits` | OpenRouter balance |
| `GET` | `/v1/admin/apollo-credits` | Apollo credits |
| `POST` | `/v1/admin/apollo-credits` | Add Apollo credits |

## Communication Service — Port 50055 (gRPC)

Single servicer: `CommunicationService`. See [services/communication-service/overview.md](../services/communication-service/overview.md) for full RPC list.

| RPC | Description |
|-----|-------------|
| `createConversation` | Create or return existing empty conversation |
| `getConversation` | Get by ID |
| `listConversations` | Paginated list with filters |
| `updateConversation` | Update metadata |
| `deleteConversation` | Soft delete |
| `createMessage` | Create message |
| `listMessages` | Paginated list with filters |
| `addCreatedFileVersion` | File versioning |
| `Health.Check` | gRPC health check |

## File Conversion Service — Port 8080

Auth: `X-Server-Auth-Key` header.

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/healthz` | Health check (no auth) |
| `POST` | `/convert` | Convert markdown to PDF/DOCX via GCS |

## See Also

- [services/agent-gateway/api.md](../services/agent-gateway/api.md) — Detailed Agent Gateway API
- [services/agent-platform/api.md](../services/agent-platform/api.md) — Redis Streams interface
- [services/ai-gateway/overview.md](../services/ai-gateway/overview.md) — AI Gateway details
- [services/communication-service/overview.md](../services/communication-service/overview.md) — gRPC API details
