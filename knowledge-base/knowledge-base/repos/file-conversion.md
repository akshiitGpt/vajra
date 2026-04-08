---
title: "file-conversion Repo"
category: repos
tags: [repo, file-conversion, python, fastapi, playwright, gcs, cloud-run]
owner: "@backend"
last_updated: "2026-04-02"
source: repo
repo_url: "https://github.com/ruh-ai/file-conversion"
---

# file-conversion

GitHub: `github.com/ruh-ai/file-conversion` (private)

Python 3.11 file conversion service built with FastAPI + Uvicorn. Managed with uv (Hatchling build). Converts Markdown to PDF (via Playwright/Chromium) or DOCX (via python-docx). Reads source files from and writes output to Google Cloud Storage. Deployed on Google Cloud Run. Also supports a CLI mode for local conversion.

## Directory Structure

```
file-conversion/
├── app/
│   ├── __init__.py
│   ├── server.py               # FastAPI: /healthz, /convert
│   └── file_converter.py       # Core conversion logic + CLI
├── tests/
│   ├── unit/                   # Auth, converter, URL parsing tests
│   ├── integration/            # Full convert flow tests
│   ├── contract/               # OpenAPI contract tests
│   └── security/               # Auth bypass, injection tests
├── smoke/                      # Post-deploy smoke tests
├── scripts/generate_openapi_baseline.py
├── convert.sh                  # Helper curl script
├── Dockerfile                  # python:3.11-slim + Playwright Chromium
├── Makefile
├── pyproject.toml
└── .github/workflows/          # CI, build-deploy, post-deploy, regression
```

## Entry Point

`app/server.py` — FastAPI application on port 8080. Exposes two endpoints:
- `GET /healthz` — health check
- `POST /convert` — accepts a Markdown URL and target format (pdf or docx), returns the converted file URL on GCS

Authentication is via `SERVER_AUTH_KEY` header validation.

## Local Development

```bash
# Prerequisites: Python 3.11+, uv, Playwright (for PDF conversion)
git clone https://github.com/ruh-ai/file-conversion
cd file-conversion
uv sync
playwright install chromium            # Required for PDF conversion
export SERVER_AUTH_KEY="your-key"
uv run uvicorn app.server:app --host 0.0.0.0 --port 8080
```

## CLI Mode

The service can also be used as a standalone CLI tool for local file conversion:

```bash
uv run file-converter --md-url "https://example.com/file.md" --format pdf
uv run file-converter --md-url "https://example.com/file.md" --format docx
```

## Key Files to Read

| File | Purpose |
|------|---------|
| `app/server.py` | FastAPI app with /healthz and /convert endpoints |
| `app/file_converter.py` | Core conversion logic (MD to PDF/DOCX) + CLI entry |
| `Dockerfile` | Build with Playwright Chromium for headless PDF |
| `convert.sh` | Example curl script for testing |
| `scripts/generate_openapi_baseline.py` | Generate OpenAPI spec for contract tests |

## Testing

```bash
make test-unit                         # Unit tests
make test-integration                  # Integration tests
make lint                              # Linting
make typecheck                         # Type checking
```

## See Also

- [repos/agent-gateway.md](agent-gateway.md) — API gateway (primary HTTP client)
