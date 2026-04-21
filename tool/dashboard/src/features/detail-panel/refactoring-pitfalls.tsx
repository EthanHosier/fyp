import { XIcon, ZapIcon } from "lucide-react"

import { PitfallCallout } from "@/components/pitfall-callout"
import { Text } from "@/components/text"
import type { DashboardViewModel, RefactoringStepVM } from "@/data/types"

/**
 * Stacks any pitfall callouts that apply to a refactoring step. Renders
 * nothing when both flags are clean — mirrors the chart glyphs so the
 * user sees the same signals in both places.
 *
 * Tests-not-run description is sharpened when the refactoring moved the
 * project from green → red (build or tests): the user would have caught
 * the regression at its source if they'd run tests after this step.
 */
export function RefactoringPitfalls({
  vm,
  step,
}: {
  vm: DashboardViewModel
  step: RefactoringStepVM
}) {
  const showTests = !step.userRanTests
  const showIde = step.ideRelevant && !step.wasPerformedByIde
  if (!showTests && !showIde) return null

  const to = vm.checkpoints[step.checkpointIndex]
  const from = vm.checkpoints.find((c) => c.sha === step.fromSha)
  const regressed =
    !!from &&
    !!to &&
    ((from.build === "pass" && to.build === "fail") ||
      (from.tests === "pass" && to.tests === "fail"))
  const testsDescription = regressed
    ? "This refactoring moved the project from a passing state to a failing one. Running tests immediately after the change would have caught the regression at its source, instead of surfacing later when the cause is harder to isolate."
    : "No run of the test suite was recorded after this refactoring. Since refactorings should preserve behaviour, tests should ideally be run after each small step to catch regressions while the cause is still easy to isolate."

  return (
    <section className="flex flex-col gap-2">
      <Text as="h3" variant="eyebrow" tone="fg-4">
        Process Signals
      </Text>
      {showTests ? (
        <PitfallCallout
          tone="bad"
          icon={<XIcon className="size-2.5" strokeWidth={2.5} />}
          title="You didn't run tests after this refactoring"
          description={testsDescription}
        />
      ) : null}
      {showIde ? (
        <PitfallCallout
          tone="warn"
          icon={<ZapIcon className="size-2.5 fill-warn" strokeWidth={0} />}
          title="Manual refactor - IDE could have done this"
          mono={step.refactoringType}
          description={`You manually performed ${step.refactoringType}. Since the IDE supports this refactoring directly, you should have used the built-in action which would apply the change more consistently and reduce the risk of missed updates.`}
        />
      ) : null}
    </section>
  )
}
