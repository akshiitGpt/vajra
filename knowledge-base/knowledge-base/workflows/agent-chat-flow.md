---
title: "Agent Chat Flow"
category: workflows
tags: [workflow, agent, chat, end-to-end]
owner: "@backend"
last_updated: "2026-03-31"
source: manual
---

# Agent Chat Flow

End-to-end lifecycle of a user chat message through the Ruh AI platform.

## Flow

```
1. User sends message via web/mobile/API
   │
2. API Gateway receives HTTP request
   │  Authenticates user, resolves org_id + agent_id
   │
3. API Gateway publishes to Redis Stream
   │  Stream: {env}:agent:chat:requests
   │  Payload: { org_id, agent_id, conversation_id, request_id, message }
   │
4. Agent Platform worker consumes the message
   │  (Consumer group distributes across workers)
   │
5. Worker fetches agent config from API Gateway
   │  GET {API_GATEWAY_URL}/agents/{agent_id}
   │  Returns: system prompt, enabled tools, model, MCP config
   │
6. Worker loads conversation checkpoint from MongoDB
   │  Key: thread_id (conversation_id)
   │  Contains: full message history, graph state
   │
7. Worker retrieves long-term memories from Qdrant
   │  Semantic search for relevant past memories
   │
8. LangGraph supervisor agent runs
   │  ├── Analyzes message + history + memories
   │  ├── Decides which subagent(s) to delegate to:
   │  │   ├── Knowledge Base → RAG search
   │  │   ├── Web Search → Tavily API
   │  │   ├── Code Executor → Daytona sandbox
   │  │   ├── File Manager → GCS operations
   │  │   └── MCP → External tools (Gmail, Jira, etc.)
   │  ├── Calls LLM via AI Gateway for each step
   │  └── Generates final response
   │
9. Response events stream to Redis
   │  Stream: {env}:agent:chat:responses:{conv_id}-{req_id}
   │  Events: STREAM_START → MESSAGE → TOOL_* → STREAM_END
   │
10. API Gateway consumes response stream
    │  Converts to SSE and streams to client
    │
11. Conversation checkpoint saved to MongoDB
    │
12. Long-term memories extracted and saved to Qdrant
    │
13. Analytics events published to Kafka
    │  Topics: agent-activity-events, token-usage-events
    │
14. User sees streamed response in UI
```

## Latency Breakdown (Typical)

| Step | Duration |
|------|----------|
| API Gateway → Redis publish | ~5ms |
| Redis → Worker consume | ~10ms |
| Agent config fetch | ~50ms |
| Checkpoint load | ~30ms |
| Memory retrieval | ~100ms |
| LLM inference (first token) | ~500ms–2s |
| LLM inference (full response) | ~2s–30s |
| Checkpoint save | ~30ms |
| **Total (first token)** | **~700ms–2.5s** |

## Error Handling

| Failure | Behavior |
|---------|----------|
| Worker crashes mid-execution | Message re-delivered to another worker (consumer group) |
| LLM call fails | Retry with exponential backoff, then error event to client |
| Checkpoint save fails | Error logged, conversation state may be stale on next turn |
| Max retries exceeded | Message sent to DLQ |

## See Also

- [architecture/data-flow.md](../architecture/data-flow.md) — Data lifecycle
- [architecture/communication.md](../architecture/communication.md) — Protocol details
- [services/agent-platform/overview.md](../services/agent-platform/overview.md) — Agent execution engine
- [data/events/redis-streams.md](../data/events/redis-streams.md) — Stream event schemas
