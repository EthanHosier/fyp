"use client"

import * as React from "react"
import { HoverCard as HoverCardPrimitive } from "radix-ui"
import { cva, type VariantProps } from "class-variance-authority"

import { cn } from "@/lib/utils"

/**
 * Surface variants for the hover-card panel. Mirrors `Tooltip`'s API
 * so the two primitives feel like siblings; pick a richer surface when
 * the card hovers over a card-coloured background.
 */
const hoverCardContentStyles = cva(
  "z-50 w-64 origin-(--radix-hover-card-content-transform-origin) rounded-md border p-3 shadow-md outline-hidden data-[side=bottom]:slide-in-from-top-2 data-[side=left]:slide-in-from-right-2 data-[side=right]:slide-in-from-left-2 data-[side=top]:slide-in-from-bottom-2 data-[state=open]:animate-in data-[state=open]:fade-in-0 data-[state=open]:zoom-in-95 data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=closed]:zoom-out-95",
  {
    variants: {
      surface: {
        "bg-1": "bg-bg-1 text-fg border-border",
        "bg-2": "bg-bg-2 text-fg border-border",
        "bg-3": "bg-bg-3 text-fg border-border",
        inverted: "bg-foreground text-background border-transparent",
      },
    },
    defaultVariants: { surface: "bg-1" },
  },
)

function HoverCard({
  ...props
}: React.ComponentProps<typeof HoverCardPrimitive.Root>) {
  return <HoverCardPrimitive.Root data-slot="hover-card" {...props} />
}

function HoverCardTrigger({
  ...props
}: React.ComponentProps<typeof HoverCardPrimitive.Trigger>) {
  return (
    <HoverCardPrimitive.Trigger data-slot="hover-card-trigger" {...props} />
  )
}

type HoverCardContentProps = React.ComponentProps<
  typeof HoverCardPrimitive.Content
> &
  VariantProps<typeof hoverCardContentStyles>

function HoverCardContent({
  className,
  align = "center",
  sideOffset = 6,
  surface,
  ...props
}: HoverCardContentProps) {
  return (
    <HoverCardPrimitive.Portal>
      <HoverCardPrimitive.Content
        data-slot="hover-card-content"
        align={align}
        sideOffset={sideOffset}
        className={cn(hoverCardContentStyles({ surface }), className)}
        {...props}
      />
    </HoverCardPrimitive.Portal>
  )
}

export { HoverCard, HoverCardContent, HoverCardTrigger }
