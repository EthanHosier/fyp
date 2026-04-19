"use client"

import * as React from "react"
import { Tooltip as TooltipPrimitive } from "radix-ui"
import { cva, type VariantProps } from "class-variance-authority"

import { cn } from "@/lib/utils"

/**
 * Surface variants for the tooltip bubble. Defaults to `bg-1` (our
 * standard card/panel colour) which sits cleanly on the lighter shell.
 * Pick a darker surface for tooltips that hover over card backgrounds,
 * or `inverted` for the shadcn default (light bubble, dark text).
 */
const tooltipContentStyles = cva(
  "z-50 inline-flex w-fit max-w-xs origin-(--radix-tooltip-content-transform-origin) items-center gap-1.5 rounded-md px-3 py-1.5 text-xs border has-data-[slot=kbd]:pr-1.5 data-[side=bottom]:slide-in-from-top-2 data-[side=left]:slide-in-from-right-2 data-[side=right]:slide-in-from-left-2 data-[side=top]:slide-in-from-bottom-2 **:data-[slot=kbd]:relative **:data-[slot=kbd]:isolate **:data-[slot=kbd]:z-50 **:data-[slot=kbd]:rounded-sm data-[state=delayed-open]:animate-in data-[state=delayed-open]:fade-in-0 data-[state=delayed-open]:zoom-in-95 data-open:animate-in data-open:fade-in-0 data-open:zoom-in-95 data-closed:animate-out data-closed:fade-out-0 data-closed:zoom-out-95",
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

const tooltipArrowStyles = cva(
  "z-50 size-2.5 translate-y-[calc(-50%_-_2px)] rotate-45 rounded-[2px]",
  {
    variants: {
      surface: {
        "bg-1": "bg-bg-1 fill-bg-1",
        "bg-2": "bg-bg-2 fill-bg-2",
        "bg-3": "bg-bg-3 fill-bg-3",
        inverted: "bg-foreground fill-foreground",
      },
    },
    defaultVariants: { surface: "bg-1" },
  },
)

function TooltipProvider({
  delayDuration = 0,
  ...props
}: React.ComponentProps<typeof TooltipPrimitive.Provider>) {
  return (
    <TooltipPrimitive.Provider
      data-slot="tooltip-provider"
      delayDuration={delayDuration}
      {...props}
    />
  )
}

function Tooltip({
  ...props
}: React.ComponentProps<typeof TooltipPrimitive.Root>) {
  return <TooltipPrimitive.Root data-slot="tooltip" {...props} />
}

function TooltipTrigger({
  ...props
}: React.ComponentProps<typeof TooltipPrimitive.Trigger>) {
  return <TooltipPrimitive.Trigger data-slot="tooltip-trigger" {...props} />
}

type TooltipContentProps = React.ComponentProps<typeof TooltipPrimitive.Content> &
  VariantProps<typeof tooltipContentStyles>

function TooltipContent({
  className,
  sideOffset = 0,
  surface,
  children,
  ...props
}: TooltipContentProps) {
  return (
    <TooltipPrimitive.Portal>
      <TooltipPrimitive.Content
        data-slot="tooltip-content"
        sideOffset={sideOffset}
        className={cn(tooltipContentStyles({ surface }), className)}
        {...props}
      >
        {children}
        <TooltipPrimitive.Arrow className={tooltipArrowStyles({ surface })} />
      </TooltipPrimitive.Content>
    </TooltipPrimitive.Portal>
  )
}

export { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger }
