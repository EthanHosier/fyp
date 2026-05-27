# Pending re-runs after `isUserCommit` fix

A bug in `AnalysisReport.kt:694` was matching shadow-repo SHAs against
user-repo commit SHAs to set `cp.isUserCommit`. The two repositories have
independent parent chains so the comparison never matched, leaving
`isUserCommit = false` on every checkpoint of every session. This made the
COMMIT_GAP detector (and the process score's `n_cg(τ)` term) ignore real
commits and falsely fire after 6 consecutive green-refactor checkpoints.

Fix applied: changed the assignment to use the checkpoint's already-grouped
event list — `events.any { it.type == EventType.GIT_COMMIT }`.

## Already re-run

- `fixtures/user-sessions/bobby-01/`
- `fixtures/user-sessions/bobby-02/`
- `fixtures/user-sessions/bobby-03/`

Bobby-03 verified: `isUserCommit` now `true` on the three commit
checkpoints; the spurious HYGIENE (COMMIT_GAP) divergence point is gone
(4 DPs now vs 5 before).

## Still need re-run

Per-session analysis reports — run `./gradlew :analysis:run --args=<dir> -q`
on each:

- `fixtures/user-sessions/vlad-baseline-01..06/` (6 sessions)
- `fixtures/user-sessions/will-01..06/` (6 sessions)
- `fixtures/user-sessions/yukie-01..06/` (6 sessions)
- `fixtures/agent-sessions/claude-opus-4.7-medium-claude-code/01-agent..06-agent/` (6 sessions)
- `fixtures/agent-sessions/claude-opus-4.7-medium-opencode/01..06/` (6 sessions)

Total: 30 more per-session reruns. Each takes ~40-100s of wall clock.

## Experiment driver re-runs (45-session corpus)

The fix also affects every fixture's `isUserCommit`, which feeds
DerivedMetricsRunner's `commitGapEvents` counter and HygieneDetector's
COMMIT_GAP firings. Any of the 45 injection fixtures with real git commits
during recording will see their COMMIT_GAP divergence-point counts drop.
Need to re-run after the per-session rerun finishes:

- `./gradlew :analysis:ablation     --args="--corpus fixtures/corpus       --output notebooks/data/ablation-inj.csv"`
- `./gradlew :analysis:ablation     --args="--corpus fixtures/user-corpus  --output notebooks/data/ablation-user.csv"`
- `./gradlew :analysis:ablation     --args="--corpus fixtures/agent-corpus --output notebooks/data/ablation-agent.csv"`
- `./gradlew :analysis:sensitivity  --args="--corpus … --output …"`  (×3 corpora)
- `./gradlew :analysis:multiKnobMC  --args="--corpus … --output … --samples 200 --seed 1"`  (×3 corpora)
- `./gradlew :analysis:divergence   --args="--corpus fixtures/corpus --manifest fixtures/sessions/manifest-v2.csv --output notebooks/data/divergence.csv"`

## Thesis numbers that will shift

Most COMMIT_GAP-related numbers will move (downward, since the
false-positive firings disappear). Affected:

- `results.tex` §5.4.2 hygiene precision / recall / beats counts. HYGIENE
  total DPs may decrease.
- `results.tex` §5.4.2 detection-quality table — hygiene beats fraction
  could change.
- `results.tex` §5.1 ablation `commitGap` rows: solo recovery + LOO τ.
  The `commitGap` knob's contribution may shrink because there are fewer
  COMMIT_GAP events to weight.
- `results.tex` §5.4 user-study session distribution table (P1/P2 per-kind
  counts) — falsely-counted HYGIENE entries will disappear.
- `conclusion.tex` headline finding #2 (kind classifier precision) — still
  perfect, but underlying counts shift.

Note: the 4→10 ORDERING strictly-better count (the lag-term headline) is
on a different axis and won't be affected by this fix.
