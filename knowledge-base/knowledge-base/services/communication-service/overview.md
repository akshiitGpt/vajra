---
title: "Communication Service — Overview"
category: services
tags: [communication-service, grpc, mongodb, beanie, conversations, messages]
owner: "@backend"
last_updated: "2026-04-02"
source: repo
---

# Communication Service

Type: gRPC server
Repo: [repos/communication-service.md](../../repos/communication-service.md)
Language: Python 3.11
Framework: gRPC (grpcio 1.71.0, async server)
Database: MongoDB (Motor + Beanie ODM)
Package Manager: Poetry
Port: 50055

## What It Does

The communication service is the **persistence layer for Ruh AI's chat system**. It stores all conversation metadata, message content, token usage, workflow responses, file attachments, and versioned created files. It supports multi-platform conversations (Web, SMS, Telegram, Slack, Email).

It does NOT route messages to external platforms — it is a pure CRUD gRPC service called synchronously by the agent-gateway and other services.

## gRPC API

Single servicer: `CommunicationService`

### Conversation Methods

| RPC | Description |
|-----|-------------|
| `createConversation` | Create new or return existing empty conversation. Platform-specific reuse for Telegram/SMS via unique_identifier |
| `getConversation` | Get by ID with userId+orgId auth check |
| `getConversationByUniqueIdentifier` | Lookup by unique_identifier + orgId |
| `updateConversation` | Update title, summaryInfo, usageInfo, status, tokens, created_files |
| `deleteConversation` | Soft delete (is_deleted=true) |
| `deleteConversationsByAgentId` | Soft delete all for an agent |
| `listConversations` | Paginated with filters (chatType, agentId, platform, search, isDeleted, isArchived) |
| `fetchLatestConversationByUser` | Latest conversation, optionally by platform |
| `archiveConversation` | Toggle is_archived |
| `markConversationAsRead` | Set is_read |

### Message Methods

| RPC | Description |
|-----|-------------|
| `createMessage` | Create with data, result, user_message, agent_message, attachments, model, usageInfo, sources, created_files. Auto-sets conversation title from first message |
| `deleteMessage` | Hard delete |
| `listMessages` | Paginated with filters (messageType, senderType, status, dateRange, sortBy), field selection |
| `updateMessageWorkflowResponse` | Append workflow response data |
| `updateMessageStatus` | Change message status |
| `addCreatedFileVersion` | Append or restore file versions on message + conversation |

Health: Standard gRPC health checking (`grpc.health.v1.Health`)

## Database Schema (MongoDB)

### conversations collection

| Field | Type | Description |
|-------|------|-------------|
| userId | str (indexed) | Owner |
| orgId | str (indexed) | Organization |
| agentId | str (optional) | Agent ID |
| chatType | enum | CHAT_TYPE_AGENT / CHAT_TYPE_GLOBAL |
| platform | enum | WEB / SMS / TELEGRAM / SLACK / EMAIL |
| status | enum | COMPLETED / RUNNING / WAITING_FOR_APPROVAL |
| title | str | Auto-set from first message |
| inputTokens, outputTokens | int | Cumulative tokens |
| is_deleted | bool | Soft delete flag |
| is_archived | bool | Archive flag |
| is_read | bool | Read/unread |
| unique_identifier | str (optional) | Platform-specific (e.g., Telegram chat ID) |
| created_files | list | Versioned file entries: {file_id, file_name, content_type, current, versions} |

Compound indexes: `(userId, orgId, is_deleted, createdAt)`, `+ chatType`, `+ agentId`, `+ is_archived`

### messages collection

| Field | Type | Description |
|-------|------|-------------|
| conversationId | ObjectId (indexed) | Parent conversation |
| orgId, userId | str | Organization and sender |
| senderType | enum | USER / ASSISTANT |
| type | enum | CHAT / USER_MESSAGE |
| status | enum | RUNNING / COMPLETED |
| user_message | dict | Structured user message |
| agent_message | list | Structured agent response |
| model | embedded | {provider, name} |
| usageInfo | embedded | {inputTokens, outputTokens, totalTokens} |
| sources | dict | Knowledge base sources |
| request_id | str | Correlation ID |
| created_files | list | Versioned file entries |

Compound indexes: `(conversationId, createdAt)`, `+ type`, `+ senderType`, `(userId, orgId, conversationId, createdAt)`

## Events

None. This service does not produce or consume any events via Kafka, Redis Streams, or message brokers. It is called synchronously via gRPC.

## Proto Generation

Proto files are NOT stored in this repo. The `generate_grpc.py` script clones a separate proto repository (specified by `REPO_URL` env var) at build time and compiles `communication.proto`.

## Key Design Patterns

- **Soft deletes**: Conversations use `is_deleted` flag. Messages are hard-deleted.
- **Platform-specific reuse**: For Telegram/SMS/Slack/Email, one conversation per agent per platform per user (via `unique_identifier`). For Web, new conversations when existing ones have messages.
- **Authorization**: Every operation validates userId + orgId at the service layer.
- **File versioning**: Created files support append (new version) and restore (revert) modes, synced across message and conversation documents.
- **Observability**: Full OpenTelemetry integration exported to SigNoz, structured ASCII table logging, custom metrics manager.

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `GRPC_PORT` | 50055 | Server port |
| `MONGO_HOST` | localhost | MongoDB host |
| `MONGO_PORT` | 27017 | MongoDB port |
| `MONGO_DB_NAME` | communication-service | Database name |
| `MONGO_USERNAME` / `MONGO_PASSWORD` | (empty) | MongoDB auth |
| `REPO_URL` | (empty) | Proto repo for gRPC generation |
| `GIT_TOKEN` | (empty) | Git PAT for proto repo |
| `SIGNOZ_ENDPOINT` | http://localhost:4317 | SigNoz OTLP endpoint |
| `OTEL_ENABLED` | true | Enable OpenTelemetry |

## Migration Scripts

Located in `app/scripts/`:
- `migrate_add_is_archived_field.py` — Adds `is_archived: false` to existing conversations
- `migrate_add_is_deleted_field.py` — Adds `is_deleted` field
- `migrate_add_unique_identifier.py` — Adds `unique_identifier` field
- `migrate_backfill_org_id.py` — Backfills `orgId`
- `migrate_created_files_versioning.py` — Migrates created_files to versioned schema

## See Also

- [repos/communication-service.md](../../repos/communication-service.md) — Repo guide
- [architecture/service-map.md](../../architecture/service-map.md) — Where this fits
- [services/agent-gateway/dependencies.md](../agent-gateway/dependencies.md) — How agent-gateway calls this
