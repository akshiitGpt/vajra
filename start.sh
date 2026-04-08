#!/usr/bin/env bash
set -euo pipefail

# =============================================================================
# VAJRA — start everything
# =============================================================================
#
# Usage:
#   ./start.sh              Start orchestrator + dashboard
#   ./start.sh orchestrator Start orchestrator only
#   ./start.sh dashboard    Start dashboard only
#
# Prerequisites:
#   - Node.js 20+
#   - GitHub CLI (gh) authenticated
#   - LINEAR_API_KEY env var (or set below)
#
# =============================================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# --- Environment -----------------------------------------------------------

if [ -z "${LINEAR_API_KEY:-}" ]; then
  fail "LINEAR_API_KEY is not set. Export it first:\n  export LINEAR_API_KEY=\"lin_api_...\""
fi
export GITHUB_TOKEN="${GITHUB_TOKEN:-$(gh auth token 2>/dev/null || echo "")}"
export VAJRA_API_KEY="${VAJRA_API_KEY:-vajra-local-dev}"
export VAJRA_API_PORT="${VAJRA_API_PORT:-3847}"
export VAJRA_API_HOST="${VAJRA_API_HOST:-0.0.0.0}"

# Dashboard env
DASHBOARD_PORT="${DASHBOARD_PORT:-3000}"

# --- Preflight checks -------------------------------------------------------

RED='\033[0;31m'
GREEN='\033[0;32m'
DIM='\033[2m'
RESET='\033[0m'

log()  { echo -e "${DIM}[vajra]${RESET} $*"; }
ok()   { echo -e "${GREEN}  ✓${RESET} $*"; }
fail() { echo -e "${RED}  ✗${RESET} $*"; exit 1; }

log "Preflight checks"

command -v node >/dev/null 2>&1 || fail "node not found — install Node.js 20+"
NODE_VERSION=$(node -v | sed 's/v//' | cut -d. -f1)
[ "$NODE_VERSION" -ge 20 ] || fail "Node.js 20+ required (found v$NODE_VERSION)"
ok "Node.js $(node -v)"

ok "LINEAR_API_KEY set"

[ -n "$GITHUB_TOKEN" ] || fail "GITHUB_TOKEN is not set — run 'gh auth login' first"
ok "GITHUB_TOKEN set (via gh cli)"

# --- Build if needed --------------------------------------------------------

if [ ! -d "orchestrator/dist" ]; then
  log "Building orchestrator..."
  npm run build:orchestrator
  ok "Orchestrator built"
else
  ok "Orchestrator already built"
fi

if [ ! -d "node_modules" ]; then
  log "Installing dependencies..."
  npm install
  ok "Dependencies installed"
fi

# --- Dashboard .env.local ---------------------------------------------------

DASHBOARD_ENV="$SCRIPT_DIR/dashboard/.env.local"
if [ ! -f "$DASHBOARD_ENV" ]; then
  log "Creating dashboard/.env.local"
  cat > "$DASHBOARD_ENV" <<EOF
VAJRA_API_URL=http://localhost:${VAJRA_API_PORT}
VAJRA_API_KEY=${VAJRA_API_KEY}
DASHBOARD_PASSWORD=vajra
EOF
  ok "dashboard/.env.local created (password: vajra)"
else
  ok "dashboard/.env.local exists"
fi

# --- Cleanup on exit --------------------------------------------------------

PIDS=()

cleanup() {
  log "Shutting down..."
  for pid in "${PIDS[@]}"; do
    kill "$pid" 2>/dev/null || true
  done
  wait 2>/dev/null
  log "Done"
}

trap cleanup EXIT INT TERM

# --- Start services ---------------------------------------------------------

MODE="${1:-all}"

start_orchestrator() {
  log "Starting orchestrator on port $VAJRA_API_PORT"
  node orchestrator/dist/index.js &
  PIDS+=($!)
  ok "Orchestrator PID $!"
}

start_dashboard() {
  log "Starting dashboard on port $DASHBOARD_PORT"
  cd "$SCRIPT_DIR/dashboard"
  npx next dev -p "$DASHBOARD_PORT" &
  PIDS+=($!)
  cd "$SCRIPT_DIR"
  ok "Dashboard PID $!"
}

echo ""
echo -e "${DIM}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${RESET}"
echo -e "  vajra"
echo -e "${DIM}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${RESET}"
echo ""

case "$MODE" in
  orchestrator)
    start_orchestrator
    echo ""
    log "Orchestrator API: http://localhost:$VAJRA_API_PORT"
    ;;
  dashboard)
    start_dashboard
    echo ""
    log "Dashboard: http://localhost:$DASHBOARD_PORT"
    ;;
  all|*)
    start_orchestrator
    sleep 1
    start_dashboard
    echo ""
    log "Orchestrator API: http://localhost:$VAJRA_API_PORT"
    log "Dashboard:        http://localhost:$DASHBOARD_PORT"
    log "Dashboard login:  vajra"
    ;;
esac

echo ""
log "Press Ctrl+C to stop"
echo ""

# Wait for all background processes
wait
