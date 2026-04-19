import type { ReactNode } from "react"

export function PreviewSection({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="flex flex-col gap-3">
      <h2 className="text-fg-2 text-[11px] font-medium tracking-[0.08em] uppercase">{title}</h2>
      <div className="grid grid-cols-2 gap-x-8 gap-y-3 lg:grid-cols-4">{children}</div>
    </section>
  )
}

export function PreviewPage({ title, description, children }: { title: string; description?: ReactNode; children: ReactNode }) {
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
