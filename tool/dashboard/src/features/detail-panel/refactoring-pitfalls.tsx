import { XIcon, ZapIcon } from "lucide-react"

import { PitfallCallout } from "@/components/pitfall-callout"
import type { DashboardViewModel, RefactoringStepVM } from "@/data/types"

/**
 * Stacks any pitfall callouts that apply to a refactoring step. Returns
 * a fragment of callouts (no outer section) — `CheckpointBody` owns the
 * single "Process Signals" header so refactoring + checkpoint signals
 * render under one heading.
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
    <>
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
          icon={<ZapIcon className="size-4 fill-warn" strokeWidth={0} />}
          title="Manual refactor - IDE could have done this"
          mono={step.refactoringType}
          description={`You manually performed ${step.refactoringType}. Since the IDE supports this refactoring directly, you should have used the built-in action which would apply the change more consistently and reduce the risk of missed updates.`}
        />
      ) : null}
    </>
  )
}

/** Mirror of `RefactoringPitfalls`'s render condition, exposed so the
 *  parent panel can decide whether to include it in the unified
 *  "Process Signals" section without rendering an empty header. */
export function hasRefactoringPitfalls(step: RefactoringStepVM): boolean {
  const showTests = !step.userRanTests
  const showIde = step.ideRelevant && !step.wasPerformedByIde
  return showTests || showIde
}
