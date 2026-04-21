import { XIcon, ZapIcon } from "lucide-react"

import { PitfallCallout } from "@/components/pitfall-callout"
import type { RefactoringStepVM } from "@/data/types"

/**
 * Stacks any pitfall callouts that apply to a refactoring step. Renders
 * nothing when both flags are clean — mirrors the chart glyphs so the
 * user sees the same signals in both places.
 */
export function RefactoringPitfalls({ step }: { step: RefactoringStepVM }) {
  const showTests = !step.userRanTests
  const showIde = step.ideRelevant && !step.wasPerformedByIde
  if (!showTests && !showIde) return null

  return (
    <div className="flex flex-col gap-2">
      {showTests ? (
        <PitfallCallout
          tone="bad"
          icon={<XIcon className="size-2.5" strokeWidth={2.5} />}
          title="Tests skipped"
          description="No test run was recorded after this refactoring. Since refactorings should preserve behaviour, tests should ideally be run after each small step to catch regressions while the cause is still easy to isolate."
        />
      ) : null}
      {showIde ? (
        <PitfallCallout
          tone="warn"
          icon={<ZapIcon className="size-2.5 fill-warn" strokeWidth={0} />}
          title="Manual refactor - IDE could have done this"
          mono={step.refactoringType}
          description={`This step appears to be a manual ${step.refactoringType}. Since the IDE supports this refactoring directly, using the built-in action would apply the change more consistently and reduce the risk of missed updates.`}
        />
      ) : null}
    </div>
  )
}
