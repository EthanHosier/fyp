import * as React from "react"
import { cva, type VariantProps } from "class-variance-authority"

import { cn } from "@/lib/utils"

/** Single source of truth for dashboard typography. */
const textStyles = cva("", {
  variants: {
    variant: {
      display: "font-sans text-[17px] font-semibold leading-tight",
      heading: "font-sans text-[15px] font-semibold leading-tight",
      body: "font-sans text-[13px] leading-[1.55]",
      bodySm: "font-sans text-[11.5px] leading-[1.55]",
      caption: "font-sans text-[11px] leading-[1.4]",
      eyebrow: "font-mono text-[10.5px] tracking-[0.12em] uppercase",
      mono: "font-mono text-[12px]",
      monoStat: "font-mono text-[17px] font-semibold leading-none",
      monoCaption: "font-mono text-[10px]",
      monoTiny: "font-mono text-[9.5px]",
    },
    tone: {
      fg: "text-fg",
      "fg-2": "text-fg-2",
      "fg-3": "text-fg-3",
      "fg-4": "text-fg-4",
      brand: "text-brand",
      "brand-2": "text-brand-2",
      "brand-3": "text-brand-3",
      "brand-4": "text-brand-4",
      "brand-5": "text-brand-5",
      "brand-6": "text-brand-6",
      "brand-7": "text-brand-7",
      "brand-8": "text-brand-8",
      good: "text-good",
      bad: "text-bad",
      warn: "text-warn",
      inherit: "",
    },
  },
  defaultVariants: { variant: "body", tone: "fg-2" },
})

type TextTag = "span" | "div" | "p" | "h1" | "h2" | "h3" | "h4" | "label" | "code" | "strong" | "small"

type TextProps = VariantProps<typeof textStyles> &
  React.HTMLAttributes<HTMLElement> & {
    as?: TextTag
  }

export function Text({ as = "span", variant, tone, className, ...rest }: TextProps) {
  const Comp = as as React.ElementType
  return <Comp className={cn(textStyles({ variant, tone }), className)} {...rest} />
}

