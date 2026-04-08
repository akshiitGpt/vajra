---
title: "File Conversion Service — Overview"
category: services
tags: [file-conversion, pdf, docx, markdown, gcs, fastapi, playwright]
owner: "@backend"
last_updated: "2026-04-02"
source: repo
---

# File Conversion Service

Type: Stateless conversion microservice
Repo: [repos/file-conversion.md](../../repos/file-conversion.md)
Language: Python 3.11
Framework: FastAPI + Uvicorn
Package Manager: uv (Hatchling build)
Port: 8080
Deployment: Google Cloud Run

## What It Does

A synchronous file conversion microservice that converts Markdown files to PDF or DOCX format:

1. Accepts a GCS (Google Cloud Storage) source URL pointing to a `.md` file
2. Converts to PDF (via Playwright/Chromium) or DOCX (via python-docx)
3. Uploads output back to the same GCS bucket, changing only the file extension

No queues, no polling, no job tracking — purely synchronous HTTP request/response.

## API Endpoints

### `GET /healthz`

Health check. Returns `{"status": "ok"}`. No authentication.

### `POST /convert`

Synchronous conversion. Requires `X-Server-Auth-Key` header matching `SERVER_AUTH_KEY` env var.

**Request:**

```json
{
  "source_url": "gs://bucket/path/file.md",
  "output_format": "pdf"
}
```

Supported `output_format`: `pdf`, `docx` (default: `pdf`)

Supported URL formats:
- `gs://bucket/path/file.md`
- `https://storage.googleapis.com/bucket/path/file.md`
- `https://bucket.storage.googleapis.com/path/file.md`

**Response (200):**

```json
{
  "bucket": "my-bucket",
  "source_object": "path/file.md",
  "object_name": "path/file.pdf",
  "source_gs_url": "gs://my-bucket/path/file.md",
  "gs_url": "gs://my-bucket/path/file.pdf",
  "https_url": "https://storage.googleapis.com/my-bucket/path/file.pdf",
  "size_bytes": 12345
}
```

**Error codes:** 400 (bad URL/format), 401 (unauthorized), 404 (source not found), 422 (validation), 500 (server error)

## Conversion Pipeline

1. **Markdown → HTML**: `markdown-it-py` with CommonMark spec, tables, task lists, footnotes. Wraps in styled HTML document (A4 page layout, clean typography)
2. **HTML → PDF**: Headless Chromium via Playwright, `wait_until="networkidle"` (for remote images), `page.pdf()` with A4 format
3. **HTML → DOCX**: BeautifulSoup parses HTML, `python-docx` builds Word document — handles headings (h1-h6), paragraphs with inline formatting, ordered/unordered lists, code blocks (Courier New), blockquotes, tables, horizontal rules

## Authentication

Simple shared-secret via `X-Server-Auth-Key` header, matched against `SERVER_AUTH_KEY` env var. Falls back to `.env` file. Returns HTTP 500 if completely unconfigured.

## CLI Mode

Also registered as `file-converter` CLI tool in pyproject.toml:

```bash
uv run file-converter --md-url "https://example.com/file.md" --format pdf
uv run file-converter --md-url "https://example.com/file.md" --format docx --out output.docx
```

Downloads from any public URL (not GCS-specific), converts, saves locally to `out/converted_<timestamp>.<ext>`.

## Database / Events

None. Stateless service — reads from GCS, converts in memory, writes to GCS. No message queues or event buses.

## Dependencies

| Package | Purpose |
|---------|---------|
| `fastapi` / `uvicorn` | Web framework / server |
| `google-cloud-storage` | GCS read/write |
| `markdown-it-py` / `mdit-py-plugins` | Markdown rendering |
| `playwright` | Headless Chromium for PDF |
| `python-docx` / `beautifulsoup4` | DOCX generation |
| `requests` | HTTP downloads (CLI mode) |

## Environment Variables

| Variable | Required | Description |
|----------|----------|-------------|
| `SERVER_AUTH_KEY` | Yes | Shared secret for `X-Server-Auth-Key` auth |
| `SERVICE_URL` | No | Used by `convert.sh` helper (default: `http://localhost:8080`) |

## Cloud Run Deployment

```bash
gcloud run deploy file-conversion \
  --region us-central1 \
  --no-allow-unauthenticated \
  --memory 2Gi \
  --timeout 300 \
  --concurrency 10 \
  --min-instances 0
```

Docker image: `python:3.11-slim` + Chromium system deps + Playwright browser install. Runs as non-root `appuser`.

**IAM requirements**: Service account needs `roles/storage.objectViewer` (read) and `roles/storage.objectCreator` (write).

## Testing

```bash
make test-unit          # Auth, converter, URL parsing, transformers
make test-integration   # Full /convert flow (PDF + DOCX)
make test-contract      # OpenAPI schema backward compatibility
make test-security      # Auth bypass, injection, path traversal
```

Coverage threshold: 80%.

## See Also

- [repos/file-conversion.md](../../repos/file-conversion.md) — Repo guide
- [architecture/service-map.md](../../architecture/service-map.md) — Where this fits
- [services/agent-gateway/api.md](../agent-gateway/api.md) — Gateway proxies to this service
