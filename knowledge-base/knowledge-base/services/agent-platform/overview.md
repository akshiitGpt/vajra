---
title: "Agent Platform — Overview"
category: services
tags: [agent-platform, python, langgraph, redis-streams, deepagents]
owner: "@backend"
last_updated: "2026-04-02"
source: repo
---

# Agent Platform

Type: Background worker (headless, no HTTP API)
Repo: [repos/agent-platform-v2.md](../../repos/agent-platform-v2.md)
Language: Python 3.11+
Framework: LangGraph, LangChain, DeepAgents
Package Manager: Poetry

## What It Does

The agent-platform is the **core AI agent execution engine** for Ruh AI. It is a headless, event-driven worker that:

1. Consumes chat requests from Redis Streams
2. Routes them to a multi-agent system (supervisor + specialized subagents)
3. Calls LLMs via AI Gateway
4. Streams responses back via Redis Streams
5. Persists conversation state in MongoDB/PostgreSQL
6. Stores long-term memory in Qdrant

It has **no HTTP API endpoints** for external clients. The only HTTP endpoint is `/health` on port 6000 for Kubernetes liveness checks.

## Subagent Architecture

The supervisor agent delegates to five specialized subagents:

| Subagent | Purpose | Key Tools |
|----------|---------|-----------|
| **Knowledge Base** | Search internal docs | RAG, document retrieval |
| **Web Search** | Search the internet | Tavily API |
| **Code Executor** | Run code in sandboxes | Daytona sandbox, bash execution |
| **File Manager** | Create/read/modify files | File I/O, GCS upload |
| **MCP** | External tool integrations | Gmail, Jira, Slack, GitHub via MCP protocol |

Additional tools (21+): todos, memory, image/audio/video generation, browser automation, scheduler.

## Key Configuration

| Config | Source | Purpose |
|--------|--------|---------|
| Agent config | Fetched from API Gateway at runtime | System prompt, tools, model selection |
| Model provider | `MODEL_PROVIDER` env var | Which LLM provider to use |
| Checkpointer | `CHECKPOINTER_BACKEND` env var | MongoDB or PostgreSQL |
| Environment | `ENV` env var (dev/qa/main) | Prefixes all Redis stream names |

## Scaling

- Horizontally scales via Redis Streams consumer groups
- Each worker joins the same consumer group — Redis distributes messages automatically
- Stateless workers — all state lives in Redis/MongoDB/Qdrant
- CPU-bound on LLM response processing; I/O-bound on LLM API calls

## See Also

- [services/agent-platform/api.md](api.md) — Redis Streams interface
- [services/agent-platform/database.md](database.md) — Data storage
- [services/agent-platform/events.md](events.md) — Events produced/consumed
- [services/agent-platform/dependencies.md](dependencies.md) — External dependencies
- [workflows/agent-chat-flow.md](../../workflows/agent-chat-flow.md) — End-to-end request flow
