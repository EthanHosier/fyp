#!/usr/bin/env bash
# finalize-agent-session.sh — end-of-session pipeline for an agent recording.
#
# Run this from the fixture root after the agent has finished editing and
# the plugin has stopped recording. The script:
#   0. reconciles events.jsonl into session.json (agent-finalize)
#   1. runs Phase A (:analysis:run) on the captured trace
#   2. copies the trace + analysis output into
#      ../agent-sessions/${AGENT_NAME}/${NN}/
#   3. runs :analysis:feedbackForAgent on the copied report and prints its
#      output (this is what gets fed back to the agent for the next session)
#   4. resets the fixture for the next session
#
# Configure the destination agent name with AGENT_NAME below.
#
# Stops at the first failing step so a half-completed run never overwrites
# the fixture state.

set -euo pipefail

# ---- config ----
AGENT_NAME="claude-opencode"

# ---- paths ----
fixture_dir="$(cd "$(dirname "$0")" && pwd)"
tool_root="$(cd "$fixture_dir/../.." && pwd)"
gradlew="$tool_root/gradlew"
traces_dir="$fixture_dir/.refactoring-traces"
agent_sessions_dir="$tool_root/fixtures/agent-sessions/$AGENT_NAME"
reset_script="$fixture_dir/../reset-for-user-study-session.sh"

log() { echo "[finalize-agent-session] $*"; }
die() { echo "[finalize-agent-session] $*" >&2; exit 1; }

# ---- 0. agent-finalize: reconcile events.jsonl into session.json ----
log "step 0: reconciling events.jsonl into session.json"
"$fixture_dir/agent-finalize"

# ---- discover the single session id under .refactoring-traces/ ----
[ -d "$traces_dir" ] || die "no .refactoring-traces/ at $traces_dir"
session_dirs=()
while IFS= read -r -d '' d; do
    session_dirs+=("$d")
done < <(find "$traces_dir" -mindepth 1 -maxdepth 1 -type d -print0)
if [ "${#session_dirs[@]}" -eq 0 ]; then
    die "no session folder under $traces_dir — was the plugin recording?"
fi
if [ "${#session_dirs[@]}" -gt 1 ]; then
    die "expected exactly 1 session folder under $traces_dir, found ${#session_dirs[@]}:"$'\n'"$(printf '  %s\n' "${session_dirs[@]}")"
fi
session_dir="${session_dirs[0]}"
session_id="$(basename "$session_dir")"
log "found session: $session_id"

# ---- 1. Phase A analysis run ----
log "step 1: running :analysis:run on $session_id"
( cd "$tool_root" && "$gradlew" --console=plain :analysis:run --args="$session_dir" )

# ---- 2. copy artefacts to agent-sessions/${AGENT_NAME}/${NN} ----
mkdir -p "$agent_sessions_dir"
# Next number = highest existing NN (matched by ls) + 1, zero-padded to 2.
latest_n=0
while IFS= read -r entry; do
    [[ "$entry" =~ ^[0-9]+$ ]] || continue
    n=$((10#$entry))
    (( n > latest_n )) && latest_n=$n
done < <(ls -1 "$agent_sessions_dir" 2>/dev/null || true)
next_n=$((latest_n + 1))
dest_dir="$agent_sessions_dir/$(printf '%02d' "$next_n")"
mkdir -p "$dest_dir"
log "step 2: copying trace + report to $dest_dir"

for item in initial-src analysis-report.json events.jsonl session.json; do
    src="$session_dir/$item"
    if [ ! -e "$src" ]; then
        die "expected artefact missing: $src"
    fi
    cp -R "$src" "$dest_dir/"
done

# ---- 3. feedbackForAgent on the copied report ----
log "step 3: running :analysis:feedbackForAgent on copied report"
log "----- agent feedback start -----"
( cd "$tool_root" && "$gradlew" --console=plain -q :analysis:feedbackForAgent \
    --args="--report $dest_dir/analysis-report.json" )
log "----- agent feedback end -------"

# ---- 4. reset fixture for next session ----
log "step 4: resetting fixture for next session"
"$reset_script"

log "done — next session number will be $(printf '%02d' $((next_n + 1)))"
