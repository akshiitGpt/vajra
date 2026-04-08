---
title: "Observability"
category: infra
tags: [infra, observability, monitoring, logging, tracing]
owner: "@devops"
last_updated: "2026-03-31"
source: manual
---

# Observability

## Stack

| Layer | Tool | Purpose |
|-------|------|---------|
| Traces | SigNoz (OpenTelemetry) | Distributed request tracing |
| Metrics | SigNoz | Application and infra metrics |
| Logs | Structured JSON logging | Centralized log aggregation |
| LLM observability | Langfuse | LLM call tracing, latency, token usage |
| Error tracking | <!-- Sentry / custom --> | Application error tracking |

## OpenTelemetry Setup (Agent Platform)

Configured in `app/shared/config/telemetry.py`:

| Config | Env Var | Default |
|--------|---------|---------|
| Enable OTEL | `OTEL_ENABLED` | `false` |
| SigNoz endpoint | `SIGNOZ_ENDPOINT` | — |
| Service name | `OTEL_SERVICE_NAME` | `agent-platform` |

Traces include:
- Redis Stream consume/produce spans
- LLM call spans (model, tokens, latency)
- Tool execution spans
- Subagent delegation spans

Logs include OTEL correlation IDs for trace-log linking.

## Key Dashboards

| Dashboard | URL | What to Watch |
|-----------|-----|---------------|
| Agent Latency | <!-- link --> | End-to-end response time |
| Token Usage | <!-- link --> | Per-org, per-model consumption |
| Error Rate | <!-- link --> | Failed requests, DLQ depth |
| Redis Streams | <!-- link --> | Stream lag, consumer group health |
| Langfuse | <!-- link --> | LLM call traces, cost |

## Alerting

| Alert | Condition | Severity |
|-------|-----------|----------|
| Stream lag > threshold | Consumer falling behind | SEV2 |
| DLQ depth > 0 | Failed messages | SEV3 |
| Health check failure | `/health` returns non-200 | SEV1 |
| High error rate | > 5% of requests failing | SEV2 |

## See Also

- [services/agent-platform/dependencies.md](../services/agent-platform/dependencies.md) — SigNoz, Langfuse config
- [runbooks/debugging.md](../runbooks/debugging.md) — How to use these tools
- [runbooks/incident-response.md](../runbooks/incident-response.md) — When alerts fire
