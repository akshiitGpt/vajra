---
title: "Agent Platform — Dependencies"
category: services
tags: [agent-platform, dependencies]
owner: "@backend"
last_updated: "2026-04-02"
source: repo
---

# Agent Platform — Dependencies

## Service Dependencies

| Service | Protocol | Env Var | Purpose |
|---------|----------|---------|---------|
| API Gateway | HTTP | `API_GATEWAY_URL` | Fetch agent configs |
| AI Gateway | HTTP | `AI_GATEWAY_BASE_URL` | LLM inference routing |
| Agent Gateway | HTTP | `AGENT_GATEWAY_URL` | Agent-specific gateway |
| MCP Gateway | HTTP | `MCP_GATEWAY_URL` | External tool execution (Gmail, Jira, etc.) |
| Communication Service | HTTP | `COMMUNICATION_CHANNEL_MANAGER_URL` | Cross-platform messaging |
| Scheduler Service | HTTP | `SCHEDULER_API_BASE_URL` | Scheduled task management |
| Workflow Execution Service | HTTP | `WORKFLOW_API_GATEWAY_URL` | Workflow orchestration |
| Daytona | HTTP | `DAYTONA_API_URL` | Cloud sandbox provisioning |
| Browser-use Cloud | HTTP | `BROWSER_USE_BASE_URL` | Browser automation |

## Infrastructure Dependencies

| Component | Protocol | Env Var | Purpose |
|-----------|----------|---------|---------|
| Redis | TCP | `REDIS_HOST:REDIS_PORT` | Streams + state cache |
| MongoDB | TCP | `MONGO_DB_URL` | Conversation checkpoints |
| PostgreSQL | TCP | `POSTGRES_CHECKPOINTER_URL` | Alt checkpoint backend |
| Qdrant | HTTP | `QDRANT_HOST:QDRANT_PORT` | Vector memory (Mem0) |
| Kafka | TCP | `KAFKA_BOOTSTRAP_SERVERS` | Analytics events |
| Google Cloud Storage | HTTP | `GCS_BUCKET_NAME` | File storage |
| SigNoz | gRPC | `SIGNOZ_ENDPOINT` | OpenTelemetry traces/metrics |

## External API Dependencies

| Provider | Purpose | Env Var |
|----------|---------|---------|
| OpenRouter | LLM routing | `OPENROUTER_API_KEY` |
| OpenAI | Direct LLM access | `OPENAI_API_KEY` |
| Anthropic | Direct Claude access | `ANTHROPIC_API_KEY` |
| Google Gemini | Gemini models | via API key |
| Tavily | Web search | via `langchain-tavily` |
| Langfuse | LLM observability | via langfuse SDK |

## Failure Impact

| Dependency | If Down | Impact |
|------------|---------|--------|
| Redis | **Critical** | No requests can be received or responded to |
| MongoDB/PostgreSQL | **Critical** | No conversation state — agents can't resume |
| AI Gateway / LLM Provider | **Critical** | Agents can't generate responses |
| Qdrant | Degraded | No long-term memory — agents still function |
| Kafka | Degraded | Analytics lost — agents still function |
| MCP Gateway | Degraded | External tools unavailable — core chat works |
| Daytona | Degraded | Code execution unavailable — core chat works |

## See Also

- [architecture/service-map.md](../../architecture/service-map.md) — Full dependency graph
- [references/environment-variables.md](../../references/environment-variables.md) — All env vars
