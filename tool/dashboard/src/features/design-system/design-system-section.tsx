import type { ReactNode } from "react"
import { cva, type VariantProps } from "class-variance-authority"

import { Text } from "@/components/text"
import { cn } from "@/lib/utils"

const designSystemSectionBodyStyles = cva("", {
  variants: {
    layout: {
      tokens: "grid grid-cols-2 gap-x-8 gap-y-3 lg:grid-cols-4",
      showcase: "flex flex-wrap items-start gap-x-8 gap-y-6",
      stack: "flex flex-col items-start gap-4",
    },
  },
  defaultVariants: { layout: "tokens" },
})

type DesignSystemSectionProps = VariantProps<typeof designSystemSectionBodyStyles> & {
  title: string
  children: ReactNode
  className?: string
}

export function DesignSystemSection({ title, layout, className, children }: DesignSystemSectionProps) {
  return (
    <section className="flex flex-col gap-3">
      <Text as="h2" variant="eyebrow" tone="fg-2">{title}</Text>
      <div className={cn(designSystemSectionBodyStyles({ layout }), className)}>{children}</div>
    </section>
  )
}

export function DesignSystemPage({
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
          <Text as="h1" variant="display" tone="fg" className="text-xl">
            {title}
          </Text>
          {description ? (
            <Text as="p" tone="fg-3">
              {description}
            </Text>
          ) : null}
        </header>
        {children}
      </div>
    </div>
  )
}
