import type { ReactNode } from "react"
import { cva, type VariantProps } from "class-variance-authority"
import { cn } from "@/lib/utils"

const previewSectionBodyStyles = cva("", {
  variants: {
    layout: {
      tokens: "grid grid-cols-2 gap-x-8 gap-y-3 lg:grid-cols-4",
      showcase: "flex flex-wrap items-start gap-x-8 gap-y-6",
    },
  },
  defaultVariants: { layout: "tokens" },
})

type PreviewSectionProps = VariantProps<typeof previewSectionBodyStyles> & {
  title: string
  children: ReactNode
  className?: string
}

export function PreviewSection({ title, layout, className, children }: PreviewSectionProps) {
  return (
    <section className="flex flex-col gap-3">
      <h2 className="text-fg-2 text-[11px] font-medium tracking-[0.08em] uppercase">{title}</h2>
      <div className={cn(previewSectionBodyStyles({ layout }), className)}>{children}</div>
    </section>
  )
}

export function PreviewPage({
  title,
  description,
  children,
}: {
  title: string
  description?: ReactNode
  children: ReactNode
}) {
  return (
    <div className="h-full w-full overflow-auto bg-bg">
      <div className="mx-auto flex max-w-5xl flex-col gap-10 p-10">
        <header className="flex flex-col gap-1">
          <h1 className="text-fg text-xl font-semibold">{title}</h1>
          {description ? <p className="text-fg-3">{description}</p> : null}
        </header>
        {children}
      </div>
    </div>
  )
}
