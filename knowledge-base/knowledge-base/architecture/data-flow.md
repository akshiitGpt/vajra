---
title: "Data Flow"
category: architecture
tags: [architecture, data-flow, lifecycle]
owner: "@team-lead"
last_updated: "2026-03-31"
source: manual
---

# Data Flow

## Agent Chat Request Lifecycle

The primary data path through the platform:

```
1. User sends message
   │
2. API Gateway receives HTTP request
   │
3. API Gateway publishes to Redis Stream
   │  Stream: {env}:agent:chat:requests
   │  Payload: { org_id, agent_id, conversation_id, request_id, message, ... }
   │
4. Agent Platform consumes from Redis Stream (consumer group)
   │
5. Agent Platform fetches agent config from API Gateway
   │  GET {API_GATEWAY_URL}/agents/{agent_id}
   │
6. LangGraph supervisor agent processes the message
   │  ├── Loads conversation checkpoint from MongoDB/PostgreSQL
   │  ├── Retrieves long-term memory from Qdrant (Mem0)
   │  ├── Delegates to subagents as needed:
   │  │   ├── Knowledge base search
   │  │   ├── Web search (Tavily)
   │  │   ├── Code execution (Daytona sandbox)
   │  │   ├── File management
   │  │   └── MCP tools (Gmail, Jira, Slack, etc.)
   │  ├── Calls LLM via AI Gateway
   │  └── Streams response tokens
   │
7. Agent Platform publishes response events to Redis Stream
   │  Stream: {env}:agent:chat:responses:{conversation_id}-{request_id}
   │  Events: STREAM_START → MESSAGE → TOOL_START → ... → STREAM_END
   │
8. API Gateway consumes response stream
   │
9. API Gateway streams SSE to client
   │
10. Conversation checkpoint saved to MongoDB/PostgreSQL
    │
11. Analytics events published to Kafka
    Topics: agent-activity-events, token-usage-events
```

## Data Storage Locations

| Data | Store | Retention | Access Pattern |
|------|-------|-----------|---------------|
| Conversation checkpoints | MongoDB or PostgreSQL | Indefinite | Read/write per agent turn |
| Long-term memory | Qdrant (vectors) | Indefinite | Semantic search per request |
| Transient agent state | Redis (key-value) | 30-min TTL | Read/write during agent execution |
| Request/response streams | Redis Streams | Consumer-group acknowledged | Produce/consume per request |
| Generated files | Google Cloud Storage | Indefinite | Write during agent execution |
| Analytics events | Kafka → downstream | Configurable | Append-only, consumed by analytics |

## Data Consistency Model

- **Conversation state**: Strongly consistent via LangGraph checkpointer (MongoDB/PostgreSQL transaction per checkpoint).
- **Memory**: Eventually consistent — Qdrant writes are async, reads may lag slightly.
- **Redis state**: Ephemeral — 30-minute TTL. Used for in-flight request data only.
- **Analytics**: At-least-once delivery via Kafka. Consumers must handle duplicates.

## Multi-Tenancy

Data is isolated by `organisation_id` and `agent_id`:

- Redis Streams: Shared streams, but each message carries `org_id` and `agent_id`
- MongoDB: Checkpoints keyed by `thread_id` (includes org/agent context)
- Qdrant: Collections filtered by `organisation_id`, `agent_id`, `user_id` metadata
- Kafka: Events carry `org_id` for downstream filtering

## See Also

- [architecture/communication.md](communication.md) — Protocol details
- [data/events/redis-streams.md](../data/events/redis-streams.md) — Stream event schemas
- [data/schemas/conversations.md](../data/schemas/conversations.md) — Checkpoint schema
- [workflows/agent-chat-flow.md](../workflows/agent-chat-flow.md) — Step-by-step walkthrough
