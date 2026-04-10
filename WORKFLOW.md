---
tracker:
  kind: linear
  endpoint: https://api.linear.app/graphql
  api_key: $LINEAR_API_KEY
  assignee_id: 38cbbbc9-437e-4bde-9916-f8b2ae8e3ce5
  active_states:
    - Todo
    - In Progress
    - In Development
    - Ready for Development
  terminal_states:
    - Done
    - Canceled
    - Cancelled
    - Duplicate
    - Done on Prod
polling:
  interval_ms: 30000
workspace:
  root: /tmp/vajra-workspaces
artifacts:
  root: ~/vajra-artifacts/issues
  workspace_dir: .vajra
hooks:
  after_create: |
    git clone --depth 1 --branch ${VAJRA_BASE_BRANCH:-main} ${VAJRA_CLONE_URL:-https://github.com/akshiitGpt/vajra.git} .
  before_run: |
    git fetch origin ${VAJRA_BASE_BRANCH:-main}
    git checkout ${VAJRA_BASE_BRANCH:-main}
    git reset --hard origin/${VAJRA_BASE_BRANCH:-main}
    git clean -fd -e .vajra
  timeout_ms: 120000
execution:
  max_concurrent_agents: 5
  max_retry_attempts: 3
  max_retry_backoff_ms: 300000
  max_concurrent_agents_by_state: {}
  max_agent_invocations_per_run: 20
escalation:
  linear_state: Require Changes
  comment: true
  slack_notify: true
workflows:
  default:
    dot_file: pipelines/default.dot
    success_state: Code Review
    inspect_pr: true
  scout:
    dot_file: pipelines/scout.dot
    success_state: In Progress
    inspect_pr: false
  multi-repo:
    dot_file: pipelines/multi-repo.dot
    success_state: Code Review
    inspect_pr: true
workflow_routing:
  default_workflow: multi-repo
  by_label: {}
backends:
  claude:
    command: claude --model {{ model }} --permission-mode bypassPermissions -p {{ prompt | shellquote }}
  codex:
    command: codex exec --model {{ model }} {{ prompt | shellquote }}
agents:
  planner:
    backend: claude
    model: claude-opus-4-6
    prompt: |-
      Use the `vajra-plan` skill.

      Issue: {{ issue.identifier }} — {{ issue.title }}
      {{ issue.description }}

      If `.vajra/run/multi-repo-context.json` exists, read it first.
      It contains scout findings: which repos are affected, your repo's scope, cross-repo notes, and related repos.
      Incorporate the scout's scopeSummary and reasoning into your plan.

      If `.vajra/run/plan.md` already exists, revise it instead of starting over.
      If `.vajra/run/plan-review.md` exists, address that review directly.

      Write: .vajra/run/plan.md
    reasoning_effort: high
  plan-reviewer:
    backend: claude
    model: claude-opus-4-6
    prompt: |-
      Use the `vajra-plan-review` skill.

      Issue: {{ issue.identifier }} — {{ issue.title }}
      {{ issue.description }}

      Read: .vajra/run/plan.md
      Write: .vajra/run/plan-review.md
      Write structured review outcome: .vajra/run/stages/{{ stage.id }}/result.json
      Allowed labels: lgtm, revise, escalate
    reasoning_effort: high
  coder:
    backend: claude
    model: claude-opus-4-6
    prompt: |-
      Use the `vajra-implement` skill.

      Issue: {{ issue.identifier }} — {{ issue.title }}
      {{ issue.description }}

      Read: .vajra/run/plan.md
      If `.vajra/run/code-review.md` exists, address that review while keeping the implementation scoped.
      Write: .vajra/run/implementation-summary.md
    reasoning_effort: high
  code-reviewer:
    backend: claude
    model: claude-opus-4-6
    prompt: |-
      Use the `vajra-code-review` skill.

      Issue: {{ issue.identifier }} — {{ issue.title }}
      {{ issue.description }}

      Read: .vajra/run/plan.md, .vajra/run/implementation-summary.md
      Inspect: the actual code changes (git diff, changed files)
      Write: .vajra/run/code-review.md
      Write structured review outcome: .vajra/run/stages/{{ stage.id }}/result.json
      Allowed labels: lgtm, revise, escalate
    reasoning_effort: high
  fixer:
    backend: claude
    model: claude-opus-4-6
    prompt: |-
      Use the `vajra-fix` skill.

      Issue: {{ issue.identifier }} — {{ issue.title }}
      {{ issue.description }}

      Read: .vajra/run/code-review.md, .vajra/run/implementation-summary.md
      Update: .vajra/run/implementation-summary.md
    reasoning_effort: high
  pr-preparer:
    backend: claude
    model: claude-sonnet-4-6
    prompt: |-
      Use the `vajra-prepare-pr` skill.

      Issue: {{ issue.identifier }} — {{ issue.title }}
      {{ issue.description }}

      Read: .vajra/run/plan.md, .vajra/run/implementation-summary.md, .vajra/run/code-review.md
      Write: .vajra/pr-title.txt, .vajra/run/pr-body.md
      Branch: vajra/{{ issue.identifier | downcase }}
      Target: origin/{{ target_branch }}
      Do not create or update the PR directly. The workflow's publish_pr tool step owns GitHub mutation.
    reasoning_effort: high
  scout:
    backend: claude
    model: claude-opus-4-6
    prompt: |-
      Use the `vajra-scout` skill.

      Issue: {{ issue.identifier }} — {{ issue.title }}
      {{ issue.description }}

      Read: repos/*.md files for repository URLs (repo_url in frontmatter)
      Read: services/*/overview.md files for service descriptions and dependencies
      Investigate: clone candidate repos temporarily and check relevant code
      Write: .vajra/run/scout-plan.json (include cloneUrl from repos/*.md frontmatter)
    reasoning_effort: high
    skills:
      - vajra-scout
  scout-reviewer:
    backend: claude
    model: claude-opus-4-6
    prompt: |-
      Use the `vajra-scout-review` skill.

      Issue: {{ issue.identifier }} — {{ issue.title }}
      {{ issue.description }}

      Read: .vajra/run/scout-plan.json
      Write: .vajra/run/scout-review.md
      Write structured review outcome: .vajra/run/stages/{{ stage.id }}/result.json
      Allowed labels: lgtm, revise, escalate
    reasoning_effort: high
    skills:
      - vajra-scout-review
github:
  repository: akshiitGpt/vajra
  api_key: $GITHUB_TOKEN
  webhook_secret: vajra-webhook-placeholder
  revision_label: vajra-revision
  revision_command: /vajra revise
  revision_state: In Progress
  merged_state: Done on Prod
  closed_state: null
multi_repo:
  knowledge_repo: ruh-ai/ruh-knowledge-base
  knowledge_branch: master
  workflow: multi-repo
  scout_workflow: scout
  execution_workflow: default
  max_parallel_repos: 3
  coordination_comment: true
  repos:
    agent-platform:
      url: https://github.com/ruh-ai/agent-platform-v2
      default_branch: main
      stack: Python, Poetry, LangGraph, Redis Streams
    agent-gateway:
      url: https://github.com/ruh-ai/agent-gateway
      default_branch: main
      stack: Python, FastAPI, gRPC, Redis Streams, SSE
    communication-service:
      url: https://github.com/ruh-ai/communication-service
      default_branch: main
      stack: Python, gRPC, MongoDB, Beanie
    ai-gateway:
      url: https://github.com/ruh-ai/ai-gateway
      default_branch: main
      stack: Python, FastAPI, OpenRouter, Kafka
    file-conversion:
      url: https://github.com/ruh-ai/file-conversion
      default_branch: main
      stack: Python, FastAPI, file processing
    ruh-super-admin-fe:
      url: https://github.com/ruh-ai/ruh-super-admin-fe
      default_branch: main
      stack: TypeScript, React, Next.js
---
