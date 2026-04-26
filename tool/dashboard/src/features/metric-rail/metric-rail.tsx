import { RailSection } from "@/components/rail-section"
import { Text } from "@/components/text"
import { Checkbox } from "@/components/ui/checkbox"
import { ScrollArea } from "@/components/ui/scroll-area"
import type { DashboardViewModel, MetricId } from "@/data/types"
import { OverlayMetricRow } from "@/features/metric-rail/overlay-metric-row"
import { PrimaryMetricRow } from "@/features/metric-rail/primary-metric-row"
import { useDashboardStore } from "@/stores/dashboard-store"

/**
 * Left rail: primary-metric radio list, overlay checkboxes (max 2,
 * mutex with primary), and the Layers section (just the build/test
 * interval toggle — annotation-types / suggested-path are out of
 * scope per PLAN).
 */
export function MetricRail({ vm }: { vm: DashboardViewModel }) {
  const primary = useDashboardStore((s) => s.primary)
  const secondaries = useDashboardStore((s) => s.secondaries)
  const layers = useDashboardStore((s) => s.layers)
  const setPrimary = useDashboardStore((s) => s.setPrimary)
  const toggleSecondary = useDashboardStore((s) => s.toggleSecondary)
  const setLayer = useDashboardStore((s) => s.setLayer)

  return (
    <aside className="bg-bg-1 border-border w-[240px] shrink-0 border-r">
      <ScrollArea className="h-full">
        <RailSection title="Primary metric">
          {vm.metrics.map((m) => (
            <PrimaryMetricRow
              key={m.id}
              metric={m}
              values={metricValues(vm, m.id)}
              active={primary === m.id}
              onClick={() => setPrimary(m.id)}
            />
          ))}
        </RailSection>

        <RailSection
          title="Overlay (max 2)"
          description="Dashed secondary lines on the graph"
        >
          {vm.metrics.map((m) => {
            const isPrimary = m.id === primary
            const active = isPrimary || secondaries.includes(m.id)
            const full = secondaries.length >= 2
            return (
              <OverlayMetricRow
                key={m.id}
                metric={m}
                active={active}
                disabled={isPrimary || (!active && full)}
                onToggle={() => toggleSecondary(m.id)}
              />
            )
          })}
        </RailSection>

        <RailSection title="Layers">
          <LayerToggle
            id="layer-build-intervals"
            label="Build intervals"
            checked={layers.buildIntervals}
            onChange={(v) => setLayer("buildIntervals", v)}
          />
          <LayerToggle
            id="layer-test-intervals"
            label="Test intervals"
            checked={layers.testIntervals}
            onChange={(v) => setLayer("testIntervals", v)}
          />
          <LayerToggle
            id="layer-alt-trajectories"
            label="Alternative paths"
            checked={layers.alternativeTrajectories}
            onChange={(v) => setLayer("alternativeTrajectories", v)}
          />
        </RailSection>
      </ScrollArea>
    </aside>
  )
}

function LayerToggle({
  id,
  label,
  checked,
  onChange,
}: {
  id: string
  label: string
  checked: boolean
  onChange: (v: boolean) => void
}) {
  return (
    <label
      htmlFor={id}
      className="hover:bg-bg-2 flex cursor-pointer items-center gap-2.5 px-3 py-[5px]"
    >
      <Checkbox
        id={id}
        tone="brand"
        checked={checked}
        onCheckedChange={(v) => onChange(v === true)}
      />
      <Text variant="body" tone="fg-2" className="text-[12px]">
        {label}
      </Text>
    </label>
  )
}

function metricValues(vm: DashboardViewModel, id: MetricId): number[] {
  const values: number[] = []
  for (const c of vm.checkpoints) {
    const v = c.values[id]
    if (typeof v === "number") values.push(v)
  }
  return values
}
