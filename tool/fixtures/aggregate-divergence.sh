#!/usr/bin/env bash
# Aggregate divergence-results.csv into two-part breakdown:
#
#   Part 1 — Injection detection.
#     For each kind k, restrict to rows whose `injection` cell == k
#     (i.e. the deliberately injected pattern in that recording was
#     of kind k). Report:
#       — recall(k):           TP / (TP + FN)
#       — caught>0 rate(k):    rows where injection_caught == true
#         (i.e. detector both surfaced kind k AND the alt had
#         positive magnitude — a genuine "the IDE/perfect path
#         would have been better" claim).
#
#   Part 2 — Other expected detections.
#     For each kind k, restrict to rows where k ∈ expected_kinds
#     AND k != injection_kind (i.e. kinds the recording also
#     supports an alt for but weren't the deliberate injection
#     point). Report recall(k) on this subset — measures how well
#     the detector picks up "bonus" alt paths beyond the planted
#     pattern.
#
#   Part 3 — Precision (shared between parts).
#     For each kind k, count FPs (rows where k ∉ expected_kinds yet
#     a DP of kind k fired). Report precision(k) over the union of
#     Part 1 + Part 2 TPs.
#
# Usage:
#   ./fixtures/aggregate-divergence.sh /tmp/divergence-results.csv
set -euo pipefail
csv="${1:-/tmp/divergence-results.csv}"
[ -f "$csv" ] || { echo "usage: $0 <divergence-results.csv>" >&2; exit 2; }

# Column layout (1-indexed): session_id, pattern, strength, target_step,
# expected_kinds, observed_kinds,
# ordering_class, ide_replay_class, rework_class, hygiene_class,
# ordering_max_magnitude, ide_replay_max_magnitude, rework_max_magnitude, hygiene_max_magnitude,
# injection, injection_caught, injection_max_magnitude,
# endpoint_improved, perstep_hit, advisor_hit, control_dp_count
awk -F, '
BEGIN {
  split("ORDERING IDE_REPLAY REWORK HYGIENE", names, " ")
  for (i in names) class_col[names[i]] = 6 + i
  for (i in names) mag_col[names[i]]   = 10 + i
}
NR > 1 {
  expected = $5
  injection = $15
  injection_caught = $16
  for (i in names) {
    k = names[i]
    cls = $(class_col[k])
    is_expected = (index(expected, k) > 0)
    is_inj = (injection == k)
    if (is_inj) {
      inj_total[k]++
      if (cls == "TP") inj_tp[k]++
      if (cls == "FN") inj_fn[k]++
      if (injection_caught == "true") inj_caught[k]++
    } else if (is_expected) {
      oth_total[k]++
      if (cls == "TP") oth_tp[k]++
      if (cls == "FN") oth_fn[k]++
    } else {
      # not in expected — TN or FP slot
      if (cls == "FP") fp[k]++
      if (cls == "TN") tn[k]++
    }
  }
}
END {
  print "=== Part 1: Injection detection ==="
  print "(rows where the recording planted this kind deliberately)"
  printf "%-12s %5s %5s %5s %8s %12s\n","kind","n","TP","FN","recall","caught_mag>0"
  for (i = 1; i <= 4; i++) {
    k = names[i]
    n   = inj_total[k] + 0
    tp  = inj_tp[k] + 0
    fn  = inj_fn[k] + 0
    cau = inj_caught[k] + 0
    rec = (tp + fn) > 0 ? tp / (tp + fn) : 0
    cau_rate = n > 0 ? cau / n : 0
    printf "%-12s %5d %5d %5d %8.3f %12.3f\n", k, n, tp, fn, rec, cau_rate
  }
  print ""
  print "=== Part 2: Other expected detections (non-injection) ==="
  print "(rows where kind is in expected_kinds but was NOT the planted pattern)"
  printf "%-12s %5s %5s %5s %8s\n","kind","n","TP","FN","recall"
  for (i = 1; i <= 4; i++) {
    k = names[i]
    n  = oth_total[k] + 0
    tp = oth_tp[k] + 0
    fn = oth_fn[k] + 0
    rec = (tp + fn) > 0 ? tp / (tp + fn) : 0
    printf "%-12s %5d %5d %5d %8.3f\n", k, n, tp, fn, rec
  }
  print ""
  print "=== Part 3: Precision (shared) ==="
  print "(FP across all rows; precision = total_TP / (total_TP + FP))"
  printf "%-12s %5s %5s %5s %5s %10s\n","kind","TP_inj","TP_other","TP_all","FP","precision"
  for (i = 1; i <= 4; i++) {
    k = names[i]
    tpa = inj_tp[k] + oth_tp[k] + 0
    f   = fp[k] + 0
    prec = (tpa + f) > 0 ? tpa / (tpa + f) : 0
    printf "%-12s %5d %5d %5d %5d %10.3f\n", k, inj_tp[k]+0, oth_tp[k]+0, tpa, f, prec
  }
}
' "$csv"
