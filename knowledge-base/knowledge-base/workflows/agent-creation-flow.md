---
title: "Agent Creation Flow"
category: workflows
tags: [workflow, agent-gateway, docker, sandbox]
owner: "@backend"
last_updated: "2026-03-31"
source: manual
---

# Agent Creation Flow

How a new agent sandbox is provisioned in the Agent Gateway (ruhclaw).

## Flow

```
1. User clicks "Create Agent" in the web UI
   │
2. Frontend sends POST /api/agents { name: "My Agent" }
   │
3. Backend creates an AgentRecord (status: "creating")
   │  Stored in-memory Map<string, AgentRecord>
   │  Returns immediately with the agent ID
   │
4. Backend builds Docker image (if not cached)
   │  Image: node:22-slim + openclaw global install
   │  Includes: entrypoint.sh, openclaw.json, sidecar scripts
   │
5. Backend creates Docker container
   │  Maps ports: 18790 (WS), 18791 (file watcher), 18792 (preview)
   │  Passes OPENROUTER_API_KEY as env var
   │
6. Container starts and runs entrypoint.sh
   │  ├── Starts OpenClaw gateway on port 18789 (internal)
   │  ├── Starts WS proxy on port 18790 (0.0.0.0 → 127.0.0.1:18789)
   │  ├── Starts file watcher on port 18791
   │  └── Starts preview proxy on port 18792
   │
7. Backend connects to the container
   │  ├── Establishes WebSocket to port 18790 (OpenClaw gateway)
   │  │   Uses Ed25519 device signing for authentication
   │  ├── Connects TCP to port 18791 (file watcher)
   │  └── Updates AgentRecord status to "running"
   │
8. Frontend polls GET /api/agents/:id until status is "running"
   │
9. Frontend opens WebSocket to ws://host/ws/chat/:agentId
   │
10. User can now chat with the agent
```

## Container Architecture

```
┌──────────────────── Docker Container ────────────────────┐
│                                                           │
│  ┌─────────────────────┐                                  │
│  │  OpenClaw Gateway    │ ← port 18789 (internal only)    │
│  │  (AI agent runtime)  │                                  │
│  └──────────┬──────────┘                                  │
│             │                                              │
│  ┌──────────▼──────────┐                                  │
│  │  WS Proxy            │ ← port 18790 (exposed to host)  │
│  │  (ws-proxy.mjs)      │                                  │
│  └─────────────────────┘                                  │
│                                                           │
│  ┌─────────────────────┐                                  │
│  │  File Watcher        │ ← port 18791 (exposed to host)  │
│  │  (file-watcher.mjs)  │                                  │
│  └─────────────────────┘                                  │
│                                                           │
│  ┌─────────────────────┐                                  │
│  │  Preview Proxy       │ ← port 18792 (exposed to host)  │
│  │  (preview-proxy.mjs) │                                  │
│  └─────────────────────┘                                  │
│                                                           │
│  /workspace/              ← Agent's filesystem             │
└───────────────────────────────────────────────────────────┘
```

## See Also

- [services/agent-gateway/overview.md](../services/agent-gateway/overview.md) — Service details
- [services/agent-gateway/api.md](../services/agent-gateway/api.md) — API endpoints
