#!/usr/bin/env bash
# Finalise a recording session:
#   1. Move the single freshly-captured
#      library-fixture/.refactoring-traces/<uuid>/ dir to
#      fixtures/sessions/<id>/.
#   2. If the corresponding playbook file documents a manifest row,
#      append it to fixtures/sessions/manifest.csv.
#
# Run from anywhere; this script resolves paths relative to itself.
#
# Usage:
#   ./end-session.sh 001
#
# Skips the manifest append for Control sessions (042-045) — those
# don't appear in the manifest by design.
set -euo pipefail

if [ "$#" -ne 1 ]; then
  echo "Usage: $0 <session-id>   (e.g. 001)" >&2
  exit 1
fi

id="$1"

if ! [[ "$id" =~ ^[0-9]{3}$ ]]; then
  echo "Error: session id must be 3 digits, got '$id'." >&2
  exit 1
fi

# Paths anchored to this script's location (fixtures/).
fixtures_dir="$(cd "$(dirname "$0")" && pwd)"
fixture_root="$fixtures_dir/library-fixture"
sessions_dir="$fixtures_dir/sessions"
playbook_dir="$fixtures_dir/playbook"

if [ ! -d "$playbook_dir" ] || [ ! -d "$sessions_dir" ]; then
  echo "Error: expected playbook/ and sessions/ under $fixtures_dir." >&2
  exit 1
fi

# Locate the playbook file matching "<id>-*.md".
playbook_file="$(ls "$playbook_dir/${id}"-*.md 2>/dev/null | head -n1 || true)"
if [ -z "$playbook_file" ]; then
  echo "Error: no playbook file matching $playbook_dir/${id}-*.md" >&2
  exit 1
fi

# Locate the single trace dir inside the fixture root.
shopt -s nullglob
traces=("$fixture_root/.refactoring-traces"/*/)
shopt -u nullglob
if [ "${#traces[@]}" -eq 0 ]; then
  echo "Error: no trace dir under $fixture_root/.refactoring-traces/." >&2
  echo "       Did you forget to End the recording in the plugin?" >&2
  exit 1
elif [ "${#traces[@]}" -gt 1 ]; then
  echo "Error: ${#traces[@]} trace dirs under .refactoring-traces/ — expected exactly 1:" >&2
  for t in "${traces[@]}"; do echo "  $t" >&2; done
  echo "       Did you forget to run reset-for-session.sh between sessions?" >&2
  exit 1
fi
trace_src="${traces[0]%/}"

target="$sessions_dir/${id}"
if [ -e "$target" ]; then
  echo "Error: $target already exists — refusing to overwrite." >&2
  exit 1
fi

mv "$trace_src" "$target"
echo "Moved $trace_src -> $target"

events="$target/events.jsonl"
if [ ! -f "$events" ]; then
  echo "Warning: $events not found inside captured trace." >&2
else
  lines=$(wc -l < "$events" | tr -d ' ')
  echo "events.jsonl: $lines line(s)"
  if [ "$lines" -lt 20 ]; then
    echo "Warning: events.jsonl has fewer than 20 lines — trace may be incomplete." >&2
  fi
fi

# Extract the manifest row from the playbook (Control sessions don't have one).
manifest_row="$(awk '
  /^```/ {
    if (in_block) { in_block = 0; next }
    if (prev_line ~ /Manifest row/) { in_block = 1; next }
  }
  in_block && NF { print; exit }
  { prev_line = $0 }
' "$playbook_file")"

manifest="$sessions_dir/manifest.csv"
if [ -n "$manifest_row" ]; then
  if grep -q "^${id}," "$manifest" 2>/dev/null; then
    echo "Manifest already has a row for session $id — skipping append."
  else
    echo "$manifest_row" >> "$manifest"
    echo "Appended manifest row: $manifest_row"
  fi
else
  echo "Playbook has no manifest row (Control session) — skipping append."
fi

echo "Session $id complete."
