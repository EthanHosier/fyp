#!/usr/bin/env python3
"""
Per-kind trajectory for each participant: 6 sessions × 4 kinds.

Shows whether each participant got better at specific kinds across
their 6-session arc.

Usage: per-kind-trajectory.py
"""
import json
import re
import sys
from collections import defaultdict
from pathlib import Path

KINDS = ["IDE_REPLAY", "HYGIENE", "ORDERING", "REWORK"]
ROOT = Path("fixtures/user-sessions")


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


def load_session(d):
    report = d / "analysis-report.json"
    if not report.exists():
        candidates = list(d.glob("*/analysis-report.json"))
        if not candidates:
            return None
        report = candidates[0]
    with open(report) as f:
        kinds = find_kinds(json.load(f))
    return {k: sum(1 for x in kinds if x == k) for k in KINDS}


def main():
    # Collect: by_participant[participant][session_idx] = {kind: count}
    by_participant = defaultdict(dict)
    for d in sorted(ROOT.iterdir()):
        if not d.is_dir():
            continue
        m = re.match(r"^([a-z]+)-(\d+)$", d.name)
        if not m:
            continue
        participant, idx = m.group(1), int(m.group(2))
        counts = load_session(d)
        if counts is None:
            continue
        by_participant[participant][idx] = counts

    # Per-participant table: 4 rows (kinds) × 6 columns (sessions) + delta
    for participant in sorted(by_participant):
        sessions = by_participant[participant]
        max_idx = max(sessions)
        print(f"\n=== {participant} ===\n")
        header = "kind".ljust(12) + "".join(f"S{i:>2}".rjust(6) for i in range(1, max_idx+1)) + "   delta(S6-S1)   trend"
        print(header)
        for kind in KINDS:
            counts_by_idx = [sessions.get(i, {}).get(kind, 0) for i in range(1, max_idx+1)]
            row = kind.ljust(12) + "".join(f"{c:>6}" for c in counts_by_idx)
            first, last = counts_by_idx[0], counts_by_idx[-1]
            delta = last - first
            # Trend: linear-ish read
            if all(c == 0 for c in counts_by_idx):
                trend = "—"
            elif sum(counts_by_idx[:3]) > sum(counts_by_idx[3:]):
                trend = "↓ declining"
            elif sum(counts_by_idx[:3]) < sum(counts_by_idx[3:]):
                trend = "↑ rising"
            else:
                trend = "≈ flat"
            row += f"   {delta:>+5}          {trend}"
            print(row)

    # Cross-participant: per-kind, average across both participants
    print("\n\n=== Both participants combined (sum per session) ===\n")
    print("kind".ljust(12) + "".join(f"S{i:>2}".rjust(6) for i in range(1, 7)) + "   delta(S6-S1)")
    for kind in KINDS:
        per_session = [
            sum(by_participant[p].get(i, {}).get(kind, 0) for p in by_participant)
            for i in range(1, 7)
        ]
        row = kind.ljust(12) + "".join(f"{c:>6}" for c in per_session)
        row += f"   {per_session[-1] - per_session[0]:>+5}"
        print(row)


if __name__ == "__main__":
    main()
