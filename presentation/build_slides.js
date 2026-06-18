// Unstyled draft of FYP presentation slides.
// Layout is intentionally plain: title at top, bullets below, white background.
// Styling pass comes later once content is approved.

const pptxgen = require("pptxgenjs");

const pres = new pptxgen();
pres.layout = "LAYOUT_16x9"; // 10" x 5.625"
pres.author = "Ethan Hosier";
pres.title = "FYP Final Presentation — Refactoring Trajectory Analysis";

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
// Slide 1 — Title
// ─────────────────────────────────────────────────────────────
{
  const s = pres.addSlide();
  s.addText("Evaluating Refactoring as a Process", {
    x: 0.5, y: 1.6, w: 9, h: 1.0, fontSize: 36, bold: true, align: "center", margin: 0,
  });
  s.addText(
    "Scoring the path a developer takes through a refactoring session, and pointing to where a better path existed.",
    { x: 0.5, y: 2.7, w: 9, h: 0.9, fontSize: 18, italic: true, align: "center", color: "555555" },
  );
  s.addText("Ethan Hosier  ·  Imperial College London  ·  Department of Computing", {
    x: 0.5, y: 4.4, w: 9, h: 0.4, fontSize: 14, align: "center", color: "777777",
  });
  s.addText("Supervisor: [Robert Chatley]  ·  Second marker: [Cristian Cadar]", {
    x: 0.5, y: 4.8, w: 9, h: 0.4, fontSize: 12, align: "center", color: "777777",
  });
}

// ─────────────────────────────────────────────────────────────
// Slide 2 — The hook: same destination, different journeys
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
// Slide 3 — What's missing in prior work
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
// Slide 4 — Core idea (placeholder for divergence diagram)
// ─────────────────────────────────────────────────────────────
{
  const s = titled("The core idea, visualised");
  bulletText(s, [
    "Solid line: user's trajectory through code states.",
    "Dashed line: synthesised alternative trajectory.",
    "Both end at the same final state.",
    "Mark the divergence point and the score gap ΔJ between the two final scores.",
    "[FIGURE: introduction/divergence_diagram.png — to be placed here]",
  ]);
}

// ─────────────────────────────────────────────────────────────
// Slide 5 — End-to-end system
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
      items: ["Ranks divergence points", "Explains alternative paths"],
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
// Slide 6 — Scoring a trajectory
// ─────────────────────────────────────────────────────────────
{
  const s = titled("Scoring a trajectory: J(τ)");
  bulletText(s, [
    "Process score J(τ) on a 0–100 scale, baseline 50.",
    "Positive terms — cleanliness gain (W_g = 50), step-savings bonus (W_l = 11).",
    "Process penalties — intermediate-lag (11), commit-gap (7).",
    "Safety penalties — broken build/test (28), skip-tests (14), manual-when-IDE (11).",
    "Key design choice: the broken-state penalty is sized so no single-step gain can outweigh a broken build.",
    "Behaviour preservation is non-negotiable.",
  ]);
}

// ─────────────────────────────────────────────────────────────
// Slide 7 — Cleanliness sub-score
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
// Slide 8 — Four kinds of divergence (overview)
// ─────────────────────────────────────────────────────────────
{
  const s = titled("Four kinds of divergence");
  bulletText(s, [
    "Ordering — the right refactorings, performed in a suboptimal order.",
    "Manual-Refactor — hand-edited what the IDE could do safely.",
    "Rework — added code then removed it (or vice versa); net-zero churn.",
    "Hygiene — long stretches without tests or commits.",
    "Each is actionable, and each has its own detector + synthesiser — the next four slides walk through them.",
  ]);
}

// ─────────────────────────────────────────────────────────────
// Slide 9 — Ordering: detect + synthesise
// ─────────────────────────────────────────────────────────────
{
  const s = titled("Ordering: detect + synthesise");
  bulletText(s, [
    "What: the right refactorings, performed in a suboptimal order — final state is fine, intermediate states are worse than they needed to be.",
    "Detect: find a subsequence of refactorings whose reordering still validates to the user's terminal state (canonical AST hash, formatting/comments ignored).",
    "Synthesise: build a dependency DAG over the refactorings in the window.",
    "Prefix-trie DFS over valid topological orderings — shares work across common prefixes, rolls back with git checkout on backtrack.",
    "[FIGURE: methodology-reorder-trie — to be placed here]",
  ]);
}

// ─────────────────────────────────────────────────────────────
// Slide 10 — Manual-Refactor: detect + synthesise
// ─────────────────────────────────────────────────────────────
{
  const s = titled("Manual-Refactor: detect + synthesise");
  bulletText(s, [
    "What: developer hand-edited something the IDE could have done safely (and with its precondition checks).",
    "Detect: run RefactoringMiner on sliding commit windows; cross-check against the IDE event stream.",
    "Anything RefactoringMiner finds that the IDE did not emit is a manual refactoring.",
    "Synthesise: apply the equivalent IDE refactoring, then three-way merge the user's other edits back on top.",
    "Wrap-and-patch layer reconciles minor JDT ↔ IntelliJ AST differences (e.g. static modifiers, variable liveness) so the synthesised path validates.",
  ]);
}

// ─────────────────────────────────────────────────────────────
// Slide 11 — Rework: detect + synthesise
// ─────────────────────────────────────────────────────────────
{
  const s = titled("Rework: detect + synthesise");
  bulletText(s, [
    "What: code added and later removed (or vice versa) — net-zero churn, but real cost while it was there.",
    "Detect: hash normalised lines; pair add/remove events by (file, scope, content-hash).",
    "Synthesise: strip both halves from the trajectory; replay the rest of the user's edits unchanged; validate the terminal state matches.",
    "Simplest of the four synthesisers, but high recall — precision and recall both 1.00 on the injection set.",
  ]);
}

// ─────────────────────────────────────────────────────────────
// Slide 12 — Hygiene: detect + synthesise
// ─────────────────────────────────────────────────────────────
{
  const s = titled("Hygiene: detect + synthesise");
  bulletText(s, [
    "What: long stretches of work without safety checkpoints — no test runs, no commits.",
    "Detect: 60-second windows with no test-run events; commit-gap events when commits are spaced beyond threshold.",
    "Synthesise: model an alternative cadence that inserts test-run and commit events at the right points.",
    "The alternative's J(τ) credits those checkpoints via the skip-tests and commit-gap terms.",
    "Cheap to compute, but consistently surfaces meaningful divergences in the user study.",
  ]);
}

// ─────────────────────────────────────────────────────────────
// Slide 13 — Comparable divergence magnitudes
// ─────────────────────────────────────────────────────────────
{
  const s = titled("Comparable divergence magnitudes");
  bulletText(s, [
    "Magnitude = J(τ*_final) − J(τ_final).",
    "The score gap when the alternative is substituted at the divergence point but the rest of the user's trajectory is left unchanged.",
    "Makes magnitudes comparable across all four divergence kinds.",
    "Lets the dashboard rank divergence points and surface the most impactful first.",
    "This is the number the demo will point at.",
  ]);
}

// ─────────────────────────────────────────────────────────────
// Slide 14 — Three datasets
// ─────────────────────────────────────────────────────────────
{
  const s = titled("Three datasets");
  bulletText(s, [
    "45 hand-recorded injection sessions with deliberately-injected bad behaviours (labelled ground truth).",
    "30-session randomised user study — 5 participants, 3 with-feedback vs 2 no-feedback baseline.",
    "48-session agent extension across 8 LLM agent stacks (mentioned briefly — motivates future work).",
  ]);
}

// ─────────────────────────────────────────────────────────────
// Slide 15 — Detector precision & recall
// ─────────────────────────────────────────────────────────────
{
  const s = titled("Detector precision & recall");
  bulletText(s, [
    "Precision = 1.00 across all four divergence kinds — every detection is valid.",
    "Recall — Rework 1.00, Hygiene 1.00, Manual-Refactor 0.76, Ordering 0.40.",
    "Inter-rater agreement (Cohen's κ) — 1.00 for Ordering and Manual-Refactor, 0.86 for Rework, 0.72 for Hygiene.",
    "Ordering recall gap is honest and explained: the synthesiser rejects windows it cannot safely reproduce. A scope limit, not a detector flaw.",
  ]);
}

// ─────────────────────────────────────────────────────────────
// Slide 16 — Score robustness
// ─────────────────────────────────────────────────────────────
{
  const s = titled("Score robustness");
  bulletText(s, [
    "Single-knob sensitivity sweep: scale any one weight by {0.1×–10×}, top-1 recommendation is preserved in 96.5% of user-study cases.",
    "Multi-knob Monte Carlo (200 samples): top-1 stability drops to 84.6%; mean Kendall τ = 0.586.",
    "Honest framing — stable near the chosen weights, less stable under arbitrary joint perturbations.",
    "Ablation confirms each process term contributes real signal; endpoint gain alone does not recover the ranking.",
  ]);
}

// ─────────────────────────────────────────────────────────────
// Slide 17 — User study: does feedback change behaviour?
// ─────────────────────────────────────────────────────────────
{
  const s = titled("User study: does feedback change behaviour?");
  bulletText(s, [
    "With-feedback group: 2.7 divergence points per session.",
    "No-feedback baseline: 6.0 divergence points per session — 2.2× difference.",
    "Gain-stripped process-score slope across the 6-session arc — +4.47 per session with feedback, −0.40 per session without.",
    "Caveat: n=3 vs n=2 is directional evidence, not a hypothesis test.",
  ]);
}

// ─────────────────────────────────────────────────────────────
// Slide 18 — Demo transition
// ─────────────────────────────────────────────────────────────
{
  const s = pres.addSlide();
  s.addText("Demo", {
    x: 0.5, y: 2.0, w: 9, h: 1.0, fontSize: 48, bold: true, align: "center", margin: 0,
  });
  s.addText(
    "I'll show one identified divergence point and the alternative path the tool constructed — and how much better it scores.",
    { x: 0.5, y: 3.1, w: 9, h: 1.0, fontSize: 18, italic: true, align: "center", color: "555555" },
  );
}

// ─────────────────────────────────────────────────────────────
// Slide 19 — Contributions
// ─────────────────────────────────────────────────────────────
{
  const s = titled("Contributions");
  bulletText(s, [
    "A process-quality metric J(τ) combining endpoint, process, and safety signals in one principled score.",
    "A divergence-point detector with four actionable kinds, each with a per-kind synthesiser that constructs a concrete alternative trajectory.",
    "Three datasets — 45 injection sessions, 30-session user study, 48-session agent extension — with reproducible analysis (Jupyter notebook reproduces every table and figure).",
    "A deployable end-to-end system: IntelliJ plugin, analysis backend, and dashboard.",
  ]);
}

// ─────────────────────────────────────────────────────────────
// Slide 20 — Closing
// ─────────────────────────────────────────────────────────────
{
  const s = pres.addSlide();
  s.addText(
    "Refactoring quality isn't just about where you end up — it's about the path you walked to get there.",
    { x: 0.5, y: 1.8, w: 9, h: 1.5, fontSize: 26, italic: true, align: "center", margin: 0 },
  );
  s.addText("This work makes that path measurable, comparable, and improvable.", {
    x: 0.5, y: 3.4, w: 9, h: 0.8, fontSize: 22, bold: true, align: "center",
  });
  s.addText("Thank you — happy to take questions.", {
    x: 0.5, y: 4.6, w: 9, h: 0.5, fontSize: 16, align: "center", color: "555555",
  });
}

pres.writeFile({ fileName: "slides.pptx" }).then((name) => {
  console.log("Wrote:", name);
});
