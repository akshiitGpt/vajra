---
title: "Conversations Schema"
category: data
tags: [data, schema, conversations, mongodb, checkpoints]
owner: "@backend"
last_updated: "2026-03-31"
source: manual
---

# Conversations (Checkpoints)

## Storage

| Aspect | Details |
|--------|---------|
| Primary store | MongoDB (via `langgraph-checkpoint-mongodb`) |
| Alternative | PostgreSQL (via `langgraph-checkpoint-postgres`) |
| Selection | `CHECKPOINTER_BACKEND` env var |

## Checkpoint Structure

LangGraph checkpoints are managed automatically by the framework. Each checkpoint contains:

| Field | Type | Description |
|-------|------|-------------|
| `thread_id` | string | Unique conversation thread identifier |
| `checkpoint_ns` | string | Namespace for the checkpoint |
| `checkpoint_id` | string | Unique checkpoint version |
| `parent_checkpoint_id` | string | Previous checkpoint in the chain |
| `channel_values` | dict | Current state of all graph channels |
| `metadata` | dict | Execution metadata (step count, timestamps) |

### Channel Values (State)

The `channel_values` contain the actual conversation state:

- `messages` — Full message history (human, AI, tool calls, tool results)
- `current_subagent` — Which subagent is active (if any)
- `tool_results` — Pending tool execution results
- `node_positions` — Current position in the LangGraph graph

## Access Patterns

| Operation | When | Key |
|-----------|------|-----|
| Load checkpoint | Start of agent turn | `thread_id` |
| Save checkpoint | End of agent turn | `thread_id` + new `checkpoint_id` |
| List checkpoints | Conversation history | `thread_id` |

## See Also

- [services/agent-platform/database.md](../../services/agent-platform/database.md) — All data stores
- [data/schemas/agents.md](agents.md) — Agent configuration
