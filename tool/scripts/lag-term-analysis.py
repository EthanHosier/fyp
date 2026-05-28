#!/usr/bin/env python3
"""Compare baseline-no-lag CSVs against new lag-term CSVs to characterise the
impact of W_lag on ablation, sensitivity, MC robustness and divergence
counts/magnitudes. Prints a structured plaintext summary."""
import csv
from collections import defaultdict
from pathlib import Path
from statistics import mean, median

ROOT = Path("/Users/ethanhosier/Desktop/random/fyp/tool/fixtures/notebooks/data")
BASE = ROOT / "baseline-no-lag"

def load_csv(p):
    with open(p) as f:
        return list(csv.DictReader(f))

# ---- ABLATION ----
def ablation_solo_recovery(rows):
    """variant string contains a single knob set to nonzero (active_count==1)."""
    by_variant = defaultdict(list)
    for r in rows:
        if int(r['active_count']) == 1:
            by_variant[r['variant']].append(float(r['magnitude_recovery_fraction']))
    return {v: mean(xs) for v, xs in by_variant.items()}

def ablation_leave_one_out(rows, total_knobs):
    """active_count == total_knobs - 1 (one knob removed) -> tau, top5."""
    by_variant = defaultdict(lambda: {'tau': [], 'top5': [], 'rec': []})
    for r in rows:
        if int(r['active_count']) == total_knobs - 1:
            by_variant[r['variant']]['tau'].append(float(r['kendall_tau']))
            by_variant[r['variant']]['top5'].append(float(r['top5_hit_rate']))
            by_variant[r['variant']]['rec'].append(float(r['magnitude_recovery_fraction']))
    return {v: {k: mean(xs) for k,xs in d.items()} for v,d in by_variant.items()}

# ---- SENSITIVITY ----
def sens_per_weight(rows):
    """group by (group,weight,factor) -> mean tau, top1, top5."""
    by_key = defaultdict(lambda: {'tau': [], 'top1': [], 'top5': []})
    for r in rows:
        key = (r['group'], r['weight'], r['factor'])
        by_key[key]['tau'].append(float(r['kendall_tau']))
        by_key[key]['top1'].append(float(r['top1_hit_rate']))
        by_key[key]['top5'].append(float(r['top5_hit_rate']))
    return {k: {m: mean(xs) for m,xs in d.items()} for k,d in by_key.items()}

# ---- MC ----
def mc_summary(rows):
    taus = [float(r['kendall_tau']) for r in rows]
    t1 = [float(r['top1_hit_rate']) for r in rows]
    t5 = [float(r['top5_hit_rate']) for r in rows]
    return {'tau_mean': mean(taus), 'tau_med': median(taus),
            'top1': mean(t1), 'top5': mean(t5), 'n': len(rows)}

# ---- DIVERGENCE ----
def div_per_kind(rows):
    kinds = ['ordering', 'ide_replay', 'rework', 'hygiene']
    out = {}
    for k in kinds:
        counts = [int(r[f'{k}_dp_count']) for r in rows]
        # parse magnitude list
        mags = []
        for r in rows:
            raw = r[f'{k}_magnitudes']
            if raw:
                for x in raw.split(';'):
                    if x:
                        try:
                            mags.append(float(x))
                        except ValueError:
                            pass
        max_mags = [float(r[f'{k}_max_magnitude']) for r in rows if r[f'{k}_max_magnitude']]
        mean_mags = [float(r[f'{k}_mean_magnitude']) for r in rows if r[f'{k}_mean_magnitude']]
        out[k] = {
            'total_dp': sum(counts),
            'sessions_with_dp': sum(1 for c in counts if c > 0),
            'mean_count_per_session': mean(counts),
            'all_mags_mean': mean(mags) if mags else 0.0,
            'all_mags_max': max(mags) if mags else 0.0,
            'all_mags_n': len(mags),
            'mean_of_session_max': mean(max_mags) if max_mags else 0.0,
            'mean_of_session_mean': mean(mean_mags) if mean_mags else 0.0,
        }
    # injection classification across kinds
    classes = {'TP':0,'FP':0,'FN':0,'TN':0,'NA':0}
    by_kind_class = defaultdict(lambda: dict(TP=0,FP=0,FN=0,TN=0))
    for r in rows:
        for k in kinds:
            c = r[f'{k}_class']
            if c in by_kind_class[k]:
                by_kind_class[k][c] += 1
    inj_caught = sum(1 for r in rows if r['injection_caught'] == 'true')
    return out, by_kind_class, inj_caught

def fmt_dict(d, indent=2):
    pad = ' ' * indent
    return '\n'.join(f"{pad}{k}: {v}" for k,v in d.items())

# ===== RUN =====
print("="*80)
print("LAG TERM IMPACT ANALYSIS")
print("="*80)

for corpus in ['inj', 'user', 'agent']:
    print(f"\n\n##### CORPUS: {corpus} #####")

    # ablation
    base_rows = load_csv(BASE / f'ablation-{corpus}.csv')
    new_rows = load_csv(ROOT / f'ablation-{corpus}.csv')
    base_solo = ablation_solo_recovery(base_rows)
    new_solo = ablation_solo_recovery(new_rows)
    print("\n--- ABLATION: solo recovery (single-knob active) ---")
    print("knob (variant), baseline_recovery, new_recovery")
    all_knobs = sorted(set(base_solo) | set(new_solo))
    for k in all_knobs:
        b = base_solo.get(k, float('nan'))
        n = new_solo.get(k, float('nan'))
        print(f"  {k:30s}  base={b:.4f}  new={n:.4f}")

    # leave-one-out: baseline had 6, new has 7 knobs
    print("\n--- ABLATION: leave-one-out (n_total-1 knobs active) ---")
    base_loo = ablation_leave_one_out(base_rows, 6)
    new_loo = ablation_leave_one_out(new_rows, 7)
    print("  baseline (6 total, n-1=5):")
    for v, d in sorted(base_loo.items()):
        print(f"    {v:32s}  tau={d['tau']:.4f}  top5={d['top5']:.4f}  rec={d['rec']:.4f}")
    print("  new (7 total, n-1=6):")
    for v, d in sorted(new_loo.items()):
        print(f"    {v:32s}  tau={d['tau']:.4f}  top5={d['top5']:.4f}  rec={d['rec']:.4f}")

    # sensitivity
    base_s = load_csv(BASE / f'sens-{corpus}.csv')
    new_s = load_csv(ROOT / f'sens-{corpus}.csv')
    base_sens = sens_per_weight(base_s)
    new_sens = sens_per_weight(new_s)
    print("\n--- SENSITIVITY: per-weight perturbation (mean across factors & fixtures) ---")
    # group by weight name
    def collapse(d):
        out = defaultdict(lambda: {'tau':[],'top1':[],'top5':[]})
        for (grp,w,f), m in d.items():
            out[(grp,w)]['tau'].append(m['tau'])
            out[(grp,w)]['top1'].append(m['top1'])
            out[(grp,w)]['top5'].append(m['top5'])
        return {k:{m: mean(xs) for m,xs in d.items()} for k,d in out.items()}
    bcol = collapse(base_sens); ncol = collapse(new_sens)
    all_weights = sorted(set(bcol) | set(ncol))
    print(f"  {'weight':30s} {'b_tau':>8s} {'n_tau':>8s} {'b_top1':>8s} {'n_top1':>8s} {'b_top5':>8s} {'n_top5':>8s}")
    for k in all_weights:
        b = bcol.get(k); n = ncol.get(k)
        bt = f"{b['tau']:.4f}" if b else "  --  "
        nt = f"{n['tau']:.4f}" if n else "  --  "
        b1 = f"{b['top1']:.4f}" if b else "  --  "
        n1 = f"{n['top1']:.4f}" if n else "  --  "
        b5 = f"{b['top5']:.4f}" if b else "  --  "
        n5 = f"{n['top5']:.4f}" if n else "  --  "
        label = f"{k[0]}.{k[1]}"
        print(f"  {label:30s} {bt:>8s} {nt:>8s} {b1:>8s} {n1:>8s} {b5:>8s} {n5:>8s}")

    # MC
    base_mc = load_csv(BASE / f'mc-{corpus}.csv')
    new_mc = load_csv(ROOT / f'mc-{corpus}.csv')
    print("\n--- MULTIKNOB MC: log-normal perturbation summary ---")
    bsum = mc_summary(base_mc)
    nsum = mc_summary(new_mc)
    print(f"  baseline: tau_mean={bsum['tau_mean']:.4f} tau_med={bsum['tau_med']:.4f} top1={bsum['top1']:.4f} top5={bsum['top5']:.4f}")
    print(f"  new:      tau_mean={nsum['tau_mean']:.4f} tau_med={nsum['tau_med']:.4f} top1={nsum['top1']:.4f} top5={nsum['top5']:.4f}")

# Divergence
print("\n\n##### DIVERGENCE EXPERIMENT (corpus=inj, manifest-v2) #####")
base_d = load_csv(BASE / 'divergence.csv')
new_d = load_csv(ROOT / 'divergence.csv')
base_kinds, base_cls, base_caught = div_per_kind(base_d)
new_kinds, new_cls, new_caught = div_per_kind(new_d)

print(f"\nInjection caught: baseline={base_caught}/{len(base_d)}  new={new_caught}/{len(new_d)}")
print("\nPer-kind DP counts and magnitude summary:")
print(f"  {'kind':12s} {'metric':28s} {'baseline':>10s} {'new':>10s} {'delta':>10s}")
for k in ['ordering','ide_replay','rework','hygiene']:
    b = base_kinds[k]; n = new_kinds[k]
    for m in ['total_dp','sessions_with_dp','mean_count_per_session',
              'all_mags_mean','all_mags_max','all_mags_n',
              'mean_of_session_max','mean_of_session_mean']:
        bv = b[m]; nv = n[m]
        if isinstance(bv, float):
            print(f"  {k:12s} {m:28s} {bv:>10.4f} {nv:>10.4f} {nv-bv:>+10.4f}")
        else:
            print(f"  {k:12s} {m:28s} {bv:>10d} {nv:>10d} {nv-bv:>+10d}")

print("\nPer-kind injection classification (TP/FP/FN/TN):")
for k in ['ordering','ide_replay','rework','hygiene']:
    print(f"  {k:12s} baseline={base_cls[k]}  new={new_cls[k]}")

# Per-session ordering delta listing (focus on ORDERING)
print("\n--- ORDERING per-fixture changes ---")
base_map = {r['session_id']: r for r in base_d}
new_map = {r['session_id']: r for r in new_d}
print(f"  {'session':10s} {'expected':20s} {'b_count':>8s} {'n_count':>8s} {'b_maxmag':>10s} {'n_maxmag':>10s} {'b_meanmag':>10s} {'n_meanmag':>10s}")
for sid in sorted(base_map):
    if sid not in new_map: continue
    br = base_map[sid]; nr = new_map[sid]
    exp = br['expected_kinds']
    bc = int(br['ordering_dp_count']); nc = int(nr['ordering_dp_count'])
    bm = br['ordering_max_magnitude'] or '0'
    nm = nr['ordering_max_magnitude'] or '0'
    bmm = br['ordering_mean_magnitude'] or '0'
    nmm = nr['ordering_mean_magnitude'] or '0'
    if bc != nc or float(bm or 0) != float(nm or 0):
        marker = "*" if 'ORDERING' in exp else " "
        print(f"{marker} {sid:10s} {exp:20s} {bc:>8d} {nc:>8d} {bm:>10s} {nm:>10s} {bmm:>10s} {nmm:>10s}")
