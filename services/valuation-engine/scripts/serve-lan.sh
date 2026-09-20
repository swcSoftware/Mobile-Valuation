#!/usr/bin/env bash
# Serve the engine on all interfaces so a physical phone on the same Wi-Fi can use it.
# Prints the URL(s) to paste into the app's Settings → Valuation engine.
set -euo pipefail
cd "$(dirname "$0")/.."
[ -d .venv ] || { python3 -m venv .venv && .venv/bin/pip install -q -e ".[dev]"; }
PORT="${PORT:-8000}"
echo "ValueLens engine — LAN mode"
for ip in $(ifconfig 2>/dev/null | awk '/inet / && $2 != "127.0.0.1" {print $2}'); do
  echo "  http://$ip:$PORT"
done
echo "Ctrl-C to stop."
exec .venv/bin/uvicorn valuation_engine.main:app --host 0.0.0.0 --port "$PORT"
