#!/usr/bin/env bash
# Start transaction-report and free the port FIRST, so it never collides with a
# leftover/orphaned instance. Usage:  ./run.sh [port]     (default 8080)
set -uo pipefail
cd "$(dirname "$0")"

PORT="${1:-8080}"

# 1) Stop any previous instance still holding the port
if fuser -k "${PORT}/tcp" 2>/dev/null; then
  echo ">> freed port ${PORT} (stopped a previous instance)"
  for _ in 1 2 3 4 5; do
    ss -ltn 2>/dev/null | grep -q ":${PORT}\b" || break
    sleep 0.4
  done
fi

# 2) Environment (override by exporting before calling, or edit here)
export DB_USER="${DB_USER:-reporting_ro}"
export DB_PASSWORD="${DB_PASSWORD:-txreport_dev_pw}"
export FONT_NOTO="${FONT_NOTO:-$(pwd)/docker/fonts/NotoSansThai-Regular.ttf}"

# 3) Start — prebuilt jar if present (fast); otherwise Maven
JAR="target/txreport-0.0.1-SNAPSHOT.jar"
if [ -f "$JAR" ]; then
  echo ">> starting jar on :${PORT}"
  exec java -jar "$JAR" --server.port="${PORT}"
else
  echo ">> jar not found — starting via Maven on :${PORT}"
  exec mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=${PORT}"
fi
