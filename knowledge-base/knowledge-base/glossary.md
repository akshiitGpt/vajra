---
title: "Glossary"
category: reference
tags: [glossary, terminology, definitions]
owner: "@team-lead"
last_updated: "2026-03-31"
source: manual
---

# Glossary

## Platform Concepts

**Agent**
An AI assistant instance configured with specific capabilities, tools, and system prompts. Runs inside the agent-platform worker.

**Agent Platform**
The core Python worker service that executes AI agents. Consumes requests from Redis Streams, runs LangGraph-based agent graphs, and streams responses back.

**Agent Gateway (ruhclaw)**
The TypeScript service that orchestrates agent sandboxes. Spins up Docker containers with OpenClaw, manages WebSocket connections, and provides the chat UI.

**AI Gateway**
A proxy service that routes LLM requests to providers (OpenRouter, OpenAI, Anthropic). Handles model selection, rate limiting, and failover.

**Communication Service**
Handles cross-platform messaging — delivers agent responses to Telegram, Slack, Email, and other channels.

**Workspace**
An isolated Docker container running an OpenClaw agent. Contains a filesystem, dev server, and tools for code execution.

## Technical Terms

**Checkpoint**
A LangGraph conversation state snapshot stored in MongoDB or PostgreSQL. Enables conversation continuity across agent restarts.

**Consumer Group**
A Redis Streams concept — multiple workers in a consumer group split the message load for horizontal scaling.

**DeepAgents**
The multi-agent framework used in agent-platform. A supervisor agent delegates to specialized subagents (knowledge base, web search, code executor, etc.).

**LangGraph**
A framework for building stateful, multi-step AI agent workflows as directed graphs. Used in agent-platform for agent orchestration.

**MCP (Model Context Protocol)**
A protocol for giving AI agents access to external tools (Gmail, Jira, Slack, GitHub, etc.) via a standardized interface.

**Mem0**
Long-term memory system backed by Qdrant vector database. Stores and retrieves user/agent memories across conversations.

**OpenClaw**
An open-source AI agent gateway that runs inside Docker containers. Manages tool execution, file operations, and dev server lifecycle.

**Redis Streams**
The primary message broker for agent-platform. Used for request/response communication between the API gateway and agent workers.

**SSE (Server-Sent Events)**
One-way streaming protocol used by the API gateway to stream agent responses to web clients.

## Environment Abbreviations

**DLQ** — Dead Letter Queue (failed messages after max retries)
**IC** — Incident Commander
**OTEL** — OpenTelemetry
**RPO** — Recovery Point Objective
**RTO** — Recovery Time Objective
**SEV** — Severity level (SEV1–SEV4)
