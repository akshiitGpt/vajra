import { readFile, writeFile, mkdir } from "node:fs/promises";
import path from "node:path";
import {
  DispatchPlan,
  Issue,
  MultiRepoConfig,
  MultiRepoDispatchPlan,
  MultiRepoRunEntry,
  PipelineRunHandle,
  PipelineRunResult,
  PipelineRunner,
  ScoutPlanArtifact,
  TrackerClient,
  WorkflowDefinition,
  GitHubConfig,
} from "./types";
import { VajraEventBus } from "./events";
import { GitHubClient } from "./github";
import { WorkspaceManager, safeIdentifier } from "./workspace";
import { log } from "./logger";

async function runWithConcurrency<T>(tasks: (() => Promise<T>)[], maxConcurrent: number): Promise<T[]> {
  const results: T[] = [];
  const executing = new Set<Promise<void>>();
  for (const task of tasks) {
    const p = task().then(result => { results.push(result); });
    executing.add(p);
    void p.finally(() => executing.delete(p));
    if (executing.size >= maxConcurrent) {
      await Promise.race(executing);
    }
  }
  await Promise.all(executing);
  return results;
}

function repoNameFromFullName(fullName: string): string {
  // "owner/repo" -> "repo", plain "repo" -> "repo"
  const parts = fullName.split("/");
  return parts[parts.length - 1];
}

export class MultiRepoCoordinator {
  constructor(
    private readonly config: MultiRepoConfig,
    private readonly createWorkspaceManager: (hookEnv?: Record<string, string>) => WorkspaceManager,
    private readonly tracker: TrackerClient | null,
    private readonly githubConfig: GitHubConfig | null,
    private readonly eventBus?: VajraEventBus,
  ) {}

  /**
   * Returns true if this issue should be handled as a multi-repo dispatch.
   * Simple check: the multi_repo config must exist, and the issue either has
   * a "multi-repo" label or the config declares repos.
   */
  isMultiRepoIssue(issue: Issue): boolean {
    if (!this.config) {
      return false;
    }
    const hasLabel = issue.labels.some(
      (label) => label.toLowerCase() === "multi-repo",
    );
    const hasRepos = Object.keys(this.config.repos).length > 0;
    return hasLabel || hasRepos;
  }

  /**
   * Run the scout phase: execute the scout workflow against the knowledge repo
   * and return the validated plan artifact.
   */
  async runScoutPhase(opts: {
    issue: Issue;
    attempt: number;
    workflow: WorkflowDefinition;
    pipelineRunner: PipelineRunner;
  }): Promise<ScoutPlanArtifact> {
    const { issue, attempt, workflow, pipelineRunner } = opts;
    const scoutIdentifier = `${issue.identifier}__scout`;

    const hookEnv: Record<string, string> = {
      VAJRA_CLONE_URL: `https://github.com/${this.config.knowledgeRepo}.git`,
      VAJRA_BASE_BRANCH: this.config.knowledgeBranch,
    };

    const workspaceManager = this.createWorkspaceManager(hookEnv);
    const workspace = await workspaceManager.prepareWorkspace(scoutIdentifier, hookEnv);
    await workspaceManager.runBeforeRunHook(workspace.path, hookEnv);

    log("multi-repo scout phase started", {
      issue: issue.identifier,
      workspace: workspace.path,
      knowledgeRepo: this.config.knowledgeRepo,
    });

    // Resolve the scout workflow definition from the config
    const scoutWorkflowName = this.config.scoutWorkflow;
    const scoutWorkflowEntry = workflow.config.workflows[scoutWorkflowName];
    if (!scoutWorkflowEntry) {
      throw new Error(`scout workflow "${scoutWorkflowName}" is not configured`);
    }

    const scoutWorkflow: WorkflowDefinition = {
      ...workflow,
      config: {
        ...workflow.config,
        workflowRouting: {
          ...workflow.config.workflowRouting,
          defaultWorkflow: scoutWorkflowName,
        },
      },
    };

    const scoutDispatchPlan: DispatchPlan = {
      workflowName: scoutWorkflowName,
      successState: scoutWorkflowEntry.successState ?? "Done",
      baseBranch: this.config.knowledgeBranch,
      targetBranch: this.config.knowledgeBranch,
      mergeStrategy: "pr-only",
      labelsToAdd: [],
      triage: null,
    };

    const handle: PipelineRunHandle = pipelineRunner.startRun({
      issue,
      attempt,
      workflow: scoutWorkflow,
      workspacePath: workspace.path,
      dispatchPlan: scoutDispatchPlan,
    });

    const result: PipelineRunResult = await handle.promise;

    if (result.status !== "success") {
      throw new Error(
        `scout phase failed with status "${result.status}"${result.error ? `: ${result.error}` : ""}`,
      );
    }

    // Read the validated scout plan (prefer validated, fall back to raw)
    const runDir = path.join(workspace.path, ".vajra", "run");
    let planContent: string;
    try {
      planContent = await readFile(
        path.join(runDir, "scout-plan-validated.json"),
        "utf8",
      );
    } catch {
      planContent = await readFile(
        path.join(runDir, "scout-plan.json"),
        "utf8",
      );
    }

    const scoutPlan: ScoutPlanArtifact = JSON.parse(planContent);

    log("multi-repo scout phase completed", {
      issue: issue.identifier,
      repoCount: scoutPlan.repos.length,
      coordinationStrategy: scoutPlan.coordinationStrategy,
    });

    return scoutPlan;
  }

  /**
   * Execute the per-repo coding pipelines for each repo in the scout plan,
   * respecting maxParallelRepos concurrency.
   */
  async executeRepoPlans(opts: {
    issue: Issue;
    scoutPlan: ScoutPlanArtifact;
    workflow: WorkflowDefinition;
    pipelineRunner: PipelineRunner;
  }): Promise<MultiRepoDispatchPlan> {
    const { issue, scoutPlan, workflow, pipelineRunner } = opts;
    const startedAt = new Date().toISOString();

    const executionWorkflowName = this.config.executionWorkflow;
    const executionWorkflowEntry = workflow.config.workflows[executionWorkflowName];
    if (!executionWorkflowEntry) {
      throw new Error(`execution workflow "${executionWorkflowName}" is not configured`);
    }

    // Sort repos by priority (lower number = higher priority)
    const sortedRepos = [...scoutPlan.repos].sort((a, b) => a.priority - b.priority);

    const multiRepoPlan: MultiRepoDispatchPlan = {
      issueId: issue.id,
      issueIdentifier: issue.identifier,
      scoutPlan,
      repoRuns: [],
      status: "running",
      startedAt,
      finishedAt: null,
    };

    log("multi-repo execution phase started", {
      issue: issue.identifier,
      repos: sortedRepos.map((r) => r.repository),
      maxParallelRepos: this.config.maxParallelRepos,
    });

    const tasks = sortedRepos.map((repoPlan) => async (): Promise<MultiRepoRunEntry> => {
      const repoName = repoNameFromFullName(repoPlan.repository);
      const repoIdentifier = `${issue.identifier}__${safeIdentifier(repoName)}`;

      // Resolve the clone URL from config.repos or construct from the repository name
      const repoConfig = this.config.repos[repoName] ?? this.config.repos[repoPlan.repository];
      const cloneUrl = repoConfig?.url ?? `https://github.com/${repoPlan.repository}.git`;
      const baseBranch = repoPlan.baseBranch;

      const hookEnv: Record<string, string> = {
        VAJRA_CLONE_URL: cloneUrl,
        VAJRA_BASE_BRANCH: baseBranch,
      };

      const workspaceManager = this.createWorkspaceManager(hookEnv);
      const workspace = await workspaceManager.prepareWorkspace(repoIdentifier, hookEnv);
      await workspaceManager.runBeforeRunHook(workspace.path, hookEnv);

      // Write multi-repo context file so the agent knows about cross-repo coordination
      const contextDir = path.join(workspace.path, ".vajra", "run");
      await mkdir(contextDir, { recursive: true });

      const multiRepoContext = {
        crossRepoNotes: scoutPlan.crossRepoNotes,
        thisRepo: {
          repository: repoPlan.repository,
          baseBranch: repoPlan.baseBranch,
          targetBranch: repoPlan.targetBranch,
          scopeSummary: repoPlan.scopeSummary,
          reasoning: repoPlan.reasoning,
          dependencies: repoPlan.dependencies,
          priority: repoPlan.priority,
        },
        otherRepos: scoutPlan.repos
          .filter((r) => r.repository !== repoPlan.repository)
          .map((r) => ({
            repository: r.repository,
            scopeSummary: r.scopeSummary,
            dependencies: r.dependencies,
          })),
      };

      await writeFile(
        path.join(contextDir, "multi-repo-context.json"),
        JSON.stringify(multiRepoContext, null, 2),
        "utf8",
      );

      const dispatchPlan: DispatchPlan = {
        workflowName: executionWorkflowName,
        successState: executionWorkflowEntry.successState ?? "Done",
        baseBranch: repoPlan.baseBranch,
        targetBranch: repoPlan.targetBranch,
        mergeStrategy: repoPlan.mergeStrategy,
        labelsToAdd: [],
        triage: null,
      };

      const entry: MultiRepoRunEntry = {
        repository: repoPlan.repository,
        dispatchPlan,
        status: "running",
        prUrl: null,
        error: null,
      };

      log("multi-repo repo execution started", {
        issue: issue.identifier,
        repository: repoPlan.repository,
        workspace: workspace.path,
      });

      try {
        const executionWorkflow: WorkflowDefinition = {
          ...workflow,
          config: {
            ...workflow.config,
            workflowRouting: {
              ...workflow.config.workflowRouting,
              defaultWorkflow: executionWorkflowName,
            },
          },
        };

        const handle: PipelineRunHandle = pipelineRunner.startRun({
          issue,
          attempt: 0,
          workflow: executionWorkflow,
          workspacePath: workspace.path,
          dispatchPlan,
        });

        const result: PipelineRunResult = await handle.promise;

        if (result.status === "success") {
          entry.status = "success";
          entry.prUrl = result.prUrl ?? result.pr?.url ?? null;
          log("multi-repo repo execution succeeded", {
            issue: issue.identifier,
            repository: repoPlan.repository,
            prUrl: entry.prUrl,
          });
        } else {
          entry.status = "failure";
          entry.error = result.error ?? `pipeline finished with status "${result.status}"`;
          log("multi-repo repo execution failed", {
            issue: issue.identifier,
            repository: repoPlan.repository,
            error: entry.error,
          });
        }
      } catch (error) {
        entry.status = "failure";
        entry.error = error instanceof Error ? error.message : String(error);
        log("multi-repo repo execution error", {
          issue: issue.identifier,
          repository: repoPlan.repository,
          error: entry.error,
        });
      }

      return entry;
    });

    const entries = await runWithConcurrency(tasks, this.config.maxParallelRepos);

    multiRepoPlan.repoRuns = entries;
    multiRepoPlan.finishedAt = new Date().toISOString();

    const hasFailure = entries.some((e) => e.status === "failure");
    const allSuccess = entries.every((e) => e.status === "success");
    multiRepoPlan.status = allSuccess ? "completed" : hasFailure ? "failed" : "completed";

    log("multi-repo execution phase completed", {
      issue: issue.identifier,
      status: multiRepoPlan.status,
      successCount: entries.filter((e) => e.status === "success").length,
      failureCount: entries.filter((e) => e.status === "failure").length,
    });

    return multiRepoPlan;
  }

  /**
   * Post-execution coordination: cross-link PRs and post summary to tracker.
   */
  async coordinateResults(opts: {
    issue: Issue;
    multiRepoPlan: MultiRepoDispatchPlan;
    githubConfig: GitHubConfig;
  }): Promise<void> {
    const { issue, multiRepoPlan, githubConfig } = opts;

    const successfulPrs = multiRepoPlan.repoRuns
      .filter((entry) => entry.status === "success" && entry.prUrl)
      .map((entry) => ({
        repository: entry.repository,
        prUrl: entry.prUrl!,
      }));

    if (successfulPrs.length === 0) {
      log("multi-repo coordination skipped: no successful PRs", {
        issue: issue.identifier,
      });
      return;
    }

    // Cross-link PRs by adding a comment to each one referencing the others
    if (this.config.coordinationComment && successfulPrs.length > 1) {
      const github = new GitHubClient(githubConfig);

      for (const pr of successfulPrs) {
        const otherPrs = successfulPrs.filter((other) => other.prUrl !== pr.prUrl);
        const relatedLinks = otherPrs
          .map((other) => `- ${other.repository}: ${other.prUrl}`)
          .join("\n");

        const commentBody = [
          `**Multi-repo change** -- Part of ${issue.identifier}.`,
          "",
          "Related PRs:",
          relatedLinks,
        ].join("\n");

        // Extract the PR number from the URL (e.g., https://github.com/owner/repo/pull/123)
        const prNumber = extractPrNumberFromUrl(pr.prUrl);
        if (prNumber !== null) {
          try {
            await github.addIssueComment(pr.repository, prNumber, commentBody);
            log("multi-repo coordination comment posted", {
              issue: issue.identifier,
              repository: pr.repository,
              prNumber,
            });
          } catch (error) {
            log("multi-repo coordination comment failed", {
              issue: issue.identifier,
              repository: pr.repository,
              prNumber,
              error: error instanceof Error ? error.message : String(error),
            });
          }
        }
      }
    }

    // Post summary to tracker (Linear)
    if (this.tracker?.commentOnIssue) {
      const prSummaryLines = multiRepoPlan.repoRuns.map((entry) => {
        const statusIcon = entry.status === "success" ? "OK" : "FAILED";
        const prLink = entry.prUrl ? ` -- ${entry.prUrl}` : "";
        const errorNote = entry.error ? ` (${entry.error})` : "";
        return `- [${statusIcon}] ${entry.repository}${prLink}${errorNote}`;
      });

      const summaryBody = [
        `**Vajra multi-repo run** for ${issue.identifier}`,
        "",
        `Status: ${multiRepoPlan.status}`,
        `Repos: ${multiRepoPlan.repoRuns.length}`,
        "",
        ...prSummaryLines,
      ].join("\n");

      try {
        await this.tracker.commentOnIssue(issue.id, summaryBody);
        log("multi-repo tracker summary posted", { issue: issue.identifier });
      } catch (error) {
        log("multi-repo tracker summary failed", {
          issue: issue.identifier,
          error: error instanceof Error ? error.message : String(error),
        });
      }
    }
  }

  /**
   * Main entry point: runs the full multi-repo flow (scout -> execute -> coordinate)
   * and returns a PipelineRunResult compatible with the orchestrator.
   */
  async run(opts: {
    issue: Issue;
    attempt: number;
    workflow: WorkflowDefinition;
    pipelineRunner: PipelineRunner;
  }): Promise<PipelineRunResult> {
    const { issue, attempt, workflow, pipelineRunner } = opts;

    log("multi-repo run started", {
      issue: issue.identifier,
      attempt,
    });

    try {
      // Phase 1: Scout
      const scoutPlan = await this.runScoutPhase({
        issue,
        attempt,
        workflow,
        pipelineRunner,
      });

      // Phase 2: Per-repo execution
      const multiRepoPlan = await this.executeRepoPlans({
        issue,
        scoutPlan,
        workflow,
        pipelineRunner,
      });

      // Phase 3: Coordination
      if (this.githubConfig) {
        await this.coordinateResults({
          issue,
          multiRepoPlan,
          githubConfig: this.githubConfig,
        });
      }

      // Determine overall result
      const allSuccess = multiRepoPlan.repoRuns.every((e) => e.status === "success");
      const hasAnySuccess = multiRepoPlan.repoRuns.some((e) => e.status === "success");

      const completedNodes = multiRepoPlan.repoRuns
        .filter((e) => e.status === "success")
        .map((e) => e.repository);

      const prUrls = multiRepoPlan.repoRuns
        .filter((e) => e.prUrl)
        .map((e) => e.prUrl!);

      if (allSuccess) {
        log("multi-repo run completed successfully", {
          issue: issue.identifier,
          prCount: prUrls.length,
        });

        return {
          status: "success",
          completedNodes,
          checkpointPath: "",
          prUrl: prUrls[0] ?? null,
        };
      }

      const failedRepos = multiRepoPlan.repoRuns
        .filter((e) => e.status === "failure")
        .map((e) => `${e.repository}: ${e.error ?? "unknown"}`);

      const errorMessage = hasAnySuccess
        ? `partial success: ${failedRepos.length} repo(s) failed -- ${failedRepos.join("; ")}`
        : `all repos failed -- ${failedRepos.join("; ")}`;

      log("multi-repo run completed with failures", {
        issue: issue.identifier,
        error: errorMessage,
      });

      return {
        status: "failure",
        completedNodes,
        checkpointPath: "",
        error: errorMessage,
        prUrl: prUrls[0] ?? null,
      };
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : String(error);

      log("multi-repo run failed", {
        issue: issue.identifier,
        error: errorMessage,
      });

      return {
        status: "failure",
        completedNodes: [],
        checkpointPath: "",
        error: errorMessage,
      };
    }
  }
}

function extractPrNumberFromUrl(prUrl: string): number | null {
  // Handles URLs like https://github.com/owner/repo/pull/123
  const match = prUrl.match(/\/pull\/(\d+)$/);
  if (match?.[1]) {
    const num = Number.parseInt(match[1], 10);
    return Number.isFinite(num) ? num : null;
  }
  return null;
}
