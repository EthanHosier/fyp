#!/usr/bin/env bash
# Run Phase A on every recorded session under fixtures/sessions/ and
# dump the PhaseAResult JSON into fixtures/corpus/<id>.json. Idempotent
# — skips sessions whose corpus file already exists.
#
# Each session is first copied into a fresh tmp directory and Phase A
# runs against that copy. Any reconstructed worktrees, gradle caches,
# JDT projects, etc. land in tmp and are deleted on exit; the original
# fixtures/sessions/<id>/ directory stays pristine. Only the
# PhaseAResult JSON is written back, into fixtures/corpus/<id>.json.
#
# Run from anywhere (script resolves paths relative to itself):
#   ./fixtures/bulk-phase-a.sh
#
# Roughly 30-45 min wall-clock for the full 45-session corpus.
set -euo pipefail

fixtures_dir="$(cd "$(dirname "$0")" && pwd)"
repo_root="$(cd "$fixtures_dir/.." && pwd)"
sessions_dir="$fixtures_dir/sessions"
corpus_dir="$fixtures_dir/corpus"
mkdir -p "$corpus_dir"

work_root="$(mktemp -d -t bulk-phase-a-XXXXXX)"
trap 'rm -rf "$work_root"' EXIT

failed=()
done=0
total=$(find "$sessions_dir" -mindepth 1 -maxdepth 1 -type d | wc -l | tr -d ' ')

for s in "$sessions_dir"/*/; do
  id="$(basename "$s")"
  out="$corpus_dir/$id.json"
  if [ -f "$out" ]; then
    echo "[$id] skip — $out already exists"
    continue
  fi

  work_session="$work_root/$id"
  work_out="$work_root/$id.json"

  echo "[$id] phase A ($((done+1))/$total) — staging to $work_session ..."
  rm -rf "$work_session"
  cp -R "$s" "$work_session"

  if (cd "$repo_root" && ./gradlew :analysis:phaseA --args="$work_session $work_out" -q); then
    if [ -f "$work_out" ]; then
      mv "$work_out" "$out"
      done=$((done+1))
    else
      echo "[$id] gradle succeeded but no output at $work_out" >&2
      failed+=("$id")
    fi
  else
    failed+=("$id")
  fi

  # Drop the staged copy immediately so the tmp tree doesn't grow.
  rm -rf "$work_session"
done

echo ""
echo "Done. $done/$total succeeded."
if [ ${#failed[@]} -gt 0 ]; then
  echo "Failed sessions: ${failed[*]}" >&2
  exit 1
fi
