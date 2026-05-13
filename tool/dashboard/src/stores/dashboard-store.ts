import { create } from "zustand"

import type { Layers, MetricId, Selection } from "@/data/types"

/**
 * Single owner of cross-feature dashboard state. The view-model itself
 * is derived from `useReport()` and passed via props — the store only
 * holds user-controlled selections (selection / primary / secondaries
 * / layers) so features can read/write without prop-drilling.
 *
 *  - `selection` — checkpoint, interval, or refactoring step currently
 *    focused in the detail panel.
 *  - `primary` — metric drawn as the solid trajectory line.
 *  - `secondaries` — up to two dashed overlay metrics; mutually
 *    exclusive with `primary` (changing primary evicts it from the
 *    overlay list, and overflow drops the oldest overlay).
 *  - `layers` — chart overlay toggles (only build/test interval rail
 *    for now).
 */

const MAX_SECONDARIES = 2

type DashboardStore = {
  selection: Selection
  primary: MetricId
  secondaries: MetricId[]
  layers: Layers
  /** Checkpoint index flagged for visual emphasis on the chart without
   *  opening the detail panel. Set by e.g. clicking a rework alt's
   *  "Code added at <ckpt>" link — chart-point gains a halo while the
   *  side panel stays anchored on the alt. */
  highlightedCheckpointIndex: number | null
  setSelection: (s: Selection) => void
  setPrimary: (m: MetricId) => void
  toggleSecondary: (m: MetricId) => void
  setLayer: <K extends keyof Layers>(key: K, value: Layers[K]) => void
  setHighlightedCheckpointIndex: (idx: number | null) => void
}

export const useDashboardStore = create<DashboardStore>((set) => ({
  selection: null,
  primary: "process",
  secondaries: [],
  layers: {
    buildIntervals: true,
    testIntervals: true,
    alternativeTrajectories: true,
    showWorseAlternatives: false,
    userCommits: true,
  },
  highlightedCheckpointIndex: null,

  setSelection: (selection) => set({ selection }),

  setHighlightedCheckpointIndex: (highlightedCheckpointIndex) =>
    set({ highlightedCheckpointIndex }),

  setPrimary: (primary) =>
    set((state) => ({
      primary,
      secondaries: state.secondaries.filter((id) => id !== primary),
    })),

  toggleSecondary: (m) =>
    set((state) => {
      const prev = state.secondaries
      if (prev.includes(m)) return { secondaries: prev.filter((id) => id !== m) }
      if (prev.length >= MAX_SECONDARIES) return { secondaries: [prev[1], m] }
      return { secondaries: [...prev, m] }
    }),

  setLayer: (key, value) =>
    set((state) => ({ layers: { ...state.layers, [key]: value } })),
}))
