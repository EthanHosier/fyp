// Unstyled draft of FYP presentation slides.
// Layout is intentionally plain: title at top, bullets below, white background.
// Styling pass comes later once content is approved.

const pptxgen = require("pptxgenjs");

const pres = new pptxgen();
pres.layout = "LAYOUT_16x9"; // 10" x 5.625"
pres.author = "Ethan Hosier";
pres.title = "Beyond Before-and-After: Process-Quality Evaluation of Refactoring Sessions";

// ── Theme ─────────────────────────────────────────────────────
// Subtle off-white background + a thin deep-blue accent strip at the top of
// every slide. Same accent colour the deck already uses (feedback group, J(τ)
// closing line, italic conclusion text).
const THEME_ACCENT = "0000CD";
const THEME_BG     = "FAFAFA";

pres.defineSlideMaster({
  title: "MAIN",
  background: { color: THEME_BG },
  objects: [
    { rect: { x: 0, y: 0, w: 10, h: 0.05, fill: { color: THEME_ACCENT }, line: { type: "none" } } },
  ],
});

// Slide geometry constants (inches)
const TITLE = { x: 0.5, y: 0.3, w: 9, h: 0.7, fontSize: 28, bold: true, margin: 0 };
const SUBTITLE = { x: 0.5, y: 1.0, w: 9, h: 0.5, fontSize: 16, italic: true, color: "555555" };
const BODY = { x: 0.5, y: 1.3, w: 9, h: 4.0, fontSize: 16, valign: "top" };

function bulletText(slide, items, opts = {}) {
  const runs = items.map((t, i) => ({
    text: t,
    options: { bullet: true, breakLine: i < items.length - 1, paraSpaceAfter: 6 },
  }));
  slide.addText(runs, { ...BODY, ...opts });
}

function titled(title, subtitle = null) {
  const s = pres.addSlide({ masterName: "MAIN" });
  s.addText(title, TITLE);
  if (subtitle) s.addText(subtitle, SUBTITLE);
  return s;
}

// Small italic-quote card pinned to the very top-right corner of a slide.
// Sized to coexist with a narrowed (w: 6.3) title on its left.
function quoteCard(slide, quote, attribution, opts = {}) {
  const x = opts.x ?? 7.05;
  const y = opts.y ?? 0.30;
  const w = opts.w ?? 2.8;
  const h = opts.h ?? 0.70;
  slide.addText(
    [
      { text: "“ " + quote + " ”", options: { breakLine: true, paraSpaceAfter: 2 } },
      { text: "— " + attribution, options: { color: "888888", italic: false } },
    ],
    {
      x, y, w, h,
      fontSize: 11, italic: true, color: "333333",
      valign: "top", margin: 3,
      fill: { color: "F5F5F5" },
    },
  );
}

// ─────────────────────────────────────────────────────────────
// Slide 1 - Title
// ─────────────────────────────────────────────────────────────
{
  const s = pres.addSlide({ masterName: "MAIN" });
  // Imperial College London logo, top-left. Native 12900x1417 (~9.1:1), shrunk to 1.8" wide.
  s.addImage({
    path: "images/Imperial_College_London_new_logo.png",
    x: 0.4, y: 0.30, w: 1.8, h: 0.198,
  });
  // Eyebrow: small italic grey, with colon
  s.addText("Beyond Before-and-After:", {
    x: 0.5, y: 1.35, w: 9, h: 0.4, fontSize: 18, italic: true, color: "555555", align: "center", margin: 0,
  });
  // Main title (line space underneath eyebrow)
  s.addText("Process-Quality Evaluation of Refactoring Sessions", {
    x: 0.5, y: 1.95, w: 9, h: 0.9, fontSize: 32, bold: true, align: "center", margin: 0,
  });
  s.addText(
    "Scoring the path a developer takes through a refactoring session, and generating reference trajectories where a better path existed.",
    { x: 0.5, y: 3.05, w: 9, h: 0.9, fontSize: 18, italic: true, align: "center", color: "555555" },
  );
  s.addText("Ethan Hosier  ·  Imperial College London  ·  Department of Computing", {
    x: 0.5, y: 4.4, w: 9, h: 0.4, fontSize: 14, align: "center", color: "777777",
  });
  s.addText("Supervisor: Dr Robert Chatley  ·  Second marker: Dr Cristian Cadar", {
    x: 0.5, y: 4.8, w: 9, h: 0.4, fontSize: 12, align: "center", color: "777777",
  });
}

// ─────────────────────────────────────────────────────────────
// Slide 2 - The hook: same destination, different journeys
// ─────────────────────────────────────────────────────────────
{
  const s = titled("Same destination, different journeys");

  // Intro line
  s.addText(
    [
      { text: "Two developers refactor the same code " },
      { text: "and arrive at the same final state", options: { bold: true } },
      { text: "." },
    ],
    { x: 0.5, y: 1.2, w: 9, h: 0.45, fontSize: 16, italic: true, color: "555555", align: "center", margin: 0 },
  );

  // Two developer cards (side by side)
  const cards = [
    {
      label: "Developer A",
      accent: "0A7D2A",
      fill: "EBF7F0",
      behaviours: [
        "Ran tests.",
        "Committed checkpoints.",
        "Used IDE refactorings.",
      ],
      x: 0.6,
    },
    {
      label: "Developer B",
      accent: "B33A1E",
      fill: "FBEDE9",
      behaviours: [
        "Broke the build twice.",
        "Added then removed 80 lines.",
        "Hand-edited what the IDE could do safely.",
      ],
      x: 5.2,
    },
  ];

  cards.forEach((c) => {
    // Rounded-rect card background
    s.addShape(pres.shapes.ROUNDED_RECTANGLE, {
      x: c.x, y: 1.85, w: 4.2, h: 2.05,
      fill: { color: c.fill },
      line: { color: c.accent, width: 1 },
      rectRadius: 0.1,
    });
    // Header label
    s.addText(c.label, {
      x: c.x, y: 1.92, w: 4.2, h: 0.4,
      fontSize: 18, bold: true, color: c.accent, align: "center", margin: 0,
    });
    // Behaviour bullets inside the card
    const bulletRuns = c.behaviours.map((b, i) => ({
      text: b,
      options: { bullet: true, breakLine: i < c.behaviours.length - 1, paraSpaceAfter: 6 },
    }));
    s.addText(bulletRuns, {
      x: c.x + 0.35, y: 2.38, w: 3.55, h: 1.45,
      fontSize: 13, color: "1A1A1A", valign: "top",
    });
  });

  // Bottom block: the question + punchline
  bulletText(
    s,
    [
      "Which one would you want on your team?",
      "Endpoint-only evaluation cannot tell them apart.",
    ],
    { y: 4.15, h: 1.3 },
  );
}

// ─────────────────────────────────────────────────────────────
// Slide 3 - What's missing in prior work
// ─────────────────────────────────────────────────────────────
{
  const s = pres.addSlide({ masterName: "MAIN" });
  s.addText("Motivation: what's missing in prior work", {
    ...TITLE, y: 0.45,
  });

  // Motivation intro
  s.addText(
    [
      { text: "To help developers refactor better, we need to measure what a " },
      { text: "good refactoring session", options: { italic: true } },
      { text: " actually looks like - not just what code it produces." },
    ],
    { x: 0.6, y: 1.20, w: 8.8, h: 0.7, fontSize: 15, italic: true, color: "333333", align: "center", margin: 0 },
  );

  // Transition line into the prior-work bullets
  s.addText(
    "But prior work mostly measures outcomes, not the process:",
    { x: 0.5, y: 1.95, w: 9, h: 0.35, fontSize: 14, bold: true, color: "1A1A1A", align: "center", margin: 0 },
  );

  const REF_COLOR = "555555";
  const BLANK = { text: " ", options: { bullet: false, breakLine: true } };
  const runs = [
    { text: "Endpoint quality metrics ", options: { bullet: true, bold: true } },
    { text: "(CK, smells, cohesion, coupling)", options: { color: REF_COLOR, italic: true } },
    { text: " - compare before vs after; ignore the path between, and can't distinguish two sessions ending at the same code.", options: { color: REF_COLOR, breakLine: true } },
    BLANK,
    { text: "Refactoring recommenders / sequence generators ", options: { bullet: true, bold: true } },
    { text: "(search-based, ReSynth)", options: { color: REF_COLOR, italic: true } },
    { text: " - generate paths toward a target end state; don't score the developer's own path.", options: { color: REF_COLOR, breakLine: true } },
    BLANK,
    { text: "Exercise-scoped feedback tools ", options: { bullet: true, bold: true } },
    { text: "- match explicit events inside predefined exercises; don't generalise to arbitrary sessions.", options: { color: REF_COLOR } },
  ];
  s.addText(runs, { x: 0.5, y: 2.45, w: 9, h: 3.0, fontSize: 13, valign: "top", paraSpaceAfter: 8 });
}

// ─────────────────────────────────────────────────────────────
// Slide 4 - Closest existing tool (Industrial Logic e-learning)
// ─────────────────────────────────────────────────────────────
{
  const s = pres.addSlide({ masterName: "MAIN" });
  s.addText(
    [
      { text: "Closest Existing Tool...", options: {} },
      { text: "  Industrial Logic", options: { color: "888888" } },
    ],
    TITLE,
  );

  // Three Industrial Logic screenshots in a row, uniform width.
  //   score        : 1686x1238 → h ≈ 3.00 / 1.36 = 2.20
  //   compilation  : 1610x784  → h ≈ 3.00 / 2.05 = 1.46
  //   refactorings : 1620x954  → h ≈ 3.00 / 1.70 = 1.76
  const ROW_Y = 1.30;
  const W = 3.0;
  const GAP = 0.15;
  const xs = [0.35, 0.35 + W + GAP, 0.35 + 2 * (W + GAP)];

  const images = [
    { path: "images/industrial-logic-score.png",             h: 2.20, caption: "Score" },
    { path: "images/industrial-logic-compilation-tests.png", h: 1.46, caption: "Compilation + tests" },
    { path: "images/industrial-logic-refactorings.png",      h: 1.76, caption: "Refactorings" },
  ];

  images.forEach((img, i) => {
    s.addImage({ path: img.path, x: xs[i], y: ROW_Y, w: W, h: img.h });
    s.addText(img.caption, {
      x: xs[i], y: ROW_Y + 2.30, w: W, h: 0.30,
      fontSize: 12, italic: true, color: "555555", align: "center", margin: 0,
    });
  });

  // Closing punchline at the bottom of the slide
  s.addText(
    [
      { text: "Gap: ", options: { bold: true } },
      { text: "no system scores an arbitrary observed refactoring session against an explicit process-quality metric, and shows where a better path was available.", options: {} },
    ],
    {
      x: 0.5, y: 4.40, w: 9, h: 1.0,
      fontSize: 14, italic: true, color: "0000CD", align: "center", valign: "middle", margin: 0,
    },
  );
}

// ─────────────────────────────────────────────────────────────
// Slide 5 - Narrowing the design space
// ─────────────────────────────────────────────────────────────
{
  const s = titled("Narrowing the design space");

  // Intro line above the design-requirement bullets
  s.addText(
    "Evaluating refactoring as a process is a wide, mostly-unexplored design space. Four requirements shaped what we built.",
    { x: 0.5, y: 1.2, w: 9, h: 0.5, fontSize: 13, italic: true, color: "555555", margin: 0 },
  );

  // Each design requirement: bold title + normal-weight body + optional superscript footnote marker
  const REF_COLOR = "888888";
  const requirements = [
    {
      title: "Quantifiable + comparable score",
      body: "one number per trajectory so sessions can be compared.",
      mark: "[1]",
    },
    {
      title: "Actionable, moment-specific feedback",
      body: 'point to a specific moment AND a "what you could have done instead" alternative.',
      mark: "[2]",
    },
    {
      title: "Efficient alternative synthesis",
      body: 'the state-action space over a real codebase is too large to enumerate, so we must find efficient methods of identifying "better" alternatives.',
      mark: null,
    },
    {
      title: "Seamless, in-workflow capture",
      body: "zero extra developer effort to use the tool.",
      mark: "[3]",
    },
  ];

  const runs = [];
  requirements.forEach((req, i) => {
    const isLast = i === requirements.length - 1;
    runs.push({
      text: req.title + " ",
      options: { bullet: true, bold: true },
    });
    runs.push({
      text: "- " + req.body,
      options: { color: REF_COLOR, breakLine: !req.mark && isLast ? false : !req.mark },
    });
    if (req.mark) {
      runs.push({
        text: " " + req.mark,
        options: { color: REF_COLOR, breakLine: !isLast },
      });
    }
    if (!isLast) {
      runs.push({ text: " ", options: { bullet: false, breakLine: true } });
    }
  });

  s.addText(runs, { x: 0.5, y: 1.8, w: 9, h: 3.2, fontSize: 14, valign: "top" });

  // Footer references
  const footerRuns = [
    { text: "[1] Composite quality models (Quamoco, QMOOD), adapted from snapshot- to trajectory-level.   ", options: {} },
    { text: "[2] Inverts prior refactoring recommenders (ReSynth, Refactoring Navigator, search-based refactoring).   ", options: {} },
    { text: "[3] IDE-event logging (Damevski et al., CodeWatcher).", options: {} },
  ];
  s.addText(footerRuns, {
    x: 0.5, y: 5.10, w: 9, h: 0.45,
    fontSize: 9, italic: true, color: REF_COLOR, align: "left", valign: "top", margin: 0,
  });
}

// ─────────────────────────────────────────────────────────────
// Slide 6 - End-to-end system
// ─────────────────────────────────────────────────────────────
{
  const s = titled("The tool, end to end: actionable refactoring process analysis");

  // Three-stage pipeline diagram, each box lists its sub-stages.
  // Slide is just the title + diagram now; diagram sits roughly centred in the body area.
  const boxY = 1.6;
  const boxW = 2.6;
  const boxH = 2.6;
  const gap = 0.9;
  const startX = (10 - (3 * boxW + 2 * gap)) / 2; // 0.4

  const cells = [
    {
      title: "IntelliJ Plugin",
      items: ["Records developer events", "Captures initial codebase"],
    },
    {
      title: "Analysis Backend",
      items: [
        "Shadow repo reconstruction",
        "Metric calculation",
        "Divergence detection & synthesis",
      ],
    },
    {
      title: "Dashboard",
      items: ["Surfaces divergence points in context", "Explains alternative paths"],
    },
  ];

  cells.forEach((cell, i) => {
    const x = startX + i * (boxW + gap);

    // Outer box
    s.addShape(pres.shapes.RECTANGLE, {
      x,
      y: boxY,
      w: boxW,
      h: boxH,
      fill: { color: "FFFFFF" },
      line: { color: "000000", width: 1 },
    });

    // Box title (top)
    s.addText(cell.title, {
      x: x + 0.1,
      y: boxY + 0.2,
      w: boxW - 0.2,
      h: 0.5,
      fontSize: 16,
      bold: true,
      align: "center",
      margin: 0,
    });

    // Divider line under the title
    s.addShape(pres.shapes.LINE, {
      x: x + 0.3,
      y: boxY + 0.8,
      w: boxW - 0.6,
      h: 0,
      line: { color: "999999", width: 0.75 },
    });

    // Sub-items as bulleted list below the divider
    s.addText(
      cell.items.map((item, j) => ({
        text: item,
        options: {
          bullet: true,
          breakLine: j < cell.items.length - 1,
          paraSpaceAfter: 8,
        },
      })),
      {
        x: x + 0.25,
        y: boxY + 1.0,
        w: boxW - 0.4,
        h: boxH - 1.1,
        fontSize: 12,
        color: "333333",
        valign: "top",
      },
    );
  });

  // Arrows between boxes, with a small flow label above each arrow
  const flowLabels = [
    [{ text: "events.jsonl + initial-src/" }],
    [
      { text: "analysis", options: { breakLine: true } },
      { text: "-report.json" },
    ],
  ];
  for (let i = 0; i < 2; i++) {
    const arrowX = startX + (i + 1) * boxW + i * gap;
    const arrowY = boxY + boxH / 2;
    s.addShape(pres.shapes.LINE, {
      x: arrowX,
      y: arrowY,
      w: gap,
      h: 0,
      line: { color: "000000", width: 1.5, endArrowType: "triangle" },
    });
    s.addText(flowLabels[i], {
      x: arrowX - 0.05,
      y: arrowY - 0.55,
      w: gap + 0.1,
      h: 0.5,
      fontSize: 9,
      italic: true,
      color: "555555",
      align: "center",
      valign: "bottom",
      margin: 0,
    });
  }
}

// ─────────────────────────────────────────────────────────────
// Slide 7 - Demo
// ─────────────────────────────────────────────────────────────
{
  const s = pres.addSlide({ masterName: "MAIN" });
  s.addText("Demo", {
    x: 0.5, y: 1.95, w: 9, h: 1.2, fontSize: 60, bold: true, align: "center", valign: "middle", margin: 0,
  });
  s.addText("OrderProcessor: Breaking up a long method into smaller methods", {
    x: 0.5, y: 3.25, w: 9, h: 0.5, fontSize: 20, italic: true, color: "555555", align: "center", valign: "middle", margin: 0,
  });
}

// ─────────────────────────────────────────────────────────────
// Slide 8 - Scoring a trajectory
// ─────────────────────────────────────────────────────────────
{
  // Title position is nudged up vs. the shared TITLE constant so the formula has a touch more room
  const s = pres.addSlide({ masterName: "MAIN" });
  s.addText("Scoring a trajectory: J(τ)", {
    x: 0.5, y: 0.15, w: 9, h: 0.7, fontSize: 28, bold: true, margin: 0,
  });

  const POS = "0A7D2A"; // green for positive weights
  const NEG = "B33A1E"; // red for negative (penalty) weights

  // Top line of the formula, centred above the term list
  s.addText(
    [
      { text: "J(τ)  =  clip" },
      { text: "[0,100]", options: { subscript: true } },
      { text: "  (    50    " },
      { text: "(baseline)", options: { italic: true, color: "777777" } },
      { text: "    +" },
    ],
    {
      x: 0.5,
      y: 0.92,
      w: 9,
      h: 0.45,
      fontSize: 22,
      align: "center",
      valign: "middle",
      margin: 0,
    },
  );

  // Weighted-term list as a borderless table so weight column is right-aligned
  // and term column is left-aligned, in a proper proportional font.
  // Weights/× stay at the table-level fontSize (16); term names get bumped to 18.
  const weightCell = (sign) => ({
    bold: true,
    color: sign === "+" ? POS : NEG,
    align: "right",
    valign: "middle",
    margin: 0.06,
  });
  const opCell = { color: "555555", align: "center", valign: "middle", margin: 0.04 };
  const termCell = { color: "1A1A1A", align: "left", valign: "middle", margin: 0.06, fontSize: 18 };

  const terms = [
    ["+", 50, "cleanliness gain"],
    ["+", 11, "step-savings bonus"],
    ["−", 28, "broken build / test rate"],
    ["−", 14, "skipped-test rate"],
    ["−", 11, "manual edits the IDE could refactor"],
    ["−", 11, "Intermediate cleanliness lag (degradation)"],
    ["−",  7, "Long durations without commits"],
  ];

  const rows = terms.map(([sign, weight, name]) => {
    // First row (cleanliness gain) carries an inline asterisk pointing to the footer.
    // Uses a non-breaking space so the asterisk can't wrap to a new line.
    const displayName = name === "cleanliness gain" ? name + "*" : name;
    return [
      { text: sign + String(weight), options: weightCell(sign) },
      { text: "×", options: opCell },
      { text: displayName, options: termCell },
    ];
  });

  s.addTable(rows, {
    x: 2.6,
    y: 1.4,
    w: 4.8,
    colW: [0.8, 0.4, 3.6],
    rowH: 0.32,
    fontSize: 16,
    border: { type: "none" },
  });

  // Footer expanding the cleanliness-gain asterisk + justifying the equal sub-weights
  s.addText(
    [
      {
        text: "* cleanliness gain = mean of 6 normalised signals: complexity, coupling, duplication, readability, smells, cohesion.",
        options: { breakLine: true },
      },
      {
        text: "All 6 weighted equally - no trajectory-level calibration data exists, so equal weights are the least-assumptive choice.",
      },
    ],
    {
      x: 0.5,
      y: 4.95,
      w: 9,
      h: 0.6,
      fontSize: 11,
      italic: true,
      color: "777777",
      align: "center",
      margin: 0,
    },
  );
}

// ─────────────────────────────────────────────────────────────
// Slide 9 - Four kinds of divergence (overview)
// ─────────────────────────────────────────────────────────────
{
  const s = titled("Four kinds of divergence");

  // Each kind: name in black + grey description
  const REF_COLOR = "888888";
  const kinds = [
    ["Manual-Refactor", "unnecessary risk (hand-edit when the IDE could have safely done it)."],
    ["Ordering",        "right refactorings, wrong sequence."],
    ["Rework",          "unnecessary churn (added then removed)."],
    ["Hygiene",         "missing safety checkpoints (no tests, no commits)."],
  ];

  const runs = [];
  kinds.forEach(([name, desc], i) => {
    const isLast = i === kinds.length - 1;
    runs.push({
      text: name + " ",
      options: { bullet: true },
    });
    runs.push({
      text: "- " + desc,
      options: { color: REF_COLOR, breakLine: !isLast },
    });
    if (!isLast) {
      runs.push({ text: " ", options: { bullet: false, breakLine: true } });
    }
  });

  s.addText(runs, { x: 0.5, y: 1.5, w: 9, h: 3.5, fontSize: 18, valign: "top" });
}

// ─────────────────────────────────────────────────────────────
// Slide 10 - Manual-Refactor: detect + synthesise
// ─────────────────────────────────────────────────────────────
{
  const s = pres.addSlide({ masterName: "MAIN" });
  s.addText("Manual-Refactor: detect + synthesise", { ...TITLE, w: 6.3, fontSize: 24 });
  {
    const intro = "Developer hand-edited something the IDE could have done safely (and with its precondition checks).";
    const steps = [
      "Refactoring Miner on sliding commit windows (of shadow repo) - anything we find that the IDE did not emit an event for is a manual refactoring.",
      "Apply the equivalent IDE refactoring (Eclipse / JDT).",
      "Reconcile minor JDT ↔ IntelliJ AST differences (e.g. static modifiers, variable liveness).",
      "Merge the user's other edits back on top.",
    ];
    const runs = [
      { text: intro, options: { bullet: true, breakLine: true, paraSpaceAfter: 20 } },
    ];
    steps.forEach((t, i) => {
      runs.push({
        text: t,
        options: { bullet: { type: "number" }, indentLevel: 1, breakLine: i < steps.length - 1, paraSpaceAfter: 6 },
      });
    });
    s.addText(runs, { x: 0.5, y: 1.2, w: 9, h: 1.95, fontSize: 13, valign: "top" });
    quoteCard(s, "Very actionable - at the start I didn't know most IntelliJ actions.", "P2");
  }

  // Manual-Refactor figure: edit-burst stream with miner detections. Original 1422x466 (~3.05:1).
  const imgW = 6.8;
  const imgH = 2.23;
  s.addImage({
    path: "images/manual-refactor.png",
    x: (10 - imgW) / 2,
    y: 3.15,
    w: imgW,
    h: imgH,
  });
}

// ─────────────────────────────────────────────────────────────
// Slide 11 - Ordering: detect + synthesise
// ─────────────────────────────────────────────────────────────
{
  const s = pres.addSlide({ masterName: "MAIN" });
  s.addText("Ordering: detect + synthesise", { ...TITLE, w: 6.3, fontSize: 24 });
  {
    const intro = "Developer performed their refactoring steps in a sub-optimal order, leading to unnecessary intermediate degradation.";
    const steps = [
      "Identify dependencies between steps → DAG.",
      "Enumerate valid topological orderings.",
      "Synthesise (Eclipse / JDT), utilising Prefix Trie (DFS) and git checkouts on backtrack.",
      "Normalize AST + compare with original.",
    ];
    const runs = [
      { text: intro, options: { bullet: true, breakLine: true, paraSpaceAfter: 20 } },
    ];
    steps.forEach((t, i) => {
      runs.push({
        text: t,
        options: { bullet: { type: "number" }, indentLevel: 1, breakLine: i < steps.length - 1, paraSpaceAfter: 6 },
      });
    });
    s.addText(runs, { x: 0.5, y: 1.2, w: 9, h: 2.05, fontSize: 14, valign: "top" });
    quoteCard(s, "How can you actually apply that in the future without knowing beforehand?", "P2");
  }

  // Reorder-synthesis figure (Dependency DAG + Prefix-trie DFS). Original 1680x574 (~2.93:1).
  const imgW = 6.0;
  const imgH = 2.05;
  s.addImage({
    path: "images/reorder-synthesis.png",
    x: (10 - imgW) / 2,
    y: 3.3,
    w: imgW,
    h: imgH,
  });
}

// ─────────────────────────────────────────────────────────────
// Slide 12 - Rework: detect + synthesise
// ─────────────────────────────────────────────────────────────
{
  const s = pres.addSlide({ masterName: "MAIN" });
  s.addText("Rework: detect + synthesise", { ...TITLE, w: 6.3, fontSize: 24 });
  {
    const intro = "Code added and later removed (or vice versa) - unnecessary churn.";
    const steps = [
      "Compare git diffs between adjacent steps of shadow repo.",
      "Hash each hunk, and look for a matching, opposite hunk with matching (file, scope, content-hash) in a future git diff.",
      "Remove that hunk from both diffs, calculate the updated line numbers of every git diff hunk between each of the intermediate states, and replay these new diffs from the start step.",
    ];
    const runs = [
      { text: intro, options: { bullet: true, breakLine: true, paraSpaceAfter: 20 } },
    ];
    steps.forEach((t, i) => {
      runs.push({
        text: t,
        options: { bullet: { type: "number" }, indentLevel: 1, breakLine: i < steps.length - 1, paraSpaceAfter: 6 },
      });
    });
    s.addText(runs, { x: 0.5, y: 1.2, w: 9, h: 1.95, fontSize: 13, valign: "top" });
    quoteCard(s, "Over-penalised undos + redos.", "P1");
  }

  // Rework figure: Add/Remove paired by (File, Scope, Content Hash). Original 1190x574 (~2.07:1).
  const imgW = 4.8;
  const imgH = 2.32;
  s.addImage({
    path: "images/rework.png",
    x: (10 - imgW) / 2,
    y: 3.1,
    w: imgW,
    h: imgH,
  });
}

// ─────────────────────────────────────────────────────────────
// Slide 13 - Hygiene: detect + synthesise
// ─────────────────────────────────────────────────────────────
{
  const s = pres.addSlide({ masterName: "MAIN" });
  s.addText("Hygiene: detect + synthesise", { ...TITLE, w: 6.3, fontSize: 24 });

  const intro = "Long stretches of work without test runs, or commits.";
  const steps = [
    "Iterate through each run's IDE events.",
    "If there has been > 1 minute since the last edit and still no test run, flag a Test DP.",
    "If there has been > 5 refactoring steps since the last commit, flag a Commit DP.",
    "For each DP, generate an identical run but with the corresponding test or commit event.",
  ];
  const outro = "Very cheap to compute.";

  const runs = [
    { text: intro, options: { bullet: true, breakLine: true, paraSpaceAfter: 20 } },
  ];
  steps.forEach((t, i) => {
    runs.push({
      text: t,
      options: { bullet: { type: "number" }, indentLevel: 1, breakLine: true, paraSpaceAfter: i === steps.length - 1 ? 20 : 6 },
    });
  });
  runs.push({
    text: outro,
    options: { bullet: true, paraSpaceAfter: 6 },
  });

  s.addText(runs, { x: 0.5, y: 1.2, w: 9, h: 4.0, fontSize: 14, valign: "top" });
  quoteCard(s, "The most actionable kind - committing more frequently in particular.", "P1");
}

// ─────────────────────────────────────────────────────────────
// Slide 14 - What we wanted to evaluate (three questions, each tied to a dataset + experiment)
// ─────────────────────────────────────────────────────────────
{
  const s = titled("What we wanted to evaluate");

  const REF_COLOR = "555555";
  const SUB_GAP = 6;
  const GROUP_GAP = 20;

  const groups = [
    {
      main: "3 Questions",
      subs: [
        "Is the tool accurate?",
        "Is the process score reliable?",
        "Does the tool change developer behaviour?",
      ],
    },
    {
      main: "2 Datasets",
      subs: [
        ["45-session labelled injection set", "controlled, balanced per-kind coverage."],
        ["30-session randomised user study", "5 MEng participants × 6 sessions, 2 arms (feedback vs no-feedback)."],
      ],
    },
  ];

  const runs = [];
  groups.forEach((g, gIdx) => {
    const isLastGroup = gIdx === groups.length - 1;
    runs.push({
      text: g.main,
      options: { bullet: true, bold: true, breakLine: true, paraSpaceAfter: 8 },
    });
    g.subs.forEach((sub, sIdx) => {
      const isLastSub = sIdx === g.subs.length - 1;
      const trailingGap = isLastSub && !isLastGroup ? GROUP_GAP : SUB_GAP;
      if (typeof sub === "string") {
        runs.push({
          text: sub,
          options: { bullet: { indent: 30 }, indentLevel: 1, breakLine: !(isLastGroup && isLastSub), paraSpaceAfter: trailingGap },
        });
      } else {
        const [lead, tail] = sub;
        runs.push({
          text: lead + " ",
          options: { bullet: { indent: 30 }, indentLevel: 1, paraSpaceAfter: trailingGap },
        });
        runs.push({
          text: "- " + tail,
          options: { color: REF_COLOR, breakLine: !(isLastGroup && isLastSub), paraSpaceAfter: trailingGap },
        });
      }
    });
  });

  s.addText(runs, { x: 0.5, y: 1.3, w: 9, h: 3.5, fontSize: 16, valign: "top" });

  // Footer note (same style as Slide 4's "Gap:" punchline)
  s.addText(
    [
      { text: "Reproducible: ", options: { bold: true } },
      { text: "every table and figure is regenerated from the Jupyter notebook in the repo.", options: {} },
    ],
    {
      x: 0.5, y: 5.0, w: 9, h: 0.5,
      fontSize: 14, italic: true, color: "0000CD", align: "center", valign: "middle", margin: 0,
    },
  );
}

// ─────────────────────────────────────────────────────────────
// Slide 15 - Experiment 1 divider
// ─────────────────────────────────────────────────────────────
{
  const s = pres.addSlide({ masterName: "MAIN" });
  s.addText("Experiment 1: Is the tool accurate?", {
    x: 0.5, y: 2.2, w: 9, h: 1.2,
    fontSize: 36, bold: true, color: "1A1A1A",
    align: "center", valign: "middle", margin: 0,
  });
}

// ─────────────────────────────────────────────────────────────
// Slide 16 - Detector evaluation: setup + label reliability + metric choice (HIDDEN — backup)
// ─────────────────────────────────────────────────────────────
{
  const s = titled("Is the tool accurate?");
  s.hidden = true;

  // Same visual pattern as Slide 8: short black lead + grey continuation
  const REF_COLOR = "888888";
  const points = [
    ["45 hand-labelled injection sessions", "on a Java codebase."],
    ["3 raters (author + 2 external MEng cohort members)", "inter-rater Cohen's κ = 1.00 (Ordering, Manual-Refactor), 0.86–1.00 (Rework), 0.72–0.86 (Hygiene)."],
    ["Disagreements predominantly surrounding hygiene + rework labels", "differences in opinion of what constitutes a step."],
    ["Track all divergence points detected", "not only ones with a higher process score than the user."],
    ["Measure precision + recall", "precision guards against FPs that waste the developer's attention. Recall guards against FNs that miss real opportunities. F1 averages the two - so a high score can hide a detector that's annoying users (lots of FPs) or one that's quietly missing things (lots of FNs). Accuracy is dominated by true negatives."],
  ];

  const runs = [];
  points.forEach(([lead, tail], i) => {
    const isLast = i === points.length - 1;
    runs.push({
      text: lead + " ",
      options: { bullet: true, paraSpaceAfter: 10 },
    });
    runs.push({
      text: "- " + tail,
      options: { color: REF_COLOR, breakLine: !isLast, paraSpaceAfter: 10 },
    });
  });

  s.addText(runs, { x: 0.5, y: 1.3, w: 9, h: 4.0, fontSize: 15, valign: "top" });
}

// ─────────────────────────────────────────────────────────────
// Slide 17 - Experiment 1 results (compressed 4-card view)
// ─────────────────────────────────────────────────────────────
{
  const s = pres.addSlide({ masterName: "MAIN" });
  s.addText("Experiment 1: Is the tool accurate?", {
    x: 0.5, y: 0.15, w: 9, h: 0.55, fontSize: 24, bold: true, margin: 0, align: "center",
  });

  // Cluster labels above each column
  s.addText("DETECTION", {
    x: 0.30, y: 0.80, w: 4.55, h: 0.25,
    fontSize: 10, bold: true, color: "888888", align: "center", margin: 0, charSpacing: 2,
  });
  s.addText("SYNTHESIS", {
    x: 5.15, y: 0.80, w: 4.55, h: 0.25,
    fontSize: 10, bold: true, color: "888888", align: "center", margin: 0, charSpacing: 2,
  });

  // Card-drawing helper — rounded rect background + a single text box that
  // vertically centres the headline / subtitle / sentence as multi-paragraph runs.
  function statCard(opts) {
    const { x, y, w, h, big, bigSize = 38, subtitle, sentence } = opts;
    s.addShape(pres.shapes.ROUNDED_RECTANGLE, {
      x, y, w, h,
      fill: { color: "F5F5F5" },
      line: { type: "none" },
      rectRadius: 0.08,
    });
    const runs = [
      { text: big, options: { fontSize: bigSize, bold: true, color: "0000CD", breakLine: true, paraSpaceAfter: 6 } },
    ];
    if (subtitle) {
      runs.push({ text: subtitle, options: { fontSize: 11, bold: true, color: "1A1A1A", breakLine: true, paraSpaceAfter: 8 } });
    }
    if (sentence) {
      runs.push({ text: sentence, options: { fontSize: 10, italic: true, color: "555555" } });
    }
    s.addText(runs, {
      x: x + 0.2, y: y + 0.10, w: w - 0.4, h: h - 0.20,
      align: "center", valign: "middle", margin: 0,
    });
  }

  // ── Card 1 (TL) — Precision
  statCard({
    x: 0.30, y: 1.10, w: 4.55, h: 1.95,
    big: "1.00", bigSize: 44,
    subtitle: "Precision  ·  all four kinds",
    sentence: "When the detector flags a divergence, it's never a false positive. (45 labelled sessions, three raters at Cohen's κ ≥ 0.72.)",
  });

  // ── Card 2 (TR) — Overall beat rate
  statCard({
    x: 5.15, y: 1.10, w: 4.55, h: 1.95,
    big: "62%", bigSize: 44,
    subtitle: "Alternatives beat user trajectory  ·  41 of 66",
    sentence: "When the tool flags a divergence, the synthesised alternative usually scores higher than the path the user actually took.",
  });

  // ── Card 3 (BL) — Recall by kind
  statCard({
    x: 0.30, y: 3.20, w: 4.55, h: 1.85,
    big: "1.00  /  1.00  /  0.76  /  0.36", bigSize: 22,
    subtitle: "Recall  ·  Hygiene / Rework / MR / Ordering",
    sentence: "Three kinds catch everything; Ordering's validator is deliberately conservative - a known scope limit.",
  });

  // ── Card 4 (BR) — Beat rate by kind
  statCard({
    x: 5.15, y: 3.20, w: 4.55, h: 1.85,
    big: "100%  /  75%  /  46%  /  42%", bigSize: 22,
    subtitle: "Beat rate by kind  ·  Hygiene / MR / Rework / Ordering",
    sentence: "Hygiene and Manual-Refactor reliably improve the score; Ordering ties often because alternatives reach the same end state.",
  });

  // Bottom summary line
  s.addText(
    "Precision-perfect detection, balanced recall (one known scope limit on Ordering), and a higher-scoring alternative in roughly two-thirds of detected moments.",
    {
      x: 0.5, y: 5.15, w: 9, h: 0.40,
      fontSize: 11, italic: true, color: "0000CD",
      align: "center", valign: "middle", margin: 0,
    },
  );
}

// ─────────────────────────────────────────────────────────────
// Slide 18 - Per-kind decision matrices (HIDDEN — backup detail)
// ─────────────────────────────────────────────────────────────
{
  // Custom-positioned title (nudged up vs. the shared TITLE constant so the 2×2 grid + footer all fit)
  const s = pres.addSlide({ masterName: "MAIN" });
  s.hidden = true;
  s.addText("Decision matrix per divergence kind", {
    x: 0.5, y: 0.05, w: 9, h: 0.5, fontSize: 28, bold: true, margin: 0,
  });

  // Preamble: clarify the unit + scope
  s.addText(
    "Session-level (45 injection sessions).",
    {
      x: 0.5, y: 0.55, w: 9, h: 0.22,
      fontSize: 12, italic: true, color: "555555", align: "center", margin: 0,
    },
  );

  // Colours
  const TP_FILL = "D4F0DC", TP_TEXT = "0A7D2A";
  const FN_FILL = "F5D5CE", FN_TEXT = "B33A1E";
  const FP_FILL = "F2F2F2", FP_TEXT = "888888";
  const TN_FILL = "EAEAEA", TN_TEXT = "555555";

  // Render a single 2×2 confusion matrix with kind title above + precision/recall caption below
  function drawMatrix(mx, my, kindName, tp, fn, fp, tn, precision, recall) {
    const labelW = 0.6, cellW = 1.25, cellH = 0.6;
    const totalW = labelW + 2 * cellW;

    // Kind title (h tightened from 0.3 → 0.22 so the title sits closer to the matrix below)
    s.addText(kindName, {
      x: mx, y: my, w: totalW, h: 0.22,
      fontSize: 14, bold: true, align: "center", valign: "bottom", margin: 0,
    });

    // Column headers
    s.addText("Predicted +", {
      x: mx + labelW, y: my + 0.24, w: cellW, h: 0.22,
      fontSize: 9, italic: true, color: "555555", align: "center", margin: 0,
    });
    s.addText("Predicted −", {
      x: mx + labelW + cellW, y: my + 0.24, w: cellW, h: 0.22,
      fontSize: 9, italic: true, color: "555555", align: "center", margin: 0,
    });

    // Row headers
    s.addText("Actual +", {
      x: mx, y: my + 0.48, w: labelW, h: cellH,
      fontSize: 9, italic: true, color: "555555", align: "right", valign: "middle", margin: 0.04,
    });
    s.addText("Actual −", {
      x: mx, y: my + 0.48 + cellH, w: labelW, h: cellH,
      fontSize: 9, italic: true, color: "555555", align: "right", valign: "middle", margin: 0.04,
    });

    // 4 cells
    const cells = [
      { row: 0, col: 0, fill: TP_FILL, color: TP_TEXT, label: "TP", value: tp },
      { row: 0, col: 1, fill: FN_FILL, color: FN_TEXT, label: "FN", value: fn },
      { row: 1, col: 0, fill: FP_FILL, color: FP_TEXT, label: "FP", value: fp },
      { row: 1, col: 1, fill: TN_FILL, color: TN_TEXT, label: "TN", value: tn },
    ];
    cells.forEach((c) => {
      const cx = mx + labelW + c.col * cellW;
      const cy = my + 0.56 + c.row * cellH;
      s.addShape(pres.shapes.RECTANGLE, {
        x: cx, y: cy, w: cellW, h: cellH,
        fill: { color: c.fill }, line: { color: "999999", width: 0.5 },
      });
      s.addText(
        [
          { text: String(c.value), options: { fontSize: 20, bold: true, color: c.color, breakLine: true } },
          { text: c.label, options: { fontSize: 9, color: "666666" } },
        ],
        { x: cx, y: cy, w: cellW, h: cellH, align: "center", valign: "middle", margin: 0, paraSpaceAfter: 0 },
      );
    });

    // Precision · Recall caption below the matrix
    s.addText(
      [
        { text: "Precision ", options: { color: "555555" } },
        { text: precision, options: { bold: true, color: "1A1A1A" } },
        { text: "   ·   Recall ", options: { color: "555555" } },
        { text: recall, options: { bold: true, color: "1A1A1A" } },
      ],
      {
        x: mx, y: my + 0.48 + 2 * cellH + 0.1, w: totalW, h: 0.22,
        fontSize: 11, italic: true, align: "center", margin: 0,
      },
    );
  }

  // 2×2 grid of mini matrices (moved up so content doesn't overlap the footer cues)
  drawMatrix(0.7,  0.85, "Manual-Refactor", 16,  5, 0, 24, "1.00", "0.76");
  drawMatrix(5.2,  0.85, "Ordering",        14, 24, 0,  7, "1.00", "0.36");
  drawMatrix(0.7,  2.95, "Rework",           9,  0, 0, 36, "1.00", "1.00");
  drawMatrix(5.2,  2.95, "Hygiene",          8,  0, 0, 37, "1.00", "1.00");

  // Footer: Ordering scope limit (shortened per request)
  s.addText(
    "Ordering recall is the outlier: the reorder synthesiser's splitOnInvalid validator rejects any window containing a step it cannot safely.",
    {
      x: 0.4, y: 5.05, w: 9.2, h: 0.28,
      fontSize: 9, italic: true, color: "555555", align: "center", margin: 0,
    },
  );
  // Critical-reflection cue
  s.addText(
    "Controlled-injection: balanced fixture, scripted behaviour - not shown on real-world refactoring sessions.",
    {
      x: 0.4, y: 5.33, w: 9.2, h: 0.28,
      fontSize: 9, italic: true, color: "999999", align: "center", margin: 0,
    },
  );
}

// ─────────────────────────────────────────────────────────────
// Slide 19 - Are the alternatives actually better? (HIDDEN — backup detail)
// ─────────────────────────────────────────────────────────────
{
  // Custom-positioned title to match Slide 17's geometry
  const s = pres.addSlide({ masterName: "MAIN" });
  s.hidden = true;
  s.addText("Are the alternatives actually better?", {
    x: 0.5, y: 0.1, w: 9, h: 0.6, fontSize: 28, bold: true, margin: 0,
  });

  // Headline subtitle
  s.addText(
    "41 of 66 synthesised alternatives strictly beat the user's trajectory (~62%). Quality varies sharply by kind.",
    {
      x: 0.5, y: 0.78, w: 9, h: 0.3,
      fontSize: 13, italic: true, color: "555555", align: "center", margin: 0,
    },
  );

  // Colour palette — match Slide 17 quadrants where possible
  const TP_FILL = "D4F0DC", TP_TEXT = "0A7D2A";
  const TIE_FILL = "FFF4D6", TIE_TEXT = "8A6A00";
  const FN_FILL = "F5D5CE", FN_TEXT = "B33A1E";

  // Cell formatting
  const HEADER = { bold: true, color: "1A1A1A", fill: { color: "F0F0F0" }, align: "center", valign: "middle", margin: 0.08 };
  const HEADER_LEFT = { ...HEADER, align: "left" };
  const KIND = { bold: true, color: "1A1A1A", align: "left", valign: "middle", margin: 0.08 };
  const NEUTRAL = { color: "1A1A1A", align: "center", valign: "middle", margin: 0.08 };
  const BEAT = { bold: true, color: TP_TEXT, fill: { color: TP_FILL }, align: "center", valign: "middle", margin: 0.08 };
  const TIE = { bold: true, color: TIE_TEXT, fill: { color: TIE_FILL }, align: "center", valign: "middle", margin: 0.08 };
  const LOSE = { bold: true, color: FN_TEXT, fill: { color: FN_FILL }, align: "center", valign: "middle", margin: 0.08 };
  const RATE_HIGH = { bold: true, color: TP_TEXT, align: "center", valign: "middle", margin: 0.08 };
  const RATE_LOW = { bold: true, color: "1A1A1A", align: "center", valign: "middle", margin: 0.08 };
  const MAX_STANDOUT = { bold: true, color: TP_TEXT, align: "center", valign: "middle", margin: 0.08 };

  const rows = [
    [
      { text: "Kind", options: HEADER_LEFT },
      { text: "DPs", options: HEADER },
      { text: "Beats ✓", options: HEADER },
      { text: "Ties =", options: HEADER },
      { text: "Loses ✗", options: HEADER },
      { text: "Beat rate", options: HEADER },
      { text: "Max Δ", options: HEADER },
    ],
    [
      { text: "Hygiene", options: KIND },
      { text: "13", options: NEUTRAL },
      { text: "13", options: BEAT },
      { text: "0", options: TIE },
      { text: "0", options: LOSE },
      { text: "100%", options: RATE_HIGH },
      { text: "+9", options: NEUTRAL },
    ],
    [
      { text: "Manual-Refactor", options: KIND },
      { text: "16", options: NEUTRAL },
      { text: "12", options: BEAT },
      { text: "3", options: TIE },
      { text: "1", options: LOSE },
      { text: "75%", options: RATE_HIGH },
      { text: "+23", options: MAX_STANDOUT },
    ],
    [
      { text: "Rework", options: KIND },
      { text: "13", options: NEUTRAL },
      { text: "6", options: BEAT },
      { text: "1", options: TIE },
      { text: "6", options: LOSE },
      { text: "46%", options: RATE_LOW },
      { text: "+8", options: NEUTRAL },
    ],
    [
      { text: "Ordering", options: KIND },
      { text: "24", options: NEUTRAL },
      { text: "10", options: BEAT },
      { text: "12", options: TIE },
      { text: "2", options: LOSE },
      { text: "42%", options: RATE_LOW },
      { text: "+9", options: NEUTRAL },
    ],
  ];

  s.addTable(rows, {
    x: 0.7, y: 1.3, w: 8.6,
    colW: [1.9, 0.7, 1.1, 1.0, 1.1, 1.2, 1.6],
    rowH: 0.45,
    fontSize: 14,
    border: { type: "solid", pt: 0.5, color: "CCCCCC" },
  });

  // Footer — saturation / same-end-state explanations
  s.addText(
    [
      {
        text: "Ordering alternatives share the end state, so only W_lag (intermediate cleanliness) can separate them. They tie when intermediate cleanliness is identical - often when build/tests broke and cleanliness stats are frozen at the last trustworthy value. ",
      },
      {
        text: "Manual-Refactor's 3 ties + 1 loss come from the [0,100] score clamp - the user's score is pinned at the floor, so the IDE alternative can't score lower either.",
      },
    ],
    {
      x: 0.4, y: 4.55, w: 9.2, h: 0.6,
      fontSize: 10, italic: true, color: "777777", align: "center", margin: 0,
    },
  );
  // Critical-reflection cue
  s.addText(
    "Result under J - not yet independently validated by expert ranking.",
    {
      x: 0.4, y: 5.20, w: 9.2, h: 0.28,
      fontSize: 9, italic: true, color: "999999", align: "center", margin: 0,
    },
  );
}

// ─────────────────────────────────────────────────────────────
// Slide 20 - Experiment 2 divider
// ─────────────────────────────────────────────────────────────
{
  const s = pres.addSlide({ masterName: "MAIN" });
  s.addText("Experiment 2: Is the process score reliable?", {
    x: 0.5, y: 2.2, w: 9, h: 1.2,
    fontSize: 36, bold: true, color: "1A1A1A",
    align: "center", valign: "middle", margin: 0,
  });
}

// ─────────────────────────────────────────────────────────────
// Slide 21 - Is the process score reliable? (setup) (HIDDEN — backup)
// ─────────────────────────────────────────────────────────────
{
  const s = titled("Is the process score reliable?");
  s.hidden = true;

  const REF_COLOR = "888888";
  const SUB_GAP = 6;
  const GROUP_GAP = 18;

  // Two main questions, each with sub-bullets describing the experiment specifics
  const groups = [
    {
      main: "How stable is the score?",
      subs: [
        ["Two perturbation sweeps", "single-knob (one weight scaled ×0.1 to ×10) + multi-knob Monte Carlo (200 samples, σ = ln 2, so ~×0.25 to ×4 of production)."],
        ["Stability metrics", "top-1 hit rate (does the top-ranked DP stay on top?) + Kendall's τ-b on per-session rankings."],
      ],
    },
    {
      main: "Does every component of the process score hold weight?",
      subs: [
        ["Process-side ablation", "enumerate all 2⁷ = 128 subsets of the 7 process weights → 5,760 cases per session set."],
        ["Recovery metrics", "sum-over-sum magnitude recovery (does the term carry signal?) + leave-one-out Kendall's τ-b (does removing it reshuffle rankings?)."],
      ],
    },
  ];

  const runs = [];
  groups.forEach((g, gIdx) => {
    const isLastGroup = gIdx === groups.length - 1;
    // Main bullet
    runs.push({
      text: g.main,
      options: { bullet: true, bold: true, breakLine: true, paraSpaceAfter: 6 },
    });
    g.subs.forEach(([lead, tail], sIdx) => {
      const isLastSub = sIdx === g.subs.length - 1;
      const trailingGap = isLastSub && !isLastGroup ? GROUP_GAP : SUB_GAP;
      runs.push({
        text: lead + " ",
        options: { bullet: { indent: 30 }, indentLevel: 1, paraSpaceAfter: trailingGap },
      });
      runs.push({
        text: "- " + tail,
        options: { color: REF_COLOR, breakLine: !(isLastGroup && isLastSub), paraSpaceAfter: trailingGap },
      });
    });
  });

  s.addText(runs, { x: 0.5, y: 1.3, w: 9, h: 4.0, fontSize: 15, valign: "top" });
}

// ─────────────────────────────────────────────────────────────
// Slide 22 - Experiment 2 results (compressed 4-card view)
// ─────────────────────────────────────────────────────────────
{
  const s = pres.addSlide({ masterName: "MAIN" });
  s.addText("Experiment 2: Is the process score reliable?", {
    x: 0.5, y: 0.15, w: 9, h: 0.55, fontSize: 24, bold: true, margin: 0, align: "center",
  });

  // Cluster labels above each column (mirrors Slide 17)
  s.addText("STABILITY", {
    x: 0.30, y: 0.80, w: 4.55, h: 0.25,
    fontSize: 10, bold: true, color: "888888", align: "center", margin: 0, charSpacing: 2,
  });
  s.addText("ABLATION", {
    x: 5.15, y: 0.80, w: 4.55, h: 0.25,
    fontSize: 10, bold: true, color: "888888", align: "center", margin: 0, charSpacing: 2,
  });

  // Card helper — same shape as Slide 17's helper (duplicated to keep slide blocks self-contained)
  function statCard(opts) {
    const { x, y, w, h, big, bigSize = 44, subtitle, sentence } = opts;
    s.addShape(pres.shapes.ROUNDED_RECTANGLE, {
      x, y, w, h,
      fill: { color: "F5F5F5" },
      line: { type: "none" },
      rectRadius: 0.08,
    });
    const runs = [
      { text: big, options: { fontSize: bigSize, bold: true, color: "0000CD", breakLine: true, paraSpaceAfter: 6 } },
    ];
    if (subtitle) {
      runs.push({ text: subtitle, options: { fontSize: 11, bold: true, color: "1A1A1A", breakLine: true, paraSpaceAfter: 8 } });
    }
    if (sentence) {
      runs.push({ text: sentence, options: { fontSize: 10, italic: true, color: "555555" } });
    }
    s.addText(runs, {
      x: x + 0.2, y: y + 0.10, w: w - 0.4, h: h - 0.20,
      align: "center", valign: "middle", margin: 0,
    });
  }

  // ── Card 1 (TL) — Single-knob stability
  statCard({
    x: 0.30, y: 1.10, w: 4.55, h: 1.95,
    big: "96.5%", bigSize: 44,
    subtitle: "Single-knob top-1 preserved",
    sentence: "Top-ranked divergence rarely changes under any one-weight perturbation. Only 1.8% of cases are clamp-frozen, so this is genuine response - not a clamp artefact.",
  });

  // ── Card 2 (TR) — Every term contributes
  statCard({
    x: 5.15, y: 1.10, w: 4.55, h: 1.95,
    big: "7 / 7", bigSize: 44,
    subtitle: "Process terms with measurable rank effect  ·  LOO τ-b range 0.527 - 0.784",
    sentence: "Length is the strongest rank-carrier (0.527); lag the weakest (0.784, but contributes via magnitude). No process term is redundant.",
  });

  // ── Card 3 (BL) — Multi-knob stability
  statCard({
    x: 0.30, y: 3.20, w: 4.55, h: 1.85,
    big: "84.6%", bigSize: 44,
    subtitle: "Multi-knob top-1 preserved  ·  mean τ-b = 0.586",
    sentence: "When all 7 weights move at once (σ = ln 2 ≈ ×0.25 to ×4), the top-ranked divergence holds in ~85% of samples. Meaningful rank correlation, not full preservation.",
  });

  // ── Card 4 (BR) — Cleanliness sub-weights barely matter
  statCard({
    x: 5.15, y: 3.20, w: 4.55, h: 1.85,
    big: "≤ 0.9%", bigSize: 44,
    subtitle: "Top-1 disruption from any single cleanliness sub-weight",
    sentence: "Perturbing any of the 6 cleanliness signals (complexity, coupling, duplication, readability, smells, cohesion) barely shifts the ranking - ~10× less than process weights. Equal weights are defensible.",
  });

  // Bottom summary (folds in the critical-reflection cue from the original slide)
  s.addText(
    "Top-1 holds under both single- and multi-knob perturbation, and every process term carries measurable rank effect. Robustness ≠ calibration: weights aren't validated against downstream outcomes.",
    {
      x: 0.5, y: 5.15, w: 9, h: 0.40,
      fontSize: 11, italic: true, color: "0000CD",
      align: "center", valign: "middle", margin: 0,
    },
  );
}

// ─────────────────────────────────────────────────────────────
// Slide 23 - Score is locally robust (HIDDEN — backup detail)
// ─────────────────────────────────────────────────────────────
{
  const s = titled("Score is locally robust");
  s.hidden = true;

  const REF_COLOR = "888888";

  // ── Top group: How stable is the score? (kept as bullets) ──
  const stabilityRuns = [
    { text: "How stable is the score?", options: { bullet: true, bold: true, breakLine: true, paraSpaceAfter: 6 } },
    { text: "Single-knob: 96.5% top-1 preserved ", options: { bullet: { indent: 30 }, indentLevel: 1, paraSpaceAfter: 4 } },
    { text: "- only 1.8% of cases are clamp-frozen.", options: { color: REF_COLOR, breakLine: true, paraSpaceAfter: 4 } },
    { text: "Multi-knob: 84.6% top-1 preserved ", options: { bullet: { indent: 30 }, indentLevel: 1, paraSpaceAfter: 4 } },
    { text: "- meaningful correlation, not full preservation.", options: { color: REF_COLOR } },
  ];
  s.addText(stabilityRuns, { x: 0.5, y: 1.15, w: 9, h: 1.10, fontSize: 13, valign: "top" });

  // ── Section header: Does every component hold weight? ──
  s.addText("Does every component of the process score hold weight?", {
    x: 0.5, y: 2.35, w: 9, h: 0.3,
    fontSize: 13, bold: true, color: "1A1A1A", margin: 0,
  });

  // ── Ablation results table ──
  // Per-term LOO τ-b on user-study rankable subset (Table 5.6 in report) + single-knob top-1 disruption (Table 5.2).
  // Sorted by LOO τ-b ascending: smaller = removing the term reshuffles rankings more = more influential.
  const HEADER_CELL = {
    bold: true, color: "1A1A1A", fill: { color: "F0F0F0" },
    align: "center", valign: "middle", margin: 0.06, fontSize: 11,
  };
  const TERM_CELL = {
    bold: true, color: "1A1A1A",
    align: "left", valign: "middle", margin: 0.06, fontSize: 12,
  };
  const NUM_CELL = {
    color: "333333",
    align: "center", valign: "middle", margin: 0.06, fontSize: 12,
  };
  const NUM_HIGHLIGHT = {
    bold: true, color: "0A7D2A",
    align: "center", valign: "middle", margin: 0.06, fontSize: 12,
  };
  const NUM_MUTED = {
    color: "999999", italic: true,
    align: "center", valign: "middle", margin: 0.06, fontSize: 12,
  };

  // Each row: [term, LOO τ, top-1 disruption %, isLowestTau, isLag]
  const ablationRows = [
    ["length",    "0.527", "6.2%",  true,  false],
    ["manualIde", "0.588", "7.6%",  false, false],
    ["broken",    "0.627", "5.3%",  false, false],
    ["commitGap", "0.655", "6.7%",  false, false],
    ["skipTests", "0.663", "6.7%",  false, false],
    ["gain",      "0.683", "10.7%", false, false],
    ["lag",       "0.784", "0.4%",  false, true],
  ];

  const tableRows = [
    [
      { text: "Removed term", options: HEADER_CELL },
      { text: "Leave-one-out τ-b (lower = more rank-carrier)", options: HEADER_CELL },
      { text: "Single-knob top-1 Δ", options: HEADER_CELL },
    ],
    ...ablationRows.map(([term, tau, topDelta, isLowest, isLag]) => [
      { text: term, options: TERM_CELL },
      { text: tau, options: isLowest ? NUM_HIGHLIGHT : (isLag ? NUM_MUTED : NUM_CELL) },
      { text: topDelta, options: isLag ? NUM_MUTED : NUM_CELL },
    ]),
  ];

  s.addTable(tableRows, {
    x: 1.0, y: 2.70, w: 8.0,
    colW: [2.0, 3.6, 2.4],
    rowH: 0.32,
    fontFace: "Calibri",
    border: { type: "solid", pt: 0.5, color: "DDDDDD" },
  });
  // Critical-reflection cue
  s.addText(
    "Robustness ≠ calibration. Weights not validated against downstream outcomes.",
    {
      x: 0.5, y: 5.30, w: 9, h: 0.28,
      fontSize: 9, italic: true, color: "999999", align: "center", margin: 0,
    },
  );
}

// ─────────────────────────────────────────────────────────────
// Slide 24 - Experiment 3 divider
// ─────────────────────────────────────────────────────────────
{
  const s = pres.addSlide({ masterName: "MAIN" });
  s.addText("Experiment 3: Does feedback change developer behaviour?", {
    x: 0.5, y: 2.2, w: 9, h: 1.2,
    fontSize: 32, bold: true, color: "1A1A1A",
    align: "center", valign: "middle", margin: 0,
  });
}

// ─────────────────────────────────────────────────────────────
// Slide 25 - User study setup (does feedback change behaviour?) (HIDDEN — backup)
// ─────────────────────────────────────────────────────────────
{
  const s = titled("Does dashboard feedback change developer behaviour?");
  s.hidden = true;

  const REF_COLOR = "888888";
  const SUB_GAP = 6;
  const GROUP_GAP = 18;

  const groups = [
    {
      main: "How did we design the study?",
      subs: [
        "5 MEng Computing participants, 6 sessions each, on an order-processing codebase",
        ["Randomised into 2 arms", "P1, P2, P3 see dashboard feedback between sessions; P4 and P5 don't. Same playbook, codebase, instrumentation."],
      ],
    },
    {
      main: "What did we measure?",
      subs: [
        ["Divergence-point count per session", "does the detector still surface the same kinds of friction over time?"],
        "Process score across the arc",
      ],
    },
  ];

  const runs = [];
  groups.forEach((g, gIdx) => {
    const isLastGroup = gIdx === groups.length - 1;
    runs.push({
      text: g.main,
      options: { bullet: true, bold: true, breakLine: true, paraSpaceAfter: 6 },
    });
    g.subs.forEach((sub, sIdx) => {
      const isLastSub = sIdx === g.subs.length - 1;
      const trailingGap = isLastSub && !isLastGroup ? GROUP_GAP : SUB_GAP;
      const lead = Array.isArray(sub) ? sub[0] : sub;
      const tail = Array.isArray(sub) ? sub[1] : null;
      if (tail) {
        runs.push({
          text: lead + " ",
          options: { bullet: { indent: 30 }, indentLevel: 1, paraSpaceAfter: trailingGap },
        });
        runs.push({
          text: "- " + tail,
          options: { color: REF_COLOR, breakLine: !(isLastGroup && isLastSub), paraSpaceAfter: trailingGap },
        });
      } else {
        runs.push({
          text: lead,
          options: { bullet: { indent: 30 }, indentLevel: 1, breakLine: !(isLastGroup && isLastSub), paraSpaceAfter: trailingGap },
        });
      }
    });
  });

  s.addText(runs, { x: 0.5, y: 1.3, w: 9, h: 3.6, fontSize: 15, valign: "top" });

  // Footer caveat
  s.addText(
    "Random assignment, n = 3 vs n = 2. Treated as a directional finding, not a hypothesis test.",
    { x: 0.5, y: 5.05, w: 9, h: 0.3, fontSize: 11, italic: true, color: "888888", align: "center" }
  );
}

// ─────────────────────────────────────────────────────────────
// Slide 26 - Fewer divergences, but production score is noisy
// ─────────────────────────────────────────────────────────────
{
  const s = pres.addSlide({ masterName: "MAIN" });
  s.addText("Fewer divergences with feedback - but the raw score is noisy", {
    x: 0.5, y: 0.15, w: 9, h: 0.55, fontSize: 24, bold: true, margin: 0, align: "center",
  });

  const LABELS = ["S1", "S2", "S3", "S4", "S5", "S6"];
  const FB_DARK = "0000CD";
  const BL_DARK = "B25500";

  // LEFT: production-weighted process score (the noisy chart — visual evidence for the title)
  {
    const data = [
      { name: "Feedback mean", labels: LABELS, values: [13.7, 66.7, 33.0, 89.7, 32.0, 48.3] },
      { name: "Baseline mean", labels: LABELS, values: [11.5, 20.0, 15.5, 76.5, 44.5, 27.0] },
    ];
    s.addChart(pres.ChartType.line, data, {
      x: 0.30, y: 1.00, w: 4.95, h: 3.65,
      chartColors: [FB_DARK, BL_DARK],
      lineSize: 2.5,
      lineDataSymbol: "circle", lineDataSymbolSize: 6,
      showTitle: true, title: "Production-weighted process score",
      titleFontSize: 11, titleColor: "333333",
      showLegend: true, legendPos: "b", legendFontSize: 9,
      catAxisLabelFontSize: 9, valAxisLabelFontSize: 9,
      valAxisMinVal: 0, valAxisMaxVal: 100, valAxisMajorUnit: 25,
      showValAxisTitle: false, showCatAxisTitle: false,
    });
  }

  // RIGHT: 2 stat cards (DP-count facts, replacing the old DP chart)
  function statCard(opts) {
    const { x, y, w, h, big, bigSize = 32, subtitle, sentence } = opts;
    s.addShape(pres.shapes.ROUNDED_RECTANGLE, {
      x, y, w, h,
      fill: { color: "F5F5F5" },
      line: { type: "none" },
      rectRadius: 0.08,
    });
    const runs = [
      { text: big, options: { fontSize: bigSize, bold: true, color: "0000CD", breakLine: true, paraSpaceAfter: 6 } },
    ];
    if (subtitle) {
      runs.push({ text: subtitle, options: { fontSize: 11, bold: true, color: "1A1A1A", breakLine: true, paraSpaceAfter: 8 } });
    }
    if (sentence) {
      runs.push({ text: sentence, options: { fontSize: 10, italic: true, color: "555555" } });
    }
    s.addText(runs, {
      x: x + 0.2, y: y + 0.10, w: w - 0.4, h: h - 0.20,
      align: "center", valign: "middle", margin: 0,
    });
  }

  // Single card — Headline rate gap (centred vertically against the chart on the left)
  statCard({
    x: 5.40, y: 1.55, w: 4.35, h: 2.55,
    big: "2.7  vs  6.0", bigSize: 40,
    subtitle: "DPs per session per participant  ·  feedback vs baseline",
    sentence: "Participants who saw feedback flagged a divergence less than half as often. 2.2× difference across the 30-session study.",
  });

  // Bottom summary (sets up Slide 27)
  s.addText(
    "But the production-weighted score is dominated by task difficulty - both groups peak at S4. The behavioural signal needs the gain-stripped view (next slide).",
    {
      x: 0.5, y: 4.85, w: 9, h: 0.50, fontSize: 12, italic: true, color: "0000CD",
      align: "center", valign: "middle", margin: 0,
    },
  );
}

// ─────────────────────────────────────────────────────────────
// Slide 27 - Strip the task signal: process discipline emerges
// ─────────────────────────────────────────────────────────────
{
  const s = pres.addSlide({ masterName: "MAIN" });
  s.addText("Strip the task signal - process discipline emerges", {
    x: 0.5, y: 0.15, w: 9, h: 0.55, fontSize: 24, bold: true, margin: 0, align: "center",
  });

  const LABELS = ["S1", "S2", "S3", "S4", "S5", "S6"];
  const FB_DARK = "0000CD";
  const BL_DARK = "B25500";

  // Custom chart title with proper subscripts (pptxgenjs's built-in chart title
  // can't render rich runs, so we render it as an addText overlay above the chart).
  s.addText(
    [
      { text: "Gain-stripped process score (W" },
      { text: "g", options: { subscript: true } },
      { text: " = W" },
      { text: "lag", options: { subscript: true } },
      { text: " = 0)" },
    ],
    {
      x: 0.5, y: 0.85, w: 9, h: 0.30,
      fontSize: 12, color: "333333", align: "center", margin: 0,
    },
  );

  // Single large gain-stripped chart, zoomed y-axis
  {
    const data = [
      { name: "Feedback mean", labels: LABELS, values: [26.7, 22.0, 38.7, 47.7, 36.7, 49.0] },
      { name: "Baseline mean", labels: LABELS, values: [22.5, 17.5, 11.0, 39.0, 16.5, 20.5] },
    ];
    s.addChart(pres.ChartType.line, data, {
      x: 1.7, y: 1.20, w: 6.6, h: 3.10,
      chartColors: [FB_DARK, BL_DARK],
      lineSize: 3,
      lineDataSymbol: "circle", lineDataSymbolSize: 7,
      showTitle: false,
      showLegend: true, legendPos: "b", legendFontSize: 10,
      catAxisLabelFontSize: 10, valAxisLabelFontSize: 10,
      valAxisMinVal: 0, valAxisMaxVal: 60, valAxisMajorUnit: 10,
      showValAxisTitle: false, showCatAxisTitle: false,
    });
  }

  // Bottom takeaway block
  {
    const runs = [
      { text: "Once cleanliness gain is removed, the score reflects ", options: {} },
      { text: "how disciplined the process was", options: { italic: true } },
      { text: " - tests run, IDE refactorings used, commits at sensible points. The feedback group climbs ", options: {} },
      { text: "+22.3", options: { bold: true, color: FB_DARK } },
      { text: " across the arc; the baseline group moves ", options: {} },
      { text: "−2.0", options: { bold: true, color: BL_DARK } },
      { text: ".", options: { breakLine: true, paraSpaceAfter: 6 } },
      { text: "n = 3 vs n = 2 - directional finding, not a hypothesis test.", options: { italic: true, color: "888888" } },
    ];
    s.addText(runs, {
      x: 0.5, y: 4.45, w: 9, h: 0.95, fontSize: 12, valign: "top", align: "center", margin: 4,
    });
  }
}

// ─────────────────────────────────────────────────────────────
// Slide 28 - Extension: tool doesn't transfer cleanly to agent traces
// ─────────────────────────────────────────────────────────────
{
  const s = pres.addSlide({ masterName: "MAIN" });
  s.addText("Extension: tool doesn't transfer cleanly to agent traces", {
    x: 0.5, y: 0.15, w: 9, h: 0.50, fontSize: 22, bold: true, margin: 0, align: "center",
  });

  s.addText(
    "48 sessions, 8 agents (Claude / GPT / Gemini × Claude Code / OpenCode / Cursor CLI), same playbook as the user study.",
    { x: 0.5, y: 0.70, w: 9, h: 0.30, fontSize: 11, italic: true, color: "555555", align: "center" }
  );

  // ── LEFT: gain-stripped score chart (group means, same scale as Slide 23) ──
  {
    const LABELS = ["S1", "S2", "S3", "S4", "S5", "S6"];
    const FB_DARK = "0000CD";
    const BL_DARK = "B25500";
    const data = [
      { name: "Feedback mean (n=6)", labels: LABELS, values: [36.7, 40.0, 40.0, 50.0, 42.0, 39.7] },
      { name: "Baseline mean (n=2)", labels: LABELS, values: [34.5, 40.0, 40.0, 50.0, 42.0, 41.0] },
    ];
    s.addChart(pres.ChartType.line, data, {
      x: 0.3, y: 1.15, w: 4.6, h: 2.95,
      chartColors: [FB_DARK, BL_DARK],
      lineSize: 2.5,
      lineDataSymbol: "circle", lineDataSymbolSize: 6,
      showTitle: true, title: "Gain-stripped score (same scale as user study)",
      titleFontSize: 11, titleColor: "333333",
      showLegend: true, legendPos: "b", legendFontSize: 9,
      catAxisLabelFontSize: 9, valAxisLabelFontSize: 9,
      valAxisMinVal: 0, valAxisMaxVal: 60, valAxisMajorUnit: 10,
      showValAxisTitle: false, showCatAxisTitle: false,
    });
  }

  // ── RIGHT: compact detector-by-kind table ──
  const HEADER_CELL = {
    bold: true, color: "000000", fill: { color: "F0F0F0" },
    align: "center", valign: "middle", margin: 0.06, fontSize: 10,
  };
  const KIND_CELL = {
    bold: true, color: "000000",
    align: "left", valign: "middle", margin: 0.06, fontSize: 11,
  };
  const COUNT_FIRED_CELL = {
    bold: true, color: "0A7D2A", fontSize: 16,
    align: "center", valign: "middle", margin: 0.06,
  };
  const COUNT_ZERO_CELL = {
    color: "999999", fontSize: 14,
    align: "center", valign: "middle", margin: 0.06,
  };
  const WHY_CELL = {
    color: "555555", fontSize: 10,
    align: "left", valign: "middle", margin: 0.06,
  };

  const rows = [
    [
      { text: "Kind", options: HEADER_CELL },
      { text: "DPs", options: HEADER_CELL },
      { text: "Why under-fires", options: HEADER_CELL },
    ],
    [
      { text: "Manual-Refactor", options: KIND_CELL },
      { text: "41", options: COUNT_FIRED_CELL },
      { text: "text edits, not IDE menu", options: WHY_CELL },
    ],
    [
      { text: "Hygiene", options: KIND_CELL },
      { text: "0", options: COUNT_ZERO_CELL },
      { text: "batched commits don't trip threshold", options: WHY_CELL },
    ],
    [
      { text: "Rework", options: KIND_CELL },
      { text: "0", options: COUNT_ZERO_CELL },
      { text: "edit bursts collapse to one", options: WHY_CELL },
    ],
    [
      { text: "Ordering", options: KIND_CELL },
      { text: "1", options: COUNT_ZERO_CELL },
      { text: "same burst-collapse", options: WHY_CELL },
    ],
  ];

  s.addTable(rows, {
    x: 5.10, y: 1.15, w: 4.65,
    colW: [1.55, 0.55, 2.55],
    rowH: 0.50,
    fontFace: "Calibri",
  });

  // ── BOTTOM: tight takeaway (single paragraph + future-work closer) ──
  {
    const runs = [
      { text: "Score is blind to the difference: ", options: { bold: true } },
      { text: "feedback ΔJ ", options: {} },
      { text: "+3.0", options: { bold: true, color: "0000CD" } },
      { text: " vs baseline ", options: {} },
      { text: "+6.5", options: { bold: true, color: "B25500" } },
      { text: ". Yet 5 of 6 agents wrote explicit \"I'll do X next session\" plans citing prior warnings - the detectors just can't see those changes happen.", options: { breakLine: true, paraSpaceAfter: 6 } },
      { text: "Scope limit by design - adapting detectors for agent traces is future work.", options: { italic: true, color: "888888" } },
    ];
    s.addText(runs, {
      x: 0.5, y: 4.30, w: 9, h: 1.20, fontSize: 12, valign: "top", align: "center", margin: 4,
    });
  }
}

// ─────────────────────────────────────────────────────────────
// Slide 29 - Conclusion
// ─────────────────────────────────────────────────────────────
{
  const s = titled("Conclusion");

  s.addText(
    "Process-aware refactoring evaluation is tractable, and a small randomised study gives directional evidence that feedback improves measured process discipline.",
    { x: 0.5, y: 1.10, w: 9, h: 0.85, fontSize: 15, italic: true, color: "0000CD", align: "center", valign: "middle" }
  );

  const BLANK = { text: " ", options: { bullet: false, breakLine: true } };
  const conclusionRuns = [
    // Bullet 1 with inline italic on "arbitrary sessions"
    { text: "A process-quality metric on ", options: { bullet: true } },
    { text: "arbitrary sessions", options: { italic: true, breakLine: true } },
    BLANK,
    { text: "A divergence-point detector & synthesiser of alternative trajectories.", options: { bullet: true, breakLine: true } },
    BLANK,
    { text: "Three datasets - 45 injection sessions, 30-session user study, 48-session agent extension.", options: { bullet: true, breakLine: true } },
    BLANK,
    { text: "A working end-to-end research prototype: IntelliJ plugin, analysis backend, and dashboard.", options: { bullet: true } },
  ];
  s.addText(conclusionRuns, { ...BODY, y: 2.10, h: 2.9 });

  // Bounded claim: what the evidence does NOT yet establish
  s.addText(
    [
      { text: "Not yet established: ", options: { bold: true } },
      { text: "externally calibrated measure of refactoring quality, statistically conclusive behavioural effect, or generalisation beyond Java / IntelliJ / human traces.", options: {} },
    ],
    {
      x: 0.5, y: 5.05, w: 9, h: 0.5,
      fontSize: 11, italic: true, color: "888888", align: "center", valign: "middle", margin: 0,
    },
  );
}

// ─────────────────────────────────────────────────────────────
// Slide 30 - Q & A
// ─────────────────────────────────────────────────────────────
{
  const s = pres.addSlide({ masterName: "MAIN" });
  s.addText("Q & A", {
    x: 0.5, y: 2.0, w: 9, h: 1.5,
    fontSize: 72, bold: true, color: "1A1A1A",
    align: "center", valign: "middle", margin: 0,
  });
}

pres.writeFile({ fileName: "slides.pptx" }).then((name) => {
  console.log("Wrote:", name);
});
