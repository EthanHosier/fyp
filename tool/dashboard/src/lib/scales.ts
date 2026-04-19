export type LinearScale = {
  (v: number): number
  invert: (r: number) => number
}

/**
 * Simple linear scale — maps a numeric domain onto a pixel range and
 * back. Ported from the reference `graph.jsx` to avoid pulling in d3.
 */
export function linearScale(
  domain: [number, number],
  range: [number, number],
): LinearScale {
  const [d0, d1] = domain
  const [r0, r1] = range
  const span = d1 - d0 || 1
  const fn = ((v: number) => r0 + ((v - d0) / span) * (r1 - r0)) as LinearScale
  fn.invert = (r: number) => d0 + ((r - r0) / (r1 - r0)) * span
  return fn
}

/** Straight-segment polyline path — `M x0,y0 L x1,y1 …`. */
export function linePath(points: Array<{ x: number; y: number }>): string {
  return points
    .map((p, i) => `${i === 0 ? "M" : "L"}${p.x.toFixed(1)},${p.y.toFixed(1)}`)
    .join(" ")
}

/** Evenly-spaced tick values across a domain (inclusive of both ends). */
export function linearTicks(domain: [number, number], count: number): number[] {
  const [lo, hi] = domain
  const step = (hi - lo) / (count - 1)
  return Array.from({ length: count }, (_, i) => lo + i * step)
}
