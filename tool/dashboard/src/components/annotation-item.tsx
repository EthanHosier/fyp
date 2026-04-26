import {
  AlertTriangleIcon,
  CheckCircle2Icon,
  GitForkIcon,
  SearchIcon,
  WrenchIcon,
} from "lucide-react"

import { Text } from "@/components/text"
import { cn } from "@/lib/utils"

/**
 * Kind of an annotation pinned to a checkpoint or span. Mirrors the
 * reference dashboard's `ANNOT_META` exactly so the colour palette
 * stays consistent across the chart annotation lane and the detail
 * panel list.
 */
export type AnnotationKind =
  | "ide-refactor"
  | "detected-refactor"
  | "event"
  | "divergence"
  | "resolved"

export type Annotation = {
  id: string
  kind: AnnotationKind
  title: string
  /** Mono sub-line (file path, count, etc.). Optional. */
  detail?: string
}

const META: Record<
  AnnotationKind,
  { label: string; iconClass: string; Icon: typeof WrenchIcon }
> = {
  // The colour utilities below resolve to the reference palette:
  // brand-2 (#548af7) blue, brand-3 (#c27bff) violet, brand-4 (#f0a868)
  // amber, brand (#7ee8d4) mint.
  "ide-refactor":      { label: "IDE refactor",      iconClass: "text-brand-2", Icon: WrenchIcon },
  "detected-refactor": { label: "Detected refactor", iconClass: "text-brand-3", Icon: SearchIcon },
  event:               { label: "Event",             iconClass: "text-brand-4", Icon: AlertTriangleIcon },
  divergence:          { label: "Divergence",        iconClass: "text-brand",   Icon: GitForkIcon },
  resolved:            { label: "Resolved",          iconClass: "text-good",    Icon: CheckCircle2Icon },
}

export function AnnotationItem({ annotation }: { annotation: Annotation }) {
  const meta = META[annotation.kind]
  const Icon = meta.Icon
  return (
    <div className="flex gap-2 py-1.5">
      <Icon className={cn("mt-0.5 size-3 shrink-0", meta.iconClass)} />
      <div className="flex flex-1 flex-col gap-0.5">
        <Text as="span" variant="bodySm" tone="fg-2">
          {annotation.title}
        </Text>
        {annotation.detail ? (
          <Text variant="monoTiny" tone="fg-4">
            {annotation.detail}
          </Text>
        ) : null}
      </div>
    </div>
  )
}

export function annotationLabel(kind: AnnotationKind): string {
  return META[kind].label
}
