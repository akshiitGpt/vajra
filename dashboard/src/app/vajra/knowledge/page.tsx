"use client";

/**
 * Knowledge Base — browse and edit the ruh-knowledge-base markdown files.
 *
 * Left: file tree grouped by directory.
 * Right: markdown viewer/editor with save.
 *
 * Reads files from the Vajra API at /knowledge/* endpoints.
 */

import { useCallback, useEffect, useMemo, useState } from "react";
import { Button } from "@/components/dashboard/button";
import { cn } from "@/lib/design";
import {
  DocumentIcon,
  ChevronRightIcon,
  ChevronDownIcon,
  SaveIcon,
  EditIcon,
  EyeIcon,
  RefreshIcon,
} from "@/components/ui/icons";
import { SimpleMarkdown } from "@/components/ui/simple-markdown";

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

interface KBFileEntry {
  path: string;
  name: string;
  dir: string;
}

interface KBTree {
  [dir: string]: KBFileEntry[];
}

// ---------------------------------------------------------------------------
// API helpers (fetch from local knowledge-base directory via API)
// ---------------------------------------------------------------------------

async function fetchFileList(): Promise<KBFileEntry[]> {
  const res = await fetch("/api/knowledge");
  if (!res.ok) throw new Error("Failed to load knowledge base index");
  const data = await res.json();
  return data.files as KBFileEntry[];
}

async function fetchFileContent(filePath: string): Promise<string> {
  const res = await fetch(`/api/knowledge/file?path=${encodeURIComponent(filePath)}`);
  if (!res.ok) throw new Error("Failed to load file");
  const data = await res.json();
  return data.content as string;
}

async function saveFileContent(filePath: string, content: string): Promise<void> {
  const res = await fetch("/api/knowledge/file", {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ path: filePath, content }),
  });
  if (!res.ok) {
    const data = await res.json().catch(() => ({}));
    throw new Error((data as { error?: string }).error || "Failed to save file");
  }
}

// ---------------------------------------------------------------------------
// File tree sidebar
// ---------------------------------------------------------------------------

function buildTree(files: KBFileEntry[]): KBTree {
  const tree: KBTree = {};
  for (const file of files) {
    const dir = file.dir || ".";
    if (!tree[dir]) tree[dir] = [];
    tree[dir].push(file);
  }
  // Sort dirs and files
  const sorted: KBTree = {};
  for (const dir of Object.keys(tree).sort()) {
    sorted[dir] = tree[dir].sort((a, b) => a.name.localeCompare(b.name));
  }
  return sorted;
}

function TreeDir({
  dir,
  files,
  selectedPath,
  onSelect,
}: {
  dir: string;
  files: KBFileEntry[];
  selectedPath: string | null;
  onSelect: (file: KBFileEntry) => void;
}) {
  const [open, setOpen] = useState(true);
  const label = dir === "." ? "root" : dir.replace("knowledge-base/", "");

  return (
    <div>
      <button
        onClick={() => setOpen(!open)}
        className="flex items-center gap-1.5 w-full px-3 py-1.5 text-left text-[12px] font-medium uppercase tracking-wide text-[var(--d-text-tertiary)] hover:text-[var(--d-text-secondary)] transition-colors"
      >
        {open ? (
          <ChevronDownIcon className="w-3 h-3" />
        ) : (
          <ChevronRightIcon className="w-3 h-3" />
        )}
        {label}
      </button>
      {open && (
        <div className="ml-2">
          {files.map((file) => (
            <button
              key={file.path}
              onClick={() => onSelect(file)}
              className={cn(
                "flex items-center gap-2 w-full px-3 py-1.5 text-left text-[13px] transition-colors",
                file.path === selectedPath
                  ? "bg-[var(--d-bg-selected-strong)] text-[var(--d-text-primary)] font-medium"
                  : "text-[var(--d-text-secondary)] hover:bg-[var(--d-bg-hover)]"
              )}
            >
              <DocumentIcon className="w-3.5 h-3.5 flex-shrink-0" />
              <span className="truncate">{file.name}</span>
            </button>
          ))}
        </div>
      )}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Main page
// ---------------------------------------------------------------------------

export default function KnowledgeBasePage() {
  const [files, setFiles] = useState<KBFileEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [selectedFile, setSelectedFile] = useState<KBFileEntry | null>(null);
  const [content, setContent] = useState("");
  const [originalContent, setOriginalContent] = useState("");
  const [fileLoading, setFileLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [mode, setMode] = useState<"view" | "edit">("view");

  const tree = useMemo(() => buildTree(files), [files]);
  const hasChanges = content !== originalContent;

  const loadFiles = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await fetchFileList();
      setFiles(data);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to load");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadFiles();
  }, [loadFiles]);

  const handleSelect = async (file: KBFileEntry) => {
    setSelectedFile(file);
    setFileLoading(true);
    setSaveError(null);
    setMode("view");
    try {
      const text = await fetchFileContent(file.path);
      setContent(text);
      setOriginalContent(text);
    } catch (e) {
      setSaveError(e instanceof Error ? e.message : "Failed to load file");
    } finally {
      setFileLoading(false);
    }
  };

  const handleSave = async () => {
    if (!selectedFile) return;
    setSaving(true);
    setSaveError(null);
    try {
      await saveFileContent(selectedFile.path, content);
      setOriginalContent(content);
    } catch (e) {
      setSaveError(e instanceof Error ? e.message : "Failed to save");
    } finally {
      setSaving(false);
    }
  };

  const handleDiscard = () => {
    setContent(originalContent);
  };

  return (
    <div className="h-screen overflow-hidden bg-[var(--d-bg-page)]">
      <div className="h-full flex">
        {/* Sidebar — file tree */}
        <div className="w-[280px] flex-shrink-0 border-r border-[var(--d-border)] bg-[var(--d-bg-surface)] flex flex-col">
          <div className="px-4 pt-5 pb-3 border-b border-[var(--d-border-subtle)]">
            <div className="flex items-center justify-between mb-1">
              <div>
                <p className="text-[11px] font-medium uppercase tracking-wide text-[var(--d-text-tertiary)]">
                  Vajra
                </p>
                <h1 className="text-[17px] font-semibold text-[var(--d-text-primary)] tracking-tight">
                  Knowledge Base
                </h1>
              </div>
              <Button
                variant="ghost"
                size="sm"
                icon={<RefreshIcon />}
                onClick={loadFiles}
                disabled={loading}
                aria-label="Refresh"
              />
            </div>
            <p className="text-[11px] text-[var(--d-text-tertiary)] mt-1">
              {files.length} files
            </p>
          </div>

          <div className="flex-1 overflow-y-auto py-2">
            {loading ? (
              <div className="px-4 py-6 space-y-3">
                {Array.from({ length: 6 }).map((_, i) => (
                  <div key={i} className="h-4 bg-[var(--d-bg-active)]" style={{ width: `${60 + Math.random() * 30}%` }} />
                ))}
              </div>
            ) : error ? (
              <div className="px-4 py-8 text-center">
                <p className="text-[13px] text-[var(--d-error-text)]">{error}</p>
                <Button variant="ghost" size="sm" onClick={loadFiles} className="mt-3">
                  Retry
                </Button>
              </div>
            ) : (
              Object.entries(tree).map(([dir, dirFiles]) => (
                <TreeDir
                  key={dir}
                  dir={dir}
                  files={dirFiles}
                  selectedPath={selectedFile?.path ?? null}
                  onSelect={handleSelect}
                />
              ))
            )}
          </div>
        </div>

        {/* Main content */}
        <div className="flex-1 flex flex-col overflow-hidden">
          {selectedFile ? (
            <>
              {/* Header bar */}
              <div className="px-6 py-4 border-b border-[var(--d-border-subtle)] bg-[var(--d-bg-surface)] flex items-center justify-between">
                <div>
                  <h2 className="text-[15px] font-medium text-[var(--d-text-primary)]">
                    {selectedFile.name}
                  </h2>
                  <p className="text-[12px] font-mono text-[var(--d-text-tertiary)] mt-0.5">
                    {selectedFile.path}
                  </p>
                </div>
                <div className="flex items-center gap-2">
                  {mode === "edit" && hasChanges && (
                    <Button variant="ghost" size="sm" onClick={handleDiscard}>
                      Discard
                    </Button>
                  )}
                  <Button
                    variant="ghost"
                    size="sm"
                    icon={mode === "view" ? <EditIcon /> : <EyeIcon />}
                    onClick={() => setMode(mode === "view" ? "edit" : "view")}
                  >
                    {mode === "view" ? "Edit" : "Preview"}
                  </Button>
                  {mode === "edit" && (
                    <Button
                      variant="primary"
                      size="sm"
                      icon={<SaveIcon />}
                      onClick={handleSave}
                      loading={saving}
                      disabled={!hasChanges}
                    >
                      Save
                    </Button>
                  )}
                </div>
              </div>

              {saveError && (
                <div className="px-6 py-2 bg-[var(--d-error-bg)] border-b border-[var(--d-error)]/20">
                  <p className="text-[13px] text-[var(--d-error-text)]">{saveError}</p>
                </div>
              )}

              {/* File content */}
              <div className="flex-1 overflow-y-auto">
                {fileLoading ? (
                  <div className="px-6 py-8 space-y-3">
                    {Array.from({ length: 8 }).map((_, i) => (
                      <div key={i} className="h-4 bg-[var(--d-bg-active)]" style={{ width: `${40 + Math.random() * 50}%` }} />
                    ))}
                  </div>
                ) : mode === "view" ? (
                  <div className="px-8 py-6 max-w-[800px]">
                    <SimpleMarkdown content={content} />
                  </div>
                ) : (
                  <textarea
                    value={content}
                    onChange={(e) => setContent(e.target.value)}
                    className={cn(
                      "w-full h-full px-6 py-6 resize-none",
                      "text-[14px] font-mono leading-relaxed",
                      "bg-[var(--d-bg-surface)] text-[var(--d-text-primary)]",
                      "focus:outline-none",
                    )}
                    spellCheck={false}
                  />
                )}
              </div>
            </>
          ) : (
            <div className="flex-1 flex items-center justify-center">
              <div className="text-center">
                <DocumentIcon className="w-12 h-12 mx-auto text-[var(--d-text-disabled)] mb-4" />
                <p className="text-[15px] font-medium text-[var(--d-text-secondary)]">
                  Select a file
                </p>
                <p className="text-[13px] text-[var(--d-text-tertiary)] mt-1">
                  Browse the knowledge base to view or edit documentation
                </p>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
