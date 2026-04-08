import { NextResponse } from "next/server";
import fs from "node:fs";
import path from "node:path";

function findKbRoot(): string {
  // Try multiple resolution strategies
  const candidates = [
    path.resolve(process.cwd(), "knowledge-base", "knowledge-base"),       // cwd is vajra root
    path.resolve(process.cwd(), "..", "knowledge-base", "knowledge-base"), // cwd is dashboard/
    path.resolve(__dirname, "..", "..", "..", "..", "..", "knowledge-base", "knowledge-base"), // relative to compiled file
  ];
  for (const candidate of candidates) {
    if (fs.existsSync(candidate)) return candidate;
  }
  return candidates[0]; // fallback
}

const KB_ROOT = findKbRoot();

interface FileEntry {
  path: string;
  name: string;
  dir: string;
}

function walkDir(dir: string, base: string): FileEntry[] {
  const entries: FileEntry[] = [];
  if (!fs.existsSync(dir)) return entries;

  for (const item of fs.readdirSync(dir, { withFileTypes: true })) {
    const fullPath = path.join(dir, item.name);
    const relPath = path.relative(base, fullPath);

    if (item.isDirectory()) {
      entries.push(...walkDir(fullPath, base));
    } else if (item.name.endsWith(".md")) {
      entries.push({
        path: relPath,
        name: item.name,
        dir: path.dirname(relPath),
      });
    }
  }
  return entries;
}

export async function GET() {
  try {
    const files = walkDir(KB_ROOT, KB_ROOT);
    return NextResponse.json({ files });
  } catch (error) {
    return NextResponse.json(
      { error: error instanceof Error ? error.message : "Failed to read knowledge base" },
      { status: 500 },
    );
  }
}
