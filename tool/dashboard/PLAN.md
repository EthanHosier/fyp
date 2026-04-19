# Rewrite the reference dashboard in our `dashboard/src` as a clean, extendable React app

## Context

`dashboard/reference/analysis dashboard/` is an AI-generated prototype: six `.jsx` files, 100 % inline styles, mock data shape (`window.REFACTOR_DATA`), and a 500-line hand-rolled SVG chart. The UI/UX is the target. The code is sloppy (prop-drilling, no abstraction, hard-coded tokens, mixed data/view/layout).

We continue on `dashboard-ui` and reproduce the reference UI **pixel-close** against the real `AnalysisReport` data from `useReport()`, but with:

- Tailwind v4 + shadcn primitives (already installed: `radix-ui`, `class-variance-authority`, `clsx`, `tailwind-merge`, `tw-animate-css`).
- Typed, reusable components organised by concern.
- A single adapter mapping `AnalysisReport` → a stable view-model, isolating UI from the Kotlin schema.
- No behaviour that depends on data we don't have — features unsupported by the real report are dropped for now, not stubbed.

## Decisions (confirmed)

1. **Chart = custom SVG, isolated.** Port the visx-style SVG into a dedicated `features/trajectory-chart/` module. Recharts/visx can't cleanly express the 4-lane annotation rail + branching suggested-path + multi-overlay in one chart, and the existing math is already correct.
2. **Hide unsupported features.** Drop `SUGGESTED_PATH`, per-checkpoint `EXPLANATIONS` prose, synthesized annotation titles/details, the annotation-type legend, and the "Layers" toggle for suggested path. The annotation lane and annotation selection **also drop** (real data has only raw `EventSummary`s without human prose; re-add later from `manualRefactorings`). The interval rail (build/test status) stays because real data supports it.
3. **Tweaks panel dropped entirely.** Fixed defaults: comfortable density, segmented line style, mint accent. No edit-mode postMessage.
4. **Dark-only.** Mirror the reference's IntelliJ-dark palette via Tailwind design tokens in `index.css`; no theme toggle.
5. **Build order = outside-in skeleton, then fill.** Get layout + data plumbing right before investing in the chart.

## Styling discipline

The component library is the default place for styling. Build reusable UI primitives (in `components/ui/`, shadcn-generated) and app-specific components (in `components/` and `features/*`) with well-defined variants — via `class-variance-authority` — for repeated patterns like **size, tone, state, emphasis**.

In feature and page files, **compose these components rather than writing raw Tailwind class strings for repeated visual patterns**. Inline Tailwind is still fine for:

- layout glue (flex/grid containers, gaps, justify/align),
- section spacing and positioning,
- genuinely one-off styling.

If the same class combination starts repeating (two or more places), extract it into a shared component or a new `cva` variant. Never duplicate a custom visual pattern across files. A rough hierarchy when styling something new:

1. Use a shadcn primitive with the existing variants.
2. If it's a shadcn primitive but a new visual mode is needed, add a `cva` variant there.
3. If it's app-specific but reusable, build a small wrapper component in `components/` (e.g. `score-pill`, `status-dot`) with its own variants.
4. Only then write raw Tailwind utilities inline.

All shadcn components are wrapped to read from our design tokens (`--bg`, `--fg`, `--accent`, etc.) rather than shadcn's default palette — edit the generated files when needed.

## Design system

Define in `src/index.css` under `@theme` (Tailwind v4 token API). Names mirror reference CSS vars so porting is mechanical:

```
--bg, --bg-1, --bg-2, --bg-3           surfaces (darkest → shell)
--fg, --fg-2, --fg-3, --fg-4           text tiers
--border, --border-strong
--accent, --accent-2, --accent-3       mint / blue / violet (fixed)
--good, --bad, --warn, --unknown       status
--font-sans (Inter/Geist), --font-mono (JetBrains Mono)
```

Radius scale: `rounded-md` = 6px, `rounded-sm` = 4px. Density: the `comfortable`/`compact` concept collapses to a single set of paddings (pick comfortable).

## Component structure

```
dashboard/src/
├── App.tsx                       ← layout shell only; composes features
├── main.tsx
├── index.css                     ← tokens + base
│
├── lib/
│   ├── utils.ts                  ← cn() (exists)
│   ├── format.ts                 ← formatDuration, formatTimestamp, formatDelta
│   └── scales.ts                 ← linearScale, linePath, invertScale (from reference graph.jsx)
│
├── hooks/
│   └── useReport.ts              ← already exists; unchanged
│
├── data/
│   ├── view-model.ts             ← AnalysisReport → DashboardViewModel (one place, fully typed)
│   └── types.ts                  ← DashboardViewModel, CheckpointVM, IntervalVM, MetricVM, Selection
│
├── components/                   ← generic shadcn-wrapped primitives + small app primitives
│   ├── ui/                       ← shadcn (button, card, checkbox, separator, badge,
│   │                               tooltip, scroll-area) added via `npx shadcn@latest add`
│   ├── icon.tsx                  ← lucide-react re-export with sized defaults
│   ├── sparkline.tsx             ← tiny inline SVG (60×18), reusable
│   ├── score-pill.tsx            ← cva variants: tone (good/bad/neutral), size (sm/md)
│   └── status-dot.tsx            ← cva variants: tone (pass/fail/unknown), size (sm/md)
│
└── features/
    ├── design-system/            ← design-system page (not a dashboard feature,
    │   │                           but a standalone route — lives alongside features
    │   │                           for consistency)
    │   ├── design-system-app.tsx
    │   ├── design-system-section.tsx
    │   ├── token-row.tsx
    │   ├── token-caption.tsx
    │   ├── color-swatch.tsx
    │   ├── text-sample.tsx
    │   └── variant-cell.tsx
    ├── header/
    │   └── header-bar.tsx
    ├── metric-rail/
    │   ├── metric-rail.tsx       ← container
    │   ├── primary-metric-row.tsx
    │   ├── overlay-metric-row.tsx
    │   └── rail-section.tsx
    ├── trajectory-chart/
    │   ├── trajectory-chart.tsx  ← public component, owns ResizeObserver
    │   ├── chart-axes.tsx        ← gridlines, ticks, Y-axis label
    │   ├── chart-lines.tsx       ← primary + secondary polylines + area fill
    │   ├── chart-points.tsx      ← checkpoint circles (hover/selected states)
    │   ├── chart-interval-rail.tsx
    │   ├── chart-hover-overlay.tsx  ← crosshair + HTML tooltip in <foreignObject>
    │   ├── chart-toolbar.tsx
    │   ├── chart-legend.tsx
    │   └── use-chart-scales.ts   ← memoised scales + margins
    ├── bottom-strip/
    │   ├── bottom-strip.tsx
    │   ├── checkpoint-filmstrip.tsx
    │   └── explanation-card.tsx  ← delta-bullet summary (no prose); hides when nothing useful
    └── detail-panel/
        ├── detail-panel.tsx      ← slide-in wrapper, animated via tw-animate-css
        ├── checkpoint-body.tsx
        ├── interval-body.tsx
        ├── metric-tile.tsx
        └── status-row.tsx
```

All cross-feature state (selection, primary metric, overlay metrics, layer toggles) lives in one `useDashboardState()` hook in `App.tsx` and flows down through typed props — no Context yet, the tree is shallow.

## View-model (the key abstraction)

`data/view-model.ts` exports a single pure function:

```ts
export function toViewModel(report: AnalysisReport): DashboardViewModel
```

Producing:

```ts
type DashboardViewModel = {
  session: { name: string; branch: string | null; startedAt: number; durationMs: number;
             checkpointCount: number; commitHash: string | null }
  metrics: MetricVM[]                 // { id, label, unit, better: 'lower'|'higher', group: 'code'|'process' }
  checkpoints: CheckpointVM[]         // { index, label: `c${i}`, tLabel: 't+MM:SS', sha, values: Record<metricId, number>,
                                      //   build: 'pass'|'fail'|'unknown', tests: 'pass'|'fail'|'unknown' }
  intervals: IntervalVM[]             // consecutive checkpoint pairs with derived joint status
  trajectory: TrajectoryStats | undefined
}
```

Metric sources (first cut — extend later):
- `complexity` ← mean `ck.perClass.wmc`
- `coupling` ← mean `ck.perClass.cbo`
- `duplication` ← `cpd.duplicatedLinesShare`
- `readability` ← `readability.summary.avgLineLength` (composite later)
- `churn` ← `diff.totalChurn`
- `process` score — deferred; omit from primary list for now.

`features/*` consume only `DashboardViewModel`. The Kotlin shape is invisible outside `data/`.

## Implementation order

Design system first, design-system page second, features after. Each step ends in a compiling, demoable state.

- [x] **1. Tokens.** `index.css` with `@theme` tokens (colors, font families, radii) + base body styles. Dark-only. No components yet.
- [x] **2. shadcn primitives wired to tokens.** Add `button`, `card`, `checkbox`, `separator`, `badge`, `scroll-area`, `tooltip`. Status-tone variants added to `badge`; colour tones added to `checkbox`; surface variants added to `tooltip`.
- [x] **3. App primitives with `cva` variants.** `status-dot`, `sparkline`, `score-pill`, `rail-section`, `metric-tile`, `status-row`, plus the earlier `meter`. `icon` skipped (using lucide directly). `exp-block` dropped (no prose). `filter-chip` + `legend-swatch` deferred to the chart steps where their context lives.
- [x] **4. Design-system page.** Showcases moved into `features/design-system/design-system-app.tsx`, routed at `/design-system` via `react-router-dom`. `App.tsx` at `/` is a placeholder until step 5.
- [x] **5. Layout shell.** `App.tsx` with the three-region grid (header / side-rail + main / bottom-strip + explanation card). Static placeholders built from primitives. Added `Text` component (`components/text.tsx`) as the single source of typography variants (display/heading/body/bodySm/caption/eyebrow/mono/monoStat/monoCaption/monoTiny × tone); refactored `rail-section`, `metric-tile`, `score-pill`, design-system page, and placeholders to use it. Design-system page now has a stacked "Typography · Text variants" section.
- [x] **6. View-model + dashboard store.** Shipped `data/types.ts`, `data/view-model.ts` (mapping `AnalysisReport` → `DashboardViewModel`: complexity/coupling/duplication/readability/churn + per-checkpoint build/tests status + derived intervals), `lib/format.ts` (tLabel, duration, delta, shortSha). Cross-feature state moved into a zustand store (`stores/dashboard-store.ts`) rather than a hook — selection / primary / secondaries (cap 2, mutex with primary) / layers. `App.tsx` calls `useReport()` → `toViewModel()`, shows a loading state, surfaces real session name / branch / commit / duration in the header placeholder. Added `/data` debug route (`features/data/data-app.tsx`) showing raw report next to parsed view-model.
- [x] **7. Header.** `features/header/header-bar.tsx` composes brand-mark (initials derived from `session.name` via `lib/format.ts:initials`), `Text`, lucide icons, shadcn `button`/`separator`/`badge`. Drops the process-score pill (deferred) and renders the commit short-sha as an outline badge. Added `projectName` to `SessionVM` + `formatClockTime` to format.ts.
- [x] **8. Metric rail.** `features/metric-rail/` — `primary-metric-row` (radio; left accent border + sparkline in the metric's own tone), `overlay-metric-row` (checkbox, tone per metric), and a `metric-rail` container wired to the zustand store. Added `MetricTone` to `MetricVM` + two extra palette tokens (`--brand-4` amber, `--brand-5` cyan) so each of the five metrics reads in a consistent colour across primary / overlay / future chart line. Overlay list shows all metrics in a fixed order; the primary row is force-checked + disabled rather than filtered out. Layers section kept only the Build/test intervals toggle. Checkbox transition-colors removed to avoid a brief flash when the primary switches.
- [x] **9. Bottom strip — filmstrip.** `features/bottom-strip/` — `checkpoint-card` (status dot + cX + t+MM:SS + one-line description, selected state = brand border + bg-bg-3), `checkpoint-filmstrip` (horizontal scroll, clicks push selection into the zustand store), and a `bottom-strip` container owning the filmstrip + still-placeholder explanation card (replaced in step 14).
- [x] **10. Trajectory chart — axes + primary line only.** Ported `lib/scales.ts` (linearScale, linePath, linearTicks). `features/trajectory-chart/`: `use-chart-scales` (margins + domain padding + tick generation), `chart-axes` (Y gridlines, Y caption, X baseline + cX labels), `chart-lines` (primary polyline, coloured via currentColor + the metric's tone), `chart-points` (checkpoint circles, selected halo, click → selection), `chart-toolbar` (eyebrow + display title + first→last mono caption + decorative FilterChips), `chart-legend` (primary swatch). `trajectory-chart.tsx` owns the ResizeObserver. Added `components/filter-chip.tsx` + `components/legend-swatch.tsx` primitives and `lib/metric-tone.ts` to centralise tone → class mapping (also adopted in primary-metric-row).
- [x] **11. Chart — secondaries + interval rail.** `chart-lines` now paints coloured interval segments on the primary line when `layers.intervals` is on (hiding the underlying line) and dashed secondary lines in each metric's own tone (independent Y scale per overlay). New `chart-interval-rail` renders the status rail below the axis with clickable rects that dispatch interval selections. `chart-legend` expanded with overlay swatches + a pass/fail/unknown key when the rail is on. Y-tick decimal count now adapts to tick step so small-range metrics (e.g. Complexity ≈ 2.5) don't print duplicate labels.
- [x] **12. Chart — hover crosshair + tooltip.** `chart-hover-overlay` adds a transparent hit-rect over the plot that snaps to the nearest checkpoint, renders a primary-toned crosshair + emphasised circle, and shows an HTML tooltip in a `<foreignObject>` (cX label, t+MM:SS, status dot, primary + active overlays with per-metric colour dots). Clicking the plot area dispatches a checkpoint selection to the store.
- [x] **13. Detail panel.** `features/detail-panel/` — right-side slide-in panel driven by the store's `selection`. `checkpoint-body` renders a metric-tile grid (fraction derived from each metric's observed min/max across all checkpoints) + StatusRow. `interval-body` renders deltas via `DataList` (from → to (+/- diff), tone per improvement direction) + Timing (duration, status dot, churn). Entry uses `tw-animate-css` `animate-in slide-in-from-right`; close is instant. MetricTile made transparent (inherits parent bg) so the card blends into the panel. Installed `@fontsource-variable/jetbrains-mono` and fronted it in `--font-mono` so monospace numerics render skinny like the reference.
- [x] **14. Explanation card.** `features/bottom-strip/explanation-card.tsx` — "What changed" bullet list (renamed from reference's "Why it matters" since we dropped the prose). Ranks top-4 metric deltas by relative magnitude (small-range metrics still surface). Checkpoint selection compares vs previous; interval selection compares its `from` → `to`. Graceful empty states for `c0` and missing values. Prompt shown when nothing selected.
- [x] **15. Polish pass.** Focus-visible rings + aria attributes on the custom buttons (`primary-metric-row` as `role=radio` with `aria-checked`, `checkpoint-card` with `aria-pressed` + descriptive `aria-label`, `filter-chip` with `aria-pressed`). Design-system page updated with FilterChip + LegendSwatch sections. Fonts (Geist + JetBrains Mono) and dark scrollbars landed earlier.

Each step: delete reference-only concepts as we reach them. Leave `dashboard/reference/` untouched as a living spec.

## Critical files to read or create

**Read (spec reference)**
- `dashboard/reference/analysis dashboard/src/app.jsx` — layout
- `dashboard/reference/analysis dashboard/src/graph.jsx` — SVG math + interactions (keep open while doing steps 6–8)
- `dashboard/reference/analysis dashboard/src/panels.jsx` — detail panel content
- `dashboard/reference/analysis dashboard/src/data.jsx` — mock shape (to map *away* from)
- `dashboard/src/generated/report-types.ts` — real data shape

**Create**
- `dashboard/src/index.css` (edit existing — add `@theme` tokens)
- `dashboard/src/data/view-model.ts`, `data/types.ts`
- `dashboard/src/lib/scales.ts`, `lib/format.ts`
- `dashboard/src/components/{icon,sparkline,score-pill,status-dot}.tsx`
- `dashboard/src/features/**` per structure above
- Replace `dashboard/src/App.tsx` with the new shell

**shadcn additions** (via `npx shadcn@latest add <name>`, as each step needs them):
`button`, `card`, `checkbox`, `separator`, `badge`, `scroll-area`, `tooltip`.

**Leave alone**
- `dashboard/src/hooks/useReport.ts` — already abstracts `window.__REPORT__` vs local-JSON divergence.
- `dashboard/src/generated/report-types.ts` — regenerated by Gradle; never hand-edit.
- Reference files.

## Verification

- **Step 1–2:** `npm run dev` → page loads, shows loading then skeletons sized correctly against a reference screenshot.
- **Every step:** `npm run typecheck && npm run lint` clean.
- **Step 6 onward:** visual side-by-side — open reference `Dashboard.html` in one browser, our dashboard (with `dashboard/src/analysis-report.json` sample) in another at the same window size. Diff by eye.
- **End-to-end:** `./gradlew runPluginAndServers`, do a real session, confirm the JCEF window renders against real analysis output (not the local sample JSON). Flip `useReport.ts` back to the `window.__REPORT__` path before this run.
- **Accessibility sanity:** tab through header + rail + filmstrip; verify shadcn focus rings are visible on dark background.

## Explicitly out of scope

- Annotation lane, annotation selection, annotation-type legend.
- Suggested-path ghost line and its legend/layer toggle.
- Explanation-card prose (WHAT/WHY/BETTER). Delta bullets only.
- Tweaks panel + accent-hue/density/line-style runtime switching.
- Light-mode / theme toggle.
- Process score — needs server-side definition first.
- Touched-files preview in detail panel — defer until we pick real fields from `diff.perFileChurn`.
- Bundling the dashboard into the plugin jar (still Vite dev server via `runPluginAndServers`).
