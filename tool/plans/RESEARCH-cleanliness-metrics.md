# Cleanliness sub-signals: per-metric methodology

This document is the Layer-1 companion to `RESEARCH-metrics-weighting.md`.
The two-layer methodology splits the process score into a
literature-backed cleanliness layer and a novel axiom-constrained
process layer. This file justifies the cleanliness layer signal by
signal: what each measures, why it belongs in a cleanliness composite,
how the raw signal is extracted, and how per-element values aggregate
into a single trajectory-level scalar.

Sub-signal *weights* within the composite live in
[§7 Composite code cleanliness](#7-composite-code-cleanliness).
Empirical robustness of the choices made here is tested by the
sensitivity analysis in `PLAN-experiment.md` (Experiment 1).

---

## 1. Cohesion (TCC)

### What it measures

Class-level cohesion captures the degree to which a class's methods
operate on the same internal state. A highly cohesive class has
methods that share access to instance variables, suggesting a single,
unified responsibility; a low-cohesion class has methods that
operate on disjoint subsets of state, suggesting it should be split
into smaller, more focused classes. Cohesion is a long-standing
indicator of design quality and is one of the structural axes
refactoring tools target when applying class-extraction operations.

### Why it matters for cleanliness

Bieman and Kang's 1995 study of cohesion in object-oriented systems
demonstrated that classes scoring poorly on cohesion metrics are more
likely to be candidates for design improvement, and that improvements
in cohesion correlate with improved reusability and reduced
maintenance burden. The cleanliness composite includes cohesion as a
distinct signal because cohesion-improving refactorings — Extract
Class in particular — are a recognised category of design improvement
that none of the other five sub-signals captures directly.

### Raw signal: per-class extraction

The per-class value is the **Tight Class Cohesion (TCC)** ratio
defined by Bieman and Kang (1995): the fraction of method pairs in a
class that share access to at least one instance variable, out of all
unordered method pairs. TCC has a single unambiguous definition and
avoids the variance issues that plague the LCOM family — LCOM has at
least five competing formulations (LCOM1 through LCOM5) whose values
disagree on the same input. Choosing TCC over LCOM is a deliberate
methodological choice in favour of a less contested signal.

TCC is undefined for classes with fewer than two eligible method
pairs (for example, classes with only one method, or classes whose
methods do not share fields by construction). The CK tooling reports
these as `null`, and we drop them from the aggregation rather than
substituting a sentinel value, because including them would falsely
characterise trivial classes as "fully cohesive" or "wholly
incohesive" depending on the substitution.

### Aggregation: per-element → trajectory-level scalar

A typical refactoring session touches only a handful of classes out
of potentially hundreds. Aggregating cohesion across the *whole
codebase* at each checkpoint dilutes the signal — a developer who
fixes a low-cohesion class barely moves the mean because the
untouched majority dominates the denominator. The score should
measure the cohesion of *the classes the developer worked on*, not
the codebase at large.

The trajectory-level cohesion value at each checkpoint is therefore
the arithmetic mean of TCC across the **trajectory-touched class
set** — the union of all classes whose files appear in
`cp.diff.perFileChurn` for any user-trajectory checkpoint, whether
the change was a manual edit or the output of an IDE refactoring.
This set is fixed for the entire session: every checkpoint, from
first to last, aggregates over the same set. The score moves only
when the *quality* of those classes changes; it never moves because
the set itself grew or shrank.

Per-class contribution at a checkpoint follows the simple rule that a
class contributes to the aggregate iff its file exists at that
checkpoint's snapshot. Three structural events handle naturally:

- **Class created mid-session** (Extract Class). Does not exist at
  earlier checkpoints; contributes from creation onwards. The
  trajectory shows its TCC evolution from its first appearance.
- **Class deleted mid-session** (Inline Class). Stops contributing
  after deletion. The trajectory shows its TCC up to the point of
  removal.
- **Class renamed mid-session**. The old and new paths both appear
  in `perFileChurn` across the rename checkpoint; both join the
  touched set. Each contributes at the checkpoints where its file
  exists, with no special identity-tracking required.

Mean aggregation is appropriate here because cohesion is a virtue
signal: average cohesiveness across the developer's working footprint
characterises overall design quality, and outlier sensitivity would
over-react to any one trivially-cohesive class in the set.

If every touched class has undefined TCC at a checkpoint — for
example, every touched class has only one method, or the touched
classes have fewer than two method pairs sharing fields — the
cohesion value carries forward from the previous checkpoint with a
defined value. If no such prior exists (the leading stretch of the
trajectory), the session-midpoint fill already implemented for
build-broken checkpoints (see
[`PLAN-metric-justification.md`](PLAN-metric-justification.md))
applies. This unifies "no cohesion signal at this step" cases
regardless of cause: untrustworthy checkpoint, every touched class
trivial, or no class yet exists in the touched set at this checkpoint.

### Implementation

- `analysis/src/main/kotlin/com/github/ethanhosier/analysis/metrics/derived/DerivedMetricsRunner.kt:381–382`
  — raw signal extraction (`tccs.average()`).
- `analysis/src/main/kotlin/com/github/ethanhosier/analysis/metrics/ck/` —
  CK runner producing `CkClassMetrics.tcc: Double?` keyed by `file`
  and `className`.
- The trajectory-touched set is computed in one upfront pass before
  the per-checkpoint walk in `DerivedMetricsRunner.run`, by unioning
  `cp.diff.perFileChurn[*].path` across every user-trajectory
  checkpoint. The set is then passed to `aggregate(cp, touchedSet)`
  and used to filter `ck.perClass` for each checkpoint.

### Limitations & future work

TCC inherits a structural limitation common to instance-variable-based
cohesion metrics: classes that achieve cohesion through method
chaining or dependency-injected collaborators score lower than their
design quality warrants. The score is also blind to *semantic*
cohesion — methods may share state syntactically but implement
unrelated behaviours. Future work could augment TCC with the textual
cohesion metrics of Marcus & Poshyvanyk (2005) or replace it
wholesale with a conceptual-cohesion model.

### Citations

```bibtex
@inproceedings{bieman1995cohesion,
  author    = {Bieman, James M. and Kang, Byung-Kyoo},
  title     = {Cohesion and Reuse in an Object-Oriented System},
  booktitle = {Proceedings of the 1995 Symposium on Software Reusability (SSR '95)},
  series    = {ACM SIGSOFT Software Engineering Notes},
  year      = {1995},
  pages     = {259--262},
  doi       = {10.1145/223427.211856}
}
```

---

## 2. Coupling (CBO)

### What it measures

Coupling captures the extent to which a class depends on other
classes. A class with high coupling is harder to change in isolation
— modifying its behaviour ripples through the classes it depends on,
and modifying those dependencies risks breaking the dependent. Low
coupling supports independent evolution: classes can be refactored,
replaced, or tested in isolation without disturbing the rest of the
system. Coupling is one of the most replicated structural quality
indicators in object-oriented design research, and it is a frequent
target of refactoring operations such as Move Method and Replace
Inheritance With Delegation.

### Why it matters for cleanliness

Chidamber and Kemerer's 1994 metrics suite identified Coupling
Between Object classes (CBO) as one of six foundational object-
oriented design measures, and subsequent empirical work has
repeatedly found CBO to correlate with fault-proneness, change
proneness, and maintenance effort. The cleanliness composite
includes coupling as a distinct signal because coupling is the
structural dual of cohesion: where cohesion measures within-class
relatedness, coupling measures cross-class dependence, and reducing
either at the expense of the other is a recognised anti-pattern. A
refactoring that improves cohesion by splitting a class but
introduces tight coupling between the resulting fragments has not
improved overall design quality.

### Raw signal: per-class extraction

The per-class value is **CBO** as defined by Chidamber and Kemerer:
the number of other classes a given class is coupled to through
method calls, field references, return types, parameter types,
exception types, or inheritance. CBO is symmetric — if class A is
coupled to class B, B is coupled to A — but each class's CBO counts
its own connections, so the metric is per-class rather than
per-edge. The CK tooling computes CBO directly per class without
filtering by coupling direction, matching the original definition.

### Aggregation: per-element → trajectory-level scalar

The trajectory-level coupling value at each checkpoint is the
arithmetic mean of CBO across the **trajectory-touched class set** —
the same set used by cohesion (see §1). Mean aggregation over the
touched set is appropriate for two reasons. First, the touched set
is small (typically 3–10 classes), and within a small set mean is
more stable than percentile-based summaries; the 90th percentile of
five values is essentially the worst-or-second-worst entry, barely
distinguishable from max. Second, restricting to the touched set
already provides the hot-spot focus that whole-codebase aggregation
would need percentile statistics to recover — the developer's
working footprint *is* where the coupling work is happening, so
every class in the set is already an actionable target.

Mean aggregation also matches the rule used for cohesion, giving the
methodology a single uniform aggregation principle: at every
checkpoint, both coupling and cohesion are the mean of their
per-class values across the trajectory-touched set, evaluated at
that checkpoint's snapshot. The same created/deleted/renamed
handling as cohesion applies here without modification.

If every touched class is absent at a given checkpoint (e.g. all
created later in the trajectory and this checkpoint precedes their
creation), the coupling value carries forward from the previous
defined value, or falls back to session-midpoint if no prior exists.

### Implementation

- `analysis/src/main/kotlin/com/github/ethanhosier/analysis/metrics/derived/DerivedMetricsRunner.kt:375`
  — current raw-signal extraction (`percentile(..., 0.9)`); under
  the agreed methodology this becomes `ck.perClass.filter { it.file
  in touchedSet }.map { it.cbo.toDouble() }.average()`.
- `analysis/src/main/kotlin/com/github/ethanhosier/analysis/metrics/ck/` —
  CK runner producing `CkClassMetrics.cbo: Int` keyed by `file` and
  `className`.

### Limitations & future work

CBO treats all forms of coupling as equivalent — a class coupled to
ten others through type imports is reported the same as a class with
ten chained method calls into a single foreign object. Subsequent
metrics in the literature, such as Message Passing Coupling (MPC)
and Information-Flow-Based Coupling, distinguish these forms but
require richer static-analysis tooling. CBO also captures only
class-level coupling; coupling between modules, packages, or
services is not represented. Future work could augment the signal
with package-level or module-level coupling metrics where the
codebase's modularity boundaries are larger than individual classes.

### Citations

```bibtex
@article{chidamber1994metrics,
  author  = {Chidamber, Shyam R. and Kemerer, Chris F.},
  title   = {A Metrics Suite for Object Oriented Design},
  journal = {IEEE Transactions on Software Engineering},
  volume  = {20}, number = {6}, pages = {476--493}, year = {1994},
  doi     = {10.1109/32.295895}
}
```

---

## 3. Code smells (PMD violation count)

### What it measures

Code smells are recurring patterns in source code that indicate
deeper design problems — overly long methods, primitive obsession,
shotgun surgery, feature envy, and similar. Each smell is a heuristic
signal that the code, while functionally correct, is harder to
understand, change, or test than it should be. The number of smells
present in a body of code is a coarse but well-established indicator
of design quality: codebases with many smells tend to be
maintenance-prone, and smell-resolving refactorings are the standard
mechanism for addressing them.

### Why it matters for cleanliness

Marinescu's 2004 detection-strategies work formalised the idea that
code smells can be operationalised as combinations of thresholded
structural metrics, turning Fowler's intuitive smell taxonomy into
mechanically detectable rule violations. Subsequent empirical work
has shown that smell density correlates with fault proneness and
change effort, and that smell-resolving refactorings are the most
common rationale developers cite for their refactoring choices. The
cleanliness composite includes smell count as a distinct signal
because none of the other five sub-signals captures *named* design
problems — coupling and cohesion measure structural shape; smells
encode specific recognised anti-patterns (god classes, long
parameter lists, dead code, etc.) that have their own remediation
playbooks.

### Raw signal: per-violation extraction

The per-element value is one PMD rule violation, as detected by the
PMD static analysis tool's configured rulesets. Each violation
carries a file path, line range, rule name, and priority. PMD's
priority field is an integer from 1 (highest severity) through 5
(lowest); the rule-to-priority mapping is set by the configured
rulesets and follows the broad PMD convention of "blocker /
critical / major / minor / info" severity tiers, mirroring the
SonarQube severity ladder.

Marinescu's detection-strategies framing is the literature precedent
for treating violations as the unit of measurement: rather than
expressing smells as continuous metric values, each smell is a
boolean predicate over thresholded metrics, and the detector emits
discrete violation events when those predicates hold. PMD operates
on the same model.

### Aggregation: per-element → trajectory-level scalar

The trajectory-level smell value at each checkpoint is the **count
of PMD violations whose file lies in the trajectory-touched set**,
evaluated at that checkpoint's snapshot. The same trajectory-touched
set used by cohesion and coupling (§1, §2) applies: it is computed
once from the union of `cp.diff.perFileChurn[*].path` across every
user-trajectory checkpoint, and held fixed throughout the session.
A violation contributes iff its file belongs to that set and exists
at this checkpoint's snapshot.

This sub-signal is intentionally an unweighted count. Severity-
weighted treatment of smells is reserved for the standalone process-
score smell term (`W_SMELL`), which time-integrates a priority-
weighted open-smell load over the trajectory. Keeping the
cleanliness sub-signal as a raw count avoids double-counting severity
across the cleanliness composite and the process-score smell penalty.
The two PMD-derived signals are then cleanly separated: the
cleanliness sub-signal asks *"how many smells live in your working
footprint right now?"*, while the smell-ledger penalty asks *"how
much severity-weighted smell load have you been carrying over the
session?"*.

Pre-existing smells in touched files are counted, consistent with
the treatment of cohesion and coupling: the cleanliness composite
measures the quality of the developer's working footprint at each
checkpoint, not the deltas introduced by their actions. A file
touched but left full of pre-existing smells contributes those
smells to the count; if the user resolves them later in the
trajectory, the count drops at that checkpoint. The standalone
process-score `W_SMELL` term, which uses
`PmdViolationTracker`-derived ledger data, is the dedicated mechanism
for crediting smell resolution and penalising smell introduction.

### Implementation

- `analysis/src/main/kotlin/com/github/ethanhosier/analysis/metrics/derived/DerivedMetricsRunner.kt:399`
  — current raw-signal extraction (`pmd.violations.size`); under the
  agreed methodology this becomes
  `pmd.violations.count { it.file in touchedSet }`.
- `analysis/src/main/kotlin/com/github/ethanhosier/analysis/metrics/pmd/` —
  PMD runner producing `PmdViolation` entries keyed by `file`, `rule`,
  and line range.

### Limitations & future work

Smell detection has well-documented precision and recall limitations.
Arcelli Fontana et al. (2016) report that smell detectors disagree
substantially on the same inputs, and Paiva et al. (2017) document
high false-positive rates for some rule families. The cleanliness
composite inherits this noise: a configured ruleset that fires
aggressively on a particular code style will inflate the smell count
without reflecting genuine design degradation. The mitigation is
range-normalisation within the session — the smell signal is
normalised to its own observed `[lo, hi]` range before contributing
to the composite, so consistent overcounting across the trajectory
does not bias the score. Future work could augment PMD with the
output of one or more academic smell detectors (Decor, JDeodorant)
and report the agreement set, following the consensus-detection
approach in recent empirical literature.

### Citations

```bibtex
@inproceedings{marinescu2004detection,
  author    = {Marinescu, Radu},
  title     = {Detection Strategies: Metrics-Based Rules for Detecting Design Flaws},
  booktitle = {20th IEEE International Conference on Software Maintenance (ICSM 2004)},
  pages     = {350--359},
  year      = {2004},
  doi       = {10.1109/ICSM.2004.1357820}
}
```

---

## 4. Duplication (CPD)

### What it measures

Code duplication captures the presence of structurally identical or
near-identical fragments at different locations in the codebase.
Duplication is one of the most direct indicators that a codebase
violates the Don't-Repeat-Yourself principle: a change to one copy
of a duplicated block typically requires synchronised changes to
every other copy, and missing one is a recognised source of defects.
Refactorings such as Extract Method, Extract Class, and Pull Up
Method exist primarily to eliminate duplication.

### Why it matters for cleanliness

Heitlager, Kuipers, and Visser's 2007 SIG maintainability model
identifies duplication as one of four primary maintainability
indicators alongside unit size, unit complexity, and unit
interfacing. Their motivation is empirical: industrial maintenance
projects consistently correlate duplication rates with effort spent
on bug fixes and feature additions. Fowler's earlier treatment in
*Refactoring* (1999) makes the case even more strongly, calling
duplication "the number one in the stink parade" of smells —
because duplication is both common and reliably remediable, it
serves as a load-bearing signal in any cleanliness composite.

### Raw signal: per-occurrence extraction

PMD's Copy-Paste Detector (CPD) tokenises every source file in the
codebase, generates fingerprints of sliding token-windows above a
configurable minimum length, and identifies matching fingerprints
across the whole source tree. Each match produces a **clone group**
— a set of two or more occurrences that share a common token
sequence — and each occurrence carries a file path and line range.

The raw per-element value for duplication is therefore an
*occurrence*: a single appearance of a duplicated fragment in a
specific file. CPD's clone-detection runs codebase-wide so that
duplications which span touched and untouched files are still found,
but the per-occurrence output is what the aggregation consumes.

### Aggregation: per-element → trajectory-level scalar

The trajectory-level duplication value at each checkpoint is the
**touched-file duplication rate**:

> `duplicated_lines_within_touched_files / total_lines_in_touched_files`

evaluated at that checkpoint's snapshot, where the trajectory-touched
file set is the same one used by cohesion, coupling, and smells.
A clone-group occurrence contributes to the numerator iff its file
belongs to the touched set; the other occurrences in the same group
(in untouched files) do not. The denominator is the total line
count of touched-set files that exist at this checkpoint, mirroring
the create/delete/rename handling already established in §1.

The choice deserves explicit defence because it is not the default
duplication metric: CPD itself reports a single codebase-wide
percentage, `duplicated_tokens / total_tokens`, and that whole-
codebase rate is the conventional measurement in maintainability
literature (Heitlager et al. 2007). The cleanliness composite uses a
touched-file rate instead for two reasons grounded in the thesis's
purpose.

First, the thesis evaluates the developer's *process* on a refactoring
trajectory, not the long-run maintainability of the codebase as a
whole. A focused deduplication within a small touched footprint —
typically 3–10 files in a session — produces a real, measurable
local improvement that the whole-codebase rate attenuates in
proportion to the size mismatch. A 400-token deduplication in a 1k-
token touched footprint moves the touched-file rate by ~40
percentage points; the same deduplication in a 100k-token codebase
moves the whole-codebase rate by 0.4 points. The whole-codebase rate
is the correct measure for *codebase maintainability*; the touched-
file rate is the correct measure for *process quality on the touched
footprint*.

Second, restricting the numerator to touched-file occurrences while
keeping CPD's whole-codebase clone detection preserves the
duplication-signal's semantic integrity. A clone group with five
occurrences across one touched file and four untouched files is
still detected as one group of five; the touched occurrence still
counts as duplicated even though the user cannot affect the other
four without expanding their working footprint. The user is
evaluated on what their footprint contains, not on what the rest of
the codebase looks like.

The same trajectory-touched set rule used in §1, §2, §3 applies here
without modification. The composite no longer has an aggregation
exception for duplication: every cleanliness sub-signal (with the
exception of the codebase-wide readability blend, §5) is measured
against the developer's touched footprint.

### Implementation

- `analysis/src/main/kotlin/com/github/ethanhosier/analysis/metrics/derived/DerivedMetricsRunner.kt:387`
  — current raw-signal extraction (`cpd.duplicatedLinesShare * 100.0`,
  whole-codebase); under the agreed methodology this becomes a
  per-occurrence filter against the touched file set, divided by the
  touched-file line count.
- `analysis/src/main/kotlin/com/github/ethanhosier/analysis/metrics/cpd/` —
  CPD runner producing `CpdResult.duplicatedLinesShare: Double`
  (whole-codebase percentage, retained for diagnostic reporting) and
  `CpdResult.duplications[].occurrences[].file` plus line ranges
  (used for the touched-file rate).
- Per-file line counts at each checkpoint are sourced from the
  readability runner's `ReadabilityResult.perFile` output (which
  already computes per-file line counts), avoiding a separate
  filesystem walk.

### Limitations & future work

CPD is a Type-1/Type-2 clone detector: it finds exact and parameter-
renamed clones reliably but misses semantically equivalent
duplications expressed in different syntactic forms (Type-3 and
Type-4 clones). Languages with rich macro or generics systems can
hide duplication that CPD's token-stream model cannot see.

The touched-file rate also inherits a structural subtlety: the
denominator (total lines in touched files) responds to file growth
within the touched set. A user who adds a large amount of new non-
duplicated code to a touched file mechanically reduces the rate
without resolving any duplication, and a user who shrinks a touched
file by removing non-duplicated code can mechanically *raise* the
rate. The companion process-score signals (in particular the smell
ledger and the cleanliness gain term) help disambiguate these cases,
but the touched-file duplication rate alone cannot distinguish
"removed duplication" from "diluted with new code". Future work
could augment CPD with AST-level clone detection (ConQAT, Deckard)
to capture Type-3 clones, and surface the absolute duplicated-line
count alongside the rate as a diagnostic alongside the normalised
sub-signal.

### Citations

```bibtex
@inproceedings{heitlager2007maintainability,
  author    = {Heitlager, Ilja and Kuipers, Tobias and Visser, Joost},
  title     = {A Practical Model for Measuring Maintainability},
  booktitle = {6th International Conference on the Quality of Information and Communications Technology (QUATIC 2007)},
  pages     = {30--39},
  year      = {2007},
  doi       = {10.1109/QUATIC.2007.8}
}
```

---

## 5. Readability

### What it measures

Readability captures how easily a human reader can extract meaning
from source code: how much cognitive effort it takes to scan a
function, follow control flow, and parse the names of the variables
and methods involved. Readable code is not the same as correct or
performant code — a tightly nested or cryptically named function
can be functionally faultless and still impede every reader who has
to modify, debug, or review it. Refactoring operations such as
Rename Variable, Extract Method, and Replace Magic Number With
Constant target readability directly.

### Why it matters for cleanliness

Buse and Weimer's 2010 study established readability as a
quantitatively measurable property: human-rated readability scores
for short Java snippets correlate strongly with structural and
textual features of the source (line length, indentation depth,
identifier characteristics, presence of recognisable words). Their
work and subsequent follow-ups demonstrated that readability scores
predict downstream maintenance outcomes — defects, time-to-
understand, modification effort — independently of complexity and
coupling. The cleanliness composite includes readability because
none of the other five sub-signals captures the *surface form* of
the code as it presents to a reader: a refactor that improves
structural metrics but leaves the names cryptic and the lines long
has not improved cleanliness in the dimension a reader experiences.

### Raw signal: per-file extraction

The raw signal is a per-file set of five textual and structural
ratios drawn from Buse and Weimer's feature taxonomy. The
readability runner computes each ratio over every source file:

- **Average line length** in characters, capped at 100 (longer →
  worse readability).
- **Average indentation** in levels, capped at 12 (deeper → worse).
- **Average identifier length** in characters, target ≥ 5
  (shorter → worse — short names tend to be less descriptive).
- **Single-letter identifier ratio** — fraction of identifiers
  that are single characters (higher → worse).
- **Dictionary-word ratio** — fraction of identifier tokens
  recognised as English dictionary words (higher → better, since
  recognisable words carry more meaning than acronyms or
  abbreviations).

Each ratio is normalised to `[0, 1]` with the convention that 1
represents the readable end and 0 the unreadable end. The cap-based
normalisation (line length capped at 100, indentation at 12) follows
the same shape as Buse and Weimer's own feature binning, though our
specific cap values are chosen for the modern Java conventions our
target codebases follow.

### Aggregation: per-element → trajectory-level scalar

The trajectory-level readability value at each checkpoint is
computed by:

1. Filtering `ReadabilityResult.perFile` to files in the trajectory-
   touched set (defined in §1).
2. Aggregating the five per-file ratios into a single set of
   trajectory-touched ratios using a **mean weighted by per-file
   line count**. A 1000-line file imposes more reader burden than a
   10-line file, so its features contribute proportionally more to
   the touched-footprint readability.
3. Applying the linear blend formula:

   ```
   readability = 0.20 · lineLengthScore
               + 0.20 · indentationScore
               + 0.20 · identifierLengthScore
               + 0.20 · singleLetterScore
               + 0.20 · dictionaryWordScore
   ```

   producing a single scalar in `[0, 1]`, then scaling by 100.

The blend uses **uniform 0.20 weights** across the five features.
This is the principled fallback in the absence of calibration data:
*Laplace's principle of insufficient reason* — when no empirical
evidence distinguishes the relative importance of features, treat
them symmetrically. Quamoco (Wagner et al. 2015) adopts the same
default for its quality-tree aggregation when expert calibration is
unavailable. The robustness of the uniform-weighting choice is
tested in the sensitivity analysis (see `PLAN-experiment.md`,
Experiment 1), where each feature's weight is perturbed
independently and the impact on top-K divergence rankings reported.

The same trajectory-touched set rule used in §1–§4 applies. Files
created mid-session contribute from creation onwards; deleted files
drop out post-deletion; renamed files contribute under both paths
at their respective lifecycles. If no touched file exists at a
checkpoint, the readability value carries forward from the previous
defined value or falls back to session-midpoint per the trust-signal
mechanism.

### Implementation

- `analysis/src/main/kotlin/com/github/ethanhosier/analysis/metrics/derived/DerivedMetricsRunner.kt:395, 417–428`
  — current implementation uses `cp.metrics.readability.summary`
  (whole-codebase aggregate) and the legacy `0.25/0.20/0.20/0.15/
  0.20` weighting; under the agreed methodology this becomes a
  filter over `ReadabilityResult.perFile` for the touched set,
  followed by line-count-weighted re-aggregation and the uniform
  `0.20` blend.
- `analysis/src/main/kotlin/com/github/ethanhosier/analysis/metrics/readability/` —
  readability runner producing `ReadabilityResult.perFile` (per-file
  ratios + line counts) and `summary` (whole-codebase aggregate).

### Limitations & future work

Readability is the sub-signal with the weakest direct paper backing
in the cleanliness composite, and this section makes that explicit.
Buse and Weimer's 2010 paper introduced both a *feature taxonomy*
(structural and textual properties of source) and a *trained model*
(logistic regression with empirically-derived coefficients over
human-rated readability data). We adopt the feature taxonomy — line
length, indentation, identifier characteristics — but we do not
adopt the trained model.

Two reasons drive this choice. First, scope: re-implementing Buse
and Weimer's logistic regression requires sourcing their published
coefficients (Table 4 of their 2010 paper), re-implementing roughly
fifteen additional features they trained over, and validating that
the reproduction matches their results. This is several days of
engineering work that yields a marginal improvement in defensibility
without changing the substance of what the composite measures.
Second, statistical validity: extracting a subset of their
coefficients in isolation is methodologically problematic, because
logistic-regression coefficients are conditional on the other
features in the model. Using only five of their fifteen-plus
features with their original coefficients would produce a model
that is neither theirs nor ours, while looking deceptively paper-
backed.

The honest alternative — a uniformly-weighted blend of the five
features we do compute, validated by sensitivity analysis — is what
this composite uses. Scalabrino et al. 2018 propose a more modern
readability model incorporating textual features beyond the Buse-
Weimer set, and would be the most defensible upgrade path for a
follow-up version of this work; the engineering cost was deemed
out of scope for this thesis. The choice of five features is also
limited: the readability runner does not capture comment density,
function length, or local control-flow complexity, all of which
have been shown in the literature to contribute to readability.
Future work could expand the feature set or switch to a Scalabrino-
style hybrid (structural + lexical + textual) model and recalibrate
weights against a small in-domain human-rated sample.

### Citations

```bibtex
@article{busweimer2010readability,
  author  = {Buse, Raymond P. L. and Weimer, Westley R.},
  title   = {Learning a Metric for Code Readability},
  journal = {IEEE Transactions on Software Engineering},
  volume  = {36}, number = {4}, pages = {546--558}, year = {2010},
  doi     = {10.1109/TSE.2009.70}
}

@article{scalabrino2018readability,
  author  = {Scalabrino, Simone and Linares-V{\'a}squez, Mario and Oliveto, Rocco and Poshyvanyk, Denys},
  title   = {A Comprehensive Model for Code Readability},
  journal = {Journal of Software: Evolution and Process},
  year    = {2018},
  doi     = {10.1002/smr.1958}
}
```

---

## 6. Cognitive complexity

### What it measures

Cognitive complexity measures how hard a piece of code is to
understand when read — the mental effort a reader expends when
walking through control flow, tracking nesting, and reasoning about
the relationships between control structures. It is a deliberate
departure from McCabe's cyclomatic complexity: rather than counting
linearly independent control-flow paths, cognitive complexity
assigns weighted increments per construct, with deeper nesting and
breaks in linear flow attracting larger increments than flat
control structures of equal cyclomatic count. The metric explicitly
targets human readers, not test-case enumeration.

### Why it matters for cleanliness

Campbell's 2018 cognitive-complexity formulation, originating from
work at SonarSource, was introduced as a more empirically meaningful
indicator of method-level understandability than cyclomatic
complexity. Subsequent industrial evidence and tooling integration
(SonarQube, several IDE plugins) have made cognitive complexity the
de facto modern complexity measure for OO codebases. The
cleanliness composite includes it as a distinct signal because none
of the other five sub-signals captures *within-method* control-flow
complexity: cohesion and coupling are class-level, smells encode
specific patterns, duplication is fragment-level, and readability
measures lexical and structural surface form. Cognitive complexity
fills the gap between "the code reads naturally" (readability) and
"the design is structurally sound" (cohesion/coupling) by measuring
the imperative texture of the code's interior.

### Raw signal: per-method extraction

The per-method value is Campbell's cognitive complexity score:
the sum of weighted increments per control-flow construct, with
weights set per Campbell's rules — for example, a top-level `if`
adds 1; a `for` nested inside it adds 2 (1 for the construct + 1
for the nesting); a `catch` deep inside a method adds more
depending on accumulated depth. The PMD method-metrics runner
implements Campbell's algorithm directly and exposes the per-
method score in `PmdResult.methodMetrics`, keyed by file, class,
and method signature.

The metric is defined per method and is undefined at file or class
level in Campbell's original specification — aggregations across
methods are a downstream design choice rather than a property of
the metric itself.

### Aggregation: per-element → trajectory-level scalar

The trajectory-level cognitive complexity value at each checkpoint
is the **mean of per-method scores across methods whose file lies
in the trajectory-touched set**, evaluated at that checkpoint's
snapshot. The same touched-set definition from §1 applies.

Mean aggregation is chosen for three reasons consistent with the
treatment of cohesion (§1) and coupling (§2):

First, methodological symmetry across the three per-element metrics
matters for the methodology chapter — cohesion, coupling, and
cognitive complexity all aggregate as means over the touched set,
under a single uniform rule. An asymmetric choice (Σ for cognitive
complexity but mean for the other two) would require its own
defence and complicate the composite's narrative.

Second, mean aggregation correctly rewards Extract Method, one of
the most common refactoring operations. Splitting a single high-
cognitive-complexity method into several lower-complexity helpers
reduces the mean even though the total cognitive complexity across
methods may stay roughly constant. Σ-aggregation has the opposite
sign — because Campbell's algorithm adds a fixed increment per
method entry, splitting a method strictly increases Σ. Mean is
the aggregation that matches the score's purpose of evaluating
refactoring decisions.

Third, the trajectory-touched set already narrows the scope to the
developer's working footprint, so the "total cognitive burden of
the codebase" interpretation that Σ would carry is not relevant
here — the scope is already bounded by the touched set. Size-
invariance, which mean provides, is more useful than total-burden
semantics in this context.

Created, deleted, and renamed methods are handled by the same rule
that governs files (a method contributes iff its file exists at
this checkpoint's snapshot and lies in the trajectory-touched set).
A new helper extracted into a touched file is a new method in the
touched set and joins the mean from its creation onwards. A method
inlined and removed drops out of the mean post-deletion.

If no touched method exists at a checkpoint (e.g. all methods are
in files yet to be created), the cognitive complexity carries
forward from the previous defined value or falls back to session-
midpoint per the trust-signal mechanism.

The whole-codebase Σ value (the current implementation) is retained
as a diagnostic alongside the touched-only mean, mirroring the
treatment of `duplicatedLinesShare` in §4 — it gives the thesis a
"total cognitive burden of the codebase" view independent of the
process-evaluation signal.

### Implementation

- `analysis/src/main/kotlin/com/github/ethanhosier/analysis/metrics/derived/DerivedMetricsRunner.kt:398`
  — current raw-signal extraction (`pmd.methodMetrics.sumOf { it.cognitive }`);
  under the agreed methodology this becomes
  `pmd.methodMetrics.filter { it.file in touchedSet }.map { it.cognitive }.average()`.
- `analysis/src/main/kotlin/com/github/ethanhosier/analysis/metrics/pmd/` —
  PMD runner producing `PmdResult.methodMetrics` keyed by `file`,
  `className`, and method signature.

### Limitations & future work

Campbell's cognitive complexity has been criticised for the lack of
a published, peer-reviewed empirical validation — the algorithm and
its weights are defined in a SonarSource white paper rather than a
formally reviewed academic publication. Subsequent third-party
studies have generally found cognitive complexity to predict
understandability better than cyclomatic complexity, but the
weights themselves are not derived from a fitted model and the
boundary cases (e.g. how exception flow is weighted) have been the
subject of ongoing debate. The metric is also language-agnostic in
principle but its weights are calibrated implicitly against C-style
imperative code; functional or reactive code styles may score
artificially low or high.

Mean aggregation has a subtle limitation: a session that adds a
trivial helper and never touches a complex method can reduce the
mean simply by enlarging the denominator. The companion process-
score signals (cleanliness gain, smell ledger) partly compensate by
penalising other dimensions of degradation, but the mean alone
cannot distinguish "diluted the average with simple methods" from
"genuinely reduced complexity per method". Future work could
augment the signal with a P90 view (worst-method focus) reported
alongside the mean, surfacing both "typical complexity" and "hot-
spot complexity" without committing to one aggregation.

### Citations

```bibtex
@techreport{campbell2018cognitive,
  author      = {Campbell, G. Ann},
  title       = {Cognitive Complexity: A new way of measuring understandability},
  institution = {SonarSource SA},
  year        = {2018},
  url         = {https://www.sonarsource.com/resources/cognitive-complexity/}
}
```

---

## 7. Composite code cleanliness

### What it measures

Code cleanliness is a single scalar summarising the structural and
textual quality of the source on a unit interval — composed from the
six preceding sub-signals (cohesion, coupling, smells, duplication,
readability, cognitive complexity). The composite expresses, in one
number, the answer to: *"how clean is the developer's working
footprint at this checkpoint, relative to the range of cleanliness
seen across the session?"*. It is the Layer-1 input to the process
score (`W_GAIN`, `W_DEGRADATION`, and the smell-ledger terms all
read this value or its trajectory) and is also surfaced directly to
the developer as a cleanliness score in the dashboard.

### Why a composite

No single structural metric captures cleanliness in all its forms.
A class can be cohesive but tightly coupled; a function can be
short but cognitively complex; identifier names can be readable but
the file riddled with smells. Quamoco (Wagner et al. 2015) and
QMOOD (Bansiya and Davis 2002) both treat code quality as a tree
of measurable factors that roll up into higher-level attributes —
their structural blueprint is what this composite follows. Six
sub-signals were chosen because each captures a distinct quality
dimension that the others miss: §1 cohesion measures within-class
relatedness; §2 coupling measures cross-class dependence; §3 smells
encode specific recognised anti-patterns; §4 duplication identifies
the most direct DRY violations; §5 readability captures surface
form; §6 cognitive complexity measures within-method understandability
load. Coverage across these six exhausts the structural-and-textual
quality space that the underlying tooling (CK, PMD, CPD, the
readability runner) can observe.

### Composition: per-sub-signal → composite scalar

The composite at each checkpoint is computed in three steps.

**Step 1 — range-normalisation per sub-signal, session-relative.**
Each sub-signal's raw value is mapped to `[0, 1]` using the min and
max observed across the session's trustworthy checkpoints:

```
n = (raw − lo) / (hi − lo)
```

with the convention that 1 represents the cleanest end and 0 the
least clean. Sub-signals where lower values are cleaner (coupling,
smells, duplication, cognitive complexity, average line length within
readability) are inverted: `n ← 1 − n`. The same range derivation is
shared with the trust-signal carry-forward machinery already
documented in `PLAN-metric-justification.md`, which excludes
untrustworthy checkpoints from range computation so that broken-state
metric values cannot deflate the `lo` floor.

Range-normalisation is session-relative rather than absolute. This
is a deliberate methodological choice: the thesis evaluates process
quality *within a refactoring session*, not codebase quality on an
absolute scale. A score of 50 in two different sessions is not a
claim that the codebases are equally clean — it is a claim that
each codebase sits at the midpoint of its own session's cleanliness
range. Cross-session comparison of absolute composite values is
explicitly out of scope.

**Step 2 — weighted average across sub-signals.** The six
normalised values combine as:

```
cleanliness_scalar = Σ wᵢ · nᵢ / Σ wᵢ
```

with division by `Σ wᵢ` so that sub-signals which are undefined at a
particular checkpoint (e.g. cohesion when every touched class has
fewer than two method pairs, or readability when no touched file
exists) drop out of both numerator and denominator without biasing
the score.

The weights are **uniform across the six sub-signals**:

| Sub-signal | Weight |
|------------|--------|
| Cohesion | 1/6 ≈ 0.167 |
| Coupling | 1/6 ≈ 0.167 |
| Smells | 1/6 ≈ 0.167 |
| Duplication | 1/6 ≈ 0.167 |
| Readability | 1/6 ≈ 0.167 |
| Cognitive complexity | 1/6 ≈ 0.167 |

Uniform weighting is the principled fallback when no in-domain
calibration corpus is available to establish the relative importance
of sub-signals — Laplace's principle of insufficient reason, the
same methodological default Quamoco adopts for quality-tree
aggregation when expert calibration data is missing. The robustness
of this choice is tested in the sensitivity analysis (see
`PLAN-experiment.md`, Experiment 1), where each sub-signal weight is
perturbed independently and the impact on top-K divergence rankings
reported.

The literature commentary attached to each sub-signal in §1–§6
establishes that each *signal* is empirically motivated — but the
strength of empirical evidence does not directly translate to a
weight magnitude in the composite. Treating "cognitive complexity is
the strongest single predictor" as implying "cognitive complexity
deserves weight 0.25" would be a non-sequitur; the strongest
predictor is still one input among six, and without an in-domain
calibration dataset that establishes how much more it predicts than
the others, uniform weighting is the more defensible choice.

**Step 3 — scale to `[0, 100]` for display.** The unit-interval
composite is multiplied by 100 and rounded to integer for
presentation, producing the cleanliness score surfaced on the
dashboard and consumed by the process layer's `W_GAIN` and
`W_DEGRADATION` terms.

### Implementation

- `analysis/src/main/kotlin/com/github/ethanhosier/analysis/metrics/derived/DerivedMetricsRunner.kt:469–514`
  — `computeCleanliness` implementing the three-step composition.
- `analysis/src/main/kotlin/com/github/ethanhosier/analysis/metrics/derived/DerivedMetricsRunner.kt:432–438`
  — `SubMetric` enum carrying the per-signal `weight` and
  `betterLower` flags. Under the agreed methodology, all six weights
  become `1/6` (effectively `1.0` since the composition normalises by
  `Σ wᵢ`).
- Range computation lives in `computeRanges` (with the trust-signal
  carry-forward filter already applied per PR #55).

### Limitations & future work

Uniform sub-signal weighting is the cleanest fallback in the absence
of calibration data, but it commits the composite to treating
cohesion and cognitive complexity as carrying equal authority despite
substantial differences in empirical evidence base. A marker reading
the composite may reasonably ask whether some signals should weight
more than others; the honest answer is that establishing such weights
requires a calibration corpus (in-domain refactoring sessions paired
with expert quality ratings) that this thesis does not construct.
Future work could derive sub-signal weights empirically — for
example, training a regression of composite-score against expert
maintenance-effort ratings on a corpus of held-out sessions — and
report whether the trained weights diverge materially from uniform.

Session-relative range-normalisation also has a known limitation:
two different sessions are not directly comparable on absolute
cleanliness, because each is normalised to its own observed range.
The thesis frames composite cleanliness as a *within-session
trajectory signal*; cross-session benchmarking would require an
absolute calibration anchor, which is out of scope. The
`cleanlinessGain` and `cleanlinessDegradation` terms in the process
score work natively in the within-session frame, so this is
limitation by design rather than by oversight, but it bounds the
generalisability of any absolute score reported in this work.

Finally, the six chosen sub-signals do not exhaust every dimension
the literature has identified — module-level coupling, type
hierarchy depth, comment density, and test coverage are all relevant
to cleanliness in some studies and absent here. The signal set was
chosen for what the underlying static-analysis tooling reliably
reports across the Java codebases this work targets; expanding it
would require adding sub-signal runners and recalibrating the
composite. Future work could grow the composite incrementally, with
each addition tested by sensitivity analysis to confirm the new
signal carries non-redundant information.

### Citations

```bibtex
@article{bansiya2002qmood,
  author  = {Bansiya, Jagdish and Davis, Carl G.},
  title   = {A Hierarchical Model for Object-Oriented Design Quality Assessment},
  journal = {IEEE Transactions on Software Engineering},
  volume  = {28}, number = {1}, pages = {4--17}, year = {2002},
  doi     = {10.1109/32.979986}
}

@article{wagner2015quamoco,
  author  = {Wagner, Stefan and Goeb, Andreas and Heinemann, Lars and Kl{\"a}s, Michael and Lampasona, Constanza and Lochmann, Klaus and others},
  title   = {Operationalised Product Quality Models and Assessment: The {Q}uamoco Approach},
  journal = {Information and Software Technology},
  volume  = {62}, pages = {101--123}, year = {2015},
  doi     = {10.1016/j.infsof.2015.02.009}
}
```

---

## Sensitivity and validation

The choices made in this document — feature selection, aggregation
methods, uniform weighting in the readability blend and the
composite — are empirically validated by the sensitivity analysis
in `PLAN-experiment.md` (Experiment 1). Each weight and aggregation
choice is perturbed independently across a fixture corpus, and the
impact on top-K divergence-point rankings reported. Choices that
prove robust (small impact on ranking) are vindicated as design
priors; choices that prove brittle are documented as limitations.
The two-layer methodology framing in `RESEARCH-metrics-weighting.md`
and `PLAN-metric-justification.md` situates this Layer-1
documentation within the broader process-score architecture: Layer 1
(this document) defends the cleanliness composite signal-by-signal;
Layer 2 (the process score's W_BROKEN, W_SKIP_TESTS, W_GAIN,
W_DEGRADATION, W_SMELL, W_COMMIT_GAP weights) defends the trajectory-
level penalties via the axiom-constrained priors described there.
