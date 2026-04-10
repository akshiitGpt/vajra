import { mkdir, readFile, writeFile } from "node:fs/promises";
import path from "node:path";

import { CommandResult } from "./process";
import { ScoutPlanArtifact, ScoutRepoPlan, StageMetadata } from "./types";

interface BuiltInToolExecutionResult extends CommandResult {
  resultMetadata?: StageMetadata;
}

const OWNER_REPO_RE = /^[A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+$/;

function isStringArray(value: unknown): value is string[] {
  return Array.isArray(value) && value.every((item) => typeof item === "string");
}

function validateRepoPlan(entry: unknown, index: number): ScoutRepoPlan {
  if (typeof entry !== "object" || entry === null) {
    throw new Error(`repos[${index}] is not an object`);
  }

  const record = entry as Record<string, unknown>;

  if (typeof record.repository !== "string" || !record.repository) {
    throw new Error(`repos[${index}].repository must be a non-empty string`);
  }
  if (!OWNER_REPO_RE.test(record.repository)) {
    throw new Error(`repos[${index}].repository "${record.repository}" is not in owner/repo format`);
  }
  if (typeof record.baseBranch !== "string" || !record.baseBranch) {
    throw new Error(`repos[${index}].baseBranch must be a non-empty string`);
  }
  if (typeof record.targetBranch !== "string" || !record.targetBranch) {
    throw new Error(`repos[${index}].targetBranch must be a non-empty string`);
  }
  if (record.mergeStrategy !== "pr-only" && record.mergeStrategy !== "auto-merge") {
    throw new Error(`repos[${index}].mergeStrategy must be "pr-only" or "auto-merge"`);
  }
  if (typeof record.reasoning !== "string") {
    throw new Error(`repos[${index}].reasoning must be a string`);
  }
  if (typeof record.scopeSummary !== "string") {
    throw new Error(`repos[${index}].scopeSummary must be a string`);
  }
  if (!isStringArray(record.dependencies)) {
    throw new Error(`repos[${index}].dependencies must be an array of strings`);
  }
  if (typeof record.priority !== "number" || !Number.isFinite(record.priority)) {
    throw new Error(`repos[${index}].priority must be a finite number`);
  }

  const result: ScoutRepoPlan = {
    repository: record.repository,
    baseBranch: record.baseBranch,
    targetBranch: record.targetBranch,
    mergeStrategy: record.mergeStrategy,
    reasoning: record.reasoning,
    scopeSummary: record.scopeSummary,
    dependencies: record.dependencies,
    priority: record.priority,
  };

  if (typeof record.cloneUrl === "string" && record.cloneUrl) {
    result.cloneUrl = record.cloneUrl;
  }

  return result;
}

function validateScoutPlan(raw: unknown): ScoutPlanArtifact {
  if (typeof raw !== "object" || raw === null) {
    throw new Error("scout plan must be a JSON object");
  }

  const record = raw as Record<string, unknown>;

  if (typeof record.issueIdentifier !== "string" || !record.issueIdentifier) {
    throw new Error("issueIdentifier must be a non-empty string");
  }

  if (!Array.isArray(record.repos)) {
    throw new Error("repos must be an array");
  }
  if (record.repos.length === 0) {
    throw new Error("repos array must not be empty");
  }

  const repos = record.repos.map((entry: unknown, index: number) => validateRepoPlan(entry, index));

  // Validate that all dependency references point to repos that exist in the plan.
  const repoNames = new Set(repos.map((r) => r.repository));
  for (const repo of repos) {
    for (const dep of repo.dependencies) {
      if (!repoNames.has(dep)) {
        throw new Error(
          `repos entry "${repo.repository}" lists dependency "${dep}" which is not in the plan (known: ${[...repoNames].join(", ")})`,
        );
      }
    }
  }

  if (typeof record.crossRepoNotes !== "string") {
    throw new Error("crossRepoNotes must be a string");
  }
  if (record.coordinationStrategy !== "independent" && record.coordinationStrategy !== "sequential") {
    throw new Error('coordinationStrategy must be "independent" or "sequential"');
  }

  return {
    issueIdentifier: record.issueIdentifier,
    repos,
    crossRepoNotes: record.crossRepoNotes,
    coordinationStrategy: record.coordinationStrategy,
  };
}

export async function executeScoutPlanValidation(cwd: string): Promise<BuiltInToolExecutionResult> {
  const start = Date.now();
  const inputPath = path.join(cwd, ".vajra", "run", "scout-plan.json");

  try {
    const content = await readFile(inputPath, "utf8");
    const raw: unknown = JSON.parse(content);
    const plan = validateScoutPlan(raw);

    const outputPath = path.join(cwd, ".vajra", "run", "scout-plan-validated.json");
    await mkdir(path.dirname(outputPath), { recursive: true });
    await writeFile(outputPath, JSON.stringify(plan, null, 2), "utf8");

    const repoList = plan.repos.map((r) => `  - ${r.repository} (priority ${r.priority})`).join("\n");
    const stdout = [
      `Scout plan validated: ${plan.repos.length} repo(s) for ${plan.issueIdentifier}`,
      `Coordination: ${plan.coordinationStrategy}`,
      repoList,
      `Validated plan written to ${outputPath}`,
    ].join("\n");

    return {
      stdout,
      stderr: "",
      exitCode: 0,
      durationMs: Date.now() - start,
      resultMetadata: {
        facts: {
          issue_identifier: plan.issueIdentifier,
          repo_count: plan.repos.length,
          coordination_strategy: plan.coordinationStrategy,
          repositories: plan.repos.map((r) => r.repository),
        },
      },
    };
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    return {
      stdout: "",
      stderr: `Scout plan validation failed: ${message}`,
      exitCode: 1,
      durationMs: Date.now() - start,
    };
  }
}
