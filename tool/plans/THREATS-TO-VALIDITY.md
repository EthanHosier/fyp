# Threats to validity — notes for the discussion chapter

Bullet form, not LaTeX. Each section will be expanded into prose for the
thesis's discussion / threats-to-validity chapter. Numbers and claims
here are anchored to the experiments described in Chapter 5 of the
thesis as of the current session.

## Construct validity

- **No external ground truth for "true code quality" or "true refactoring-trajectory quality"** exists in the field. The score is grounded in literature-supported per-weight justifications (\S\ref{sec:methodology-weights}), not calibrated against an outcome variable. The claim is "process behaviours the literature identifies as bad practice", not "true code quality" in any absolute sense.
- **The score is not an attempt at a maintainability index** (e.g.\ Coleman et al.\ 1994). It does not predict bug rates, time-to-merge, or any other downstream-of-refactoring metric. The chapter does not make such a claim.
- **The divergence-point detector's "kind" labels are a methodology choice**, not a discovered taxonomy. A different label scheme (e.g.\ collapsing HYGIENE into TESTS_SKIPPED + COMMIT_GAP as separate top-level kinds) is defensible; the choice is grounded in the rater-design rationale at §\ref{sec:methodology-rater} but is not the only valid scheme.
- **`SuboptimalOrdering` is an author-claimed property of the playbook design, not a measured one.** The 5 ORDERING-injection sessions in the manifest carry pattern `SuboptimalOrdering` because the playbook author selected an ordering they believed was worse than its alternatives. The score formula cannot independently verify this; §\ref{sec:results-headline} reports exactly this finding — 0 of the 5 "suboptimal" orderings produce a positive-magnitude reorder alternative under the production formula, because the score formula and the synthesiser together treat commuting orderings as observationally equivalent. The label is therefore a statement of authorial intent rather than ground truth, and the headline §\ref{sec:results-headline} finding is consistent with the alternative reading that the orderings were never empirically suboptimal in the first place. This does not invalidate the prominence claim of §\ref{sec:results-divergence} (the ORDERING-kind catch ranks at top-1 within its session in both cases the detector did catch one), but it tightens what the "5 ordering injections" subset of the precision/recall numbers can be read as.

## Internal validity — calibration

- **Sensitivity sweep is single-knob in the headline.** Multi-knob coverage is via the Monte Carlo experiment (§\ref{sec:results-sensitivity}, MC paragraph): on the user-study corpus, top-$1$ stability falls from $95.0\%$ (single-knob) to $77.0\%$ (multi-knob), so the formula is robust to *local* but not *arbitrary* weight choices in the multiplicative-factor-of-two neighbourhood.
- **No rater-rank validation**: Cohen's $\kappa$ in §\ref{sec:results-userstudy} validates that raters agree on *which kinds fire* per session, not on the *order* the score puts divergence points in. Without this, the strongest claim the chapter can make is "the score is internally consistent and the kinds it surfaces match what raters identify". A rater-rank study (engage raters to rank the divergence points within each session, compute $\tau$-b against the score's ranking) would close this gap but was out of scope.
- **Cleanliness sub-weights are uniform-$1.0$** by Laplace's principle of insufficient reason. No per-signal calibration data exists. The sensitivity sweep does show cleanliness sub-weights are inert in the top of the ranking (Table \ref{tab:results-sensitivity-knobs}: zero top-$1$ disruptions across all six cleanliness knobs), which makes the uniform choice defensible *in practice* on this corpus, but is not a positive calibration result.
- **$\textsc{min\_commit\_gap} = 6$ is a heuristic** derived from the $60$ s Murphy-Hill batching primitive, not externally calibrated. The threshold is not sensitivity-tested directly; only the *weight* on the commit-gap term is perturbed in the sweep.
- **Production weight vector itself is hand-picked** within the literature-grounded severity ordering at §\ref{sec:methodology-weights}. The robustness experiments establish that the formula sits in a locally stable region around it; they do not establish that no other defensible vector exists in the same region.
- **Term-importance varies by corpus.** Ablation on the injection corpus identifies `manualIde`, `broken`, `length` as the dominant rank-carriers (Table \ref{tab:results-ablation-loo}); on the user-study corpus the dominant carrier is `commitGap` instead (Table \ref{tab:results-ablation-user-loo}). The formula's *effective parameter dimension* depends on the kind composition of the session being scored. This is a finding worth reporting, not just a limitation, but it qualifies any "this weight matters" claim.

## External validity — corpus

- **$45$ injection fixtures are scoped exercises**, not naturalistic sessions. Per-fixture divergence-point counts are tiny ($\max 5$, mean $1.5$). The top-$5$ hit rate on this corpus is *trivially* $100\%$ across the sensitivity sweep because no fixture has more than $5$ divergence points; the metric collapses to "did the divergence-point set change?". This is named in the chapter and accounts for the user-study corpus being the canonical sensitivity benchmark.
- **$12$ user-study sessions, $2$ participants** — small $N$, descriptive only. The chapter does not report inferential statistics on this corpus; numbers are reported as raw counts and proportions alongside qualitative findings.
- **$6$ agent sessions, $n = 1$ agent, one model version, one date**. The agent-comparison is illustrative rather than generalisable. A multi-agent study is named as future work.
- **All corpora are single-author IntelliJ sessions on a Gradle Java project**. The detector's behaviour on team-PR-driven workflows, other IDEs, or other languages is unstudied. The plugin's instrumentation is IntelliJ-specific; ports to VSCode or JetBrains-Aqua would require re-implementing the event capture layer.
- **Refactoring kinds the synthesiser cannot handle are excluded by construction.** A trajectory built entirely from refactoring operations outside the JDT engine's supported set (or outside RefactoringMiner's detection capability) would produce no IDE_REPLAY divergence points; the detector cannot flag what it cannot synthesise an alt for.

## Statistical validity

- **Cohen's $\kappa$ confidence intervals not reported.** With $45$ sessions per pair the intervals would be wide; the chapter reports point estimates alongside raw cell counts.
- **Sensitivity $\tau$-b is brittle on small per-fixture rankings**: a single pair swap on a three-point ranking moves $\tau$ by $0.333$. The chapter reports top-$1$ hit rate alongside as a more stable surrogate, especially on the user-study corpus.
- **No multiple-comparison correction** applied across the weight $\times$ factor sweep ($12 \times 9 = 108$ tests per fixture). The headline result is not a hypothesis test but a descriptive statistic; corrections would be appropriate if individual knob-factor cells were being interpreted as significance claims, which they are not.
- **The multi-knob Monte Carlo uses a fixed seed** ($\text{seed} = 1$) for reproducibility; the $200$-sample size was chosen heuristically rather than from a power calculation. Distribution shape is reported (mean $\tau$, top-$N$ hit rate) rather than a point estimate with confidence interval.

## Threats specific to the kind-classifier accuracy result

- **Precision $= 1.00$ on all four kinds is a corpus-conditional claim**: the manifest's `expected_kinds` field encodes what the playbook author intended the session to exercise, not what a session arbitrarily-recorded by a third party would contain. Precision on a free-form corpus is unstudied.
- **ORDERING recall ($0.36$) is documented and deliberate**: the reorder synthesiser's split-on-invalid gate collapses windows containing manual edits into singletons, producing no permutations to compare. The recall gap is left open because §\ref{sec:results-headline} establishes the score formula is insensitive to commuting reorders anyway.
- **IDE_REPLAY precision was $0.80$ before the plugin instrumentation fix** of §\ref{sec:results-scoped-out}; the false positives were upstream plugin-capture issues, not detector-logic errors. The current $1.00$ figure is post-fix; it would not hold on the pre-fix recordings.
- **TP classification is per-session, not per-step.** A divergence point is counted as a true positive if a DP of the expected kind fires *anywhere* in the session, not if it fires *at the step where the playbook injected the bad behaviour*. The looser policy is deliberate (step indices are a function of plugin edit-burst cadence and are not semantically stable across recordings — see methodology §3.x), but it means a session where the detector caught the right kind at a wrong step still counts as a TP. The author informally inspected the catches and did not find any such cases on the $45$-session set; this was not formally verified, and a free-form session corpus could plausibly contain right-kind-wrong-step catches that the looser policy would over-credit. Closing this loophole would require either (a) per-DP manual review of step anchoring against the playbook author's intent, or (b) a tighter step-stability guarantee from the plugin.

## Threats specific to the agent comparison

- **The agent has no IDE refactor surface** (Claude Code edits files via text tool calls, not through IntelliJ's Refactor menu). IDE_REPLAY count is therefore structurally bounded above zero whenever the agent performs any structural refactoring at all. This is acknowledged in §\ref{sec:results-userstudy-agent} but is a genuine measurement limitation, not a finding about the agent's capability.
- **Agent process score is highly variable across sessions** ($0$ to $100$). The variability tracks task structure (S2/S4 short vs S6 complex), not agent learning across the arc. No behavioural-improvement claim is made from the per-session score row.
- **$n = 1$ agent run** with feedback injection between sessions. No control condition (e.g.\ the same agent without feedback injection, to isolate the effect of the dashboard markdown) was run. The "agent saw the feedback and changed behaviour" claim is therefore observational, not controlled.
- **One agent ORDERING window in S1 was marked informational under the strict magnitude filter** of §\ref{sec:methodology-detector-ordering}; the agent acknowledged it from the dashboard view but the divergence point itself does not count under the experiment-stage filter. The "feedback drove behavioural change" narrative is correspondingly weak.

## Threats specific to the user-study

- **$n = 2$ participants** is the smallest meaningful sample. Per-participant trajectories are reported but no aggregate statistical claims (e.g.\ "the median participant improved on metric X") are made.
- **No control condition**: there is no group of participants who performed the same six-session playbook *without* dashboard feedback, against which to compare the with-feedback group's behavioural trajectory. The IDE_REPLAY and hygiene falls across the arc are *consistent with* feedback-driven behavioural change but do not establish causation.
- **Self-reported behavioural change is paraphrased**, not transcribed; the source notes are retained as private study artefacts (§\ref{sec:results-userstudy}'s footnote names this).
- **Anonymisation policy** (P1/P2 only, no participant names in the thesis) is enforced; the dashboard screenshot in §\ref{sec:arch-dashboard} was redacted before inclusion.

## Threats specific to the rater-study (κ)

- **Three labellers including the author**: the author is an inherent self-bias risk; the chapter reports pair-wise κ explicitly so the author-vs-rater $\kappa$ values are visible alongside the rater-vs-rater pair.
- **Two of the four kinds (REWORK and HYGIENE) show $\kappa < 1.0$**, with specific disagreement structure named in the chapter (REWORK: sessions $024$ and $043$ borderline; HYGIENE: sessions $014$, $015$, $016$, $039$ borderline). Reconciliation was deliberately *not* applied to inflate $\kappa$; the disagreements are reported as methodology findings.
- **All labels were assigned before any participant performed a user-study session**, so labelling cannot have been biased by what the participants later did. But the rater pool (the author + the two study participants) is the same pool as the study participants, so familiarity with the broader project is non-zero.

## Implementation threats

- **The shadow-repo reconstruction depends on the IntelliJ plugin's event stream being complete** (every state-bearing change is captured). Edit bursts are debounced over $2$ seconds (§\ref{sec:arch-events}); pathological rapid-fire activity that completes inside a single burst window would arrive as one event rather than several, possibly losing intermediate state.
- **The wrap-and-patch layer** of §\ref{sec:arch-wrap-patch} closes residual gaps between IntelliJ and JDT for accepted brackets but is not exhaustive; brackets where the gap cannot be safely patched receive `ast_diverged` and are excluded from reordering. Some legitimate reorder candidates are therefore left on the table.
- **The agent-test wrapper script + agent-finalize reconciliation** is a fragile capture mechanism: the script `scripts/reconcile-session-events.py` exists to handle the case where events arrived after the plugin's final flush, but a session lost to a hard plugin crash would not be recoverable.

## Out-of-scope by design

These are explicitly **not** threats — the thesis does not claim to address them and the chapters say so:

- Multi-agent agent comparison (named as future work in §\ref{sec:results-userstudy-agent}).
- Longitudinal validation against external code-quality outcomes (infeasible at MEng scope).
- Cross-IDE / cross-language validation (single-tool scope is named in the thesis).
- Production-weight refitting against an outcome dataset (no such dataset exists).
- Real-time / live-feedback dashboard mode (the dashboard is a session-replay view; live feedback during refactoring is flagged in §\ref{sec:results-userstudy}'s closing as future work).
