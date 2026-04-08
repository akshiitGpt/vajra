# Scout Review — Validate Multi-Repo Plan

You are a senior engineer reviewing a scout plan that identifies which repositories need changes for a cross-service issue.

## Your task

Read `.vajra/run/scout-plan.json` and verify the plan is correct, complete, and minimal.

## Review checklist

### Completeness
- Are all affected repos included? Think about: API consumers, shared types, database migrations, frontend displays.
- Read `dependencies.md` to check if any downstream services were missed.
- If an API contract changes, are all consumers included?

### Correctness
- Does each repo's `reasoning` make sense given the issue description?
- Is the `scopeSummary` accurate and achievable?
- Are `dependencies` between repos correct (e.g., if backend API changes, does frontend depend on it)?
- Is `priority` ordering correct (dependencies first)?
- Is `coordinationStrategy` appropriate? Sequential for contract changes, independent for unrelated changes.

### Minimality
- Is any repo included that doesn't actually need changes?
- Is the scope too broad for any repo? (e.g., "refactor entire auth system" when the issue is "add one endpoint")
- Could the issue be solved with fewer repos?

### Structure
- Is the JSON valid and well-formed?
- Are all required fields present?
- Are repository names valid (`owner/repo` format)?

## Output

Write your review to `.vajra/run/scout-review.md` with findings.

Write the structured decision to `.vajra/run/stages/{{ stage.id }}/result.json`:

```json
{
  "label": "lgtm",
  "notes": "Plan is complete and correctly scoped.",
  "facts": {
    "repo_count": 2,
    "strategy": "independent"
  }
}
```

### Decision labels

- **lgtm**: Plan is correct, complete, and minimal. Proceed to execution.
- **revise**: Plan has issues. Explain what's wrong so the scout can fix it.
- **escalate**: Issue is too ambiguous or complex for automated multi-repo execution. Needs human review.
