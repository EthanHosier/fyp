#!/usr/bin/env bash
# Re-run Phase A for one or more specific session ids with full
# gradle output (no -q) so you can see what actually failed.
#
# Mirrors bulk-phase-a.sh's tmp-staging behaviour so the original
# session dir stays untouched. Output JSON (if any) lands in tmp
# next to the staged copy — NOT copied into corpus/ — so a failing
# rerun doesn't overwrite the "good" corpus state.
#
# Usage:
#   ./fixtures/debug-phase-a.sh 022 023
#
# After it runs, the staged dirs survive under $work_root so you
# can poke around (look for half-written worktrees, gradle logs,
# etc.). The script prints the path at the end.
set -euo pipefail

if [ $# -eq 0 ]; then
  echo "usage: $0 <session-id> [<session-id> ...]" >&2
  exit 2
fi

fixtures_dir="$(cd "$(dirname "$0")" && pwd)"
repo_root="$(cd "$fixtures_dir/.." && pwd)"
sessions_dir="$fixtures_dir/sessions"

work_root="$(mktemp -d -t debug-phase-a-XXXXXX)"
echo "Staging under: $work_root"
echo "(not cleaned up on exit — inspect after run, then rm -rf manually)"
echo ""

for id in "$@"; do
  src="$sessions_dir/$id"
  if [ ! -d "$src" ]; then
    echo "[$id] ERROR: $src does not exist" >&2
    continue
  fi

  work_session="$work_root/$id"
  work_out="$work_root/$id.json"
  log="$work_root/$id.log"

  echo "============================================================"
  echo "[$id] staging copy of $src -> $work_session"
  cp -R "$src" "$work_session"

  echo "[$id] running :analysis:phaseA (full output, tee'd to $log)"
  echo ""
  set +e
  (cd "$repo_root" && ./gradlew :analysis:phaseA \
      --args="$work_session $work_out" \
      --console=plain --stacktrace) 2>&1 | tee "$log"
  rc=${PIPESTATUS[0]}
  set -e

  echo ""
  if [ $rc -eq 0 ] && [ -f "$work_out" ]; then
    echo "[$id] SUCCEEDED (rc=$rc, $(wc -c <"$work_out" | tr -d ' ') bytes)"
  else
    echo "[$id] FAILED (rc=$rc, output present: $([ -f "$work_out" ] && echo yes || echo no))"
    echo "[$id] full log: $log"
    echo "[$id] staged tree: $work_session"
  fi
  echo ""
done

echo "============================================================"
echo "Staged work root preserved at: $work_root"
echo "When done, clean up with:  rm -rf \"$work_root\""
