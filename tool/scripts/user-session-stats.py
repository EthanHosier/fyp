#!/usr/bin/env python3
"""
Walk tool/fixtures/user-sessions/<participant>-<NN>/analysis-report.json
and emit per-session DP counts by kind, plus per-participant trajectory.

Usage: user-session-stats.py [user-sessions-dir]
"""
import json
import re
import sys
from collections import defaultdict
from pathlib import Path

KINDS = ["IDE_REPLAY", "REWORK", "HYGIENE", "ORDERING"]


def find_kinds(o):
    out = []
    if isinstance(o, dict):
        if "kind" in o and isinstance(o.get("kind"), str):
            out.append(o["kind"])
        for v in o.values():
            out.extend(find_kinds(v))
    elif isinstance(o, list):
        for v in o:
            out.extend(find_kinds(v))
    return out


def main():
    root = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("fixtures/user-sessions")
    sessions = []
    for d in sorted(root.iterdir()):
        if not d.is_dir():
            continue
        m = re.match(r"^([a-z]+)-(\d+)$", d.name)
        if not m:
            continue
        participant, idx = m.group(1), int(m.group(2))
        report = d / "analysis-report.json"
        if not report.exists():
            # Some sessions weren't unwrapped: the report is in a UUID subdir.
            candidates = list(d.glob("*/analysis-report.json"))
            if not candidates:
                print(f"WARN: no analysis-report under {d}", file=sys.stderr)
                continue
            report = candidates[0]
        with open(report) as f:
            r = json.load(f)
        kinds = find_kinds(r)
        # Only count divergence-point kinds (not e.g. PROCESS_SCORE_DEGRADED which
        # is a session-level summary). Filter to the four DP kinds.
        counts = {k: sum(1 for x in kinds if x == k) for k in KINDS}
        sessions.append((participant, idx, d.name, counts))

    # Per-session table
    print(f"\n{'session':12} {'IDE_REPLAY':>11} {'REWORK':>7} {'HYGIENE':>8} {'ORDERING':>9} {'total':>6}")
    for participant, idx, name, counts in sessions:
        total = sum(counts.values())
        print(f"{name:12} {counts['IDE_REPLAY']:>11} {counts['REWORK']:>7} {counts['HYGIENE']:>8} {counts['ORDERING']:>9} {total:>6}")

    # Per-participant trajectory: average DPs per session, plus session-N rate
    print("\n=== Per-participant trajectory (DP counts per session) ===")
    by_p = defaultdict(list)
    for participant, idx, _, counts in sessions:
        by_p[participant].append((idx, counts))
    for participant, rows in sorted(by_p.items()):
        rows.sort(key=lambda r: r[0])
        print(f"\n{participant}:")
        for idx, counts in rows:
            total = sum(counts.values())
            kbreakdown = " ".join(f"{k}={counts[k]}" for k in KINDS if counts[k] > 0)
            print(f"  session {idx}: {total} DPs ({kbreakdown or 'none'})")

    # Per-kind aggregate
    print("\n=== Aggregate (all 12 sessions) ===")
    agg = {k: 0 for k in KINDS}
    for _, _, _, counts in sessions:
        for k in KINDS:
            agg[k] += counts[k]
    total = sum(agg.values())
    print(f"  total DPs: {total}")
    for k in KINDS:
        share = agg[k] / total if total else 0
        print(f"  {k:12} {agg[k]:>4} ({share:.1%})")


if __name__ == "__main__":
    main()
