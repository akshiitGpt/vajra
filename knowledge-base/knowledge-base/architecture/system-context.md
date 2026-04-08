---
title: "System Context"
category: architecture
tags: [architecture, context, boundaries]
owner: "@team-lead"
last_updated: "2026-03-31"
source: manual
---

# System Context

## External Actors

| Actor | Interaction | Protocol |
|-------|------------|----------|
| **End Users** | Chat with agents via web UI or API | HTTP/SSE, WebSocket |
| **Telegram Users** | Chat via Telegram bot | Telegram Bot API → Communication Service |
| **Slack Users** | Chat via Slack integration | Slack Events API → Communication Service |
| **Email Users** | Interact via email | SMTP/IMAP → Communication Service |

## External Dependencies

| Dependency | Purpose | Used By |
|------------|---------|---------|
| **OpenRouter** | LLM inference routing (Claude, GPT, etc.) | AI Gateway |
| **OpenAI API** | Direct LLM access | Agent Platform (fallback) |
| **Anthropic API** | Direct Claude access | Agent Platform (fallback) |
| **Google Gemini** | Gemini model access | Agent Platform |
| **Tavily** | Web search API | Agent Platform (web search subagent) |
| **Daytona** | Cloud sandbox provisioning | Agent Platform (code execution) |
| **Google Cloud Storage** | File storage for generated artifacts | Agent Platform |
| **Langfuse** | LLM observability and tracing | Agent Platform |
| **SigNoz** | OpenTelemetry backend (traces, metrics) | Agent Platform |
| **MCP Servers** | External tool integrations (Gmail, Jira, Slack, GitHub) | Agent Platform via MCP Gateway |

## System Boundary

```
┌─────────────────────────────────────────────────────────┐
│                      Ruh AI Platform                     │
│                                                          │
│  ┌──────────┐  ┌──────────────┐  ┌───────────────────┐  │
│  │ API GW   │  │Agent Platform│  │ Agent Gateway      │  │
│  │ (HTTP/   │  │  (Python     │  │ (Docker sandboxes) │  │
│  │  SSE)    │  │   worker)    │  │                    │  │
│  └────┬─────┘  └──────┬───────┘  └────────┬──────────┘  │
│       │               │                    │             │
│  ┌────▼───────────────▼────────────────────▼──────────┐  │
│  │        Redis · MongoDB · Qdrant · Kafka             │  │
│  └─────────────────────────────────────────────────────┘  │
│                                                          │
│  ┌──────────────┐  ┌───────────────────┐                 │
│  │  AI Gateway  │  │ Communication Svc │                 │
│  └──────┬───────┘  └────────┬──────────┘                 │
└─────────┼───────────────────┼────────────────────────────┘
          │                   │
          ▼                   ▼
   LLM Providers      Telegram/Slack/Email
   (OpenRouter,       (external platforms)
    OpenAI, etc.)
```

## Trust Boundaries

- **Public internet** → API Gateway (authenticated)
- **API Gateway** → Internal services (trusted, internal network)
- **Agent Platform** → LLM providers (API key authenticated)
- **Agent Platform** → MCP servers (OAuth / API key per integration)
- **Agent containers** → Isolated Docker network, no direct internet access except via proxy

## See Also

- [architecture/overview.md](overview.md)
- [architecture/service-map.md](service-map.md)
