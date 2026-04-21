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
  // tests: "run" (tests executed), "skipped" (no test run after change), "partial" (some suites), "none" (no tests in touched area)
  // manualIdeAble: the user hand-edited something the IDE has a safe automated refactoring for; suggestion = which one.
  { i: 0,  t: "00:00", label: "c0  initial",        complexity: 48, coupling: 22, duplication: 9.1,  readability: 54, churn: 0,   process: 55, status: "pass",    tests: "run",     manualIdeAble: null },
  { i: 1,  t: "00:12", label: "c1  extract helper", complexity: 46, coupling: 22, duplication: 8.7,  readability: 56, churn: 34,  process: 58, status: "pass",    tests: "run",     manualIdeAble: null },
  { i: 2,  t: "00:28", label: "c2  rename symbols", complexity: 46, coupling: 21, duplication: 8.7,  readability: 60, churn: 18,  process: 61, status: "pass",    tests: "skipped", manualIdeAble: { action: "Rename", why: "6 identifiers renamed by hand-edit in multiple files — IDE 'Rename' (⇧F6) would have done this atomically with reference updates." } },
  { i: 3,  t: "00:41", label: "c3  inline method",  complexity: 44, coupling: 20, duplication: 9.4,  readability: 61, churn: 52,  process: 60, status: "fail",    tests: "skipped", manualIdeAble: { action: "Inline Method", why: "Method body was copy-pasted at 3 call sites and the definition deleted — IDE 'Inline Method' (⌘⌥N) would have handled all call sites and avoided the tsc break." } },
  { i: 4,  t: "01:02", label: "c4  split module",   complexity: 50, coupling: 26, duplication: 12.2, readability: 57, churn: 188, process: 49, status: "fail",    tests: "skipped", manualIdeAble: { action: "Move Class", why: "Classes relocated to new files with manual import rewiring — IDE 'Move' (F6) tracks all imports." } },
  { i: 5,  t: "01:18", label: "c5  fix imports",    complexity: 49, coupling: 24, duplication: 11.8, readability: 58, churn: 41,  process: 52, status: "unknown", tests: "none",    manualIdeAble: { action: "Optimize Imports", why: "Hand-edited import statements — 'Optimize Imports' (⌃⌥O) sorts and dedupes automatically." } },
  { i: 6,  t: "01:33", label: "c6  extract class",  complexity: 44, coupling: 23, duplication: 14.6, readability: 59, churn: 96,  process: 54, status: "pass",    tests: "partial", manualIdeAble: null },
  { i: 7,  t: "01:49", label: "c7  move method",    complexity: 42, coupling: 25, duplication: 15.1, readability: 58, churn: 62,  process: 53, status: "fail",    tests: "skipped", manualIdeAble: { action: "Move Method", why: "Cut+paste across files leaves refs dangling — IDE 'Move Method' (F6) updates them." } },
  { i: 8,  t: "02:06", label: "c8  revert partial", complexity: 43, coupling: 22, duplication: 11.2, readability: 60, churn: 148, process: 56, status: "pass",    tests: "run",     manualIdeAble: null },
  { i: 9,  t: "02:22", label: "c9  introduce iface",complexity: 40, coupling: 19, duplication: 10.3, readability: 63, churn: 58,  process: 64, status: "pass",    tests: "run",     manualIdeAble: { action: "Extract Interface", why: "Interface written by hand and three classes manually annotated — 'Extract Interface' (⌃T) would produce and wire it up." } },
  { i: 10, t: "02:38", label: "c10 parameterize",   complexity: 38, coupling: 18, duplication: 9.5,  readability: 65, churn: 31,  process: 68, status: "pass",    tests: "partial", manualIdeAble: { action: "Change Signature", why: "New parameter added by editing the declaration and every call site — 'Change Signature' (⌘F6) handles call-site updates." } },
  { i: 11, t: "02:51", label: "c11 dedupe service", complexity: 37, coupling: 18, duplication: 6.8,  readability: 67, churn: 74,  process: 71, status: "pass",   tests: "run",     manualIdeAble: null },
  { i: 12, t: "03:01", label: "c12 cleanup tests",  complexity: 36, coupling: 17, duplication: 6.4,  readability: 70, churn: 22,  process: 73, status: "pass",    tests: "run",     manualIdeAble: null },
  { i: 13, t: "03:07", label: "c13 final",          complexity: 35, coupling: 17, duplication: 6.1,  readability: 72, churn: 9,   process: 76, status: "pass",    tests: "run",     manualIdeAble: null },
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

// Mock diffs per checkpoint — unified hunk format.
// Lines: { t: "ctx"|"add"|"del"|"hunk", a?: oldLineNo, b?: newLineNo, s: text }
function hunk(header) { return { t: "hunk", s: header }; }
function ctx(a, b, s)  { return { t: "ctx", a, b, s }; }
function add(b, s)     { return { t: "add", b, s }; }
function del(a, s)     { return { t: "del", a, s }; }

const DIFFS = {
  0: [
    { path: "src/order/CheckoutService.ts", plus: 0, minus: 0, kind: "init",
      lines: [ hunk("@@ initial snapshot @@"),
               ctx(1,1," export class CheckoutService {"),
               ctx(2,2,"   process(order: Order) {"),
               ctx(3,3,"     /* ... baseline ... */"),
               ctx(4,4,"   }"),
               ctx(5,5," }") ] },
  ],
  1: [
    { path: "src/order/CheckoutService.ts", plus: 7, minus: 3, kind: "modify",
      lines: [
        hunk("@@ -12,8 +12,12 @@ process(order)"),
        ctx(12,12,"   const subtotal = order.items.reduce((a,i)=>a+i.price,0);"),
        del(13,     "   let discount = 0;"),
        del(14,     "   if (order.coupon) discount = subtotal * 0.1;"),
        del(15,     "   if (order.vip)    discount = Math.max(discount, subtotal*0.15);"),
        add(13,     "   const discount = this.computeDiscount(order, subtotal);"),
        ctx(16,14,"   return subtotal - discount;"),
        ctx(17,15," }"),
        ctx(18,16,""),
        add(17,     " private computeDiscount(order: Order, subtotal: number) {"),
        add(18,     "   let d = 0;"),
        add(19,     "   if (order.coupon) d = subtotal * 0.1;"),
        add(20,     "   if (order.vip)    d = Math.max(d, subtotal * 0.15);"),
        add(21,     "   return d;"),
        add(22,     " }"),
      ] },
  ],
  2: [
    { path: "src/order/CheckoutService.ts", plus: 6, minus: 6, kind: "rename",
      lines: [
        hunk("@@ -12,6 +12,6 @@ process()"),
        del(12,"   const subtotal = order.items.reduce((a,i)=>a+i.price,0);"),
        add(12,"   const lineSum = order.items.reduce((a,i)=>a+i.price,0);"),
        del(13,"   const discount = this.computeDiscount(order, subtotal);"),
        add(13,"   const discount = this.computeDiscount(order, lineSum);"),
        del(14,"   return subtotal - discount;"),
        add(14,"   return lineSum - discount;"),
      ] },
    { path: "src/order/types.ts", plus: 2, minus: 2, kind: "rename",
      lines: [
        hunk("@@ -4,4 +4,4 @@ Order"),
        del(4," export interface Order { coupon?: string; vip?: boolean;"),
        add(4," export interface Order { couponCode?: string; isVip?: boolean;"),
        ctx(5,5,"   items: LineItem[];"),
        del(6,"   userId: string;"),
        add(6,"   customerId: string;"),
      ] },
  ],
  3: [
    { path: "src/order/CheckoutService.ts", plus: 5, minus: 9, kind: "inline",
      lines: [
        hunk("@@ -10,16 +10,12 @@ process()"),
        ctx(10,10," process(order: Order) {"),
        ctx(11,11,"   const lineSum = order.items.reduce((a,i)=>a+i.price,0);"),
        del(12,"   const discount = this.computeDiscount(order, lineSum);"),
        del(13,"   return lineSum - discount;"),
        del(14," }"),
        del(15,""),
        del(16," private computeDiscount(order: Order, subtotal: number) {"),
        del(17,"   let d = 0;"),
        del(18,"   if (order.couponCode) d = subtotal * 0.1;"),
        del(19,"   if (order.isVip)      d = Math.max(d, subtotal * 0.15);"),
        add(12,"   let d = 0;"),
        add(13,"   if (order.couponCode) d = lineSum * 0.1;"),
        add(14,"   if (order.isVip)      d = Math.max(d, lineSum * 0.15);"),
        add(15,"   return lineSum - d;"),
        add(16," }"),
      ] },
    { path: "src/order/OrderPipeline.ts", plus: 0, minus: 0, kind: "broken",
      lines: [
        hunk("@@ tsc error @@"),
        ctx(41,41," import { CheckoutService } from './CheckoutService';"),
        ctx(42,42," const svc = new CheckoutService();"),
        ctx(43,43," svc.computeDiscount(order, 0);  // ✖ TS2339: no such member"),
      ] },
  ],
  4: [
    { path: "src/order/CheckoutService.ts", plus: 8, minus: 72, kind: "split",
      lines: [
        hunk("@@ -1,80 +1,12 @@ split module"),
        del(1," import { applyTax, computeShipping, validateAddress, ..."),
        del(2,"   /* 60 lines of utilities */"),
        del(3,"   /* extracted below */"),
        add(1," import { Pricing } from './pricing';"),
        add(2," import { Shipping } from './shipping';"),
        ctx(4,3," export class CheckoutService {"),
        ctx(5,4,"   constructor(private p: Pricing, private s: Shipping) {}"),
        add(5,"   /* body reduced to orchestration */"),
      ] },
    { path: "src/order/pricing.ts", plus: 42, minus: 0, kind: "new",
      lines: [
        hunk("@@ new file @@"),
        add(1," export class Pricing {"),
        add(2,"   apply(order: Order, sum: number) {"),
        add(3,"     let d = 0;"),
        add(4,"     if (order.couponCode) d = sum * 0.1;"),
        add(5,"     /* … 37 more lines … */"),
        add(6,"   }"),
        add(7," }"),
      ] },
  ],
  5: [
    { path: "src/order/OrderPipeline.ts", plus: 4, minus: 4, kind: "fix",
      lines: [
        hunk("@@ -1,8 +1,8 @@ fix imports"),
        del(1," import { CheckoutService } from './CheckoutService';"),
        add(1," import { CheckoutService } from './CheckoutService';"),
        del(2," /* computeDiscount no longer exists */"),
        add(2," import { Pricing } from './pricing';"),
        ctx(3,3," const svc = new CheckoutService(new Pricing(), new Shipping());"),
        del(4," svc.computeDiscount(order, 0);"),
        add(4," new Pricing().apply(order, 0);"),
      ] },
  ],
  6: [
    { path: "src/order/pricing.ts", plus: 26, minus: 4, kind: "extract-class",
      lines: [
        hunk("@@ -1,10 +1,32 @@ Extract Class"),
        ctx(1,1," export class Pricing {"),
        del(2,"   apply(order: Order, sum: number) {"),
        add(2,"   apply(order: Order, sum: number) {"),
        add(3,"     return new DiscountRules(order).reduce(sum);"),
        add(4,"   }"),
        add(5," }"),
        add(6,""),
        add(7," export class DiscountRules {"),
        add(8,"   constructor(private order: Order) {}"),
        add(9,"   reduce(sum: number) {"),
        add(10,"    let d = 0;"),
        add(11,"    if (this.order.couponCode) d = sum * 0.1;"),
        add(12,"    if (this.order.isVip)      d = Math.max(d, sum*0.15);"),
        add(13,"    return sum - d;"),
        add(14,"  }"),
        add(15," }"),
      ] },
  ],
  7: [
    { path: "src/order/DiscountRules.ts", plus: 20, minus: 0, kind: "move",
      lines: [
        hunk("@@ new file (moved) @@"),
        add(1," import { Order } from './types';"),
        add(2," export class DiscountRules { /* moved from pricing.ts */ }"),
      ] },
    { path: "src/order/pricing.ts", plus: 1, minus: 15, kind: "broken",
      lines: [
        hunk("@@ -6,16 +6,2 @@ post-move"),
        del(6," export class DiscountRules { /* moved */ }"),
        add(6," /* DiscountRules moved to DiscountRules.ts — import missing */"),
      ] },
  ],
  8: [
    { path: "src/order/pricing.ts", plus: 2, minus: 1, kind: "revert",
      lines: [
        hunk("@@ -1,3 +1,4 @@ partial revert"),
        add(1," import { DiscountRules } from './DiscountRules';"),
        ctx(2,2," export class Pricing {"),
        del(3,"   /* broken */"),
        add(3,"   apply(o: Order, sum: number) { return new DiscountRules(o).reduce(sum); }"),
      ] },
  ],
  9: [
    { path: "src/order/IOrderSink.ts", plus: 6, minus: 0, kind: "extract-iface",
      lines: [
        hunk("@@ new file @@"),
        add(1," export interface IOrderSink {"),
        add(2,"   accept(order: Order): Promise<void>;"),
        add(3,"   reject(order: Order, reason: string): void;"),
        add(4," }"),
      ] },
    { path: "src/order/CheckoutService.ts", plus: 3, minus: 2, kind: "implement",
      lines: [
        hunk("@@ -3,5 +3,6 @@"),
        del(3," export class CheckoutService {"),
        add(3," export class CheckoutService implements IOrderSink {"),
        ctx(4,4,"   constructor(private p: Pricing, private s: Shipping) {}"),
        add(5,"   accept(o: Order) { return this.p.apply(o, 0), Promise.resolve(); }"),
      ] },
  ],
  10: [
    { path: "src/order/pricing.ts", plus: 4, minus: 2, kind: "param",
      lines: [
        hunk("@@ -2,5 +2,7 @@ parameterize"),
        del(2,"   apply(o: Order, sum: number) {"),
        add(2,"   apply(o: Order, sum: number, rules: DiscountRules = new DiscountRules(o)) {"),
        ctx(3,3,"     return rules.reduce(sum);"),
        del(4,"   }"),
        add(4,"   }"),
      ] },
  ],
  11: [
    { path: "src/order/DiscountRules.ts", plus: 2, minus: 8, kind: "dedupe",
      lines: [
        hunk("@@ -10,10 +10,2 @@ dedupe"),
        del(10,"   reduceCoupon(s:number){ return this.order.couponCode? s*0.1:0; }"),
        del(11,"   reduceVip(s:number){ return this.order.isVip? s*0.15:0; }"),
        del(12,"   reduceAll(s:number){ /* dup of reduce() */ }"),
        add(10,"   // consolidated into reduce()"),
      ] },
  ],
  12: [
    { path: "test/order/checkout.spec.ts", plus: 6, minus: 3, kind: "tests",
      lines: [
        hunk("@@ -40,6 +40,9 @@"),
        del(40,"   it('discounts vip',()=>{ /* old */ });"),
        add(40,"   it('applies vip discount at 15%',()=>{"),
        add(41,"     const svc = new CheckoutService(new Pricing(), new Shipping());"),
        add(42,"     expect(svc.accept(vipOrder)).resolves.toBeUndefined();"),
        add(43,"   });"),
      ] },
  ],
  13: [
    { path: "src/order/CheckoutService.ts", plus: 1, minus: 2, kind: "cleanup",
      lines: [
        hunk("@@ final @@"),
        del(9,"   // TODO: revisit"),
        del(10,"   // FIXME"),
        add(9,"   // stable"),
      ] },
  ],
};

window.REFACTOR_DATA = { SESSION, METRICS, CHECKPOINTS, INTERVALS, ANNOTATIONS, SUGGESTED_PATH, EXPLANATIONS, DIFFS };
