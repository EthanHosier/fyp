import { DataList, DataListRow } from "@/components/data-list"
import { Text } from "@/components/text"
import type {
  DashboardViewModel,
  RefactoringStepVM,
} from "@/data/types"

/**
 * Details for a detected refactoring step — the RefactoringMiner
 * description on top, then metric deltas between the tight window's
 * `fromSha` and `toSha` (reconstructed via the underlying checkpoints).
 */
export function RefactoringBody({
  vm,
  step,
}: {
  vm: DashboardViewModel
  step: RefactoringStepVM
}) {
  const to = vm.checkpoints[step.checkpointIndex]
  // fromSha may not correspond to a checkpoint in the VM if it was the
  // session's initial state — fall back to the checkpoint immediately
  // before `toCheckpointIndex` in that case.
  const from =
    vm.checkpoints.find((c) => c.sha === step.fromSha) ??
    vm.checkpoints[Math.max(0, step.checkpointIndex - 1)]

  return (
    <div className="flex flex-col gap-4">
      <section className="flex flex-col gap-2">
        <Text as="h3" variant="eyebrow" tone="fg-4">
          Description
        </Text>
        <Text variant="body" tone="fg-2" className="text-[12px]">
          {step.description}
        </Text>
      </section>

      <section className="flex flex-col gap-2">
        <Text as="h3" variant="eyebrow" tone="fg-4">
          Window
        </Text>
        <KeyValue k="From" v={step.shortFromSha} />
        <KeyValue k="To" v={step.shortToSha} />
        <KeyValue k="Time" v={step.tLabel} />
        {step.ideRelevant ? (
          <KeyValue k="Source" v="IDE-relevant" />
        ) : null}
      </section>

      {from && to ? (
        <section className="flex flex-col gap-2">
          <Text as="h3" variant="eyebrow" tone="fg-4">
            Deltas
          </Text>
          <DataList>
            {vm.metrics.map((m) => {
              const a = from.values[m.id]
              const b = to.values[m.id]
              if (typeof a !== "number" || typeof b !== "number") return null
              const diff = b - a
              const decimals = m.unit === "%" ? 1 : 0
              const tone =
                diff === 0
                  ? "fg-3"
                  : (m.better === "lower" ? diff < 0 : diff > 0)
                    ? "good"
                    : "bad"
              return (
                <DataListRow
                  key={m.id}
                  className="grid grid-cols-[1fr_auto_auto_auto] items-center gap-2.5"
                >
                  <Text variant="body" tone="fg-2" className="text-[12px]">
                    {m.label}
                  </Text>
                  <Text variant="mono" tone="fg-4">
                    {a.toFixed(decimals)}
                  </Text>
                  <Text tone="fg-4">→</Text>
                  <Text variant="mono" tone={tone} className="w-14 text-right">
                    {b.toFixed(decimals)}{" "}
                    <span className="opacity-65">
                      ({diff > 0 ? "+" : ""}
                      {diff.toFixed(decimals)})
                    </span>
                  </Text>
                </DataListRow>
              )
            })}
          </DataList>
        </section>
      ) : null}
    </div>
  )
}

function KeyValue({ k, v }: { k: string; v: React.ReactNode }) {
  return (
    <div className="grid grid-cols-[100px_1fr] items-center gap-2.5 py-[3px]">
      <Text variant="body" tone="fg-3" className="text-[12px]">
        {k}
      </Text>
      <Text as="div" variant="mono" tone="fg-2" className="text-[12px]">
        {v}
      </Text>
    </div>
  )
}
