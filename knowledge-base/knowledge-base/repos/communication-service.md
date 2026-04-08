---
title: "communication-service Repo"
category: repos
tags: [repo, communication-service, python, grpc, mongodb, beanie]
owner: "@backend"
last_updated: "2026-04-02"
source: repo
repo_url: "https://github.com/ruh-ai/communication-service"
---

# communication-service

GitHub: `github.com/ruh-ai/communication-service`

Python 3.11 gRPC microservice for conversation and message persistence. Uses grpcio 1.71.0 with an async server. Stores data in MongoDB via Motor + Beanie ODM. Instrumented with OpenTelemetry (SigNoz). Proto files are generated at build time from a separate proto repo.

## Directory Structure

```
communication-service/
├── app/
│   ├── main.py                          # Entry: async gRPC server
│   ├── core/
│   │   ├── config.py                    # Pydantic Settings
│   │   └── telemetry.py                # OpenTelemetry setup
│   ├── db/mongo.py                      # Motor/Beanie MongoDB init
│   ├── grpc_/                           # Generated pb2/pb2_grpc files
│   ├── models/
│   │   ├── conversation_model.py        # Conversation Beanie Document
│   │   └── message_model.py            # Message Beanie Document
│   ├── services/
│   │   ├── communication_service.py     # Main gRPC servicer
│   │   ├── conversation_service.py      # Conversation CRUD
│   │   └── message_service.py          # Message CRUD
│   ├── scripts/
│   │   ├── generate_grpc.py            # Proto repo clone + protoc
│   │   └── migrate_*.py               # 5 migration scripts
│   └── utils/                          # Created files utils, logger, metrics, tracing
├── tests/                               # Unit, integration, security, workflow tests
├── smoke/                               # Post-deploy smoke tests
├── helm/                                # Helm values (dev/qa/main)
├── Dockerfile
├── docker-compose.test.yml
├── pyproject.toml
├── Makefile
└── .github/workflows/                   # CI, build-deploy, post-deploy, regression
```

## Entry Point

`app/main.py` — starts an async gRPC server on port 50055. Initializes MongoDB (Beanie), registers the gRPC servicer, sets up OpenTelemetry instrumentation, and runs until terminated.

## Local Development

```bash
# Prerequisites: Python 3.11+, Poetry, Docker (for MongoDB)
git clone https://github.com/ruh-ai/communication-service
cd communication-service
poetry install --no-root
cp .env.example .env                   # Fill in MongoDB URI, telemetry config
poetry run python -m app.scripts.generate_grpc   # requires REPO_URL + GIT_TOKEN
poetry run python -m app.main
```

## Key Files to Read

| File | Purpose |
|------|---------|
| `app/main.py` | gRPC server bootstrap and lifecycle |
| `app/services/communication_service.py` | Main gRPC servicer (all RPC methods) |
| `app/services/conversation_service.py` | Conversation CRUD operations |
| `app/services/message_service.py` | Message CRUD operations |
| `app/models/conversation_model.py` | Conversation Beanie Document schema |
| `app/models/message_model.py` | Message Beanie Document schema |
| `app/db/mongo.py` | MongoDB connection and Beanie init |
| `app/core/config.py` | All configuration / env vars |

## Testing

```bash
make test-unit                         # Unit tests
make test-integration                  # Integration tests
make lint                              # Linting
make typecheck                         # Type checking
```

## See Also

- [services/communication-service/overview.md](../services/communication-service/overview.md) — Service-level docs
- [repos/agent-gateway.md](agent-gateway.md) — API gateway (primary gRPC client)
- [data/schemas/](../data/) — Data schemas and event definitions
