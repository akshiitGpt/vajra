---
title: "Agent Platform — Database"
category: services
tags: [agent-platform, database, mongodb, qdrant, redis]
owner: "@backend"
last_updated: "2026-04-02"
source: repo
---

# Agent Platform — Database

The agent-platform uses four data stores, each for a different purpose.

## MongoDB (Conversation Checkpoints)

Primary checkpoint store via `langgraph-checkpoint-mongodb`.

| Aspect | Details |
|--------|---------|
| Purpose | LangGraph conversation state snapshots |
| Connection | `MONGO_DB_URL` env var |
| Library | `langgraph-checkpoint-mongodb` (MongoDBSaver) |
| Key | `thread_id` (includes conversation context) |
| Write pattern | One checkpoint per agent turn |
| Read pattern | Load latest checkpoint at conversation start |

Checkpoints contain: message history, tool call results, subagent state, and graph node positions.

## PostgreSQL (Alternative Checkpoint Backend)

Selectable via `CHECKPOINTER_BACKEND=postgres`.

| Aspect | Details |
|--------|---------|
| Purpose | Alternative LangGraph checkpoint store |
| Connection | `POSTGRES_CHECKPOINTER_URL` env var |
| Library | `langgraph-checkpoint-postgres` |
| When to use | When MongoDB is not available or for testing |

## Qdrant (Vector Memory)

Long-term memory via Mem0.

| Aspect | Details |
|--------|---------|
| Purpose | User and agent memories as vector embeddings |
| Connection | `QDRANT_HOST:QDRANT_PORT`, `QDRANT_API_KEY` |
| Collection | `langgraph_memory` |
| Metadata filters | `user_id`, `organisation_id`, `agent_id`, `conversation_id` |
| Write pattern | After agent turn (stores notable facts) |
| Read pattern | Semantic search at conversation start |

## Redis (Transient State)

Ephemeral state for in-flight requests.

| Aspect | Details |
|--------|---------|
| Purpose | Sources, approval data, created files, pagination, stop signals |
| Connection | `REDIS_HOST:REDIS_PORT` |
| TTL | 30 minutes |
| Key pattern | `{env}:{data_type}:{conversation_id}` |
| Library | `app/utils/agent_redis_store.py` |

This data is disposable — it only exists during active agent execution.

## See Also

- [data/schemas/conversations.md](../../data/schemas/conversations.md) — Checkpoint schema details
- [data/schemas/agents.md](../../data/schemas/agents.md) — Agent configuration schema
