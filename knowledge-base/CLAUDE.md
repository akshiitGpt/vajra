# Company Docs

Ruh AI's grep-able markdown knowledge base. Clone into any project, search with `rg`, navigate with `cat`/`head`/`sed`.

## Project Structure

```
ruh-knowledge-base/
├── knowledge-base/          # 45 markdown documents
│   ├── index.md             # Global map — start here
│   ├── navigation.md        # Agent navigation instructions
│   ├── glossary.md          # Company terminology
│   ├── architecture/        # System-level: overview, service map, data flow, communication
│   ├── services/            # Per-service deep dives (overview, api, database, events, dependencies)
│   │   ├── agent-platform/  # Core AI agent execution engine (Python)
│   │   ├── agent-gateway/   # Sandbox orchestrator (TypeScript/Docker)
│   │   ├── communication-service/
│   │   ├── ai-gateway/
│   │   └── file-conversion/
│   ├── repos/               # Code-level repo guides (directory structure, local dev)
│   ├── data/                # Schemas, events, pipelines
│   ├── infra/               # Kubernetes, CI/CD, environments, observability
│   ├── workflows/           # End-to-end request flows
│   ├── runbooks/            # Deployments, debugging, incident response
│   └── references/          # API endpoints, env vars, feature flags
└── CLAUDE.md
```

## Searching the Knowledge Base

```bash
KB="knowledge-base"

# Full-text search
rg -i "redis streams" "$KB"

# Search within a section
rg -i "deploy" "$KB/runbooks/"

# Find files by tag
rg "tags:.*kafka" "$KB"

# Find files by name
find "$KB" -name "*agent*" -type f

# Read a doc
cat "$KB/services/agent-platform/overview.md"

# Read first 50 lines (summary)
head -n 50 "$KB/services/agent-platform/overview.md"

# Read a specific line range
sed -n '30,80p' "$KB/workflows/agent-chat-flow.md"

# List all docs
find "$KB" -name '*.md' -type f | sort
```

## Conventions

### Knowledge Base Files

Every `.md` file MUST start with YAML front-matter:

```yaml
---
title: "Document Title"
category: architecture    # architecture | services | repos | data | infra | workflows | runbooks | references
tags: [tag1, tag2]
owner: "@username"
last_updated: "YYYY-MM-DD"
source: manual
---
```

- File names: lowercase, hyphens, no spaces (e.g., `agent-chat-flow.md`)
- One topic per file, 100–300 lines max
- Every doc ends with a `See Also:` section linking related files
- Cross-link between documents rather than duplicating content

### Navigation Order

```
1. index.md → find the right section
2. architecture/service-map.md → which services are involved
3. services/<name>/overview.md → service details
4. workflows/<flow>.md → how pieces connect
5. data/events/ or data/schemas/ → data contracts
6. repos/<name>.md → code navigation
```

## GitHub

- Repo: github.com/ruh-ai/ruh-knowledge-base
- Branch: master
