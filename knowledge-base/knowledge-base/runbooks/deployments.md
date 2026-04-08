---
title: "Deployments"
category: runbooks
tags: [runbook, deploy, production]
owner: "@devops"
last_updated: "2026-03-31"
source: manual
---

# Deployments

## Prerequisites

- [ ] All CI checks passing on `main`
- [ ] PR approved and merged
- [ ] No active SEV1/SEV2 incidents
- [ ] Deploy window: weekdays, not Fridays

## Deploy Agent Platform

```bash
# 1. Verify CI is green
gh run list --branch main --repo ruh-ai/agent-platform-v2 --limit 5

# 2. Build and push Docker image
docker build -t agent-platform:latest .
docker push <registry>/agent-platform:latest

# 3. Deploy via Helm
cd helm/main
helm upgrade agent-platform . --values values.yaml

# 4. Monitor rollout
kubectl rollout status deployment/agent-platform -n agent-platform --timeout=300s

# 5. Verify health
curl -s https://<endpoint>:6000/health
```

## Deploy Agent Gateway (ruhclaw)

```bash
# 1. Build and push
cd ruhclaw
docker build -t agent-gateway:latest -f backend/docker/Dockerfile .
docker push <registry>/agent-gateway:latest

# 2. Deploy
kubectl rollout restart deployment/agent-gateway -n agent-gateway

# 3. Verify
curl -s https://<endpoint>/api/agents
```

## Rollback

```bash
# Helm rollback (Agent Platform)
helm rollback agent-platform

# Kubernetes rollback (any service)
kubectl rollout undo deployment/<service-name> -n <namespace>
```

## Post-Deploy Checks

- [ ] Health endpoints returning 200
- [ ] Redis Streams — no consumer lag spike
- [ ] Error rate stable in monitoring
- [ ] Langfuse — LLM calls succeeding

## See Also

- [infra/ci-cd.md](../infra/ci-cd.md) — Pipeline details
- [infra/kubernetes.md](../infra/kubernetes.md) — Cluster config
- [runbooks/incident-response.md](incident-response.md) — If something goes wrong
