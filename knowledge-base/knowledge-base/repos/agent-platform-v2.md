---
title: "agent-platform-v2 Repo"
category: repos
tags: [repo, agent-platform, python, poetry, langgraph, redis-streams]
owner: "@backend"
last_updated: "2026-04-02"
source: repo
repo_url: "https://github.com/ruh-ai/agent-platform-v2"
---

# agent-platform-v2

GitHub: `github.com/ruh-ai/agent-platform-v2`

## Directory Structure

```
app/
  main.py                        # Entry point: init services, start Redis listeners
  agent/
    run_agent.py                 # Main agent execution orchestrator (async generator)
    model.py                     # Chat model factory (provider selection)
    state.py                     # Agent state TypedDict (Todo)
    config/                      # ChatOpenAI wrapper, Mem0 config
    utils/                       # Message utils, summarization, MCP client
    subagents/                   # 5 subagents: KB, web, code, files, MCP
    tools/                       # 21+ tools (memory, web, files, image/audio/video, sandbox, browser, scheduler)
    system_prompts/              # Global + custom prompts, 20+ usage instruction files
  services/
    redis_streams.py             # Redis Streams producer/consumer with retry + DLQ
    redis_listener.py            # Main event listener (routes requests to agent/workflow)
    kafka_producer.py            # Kafka event publishing
    agent_fetch.py               # Fetches agent config from API Gateway
    ruh_agent_checkpointer.py    # Agent state checkpointing
    db_client.py                 # MongoDB + PostgreSQL initialization
    stream_formatter.py          # LangGraph events → SSE format
    daytona_service.py           # Sandbox provisioning
    http_client.py               # Global async HTTP client pool
    browser_task_manager.py      # Browser automation orchestration
  shared/config/
    base.py                      # Settings dataclass (all env vars)
    constants.py                 # Enums: RedisStreamEnum, EventType, AgentToolEnum
    logging_config.py            # Structured logging + OTEL correlation
    telemetry.py                 # OpenTelemetry setup
  utils/                         # Tracing, metrics, exceptions, Redis state store, GCS, MCP
    mcp_client.py                # MCP (Model Context Protocol) client
  helper/                        # Stop signals, Daytona state, background tasks
  workflow_generation_graph/     # LangGraph workflow generation
  workflow_chat/                 # LangGraph workflow chat
tests/                           # ~90 test files mirroring app/ structure
helm/                            # Kubernetes Helm charts (dev, qa, main)
scripts/                         # Utility scripts (test, cleanup, migration)
```

## Entry Point

`app/main.py` — initializes all service clients, sets up telemetry, then starts two concurrent async listeners:
1. `listen_event_from_redis_pubsub()` — Main Redis Streams consumer
2. `listen_stop_events_from_redis_streams()` — Stop signal listener

## Local Development

```bash
# Prerequisites: Python 3.11+, Poetry, Docker (for Redis/Mongo/Qdrant)
git clone https://github.com/ruh-ai/agent-platform-v2
cd agent-platform-v2
cp .env.example .env            # Fill in API keys and service URLs
poetry install
poetry run python -m app.main
```

## Key Files to Read

| File | Purpose |
|------|---------|
| `app/main.py` | Bootstrap and startup |
| `app/agent/run_agent.py` | Core agent execution logic |
| `app/services/redis_listener.py` | Request routing |
| `app/services/redis_streams.py` | Stream producer/consumer |
| `app/shared/config/base.py` | All configuration / env vars |
| `app/shared/config/constants.py` | Enums and constants |

## Testing

```bash
make test-unit                             # Unit tests
make test-integration                      # Integration tests
make lint                                  # Linting
make typecheck                             # Type checking
poetry run pytest tests/ -k "test_redis"   # Pattern match
```

## See Also

- [services/agent-platform/overview.md](../services/agent-platform/overview.md) — Service-level docs
