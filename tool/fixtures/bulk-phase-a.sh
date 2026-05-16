#!/usr/bin/env bash
# Run Phase A on every recorded session under fixtures/sessions/ and
# dump the PhaseAResult JSON into fixtures/corpus/<id>.json. Idempotent
# — skips sessions whose corpus file already exists.
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
  echo "[$id] phase A ($((done+1))/$total) ..."
  if (cd "$repo_root" && ./gradlew :analysis:phaseA --args="$s $out" -q); then
    done=$((done+1))
  else
    failed+=("$id")
  fi
done

echo ""
echo "Done. $done/$total succeeded."
if [ ${#failed[@]} -gt 0 ]; then
  echo "Failed sessions: ${failed[*]}" >&2
  exit 1
fi
