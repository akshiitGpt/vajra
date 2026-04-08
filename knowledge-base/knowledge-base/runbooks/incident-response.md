---
title: "Incident Response"
category: runbooks
tags: [runbook, incident, oncall]
owner: "@devops"
last_updated: "2026-03-31"
source: manual
---

# Incident Response

## Severity Levels

| Severity | Definition | Response Time |
|----------|-----------|---------------|
| **SEV1** | Complete outage — agents not responding | 5 min |
| **SEV2** | Major degradation — partial failures | 15 min |
| **SEV3** | Minor issue — workaround available | 1 hour |
| **SEV4** | Cosmetic / low-impact | 1 business day |

## Response Protocol

### 1. Acknowledge
Claim the alert. Post in #incidents:
> **[SEV-X] Brief description** — IC: @your-name — Status: Investigating

### 2. Assess
- Which services are affected? Check dashboards.
- When did it start? Check recent deploys: `git log --oneline --since="2 hours ago" main`
- Is it getting worse or stable?

### 3. Mitigate
**Restore service first, root cause later.**

| Issue | Action |
|-------|--------|
| Bad deploy | `helm rollback` or `kubectl rollout undo` |
| Redis Stream lag | Scale up Agent Platform workers |
| LLM provider down | Switch provider in AI Gateway config |
| Container failures | Restart Agent Gateway, check Docker daemon |
| Database overload | Check connection pools, slow queries |

### 4. Resolve
- Confirm monitoring stable for 15 min
- Post resolution in #incidents
- Schedule post-mortem within 48 hours (SEV1/SEV2)

## Quick Diagnostic Commands

```bash
# Agent Platform health
curl -s https://<endpoint>:6000/health

# Redis Stream lag
redis-cli XINFO GROUPS {env}:agent:chat:requests

# Kubernetes pod status
kubectl get pods -n agent-platform
kubectl logs -n agent-platform deployment/agent-platform --tail=100

# Recent deploys
helm history agent-platform
```

## Escalation Path

1. On-call engineer
2. Service owner
3. Engineering manager
4. CTO

## See Also

- [runbooks/deployments.md](deployments.md) — Rollback procedures
- [runbooks/debugging.md](debugging.md) — Debugging guide
- [infra/observability.md](../infra/observability.md) — Dashboards and alerts
