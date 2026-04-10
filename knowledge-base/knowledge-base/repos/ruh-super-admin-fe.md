---
title: "ruh-super-admin-fe — Code-Level Repo Guide"
category: repos
tags: [super-admin, frontend, nextjs, react, typescript, admin-dashboard]
owner: "@dev-team"
last_updated: "2026-04-10"
source: manual
repo_url: "https://github.com/ruh-ai/ruh-super-admin-fe"
---

# ruh-super-admin-fe — Code-Level Repo Guide

## Overview

The **Ruh Super Admin Dashboard** is a Next.js 15 frontend application that provides Ruh AI platform administrators with centralized visibility and control over the entire platform — organizations, users, AI agents, credit monitoring, model configuration, inbox rotation, and RBAC.

**Repo:** `https://github.com/ruh-ai/ruh-super-admin-fe`
**Environments:**
- Local: `http://localhost:3000`
- QA: `ruh-superadmin-qa.ruh.ai`
- Production: `admin.ruh.ai`

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Framework | Next.js 15.3.3 (App Router) |
| Language | TypeScript 5 (strict mode) |
| UI Components | Radix UI (headless) + shadcn/ui pattern |
| Styling | Tailwind CSS 4 |
| State Management | Zustand 5 |
| HTTP Client | Axios (with interceptors) |
| Charts | Recharts 2 |
| Icons | Lucide React |
| Notifications | Sonner (toast) |
| Date Utils | date-fns 4 |
| Build | Turbopack (dev), Next.js build (prod) |
| Containerization | Docker (Node 23 Alpine, multi-stage) |

---

## Directory Structure

```
src/
├── app/                        # Next.js App Router pages
│   ├── layout.tsx              # Root layout → wraps DashboardLayout
│   ├── page.tsx                # Dashboard overview (/)
│   ├── login/page.tsx          # Login page (/login)
│   ├── agents/                 # /agents, /agents/[id]
│   ├── organisations/          # /organisations, /organisations/[id]
│   ├── users/                  # /users, /users/[id], /users/[id]/agents/[agentId]
│   │                           # /users/[id]/campaigns/[campaignId]
│   ├── activities/             # /activities, /activities/[id]
│   ├── credit-monitoring/      # /credit-monitoring
│   ├── mcps/[id]/              # /mcps/[id]
│   ├── platform-utility/
│   │   └── inbox-rotation/     # /platform-utility/inbox-rotation
│   └── settings/               # /settings, /settings/admins,
│                               # /settings/credit-monitoring,
│                               # /settings/model-selection,
│                               # /settings/waitlist,
│                               # /settings/change-password
│
├── components/                 # Feature-domain components
│   ├── agents/                 # AgentCard, AgentsList, AgentDetailContent,
│   │                           # AgentActivitiesTab, AgentMCPsTab,
│   │                           # AgentIntegrationsTab, AgentPaymentsTab,
│   │                           # AddAgentToUserModal
│   ├── auth/                   # AuthProvider, LoginForm, ProtectedRoute,
│   │                           # PermissionGate, NoPermission
│   ├── activities/             # ActivityColumns, ActivityDetailContent,
│   │                           # ActivityFilters, ActivityLogsDialog
│   ├── campaigns/              # CampaignDetailContent, metrics/overview/
│   │                           # prospect/response tabs
│   ├── users/                  # UserTable, UserCards, UserDetailContent,
│   │                           # UserCampaignsTab, UserIntegrationsTab,
│   │                           # UserMCPsTab, UserWorkflowsTab
│   ├── workflows/              # WorkflowCard
│   └── ui/                     # Radix-based primitives (button, dialog,
│                               # table, badge, input, select, tabs…)
│
├── shared/                     # Cross-cutting layout & utilities
│   ├── DashboardLayout.tsx     # Root layout shell (sidebar + header + content)
│   ├── Sidebar.tsx             # Collapsible nav with permission-gated items
│   ├── Header.tsx              # Top bar
│   ├── DataTable.tsx           # Generic paginated table
│   ├── StatCard.tsx            # KPI card widget
│   ├── ChartContainer.tsx      # Recharts wrapper
│   ├── PageContainer.tsx       # Standard page wrapper
│   ├── FormDialog.tsx          # Reusable modal form
│   ├── DeleteConfirmationDialog.tsx
│   ├── Pagination.tsx
│   ├── EmptyState.tsx
│   ├── ErrorBoundary.tsx
│   └── types.ts                # Shared TypeScript types
│
├── services/                   # All API calls (one file per domain)
│   ├── api.ts                  # Axios instance factory + interceptors
│   ├── userService.ts          # /admin/users/*
│   ├── agentService.ts         # /admin/agents/*
│   ├── organisationService.ts  # /admin/organisations/*
│   ├── activityService.ts
│   ├── adminService.ts         # /admin/me, admin CRUD
│   ├── auditLogService.ts
│   ├── campaignService.ts
│   ├── creditMonitoringService.ts  # /credit/*
│   ├── creditService.ts
│   ├── csvService.ts
│   ├── inboxRotationService.ts # Separate axios instance → NEXT_PUBLIC_INBOX_ROTATION_URL
│   ├── integrationService.ts
│   ├── mcpService.ts
│   ├── modelService.ts         # /admin/models
│   ├── paymentService.ts
│   ├── roleService.ts
│   ├── subscriptionService.ts
│   ├── waitlistService.ts
│   └── workflowService.ts
│
├── types/                      # Per-domain TypeScript interfaces
│   ├── authType.ts
│   ├── userType.ts
│   ├── agentsTypes.ts
│   ├── organisationsType.ts
│   ├── campaignTypes.ts
│   ├── inboxRotationType.ts    # ~50 types for inbox rotation
│   ├── creditMonitoringTypes.ts
│   ├── modelTypes.ts
│   ├── mcpTypes.ts
│   └── …
│
├── lib/
│   ├── store/
│   │   ├── authStore.ts        # Zustand auth store (JWT tokens, roles, login/logout)
│   │   └── appStore.ts         # Zustand app store (sidebar state, current user)
│   ├── permissions/
│   │   ├── constants.ts        # PERMISSIONS catalog, ROUTE_PERMISSIONS, ROLE_NAMES
│   │   └── index.ts
│   └── providers/
│       └── ThemeProvider.tsx
│
├── hooks/
│   ├── usePermissions.ts       # React hook wrapping authStore permission checks
│   ├── useCreditMonitoring.ts
│   └── useWaitlist.ts
│
├── api/
│   ├── auth.ts                 # loginUser(), logoutUser() calls
│   └── index.ts
│
├── utils/
│   ├── agentHelpers.tsx
│   └── commonMethods.ts
│
├── config/
│   └── constants.ts            # App-level constants
│
└── middleware.ts               # Next.js middleware — JWT cookie check, redirects to /login
```

---

## Authentication & Authorization

### Authentication Flow
1. User submits credentials on `/login`
2. `authStore.login()` calls `POST /api/v1/auth/login`
3. JWT tokens stored in cookies (`access_token`, `refresh_token`)
4. Cookie domain: `NEXT_PUBLIC_COOKIES_DOMAIN` (e.g. `.ruh.ai`)
5. Next.js **middleware** (`src/middleware.ts`) checks `access_token` cookie on every request — redirects unauthenticated users to `/login?redirect=<path>`
6. Axios interceptors auto-refresh expired tokens using `refresh_token`

### RBAC / Permissions

Permissions follow `domain:action` naming:

| Permission | Description |
|-----------|-------------|
| `organisations:read` | View organizations |
| `users:read` | View users |
| `agents:read_admin` | View agents (admin view) |
| `agents:admin_use` | Assign agents to users |
| `credits:read_monitoring` | View credit monitoring |
| `credits:update_threshold` | Update alert thresholds |
| `credits:grant_org` | Grant credits to org |
| `credits:grant_apollo` | Grant Apollo credits |
| `models:update_config` | Configure LLM models |
| `mcps:integration_keys:update` | Update MCP integration keys |
| `inbox_rotation:manage` | Manage inbox rotation |
| `campaigns:manage` | Manage campaigns |
| `roles:manage` | RBAC role management |
| `admins:manage` | Admin user management |
| `audit_logs:read` | View audit logs |
| `all` | Full access (Super Admin) |

**Predefined Roles:** `Super Admin`, `Developer Team`, `Marketing Team`

Sidebar nav items and routes are gated via `PermissionGate` component and `usePermissions()` hook.

---

## API Integration

### Main API (Platform Backend)
- **Base URL:** `NEXT_PUBLIC_BASE_URL` (default: `http://localhost:8000/api/v1`)
- **Auth:** `Authorization: Bearer <access_token>` (injected by Axios interceptor)
- **Token refresh:** Automatic via response interceptor on 401
- **Timeout:** 30 seconds

### Inbox Rotation API (Separate Service)
- **Base URL:** `NEXT_PUBLIC_INBOX_ROTATION_URL`
- **Auth:** `X-Auth-Key: <NEXT_PUBLIC_INBOX_ROTATION_AUTH_KEY>` header
- **Separate Axios instance** in `inboxRotationService.ts`
- Does NOT use JWT bearer tokens

### SDR API
- **Base URL:** `NEXT_PUBLIC_SDR_URL`
- **Auth:** `NEXT_PUBLIC_SDR_AUTH_KEY`

---

## Key Pages & Features

### Dashboard (`/`)
Accessible to all authenticated admins. KPI overview cards and activity feed.

### Organizations (`/organisations`, `/organisations/[id]`)
- Requires `organisations:read`
- Lists all orgs with department/member counts
- Detail page: org info, members, agents, knowledge base

### Users (`/users`, `/users/[id]`)
- Requires `users:read`
- Directory with search, sort, active/verified filters
- Detail tabs: Agents, Campaigns, Integrations, MCPs, Workflows

### Agents (`/agents`, `/agents/[id]`)
- Requires `agents:read_admin`
- Full platform agent catalog with performance metrics
- Detail tabs: Activities, Integrations, MCPs, Payments
- Admin can assign agents to users (`agents:admin_use`)

### Credit Monitoring (`/credit-monitoring`)
- Requires `credits:read_monitoring`
- OpenRouter + Apollo credit balances
- Alert thresholds management

### Inbox Rotation (`/platform-utility/inbox-rotation`)
- Requires `inbox_rotation:manage`
- Manages email inboxes, domains, API keys for the inbox rotation service
- KPI dashboard (active inboxes, domains, API keys)
- Domain management: block/unblock, warmup status
- API key management: create, rotate, revoke
- Bulk inbox assignment to users
- Approve domain purchase requests

### Settings
| Route | Permission | Description |
|-------|-----------|-------------|
| `/settings/admins` | `admins:manage` or `roles:manage` or `audit_logs:read` | Manage admin users, roles, view audit logs |
| `/settings/model-selection` | `models:update_config` | Configure LLM model availability |
| `/settings/credit-monitoring` | `credits:read_monitoring` | Credit threshold settings |
| `/settings/waitlist` | — | Waitlist management |
| `/settings/change-password` | — | Password change |

---

## State Management

Two Zustand stores persisted to `localStorage`:

**`authStore` (`super-auth-state`)**
- `isAuthenticated`, `roles`, `mustChangePassword`
- `login()`, `logout()`, `setAuth()`, `updateTokens()`
- Helper functions: `getEffectivePermissions()`, `hasPermission()`, `isSuperAdmin()`

**`appStore` (`super-app-state`)**
- `sidebarCollapsed`, `currentUser` (name, email, role)
- `toggleSidebar()`, `setCurrentUser()`

---

## Environment Variables

| Variable | Required | Description |
|----------|----------|-------------|
| `NEXT_PUBLIC_BASE_URL` | Yes | Platform backend API base URL |
| `NEXT_PUBLIC_APP_ENV` | No | `dev` \| `qa` \| `production` — feature flags |
| `NEXT_PUBLIC_COOKIES_DOMAIN` | Yes | Cookie domain (`.ruh.ai` for QA/Prod, `localhost` for local) |
| `NEXT_PUBLIC_INBOX_ROTATION_URL` | Yes | Inbox rotation service base URL |
| `NEXT_PUBLIC_INBOX_ROTATION_AUTH_KEY` | Yes | X-Auth-Key for inbox rotation API |
| `NEXT_PUBLIC_SDR_URL` | No | SDR service base URL |
| `NEXT_PUBLIC_SDR_AUTH_KEY` | No | SDR service auth key |

---

## Local Development

```bash
git clone https://github.com/ruh-ai/ruh-super-admin-fe.git
cd ruh-super-admin-fe
npm install          # or yarn / pnpm / bun

cp .env.example .env
# Edit .env with your local values

npm run dev          # Starts on http://localhost:3000 with Turbopack
```

**Scripts:**
| Script | Description |
|--------|-------------|
| `npm run dev` | Dev server with Turbopack HMR |
| `npm run build` | Production build |
| `npm run start` | Serve production build |
| `npm run lint` | ESLint check |
| `npm run format` | Prettier format all files |
| `npm run save-all` | Prettier + ESLint fix (pre-commit) |

---

## Docker

Multi-stage build using Node 23 Alpine:
```bash
docker build -t ruh-super-admin-fe .
docker run -p 3000:3000 ruh-super-admin-fe
```

Note: `.env` is copied into the builder stage — ensure env vars are baked in at build time (Next.js `NEXT_PUBLIC_*` vars are bundled at build).

---

## Component Conventions

- **Functional components** with hooks only (no class components)
- **PascalCase** for component files; **camelCase** for utilities
- **Named exports** for components; **default exports** for pages
- **Props interfaces** defined inline above each component
- All components in TypeScript strict mode

---

## See Also

- [repos/agent-platform-v2.md](agent-platform-v2.md) — Backend the admin dashboard calls
- [repos/communication-service.md](communication-service.md) — Messaging service
- [services/agent-platform/api.md](../services/agent-platform/api.md) — Backend API endpoints
- [references/api-endpoints.md](../references/api-endpoints.md) — All platform API endpoints
- [references/environment-variables.md](../references/environment-variables.md) — All env vars
- [infra/environments.md](../infra/environments.md) — QA/Prod environment details
