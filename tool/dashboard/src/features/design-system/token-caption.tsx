export function TokenCaption({ token, label }: { token: string; label: string }) {
  return (
    <div className="flex flex-col">
      <code className="font-mono text-fg">--{token}</code>
      <span className="text-fg-3 text-[11px]">{label}</span>
    </div>
  )
}
