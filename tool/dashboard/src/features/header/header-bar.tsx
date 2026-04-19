import type { ReactNode } from "react"
import { Clock, GitBranch, Layers, Settings } from "lucide-react"
import { useNavigate } from "react-router-dom"

import { Text } from "@/components/text"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Separator } from "@/components/ui/separator"
import type { DashboardViewModel } from "@/data/types"
import { formatClockTime, formatDurationShort, initials, shortSha } from "@/lib/format"

/**
 * Top chrome — brand + app name, session metadata pills, and right-side
 * action buttons. Mirrors the reference layout but drops the "Process
 * score" pill (deferred per PLAN) and renders the commit short-sha as a
 * badge where the reference would put the author.
 */
export function HeaderBar({ vm }: { vm: DashboardViewModel }) {
  const { session } = vm
  const navigate = useNavigate()
  return (
    <header className="bg-bg-1 border-border flex h-12 shrink-0 items-center gap-4 border-b px-4">
      <BrandMark>{initials(session.name)}</BrandMark>
      <Text variant="mono" tone="fg-2">{session.name}</Text>
      <Separator orientation="vertical" className="h-5" />

      <HeaderMeta icon={<GitBranch className="size-3" />} mono>
        {session.projectName} · {session.branch ?? "(detached)"}
      </HeaderMeta>
      <HeaderMeta icon={<Clock className="size-3" />}>
        {formatClockTime(session.startedAt)} ·{" "}
        {formatDurationShort(session.durationMs)}
      </HeaderMeta>
      <HeaderMeta icon={<Layers className="size-3" />}>
        {session.checkpointCount} checkpoints
      </HeaderMeta>

      <div className="flex-1" />

      {session.commitHash ? (
        <Badge variant="outline" className="font-mono">
          {shortSha(session.commitHash)}
        </Badge>
      ) : null}
      <Button variant="outline" size="sm" onClick={() => navigate("/data")}>
        <Settings />
        <span>Settings</span>
      </Button>
    </header>
  )
}

function BrandMark({ children }: { children: ReactNode }) {
  return (
    <div
      className="from-brand to-brand-2 grid size-[22px] place-items-center rounded-sm bg-gradient-to-br"
      aria-hidden
    >
      <Text variant="mono" tone="inherit" className="text-[11px] font-bold text-bg">
        {children}
      </Text>
    </div>
  )
}

function HeaderMeta({
  icon,
  mono = false,
  children,
}: {
  icon: ReactNode
  mono?: boolean
  children: ReactNode
}) {
  return (
    <div className="text-fg-3 inline-flex items-center gap-1.5">
      <span className="text-fg-4">{icon}</span>
      <Text variant={mono ? "mono" : "body"} tone="fg-3">
        {children}
      </Text>
    </div>
  )
}
