import { cva, type VariantProps } from "class-variance-authority"
import { cn } from "@/lib/utils"

const textSampleStyles = cva("", {
  variants: {
    family: {
      sans: "font-sans text-base",
      mono: "font-mono",
    },
  },
  defaultVariants: { family: "sans" },
})

type TextSampleProps = VariantProps<typeof textSampleStyles> & {
  token?: string
  className?: string
  children: React.ReactNode
}

export function TextSample({ token, family, className, children }: TextSampleProps) {
  return (
    <span
      className={cn(textSampleStyles({ family }), className)}
      style={token ? { color: `var(--${token})` } : undefined}
    >
      {children}
    </span>
  )
}
