// Mock session data for the refactoring trajectory dashboard.
// A "messy" session: broken intervals, IDE refactorings, divergence & suggested better path.

const SESSION = {
  id: "sess-4f2a-e1c9",
  repo: "acme-corp/order-service",
  branch: "refactor/checkout-pipeline",
  author: "l.rashid",
  startedAt: "2026-04-16 09:41",
  durationMin: 187,
  language: "TypeScript",
  checkpointCount: 14,
  processScore: 62,     // 0-100
  prevProcessScore: 58, // for delta
};

const METRICS = [
  { id: "complexity",   label: "Complexity",       unit: "McCabe",     better: "lower",  group: "code"    },
  { id: "coupling",     label: "Coupling",         unit: "CBO",        better: "lower",  group: "code"    },
  { id: "duplication",  label: "Duplication",      unit: "%",          better: "lower",  group: "code"    },
  { id: "readability",  label: "Readability",      unit: "score",      better: "higher", group: "code"    },
  { id: "churn",        label: "Churn",            unit: "LOC/ck",     better: "lower",  group: "process" },
  { id: "process",      label: "Process Score",    unit: "/100",       better: "higher", group: "process" },
];

// 14 checkpoints. Metric values chosen to tell a story:
// - Early work reduces complexity a bit, then a bad interval breaks the build
// - Duplication spikes around checkpoints 6-8 (extraction gone wrong)
// - Recovery in the final third.
const CHECKPOINTS = [
  { i: 0,  t: "00:00", label: "c0  initial",        complexity: 48, coupling: 22, duplication: 9.1,  readability: 54, churn: 0,   process: 55, status: "pass"    },
  { i: 1,  t: "00:12", label: "c1  extract helper", complexity: 46, coupling: 22, duplication: 8.7,  readability: 56, churn: 34,  process: 58, status: "pass"    },
  { i: 2,  t: "00:28", label: "c2  rename symbols", complexity: 46, coupling: 21, duplication: 8.7,  readability: 60, churn: 18,  process: 61, status: "pass"    },
  { i: 3,  t: "00:41", label: "c3  inline method",  complexity: 44, coupling: 20, duplication: 9.4,  readability: 61, churn: 52,  process: 60, status: "fail"    },
  { i: 4,  t: "01:02", label: "c4  split module",   complexity: 50, coupling: 26, duplication: 12.2, readability: 57, churn: 188, process: 49, status: "fail"    },
  { i: 5,  t: "01:18", label: "c5  fix imports",    complexity: 49, coupling: 24, duplication: 11.8, readability: 58, churn: 41,  process: 52, status: "unknown" },
  { i: 6,  t: "01:33", label: "c6  extract class",  complexity: 44, coupling: 23, duplication: 14.6, readability: 59, churn: 96,  process: 54, status: "pass"    },
  { i: 7,  t: "01:49", label: "c7  move method",    complexity: 42, coupling: 25, duplication: 15.1, readability: 58, churn: 62,  process: 53, status: "fail"    },
  { i: 8,  t: "02:06", label: "c8  revert partial", complexity: 43, coupling: 22, duplication: 11.2, readability: 60, churn: 148, process: 56, status: "pass"    },
  { i: 9,  t: "02:22", label: "c9  introduce iface",complexity: 40, coupling: 19, duplication: 10.3, readability: 63, churn: 58,  process: 64, status: "pass"    },
  { i: 10, t: "02:38", label: "c10 parameterize",   complexity: 38, coupling: 18, duplication: 9.5,  readability: 65, churn: 31,  process: 68, status: "pass"    },
  { i: 11, t: "02:51", label: "c11 dedupe service", complexity: 37, coupling: 18, duplication: 6.8,  readability: 67, churn: 74,  process: 71, status: "pass"    },
  { i: 12, t: "03:01", label: "c12 cleanup tests",  complexity: 36, coupling: 17, duplication: 6.4,  readability: 70, churn: 22,  process: 73, status: "pass"    },
  { i: 13, t: "03:07", label: "c13 final",          complexity: 35, coupling: 17, duplication: 6.1,  readability: 72, churn: 9,   process: 76, status: "pass"    },
];

// Intervals between adjacent checkpoints: status (pass/fail/unknown) for color bands
const INTERVALS = CHECKPOINTS.slice(0, -1).map((c, idx) => {
  const next = CHECKPOINTS[idx + 1];
  // Status of interval = status of the checkpoint it lands on (broken from c3->c4, c6->c7, recovery at c8)
  const status = (
    idx === 2 ? "fail" :        // c2 -> c3
    idx === 3 ? "fail" :        // c3 -> c4
    idx === 4 ? "unknown" :     // c4 -> c5
    idx === 6 ? "fail" :        // c6 -> c7
    "pass"
  );
  return { from: c.i, to: next.i, status };
});

// Refactoring / event annotations. kind in:
//   ide-refactor | detected-refactor | event | divergence | suggestion-start
const ANNOTATIONS = [
  { id: "a1", atCheckpoint: 1,  kind: "ide-refactor",     title: "IDE: Extract Method",           detail: "CheckoutService.ts → computeDiscount()" },
  { id: "a2", atCheckpoint: 2,  kind: "ide-refactor",     title: "IDE: Rename (×6)",              detail: "identifiers renamed, 6 files touched" },
  { id: "a3", atCheckpoint: 3,  kind: "event",            title: "Build broke",                   detail: "tsc: 4 errors in order/*.ts" },
  { id: "a4", spanFrom: 3, spanTo: 5, kind: "detected-refactor", title: "Detected: Move Class", detail: "RefactoringMiner matched MoveClass over 3 checkpoints" },
  { id: "a5", atCheckpoint: 4,  kind: "event",            title: "Large churn spike",             detail: "188 LOC changed — exceeds p95 threshold" },
  { id: "a6", atCheckpoint: 6,  kind: "divergence",       title: "Divergence point",              detail: "Suggested path splits here — see alt trajectory" },
  { id: "a7", atCheckpoint: 8,  kind: "event",            title: "Partial revert",                detail: "git: reverted 2 commits on branch" },
  { id: "a8", atCheckpoint: 9,  kind: "detected-refactor",title: "Detected: Extract Interface",   detail: "IOrderSink introduced; 3 implementers" },
  { id: "a9", atCheckpoint: 11, kind: "ide-refactor",     title: "IDE: Pull Up Method",           detail: "→ BaseOrderService" },
];

// Suggested "better path" trajectory — complexity values from divergence c6 onward.
// Skips the c6→c7 break entirely and goes straight to the c9-like plateau.
const SUGGESTED_PATH = {
  fromCheckpoint: 6,
  metric: "complexity",
  points: [
    { i: 6,   v: 44 },
    { i: 7.2, v: 40 },
    { i: 8.3, v: 37 },
    { i: 9.5, v: 35 },
    { i: 11,  v: 34 },
    { i: 13,  v: 33 },
  ],
  rationale: "Applying Extract Interface before Move Method would have avoided the failing interval at c6→c7 and reduced total churn by ~34%. RefactoringMiner suggests this ordering based on dependency analysis of the touched classes.",
};

// Explanation cards keyed by checkpoint or interval id
const EXPLANATIONS = {
  // checkpoint-id keyed
  ck: {
    3: { title: "Build broke at c3",
         what: "An inline-method refactor touched a cross-module boundary without updating imports in two dependents.",
         why:  "Inline Method inside a public API surface should preface a boundary check. The tsc errors indicate the dependents still expect the original call-site shape.",
         better: "Run 'Find Usages' before inlining, or apply Safe Delete to surface callers. Alternatively, sequence this refactor after c5's import cleanup." },
    4: { title: "Churn spike & coupling regression",
         what: "Splitting the checkout module introduced 188 LOC of change and raised CBO from 20 → 26.",
         why:  "A module split done before extracting shared types pulls coupling into both halves.",
         better: "Extract interface first (what finally happened at c9). This is the divergence point the suggested path picks up from." },
    6: { title: "Divergence point",
         what: "At c6 the trajectory had recovered build health but duplication was climbing (14.6%).",
         why:  "The branch 'extract class → move method' was taken, which briefly improved complexity but re-broke the build at c7.",
         better: "The suggested path extracts the interface first (what you eventually did at c9) — skipping the c6→c7 failure entirely." },
    8: { title: "Partial revert recovers baseline",
         what: "Reverting two commits returned the build to green and shed 4.1pp of duplication.",
         why:  "Reverts aren't inherently bad — they're a rational response to the c7 regression.",
         better: "Would have been avoidable by sequencing per the suggested path." },
    11:{ title: "Sustained improvement",
         what: "Duplication dropped to 6.8%, process score crossed 70 for the first time.",
         why:  "Dedupe pass after the interface extraction landed — the dependency graph was finally clean enough for it to be a local change.",
         better: "On-path. This is the shape of a good refactoring." },
  },
  // interval-id keyed ("from-to")
  iv: {
    "3-4": { title: "Interval c3→c4 · failing (21 min)",
             what: "Build red for the full interval. Churn was 188 LOC across 12 files.",
             why:  "Long red intervals correlate with higher likelihood of revert — which is what happened at c8.",
             better: "Small, green-preserving steps. Aim for intervals under ~10 min with at least unit tests passing." },
    "6-7": { title: "Interval c6→c7 · failing (16 min)",
             what: "Second failing interval. Tests green, but type-check red.",
             why:  "This is the interval the suggested path avoids entirely.",
             better: "See divergence annotation at c6." },
  },
};

window.REFACTOR_DATA = { SESSION, METRICS, CHECKPOINTS, INTERVALS, ANNOTATIONS, SUGGESTED_PATH, EXPLANATIONS };
