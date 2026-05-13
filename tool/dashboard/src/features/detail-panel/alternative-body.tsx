import { useEffect } from "react"

import { StatusRow } from "@/components/status-row"
import { Text } from "@/components/text"
import type { AlternativeTrajectoryVM, DashboardViewModel } from "@/data/types"
import { DiffSection } from "@/features/detail-panel/diff-section"
import { useDashboardStore } from "@/stores/dashboard-store"
import { cn } from "@/lib/utils"

type Outcome = "better" | "worse" | "neutral" | "failed"

/**
 * Right-panel body for an alternative-trajectory selection. Replaces
 * the per-metric tile grid with an outcome pill + comparison row that
 * answers the question the alt is trying to surface: "what would the
 * user have saved by letting the IDE drive this refactoring?"
 *
 * Steps and churn are derived right here rather than baked into the
 * VM — they're trivially computable from the user's checkpoint window
 * (`fromCheckpointIndex+1 .. toCheckpointIndex`) and changing the
 * panel shouldn't require a server round-trip / VM regen.
 */
export function AlternativeBody({
  vm,
  alt,
}: {
  vm: DashboardViewModel
  alt: AlternativeTrajectoryVM
}) {
  // HYGIENE alts have no "steps saved" / "churn saved" story — the code
  // is identical; only a process decision differs. Render a different
  // body shape (DP-driven copy + status row, no diff section) and
  // short-circuit the rest of the layout.
  if (alt.kind === "HYGIENE") {
    return <HygieneBody vm={vm} alt={alt} />
  }

  // User path: every checkpoint between (from, to] is one manual step.
  const userSteps = Math.max(0, alt.toCheckpointIndex - alt.fromCheckpointIndex)
  const userChurn = vm.checkpoints
    .slice(alt.fromCheckpointIndex + 1, alt.toCheckpointIndex + 1)
    .reduce((sum, cp) => sum + cp.churn, 0)
  // Alt step count depends on the divergence kind:
  //  - IDE_REPLAY: one IDE action regardless of how many user steps it
  //    replaces — the comparison is "one automated invocation vs N
  //    manual edits". Hardcoded 1.
  //  - REWORK: the surviving alt checkpoints after whitespace-only
  //    intermediates are absorbed — `alt.steps.length` already reflects
  //    that filter.
  //  - ORDERING: alt has the same step count as the user (different
  //    order), so the steps-saved comparison naturally evaluates to 0.
  const altSteps = alt.kind === "IDE_REPLAY" ? 1 : alt.steps.length
  const altChurn = alt.altChurn

  const stepsSaved = userSteps - altSteps
  const churnSaved = userChurn - altChurn

  // Outcome is pinned to "better" — the alt is only synthesised when
  // the IDE-driven path is the recommended swap, and the panel exists
  // to make that recommendation tangible. Worse / failed branches are
  // kept in the type so we don't have to retouch styles if we later
  // start surfacing them.
  const outcome: Outcome = "better"

  return (
    <div className="flex flex-col gap-4">
      <section className="flex flex-col gap-2">
        <Text as="h3" variant="eyebrow" tone="fg-4">
          Outcome
        </Text>
        <OutcomePill outcome={outcome} />
      </section>

      <section className="flex flex-col gap-2">
        <Text as="h3" variant="eyebrow" tone="fg-4">
          Comparison
        </Text>
        <Text variant="body" tone="fg-2" className="text-[12px] leading-relaxed">
          {alt.kind === "REWORK" ? (
            <>
              Skipping this rework round-trip would have avoided{" "}
              <SavingsValue n={stepsSaved} unit={stepsSaved === 1 ? "step" : "steps"} />{" "}
              and{" "}
              <SavingsValue n={churnSaved} unit="LOC" /> of wasted churn.
            </>
          ) : alt.kind === "ORDERING" ? (
            <>
              Reordering these steps would have completed the same window in{" "}
              <SavingsValue n={stepsSaved} unit={stepsSaved === 1 ? "step" : "steps"} />{" "}
              and{" "}
              <SavingsValue n={churnSaved} unit="LOC" /> of churn.
            </>
          ) : (
            <>
              Using the automated <strong>{alt.label}</strong> would have completed
              this refactoring in{" "}
              <SavingsValue n={stepsSaved} unit={stepsSaved === 1 ? "step" : "steps"} />{" "}
              and{" "}
              <SavingsValue n={churnSaved} unit="LOC" /> of churn.
            </>
          )}
        </Text>
        <div className="grid grid-cols-[1fr_auto_auto] gap-x-3 gap-y-1 pt-1">
          <Text variant="bodySm" tone="fg-4">
            &nbsp;
          </Text>
          <Text variant="monoTiny" tone="fg-4" className="text-right">
            User
          </Text>
          <Text variant="monoTiny" tone="fg-4" className="text-right">
            {alt.kind === "REWORK"
              ? "No rework"
              : alt.kind === "ORDERING"
                ? "Reordered"
                : "Automated"}
          </Text>
          <Text variant="bodySm" tone="fg-2">
            Steps
          </Text>
          <Text variant="mono" tone="fg" className="text-right">
            {userSteps}
          </Text>
          <Text variant="mono" tone="fg" className="text-right">
            {altSteps}
          </Text>
          <Text variant="bodySm" tone="fg-2">
            Churn (LOC)
          </Text>
          <Text variant="mono" tone="fg" className="text-right">
            {userChurn}
          </Text>
          <Text variant="mono" tone="fg" className="text-right">
            {altChurn}
          </Text>
        </div>
      </section>

      <section className="flex flex-col gap-2">
        <Text as="h3" variant="eyebrow" tone="fg-4">
          Status at alt commit ({alt.shortAltSha})
        </Text>
        <StatusRow build={alt.build} tests={alt.tests} />
      </section>

      {alt.kind === "REWORK" ? (
        <ReworkRoundTripDiffs vm={vm} alt={alt} />
      ) : (
        <DiffSection
          title="Diff"
          patch={alt.patch}
          cacheKey={`alternative-${alt.index}`}
          emptyMessage="No diff captured for this alternative."
        />
      )}
    </div>
  )
}

/**
 * HYGIENE body — TESTS_SKIPPED or COMMIT_GAP. No diff (code is the same
 * between user and alt), no steps/churn comparison. Pulls copy from the
 * owning DP so the sidebar reads consistently with the indicator card.
 */
function HygieneBody({
  vm,
  alt,
}: {
  vm: DashboardViewModel
  alt: AlternativeTrajectoryVM
}) {
  const dp = vm.divergencePoints.find((d) => d.altIndexes.includes(alt.index))
  const anchorCp = vm.checkpoints[alt.toCheckpointIndex]
  const userProcess = anchorCp?.values.process
  const altProcess = alt.altValues.process

  const subKind = dp?.hygieneSubKind
  const heading =
    subKind === "TESTS_SKIPPED"
      ? "If you'd run the tests"
      : subKind === "COMMIT_GAP"
        ? "If you'd committed here"
        : "Alternative process decision"
  return (
    <div className="flex flex-col gap-4">
      <section className="flex flex-col gap-2">
        <Text as="h3" variant="eyebrow" tone="fg-4">
          {heading}
        </Text>
        <Text variant="body" tone="fg-2" className="text-[12px] leading-relaxed">
          {dp?.explanation ??
            "An alternative process decision at this checkpoint would have given a cleaner trajectory."}
        </Text>
      </section>

      <section className="flex flex-col gap-2">
        <Text as="h3" variant="eyebrow" tone="fg-4">
          Process score at this checkpoint
        </Text>
        <div className="grid grid-cols-[1fr_auto_auto] gap-x-3 gap-y-1 pt-1">
          <Text variant="bodySm" tone="fg-4">&nbsp;</Text>
          <Text variant="monoTiny" tone="fg-4" className="text-right">
            User
          </Text>
          <Text variant="monoTiny" tone="fg-4" className="text-right">
            Alt
          </Text>
          <Text variant="bodySm" tone="fg-2">
            Process
          </Text>
          <Text variant="mono" tone="fg" className="text-right">
            {typeof userProcess === "number" ? userProcess.toFixed(0) : "—"}
          </Text>
          <Text variant="mono" tone="fg" className="text-right">
            {typeof altProcess === "number" ? altProcess.toFixed(0) : "—"}
          </Text>
        </div>
      </section>
    </div>
  )
}

/**
 * REWORK-specific diff pair: the user's transition diff entering the
 * originating step (where the round-tripped code was added) and the
 * transition diff entering the terminal step (where it was removed).
 * Each section's header is a clickable button — clicking it selects
 * the corresponding user checkpoint on the chart so the graph point
 * lights up alongside.
 */
function ReworkRoundTripDiffs({
  vm,
  alt,
}: {
  vm: DashboardViewModel
  alt: AlternativeTrajectoryVM
}) {
  const setHighlightedCheckpointIndex = useDashboardStore(
    (s) => s.setHighlightedCheckpointIndex,
  )
  // Clear the chart-side halo when this body unmounts (the user
  // navigated away from the alt panel) so it doesn't linger on
  // unrelated subsequent views.
  useEffect(() => {
    return () => setHighlightedCheckpointIndex(null)
  }, [setHighlightedCheckpointIndex])
  // The DP owns the focused unified-diff patches — backend-built,
  // already narrowed to just the matched rework chunks. Match by
  // altIndexes containing this alt's synthetic index.
  const dp = vm.divergencePoints.find((d) => d.altIndexes.includes(alt.index))
  // The alt's `fromCheckpointIndex` is the originating step's *parent*
  // checkpoint; the originating edit *lands* at the next checkpoint
  // (the step's resulting tree). Reference the resulting checkpoint
  // when describing where code was added/removed so it matches the
  // checkpoint where the indicator was clicked.
  const originatingCp = vm.checkpoints[alt.fromCheckpointIndex + 1]
  const terminalCp = vm.checkpoints[alt.toCheckpointIndex]
  // Direction governs which side of the round trip is the add vs the
  // remove. Default to ADD_THEN_REMOVE if the DP doesn't carry the
  // field (older reports pre-plumbing).
  const direction = dp?.reworkDirection ?? "ADD_THEN_REMOVE"
  const originatingVerb = direction === "ADD_THEN_REMOVE" ? "added" : "removed"
  const terminalVerb = direction === "ADD_THEN_REMOVE" ? "removed" : "added"

  return (
    <div className="flex flex-col gap-3">
      {originatingCp ? (
        <div className="flex flex-col gap-1">
          <Text variant="body" tone="fg-2" className="text-[11px] leading-tight">
            Code {originatingVerb} at{" "}
            <CheckpointHighlightLink
              label={`${originatingCp.label} (${originatingCp.shortSha})`}
              checkpointIndex={originatingCp.index}
              onHighlight={setHighlightedCheckpointIndex}
            />
          </Text>
          <DiffSection
            title="Originating diff"
            patch={dp?.originatingPatch ?? ""}
            cacheKey={`rework-from-${alt.index}`}
            emptyMessage="No diff captured at the originating step."
          />
        </div>
      ) : null}
      {terminalCp ? (
        <div className="flex flex-col gap-1">
          <Text variant="body" tone="fg-2" className="text-[11px] leading-tight">
            Code {terminalVerb} at{" "}
            <CheckpointHighlightLink
              label={`${terminalCp.label} (${terminalCp.shortSha})`}
              checkpointIndex={terminalCp.index}
              onHighlight={setHighlightedCheckpointIndex}
            />
          </Text>
          <DiffSection
            title="Terminal diff"
            patch={dp?.terminalPatch ?? ""}
            cacheKey={`rework-to-${alt.index}`}
            emptyMessage="No diff captured at the terminal step."
          />
        </div>
      ) : null}
    </div>
  )
}

/**
 * Inline anchor-style button that flags a checkpoint for visual
 * emphasis on the chart without opening the detail panel for it. Clear
 * underline + accent colour signals affordance; toggles off on a
 * second click.
 */
function CheckpointHighlightLink({
  label,
  checkpointIndex,
  onHighlight,
}: {
  label: string
  checkpointIndex: number
  onHighlight: (idx: number | null) => void
}) {
  const highlightedCheckpointIndex = useDashboardStore(
    (s) => s.highlightedCheckpointIndex,
  )
  const active = highlightedCheckpointIndex === checkpointIndex
  return (
    <button
      type="button"
      onClick={() => onHighlight(active ? null : checkpointIndex)}
      className={cn(
        "text-brand-2 hover:text-brand cursor-pointer font-mono underline decoration-dotted underline-offset-2",
        active && "text-brand decoration-solid",
      )}
    >
      {label}
    </button>
  )
}

const OUTCOME_LABEL: Record<Outcome, string> = {
  better: "Better Alternative",
  worse: "Worse Alternative",
  neutral: "Neutral Alternative",
  failed: "Failed Alternative",
}

const OUTCOME_CLASS: Record<Outcome, string> = {
  // Brand mint = the same hue we use for the alt path on the chart.
  better: "border-brand text-brand",
  worse: "border-warn text-warn",
  failed: "border-bad text-bad",
  neutral: "border-fg-4 text-fg-3",
}

/**
 * Reference-style pill: bg-2 fill, 1px coloured border, dot + caps
 * label. Matches the geometry of the `Subhead → KIND` chip in
 * reference/panels.jsx so the alt panel reads as part of the same
 * visual family.
 */
function OutcomePill({ outcome }: { outcome: Outcome }) {
  return (
    <div
      className={cn(
        "bg-bg-2 inline-flex items-center gap-2 self-start rounded-full border px-2.5 py-1 tracking-[0.02em]",
        OUTCOME_CLASS[outcome],
      )}
    >
      <span
        className={cn("inline-block size-1.5 rounded-full bg-current")}
      />
      <Text as="span" variant="bodySm" tone="inherit">
        {OUTCOME_LABEL[outcome]}
      </Text>
    </div>
  )
}

function SavingsValue({ n, unit }: { n: number; unit: string }) {
  if (n > 0) {
    return (
      <span className="text-good font-mono">
        {n} fewer {unit}
      </span>
    )
  }
  if (n < 0) {
    return (
      <span className="text-bad font-mono">
        {Math.abs(n)} more {unit}
      </span>
    )
  }
  return <span className="text-fg-3 font-mono">the same {unit}</span>
}
