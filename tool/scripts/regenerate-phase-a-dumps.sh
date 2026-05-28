#!/usr/bin/env bash
# One-time pass: regenerate Phase A dumps for every recorded session under
# fixtures/user-sessions/ and fixtures/agent-sessions/ and write each dump
# back into the session's own dir as `phase-a.json`.
#
# To keep the real session dirs free of Phase A's transient artefacts
# (shadow-repo/, build/, IDE caches, etc.) the script copies each session
# to a tmp dir, runs :analysis:phaseA there, then moves *only* the
# resulting phase-a.json back into the original session dir.
#
# Idempotent: a session that already has phase-a.json is skipped. Failed
# sessions leave their tmp dir on disk for debugging; successful ones
# clean up after themselves.
#
# Usage:
#   ./scripts/regenerate-phase-a-dumps.sh                # process everything missing
#   FORCE=1 ./scripts/regenerate-phase-a-dumps.sh        # ignore existing phase-a.json
#
# Progress is logged to stdout after every session: index/total, ETA, and
# per-session wall time. Stop with Ctrl+C; rerun later to resume.

set -uo pipefail

TOOL_ROOT="/Users/ethanhosier/Desktop/random/fyp/tool"
GRADLEW="$TOOL_ROOT/gradlew"
FORCE="${FORCE:-0}"

# ---- discover sessions ----
sessions=()
# user-sessions: one level deep
while IFS= read -r -d '' d; do
    sessions+=("$d")
done < <(find "$TOOL_ROOT/fixtures/user-sessions" -mindepth 1 -maxdepth 1 -type d -print0 2>/dev/null | sort -z)
# agent-sessions: two levels deep (agent-name/NN)
while IFS= read -r -d '' d; do
    sessions+=("$d")
done < <(find "$TOOL_ROOT/fixtures/agent-sessions" -mindepth 2 -maxdepth 2 -type d -print0 2>/dev/null | sort -z)

total="${#sessions[@]}"
if [ "$total" -eq 0 ]; then
    echo "no sessions found under fixtures/{user,agent}-sessions/" >&2
    exit 1
fi

log_dir="$TOOL_ROOT/build/phase-a-regen-logs"
mkdir -p "$log_dir"

started=$(date +%s)
processed=0
skipped=0
ok=0
failed=0
nonskip_time=0   # cumulative seconds spent on actually-run sessions (for ETA)

printf '[%s] discovered %d sessions; logs at %s\n' "$(date +%H:%M:%S)" "$total" "$log_dir"

for session in "${sessions[@]}"; do
    processed=$((processed + 1))
    # `fixtures/agent-sessions/foo/01` or `fixtures/user-sessions/will-03`
    rel="${session#$TOOL_ROOT/fixtures/}"
    out_file="$session/phase-a.json"

    if [ "$FORCE" != "1" ] && [ -f "$out_file" ]; then
        skipped=$((skipped + 1))
        printf '[%s] (%d/%d) skip (phase-a.json exists): %s\n' \
            "$(date +%H:%M:%S)" "$processed" "$total" "$rel"
        continue
    fi

    # ETA only meaningful once we've actually run at least one session
    ran=$(( processed - skipped - 1 ))   # sessions already RUN (not skipped) before this one
    if [ "$ran" -gt 0 ]; then
        mean=$(( nonskip_time / ran ))
        remaining_to_run=$(( total - processed ))   # rough upper bound; some may also skip
        eta=$(( mean * remaining_to_run ))
        eta_str=$(printf '%dm%02ds' $((eta / 60)) $((eta % 60)))
    else
        eta_str='?'
    fi

    printf '[%s] (%d/%d, ETA %s) processing: %s\n' \
        "$(date +%H:%M:%S)" "$processed" "$total" "$eta_str" "$rel"

    sess_start=$(date +%s)
    tmp_dir=$(mktemp -d -t phase-a-regen-XXXXXX)
    tmp_session="$tmp_dir/session"
    sess_log="$log_dir/$(printf '%03d' "$processed")-${rel//\//_}.log"

    # 1. copy session to tmp (so all Phase A artefacts land in tmp_dir)
    if ! cp -R "$session" "$tmp_session" 2>"$sess_log"; then
        failed=$((failed + 1))
        printf '[%s] FAILED (copy): %s — see %s\n' "$(date +%H:%M:%S)" "$rel" "$sess_log" >&2
        continue
    fi

    # Some recorded sessions already contain a previous phase-a.json from a
    # prior run; ensure the copy in tmp doesn't accidentally short-circuit
    # downstream tooling.
    rm -f "$tmp_session/phase-a.json"

    # 2. run :analysis:phaseA in tmp; pipe output to per-session log
    if ( cd "$TOOL_ROOT" && "$GRADLEW" --console=plain -q :analysis:phaseA \
            --args="$tmp_session $tmp_session/phase-a.json" ) >>"$sess_log" 2>&1; then
        if [ -s "$tmp_session/phase-a.json" ]; then
            mv "$tmp_session/phase-a.json" "$out_file"
            rm -rf "$tmp_dir"
            sess_elapsed=$(( $(date +%s) - sess_start ))
            nonskip_time=$(( nonskip_time + sess_elapsed ))
            ok=$((ok + 1))
            printf '[%s] ok (%ds): %s\n' "$(date +%H:%M:%S)" "$sess_elapsed" "$rel"
        else
            failed=$((failed + 1))
            printf '[%s] FAILED (empty output): %s — see %s; tmp left at %s\n' \
                "$(date +%H:%M:%S)" "$rel" "$sess_log" "$tmp_dir" >&2
        fi
    else
        failed=$((failed + 1))
        printf '[%s] FAILED (gradle): %s — see %s; tmp left at %s\n' \
            "$(date +%H:%M:%S)" "$rel" "$sess_log" "$tmp_dir" >&2
    fi
done

total_elapsed=$(( $(date +%s) - started ))
printf '[%s] done: %d ok, %d skipped, %d failed in %dm%02ds\n' \
    "$(date +%H:%M:%S)" "$ok" "$skipped" "$failed" \
    $((total_elapsed / 60)) $((total_elapsed % 60))
