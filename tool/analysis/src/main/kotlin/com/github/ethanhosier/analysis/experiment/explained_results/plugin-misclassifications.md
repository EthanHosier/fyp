# Known plugin misclassifications — CLOSED (historical reference)

> **Status (2026-05-20):** The misclassifications documented below
> were closed in PR #62 (merge commit `8daecbf`) by extending
> `RefactoringCommandListener` to override `commandStarted` and
> synthesise a refactoring envelope around IntelliJ commands the
> platform `RefactoringEventListener` is silent for (Move Method,
> Change Method Signature) or fires under a refactoringId the
> analyser's name-matching does not bridge to RefactoringMiner's
> vocabulary (Extract/Introduce/Inline operations). Sessions 025,
> 032, 037, 039 have been re-recorded against the patched plugin
> and now produce zero IDE_REPLAY false positives. Corpus-wide
> IDE_REPLAY precision rises from 0.80 to 1.00. The original
> per-session diagnostics below are kept as a historical record
> of the bug surface and the root-cause analysis.

Sessions where the original recorded `refactoringSteps` list
contained steps with `wasPerformedByIde = false` even though the
user *did* perform the operation via an IntelliJ refactoring.
These were **plugin-side detection bugs**, not real manual edits —
the "perfect detector" ground truth labels in `manifest-v2.csv` do
**not** include `IDE_REPLAY` for them.

Recorded so the misclassifications aren't mistaken for genuine
manual-edit ground truth during future label audits or chapter
writeup.

## Sessions affected (original diagnostics, pre-PR-#62)

### 025 — SuboptimalOrdering (borderline)

**Expected (label):** `ORDERING`
**Recorded steps:** 6 — Rename Variable (IDE) + **Remove Parameter
(manual)** + **Move Method (manual)** + **Rename Method (manual)** +
**Rename Method (manual)** + Rename Method (IDE).

Four steps recorded as manual. **Cause:** how the plugin captures
IntelliJ's `Move Instance Method` refactoring template events for this
specific template path — the template's post-confirm document edits
land outside the plugin's `REFACTORING_STARTED`/`REFACTORING_FINISHED`
envelope, so the resulting renames-of-call-sites and parameter
adjustments register as EDIT_BURSTs. Same root cause as the in-place
rename template artefacts noted in earlier session-001 / session-006
diagnostics.

### 032 — SkippedTests (loud)

**Expected (label):** `HYGIENE;ORDERING`
**Recorded steps:** 5 — 3 IDE + **Remove Parameter (manual)** +
**Move Method (manual)**.

Same plugin-capture issue as 025: the user performed both Remove
Parameter and Move Method via IntelliJ Refactor menu, but the
plugin's event envelope didn't wrap them.

### 037 — NoCommitStretch (loud)

**Expected (label):** `REWORK;HYGIENE;ORDERING`
**Recorded steps:** 7 — 6 IDE + **Extract Variable (manual)**.

**Cause:** **a deliberate manual edit got mixed in with the IDE
refactor's batch** during recording. The single Extract Variable that
shows as manual was a true manual edit blended into an otherwise
IDE-driven session — i.e. the recording captured the truth, but the
manual edit wasn't part of the intended NoCommitStretch pattern, just
incidental noise from the recording session.

We don't add `IDE_REPLAY` to expected_kinds because adding it would
penalise the detector for *not* surfacing an IDE replay alt of an
incidental, intent-unrelated edit. The label encodes "what a perfect
detector should flag *given the intended scenario*."

### 039 — NoCommitStretch (borderline)

**Expected (label):** `REWORK;HYGIENE;ORDERING`
**Recorded steps:** 6 — 5 IDE + **Extract Variable (manual)**.

**Cause:** how the plugin detects `Extract Variable` events when
IntelliJ uses an *inline template* (the "introduce variable" preview
overlay). The template's live-preview edits hit the document before
the refactoring envelope fires; the plugin records them as
EDIT_BURSTs and the final committed refactor is misclassified as
manual. Same family of in-place-template bugs called out in
session-006 diagnostics.

## Implications for results

- **Tier 0 (detection)** false positives on `wasPerformedByIde =
  false` in these four sessions are plugin bugs, not detector merit.
  When reporting Tier-0 capture-rate numbers in the chapter, either
  exclude these or footnote them.
- **Tier 1 (synthesis)** IDE_REPLAY alts get synthesised for these
  miscaptured steps in the current pipeline. These show up as **FPs**
  for IDE_REPLAY against the corrected labels — and they *are*
  legitimate FPs (the detector synthesised an IDE replay alt for a
  step that was actually IDE-performed, which is a false claim). But
  the root cause is upstream (plugin classification), not the
  synthesiser itself.
- **Headline framing for the chapter:** call this out as a
  plugin-side capture issue rather than a detector-side false-positive
  issue. Two distinct failure modes that current results conflate.

## Fix paths (deferred)

1. **In-place rename / introduce-variable template detection.** Hook
   `TemplateManagerListener` (or `InplaceRefactoring`'s start/finish
   callbacks) in the plugin to suppress `EDIT_BURST` flushes while a
   refactoring template is active.
2. **`MoveInstanceMethod` event envelope.** Subscribe to
   `RefactoringElementListenerProvider` directly so Move emits a
   proper `REFACTORING_STARTED` / `REFACTORING_FINISHED` pair around
   the call-site rewrites.
3. **Reclassify post-hoc.** Run a sanity-check pass that looks at the
   `refactoringPatches` and re-flags steps whose patch shape matches
   a known IDE refactoring template even if `wasPerformedByIde` is
   false. Cheaper than fixing the plugin but more brittle.
