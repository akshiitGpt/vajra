---
title: "Feature Flags"
category: references
tags: [reference, feature-flags, configuration]
owner: "@backend"
last_updated: "2026-03-31"
source: manual
---

# Feature Flags

## Agent Platform Feature Toggles

These are controlled via environment variables and agent configuration.

| Flag | Env Var / Config | Default | Description |
|------|-----------------|---------|-------------|
| Checkpointer backend | `CHECKPOINTER_BACKEND` | `mongodb` | Switch between MongoDB and PostgreSQL |
| OTEL tracing | `OTEL_ENABLED` | `false` | Enable/disable OpenTelemetry |
| Memory enabled | Agent config: `memory_enabled` | Per agent | Enable Mem0 long-term memory |
| MCP tools | Agent config: `mcp_tools` | Per agent | Which MCP integrations are active |
| Available tools | Agent config: `tools` | Per agent | Which tools the agent can use |

## Per-Agent Configuration

Most "feature flags" in the Ruh AI platform are per-agent configuration rather than global flags. Each agent can have different:

- Enabled tools
- MCP integrations
- Model and provider
- Memory settings
- System prompt

These are managed through the API Gateway's agent configuration.

<!-- TODO: Add global feature flag system if/when implemented (LaunchDarkly, Unleash, etc.) -->

## See Also

- [data/schemas/agents.md](../data/schemas/agents.md) — Agent config schema
- [references/environment-variables.md](environment-variables.md) — Env var toggles
