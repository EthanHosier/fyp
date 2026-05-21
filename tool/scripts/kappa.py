#!/usr/bin/env python3
"""
Compute Cohen's kappa per kind between two manifests with the same
column layout as fixtures/sessions/manifest-v2.csv.

Usage: kappa.py <rater1.csv> <rater2.csv>

For each kind K in {ORDERING, IDE_REPLAY, REWORK, HYGIENE}:
  - For each session id present in both manifests, compute a 2x2
    confusion matrix: did rater1 include K in expected_kinds (Y/N)
    vs did rater2 include K (Y/N).
  - Report agreement counts and Cohen's kappa.
"""
import csv
import sys
from pathlib import Path

KINDS = ["ORDERING", "IDE_REPLAY", "REWORK", "HYGIENE"]


def load(path):
    """Return {session_id: set_of_kinds}."""
    out = {}
    with open(path) as f:
        for row in csv.DictReader(f):
            kinds = set()
            for k in (row["expected_kinds"] or "").split(";"):
                k = k.strip()
                if k:
                    kinds.add(k)
            out[row["session_id"]] = kinds
    return out


def kappa(r1_yes_r2_yes, r1_yes_r2_no, r1_no_r2_yes, r1_no_r2_no):
    n = r1_yes_r2_yes + r1_yes_r2_no + r1_no_r2_yes + r1_no_r2_no
    if n == 0:
        return float("nan")
    po = (r1_yes_r2_yes + r1_no_r2_no) / n
    r1_yes = (r1_yes_r2_yes + r1_yes_r2_no) / n
    r2_yes = (r1_yes_r2_yes + r1_no_r2_yes) / n
    pe = r1_yes * r2_yes + (1 - r1_yes) * (1 - r2_yes)
    if pe == 1:
        return 1.0 if po == 1.0 else float("nan")
    return (po - pe) / (1 - pe)


def main():
    if len(sys.argv) != 3:
        print("usage: kappa.py <rater1.csv> <rater2.csv>", file=sys.stderr)
        sys.exit(2)
    r1 = load(sys.argv[1])
    r2 = load(sys.argv[2])
    sessions = sorted(set(r1) & set(r2))
    print(f"{Path(sys.argv[1]).name} vs {Path(sys.argv[2]).name} — {len(sessions)} sessions in common\n")
    print(f"{'kind':12} {'n':>4} {'both_yes':>9} {'both_no':>8} {'r1y_r2n':>8} {'r1n_r2y':>8} {'agree%':>7} {'kappa':>7}")
    for kind in KINDS:
        yy = yn = ny = nn = 0
        for sid in sessions:
            in1 = kind in r1[sid]
            in2 = kind in r2[sid]
            if in1 and in2: yy += 1
            elif in1 and not in2: yn += 1
            elif not in1 and in2: ny += 1
            else: nn += 1
        n = yy + yn + ny + nn
        agree = (yy + nn) / n if n else 0
        k = kappa(yy, yn, ny, nn)
        print(f"{kind:12} {n:>4} {yy:>9} {nn:>8} {yn:>8} {ny:>8} {agree:>7.3f} {k:>7.3f}")


if __name__ == "__main__":
    main()
