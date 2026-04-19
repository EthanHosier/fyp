import type { MetricTone } from "@/data/types"

/**
 * Centralised mapping from a metric's `tone` to Tailwind utility classes.
 * Keeps the colour identity of a metric in one place — any surface that
 * needs to paint in a metric's colour (primary row border, overlay
 * checkbox fill, sparkline, chart line, tooltip swatch) reads from here.
 *
 * Full class strings are written out so Tailwind's JIT picks them up.
 */

export const TONE_TEXT: Record<MetricTone, string> = {
  brand: "text-brand",
  "brand-2": "text-brand-2",
  "brand-3": "text-brand-3",
  "brand-4": "text-brand-4",
  "brand-5": "text-brand-5",
}

export const TONE_BORDER: Record<MetricTone, string> = {
  brand: "border-brand",
  "brand-2": "border-brand-2",
  "brand-3": "border-brand-3",
  "brand-4": "border-brand-4",
  "brand-5": "border-brand-5",
}

export const TONE_BG: Record<MetricTone, string> = {
  brand: "bg-brand",
  "brand-2": "bg-brand-2",
  "brand-3": "bg-brand-3",
  "brand-4": "bg-brand-4",
  "brand-5": "bg-brand-5",
}
