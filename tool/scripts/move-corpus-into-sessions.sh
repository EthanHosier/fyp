#!/usr/bin/env bash
# One-time pass: move each Phase A dump in fixtures/corpus/{NNN}.json into
# the corresponding session dir as fixtures/sessions/{NNN}/phase-a.json.
#
# Skips entries where the target session dir is missing (with a warning) or
# where phase-a.json already exists (idempotent — re-runnable). Uses `mv`
# so the corpus dir empties out; set COPY=1 to copy instead.
#
# Usage:
#   ./scripts/move-corpus-into-sessions.sh           # move (default)
#   COPY=1 ./scripts/move-corpus-into-sessions.sh    # copy instead of move

set -uo pipefail

TOOL_ROOT="/Users/ethanhosier/Desktop/random/fyp/tool"
CORPUS_DIR="$TOOL_ROOT/fixtures/corpus"
SESSIONS_DIR="$TOOL_ROOT/fixtures/sessions"
COPY="${COPY:-0}"

if [ ! -d "$CORPUS_DIR" ]; then
    echo "no corpus dir at $CORPUS_DIR" >&2
    exit 1
fi
if [ ! -d "$SESSIONS_DIR" ]; then
    echo "no sessions dir at $SESSIONS_DIR" >&2
    exit 1
fi

moved=0
skipped=0
missing_target=0
total=0

# Collect dumps in sorted order so the log reads in numerical order.
# (avoid `mapfile` — not present in macOS's bash 3.2)
dumps=()
while IFS= read -r d; do
    dumps+=("$d")
done < <(find "$CORPUS_DIR" -mindepth 1 -maxdepth 1 -name '*.json' -type f | sort)
total="${#dumps[@]}"

if [ "$total" -eq 0 ]; then
    echo "no *.json files under $CORPUS_DIR — nothing to do"
    exit 0
fi

echo "[$(date +%H:%M:%S)] found $total Phase A dumps in $(basename "$CORPUS_DIR")/"

idx=0
for src in "${dumps[@]}"; do
    idx=$((idx + 1))
    fname="$(basename "$src")"
    session_id="${fname%.json}"
    target_dir="$SESSIONS_DIR/$session_id"
    target_file="$target_dir/phase-a.json"

    if [ ! -d "$target_dir" ]; then
        missing_target=$((missing_target + 1))
        printf '[%s] (%d/%d) NO target session dir for %s — left in place\n' \
            "$(date +%H:%M:%S)" "$idx" "$total" "$fname" >&2
        continue
    fi

    if [ -f "$target_file" ]; then
        skipped=$((skipped + 1))
        printf '[%s] (%d/%d) skip (phase-a.json exists): sessions/%s/\n' \
            "$(date +%H:%M:%S)" "$idx" "$total" "$session_id"
        continue
    fi

    if [ "$COPY" = "1" ]; then
        cp -p "$src" "$target_file"
        printf '[%s] (%d/%d) copied: corpus/%s -> sessions/%s/phase-a.json\n' \
            "$(date +%H:%M:%S)" "$idx" "$total" "$fname" "$session_id"
    else
        mv "$src" "$target_file"
        printf '[%s] (%d/%d) moved: corpus/%s -> sessions/%s/phase-a.json\n' \
            "$(date +%H:%M:%S)" "$idx" "$total" "$fname" "$session_id"
    fi
    moved=$((moved + 1))
done

verb='moved'
[ "$COPY" = "1" ] && verb='copied'
printf '[%s] done: %d %s, %d skipped, %d missing target\n' \
    "$(date +%H:%M:%S)" "$moved" "$verb" "$skipped" "$missing_target"

# If we moved everything and the corpus dir is now empty, remind the user
# they can drop it.
if [ "$COPY" != "1" ] && [ "$moved" -gt 0 ] && [ -z "$(ls -A "$CORPUS_DIR" 2>/dev/null)" ]; then
    echo "[$(date +%H:%M:%S)] $CORPUS_DIR is now empty — safe to rmdir if you want"
fi
