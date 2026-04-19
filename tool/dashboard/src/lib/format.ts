export function formatDurationShort(ms: number): string {
  const s = Math.max(0, Math.round(ms / 1000))
  const m = Math.floor(s / 60)
  const r = s % 60
  return `${m}m ${r.toString().padStart(2, "0")}s`
}

export function formatTLabel(deltaMs: number): string {
  const s = Math.max(0, Math.round(deltaMs / 1000))
  const m = Math.floor(s / 60)
  const r = s % 60
  return `t+${m.toString().padStart(2, "0")}:${r.toString().padStart(2, "0")}`
}

export function formatDelta(n: number, decimals = 1): string {
  const sign = n > 0 ? "+" : ""
  return `${sign}${n.toFixed(decimals)}`
}

export function shortSha(sha: string): string {
  return sha.slice(0, 7)
}

export function formatClockTime(ms: number): string {
  const d = new Date(ms)
  return `${d.getHours().toString().padStart(2, "0")}:${d.getMinutes().toString().padStart(2, "0")}`
}

export function initials(name: string): string {
  const words = name.split(/[^a-zA-Z0-9]+/).filter(Boolean)
  if (words.length >= 2) return (words[0][0] + words[1][0]).toLowerCase()
  return name.slice(0, 2).toLowerCase()
}
