/*
 * Temporary token preview. Confirms the design-system palette renders
 * correctly before we build any primitives. Replaced by the real shell
 * in step 5 of PLAN.md.
 */

import { ColorSwatch } from "@/features/preview/color-swatch"
import { PreviewPage, PreviewSection } from "@/features/preview/preview-section"
import { TextSample } from "@/features/preview/text-sample"
import { TokenCaption } from "@/features/preview/token-caption"
import { TokenRow } from "@/features/preview/token-row"

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

export default function App() {
  return (
    <PreviewPage
      title="Design tokens"
      description={
        <>
          Temporary preview — confirms the IntelliJ-dark palette and shadcn semantic
          mapping are wired. See <code className="font-mono">PLAN.md</code> step 1.
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
    </PreviewPage>
  )
}
