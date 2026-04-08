---
title: "Debugging"
category: runbooks
tags: [runbook, debugging, troubleshooting]
owner: "@backend"
last_updated: "2026-03-31"
source: manual
---

# Debugging

## Agent Not Responding

```bash
# 1. Check if the worker is running
kubectl get pods -n agent-platform

# 2. Check Redis Stream health
redis-cli XINFO GROUPS {env}:agent:chat:requests
# Look for: pending messages, consumer lag

# 3. Check DLQ for failed messages
redis-cli XLEN {env}:agent:chat:requests:dlq

# 4. Check worker logs
kubectl logs -n agent-platform deployment/agent-platform --tail=200 | grep ERROR
```

## LLM Calls Failing

```bash
# 1. Check AI Gateway health
curl -s https://<ai-gateway-endpoint>/health

# 2. Check Langfuse for error traces
# Open Langfuse dashboard → filter by status=error

# 3. Check provider status
# OpenRouter: https://status.openrouter.ai
# OpenAI: https://status.openai.com
# Anthropic: https://status.anthropic.com

# 4. Check API key validity
# Verify env vars: AI_GATEWAY_BASE_URL, OPENROUTER_API_KEY
```

## Agent Container Not Starting (ruhclaw)

```bash
# 1. Check Docker daemon
docker info

# 2. Check container status
docker ps -a | grep openclaw

# 3. Check container logs
docker logs <container-id>

# 4. Verify image exists
docker images | grep openclaw
```

## Redis Stream Lag

```bash
# Check consumer group info
redis-cli XINFO GROUPS {env}:agent:chat:requests

# Check pending messages per consumer
redis-cli XPENDING {env}:agent:chat:requests <group-name> - + 10

# Check stream length
redis-cli XLEN {env}:agent:chat:requests
```

## Memory Issues (Qdrant)

```bash
# Check Qdrant health
curl -s http://<qdrant-host>:6333/healthz

# Check collection info
curl -s http://<qdrant-host>:6333/collections/langgraph_memory
```

## Checkpoint Issues (MongoDB)

```bash
# Check MongoDB connectivity
mongosh "$MONGO_DB_URL" --eval "db.runCommand({ ping: 1 })"

# Check checkpoint collection
mongosh "$MONGO_DB_URL" --eval "db.checkpoints.stats()"
```

## See Also

- [runbooks/incident-response.md](incident-response.md) — Incident handling
- [infra/observability.md](../infra/observability.md) — Dashboards
- [services/agent-platform/dependencies.md](../services/agent-platform/dependencies.md) — Failure impact
