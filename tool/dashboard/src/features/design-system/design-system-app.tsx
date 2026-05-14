/*
 * Design-system page. Mounted at `/design-system` (see main.tsx).
 * Shows every primitive and every variant side-by-side so we can
 * iterate on look & feel without the real dashboard state.
 */

import { CheckIcon, FileIcon, HelpCircleIcon, XIcon, ZapIcon } from "lucide-react"
import { parsePatchFiles } from "@pierre/diffs"
import { DataList, DataListRow } from "@/components/data-list"
import { CodeSmellCard } from "@/components/code-smell-card"
import { FileDiffCard } from "@/components/file-diff-card"
import { FilterChip } from "@/components/filter-chip"
import { LegendSwatch } from "@/components/legend-swatch"
import { Meter } from "@/components/meter"
import { PitfallCallout } from "@/components/pitfall-callout"
import { AnnotationItem } from "@/components/annotation-item"
import { AnnotationList } from "@/components/annotation-list"
import { MetricTile } from "@/components/metric-tile"
import { ProcessScoreBreakdownContent } from "@/components/process-score-breakdown"
import type { ProcessScoreBreakdown } from "@/data/types"
import { RailSection } from "@/components/rail-section"
import { ScorePill } from "@/components/score-pill"
import { Sparkline } from "@/components/sparkline"
import { StatusDot } from "@/components/status-dot"
import { StatusRow } from "@/components/status-row"
import { Text } from "@/components/text"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Checkbox } from "@/components/ui/checkbox"
import { HoverCard, HoverCardContent, HoverCardTrigger } from "@/components/ui/hover-card"
import { ScrollArea } from "@/components/ui/scroll-area"
import { Separator } from "@/components/ui/separator"
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/tooltip"
import { ColorSwatch } from "@/features/design-system/color-swatch"
import { DesignSystemPage, DesignSystemSection } from "@/features/design-system/design-system-section"
import { TextSample } from "@/features/design-system/text-sample"
import { TokenCaption } from "@/features/design-system/token-caption"
import { TokenRow } from "@/features/design-system/token-row"
import { VariantCell } from "@/features/design-system/variant-cell"

// Hand-tuned breakdown that hits every contribution row in both signs
// so the showcase exercises the full layout (positive cleanliness gain,
// each penalty firing). Numbers chosen to roughly match a mid-trajectory
// session — not a real computation.
const SAMPLE_PROCESS_BREAKDOWN: ProcessScoreBreakdown = {
  total: 62,
  baseline: 50,
  contributions: [
    {
      id: "cleanliness",
      label: "Cleanliness gain",
      points: 12.4,
      detail: "up 0.35 from c0 baseline",
    },
    {
      id: "broken",
      label: "Broken checkpoints",
      points: -3,
      detail: "2 of 13 checkpoints broken (15%)",
    },
    {
      id: "smells",
      label: "Smell load (time-average)",
      points: -1.2,
      detail: "avg 0.6 open weight per checkpoint (currently 4 open; 8 introduced, 4 resolved cumulatively)",
    },
    {
      id: "skipTests",
      label: "Tests skipped after refactor",
      points: -2.5,
      detail: "3 of 5 refactoring steps not followed by tests",
    },
    {
      id: "manualIde",
      label: "Manual when IDE could refactor",
      points: -1.6,
      detail: "2 of 4 IDE-relevant steps done manually",
    },
  ],
  clamped: false,
}

const SURFACES = [
  ["bg", "shell"],
  ["bg-1", "card / panel"],
  ["bg-2", "muted / hover"],
  ["bg-3", "deepest well"],
] as const

const TEXT_TIERS = [
  ["fg", "primary text"],
  ["fg-2", "secondary text"],
  ["fg-3", "muted text"],
  ["fg-4", "disabled / hints"],
] as const

const BRAND = [
  ["brand", "mint"],
  ["brand-2", "blue"],
  ["brand-3", "violet"],
  ["brand-4", "amber"],
  ["brand-5", "cyan"],
] as const

const STATUS = [
  ["good", "pass"],
  ["bad", "fail"],
  ["warn", "warning"],
  ["unknown", "unknown"],
] as const

const RADII = [
  ["sm", "4px"],
  ["md", "6px"],
  ["lg", "8px"],
] as const

const BUTTON_VARIANTS = ["default", "secondary", "outline", "ghost", "destructive", "link"] as const
const BUTTON_SIZES = ["xs", "sm", "default", "lg"] as const
const BADGE_VARIANTS = ["default", "secondary", "outline", "ghost", "destructive"] as const
const BADGE_STATUS_VARIANTS = ["success", "danger", "warning", "neutral"] as const
const CHECKBOX_TONES = ["brand", "brand-2", "brand-3", "brand-4", "brand-5", "fg"] as const
const STATUS_DOT_TONES = ["success", "danger", "warning", "neutral"] as const
const TOOLTIP_SURFACES = ["bg-1", "bg-2", "bg-3", "inverted"] as const

// Hand-crafted multi-file Java patch covering modify + new file + rename —
// the three kinds a FileDiffCard renders distinctly.
const SAMPLE_PATCH = `diff --git a/src/main/java/com/example/order/CheckoutService.java b/src/main/java/com/example/order/CheckoutService.java
--- a/src/main/java/com/example/order/CheckoutService.java
+++ b/src/main/java/com/example/order/CheckoutService.java
@@ -1,12 +1,16 @@
 package com.example.order;

+import java.util.List;
+
 public class CheckoutService {
-    public double computeTotal(List<Item> items) {
-        double sum = 0;
-        for (Item it : items) sum += it.getPrice();
-        return sum;
+    /** Compute the order total, applying an optional discount factor. */
+    public double computeTotal(List<Item> items, double discount) {
+        double subtotal = 0;
+        for (Item it : items) subtotal += it.getPrice();
+        return subtotal * (1 - discount);
     }
 }
diff --git a/src/main/java/com/example/order/DiscountCodes.java b/src/main/java/com/example/order/DiscountCodes.java
new file mode 100644
--- /dev/null
+++ b/src/main/java/com/example/order/DiscountCodes.java
@@ -0,0 +1,11 @@
+package com.example.order;
+
+public final class DiscountCodes {
+    private DiscountCodes() {}
+
+    public static double fractionFor(String code) {
+        if ("SPRING10".equals(code)) return 0.10;
+        if ("WELCOME".equals(code)) return 0.05;
+        return 0.0;
+    }
+}
diff --git a/src/main/java/com/example/order/cart.java b/src/main/java/com/example/order/Cart.java
similarity index 85%
rename from src/main/java/com/example/order/cart.java
rename to src/main/java/com/example/order/Cart.java
--- a/src/main/java/com/example/order/cart.java
+++ b/src/main/java/com/example/order/Cart.java
@@ -1,6 +1,6 @@
 package com.example.order;

-public class cart {
+public class Cart {
     public void add(Item item) {}
     public void remove(Item item) {}
 }
`

const SAMPLE_PATCH_FILES = parsePatchFiles(SAMPLE_PATCH, "design-system-sample")[0]?.files ?? []

// Synthetic mini unified-diff strings shaped exactly like what
// `PmdRunner.loadSnippet` will emit: one hunk, all-context lines, with
// the absolute line range in the `@@` header so the renderer shows the
// right line numbers without us needing to ship full file contents.
const SAMPLE_SMELL_FILE = "src/main/java/org/example/OrderPricingService.java"

function buildSamplePatch(file: string, startLine: number, lines: string[]): string {
  const body = lines.map((l) => ` ${l}`).join("\n")
  return (
    [
      `diff --git a/${file} b/${file}`,
      `--- a/${file}`,
      `+++ b/${file}`,
      `@@ -${startLine},${lines.length} +${startLine},${lines.length} @@`,
      body,
    ].join("\n") + "\n"
  )
}

const SAMPLE_SMELL_PATCH_POINT = buildSamplePatch(SAMPLE_SMELL_FILE, 39, [
  "  public Money priceFor(Order o) {",
  '    if (o == null) throw new NullPointerException();',
  "    var total = Money.ZERO;",
  "    for (var l : o.lines()) {",
  "      if (l.discounted()) {",
  "        total = total.plus(l.discountAmount());",
  "      }",
  "    }",
])

const SAMPLE_SMELL_PATCH_RANGE = buildSamplePatch(SAMPLE_SMELL_FILE, 39, [
  "  public Money priceFor(Order o) {",
  '    if (o == null) throw new NullPointerException();',
  "    var total = Money.ZERO;",
  "    for (var l : o.lines()) {",
  "      if (l.discounted()) {",
  "        total = total.plus(l.discountAmount());",
  "      } else if (l.isGift()) {",
  "        total = total.plus(Money.ZERO);",
  "      }",
  "    }",
  "    return total;",
])

const TEXT_VARIANTS = [
  ["display", "Chart toolbar title · 17 sans semibold"],
  ["heading", "Detail-panel sub-heading · 15 sans semibold"],
  ["body", "Default prose / label · 13 sans"],
  ["bodySm", "Smaller prose / tooltip · 11.5 sans"],
  ["caption", "Muted inline caption · 11 sans"],
  ["eyebrow", "SECTION TITLE · 10.5 MONO UPPER"],
  ["mono", "const x = 42  · 12 mono"],
  ["monoStat", "72.4 · 17 mono semibold"],
  ["monoCaption", "wmc · 10 mono"],
  ["monoTiny", "t+03:42 · 9.5 mono"],
] as const

export function DesignSystemApp() {
  return (
    <TooltipProvider>
      <DesignSystemPage
        title="Design system"
        description={
          <>
            Tokens + shadcn primitives + app primitives wired to the IntelliJ-dark palette. See{" "}
            <code className="font-mono">PLAN.md</code> steps 1–3.
          </>
        }
      >
        <DesignSystemSection title="Surfaces">
          {SURFACES.map(([token, label]) => (
            <TokenRow key={token} visual={<ColorSwatch token={token} />}>
              <TokenCaption token={token} label={label} />
            </TokenRow>
          ))}
        </DesignSystemSection>

        <DesignSystemSection title="Text">
          {TEXT_TIERS.map(([token, label]) => (
            <TokenRow key={token} visual={<TextSample token={token}>Aa</TextSample>}>
              <TokenCaption token={token} label={label} />
            </TokenRow>
          ))}
        </DesignSystemSection>

        <DesignSystemSection title="Brand">
          {BRAND.map(([token, label]) => (
            <TokenRow key={token} visual={<ColorSwatch token={token} />}>
              <TokenCaption token={token} label={label} />
            </TokenRow>
          ))}
        </DesignSystemSection>

        <DesignSystemSection title="Status">
          {STATUS.map(([token, label]) => (
            <TokenRow key={token} visual={<ColorSwatch token={token} />}>
              <TokenCaption token={token} label={label} />
            </TokenRow>
          ))}
        </DesignSystemSection>

        <DesignSystemSection title="Typography · families">
          <TokenRow visual={<TextSample family="sans">The quick brown fox</TextSample>}>
            <TokenCaption token="font-sans" label="default body" />
          </TokenRow>
          <TokenRow visual={<TextSample family="mono">const x = 42;</TextSample>}>
            <TokenCaption token="font-mono" label="code / numerics" />
          </TokenRow>
        </DesignSystemSection>

        <DesignSystemSection title="Typography · Text variants" layout="stack">
          {TEXT_VARIANTS.map(([variant, sample]) => (
            <VariantCell key={variant} label={variant}>
              <Text
                variant={variant as Parameters<typeof Text>[0]["variant"]}
                tone="fg-2"
              >
                {sample}
              </Text>
            </VariantCell>
          ))}
        </DesignSystemSection>

        <DesignSystemSection title="Radii">
          {RADII.map(([radius, px]) => (
            <TokenRow
              key={radius}
              visual={<ColorSwatch token="bg-2" size="sm" radius={radius} />}
            >
              <TokenCaption token={`radius-${radius}`} label={px} />
            </TokenRow>
          ))}
        </DesignSystemSection>

        <DesignSystemSection title="Button · variants" layout="showcase">
          {BUTTON_VARIANTS.map((variant) => (
            <VariantCell key={variant} label={variant}>
              <Button variant={variant}>Click me</Button>
            </VariantCell>
          ))}
        </DesignSystemSection>

        <DesignSystemSection title="Button · sizes" layout="showcase">
          {BUTTON_SIZES.map((size) => (
            <VariantCell key={size} label={size}>
              <Button size={size}>Click me</Button>
            </VariantCell>
          ))}
          <VariantCell label="icon">
            <Button size="icon" aria-label="icon">
              <span aria-hidden>✓</span>
            </Button>
          </VariantCell>
          <VariantCell label="disabled">
            <Button disabled>Disabled</Button>
          </VariantCell>
        </DesignSystemSection>

        <DesignSystemSection title="Badge · variants" layout="showcase">
          {BADGE_VARIANTS.map((variant) => (
            <VariantCell key={variant} label={variant}>
              <Badge variant={variant}>badge</Badge>
            </VariantCell>
          ))}
        </DesignSystemSection>

        <DesignSystemSection title="Badge · status tones" layout="showcase">
          <VariantCell label="success · build pass">
            <Badge variant="success">
              <CheckIcon />
              build pass
            </Badge>
          </VariantCell>
          <VariantCell label="danger · build fail">
            <Badge variant="danger">
              <XIcon />
              build fail
            </Badge>
          </VariantCell>
          <VariantCell label="warning">
            <Badge variant="warning">flaky</Badge>
          </VariantCell>
          <VariantCell label="neutral · unknown">
            <Badge variant="neutral">
              <HelpCircleIcon />
              unknown
            </Badge>
          </VariantCell>
          {BADGE_STATUS_VARIANTS.map((variant) => (
            <VariantCell key={`${variant}-plain`} label={`${variant} · plain`}>
              <Badge variant={variant}>{variant}</Badge>
            </VariantCell>
          ))}
        </DesignSystemSection>

        <DesignSystemSection title="Checkbox · tones" layout="showcase">
          {CHECKBOX_TONES.map((tone) => (
            <VariantCell key={tone} label={`${tone} · checked`}>
              <Checkbox tone={tone} defaultChecked />
            </VariantCell>
          ))}
          <VariantCell label="unchecked">
            <Checkbox />
          </VariantCell>
          <VariantCell label="disabled">
            <Checkbox disabled />
          </VariantCell>
          <VariantCell label="checked + disabled">
            <Checkbox defaultChecked disabled />
          </VariantCell>
        </DesignSystemSection>

        <DesignSystemSection title="StatusDot" layout="showcase">
          {STATUS_DOT_TONES.map((tone) => (
            <VariantCell key={tone} label={tone}>
              <div className="flex items-center gap-2">
                <StatusDot tone={tone} />
                <StatusDot tone={tone} size="md" />
              </div>
            </VariantCell>
          ))}
        </DesignSystemSection>

        <DesignSystemSection title="Sparkline" layout="showcase">
          <VariantCell label="muted">
            <Sparkline values={[3, 5, 4, 6, 8, 7, 10, 9, 12]} />
          </VariantCell>
          <VariantCell label="brand (active)">
            <Sparkline values={[3, 5, 4, 6, 8, 7, 10, 9, 12]} tone="brand" />
          </VariantCell>
          <VariantCell label="brand-2">
            <Sparkline values={[12, 9, 10, 7, 8, 6, 4, 5, 3]} tone="brand-2" />
          </VariantCell>
          <VariantCell label="brand-3 · larger">
            <Sparkline
              values={[5, 7, 6, 8, 6, 7, 9, 8, 10]}
              tone="brand-3"
              width={120}
              height={28}
            />
          </VariantCell>
        </DesignSystemSection>

        <DesignSystemSection title="ScorePill" layout="showcase">
          <VariantCell label="positive delta">
            <ScorePill label="Process score" value={72} delta={4} />
          </VariantCell>
          <VariantCell label="negative delta">
            <ScorePill label="Process score" value={58} delta={-6} />
          </VariantCell>
          <VariantCell label="no delta">
            <ScorePill label="Readability" value={83} />
          </VariantCell>
          <VariantCell label="size=sm">
            <ScorePill label="Coverage" value={91} total={100} delta={2} size="sm" />
          </VariantCell>
        </DesignSystemSection>

        <DesignSystemSection title="RailSection" layout="showcase">
          <VariantCell label="with description + items">
            <div className="border-border bg-bg-1 w-60 rounded-md border">
              <RailSection title="PRIMARY METRIC">
                <div className="text-fg-2 px-3 pb-2 text-xs">Complexity · wmc</div>
                <div className="text-fg-2 px-3 pb-2 text-xs">Coupling · cbo</div>
              </RailSection>
              <RailSection
                title="OVERLAY (MAX 2)"
                description="Dashed secondary lines on the graph"
              >
                <div className="text-fg-3 px-3 pb-2 text-xs">Readability</div>
              </RailSection>
            </div>
          </VariantCell>
        </DesignSystemSection>

        <DesignSystemSection title="MetricTile" layout="showcase">
          <VariantCell label="no delta">
            <div className="w-48">
              <MetricTile label="Complexity" value={12.4} unit="wmc" />
            </div>
          </VariantCell>
          <VariantCell label="lower-is-better · improved (−)">
            <div className="w-48">
              <MetricTile
                label="Duplication"
                value={18.7}
                unit="%"
                delta={-2.3}
              />
            </div>
          </VariantCell>
          <VariantCell label="lower-is-better · regressed (+)">
            <div className="w-48">
              <MetricTile
                label="Duplication"
                value={21.0}
                unit="%"
                delta={+2.3}
              />
            </div>
          </VariantCell>
          <VariantCell label="higher-is-better · improved (+)">
            <div className="w-48">
              <MetricTile
                label="Readability"
                value={83}
                unit="score"
                delta={+5}
                better="higher"
              />
            </div>
          </VariantCell>
          <VariantCell label="higher-is-better · regressed (−)">
            <div className="w-48">
              <MetricTile
                label="Readability"
                value={73}
                unit="score"
                delta={-5}
                better="higher"
              />
            </div>
          </VariantCell>
          <VariantCell label="with hoverContent · process-score breakdown">
            <div className="w-48">
              <MetricTile
                label="Process Score"
                value={62}
                unit="/100"
                delta={+12}
                better="higher"
                hoverContent={
                  <ProcessScoreBreakdownContent
                    breakdown={SAMPLE_PROCESS_BREAKDOWN}
                  />
                }
              />
            </div>
          </VariantCell>
        </DesignSystemSection>

        <DesignSystemSection title="AnnotationItem" layout="showcase">
          <VariantCell label="ide-refactor">
            <div className="w-72">
              <AnnotationItem
                annotation={{
                  id: "ds-1",
                  kind: "ide-refactor",
                  title: "IDE: Extract Method",
                  detail: "CheckoutService.ts → computeDiscount()",
                }}
              />
            </div>
          </VariantCell>
          <VariantCell label="detected-refactor">
            <div className="w-72">
              <AnnotationItem
                annotation={{
                  id: "ds-2",
                  kind: "detected-refactor",
                  title: "Detected: Move Class",
                  detail: "RefactoringMiner matched MoveClass over 3 checkpoints",
                }}
              />
            </div>
          </VariantCell>
          <VariantCell label="event">
            <div className="w-72">
              <AnnotationItem
                annotation={{
                  id: "ds-3",
                  kind: "event",
                  title: "Build broke",
                  detail: "passing at previous checkpoint → failing here, 24 LOC changed",
                }}
              />
            </div>
          </VariantCell>
          <VariantCell label="divergence">
            <div className="w-72">
              <AnnotationItem
                annotation={{
                  id: "ds-4",
                  kind: "divergence",
                  title: "Divergence point",
                  detail: "Suggested path splits here — see alt trajectory",
                }}
              />
            </div>
          </VariantCell>
        </DesignSystemSection>

        <DesignSystemSection title="AnnotationList" layout="stack">
          <VariantCell label="populated">
            <div className="w-96">
              <AnnotationList
                annotations={[
                  {
                    id: "dsl-1",
                    kind: "ide-refactor",
                    title: "IDE: Rename (×6)",
                    detail: "identifiers renamed, 6 files touched",
                  },
                  {
                    id: "dsl-2",
                    kind: "detected-refactor",
                    title: "Detected: Extract Interface",
                    detail: "IOrderSink introduced; 3 implementers",
                  },
                  {
                    id: "dsl-3",
                    kind: "event",
                    title: "Complexity spike",
                    detail: "+8wmc since previous checkpoint",
                  },
                ]}
              />
            </div>
          </VariantCell>
          <VariantCell label="empty">
            <div className="w-96">
              <AnnotationList annotations={[]} />
            </div>
          </VariantCell>
        </DesignSystemSection>

        <DesignSystemSection title="StatusRow" layout="showcase">
          <VariantCell label="build pass · tests pass">
            <StatusRow build="pass" tests="pass" />
          </VariantCell>
          <VariantCell label="build pass · tests fail">
            <StatusRow build="pass" tests="fail" />
          </VariantCell>
          <VariantCell label="build fail · tests unknown">
            <StatusRow build="fail" tests="unknown" />
          </VariantCell>
        </DesignSystemSection>

        <DesignSystemSection title="Meter · rainbow bar" layout="showcase">
          <VariantCell label="lower is better · 20%">
            <div className="w-40">
              <Meter value={0.2} better="lower" />
            </div>
          </VariantCell>
          <VariantCell label="lower is better · 70%">
            <div className="w-40">
              <Meter value={0.7} better="lower" />
            </div>
          </VariantCell>
          <VariantCell label="higher is better · 70%">
            <div className="w-40">
              <Meter value={0.7} better="higher" />
            </div>
          </VariantCell>
          <VariantCell label="size=md">
            <div className="w-40">
              <Meter value={0.5} better="lower" size="md" />
            </div>
          </VariantCell>
        </DesignSystemSection>

        <DesignSystemSection title="DataList · files touched" layout="showcase">
          <VariantCell label="flex rows · icon + path + churn">
            <DataList className="w-[360px]">
              {[
                ["src/order/CheckoutService.ts", 34, 11],
                ["src/order/computeDiscount.ts", 12, 3],
                ["test/order/checkout.spec.ts", 7, 2],
              ].map(([path, added, removed]) => (
                <DataListRow key={path as string} className="flex items-center gap-2">
                  <FileIcon className="text-fg-4 size-3" />
                  <Text variant="mono" tone="fg-2" className="flex-1 truncate">
                    {path}
                  </Text>
                  <Text variant="mono" tone="good">+{added}</Text>
                  <Text variant="mono" tone="bad">−{removed}</Text>
                </DataListRow>
              ))}
            </DataList>
          </VariantCell>
        </DesignSystemSection>

        <DesignSystemSection title="DataList · metric deltas" layout="showcase">
          <VariantCell label="grid rows · label + from → to (delta)">
            <DataList className="w-[360px]">
              {[
                ["Complexity", 18.2, 14.1, "lower"],
                ["Coupling", 6.4, 7.2, "lower"],
                ["Duplication", 11.0, 9.3, "lower"],
                ["Readability", 72, 80, "higher"],
              ].map(([label, from, to, better]) => {
                const diff = (to as number) - (from as number)
                const improved = better === "lower" ? diff < 0 : diff > 0
                const tone = diff === 0 ? "fg-3" : improved ? "good" : "bad"
                return (
                  <DataListRow
                    key={label as string}
                    className="grid grid-cols-[1fr_auto_auto_auto] items-center gap-2.5"
                  >
                    <Text variant="body" tone="fg-2">{label}</Text>
                    <Text variant="mono" tone="fg-4">{from}</Text>
                    <Text tone="fg-4">→</Text>
                    <Text variant="mono" tone={tone} className="w-14 text-right">
                      {to}{" "}
                      <span className="opacity-65">
                        ({diff > 0 ? "+" : ""}{diff.toFixed(1)})
                      </span>
                    </Text>
                  </DataListRow>
                )
              })}
            </DataList>
          </VariantCell>
        </DesignSystemSection>

        <DesignSystemSection title="FilterChip" layout="showcase">
          <VariantCell label="default">
            <FilterChip>zoom · fit</FilterChip>
          </VariantCell>
          <VariantCell label="active">
            <FilterChip active>x · checkpoint</FilterChip>
          </VariantCell>
        </DesignSystemSection>

        <DesignSystemSection title="LegendSwatch" layout="showcase">
          <VariantCell label="solid line">
            <LegendSwatch label="primary · complexity">
              <span className="bg-brand inline-block h-[2px] w-[18px]" />
            </LegendSwatch>
          </VariantCell>
          <VariantCell label="dashed stub">
            <LegendSwatch label="duplication">
              <span className="inline-flex w-[18px] items-center justify-between">
                <span className="bg-brand-3 h-[2px] w-[5px]" />
                <span className="bg-brand-3 h-[2px] w-[5px]" />
                <span className="bg-brand-3 h-[2px] w-[5px]" />
              </span>
            </LegendSwatch>
          </VariantCell>
          <VariantCell label="status square">
            <LegendSwatch label="build pass">
              <span className="bg-good/80 inline-block size-2.5 rounded-sm" />
            </LegendSwatch>
          </VariantCell>
        </DesignSystemSection>

        <DesignSystemSection title="Separator" layout="showcase">
          <VariantCell label="horizontal">
            <div className="w-48">
              <Separator />
            </div>
          </VariantCell>
          <VariantCell label="vertical">
            <div className="flex h-8 items-center">
              <span className="text-fg-2">left</span>
              <Separator orientation="vertical" className="mx-3" />
              <span className="text-fg-2">right</span>
            </div>
          </VariantCell>
        </DesignSystemSection>

        <DesignSystemSection title="HoverCard · surfaces" layout="showcase">
          {TOOLTIP_SURFACES.map((surface) => (
            <VariantCell key={surface} label={surface}>
              <HoverCard openDelay={120} closeDelay={80}>
                <HoverCardTrigger asChild>
                  <button
                    type="button"
                    className="text-fg-2 hover:text-fg cursor-help font-mono text-xs underline decoration-dotted underline-offset-4"
                  >
                    How it&apos;s calculated
                  </button>
                </HoverCardTrigger>
                <HoverCardContent surface={surface} className="w-72">
                  <Text as="div" variant="eyebrow" tone="fg-4">
                    Cognitive Complexity
                  </Text>
                  <Text as="p" variant="bodySm" tone="fg-2" className="mt-1.5">
                    Mean of PMD&apos;s per-method cognitive complexity, restricted to
                    methods whose file is in the trajectory-touched set. Penalises
                    nesting and breaks of linear flow rather than mere branch count.
                  </Text>
                  <div className="border-border bg-bg-2 mt-2 rounded-sm border px-2 py-1.5">
                    <Text as="code" variant="mono" tone="fg">
                      mean ( cognitive complexity per method ∈ touched files )
                    </Text>
                  </div>
                </HoverCardContent>
              </HoverCard>
            </VariantCell>
          ))}
        </DesignSystemSection>

        <DesignSystemSection title="Tooltip · surfaces" layout="showcase">
          {TOOLTIP_SURFACES.map((surface) => (
            <VariantCell key={surface} label={surface}>
              <Tooltip>
                <TooltipTrigger asChild>
                  <Button variant="outline">Hover · {surface}</Button>
                </TooltipTrigger>
                <TooltipContent surface={surface}>surface=&quot;{surface}&quot;</TooltipContent>
              </Tooltip>
            </VariantCell>
          ))}
        </DesignSystemSection>

        <DesignSystemSection title="Scroll area" layout="showcase">
          <VariantCell label="vertical · 160px">
            <ScrollArea className="border-border bg-bg-1 h-40 w-60 rounded-md border p-3">
              <div className="flex flex-col gap-2 font-mono text-xs">
                {Array.from({ length: 30 }, (_, i) => (
                  <span key={i}>line {String(i + 1).padStart(2, "0")}</span>
                ))}
              </div>
            </ScrollArea>
          </VariantCell>
        </DesignSystemSection>

        <DesignSystemSection title="FileDiffCard" layout="stack">
          <VariantCell label="multi-file patch · modify + new + rename · width=640">
            <div className="flex flex-col gap-2">
              {SAMPLE_PATCH_FILES.map((file, i) => (
                <FileDiffCard
                  key={file.name + i}
                  fileDiff={file}
                  defaultOpen={i === 0}
                  width={640}
                />
              ))}
            </div>
          </VariantCell>
        </DesignSystemSection>

        <DesignSystemSection title="CodeSmellCard" layout="stack">
          <VariantCell label="point violation · priority 3 · open · width=640">
            <CodeSmellCard
              width={640}
              rule="AvoidLiteralsInIfCondition"
              ruleSet="error prone"
              priority={3}
              file={SAMPLE_SMELL_FILE}
              beginLine={42}
              endLine={42}
              message="Avoid using literals in if statements; assign the constant to a named variable so the intent is explicit."
              patch={SAMPLE_SMELL_PATCH_POINT}
              cacheKey="design-system-smell-point"
            />
          </VariantCell>
          <VariantCell label="range violation · priority 3 · open">
            <CodeSmellCard
              width={640}
              rule="CognitiveComplexity"
              ruleSet="design"
              priority={3}
              file={SAMPLE_SMELL_FILE}
              beginLine={42}
              endLine={48}
              message="The method 'priceFor(Order)' has a cognitive complexity of 18, current threshold is 15."
              patch={SAMPLE_SMELL_PATCH_RANGE}
              cacheKey="design-system-smell-range"
            />
          </VariantCell>
          <VariantCell label="priority 1 · high severity · collapsed">
            <CodeSmellCard
              width={640}
              defaultOpen={false}
              rule="BrokenNullCheck"
              ruleSet="errorprone"
              priority={1}
              file={SAMPLE_SMELL_FILE}
              beginLine={42}
              endLine={42}
              message="Broken null check; the negation makes this branch unreachable."
              patch={SAMPLE_SMELL_PATCH_POINT}
              cacheKey="design-system-smell-collapsed"
            />
          </VariantCell>
          <VariantCell label="snippet unavailable · processing error">
            <CodeSmellCard
              width={640}
              rule="UseUtilityClass"
              ruleSet="design"
              priority={3}
              file="src/main/java/org/example/Helpers.java"
              beginLine={5}
              endLine={5}
              message="All methods are static; consider making this a utility class with a private constructor."
              patch={null}
            />
          </VariantCell>
          <VariantCell label="state=new · first seen at this checkpoint">
            <CodeSmellCard
              width={640}
              state="new"
              rule="AvoidLiteralsInIfCondition"
              ruleSet="errorprone"
              priority={3}
              file={SAMPLE_SMELL_FILE}
              beginLine={42}
              endLine={42}
              message="Avoid using literals in if statements; assign the constant to a named variable so the intent is explicit."
              patch={SAMPLE_SMELL_PATCH_POINT}
              cacheKey="design-system-smell-new"
            />
          </VariantCell>
          <VariantCell label="state=resolved · was at prev, gone here">
            <CodeSmellCard
              width={640}
              state="resolved"
              rule="AvoidLiteralsInIfCondition"
              ruleSet="errorprone"
              priority={3}
              file={SAMPLE_SMELL_FILE}
              beginLine={42}
              endLine={42}
              message="Avoid using literals in if statements; assign the constant to a named variable so the intent is explicit."
              patch={SAMPLE_SMELL_PATCH_POINT}
              cacheKey="design-system-smell-resolved"
            />
          </VariantCell>
        </DesignSystemSection>

        <DesignSystemSection title="Card" layout="showcase">
          <VariantCell label="default">
            <Card className="w-64">
              <CardHeader>
                <CardTitle>Card title</CardTitle>
                <CardDescription>Short description of the card.</CardDescription>
              </CardHeader>
              <CardContent>
                <p className="text-fg-2 text-sm">
                  Body content — sits on <code className="font-mono">bg-card</code>.
                </p>
              </CardContent>
            </Card>
          </VariantCell>
          <VariantCell label="size=sm">
            <Card size="sm" className="w-64">
              <CardHeader>
                <CardTitle>Compact</CardTitle>
                <CardDescription>Denser padding.</CardDescription>
              </CardHeader>
              <CardContent>
                <p className="text-fg-2 text-sm">Body content.</p>
              </CardContent>
            </Card>
          </VariantCell>
        </DesignSystemSection>

        <DesignSystemSection title="PitfallCallout" layout="stack">
          <VariantCell label="tone=bad · tests not run">
            <PitfallCallout
              tone="bad"
              icon={<XIcon className="size-2.5" strokeWidth={2.5} />}
              title="Tests skipped"
              description="No test run was recorded after this refactoring. Changes landed unverified."
            />
          </VariantCell>
          <VariantCell label="tone=warn · IDE-able manual refactor">
            <PitfallCallout
              tone="warn"
              icon={<ZapIcon className="size-2.5 fill-warn" strokeWidth={0} />}
              title="Manual refactor — IDE could have done this"
              mono="Extract Method"
              description="RefactoringMiner detected a refactoring that maps to a built-in IntelliJ action. Invoking it from the Refactor menu would keep references and usages in sync automatically."
            />
          </VariantCell>
        </DesignSystemSection>
      </DesignSystemPage>
    </TooltipProvider>
  )
}
