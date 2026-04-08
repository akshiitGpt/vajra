# CLAUDE.md

## Project

Vajra is an autonomous coding agent that polls Linear for assigned issues, runs them through a multi-stage AI pipeline (plan → review → code → review → PR), and publishes pull requests to GitHub — without human intervention.

**Owner**: akshiitGpt  
**Repo**: `akshiitGpt/vajra`  
**Linear assignee ID**: `38cbbbc9-437e-4bde-9916-f8b2ae8e3ce5`

## Architecture

```
vajra/
├── orchestrator/     # Node.js/TypeScript engine — polls Linear, manages pipelines
│   ├── src/          # Source (TypeScript, compiled to dist/)
│   ├── skills/       # Markdown skill documents (12 skills)
│   └── tests/        # 197 tests
├── dashboard/        # Next.js 16 + Tailwind v4 monitoring UI
├── pipelines/        # DOT workflow graphs (default, revision, document, knowledge)
├── WORKFLOW.md       # Runtime configuration (tracker, agents, backends, hooks)
└── start.sh          # Starts orchestrator + dashboard
```

## Commands

```bash
npm install                        # Install all workspaces
npm run build:orchestrator         # Build orchestrator TypeScript → dist/
npm run build                      # Build orchestrator + dashboard
npm test                           # Run orchestrator tests (197 tests)
npm run dev:dashboard              # Dashboard dev server on :3000
./start.sh                         # Start everything (orchestrator :3847 + dashboard :3000)
./start.sh orchestrator            # Orchestrator only
./start.sh dashboard               # Dashboard only
```

## Environment Variables

- `LINEAR_API_KEY` — Linear API key (required)
- `GITHUB_TOKEN` — auto-resolved from `gh auth token` in start.sh
- `VAJRA_API_KEY` — protects the Vajra REST API (default: `vajra-local-dev`)
- `VAJRA_API_PORT` — orchestrator port (default: 3847)
- `DASHBOARD_PORT` — dashboard port (default: 3000)

## Configuration

All runtime config lives in `WORKFLOW.md` (YAML frontmatter). Key sections:
- `tracker` — Linear connection, assignee, active/terminal states
- `hooks` — Shell scripts for workspace setup (clone, reset)
- `agents` — Agent definitions (backend, model, prompt, skills)
- `backends` — CLI command templates for Claude/Codex
- `workflows` — DOT pipeline files and routing
- `github` — Repository, tokens, PR revision settings

## Linear States (our workspace)

Active: Todo, In Progress, In Development, Ready for Development  
Terminal: Done, Canceled, Cancelled, Duplicate, Done on Prod  
On success: → Code Review  
On escalation: → Require Changes

## Pipeline Flow

### Single-repo (default)
`Plan → Review Plan → Code → Review Code → Prepare PR → Publish PR`

### Multi-repo (Option C — two-phase)
```
Scout Phase:  Scout → Review Scout Plan → (validated plan JSON)
     ↓
Per-Repo:     Plan → Review → Code → Review → PR  (×N repos in parallel)
     ↓
Coordination: Cross-link PRs, update Linear
```

The scout runs in a knowledge repo workspace (`akshiitGpt/service-knowledge`), reads `services/*/SERVICE.md` files, clones candidate repos to investigate, and outputs a `scout-plan.json` listing which repos need changes.

Review stages output labels: `lgtm` (proceed), `revise` (loop back), `escalate` (human needed). Max 4 revision visits per stage before escalation. Global budget: 20 agent invocations per run.

## Multi-Repo Config

`WORKFLOW.md` has a `multi_repo` section:
- `knowledge_repo` — repo with SERVICE.md files describing all services
- `scout_workflow` — pipeline for investigation phase
- `execution_workflow` — pipeline for per-repo coding (reuses `default`)
- `max_parallel_repos` — concurrency limit for per-repo runs

The `after_create` hook uses `${VAJRA_CLONE_URL}` so the coordinator can point each workspace at the right repo.

## Dashboard Design

- **Light theme**, system fonts, sharp corners (zero border-radius)
- Tailwind v4 with CSS custom properties (`--d-*` tokens in globals.css)
- All design tokens in `dashboard/src/lib/design/tokens.ts`
- React Flow for pipeline graph visualization
- SSE for real-time updates from orchestrator
- Pages: Monitor, Agents, Skills, Workflows, Knowledge Base, Config

## Code Style

- TypeScript strict mode throughout
- Orchestrator: CommonJS output, ES2022 target
- Dashboard: Next.js App Router, React 19, `"use client"` directives
- Path alias: `@/*` → `dashboard/src/*`
- No semicolons in quotes (YAML), double quotes in TypeScript
- Tests use Node.js built-in test runner

## Key Files

- `WORKFLOW.md` — all runtime config (edit this to change agent behavior)
- `orchestrator/src/orchestrator.ts` — main polling/dispatch loop
- `orchestrator/src/pipeline.ts` — pipeline execution engine
- `orchestrator/src/types.ts` — all TypeScript types
- `pipelines/default.dot` — default workflow graph
- `pipelines/scout.dot` — multi-repo scout workflow graph
- `orchestrator/skills/vajra-scout/SKILL.md` — scout agent instructions
- `orchestrator/skills/vajra-scout-review/SKILL.md` — scout review instructions
- `orchestrator/src/multi-repo-coordinator.ts` — multi-repo orchestration (scout + per-repo + coordination)
- `orchestrator/src/scout-plan-validator.ts` — validates scout plan JSON schema
- `knowledge-base/` — embedded ruh-knowledge-base (45 docs on services, architecture, workflows)
- `dashboard/src/app/globals.css` — design tokens and base styles
- `dashboard/src/app/vajra/knowledge/page.tsx` — knowledge base browser/editor
