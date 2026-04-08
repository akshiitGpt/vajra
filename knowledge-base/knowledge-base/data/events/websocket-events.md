---
title: "WebSocket Events"
category: data
tags: [data, events, websocket, agent-gateway]
owner: "@backend"
last_updated: "2026-03-31"
source: manual
---

# WebSocket Event Contracts

Used between the Agent Gateway (ruhclaw) backend and the React frontend.

## Connection

```
ws://host/ws/chat/:agentId
```

## Client → Server

```json
{ "type": "message", "content": "user message text" }
```

## Server → Client

### chunk

Streaming text delta from the AI.

```json
{ "type": "chunk", "content": "partial text..." }
```

### tool_call_start

Agent started invoking a tool.

```json
{ "type": "tool_call_start", "name": "create_file", "args": { "path": "src/App.tsx" } }
```

### tool_call_update

Partial tool output.

```json
{ "type": "tool_call_update", "content": "partial output..." }
```

### tool_call_result

Tool execution complete.

```json
{ "type": "tool_call_result", "name": "create_file", "result": "File created", "error": null }
```

### file_change

File created, modified, or deleted in the agent workspace.

```json
{ "type": "file_change", "event": "create", "path": "/workspace/src/App.tsx" }
```

### done

Agent turn complete.

```json
{ "type": "done" }
```

### error

Error occurred.

```json
{ "type": "error", "message": "Container not ready" }
```

## See Also

- [services/agent-gateway/api.md](../../services/agent-gateway/api.md) — API details
- [services/agent-gateway/events.md](../../services/agent-gateway/events.md) — Service-level event docs
