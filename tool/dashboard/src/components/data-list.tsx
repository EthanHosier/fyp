import * as React from "react"
import { cva, type VariantProps } from "class-variance-authority"

import { cn } from "@/lib/utils"

/**
 * Bordered, zebra-striped vertical list used in the detail panel for
 * tabular snippets like FILES TOUCHED and DELTAS.
 *
 * The container draws the outer border, rounding, and separators between
 * rows. Rows set their own column layout (flex vs grid) via className —
 * the row primitive only applies padding + zebra background so the same
 * component covers both patterns.
 *
 *   <DataList>
 *     <DataListRow className="flex items-center gap-2">
 *       <FileIcon /> <Text className="flex-1 truncate">{path}</Text>
 *       <Text tone="good">+34</Text> <Text tone="bad">-11</Text>
 *     </DataListRow>
 *   </DataList>
 */

const dataListStyles = cva(
  "overflow-hidden border border-border divide-y divide-border bg-bg-2",
  {
    variants: {
      radius: {
        sm: "rounded-sm",
        md: "rounded-md",
      },
    },
    defaultVariants: { radius: "sm" },
  },
)

type DataListProps = React.HTMLAttributes<HTMLDivElement> &
  VariantProps<typeof dataListStyles>

export function DataList({ className, radius, ...rest }: DataListProps) {
  return (
    <div
      data-slot="data-list"
      className={cn(dataListStyles({ radius }), className)}
      {...rest}
    />
  )
}

const dataListRowStyles = cva("", {
  variants: {
    density: {
      comfortable: "px-2.5 py-[7px]",
      compact: "px-2 py-1.5",
    },
  },
  defaultVariants: { density: "comfortable" },
})

type DataListRowProps = React.HTMLAttributes<HTMLDivElement> &
  VariantProps<typeof dataListRowStyles>

export function DataListRow({ className, density, ...rest }: DataListRowProps) {
  return (
    <div
      data-slot="data-list-row"
      className={cn(dataListRowStyles({ density }), className)}
      {...rest}
    />
  )
}
