// Unstyled draft of FYP presentation slides.
// Layout is intentionally plain: title at top, bullets below, white background.
// Styling pass comes later once content is approved.

const pptxgen = require("pptxgenjs");

const pres = new pptxgen();
pres.layout = "LAYOUT_16x9"; // 10" x 5.625"
pres.author = "Ethan Hosier";
pres.title = "Beyond Before-and-After: Process-Quality Evaluation of Refactoring Sessions";

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
  const s = pres.addSlide();
  s.addText(title, TITLE);
  if (subtitle) s.addText(subtitle, SUBTITLE);
  return s;
}

// ─────────────────────────────────────────────────────────────
// Slide 1 - Title
// ─────────────────────────────────────────────────────────────
{
  const s = pres.addSlide();
  s.addText(
    [
      { text: "Beyond Before-and-After", options: { breakLine: true } },
      { text: "Process-Quality Evaluation of Refactoring Sessions" },
    ],
    { x: 0.5, y: 1.3, w: 9, h: 1.6, fontSize: 32, bold: true, align: "center", margin: 0 },
  );
  s.addText(
    "Scoring the path a developer takes through a refactoring session, and pointing to where a better path existed.",
    { x: 0.5, y: 3.1, w: 9, h: 0.9, fontSize: 18, italic: true, align: "center", color: "555555" },
  );
  s.addText("Ethan Hosier  ·  Imperial College London  ·  Department of Computing", {
    x: 0.5, y: 4.4, w: 9, h: 0.4, fontSize: 14, align: "center", color: "777777",
  });
  s.addText("Supervisor: [Robert Chatley]  ·  Second marker: [Cristian Cadar]", {
    x: 0.5, y: 4.8, w: 9, h: 0.4, fontSize: 12, align: "center", color: "777777",
  });
}

// ─────────────────────────────────────────────────────────────
// Slide 2 - The hook: same destination, different journeys
// ─────────────────────────────────────────────────────────────
{
  const s = titled("Same destination, different journeys");
  bulletText(s, [
    "Two developers refactor the same code and arrive at the same final state.",
    "Developer A: ran tests, committed checkpoints, used IDE refactorings.",
    "Developer B: broke the build twice, added then removed 80 lines, hand-edited what the IDE could do safely.",
    "Which one would you want on your team?",
    "Endpoint-only evaluation cannot tell them apart.",
  ]);
}

// ─────────────────────────────────────────────────────────────
// Slide 3 - What's missing in prior work
// ─────────────────────────────────────────────────────────────
{
  const s = titled("What's missing in prior work");
  bulletText(s, [
    "Endpoint quality metrics (CK, smells, readability): compare before vs after, ignore the path taken.",
    "Refactoring recommenders / sequence generators: produce new sequences, but do not evaluate the one the developer actually walked.",
    "Gap: no system scores an arbitrary observed refactoring session against an explicit process-quality metric, and shows where a better path was available.",
    "This is the gap the project fills.",
  ]);
}

// ─────────────────────────────────────────────────────────────
// Slide 4 - Narrowing the design space
// ─────────────────────────────────────────────────────────────
{
  const s = titled("Narrowing the design space");

  // Intro line above the design-requirement bullets
  s.addText(
    "Evaluating refactoring as a process is a wide, mostly-unexplored design space. Four requirements shaped what we built.",
    { x: 0.5, y: 1.2, w: 9, h: 0.5, fontSize: 13, italic: true, color: "555555", margin: 0 },
  );

  // Each design requirement: bold title + normal-weight body + grey reference clause
  const REF_COLOR = "888888";
  const requirements = [
    {
      title: "Quantifiable + comparable score",
      body: "one number per trajectory so sessions can be compared.",
      ref: "Builds on composite quality models (Quamoco, QMOOD), adapted from snapshot- to trajectory-level.",
    },
    {
      title: "Seamless, in-workflow capture",
      body: "zero extra developer effort to use the tool.",
      ref: "IDE-event logging is a known-feasible approach (Damevski et al., CodeWatcher).",
    },
    {
      title: "Actionable, moment-specific feedback",
      body: 'point to a specific moment AND a "what you could have done instead" alternative.',
      ref: "Inverts prior refactoring recommenders (ReSynth, Refactoring Navigator, search-based refactoring): the synthesised sequence becomes the comparison point against an observed trace.",
    },
    {
      title: "Efficient alternative synthesis",
      body: 'the state-action space over a real codebase is too large to enumerate, so we must find efficient methods of identifying "better" alternatives.',
      ref: null,
    },
  ];

  const runs = [];
  requirements.forEach((req, i) => {
    const isLast = i === requirements.length - 1;
    // Bold title - starts the bullet
    runs.push({
      text: req.title + " ",
      options: { bullet: true, bold: true, paraSpaceAfter: 6 },
    });
    // Normal-weight body on the same bullet line, with dash separator
    runs.push({
      text: "- " + req.body + (req.ref ? " " : ""),
      options: { breakLine: !req.ref, paraSpaceAfter: 6 },
    });
    // Grey reference clause (where applicable)
    if (req.ref) {
      runs.push({
        text: req.ref,
        options: { color: REF_COLOR, breakLine: !isLast, paraSpaceAfter: 6 },
      });
    }
  });

  s.addText(runs, { x: 0.5, y: 1.8, w: 9, h: 3.6, fontSize: 13, valign: "top" });
}

// ─────────────────────────────────────────────────────────────
// Slide 5 - Desired outcome (divergence diagram)
// ─────────────────────────────────────────────────────────────
{
  const s = titled("Desired outcome: a comparable counterfactual");
  bulletText(
    s,
    [
      "Given those four requirements, the concrete thing we wanted the tool to produce, for any recorded session:",
      "Solid line - the user's actual trajectory through code states.",
      "Dashed line - synthesised alternative; same start, same finish, measurably higher J(τ).",
      "Divergence point marks where the two paths split; ΔJ quantifies the gap.",
    ],
    { y: 1.2, h: 1.6 },
  );

  // Divergence-point figure (fig:divergence from introduction). Original 1002x594 (~1.69:1).
  const imgW = 4.2;
  const imgH = 2.49;
  s.addImage({
    path: "images/divergence-point.png",
    x: (10 - imgW) / 2,
    y: 2.95,
    w: imgW,
    h: imgH,
  });
}

// ─────────────────────────────────────────────────────────────
// Slide 6 - Demo (part 1): live capture
// ─────────────────────────────────────────────────────────────
{
  const s = pres.addSlide();
  s.addText("Demo (part 1): live capture", {
    x: 0.5, y: 2.0, w: 9, h: 1.0, fontSize: 44, bold: true, align: "center", margin: 0,
  });
  s.addText(
    "I'll start a short live refactoring session. The tool will analyse it in the background while we walk through the methodology.",
    { x: 0.5, y: 3.1, w: 9, h: 1.2, fontSize: 18, italic: true, align: "center", color: "555555" },
  );
}

// ─────────────────────────────────────────────────────────────
// Slide 7 - End-to-end system
// ─────────────────────────────────────────────────────────────
{
  const s = titled("What the tool does, end to end");

  // Short description bullets above the diagram
  bulletText(
    s,
    [
      "IntelliJ plugin: records every edit, refactoring, build, test, and commit during a session.",
      "Analysis backend: reconstructs the trajectory, scores it, and detects divergence points.",
      "Dashboard: surfaces where you diverged, an alternative path, how much better it scores, and why.",
    ],
    { y: 1.2, h: 1.7 },
  );

  // Three-stage pipeline diagram below the bullets, each box lists its sub-stages
  const boxY = 3.05;
  const boxW = 2.6;
  const boxH = 2.3;
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
        "Divergence synthesis",
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
  const flowLabels = ["events.jsonl + initial-src/", "analysis-report.json"];
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
      y: arrowY - 0.35,
      w: gap + 0.1,
      h: 0.3,
      fontSize: 9,
      italic: true,
      color: "555555",
      align: "center",
      margin: 0,
    });
  }
}

// ─────────────────────────────────────────────────────────────
// Slide 8 - Scoring a trajectory
// ─────────────────────────────────────────────────────────────
{
  const s = titled("Scoring a trajectory: J(τ)");

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
      y: 1.25,
      w: 9,
      h: 0.6,
      fontSize: 22,
      align: "center",
      valign: "middle",
      margin: 0,
    },
  );

  // Weighted-term list as a borderless table so weight column is right-aligned
  // and term column is left-aligned, in a proper proportional font.
  const weightCell = (sign) => ({
    bold: true,
    color: sign === "+" ? POS : NEG,
    align: "right",
    valign: "middle",
    margin: 0.06,
  });
  const opCell = { color: "555555", align: "center", valign: "middle", margin: 0.04 };
  const termCell = { color: "1A1A1A", align: "left", valign: "middle", margin: 0.06 };

  const terms = [
    ["+", 50, "cleanliness gain"],
    ["+", 11, "step-savings bonus"],
    ["−", 28, "broken build / test rate"],
    ["−", 14, "skipped-test rate"],
    ["−", 11, "manual edits the IDE could refactor"],
    ["−", 11, "intermediate-state lag"],
    ["−",  7, "commit-gap events"],
  ];

  const rows = terms.map(([sign, weight, name]) => [
    { text: sign + String(weight), options: weightCell(sign) },
    { text: "×", options: opCell },
    { text: name, options: termCell },
  ]);

  s.addTable(rows, {
    x: 2.6,
    y: 2.05,
    w: 4.8,
    colW: [0.8, 0.4, 3.6],
    rowH: 0.34,
    fontSize: 16,
    border: { type: "none" },
  });

  // Closing paren centred below
  s.addText(")", {
    x: 0.5,
    y: 4.55,
    w: 9,
    h: 0.5,
    fontSize: 22,
    align: "center",
    valign: "middle",
    margin: 0,
  });
}

// ─────────────────────────────────────────────────────────────
// Slide 9 - Cleanliness sub-score
// ─────────────────────────────────────────────────────────────
{
  const s = titled("The cleanliness sub-score");
  bulletText(s, [
    "Six equally-weighted code-quality signals.",
    "Cognitive complexity, coupling (CBO), duplication (CPD), readability, code smells (PMD), cohesion (TCC).",
    "Normalised against the trajectory's own range, so different sessions are comparable.",
    "Equal weights are justified: no trajectory-level calibration data exists, so equal weights are the least-assumptive choice.",
  ]);
}

// ─────────────────────────────────────────────────────────────
// Slide 10 - Four kinds of divergence (overview)
// ─────────────────────────────────────────────────────────────
{
  const s = titled("Four kinds of divergence");
  bulletText(s, [
    "Four categories of process waste - each fitting the four requirements: detectable from the event stream, actionable for the developer, synthesisable as a concrete alternative.",
    "Ordering - right refactorings, wrong sequence.",
    "Manual-Refactor - wrong tool (hand-edit when the IDE could safely have done it).",
    "Rework - wrong churn (added then removed).",
    "Hygiene - missing safety checkpoints (no tests, no commits).",
    "Each kind gets its own slide next, covering both detection and synthesis.",
  ]);
}

// ─────────────────────────────────────────────────────────────
// Slide 11 - Ordering: detect + synthesise
// ─────────────────────────────────────────────────────────────
{
  const s = titled("Ordering: detect + synthesise");
  bulletText(
    s,
    [
      "What: the right refactorings, performed in a suboptimal order - final state is fine, intermediate states are worse than they needed to be.",
      "Detect: find a subsequence of refactorings whose reordering still validates to the user's terminal state (canonical AST hash, formatting/comments ignored).",
      "Synthesise: build a dependency DAG over the refactorings in the window.",
      "Prefix-trie DFS over valid topological orderings - shares work across common prefixes, rolls back with git checkout on backtrack.",
    ],
    { y: 1.2, h: 1.95, fontSize: 14 },
  );

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
// Slide 12 - Manual-Refactor: detect + synthesise
// ─────────────────────────────────────────────────────────────
{
  const s = titled("Manual-Refactor: detect + synthesise");
  bulletText(
    s,
    [
      "What: developer hand-edited something the IDE could have done safely (and with its precondition checks).",
      "Detect: run RefactoringMiner on sliding commit windows; cross-check against the IDE event stream — anything RefactoringMiner finds that the IDE did not emit is a manual refactoring.",
      "Synthesise: apply the equivalent IDE refactoring, then three-way merge the user's other edits back on top.",
      "Wrap-and-patch layer reconciles minor JDT ↔ IntelliJ AST differences (e.g. static modifiers, variable liveness).",
    ],
    { y: 1.2, h: 1.85, fontSize: 13 },
  );

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
// Slide 13 - Rework: detect + synthesise
// ─────────────────────────────────────────────────────────────
{
  const s = titled("Rework: detect + synthesise");
  bulletText(
    s,
    [
      "What: code added and later removed (or vice versa) - net-zero churn, but real cost while it was there.",
      "Detect: hash normalised lines; pair add/remove events by (file, scope, content-hash).",
      "Synthesise: strip both halves from the trajectory; replay the rest of the user's edits unchanged; validate the terminal state matches.",
      "Simplest of the four synthesisers, but high recall - precision and recall both 1.00 on the injection set.",
    ],
    { y: 1.2, h: 1.85, fontSize: 13 },
  );

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
// Slide 14 - Hygiene: detect + synthesise
// ─────────────────────────────────────────────────────────────
{
  const s = titled("Hygiene: detect + synthesise");
  bulletText(s, [
    "What: long stretches of work without safety checkpoints - no test runs, no commits.",
    "Detect: 60-second windows with no test-run events; commit-gap events when commits are spaced beyond threshold.",
    "Synthesise: model an alternative cadence that inserts test-run and commit events at the right points.",
    "The alternative's J(τ) credits those checkpoints via the skip-tests and commit-gap terms.",
    "Cheap to compute, but consistently surfaces meaningful divergences in the user study.",
  ]);
}

// ─────────────────────────────────────────────────────────────
// Slide 15 - Comparable divergence magnitudes
// ─────────────────────────────────────────────────────────────
{
  const s = titled("Comparable divergence magnitudes");
  bulletText(s, [
    "Magnitude = J(τ*_final) − J(τ_final).",
    "The score gap when the alternative is substituted at the divergence point but the rest of the user's trajectory is left unchanged.",
    "Makes magnitudes comparable across all four divergence kinds.",
    "Lets the dashboard surface each divergence point alongside its score impact, so the highest-impact moments are easy to spot in the session summary.",
    "This is the number the demo will point at.",
  ]);
}

// ─────────────────────────────────────────────────────────────
// Slide 16 - Demo (part 2): results
// ─────────────────────────────────────────────────────────────
{
  const s = pres.addSlide();
  s.addText("Demo (part 2): results", {
    x: 0.5, y: 2.0, w: 9, h: 1.0, fontSize: 44, bold: true, align: "center", margin: 0,
  });
  s.addText(
    "Now the live session has been analysed: walk through the divergence points the tool found, then show a higher-scoring alternative I prepared earlier.",
    { x: 0.5, y: 3.1, w: 9, h: 1.2, fontSize: 18, italic: true, align: "center", color: "555555" },
  );
}

// ─────────────────────────────────────────────────────────────
// Slide 17 - What we wanted to evaluate (three questions, each tied to a dataset + experiment)
// ─────────────────────────────────────────────────────────────
{
  const s = titled("What we wanted to evaluate");

  const HEADER_CELL = {
    bold: true,
    color: "1A1A1A",
    fill: { color: "F0F0F0" },
    align: "left",
    valign: "middle",
    margin: 0.08,
  };
  const QUESTION_CELL = {
    bold: true,
    color: "1A1A1A",
    align: "left",
    valign: "middle",
    margin: 0.1,
  };
  const ANSWER_CELL = {
    color: "555555",
    align: "left",
    valign: "middle",
    margin: 0.1,
  };

  const rows = [
    [
      { text: "Question", options: HEADER_CELL },
      { text: "Answered by", options: HEADER_CELL },
    ],
    [
      {
        text: "Is the tool accurate? Does it detect real divergences without false positives?",
        options: QUESTION_CELL,
      },
      {
        text: "45 labelled injection sessions, scored against per-kind precision and recall (Slide 18).",
        options: ANSWER_CELL,
      },
    ],
    [
      {
        text: "Is the process score reliable? Does the ranking hold up when the weights are perturbed?",
        options: QUESTION_CELL,
      },
      {
        text: "Injection set + user-study rankable subset, used for the sensitivity sweep and ablation study (Slide 19).",
        options: ANSWER_CELL,
      },
    ],
    [
      {
        text: "Does the tool change developer behaviour? Fewer divergences over time when feedback is shown?",
        options: QUESTION_CELL,
      },
      {
        text: "30-session randomised user study, plus a 48-session agent extension as motivation for future work (Slide 20).",
        options: ANSWER_CELL,
      },
    ],
  ];

  s.addTable(rows, {
    x: 0.5,
    y: 1.4,
    w: 9,
    colW: [3.8, 5.2],
    fontSize: 13,
    border: { type: "solid", pt: 0.5, color: "CCCCCC" },
  });
}

// ─────────────────────────────────────────────────────────────
// Slide 18 - Detector precision & recall
// ─────────────────────────────────────────────────────────────
{
  const s = titled("Detector precision & recall");
  bulletText(s, [
    "Precision = 1.00 across all four divergence kinds - every detection is valid.",
    "Recall - Rework 1.00, Hygiene 1.00, Manual-Refactor 0.76, Ordering 0.40.",
    "Inter-rater agreement (Cohen's κ) -1.00 for Ordering and Manual-Refactor, 0.86 for Rework, 0.72 for Hygiene.",
    "Ordering recall gap is honest and explained: the synthesiser rejects windows it cannot safely reproduce. A scope limit, not a detector flaw.",
  ]);
}

// ─────────────────────────────────────────────────────────────
// Slide 19 - Score robustness
// ─────────────────────────────────────────────────────────────
{
  const s = titled("Score robustness");
  bulletText(s, [
    "Single-knob sensitivity sweep: scale any one weight by {0.1×–10×}, top-1 recommendation is preserved in 96.5% of user-study cases.",
    "Multi-knob Monte Carlo (200 samples): top-1 stability drops to 84.6%; mean Kendall τ = 0.586.",
    "Honest framing - stable near the chosen weights, less stable under arbitrary joint perturbations.",
    "Ablation confirms each process term contributes real signal; endpoint gain alone does not recover the ranking.",
  ]);
}

// ─────────────────────────────────────────────────────────────
// Slide 20 - User study: does feedback change behaviour?
// ─────────────────────────────────────────────────────────────
{
  const s = titled("User study: does feedback change behaviour?");
  bulletText(s, [
    "With-feedback group: 2.7 divergence points per session.",
    "No-feedback baseline: 6.0 divergence points per session -2.2× difference.",
    "Gain-stripped process-score slope across the 6-session arc -+4.47 per session with feedback, −0.40 per session without.",
    "Caveat: n=3 vs n=2 is directional evidence, not a hypothesis test.",
  ]);
}

// ─────────────────────────────────────────────────────────────
// Slide 21 - Contributions
// ─────────────────────────────────────────────────────────────
{
  const s = titled("Contributions");
  bulletText(s, [
    "A process-quality metric J(τ) combining endpoint, process, and safety signals in one principled score.",
    "A divergence-point detector with four actionable kinds, each with a per-kind synthesiser that constructs a concrete alternative trajectory.",
    "Three datasets -45 injection sessions, 30-session user study, 48-session agent extension - with reproducible analysis (Jupyter notebook reproduces every table and figure).",
    "A deployable end-to-end system: IntelliJ plugin, analysis backend, and dashboard.",
  ]);
}

// ─────────────────────────────────────────────────────────────
// Slide 22 - Closing
// ─────────────────────────────────────────────────────────────
{
  const s = pres.addSlide();
  s.addText(
    "Refactoring quality isn't just about where you end up - it's about the path you walked to get there.",
    { x: 0.5, y: 1.8, w: 9, h: 1.5, fontSize: 26, italic: true, align: "center", margin: 0 },
  );
  s.addText("This work makes that path measurable, comparable, and improvable.", {
    x: 0.5, y: 3.4, w: 9, h: 0.8, fontSize: 22, bold: true, align: "center",
  });
  s.addText("Thank you - happy to take questions.", {
    x: 0.5, y: 4.6, w: 9, h: 0.5, fontSize: 16, align: "center", color: "555555",
  });
}

pres.writeFile({ fileName: "slides.pptx" }).then((name) => {
  console.log("Wrote:", name);
});
