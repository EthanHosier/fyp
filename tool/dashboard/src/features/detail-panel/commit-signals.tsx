import { PitfallCallout } from "@/components/pitfall-callout"
import { CommitChip } from "@/components/tone-chip"
import type {
  CheckpointVM,
  CommitMarkerVM,
  DashboardViewModel,
} from "@/data/types"

const MESSAGE_TRUNC = 80

/**
 * Process-signal callouts for any user-side `git commit`s that landed
 * after [checkpoint] but before the next checkpoint. Surfaces commit
 * cadence so a reader can correlate "I committed here" with whatever
 * the metric trajectory was doing at that moment.
 *
 * Returns a fragment (no outer section); `CheckpointBody` owns the
 * shared "Process Signals" header. One callout per commit — multiple
 * commits between the same pair of checkpoints stack.
 */
export function CommitSignals({
  vm,
  checkpoint,
}: {
  vm: DashboardViewModel
  checkpoint: CheckpointVM
}) {
  const commits = commitsAfterCheckpoint(vm, checkpoint.index)
  if (commits.length === 0) return null
  return (
    <>
      {commits.map((c) => {
        const subject = c.message.split("\n")[0] || "(empty message)"
        const truncated =
          subject.length > MESSAGE_TRUNC
            ? `${subject.slice(0, MESSAGE_TRUNC - 1)}…`
            : subject
        return (
          <PitfallCallout
            key={c.sha}
            tone="good"
            chip={<CommitChip className="shrink-0" />}
            title="You committed after this change"
            mono={`${c.shortSha} · ${truncated}`}
            description="A `git commit` landed on your working repo after this checkpoint. Committing right after a refactoring keeps history bisectable and makes it easy to roll back a single small change without losing surrounding work."
          />
        )
      })}
    </>
  )
}

/**
 * Mirror of `CommitSignals`'s render condition, exposed so the parent
 * panel can decide whether to render the unified "Process Signals"
 * header without an empty section.
 */
export function hasCommitSignals(
  vm: DashboardViewModel,
  checkpointIndex: number,
): boolean {
  return commitsAfterCheckpoint(vm, checkpointIndex).length > 0
}

/**
 * Returns commits whose timestamp falls in
 * `[checkpoint.timestamp, nextCheckpoint.timestamp)`. The last
 * checkpoint's interval extends to +∞ so trailing commits before
 * session end land on it.
 */
function commitsAfterCheckpoint(
  vm: DashboardViewModel,
  checkpointIndex: number,
): CommitMarkerVM[] {
  const cp = vm.checkpoints[checkpointIndex]
  if (!cp) return []
  const next = vm.checkpoints[checkpointIndex + 1]
  const from = cp.timestamp
  const to = next ? next.timestamp : Number.POSITIVE_INFINITY
  return vm.commitMarkers.filter((c) => c.timestamp >= from && c.timestamp < to)
}
