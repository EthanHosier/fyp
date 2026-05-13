import { Text } from "@/components/text"
import type { DashboardViewModel, DivergencePointVM } from "@/data/types"
import { useDashboardStore } from "@/stores/dashboard-store"

/**
 * Detail-panel body rendered when the active selection is a
 * divergence-point marker. Headlines the kind-specific title and
 * explanation, surfaces the per-kind extras (file/scope/lineCount for
 * REWORK, window steps for ORDERING, replacedRefactoringId for
 * IDE_REPLAY), and chevrons through the owned alts — clicking an alt
 * row transitions to the existing alt-detail card via the normal
 * `alternative` selection.
 */
export function DivergenceBody({
  vm,
  dp,
}: {
  vm: DashboardViewModel
  dp: DivergencePointVM
}) {
  const setSelection = useDashboardStore((s) => s.setSelection)

  return (
    <div className="flex flex-col gap-4">
      <section>
        <Text variant="body" tone="fg-2">
          {dp.explanation}
        </Text>
      </section>

      <DetailRow label="Magnitude" value={`${formatMagnitude(dp)}`} />
      {dp.kind === "REWORK" ? (
        <>
          <DetailRow label="File" value={dp.file ?? "—"} mono />
          <DetailRow label="Scope" value={dp.scopeLabel ?? "—"} mono />
          <DetailRow
            label="Originating step"
            value={dp.originatingStepIndex?.toString() ?? "—"}
          />
        </>
      ) : null}
      {dp.kind === "ORDERING" && dp.orderingWindowSteps ? (
        <DetailRow
          label="Window steps"
          value={dp.orderingWindowSteps.join(", ")}
          mono
        />
      ) : null}
      {dp.kind === "IDE_REPLAY" && dp.replacedRefactoringId ? (
        <DetailRow label="Refactoring" value={dp.replacedRefactoringId} mono />
      ) : null}

      <section className="flex flex-col gap-2">
        <Text variant="eyebrow" tone="fg-4">
          Alternatives ({dp.altIndexes.length})
        </Text>
        <div className="flex flex-col gap-1">
          {dp.altIndexes.map((i) => {
            const alt = vm.alternativeTrajectories[i]
            if (!alt) return null
            const altFinal =
              alt.continuationSteps.length > 0
                ? alt.continuationSteps[alt.continuationSteps.length - 1]
                    .processScore
                : alt.altValues.process
            return (
              <button
                key={alt.index}
                onClick={() =>
                  setSelection({ kind: "alternative", index: alt.index })
                }
                className="border-border bg-bg-2 hover:bg-bg-3 flex items-center justify-between rounded-md border px-3 py-2 text-left transition-colors"
              >
                <span className="flex flex-col">
                  <Text variant="body" tone="fg">
                    {alt.label}
                  </Text>
                  <Text variant="mono" tone="fg-4">
                    {alt.shortAltSha}
                  </Text>
                </span>
                <Text variant="mono" tone="fg-3">
                  process {altFinal ?? "—"}
                </Text>
              </button>
            )
          })}
        </div>
      </section>
    </div>
  )
}

function DetailRow({
  label,
  value,
  mono,
}: {
  label: string
  value: string
  mono?: boolean
}) {
  return (
    <div className="flex items-baseline justify-between gap-3">
      <Text variant="eyebrow" tone="fg-4">
        {label}
      </Text>
      <Text variant={mono ? "mono" : "body"} tone="fg-2">
        {value}
      </Text>
    </div>
  )
}

function formatMagnitude(dp: DivergencePointVM): string {
  if (dp.kind === "REWORK") {
    return `${dp.reworkLineCount ?? dp.magnitude} lines`
  }
  return `+${dp.magnitude.toFixed(1)} process points`
}
