import { mkdir, readFile, readdir, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";

import { GitHubClient } from "./github";
import { log } from "./logger";
import { CommandRunner } from "./process";
import { GitHubConfig, MultiRepoRunEntry } from "./types";

interface KnowledgeBaseUpdateResult {
  prUrl: string | null;
  updatedFiles: string[];
}

interface RepoDocMatch {
  filePath: string;
  repoUrl: string;
  content: string;
}

function extractFrontmatterField(content: string, field: string): string | null {
  const fmMatch = content.match(/^---\n([\s\S]*?)\n---/);
  if (!fmMatch) return null;
  const fm = fmMatch[1];
  const fieldMatch = fm.match(new RegExp(`^${field}:\\s*"?([^"\\n]+)"?`, "m"));
  return fieldMatch?.[1]?.trim() ?? null;
}

function updateFrontmatterField(content: string, field: string, value: string): string {
  const fmMatch = content.match(/^(---\n)([\s\S]*?)(\n---)/);
  if (!fmMatch) return content;

  const prefix = fmMatch[1];
  const fm = fmMatch[2];
  const suffix = fmMatch[3];
  const rest = content.slice(fmMatch[0].length);

  const fieldRegex = new RegExp(`^(${field}:\\s*).*$`, "m");
  if (fieldRegex.test(fm)) {
    const updatedFm = fm.replace(fieldRegex, `$1"${value}"`);
    return `${prefix}${updatedFm}${suffix}${rest}`;
  }

  return `${prefix}${fm}\n${field}: "${value}"${suffix}${rest}`;
}

function appendRecentChange(content: string, entry: {
  prUrl: string;
  issueIdentifier: string;
  date: string;
  summary: string;
}): string {
  const changeLine = `- [${entry.issueIdentifier}](${entry.prUrl}) — ${entry.summary} (${entry.date})`;

  const sectionMatch = content.match(/\n## Recent Changes\n/);
  if (sectionMatch && sectionMatch.index !== undefined) {
    const insertPos = sectionMatch.index + sectionMatch[0].length;
    return content.slice(0, insertPos) + changeLine + "\n" + content.slice(insertPos);
  }

  // Find "See Also" section to insert before it, or append at end
  const seeAlsoMatch = content.match(/\n## See Also/i);
  if (seeAlsoMatch && seeAlsoMatch.index !== undefined) {
    return (
      content.slice(0, seeAlsoMatch.index) +
      "\n## Recent Changes\n" +
      changeLine +
      "\n" +
      content.slice(seeAlsoMatch.index)
    );
  }

  return content + "\n\n## Recent Changes\n" + changeLine + "\n";
}

function extractFirstSentence(summary: string): string {
  // Get a one-line summary from the implementation summary
  const lines = summary.split("\n").filter((line) => line.trim().length > 0);
  // Try to find a "# Changes" heading and get the first bullet
  const changesIdx = lines.findIndex((line) => /^#\s*Changes/i.test(line));
  if (changesIdx >= 0) {
    for (let i = changesIdx + 1; i < lines.length; i++) {
      const line = lines[i].trim();
      if (line.startsWith("-") || line.startsWith("*")) {
        return line.replace(/^[-*]\s*/, "").slice(0, 120);
      }
    }
  }
  // Fall back to first non-heading, non-empty line
  for (const line of lines) {
    if (!line.startsWith("#") && line.trim().length > 10) {
      return line.trim().slice(0, 120);
    }
  }
  return "Multi-repo update";
}

async function findRepoDoc(kbReposDir: string, repoUrl: string): Promise<RepoDocMatch | null> {
  let entries: string[];
  try {
    entries = await readdir(kbReposDir);
  } catch {
    return null;
  }

  // Normalize the URL for comparison (strip trailing .git, trailing slashes)
  const normalizedUrl = repoUrl.replace(/\.git$/, "").replace(/\/$/, "").toLowerCase();

  for (const entry of entries) {
    if (!entry.endsWith(".md")) continue;
    const filePath = path.join(kbReposDir, entry);
    try {
      const content = await readFile(filePath, "utf8");
      const docUrl = extractFrontmatterField(content, "repo_url");
      if (docUrl) {
        const normalizedDocUrl = docUrl.replace(/\.git$/, "").replace(/\/$/, "").toLowerCase();
        if (normalizedDocUrl === normalizedUrl) {
          return { filePath, repoUrl: docUrl, content };
        }
      }
    } catch {
      continue;
    }
  }

  return null;
}

export async function updateKnowledgeBase(opts: {
  knowledgeRepo: string;
  knowledgeBranch: string;
  issueIdentifier: string;
  repoRuns: MultiRepoRunEntry[];
  githubConfig: GitHubConfig;
  commandRunner: CommandRunner;
  workspaceRoot: string;
}): Promise<KnowledgeBaseUpdateResult> {
  const successfulRuns = opts.repoRuns.filter(
    (entry) => entry.status === "success" && entry.prUrl,
  );

  if (successfulRuns.length === 0) {
    return { prUrl: null, updatedFiles: [] };
  }

  const tempDir = path.join(opts.workspaceRoot, `vajra-kb-update-${Date.now()}`);
  await mkdir(tempDir, { recursive: true });

  const cloneUrl = `https://github.com/${opts.knowledgeRepo}.git`;
  const branch = `vajra/kb-update-${opts.issueIdentifier.toLowerCase()}`;
  const today = new Date().toISOString().slice(0, 10);

  try {
    // Clone the knowledge repo
    const cloneResult = await opts.commandRunner.run(
      `git clone --depth 1 --branch ${opts.knowledgeBranch} ${cloneUrl} .`,
      { cwd: tempDir, timeoutMs: 60_000 },
    );
    if (cloneResult.exitCode !== 0) {
      throw new Error(`Failed to clone knowledge repo: ${cloneResult.stderr}`);
    }

    // Create the update branch
    const branchResult = await opts.commandRunner.run(
      `git checkout -b ${branch}`,
      { cwd: tempDir, timeoutMs: 10_000 },
    );
    if (branchResult.exitCode !== 0) {
      throw new Error(`Failed to create branch: ${branchResult.stderr}`);
    }

    // Find the knowledge-base/repos/ directory (may be nested)
    const kbReposDir = await findKbReposDir(tempDir);
    if (!kbReposDir) {
      log("knowledge-base-updater: no repos/ directory found in knowledge repo", {});
      return { prUrl: null, updatedFiles: [] };
    }

    const updatedFiles: string[] = [];

    for (const run of successfulRuns) {
      // Build a URL to match against (from repository "owner/repo")
      const repoUrl = `https://github.com/${run.repository}`;
      const doc = await findRepoDoc(kbReposDir, repoUrl);

      if (!doc) {
        log("knowledge-base-updater: no matching doc found for repo", {
          repository: run.repository,
        });
        continue;
      }

      let content = doc.content;

      // Update last_updated
      content = updateFrontmatterField(content, "last_updated", today);

      // Append recent change entry
      const summary = run.implementationSummary
        ? extractFirstSentence(run.implementationSummary)
        : "Multi-repo update";

      content = appendRecentChange(content, {
        prUrl: run.prUrl!,
        issueIdentifier: opts.issueIdentifier,
        date: today,
        summary,
      });

      await writeFile(doc.filePath, content, "utf8");
      updatedFiles.push(path.relative(tempDir, doc.filePath));
    }

    if (updatedFiles.length === 0) {
      log("knowledge-base-updater: no files updated, skipping PR", {});
      return { prUrl: null, updatedFiles: [] };
    }

    // Commit and push
    await opts.commandRunner.run("git add -A", { cwd: tempDir, timeoutMs: 10_000 });

    const commitMessage = `chore: update knowledge base for ${opts.issueIdentifier}\n\nUpdated: ${updatedFiles.join(", ")}`;
    const commitResult = await opts.commandRunner.run(
      `git commit -m "${commitMessage.replace(/"/g, '\\"')}"`,
      { cwd: tempDir, timeoutMs: 10_000 },
    );
    if (commitResult.exitCode !== 0) {
      throw new Error(`Failed to commit: ${commitResult.stderr}`);
    }

    const pushResult = await opts.commandRunner.run(
      `git push -u origin ${branch}`,
      { cwd: tempDir, timeoutMs: 60_000 },
    );
    if (pushResult.exitCode !== 0) {
      throw new Error(`Failed to push: ${pushResult.stderr}`);
    }

    // Create PR on the knowledge repo
    const github = new GitHubClient(opts.githubConfig);
    const prBody = [
      `## Knowledge Base Update for ${opts.issueIdentifier}`,
      "",
      "Updated repo documentation with recent changes:",
      "",
      ...successfulRuns
        .filter((run) => updatedFiles.some((f) => f.includes(run.repository.split("/").pop()!)))
        .map((run) => `- **${run.repository}**: ${run.prUrl}`),
      "",
      "---",
      "*Automated by Vajra multi-repo coordinator*",
    ].join("\n");

    const pr = await github.createPullRequest(opts.knowledgeRepo, {
      title: `chore: KB update for ${opts.issueIdentifier}`,
      body: prBody,
      head: branch,
      base: opts.knowledgeBranch,
    });

    log("knowledge-base-updater: PR created", {
      prUrl: pr.url,
      updatedFiles,
    });

    return { prUrl: pr.url, updatedFiles };
  } finally {
    // Cleanup temp directory
    try {
      await opts.commandRunner.run(`rm -rf ${tempDir}`, {
        cwd: opts.workspaceRoot,
        timeoutMs: 10_000,
      });
    } catch {
      // best-effort cleanup
    }
  }
}

async function findKbReposDir(root: string): Promise<string | null> {
  // Try common paths: knowledge-base/repos/, knowledge-base/knowledge-base/repos/, repos/
  const candidates = [
    path.join(root, "knowledge-base", "repos"),
    path.join(root, "knowledge-base", "knowledge-base", "repos"),
    path.join(root, "repos"),
  ];

  for (const candidate of candidates) {
    try {
      const entries = await readdir(candidate);
      if (entries.some((e) => e.endsWith(".md"))) {
        return candidate;
      }
    } catch {
      continue;
    }
  }

  return null;
}
