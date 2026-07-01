#!/usr/bin/env bash
# Stop the transaction-report instance on a port. Usage:  ./stop.sh [port]  (default 8080)
PORT="${1:-8080}"
if fuser -k "${PORT}/tcp" 2>/dev/null; then
  echo ">> stopped instance on port ${PORT}"
else
  echo ">> nothing was running on port ${PORT}"
fi
