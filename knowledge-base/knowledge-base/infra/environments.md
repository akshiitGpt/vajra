---
title: "Environments"
category: infra
tags: [infra, environments, dev, qa, production]
owner: "@devops"
last_updated: "2026-03-31"
source: manual
---

# Environments

## Overview

| Environment | Purpose | Redis Prefix | Deploy Trigger |
|-------------|---------|-------------|----------------|
| `dev` | Development and testing | `dev:` | Manual / PR deploy |
| `qa` | QA and staging | `qa:` | Auto on merge to main |
| `main` | Production | `main:` | Manual approval |

## Environment Isolation

All Redis Streams are prefixed with the environment name (`{env}:`), ensuring complete message isolation between environments. Each environment has its own:

- Redis instance (or logical database)
- MongoDB database
- Qdrant collection
- Kafka topics (prefixed)
- Kubernetes namespace

## Environment Variables

The `ENV` variable controls which environment the service operates in. Set in Helm values per environment.

```
ENV=dev    → dev:agent:chat:requests
ENV=qa     → qa:agent:chat:requests
ENV=main   → main:agent:chat:requests
```

## Access

| Environment | Who | How |
|-------------|-----|-----|
| `dev` | All engineers | Direct access, local or shared cluster |
| `qa` | All engineers | Shared cluster, auto-deployed |
| `main` | On-call + deploy approvers | Manual approval required |

## See Also

- [infra/kubernetes.md](kubernetes.md) — Cluster and namespaces
- [references/environment-variables.md](../references/environment-variables.md) — All env vars
