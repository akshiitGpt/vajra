---
title: "Workflow Execution Flow"
category: workflows
tags: [workflow, multi-step, langraph]
owner: "@backend"
last_updated: "2026-03-31"
source: manual
---

# Workflow Execution Flow

How multi-step workflows are generated and executed in Agent Platform.

## Overview

Workflows are multi-step automated sequences that an agent can generate and run. They use a separate LangGraph graph (`workflow_generation_graph/`) from the main chat agent.

## Workflow Generation

```
1. User requests a workflow via chat or API
   │
2. Request published to Redis Stream
   │  Stream: {env}:workflow:requests
   │
3. Agent Platform consumes and routes to workflow generation graph
   │  Uses: app/workflow_generation_graph/
   │
4. LLM generates a multi-step workflow definition
   │  Each step: { action, params, dependencies }
   │
5. Workflow definition streamed back via Redis
   │  Stream: {env}:workflow:responses:{request_id}
```

## Workflow Chat

```
1. User sends a message within a workflow context
   │
2. Request published to Redis Stream
   │  Stream: {env}:workflow:chat:requests
   │
3. Agent Platform consumes and routes to workflow chat graph
   │  Uses: app/workflow_chat/
   │
4. LLM processes message with workflow context
   │
5. Response streamed back via Redis
```

## See Also

- [services/agent-platform/overview.md](../services/agent-platform/overview.md) — Agent execution engine
- [workflows/agent-chat-flow.md](agent-chat-flow.md) — Standard chat flow
