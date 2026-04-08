---
title: "Analytics Pipeline"
category: data
tags: [data, pipelines, analytics, kafka]
owner: "@backend"
last_updated: "2026-03-31"
source: manual
---

# Analytics Pipeline

## Overview

Agent activity and token usage events flow from Agent Platform through Kafka to downstream analytics consumers.

```
Agent Platform → Kafka → Analytics Service → Dashboards / Billing
```

## Data Sources

| Source | Topic | Data |
|--------|-------|------|
| Agent Platform | `analytics-service-agent-activity-events` | Tool calls, errors, sessions |
| Agent Platform | `token-usage-events` | Input/output token counts per request |

## Key Metrics

| Metric | Source | Purpose |
|--------|--------|---------|
| Token usage per org | `token-usage-events` | Billing and cost tracking |
| Tool call frequency | `agent-activity-events` | Feature usage analytics |
| Error rate per agent | `agent-activity-events` | Reliability monitoring |
| Session duration | `agent-activity-events` | Engagement metrics |

## See Also

- [data/events/kafka-events.md](../events/kafka-events.md) — Event schemas
- [infra/observability.md](../../infra/observability.md) — Monitoring setup
