---
title: "Agent Gateway — Database"
category: services
tags: [agent-gateway, database, grpc, redis, data-models]
owner: "@backend"
last_updated: "2026-04-02"
source: repo
---

# Agent Gateway — Database

The agent-gateway has **no database**. All persistent data is accessed via gRPC calls to communication-service (which uses MongoDB). Redis is used for sessions, caching, bot configs, and stream state.

## Data Access Pattern

```
Client → Agent Gateway → gRPC → Communication Service → MongoDB
                       → Redis (sessions, cache, streams, bot configs)
```

The gateway never reads from or writes to a database directly. It acts as a pass-through, transforming HTTP requests into gRPC calls and returning the results.

---

## Data Models (Passed Through)

### Conversation

| Field | Type | Description |
|-------|------|-------------|
| `id` | string | Unique conversation ID |
| `userId` | string | Owner user ID |
| `orgId` | string | Organization ID |
| `agentId` | string | Associated agent ID |
| `chatType` | enum | `AGENT` or `GLOBAL` |
| `platform` | enum | `WEB`, `SMS`, `TELEGRAM`, `SLACK`, `EMAIL` |
| `status` | enum | `RUNNING`, `COMPLETED`, `WAITING_FOR_APPROVAL` |
| `title` | string | Conversation title |
| `inputTokens` | int | Total input tokens consumed |
| `outputTokens` | int | Total output tokens consumed |
| `summaryInfo` | object | Conversation summary metadata |
| `usageInfo` | object | Token/cost usage breakdown |
| `unique_identifier` | string | External platform identifier |
| `is_archived` | bool | Archive flag |
| `is_deleted` | bool | Soft delete flag |
| `is_read` | bool | Read/unread flag |
| `created_files` | list | Files created during conversation |
| `tasks` | list | Tasks associated with conversation |
| `createdAt` | datetime | Creation timestamp |
| `updatedAt` | datetime | Last update timestamp |

### Message

| Field | Type | Description |
|-------|------|-------------|
| `id` | string | Unique message ID |
| `conversationId` | string | Parent conversation ID |
| `senderType` | enum | `USER` or `ASSISTANT` |
| `userId` | string | Sender user ID |
| `orgId` | string | Organization ID |
| `data` | object | Raw message data |
| `result` | list | Structured result blocks |
| `user_message` | string | Original user message text |
| `agent_message` | list | Agent response blocks |
| `status` | enum | `RUNNING` or `COMPLETED` |
| `type` | enum | `CHAT` or `USER_MESSAGE` |
| `model` | string | LLM model used |
| `usageInfo` | object | Token/cost usage for this message |
| `sources` | list | Source references |
| `request_id` | string | Unique request identifier |
| `created_files` | list | Files created in this message |
| `createdAt` | datetime | Creation timestamp |
| `updatedAt` | datetime | Last update timestamp |

---

## Redis Usage

Redis serves multiple purposes in the gateway (none of which are persistent data storage):

| Purpose | Key Pattern | Description |
|---------|-------------|-------------|
| **Sessions** | `session:{token}` | JWT session validation — checked on every authenticated request |
| **Bot Configs** | `bot:{bot_id}` | Telegram/Slack bot configuration, read by listener service |
| **Conversation Cache** | `conv:{conv_id}` | Cached conversation data with configurable TTL |
| **Message Cache** | `msg:{conv_id}:*` | Cached message lists with configurable TTL |
| **Stream State** | `{env}:agent:chat:*` | Redis Streams for chat request/response flow |

### Cache Strategy

- Cache is populated on read (cache-aside pattern)
- Cache is invalidated on write (conversation update, message creation)
- TTL is configurable per cache type
- Cache misses fall through to gRPC calls to communication-service

---

## gRPC Services Called

| Service | Proto Package | Key RPCs |
|---------|--------------|----------|
| Communication Service | `communication` | `CreateConversation`, `GetConversation`, `UpdateConversation`, `DeleteConversation`, `ListConversations`, `GetMessages`, `CreateMessage`, `UpdateMessage` |
| User Service | `user` | `GetUser`, `ValidateUser` |
| Agent Service | `agent` | `GetAgent`, `ValidateAgent` |

## See Also

- [services/agent-gateway/overview.md](overview.md) — Service overview
- [services/agent-gateway/events.md](events.md) — Redis Streams events
- [services/communication-service/database.md](../communication-service/database.md) — MongoDB schemas (source of truth)
