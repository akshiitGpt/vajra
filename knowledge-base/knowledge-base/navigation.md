---
title: "Agent Navigation Guide"
category: navigation
tags: [navigation, agent, guide]
owner: "system"
last_updated: "2026-03-31"
source: manual
---

# Agent Navigation Guide

## How to Explore This Knowledge Base

Follow this workflow to find information efficiently with minimal token usage.

### 1. Read the Index

```bash
cat knowledge-base/index.md
```

This gives you the full map of every document, organized by topic.

### 2. Search with ripgrep

```bash
# Find files mentioning a topic
rg "redis streams" knowledge-base/

# Search within a specific section
rg "agent-platform" knowledge-base/architecture/

# Find files by tag
rg "tags:.*kafka" knowledge-base/
```

### 3. Read Only What You Need

Never read entire files. Use targeted reads:

```bash
# First 50 lines (front-matter + summary)
head -n 50 knowledge-base/services/agent-platform/overview.md

# Specific line range from search results
sed -n '30,80p' knowledge-base/architecture/data-flow.md
```

### 4. Find Files by Name

```bash
# Find files related to a topic
find knowledge-base -name "*agent*" -type f

# List all docs
find knowledge-base -name '*.md' -type f | sort
```

### 5. Follow Cross-References

Documents link to related files with `See Also:` sections. Follow these to navigate between topics.

## Document Structure

Every file starts with YAML front-matter:

```yaml
---
title: "Document Title"
category: architecture
tags: [tag1, tag2]
owner: "@username"
last_updated: "YYYY-MM-DD"
source: manual
---
```

## Directory Map

```
knowledge-base/
  index.md              ← Start here
  navigation.md         ← You are here
  glossary.md           ← Company terminology

  architecture/         ← System-level understanding
  services/             ← Per-service deep dives (most important)
  repos/                ← Code-level repo guides
  data/                 ← Schemas, events, pipelines
  infra/                ← Kubernetes, CI/CD, environments
  workflows/            ← End-to-end request flows
  runbooks/             ← Operational procedures
  references/           ← Quick lookup tables
```

## Recommended Search Order

For most questions:

```
1. index.md → find the right section
2. architecture/service-map.md → understand which services are involved
3. services/<name>/overview.md → service-level understanding
4. workflows/<flow>.md → how the pieces connect
5. data/events/ or data/schemas/ → data contracts
6. repos/<name>.md → code-level navigation
```
