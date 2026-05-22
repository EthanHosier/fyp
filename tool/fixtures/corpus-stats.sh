#!/usr/bin/env bash
# Print per-corpus session count, max divergence points per fixture, and
# total divergence points. Reads each session's analysis-report.json and
# counts the divergencePoints array.
#
# Numbers reported in the results chapter (\S\ref{sec:results-sensitivity}
# corpus description) are sourced from this script.
#
# Usage:
#   ./fixtures/corpus-stats.sh
set -euo pipefail

fixtures_dir="$(cd "$(dirname "$0")" && pwd)"

stats_for() {
  local label="$1"
  local glob="$2"
  local sessions=0
  local total=0
  local max=0
  local missing=()

  for d in $glob; do
    [ -d "$d" ] || continue
    sessions=$((sessions + 1))
    local report="$d/analysis-report.json"
    if [ ! -f "$report" ]; then
      missing+=("$(basename "$d")")
      continue
    fi
    local n
    if ! n=$(jq -e '.divergencePoints | length' "$report" 2>/dev/null); then
      echo "ERROR: $report is not valid JSON (possibly an unmaterialised git-lfs pointer)" >&2
      exit 1
    fi
    total=$((total + n))
    if [ "$n" -gt "$max" ]; then max=$n; fi
  done

  printf "%-12s sessions=%-3d max=%-2d total=%d\n" "$label" "$sessions" "$max" "$total"
  if [ ${#missing[@]} -gt 0 ]; then
    printf "  missing analysis-report.json: %s\n" "${missing[*]}"
  fi
}

stats_for "injection"  "$fixtures_dir/sessions/*/"
stats_for "user-study" "$fixtures_dir/user-sessions/*/"
stats_for "agent"      "$fixtures_dir/agent-sessions/*/"
