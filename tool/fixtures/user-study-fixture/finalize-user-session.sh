#!/usr/bin/env bash
# finalize-user-session.sh — end-of-session pipeline for a human user-study
# recording.
#
# Run this from the fixture root after the participant has stopped recording.
# The script:
#   1. runs Phase A (:analysis:run) on the captured trace
#   2. copies the trace + analysis output into
#      ../user-sessions/${USER_NAME}-${NN}/   (NN is the next free number
#                                              scoped to USER_NAME, so will-01,
#                                              will-02, … ignoring other names)
#   3. resets the fixture for the next session (reuses the same reset script
#      as finalize-agent-session.sh; there is no separate user-reset)
#   4. launches the dashboard with REFDASH_REPORT pointed at the copied
#      report — the script blocks here until the participant ends the
#      dashboard (Ctrl+C in this terminal stops `npm run dev`).
#
# Configure the participant identifier with USER_NAME below.

set -euo pipefail

# ---- config ----
USER_NAME="ethan"

# ---- paths ----
fixture_dir="$(cd "$(dirname "$0")" && pwd)"
tool_root="$(cd "$fixture_dir/../.." && pwd)"
gradlew="$tool_root/gradlew"
traces_dir="$fixture_dir/.refactoring-traces"
user_sessions_dir="$tool_root/fixtures/user-sessions"
reset_script="$fixture_dir/../reset-for-user-study-session.sh"
dashboard_dir="$tool_root/dashboard"

log() { echo "[finalize-user-session] $*"; }
die() { echo "[finalize-user-session] $*" >&2; exit 1; }

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
log "user: ${USER_NAME}"
log "found session: $session_id"

# ---- 1. Phase A analysis ----
log "step 1: running :analysis:run on $session_id"
( cd "$tool_root" && "$gradlew" --console=plain :analysis:run --args="$session_dir" )

# ---- 2. copy artefacts to user-sessions/${USER_NAME}-${NN} ----
mkdir -p "$user_sessions_dir"
# Next NN scoped to USER_NAME: scan entries matching `${USER_NAME}-<digits>`,
# pick the largest digit-suffix + 1, zero-pad to 2.
latest_n=0
while IFS= read -r entry; do
    if [[ "$entry" =~ ^${USER_NAME}-([0-9]+)$ ]]; then
        n=$((10#${BASH_REMATCH[1]}))
        (( n > latest_n )) && latest_n=$n
    fi
done < <(ls -1 "$user_sessions_dir" 2>/dev/null || true)
next_n=$((latest_n + 1))
dest_dir="$user_sessions_dir/${USER_NAME}-$(printf '%02d' "$next_n")"
mkdir -p "$dest_dir"
log "step 2: copying trace + report to $dest_dir"

for item in initial-src analysis-report.json events.jsonl session.json; do
    src="$session_dir/$item"
    if [ ! -e "$src" ]; then
        die "expected artefact missing: $src"
    fi
    cp -R "$src" "$dest_dir/"
done

# ---- 3. reset fixture (same script as finalize-agent-session uses) ----
log "step 3: resetting fixture for next session"
"$reset_script"

# ---- 4. launch dashboard pointed at the copied report; blocks until Ctrl+C ----
log "step 4: launching dashboard against $dest_dir/analysis-report.json"
log "Ctrl+C in this terminal to stop the dashboard"
cd "$dashboard_dir"
# Clear Vite's pre-bundled-dependency cache so the browser doesn't 504 on a
# stale optimized-dep hash from a previous run. Adds ~5s to dev startup but
# avoids the manual rm/refresh dance per session.
rm -rf node_modules/.vite node_modules/.vite-temp
exec env REFDASH_REPORT="$dest_dir/analysis-report.json" npm run dev
