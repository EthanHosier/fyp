import type { DashboardViewModel, MetricVM } from "@/data/types"
import type { ChartScales } from "@/features/trajectory-chart/use-chart-scales"

/**
 * Non-interactive overlay — one glyph cluster per refactoring step with
 * a pitfall flag. Glyphs sit above the dot (connected by a faint tick)
 * so the user sees at a glance which refactorings went untested or could
 * have used an IDE action.
 *
 *  - Tests-not-run (red ✕ in dashed circle): no TEST_RUN_FINISHED event
 *    on the step's toSha checkpoint.
 *  - IDE-able (amber lightning bolt): the RM detection corresponds to a
 *    built-in IntelliJ refactoring, but the user did it by hand (no
 *    REFACTORING_FINISHED event on the checkpoint).
 *
 * If only one flag fires on a step, the glyph is centred above the dot.
 * If both fire, tests goes left, IDE-able right — same layout as the
 * reference prototype.
 */
export function ChartRefactoringGlyphs({
  vm,
  primary,
  scales,
}: {
  vm: DashboardViewModel
  primary: MetricVM
  scales: ChartScales
}) {
  const { xs, ys } = scales

  return (
    <g className="pointer-events-none">
      {vm.refactoringSteps.map((s) => {
        const c = vm.checkpoints[s.checkpointIndex]
        if (!c) return null
        const v = c.values[primary.id]
        if (typeof v !== "number") return null

        const showTests = !s.userRanTests
        const showIde = s.ideRelevant && !s.wasPerformedByIde
        if (!showTests && !showIde) return null

        const cx = xs(s.tMs)
        const cy = ys(v)
        const gy = cy - 14
        const both = showTests && showIde
        const testsDx = both ? -8 : 0
        const ideDx = both ? 8 : 0

        return (
          <g key={s.index}>
            <line
              x1={cx}
              x2={cx}
              y1={gy + 5}
              y2={cy - 8}
              className="stroke-border-strong"
              strokeOpacity={0.55}
              strokeWidth={0.8}
            />
            {showTests ? <TestsSkippedGlyph x={cx + testsDx} y={gy} /> : null}
            {showIde ? (
              <IdeAbleGlyph
                x={cx + ideDx}
                y={gy}
                refactoringType={s.refactoringType}
              />
            ) : null}
          </g>
        )
      })}
    </g>
  )
}

function TestsSkippedGlyph({ x, y }: { x: number; y: number }) {
  return (
    <g transform={`translate(${x},${y})`}>
      <title>No tests were run after this refactoring.</title>
      <circle
        r={4.6}
        className="fill-bad/15 stroke-bad"
        strokeWidth={1}
        strokeDasharray="1.6 1.4"
      />
      <path
        d="M-2 -2 L2 2 M2 -2 L-2 2"
        className="stroke-bad"
        strokeWidth={1.1}
        strokeLinecap="round"
        fill="none"
      />
    </g>
  )
}

function IdeAbleGlyph({
  x,
  y,
  refactoringType,
}: {
  x: number
  y: number
  refactoringType: string
}) {
  return (
    <g transform={`translate(${x},${y})`}>
      <title>{`Manual refactor — IDE could have done this: ${refactoringType}.`}</title>
      <circle r={4.6} className="fill-warn/20 stroke-warn" strokeWidth={1} />
      <path
        d="M0.4 -2.6 L-1.6 0.3 L-0.1 0.3 L-0.6 2.6 L1.6 -0.4 L0.1 -0.4 Z"
        className="fill-warn"
      />
    </g>
  )
}
