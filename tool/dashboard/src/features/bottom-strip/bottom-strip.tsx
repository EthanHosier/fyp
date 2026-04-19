import { Lightbulb } from "lucide-react"

import { RailSection } from "@/components/rail-section"
import { Text } from "@/components/text"
import type { DashboardViewModel } from "@/data/types"
import { CheckpointFilmstrip } from "@/features/bottom-strip/checkpoint-filmstrip"

/**
 * Bottom region: filmstrip of checkpoints on the left, "why it matters"
 * explanation card on the right. Explanation card stays a placeholder
 * until step 14 (delta-bullet summary driven by selection).
 */
export function BottomStrip({ vm }: { vm: DashboardViewModel }) {
  return (
    <div className="border-border bg-bg-1 grid shrink-0 grid-cols-[1fr_420px] border-t">
      <div className="border-border overflow-hidden border-r">
        <CheckpointFilmstrip checkpoints={vm.checkpoints} />
      </div>
      <ExplanationPlaceholder />
    </div>
  )
}

function ExplanationPlaceholder() {
  return (
    <div className="overflow-auto">
      <RailSection
        title="Why it matters"
        icon={<Lightbulb className="size-3 text-brand" />}
      >
        <Text as="p" variant="bodySm" tone="fg-3" className="px-3">
          Click a{" "}
          <Text as="strong" variant="bodySm" tone="fg-2" className="font-semibold">
            checkpoint
          </Text>
          , a{" "}
          <Text as="strong" variant="bodySm" tone="fg-2" className="font-semibold">
            build interval
          </Text>
          , or an{" "}
          <Text as="strong" variant="bodySm" tone="fg-2" className="font-semibold">
            annotation
          </Text>{" "}
          to see a narrated explanation here.
        </Text>
      </RailSection>
    </div>
  )
}
