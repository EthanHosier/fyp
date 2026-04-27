/**
 * Pure-SVG process-signal chip in the bad/warn/good visual family used
 * by `PitfallCallout` and the trajectory chart's signal glyphs:
 *  - `bad`  — circular chip, tinted background, X strokes.
 *  - `warn` — rounded triangle, tinted fill, centred `!`.
 *  - `good` — circular chip, tinted background, check strokes.
 *
 * Self-contained `<svg>` so it slots into both DOM contexts (children of
 * a `<div>`) and SVG contexts (nested inside another `<svg>`, e.g. the
 * chart overlay) without any per-call wrapper plumbing. Ink follows the
 * tone token via inline `style.color` + `currentColor` paint, so a
 * caller wrapping in a coloured ancestor doesn't override it.
 */

type Tone = "bad" | "warn" | "good"

const PAINT: Record<Tone, string> = {
  bad: "var(--color-bad)",
  warn: "var(--color-warn)",
  good: "var(--color-good)",
}

export function ToneChip({
  tone,
  size = 18,
  className,
}: {
  tone: Tone
  size?: number
  className?: string
}) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 18 18"
      style={{ color: PAINT[tone] }}
      className={className}
      aria-hidden
    >
      {tone === "warn" ? <WarnTriangle /> : <CircleChip tone={tone} />}
    </svg>
  )
}

function CircleChip({ tone }: { tone: "bad" | "good" }) {
  return (
    <>
      <circle
        cx={9}
        cy={9}
        r={8.5}
        fill="currentColor"
        fillOpacity={0.2}
        stroke="currentColor"
        strokeWidth={1}
      />
      {tone === "bad" ? (
        <path
          d="M6 6 L12 12 M12 6 L6 12"
          stroke="currentColor"
          strokeWidth={1.8}
          strokeLinecap="round"
          fill="none"
        />
      ) : (
        <path
          d="M5 9.5 L8 12 L13 6.5"
          stroke="currentColor"
          strokeWidth={1.8}
          strokeLinecap="round"
          strokeLinejoin="round"
          fill="none"
        />
      )}
    </>
  )
}

function WarnTriangle() {
  // Explicit arc at each vertex (radius ~1.6) so corners read as
  // rounded regardless of stroke width — `strokeLinejoin="round"` alone
  // gives a too-subtle curve at this stroke weight. Tinted fill mirrors
  // the bad/good circular chips' `bg-<tone>/20`.
  return (
    <>
      <path
        fill="currentColor"
        fillOpacity={0.15}
        stroke="currentColor"
        strokeWidth={1.5}
        strokeLinejoin="round"
        d="M7.62 2.81 L1.71 13.04 A1.6 1.6 0 0 0 3.09 15.5 L14.91 15.5 A1.6 1.6 0 0 0 16.29 13.04 L10.38 2.81 A1.6 1.6 0 0 0 7.62 2.81 Z"
      />
      <text
        x={9}
        y={13}
        textAnchor="middle"
        fontSize={10}
        fontWeight={700}
        fontFamily="ui-sans-serif, system-ui, sans-serif"
        fill="currentColor"
      >
        !
      </text>
    </>
  )
}
