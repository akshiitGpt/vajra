import { readFile } from "node:fs/promises";
import path from "node:path";

import { CommandRunner } from "./process";
import { ScoutPlanArtifact, StageMetadata } from "./types";
import { BuiltInToolExecutionResult } from "./pr-tools";

function resolveCloneUrl(repoPlan: { repository: string; cloneUrl?: string }): string {
  if (repoPlan.cloneUrl) {
    return repoPlan.cloneUrl.endsWith(".git") ? repoPlan.cloneUrl : `${repoPlan.cloneUrl}.git`;
  }
  return `https://github.com/${repoPlan.repository}.git`;
}

export async function executePreflightRepos(
  cwd: string,
  commandRunner: CommandRunner,
  signal?: AbortSignal,
): Promise<BuiltInToolExecutionResult> {
  const start = Date.now();
  const runDir = path.join(cwd, ".vajra", "run");

  let planContent: string;
  try {
    planContent = await readFile(path.join(runDir, "scout-plan-validated.json"), "utf8");
  } catch {
    try {
      planContent = await readFile(path.join(runDir, "scout-plan.json"), "utf8");
    } catch {
      return {
        stdout: "",
        stderr: "Preflight failed: no scout plan found (tried scout-plan-validated.json and scout-plan.json)",
        exitCode: 1,
        durationMs: Date.now() - start,
      };
    }
  }

  let plan: ScoutPlanArtifact;
  try {
    plan = JSON.parse(planContent) as ScoutPlanArtifact;
  } catch {
    return {
      stdout: "",
      stderr: "Preflight failed: scout plan is not valid JSON",
      exitCode: 1,
      durationMs: Date.now() - start,
    };
  }

  if (!plan.repos || plan.repos.length === 0) {
    return {
      stdout: "",
      stderr: "Preflight failed: scout plan has no repos",
      exitCode: 1,
      durationMs: Date.now() - start,
    };
  }

  const results: Array<{ repository: string; cloneUrl: string; ok: boolean; error?: string }> = [];

  for (const repo of plan.repos) {
    const cloneUrl = resolveCloneUrl(repo);
    try {
      const result = await commandRunner.run(`git ls-remote --exit-code ${cloneUrl}`, {
        cwd,
        timeoutMs: 30_000,
        signal,
      });
      if (result.exitCode === 0) {
        results.push({ repository: repo.repository, cloneUrl, ok: true });
      } else {
        results.push({
          repository: repo.repository,
          cloneUrl,
          ok: false,
          error: result.stderr.trim() || `exit code ${result.exitCode}`,
        });
      }
    } catch (error) {
      results.push({
        repository: repo.repository,
        cloneUrl,
        ok: false,
        error: error instanceof Error ? error.message : String(error),
      });
    }
  }

  const passed = results.filter((r) => r.ok);
  const failed = results.filter((r) => !r.ok);

  if (failed.length > 0) {
    const failedLines = failed.map((r) => `  - ${r.repository} (${r.cloneUrl}): ${r.error}`).join("\n");
    const passedLines = passed.map((r) => `  - ${r.repository}`).join("\n");
    const stderr = [
      `Preflight failed: ${failed.length} repo(s) unreachable`,
      failedLines,
      ...(passed.length > 0 ? [`\nReachable (${passed.length}):`, passedLines] : []),
    ].join("\n");

    return {
      stdout: "",
      stderr,
      exitCode: 1,
      durationMs: Date.now() - start,
    };
  }

  const lines = passed.map((r) => `  - ${r.repository} (${r.cloneUrl})`).join("\n");
  const stdout = [
    `Preflight passed: ${passed.length} repo(s) verified`,
    lines,
  ].join("\n");

  const resultMetadata: StageMetadata = {
    facts: {
      verified_repos: passed.length,
      repo_count: plan.repos.length,
      repositories: plan.repos.map((r) => r.repository),
    },
  };

  return {
    stdout,
    stderr: "",
    exitCode: 0,
    durationMs: Date.now() - start,
    resultMetadata,
  };
}
