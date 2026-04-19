/*
 * Temporary preview page. Used to eyeball tokens and primitives before
 * we build the real dashboard shell in step 5 of PLAN.md.
 */

import { CheckIcon, XIcon, HelpCircleIcon } from "lucide-react"
import { Meter } from "@/components/meter"
import { MetricTile } from "@/components/metric-tile"
import { RailSection } from "@/components/rail-section"
import { ScorePill } from "@/components/score-pill"
import { Sparkline } from "@/components/sparkline"
import { StatusDot } from "@/components/status-dot"
import { StatusRow } from "@/components/status-row"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Checkbox } from "@/components/ui/checkbox"
import { ScrollArea } from "@/components/ui/scroll-area"
import { Separator } from "@/components/ui/separator"
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/tooltip"
import { ColorSwatch } from "@/features/preview/color-swatch"
import { PreviewPage, PreviewSection } from "@/features/preview/preview-section"
import { TextSample } from "@/features/preview/text-sample"
import { TokenCaption } from "@/features/preview/token-caption"
import { TokenRow } from "@/features/preview/token-row"
import { VariantCell } from "@/features/preview/variant-cell"

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
  ["brand", "mint (primary accent)"],
  ["brand-2", "blue (overlay 1)"],
  ["brand-3", "violet (overlay 2)"],
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
const CHECKBOX_TONES = ["brand", "brand-2", "brand-3", "fg"] as const

export default function App() {
  return (
    <TooltipProvider>
      <PreviewPage
        title="Design system preview"
        description={
          <>
            Temporary preview — tokens + shadcn primitives wired to the IntelliJ-dark palette.
            See <code className="font-mono">PLAN.md</code> steps 1–2.
          </>
        }
      >
        <PreviewSection title="Surfaces">
          {SURFACES.map(([token, label]) => (
            <TokenRow key={token} visual={<ColorSwatch token={token} />}>
              <TokenCaption token={token} label={label} />
            </TokenRow>
          ))}
        </PreviewSection>

        <PreviewSection title="Text">
          {TEXT_TIERS.map(([token, label]) => (
            <TokenRow key={token} visual={<TextSample token={token}>Aa</TextSample>}>
              <TokenCaption token={token} label={label} />
            </TokenRow>
          ))}
        </PreviewSection>

        <PreviewSection title="Brand">
          {BRAND.map(([token, label]) => (
            <TokenRow key={token} visual={<ColorSwatch token={token} />}>
              <TokenCaption token={token} label={label} />
            </TokenRow>
          ))}
        </PreviewSection>

        <PreviewSection title="Status">
          {STATUS.map(([token, label]) => (
            <TokenRow key={token} visual={<ColorSwatch token={token} />}>
              <TokenCaption token={token} label={label} />
            </TokenRow>
          ))}
        </PreviewSection>

        <PreviewSection title="Typography">
          <TokenRow visual={<TextSample family="sans">The quick brown fox</TextSample>}>
            <TokenCaption token="font-sans" label="default body" />
          </TokenRow>
          <TokenRow visual={<TextSample family="mono">const x = 42;</TextSample>}>
            <TokenCaption token="font-mono" label="code / numerics" />
          </TokenRow>
        </PreviewSection>

        <PreviewSection title="Radii">
          {RADII.map(([radius, px]) => (
            <TokenRow
              key={radius}
              visual={<ColorSwatch token="bg-2" size="sm" radius={radius} />}
            >
              <TokenCaption token={`radius-${radius}`} label={px} />
            </TokenRow>
          ))}
        </PreviewSection>

        <PreviewSection title="Button · variants" layout="showcase">
          {BUTTON_VARIANTS.map((variant) => (
            <VariantCell key={variant} label={variant}>
              <Button variant={variant}>Click me</Button>
            </VariantCell>
          ))}
        </PreviewSection>

        <PreviewSection title="Button · sizes" layout="showcase">
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
        </PreviewSection>

        <PreviewSection title="Badge · variants" layout="showcase">
          {BADGE_VARIANTS.map((variant) => (
            <VariantCell key={variant} label={variant}>
              <Badge variant={variant}>badge</Badge>
            </VariantCell>
          ))}
        </PreviewSection>

        <PreviewSection title="Badge · status tones" layout="showcase">
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
        </PreviewSection>

        <PreviewSection title="Checkbox · tones" layout="showcase">
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
        </PreviewSection>

        <PreviewSection title="StatusDot" layout="showcase">
          {(["success", "danger", "warning", "neutral"] as const).map((tone) => (
            <VariantCell key={tone} label={tone}>
              <div className="flex items-center gap-2">
                <StatusDot tone={tone} />
                <StatusDot tone={tone} size="md" />
              </div>
            </VariantCell>
          ))}
        </PreviewSection>

        <PreviewSection title="Sparkline" layout="showcase">
          <VariantCell label="muted">
            <Sparkline values={[3, 5, 4, 6, 8, 7, 10, 9, 12]} />
          </VariantCell>
          <VariantCell label="brand (active)">
            <Sparkline values={[3, 5, 4, 6, 8, 7, 10, 9, 12]} tone="brand" />
          </VariantCell>
          <VariantCell label="brand-2">
            <Sparkline values={[12, 9, 10, 7, 8, 6, 4, 5, 3]} tone="brand2" />
          </VariantCell>
          <VariantCell label="brand-3 · larger">
            <Sparkline
              values={[5, 7, 6, 8, 6, 7, 9, 8, 10]}
              tone="brand3"
              width={120}
              height={28}
            />
          </VariantCell>
        </PreviewSection>

        <PreviewSection title="ScorePill" layout="showcase">
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
        </PreviewSection>

        <PreviewSection title="RailSection" layout="showcase">
          <VariantCell label="with description + items">
            <div className="w-60 rounded-md border border-border bg-bg-1">
              <RailSection title="PRIMARY METRIC">
                <div className="px-3 pb-2 text-fg-2 text-xs">Complexity · wmc</div>
                <div className="px-3 pb-2 text-fg-2 text-xs">Coupling · cbo</div>
              </RailSection>
              <RailSection
                title="OVERLAY (MAX 2)"
                description="Dashed secondary lines on the graph"
              >
                <div className="px-3 pb-2 text-fg-3 text-xs">Readability</div>
              </RailSection>
            </div>
          </VariantCell>
        </PreviewSection>

        <PreviewSection title="MetricTile" layout="showcase">
          <VariantCell label="lower is better · low">
            <div className="w-48">
              <MetricTile label="Complexity" value={12.4} unit="wmc" fraction={0.2} />
            </div>
          </VariantCell>
          <VariantCell label="lower is better · high">
            <div className="w-48">
              <MetricTile label="Duplication" value={18.7} unit="%" fraction={0.85} />
            </div>
          </VariantCell>
          <VariantCell label="higher is better">
            <div className="w-48">
              <MetricTile
                label="Readability"
                value={83}
                unit="score"
                fraction={0.7}
                better="higher"
              />
            </div>
          </VariantCell>
        </PreviewSection>

        <PreviewSection title="StatusRow" layout="showcase">
          <VariantCell label="build pass · tests pass">
            <StatusRow build="pass" tests="pass" />
          </VariantCell>
          <VariantCell label="build pass · tests fail">
            <StatusRow build="pass" tests="fail" />
          </VariantCell>
          <VariantCell label="build fail · tests unknown">
            <StatusRow build="fail" tests="unknown" />
          </VariantCell>
        </PreviewSection>

        <PreviewSection title="Meter · rainbow bar" layout="showcase">
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
        </PreviewSection>

        <PreviewSection title="Separator" layout="showcase">
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
        </PreviewSection>

        <PreviewSection title="Tooltip · surfaces" layout="showcase">
          {(["bg-1", "bg-2", "bg-3", "inverted"] as const).map((surface) => (
            <VariantCell key={surface} label={surface}>
              <Tooltip>
                <TooltipTrigger asChild>
                  <Button variant="outline">Hover · {surface}</Button>
                </TooltipTrigger>
                <TooltipContent surface={surface}>
                  surface=&quot;{surface}&quot;
                </TooltipContent>
              </Tooltip>
            </VariantCell>
          ))}
        </PreviewSection>

        <PreviewSection title="Scroll area" layout="showcase">
          <VariantCell label="vertical · 160px">
            <ScrollArea className="border-border bg-bg-1 h-40 w-60 rounded-md border p-3">
              <div className="flex flex-col gap-2 font-mono text-xs">
                {Array.from({ length: 30 }, (_, i) => (
                  <span key={i}>line {String(i + 1).padStart(2, "0")}</span>
                ))}
              </div>
            </ScrollArea>
          </VariantCell>
        </PreviewSection>

        <PreviewSection title="Card" layout="showcase">
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
        </PreviewSection>
      </PreviewPage>
    </TooltipProvider>
  )
}
