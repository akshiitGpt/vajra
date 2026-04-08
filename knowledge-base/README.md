# Company Documentation Suite

A grep-able markdown knowledge base + CLI tool + OpenClaw agent system with sub-agents for search, summarization, sync, and documentation tasks.

## Quick Start

```bash
# 1. Clone and enter the repo
cd ruh-knowledge-base

# 2. Copy env file and fill in API keys
cp .env.example .env
# Edit .env with your LINEAR_API_KEY, GITHUB_TOKEN, etc.

# 3. Run setup
source .env
./setup.sh

# 4. Try the CLI
docs-query list
docs-query search "deploy"
docs-query get runbooks/deploy-production.md
```

## Architecture

```
ruh-knowledge-base/
├── knowledge-base/     # Markdown documentation store (git repo)
├── cli/                # docs-query CLI tool
├── sync/               # Scripts to pull data from Linear, GitHub, git
└── openclaw/           # OpenClaw agent workspaces and config
```

### Knowledge Base

All documentation lives in `knowledge-base/` as standalone markdown files with YAML front-matter:

```yaml
---
title: "Document Title"
category: architecture    # architecture | repos | linear | runbooks | team
tags: [tag1, tag2]
owner: "@username"
last_updated: "2026-03-30"
source: manual            # manual | linear-sync | repo-sync | git-sync
---
```

Categories:
- **architecture/** — System design, infrastructure, tech stack, ADRs
- **repos/** — Per-repo documentation (auto-synced from GitHub)
- **linear/** — Sprint data, projects, completed issues (auto-synced)
- **runbooks/** — Operational procedures (deploy, incident response, onboarding)
- **team/** — Ownership, conventions, processes

### CLI Tool — `docs-query`

```bash
docs-query search "auth"                    # Full-text search
docs-query search "deploy" --category runbooks  # Category filter
docs-query search --tag kubernetes          # Tag search
docs-query search "auth" --json             # JSON output (for agents)
docs-query get architecture/system-overview.md  # Get specific doc
docs-query list                             # List all docs
docs-query list --category repos --json     # List with filter + JSON
docs-query meta architecture/system-overview.md --json  # Metadata only
docs-query reindex                          # Rebuild INDEX.md
```

Dependencies: `ripgrep`, `jq`, `git` (required); `yq` (optional)

### Sync Scripts

```bash
# Sync everything
./sync/sync-all.sh

# Individual syncs
./sync/linear-sync.sh      # Linear sprints/projects → markdown
./sync/repo-sync.sh        # GitHub repos → markdown
./sync/git-log-sync.sh     # Local git logs → markdown
./sync/index-rebuild.sh    # Rebuild INDEX.md
```

Configure repos to sync:
- `sync/repos.conf` — GitHub repos (format: `org/repo-name`)
- `sync/local-repos.conf` — Local repos (format: `/path/to/repo repo-name`)

### OpenClaw Agents

The system uses 5 agents in an orchestrator pattern:

| Agent | Model | Purpose |
|-------|-------|---------|
| **orchestrator** | claude-opus-4-6 | Routes questions to sub-agents |
| **searcher** | claude-haiku-4-5 | Searches knowledge base via docs-query |
| **summarizer** | claude-sonnet-4-5 | Produces concise summaries |
| **syncer** | claude-haiku-4-5 | Runs sync scripts |
| **writer** | claude-sonnet-4-5 | Creates/updates documentation |

Start the gateway:
```bash
cd openclaw
openclaw gateway
```

The orchestrator is bound to Slack, Telegram, Discord, and webchat by default.

## Environment Variables

| Variable | Required | Description |
|----------|----------|-------------|
| `COMPANY_DOCS_HOME` | Yes | Path to this project root |
| `LINEAR_API_KEY` | For sync | Linear API key |
| `GITHUB_TOKEN` | For sync | GitHub personal access token |
| `GITHUB_ORG` | No | GitHub org name |
| `SLACK_CHANNEL_ID` | No | Slack channel for daily briefs |

## Adding Documentation

1. **Manual**: Create a `.md` file in the appropriate `knowledge-base/` subdirectory with proper front-matter
2. **Via agent**: Ask the docs bot to "document X" and the writer sub-agent handles it
3. **Via sync**: Add repos to `sync/repos.conf` and run `./sync/repo-sync.sh`

### Creating an ADR

```bash
cp knowledge-base/architecture/adr/TEMPLATE.md \
   knowledge-base/architecture/adr/001-your-decision.md
# Edit the file, then:
docs-query reindex
```

## Security

- Never commit `.env` (it's in `.gitignore`)
- Sub-agents cannot access `gateway`, `cron`, or `message` tools
- Keep the knowledge base repo private if it contains sensitive details
- The `exec` tool for sub-agents should be restricted to `docs-query` and sync scripts
