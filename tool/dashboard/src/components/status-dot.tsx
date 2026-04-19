import { cva, type VariantProps } from "class-variance-authority"
import { cn } from "@/lib/utils"

const statusDotStyles = cva("inline-block shrink-0 rounded-full", {
  variants: {
    tone: {
      success: "bg-good",
      danger: "bg-bad",
      warning: "bg-warn",
      neutral: "bg-unknown",
    },
    size: {
      sm: "size-[7px]",
      md: "size-[10px]",
    },
  },
  defaultVariants: { tone: "neutral", size: "sm" },
})

type StatusDotProps = VariantProps<typeof statusDotStyles> & {
  className?: string
  "aria-label"?: string
}

export function StatusDot({ tone, size, className, ...rest }: StatusDotProps) {
  return <span className={cn(statusDotStyles({ tone, size }), className)} {...rest} />
}
