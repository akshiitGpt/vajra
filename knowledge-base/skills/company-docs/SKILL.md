---
name: company-docs
description: Set up, search, navigate, and add information to the Ruh AI company documentation knowledge base. Use this skill when an agent needs to clone the company-docs repo, efficiently search/browse documentation about services (agent-platform, agent-gateway, ai-gateway, communication-service), architecture, data schemas, workflows, runbooks, or infrastructure. Covers token-efficient navigation patterns for large markdown knowledge bases.
---

This skill enables any agent to clone, navigate, search, and contribute to the Ruh AI company documentation knowledge base with minimal token usage.

## Setup: Clone and Configure

```bash
# Clone into the current project root
git clone https://github.com/ruh-ai/ruh-knowledge-base.git .ruh-knowledge-base

# Gitignore it
grep -qxF '.ruh-knowledge-base/' .gitignore 2>/dev/null || echo ".ruh-knowledge-base/" >> .gitignore

# Set the knowledge base path
export KB="$(pwd)/.ruh-knowledge-base/knowledge-base"
```

Verify: `find "$KB" -name '*.md' | wc -l` (should be 45 files).

## Keeping Docs Up to Date

**IMPORTANT: Always pull latest docs at the start of every session.**

```bash
cd .ruh-knowledge-base && git pull --ff-only && cd ..
```

### Auto-pull via Claude Code hook (recommended)

Add this to your project's `.claude/settings.json` to auto-pull on every session start:

```json
{
  "hooks": {
    "PreToolUse": [
      {
        "matcher": "Bash",
        "hooks": []
      }
    ],
    "SessionStart": [
      {
        "hooks": [
          {
            "type": "command",
            "command": "cd .ruh-knowledge-base && git pull --ff-only 2>/dev/null; true"
          }
        ]
      }
    ]
  }
}
```

This ensures docs are always fresh without manual intervention.

## Knowledge Base Structure

```
knowledge-base/
  index.md                  # Global map — START HERE
  navigation.md             # Agent navigation instructions
  glossary.md               # Company terminology

  architecture/             # System-level understanding
    overview.md             # Platform overview + simplified diagram
    system-context.md       # External boundaries, actors, trust
    service-map.md          # All services + dependency graph
    communication.md        # Redis Streams, WebSocket, REST patterns
    data-flow.md            # End-to-end data lifecycle

  services/                 # Per-service deep dives (MOST IMPORTANT)
    agent-platform/         # Core AI agent execution engine (Python)
      overview.md api.md database.md events.md dependencies.md
    agent-gateway/          # Sandbox orchestrator (TypeScript/Docker)
      overview.md api.md database.md events.md dependencies.md
    communication-service/  # Cross-platform messaging
      overview.md
    ai-gateway/             # LLM request proxy
      overview.md

  repos/                    # Code-level repo guides
    agent-platform-v2.md    # Directory structure, entry point, local dev
    agent-gateway.md        # Directory structure, entry point, local dev
    communication-service.md
    ai-gateway.md

  data/                     # Schemas, events, pipelines
    schemas/                # conversations.md, agents.md
    events/                 # redis-streams.md, kafka-events.md, websocket-events.md
    pipelines/              # analytics.md

  infra/                    # Infrastructure
    kubernetes.md ci-cd.md environments.md observability.md

  workflows/                # End-to-end request flows
    agent-chat-flow.md      # Full chat request lifecycle (most useful)
    agent-creation-flow.md  # Docker sandbox provisioning
    workflow-execution-flow.md

  runbooks/                 # Operational procedures
    deployments.md debugging.md incident-response.md

  references/               # Quick lookup tables
    api-endpoints.md environment-variables.md feature-flags.md
```

## Navigation: Token-Efficient Workflow

**NEVER read entire files. Progressively narrow your search.**

### 1. Read the index
```bash
cat "$KB/index.md"
```

### 2. Search with ripgrep
```bash
# Full-text search
rg -i "redis streams" "$KB"

# Search within a section
rg -i "deploy" "$KB/runbooks/"

# Find files by tag
rg "tags:.*kafka" "$KB"

# Find files by name
find "$KB" -name "*agent*" -type f
```

### 3. Read only what you need
```bash
# First 50 lines (front-matter + summary)
head -n 50 "$KB/services/agent-platform/overview.md"

# Specific line range from search results
sed -n '30,80p' "$KB/architecture/data-flow.md"

# List all docs
find "$KB" -name '*.md' -type f | sort
```

### Recommended search order for most questions:
```
1. index.md → find the right section
2. architecture/service-map.md → which services are involved
3. services/<name>/overview.md → service details
4. workflows/<flow>.md → how pieces connect
5. data/events/ or data/schemas/ → data contracts
6. repos/<name>.md → code navigation
```

## Adding New Documentation

Every `.md` file MUST start with YAML front-matter:

```yaml
---
title: "Document Title"
category: services
tags: [agent-platform, redis-streams]
owner: "@username"
last_updated: "YYYY-MM-DD"
source: manual
---
```

File naming: lowercase, hyphens, no spaces. One topic per file. 100-300 lines max.

### Where to put new docs:

| Type | Location |
|------|----------|
| New service | `services/<name>/overview.md` + api/database/events/dependencies |
| Service code guide | `repos/<name>.md` |
| Data schema | `data/schemas/<name>.md` |
| New event type | `data/events/<name>.md` |
| End-to-end flow | `workflows/<name>-flow.md` |
| Operational procedure | `runbooks/<name>.md` |
| Quick reference | `references/<name>.md` |
| Architecture doc | `architecture/<name>.md` |

### After creating or editing:
```bash
cd .ruh-knowledge-base
git add knowledge-base/
git commit -m "docs: add descriptive-name"
git push
```

## Cross-Linking

Every doc should end with a `See Also:` section linking to related files.

## Common Recipes

| Question | Start here |
|----------|-----------|
| How does the platform work? | `architecture/overview.md` |
| What services exist? | `architecture/service-map.md` |
| How does agent chat work E2E? | `workflows/agent-chat-flow.md` |
| What does agent-platform do? | `services/agent-platform/overview.md` |
| Redis Stream event format? | `data/events/redis-streams.md` |
| All API endpoints? | `references/api-endpoints.md` |
| All environment variables? | `references/environment-variables.md` |
| How to deploy? | `runbooks/deployments.md` |
| How to debug? | `runbooks/debugging.md` |
| What does a term mean? | `glossary.md` |
