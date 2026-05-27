#!/usr/bin/env bash
# Polls the IntelliJ plugin's VFS-refresh endpoint at a fixed interval so the
# editor stays in sync with files modified by an external agent (Claude Code,
# OpenCode, Cursor, etc.) outside the IDE. Run this in a separate terminal
# while recording an agent session.
#
# Usage:
#   ./poll-intellij-vfs-refresh.sh                    # default 0.3s, port 63342
#   INTERVAL=1.0 ./poll-intellij-vfs-refresh.sh       # custom interval
#   IDE_PORT=63343 ./poll-intellij-vfs-refresh.sh     # custom port (if IntelliJ binds a non-default port on a busy machine)
#   VERBOSE=1 ./poll-intellij-vfs-refresh.sh          # log every poll instead of every 10th
#
# Stop with Ctrl+C.

set -u

INTERVAL="${INTERVAL:-0.3}"
IDE_PORT="${IDE_PORT:-63342}"
VERBOSE="${VERBOSE:-0}"
URL="http://localhost:${IDE_PORT}/api/refactoring-tracer/refresh-vfs"

SUMMARY_EVERY=10

echo "[$(date +%H:%M:%S)] polling ${URL} every ${INTERVAL}s (Ctrl+C to stop)"

ok_total=0
ok_since_summary=0
fail_streak=0
elapsed_sum_ms=0
elapsed_max_ms=0
first_success_logged=0

while true; do
  resp=$(curl -sS -m 5 -w "\n%{http_code}" "${URL}" 2>&1) || true
  status="${resp##*$'\n'}"
  body="${resp%$'\n'*}"
  if [[ "${status}" == "200" ]]; then
    fail_streak=0
    ok_total=$((ok_total + 1))
    ok_since_summary=$((ok_since_summary + 1))
    elapsed_ms="$(printf '%s' "${body}" | sed -n 's/.*"elapsedMs":\([0-9]*\).*/\1/p')"
    elapsed_ms="${elapsed_ms:-0}"
    elapsed_sum_ms=$((elapsed_sum_ms + elapsed_ms))
    (( elapsed_ms > elapsed_max_ms )) && elapsed_max_ms=$elapsed_ms

    if [[ "${VERBOSE}" == "1" ]]; then
      echo "[$(date +%H:%M:%S)] poll #${ok_total} ok (refresh ${elapsed_ms}ms)"
    elif (( first_success_logged == 0 )); then
      echo "[$(date +%H:%M:%S)] first poll ok (refresh ${elapsed_ms}ms) — handler reachable"
      first_success_logged=1
    elif (( ok_since_summary >= SUMMARY_EVERY )); then
      avg=$(( elapsed_sum_ms / ok_since_summary ))
      echo "[$(date +%H:%M:%S)] +${ok_since_summary} polls ok (avg ${avg}ms, max ${elapsed_max_ms}ms) — total ${ok_total}"
      ok_since_summary=0
      elapsed_sum_ms=0
      elapsed_max_ms=0
    fi
  else
    fail_streak=$((fail_streak + 1))
    if (( fail_streak == 1 )) || (( fail_streak % 20 == 0 )); then
      echo "[$(date +%H:%M:%S)] refresh failed (status=${status:-N/A}, streak=${fail_streak}): ${body}" >&2
    fi
  fi
  sleep "${INTERVAL}"
done
