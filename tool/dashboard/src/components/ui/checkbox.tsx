import * as React from "react"
import { Checkbox as CheckboxPrimitive } from "radix-ui"
import { cva, type VariantProps } from "class-variance-authority"

import { cn } from "@/lib/utils"
import { CheckIcon } from "lucide-react"

/**
 * Colour tones for the checked-state fill + border. `brand` (mint) is the
 * default. Use `brand-2` / `brand-3` to colour-match overlay metric lines
 * on the chart (blue / violet), or `fg` for a neutral dark tick.
 */
const checkboxStyles = cva(
  "peer relative flex size-4 shrink-0 cursor-pointer items-center justify-center rounded-[4px] border border-input outline-none group-has-disabled/field:opacity-50 after:absolute after:-inset-x-3 after:-inset-y-2 focus-visible:ring-3 focus-visible:ring-ring/50 disabled:cursor-not-allowed disabled:opacity-50 aria-invalid:border-destructive aria-invalid:ring-3 aria-invalid:ring-destructive/20 dark:bg-input/30 dark:aria-invalid:border-destructive/50 dark:aria-invalid:ring-destructive/40",
  {
    variants: {
      tone: {
        brand: "focus-visible:border-ring data-checked:border-brand data-checked:bg-brand data-checked:text-bg",
        "brand-2": "focus-visible:border-brand-2 data-checked:border-brand-2 data-checked:bg-brand-2 data-checked:text-bg",
        "brand-3": "focus-visible:border-brand-3 data-checked:border-brand-3 data-checked:bg-brand-3 data-checked:text-bg",
        "brand-4": "focus-visible:border-brand-4 data-checked:border-brand-4 data-checked:bg-brand-4 data-checked:text-bg",
        "brand-5": "focus-visible:border-brand-5 data-checked:border-brand-5 data-checked:bg-brand-5 data-checked:text-bg",
        "brand-6": "focus-visible:border-brand-6 data-checked:border-brand-6 data-checked:bg-brand-6 data-checked:text-bg",
        "brand-7": "focus-visible:border-brand-7 data-checked:border-brand-7 data-checked:bg-brand-7 data-checked:text-bg",
        fg: "focus-visible:border-fg-2 data-checked:border-fg-2 data-checked:bg-fg-2 data-checked:text-bg",
      },
    },
    defaultVariants: { tone: "brand" },
  },
)

type CheckboxProps = React.ComponentProps<typeof CheckboxPrimitive.Root> &
  VariantProps<typeof checkboxStyles>

function Checkbox({ className, tone, ...props }: CheckboxProps) {
  return (
    <CheckboxPrimitive.Root
      data-slot="checkbox"
      className={cn(checkboxStyles({ tone }), className)}
      {...props}
    >
      <CheckboxPrimitive.Indicator
        data-slot="checkbox-indicator"
        className="grid place-content-center text-current transition-none [&>svg]:size-3.5"
      >
        <CheckIcon />
      </CheckboxPrimitive.Indicator>
    </CheckboxPrimitive.Root>
  )
}

export { Checkbox }
