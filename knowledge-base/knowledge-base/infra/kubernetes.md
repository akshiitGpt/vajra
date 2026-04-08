---
title: "Kubernetes"
category: infra
tags: [infra, kubernetes, helm, deployment]
owner: "@devops"
last_updated: "2026-03-31"
source: manual
---

# Kubernetes

## Cluster Setup

| Aspect | Details |
|--------|---------|
| Provider | <!-- GKE / EKS / AKS --> |
| Cluster name | <!-- production-cluster --> |
| Region | <!-- e.g. us-central1 --> |

## Namespaces

| Namespace | Services |
|-----------|----------|
| `agent-platform` | agent-platform-v2 workers |
| `agent-gateway` | ruhclaw backend + frontend |
| `ai-gateway` | AI Gateway proxy |
| `communication` | Communication service |
| `infrastructure` | Redis, MongoDB, Qdrant, Kafka |

## Helm Charts

Agent Platform has Helm charts for each environment:

```
agent-platform-v2/helm/
  dev/
  qa/
  main/
```

Each chart includes:
- Deployment (worker replicas)
- Service (health check endpoint)
- ConfigMap (env vars)
- Secrets (API keys)
- HPA (horizontal pod autoscaler)

## Scaling

| Service | Strategy | Min | Max |
|---------|----------|-----|-----|
| Agent Platform | HPA (CPU/memory) | <!-- 2 --> | <!-- 20 --> |
| Agent Gateway | Single instance (Docker socket) | 1 | 1 |
| AI Gateway | HPA (requests/sec) | <!-- 2 --> | <!-- 10 --> |

## See Also

- [infra/environments.md](environments.md) — Environment details
- [infra/ci-cd.md](ci-cd.md) — Deploy pipeline
- [runbooks/deployments.md](../runbooks/deployments.md) — Deploy procedure
