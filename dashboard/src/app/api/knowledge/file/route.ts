import { NextRequest, NextResponse } from "next/server";
import fs from "node:fs";
import path from "node:path";

const KB_ROOT = path.resolve(process.cwd(), "..", "knowledge-base", "knowledge-base");

function safePath(filePath: string): string | null {
  const resolved = path.resolve(KB_ROOT, filePath);
  if (!resolved.startsWith(KB_ROOT)) return null;
  return resolved;
}

export async function GET(request: NextRequest) {
  const filePath = request.nextUrl.searchParams.get("path");
  if (!filePath) {
    return NextResponse.json({ error: "Missing path parameter" }, { status: 400 });
  }

  const resolved = safePath(filePath);
  if (!resolved) {
    return NextResponse.json({ error: "Invalid path" }, { status: 400 });
  }

  try {
    const content = fs.readFileSync(resolved, "utf8");
    return NextResponse.json({ content, path: filePath });
  } catch (error) {
    if ((error as NodeJS.ErrnoException).code === "ENOENT") {
      return NextResponse.json({ error: "File not found" }, { status: 404 });
    }
    return NextResponse.json(
      { error: error instanceof Error ? error.message : "Failed to read file" },
      { status: 500 },
    );
  }
}

export async function PUT(request: NextRequest) {
  try {
    const body = await request.json() as { path?: string; content?: string };
    if (!body.path || typeof body.content !== "string") {
      return NextResponse.json({ error: "Missing path or content" }, { status: 400 });
    }

    const resolved = safePath(body.path);
    if (!resolved) {
      return NextResponse.json({ error: "Invalid path" }, { status: 400 });
    }

    fs.mkdirSync(path.dirname(resolved), { recursive: true });
    fs.writeFileSync(resolved, body.content, "utf8");
    return NextResponse.json({ ok: true, path: body.path });
  } catch (error) {
    return NextResponse.json(
      { error: error instanceof Error ? error.message : "Failed to save file" },
      { status: 500 },
    );
  }
}
