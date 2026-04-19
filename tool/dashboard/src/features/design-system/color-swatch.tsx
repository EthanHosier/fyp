import { cva, type VariantProps } from "class-variance-authority"
import { cn } from "@/lib/utils"

const colorSwatchStyles = cva(
  "border border-[color:var(--border-color)]",
  {
    variants: {
      size: {
        md: "h-10 w-16 rounded-md",
        sm: "h-10 w-10 rounded-sm",
      },
      radius: {
        sm: "rounded-sm",
        md: "rounded-md",
        lg: "rounded-lg",
      },
    },
    defaultVariants: { size: "md" },
  },
)

type ColorSwatchProps = VariantProps<typeof colorSwatchStyles> & {
  token: string
  className?: string
}

export function ColorSwatch({ token, size, radius, className }: ColorSwatchProps) {
  return (
    <div
      className={cn(colorSwatchStyles({ size, radius }), className)}
      style={{ background: `var(--${token})` }}
    />
  )
}
