---
title: "Knowledge Base Index"
category: index
tags: [index, navigation, toc]
owner: "system"
last_updated: "2026-04-02"
source: manual
---

# Ruh AI — Knowledge Base Index

## Architecture
- [architecture/overview.md](architecture/overview.md) — High-level platform overview
- [architecture/system-context.md](architecture/system-context.md) — External boundaries and actors
- [architecture/service-map.md](architecture/service-map.md) — All services and their relationships
- [architecture/communication.md](architecture/communication.md) — How services communicate (Redis Streams, gRPC, REST)
- [architecture/data-flow.md](architecture/data-flow.md) — How data moves through the system

## Services
- [services/agent-platform/](services/agent-platform/overview.md) — Core AI agent execution engine (Python, LangGraph, Redis Streams)
- [services/agent-gateway/](services/agent-gateway/overview.md) — API Gateway — HTTP entry point (Python, FastAPI, gRPC, SSE)
- [services/communication-service/](services/communication-service/overview.md) — Conversation/message persistence (Python, gRPC, MongoDB)
- [services/ai-gateway/](services/ai-gateway/overview.md) — API proxy for LLM, search, TTS, STT, video (Python, FastAPI)
- [services/file-conversion/](services/file-conversion/overview.md) — Markdown to PDF/DOCX conversion (Python, FastAPI, Playwright)

## Repos
- [repos/agent-platform-v2.md](repos/agent-platform-v2.md) — Code-level repo guide
- [repos/agent-gateway.md](repos/agent-gateway.md) — Code-level repo guide
- [repos/communication-service.md](repos/communication-service.md) — Code-level repo guide
- [repos/ai-gateway.md](repos/ai-gateway.md) — Code-level repo guide
- [repos/file-conversion.md](repos/file-conversion.md) — Code-level repo guide
- [repos/ruh-super-admin-fe.md](repos/ruh-super-admin-fe.md) — Next.js 15 super admin dashboard (TypeScript, Radix UI, Zustand, RBAC)

## Data
- [data/schemas/conversations.md](data/schemas/conversations.md) — Conversation checkpoints (MongoDB/PostgreSQL)
- [data/schemas/agents.md](data/schemas/agents.md) — Agent configuration and state
- [data/events/redis-streams.md](data/events/redis-streams.md) — Redis Streams event contracts
- [data/events/kafka-events.md](data/events/kafka-events.md) — Kafka analytics events
- [data/events/websocket-events.md](data/events/websocket-events.md) — WebSocket event contracts
- [data/pipelines/analytics.md](data/pipelines/analytics.md) — Analytics data pipeline

## Infrastructure
- [infra/kubernetes.md](infra/kubernetes.md) — Cluster setup, namespaces, Helm charts
- [infra/ci-cd.md](infra/ci-cd.md) — CI/CD pipeline
- [infra/environments.md](infra/environments.md) — Dev, QA, production environments
- [infra/observability.md](infra/observability.md) — Monitoring, tracing, logging

## Workflows
- [workflows/agent-chat-flow.md](workflows/agent-chat-flow.md) — End-to-end agent chat request lifecycle
- [workflows/agent-creation-flow.md](workflows/agent-creation-flow.md) — How a new agent sandbox is provisioned
- [workflows/workflow-execution-flow.md](workflows/workflow-execution-flow.md) — Multi-step workflow orchestration

## Runbooks
- [runbooks/deployments.md](runbooks/deployments.md) — Deploy to production
- [runbooks/incident-response.md](runbooks/incident-response.md) — Incident handling procedure
- [runbooks/debugging.md](runbooks/debugging.md) — Common debugging procedures

## References
- [references/api-endpoints.md](references/api-endpoints.md) — All API endpoints across services
- [references/environment-variables.md](references/environment-variables.md) — All env vars across services
- [references/feature-flags.md](references/feature-flags.md) — Feature flags and toggles
