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
    ├── preview/                  ← design-system preview page (not a dashboard feature,
    │   │                           but a standalone route — lives alongside features
    │   │                           for consistency)
    │   ├── preview-section.tsx
    │   ├── token-row.tsx
    │   ├── token-caption.tsx
    │   ├── color-swatch.tsx
    │   └── text-sample.tsx
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

Design system first, preview page second, features after. Each step ends in a compiling, demoable state.

- [x] **1. Tokens.** `index.css` with `@theme` tokens (colors, font families, radii) + base body styles. Dark-only. No components yet.
- [x] **2. shadcn primitives wired to tokens.** Add `button`, `card`, `checkbox`, `separator`, `badge`, `scroll-area`, `tooltip`. Status-tone variants added to `badge`; colour tones added to `checkbox`; surface variants added to `tooltip`.
- [x] **3. App primitives with `cva` variants.** `status-dot`, `sparkline`, `score-pill`, `rail-section`, `metric-tile`, `status-row`, plus the earlier `meter`. `icon` skipped (using lucide directly). `exp-block` dropped (no prose). `filter-chip` + `legend-swatch` deferred to the chart steps where their context lives.
- [ ] **4. Preview page.** Move the showcases from `App.tsx` into `features/preview/preview-app.tsx`, gated via `?preview` (or similar) in `main.tsx`. Shows every primitive + variant side-by-side. Iterate on look & feel here — **gate** before moving on to step 5.
- [ ] **5. Layout shell.** `App.tsx` with the three-region grid (header / side-rail + main / bottom-strip). Static placeholders built from primitives. Verify palette + spacing against reference screenshot.
- [ ] **6. View-model + `useDashboardState`.** Ship `data/view-model.ts`, `data/types.ts`. Wire `useReport()` → `toViewModel()`. Empty/loading states. Expose `selection`, `primary`, `secondaries`, `layers` state + setters.
- [ ] **7. Header.** `header-bar` composing `score-pill`, `status-dot`, `icon`, shadcn `button`/`separator`/`badge`.
- [ ] **8. Metric rail.** `primary-metric-row` (radio behaviour), `overlay-metric-row` (checkbox, enforce max 2 in state). Drop "Annotation types" and "Suggested path" sub-sections. Keep only the "Build/test intervals" toggle.
- [ ] **9. Bottom strip — filmstrip first, explanation later.** `checkpoint-filmstrip` shows status dot + label + time + short description (first event summary if present, else short sha). Selection updates global state.
- [ ] **10. Trajectory chart — axes + primary line only.** Port `lib/scales.ts` from the reference's math. Build `chart-axes`, `chart-lines` (primary only), `chart-points`, `use-chart-scales`, `chart-toolbar`, `chart-legend` (primary swatch only). ResizeObserver in `trajectory-chart.tsx`. Build `filter-chip` + `legend-swatch` primitives here.
- [ ] **11. Chart — secondaries + interval rail.** Extend `chart-lines` for dashed overlays; add `chart-interval-rail` driven by `vm.intervals`.
- [ ] **12. Chart — hover crosshair + tooltip.** `chart-hover-overlay` with `<foreignObject>` HTML tooltip. Click dispatches selection.
- [ ] **13. Detail panel.** `detail-panel` animates in/out via `tw-animate-css` utility classes. `checkpoint-body` (metrics grid using `metric-tile`, `status-row` — **no** touched-files preview until we map `diff.perFileChurn`). `interval-body` (deltas between the two checkpoints, timing from checkpoint timestamps, churn from diff).
- [ ] **14. Explanation card.** Minimal version: when a checkpoint is selected, render bullet list of biggest metric deltas vs previous checkpoint (computed in `view-model`). No prose. Hidden otherwise.
- [ ] **15. Polish pass.** Keyboard focus outlines, `aria-*` on buttons, dark scrollbar styling, `@fontsource-variable/geist` for sans, JetBrains Mono for numerics. Side-by-side visual diff against reference HTML. Update the preview page with any primitives added during feature work.

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
