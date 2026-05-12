/*
 * CSS injected into @pierre/diffs's shadow DOM via the `unsafeCSS`
 * render option. Used by every component that renders a <FileDiff>
 * (FileDiffCard, code-smell-card, duplication-card) so JCEF and web
 * stay consistent.
 *
 * Why each rule exists:
 *  - The library composes most of its variable-driven colours through
 *    `color-mix(in lab, ...)` + `light-dark(...)` + relative-color
 *    `rgb(from ... r g b / α)`. All three need recent Chromium and
 *    fail silently on the older Chromium that JCEF ships, leaving
 *    rules unparsed and elements untinted / unsized.
 *  - The hunk-separator row collapses without subgrid-friendly grid
 *    behaviour; explicit width restores it.
 */
export const DIFF_SHADOW_CSS = `
  [data-separator-wrapper] {
    width: 900px;
  }
  [data-separator-content] {
    flex-basis: 0;
  }
  /* Inline word-diff highlight backgrounds. */
  [data-line-type='change-addition'] [data-diff-span] {
    background-color: #2b4a33;
  }
  [data-line-type='change-deletion'] [data-diff-span] {
    background-color: #43292d;
  }
  /* Code-smell / duplication line-selection highlight. The library
     uses color-mix-inline for change-addition / change-deletion +
     selected, with no override-variable hook — so we pin colours
     directly here. */
  [data-selected-line][data-line] {
    background-color: #2c3f55;
  }
  [data-selected-line][data-column-number] {
    background-color: #385677;
    color: #daecff;
  }
  [data-line-type='change-addition'][data-selected-line][data-line],
  [data-line-type='change-addition'][data-selected-line][data-line][data-hovered],
  [data-line-type='change-deletion'][data-selected-line][data-line],
  [data-line-type='change-deletion'][data-selected-line][data-line][data-hovered] {
    background-color: #2c3f55;
  }
  [data-line-type='change-addition'][data-selected-line][data-column-number],
  [data-line-type='change-deletion'][data-selected-line][data-column-number] {
    background-color: #385677;
    color: #daecff;
  }
`
