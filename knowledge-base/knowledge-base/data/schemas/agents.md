---
title: "Agents Schema"
category: data
tags: [data, schema, agents, configuration]
owner: "@backend"
last_updated: "2026-03-31"
source: manual
---

# Agent Configuration

## Overview

Agent configurations are stored in the **API Gateway** (external service) and fetched at runtime by the Agent Platform.

## Fetch Endpoint

```
GET {API_GATEWAY_URL}/agents/{agent_id}
```

## Configuration Fields

| Field | Type | Description |
|-------|------|-------------|
| `agent_id` | string | Unique agent identifier |
| `organisation_id` | string | Owning organization |
| `name` | string | Agent display name |
| `system_prompt` | string | Custom system prompt |
| `model` | string | LLM model to use (e.g., `claude-sonnet-4`) |
| `model_provider` | string | Provider override (openrouter, openai, anthropic) |
| `tools` | list[string] | Enabled tools (from AgentToolEnum) |
| `mcp_tools` | list[dict] | MCP tool configurations |
| `knowledge_base` | dict | RAG knowledge base config |
| `memory_enabled` | boolean | Whether Mem0 long-term memory is active |
| `max_tokens` | integer | Max response token limit |
| `temperature` | float | LLM temperature setting |

## Agent State (In-Memory, Agent Gateway)

For ruhclaw/agent-gateway, agent state is in-memory only:

```typescript
{
  id: string               // UUID
  name: string
  status: "creating" | "running" | "stopped" | "error"
  containerId: string      // Docker container ID
  gatewayUrl: string       // OpenClaw gateway URL
  previewPort: number
  createdAt: string
}
```

## See Also

- [services/agent-platform/overview.md](../../services/agent-platform/overview.md) — How agents execute
- [services/agent-gateway/database.md](../../services/agent-gateway/database.md) — In-memory state
