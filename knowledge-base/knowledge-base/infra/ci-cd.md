---
title: "CI/CD Pipeline"
category: infra
tags: [infra, ci-cd, github-actions, deployment]
owner: "@devops"
last_updated: "2026-03-31"
source: manual
---

# CI/CD Pipeline

## Pipeline Stages

```
Push to branch
  → Lint + type check
  → Unit tests
  → Build container image
  → Push to container registry
  → Deploy to dev/qa (automatic on merge to main)
  → Integration tests
  → Manual approval
  → Deploy to production
  → Post-deploy health checks
```

## Per-Service CI

### Agent Platform (Python)

```
poetry install → ruff lint → mypy → pytest → docker build → push → helm upgrade
```

### Agent Gateway (TypeScript/Bun)

```
bun install → eslint → bun test → docker build → push → deploy
```

## Tooling

| Stage | Tool |
|-------|------|
| CI runner | GitHub Actions |
| Container registry | <!-- GCR / ECR / Docker Hub --> |
| Deployment | Helm + kubectl |
| Secrets | <!-- GitHub Secrets / Vault --> |

## Branch Strategy

- `main` — always deployable, auto-deploys to dev/qa
- `feature/*` — short-lived, PR into main
- Squash merge to main

## See Also

- [infra/kubernetes.md](kubernetes.md) — Helm charts and cluster
- [infra/environments.md](environments.md) — Environment config
- [runbooks/deployments.md](../runbooks/deployments.md) — Deploy procedure
