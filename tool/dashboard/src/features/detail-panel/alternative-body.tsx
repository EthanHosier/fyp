import { StatusRow } from "@/components/status-row"
import { Text } from "@/components/text"
import type { AlternativeTrajectoryVM, DashboardViewModel } from "@/data/types"
import { DiffSection } from "@/features/detail-panel/diff-section"
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
  // User path: every checkpoint between (from, to] is one manual step.
  const userSteps = Math.max(0, alt.toCheckpointIndex - alt.fromCheckpointIndex)
  const userChurn = vm.checkpoints
    .slice(alt.fromCheckpointIndex + 1, alt.toCheckpointIndex + 1)
    .reduce((sum, cp) => sum + cp.churn, 0)
  // The alt is one IDE action regardless of how many user steps it replaces.
  const altSteps = 1
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
          Using the automated <strong>{alt.label}</strong> would have completed
          this refactoring in{" "}
          <SavingsValue n={stepsSaved} unit={stepsSaved === 1 ? "step" : "steps"} />{" "}
          and{" "}
          <SavingsValue n={churnSaved} unit="LOC" /> of churn.
        </Text>
        <div className="grid grid-cols-[1fr_auto_auto] gap-x-3 gap-y-1 pt-1">
          <Text variant="bodySm" tone="fg-4">
            &nbsp;
          </Text>
          <Text variant="monoTiny" tone="fg-4" className="text-right">
            User
          </Text>
          <Text variant="monoTiny" tone="fg-4" className="text-right">
            Automated
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

      <DiffSection
        title="Diff"
        patch={alt.patch}
        cacheKey={`alternative-${alt.index}`}
        emptyMessage="No diff captured for this alternative."
      />
    </div>
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
