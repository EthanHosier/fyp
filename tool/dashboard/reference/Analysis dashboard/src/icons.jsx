// Tiny inline SVG icon set — stroke-based, IDE vibe.
const Icon = ({ name, size = 14, style }) => {
  const s = size;
  const common = { width: s, height: s, viewBox: "0 0 16 16", fill: "none", stroke: "currentColor", strokeWidth: 1.4, strokeLinecap: "round", strokeLinejoin: "round", style };
  switch (name) {
    case "git-branch":    return <svg {...common}><circle cx="4" cy="3" r="1.4"/><circle cx="4" cy="13" r="1.4"/><circle cx="12" cy="5" r="1.4"/><path d="M4 4.5v7"/><path d="M12 6.5c0 3-4 3-4 6"/></svg>;
    case "clock":         return <svg {...common}><circle cx="8" cy="8" r="6"/><path d="M8 5v3.5L10 10"/></svg>;
    case "user":          return <svg {...common}><circle cx="8" cy="6" r="2.5"/><path d="M3 13c1-2.5 3-3.5 5-3.5s4 1 5 3.5"/></svg>;
    case "file":          return <svg {...common}><path d="M4 2h5l3 3v9H4z"/><path d="M9 2v3h3"/></svg>;
    case "check":         return <svg {...common}><path d="M3 8.5l3.2 3L13 5"/></svg>;
    case "x":             return <svg {...common}><path d="M4 4l8 8M12 4l-8 8"/></svg>;
    case "question":      return <svg {...common}><circle cx="8" cy="8" r="6"/><path d="M6.5 6.5c0-1 .7-1.8 1.8-1.8s1.7.8 1.7 1.6c0 1.4-1.7 1.4-1.7 2.7"/><circle cx="8" cy="11.5" r=".4" fill="currentColor"/></svg>;
    case "zap":           return <svg {...common}><path d="M9 1.5L3.5 9h3L7 14.5 12.5 7h-3z"/></svg>;
    case "wrench":        return <svg {...common}><path d="M10.5 2.5a3 3 0 013 3l-1.5 1.5-1-1L9.5 7.5l1 1L9 10l-5 5-1.5-1.5 5-5 1.5-1.5-1-1L9 4.5l1 1z"/></svg>;
    case "search":        return <svg {...common}><circle cx="7" cy="7" r="4.5"/><path d="M10.5 10.5L14 14"/></svg>;
    case "gitcommit":     return <svg {...common}><circle cx="8" cy="8" r="2.2"/><path d="M2 8h3.8M10.2 8H14"/></svg>;
    case "fork":          return <svg {...common}><circle cx="4" cy="3" r="1.4"/><circle cx="12" cy="3" r="1.4"/><circle cx="8" cy="13" r="1.4"/><path d="M4 4.5V7c0 1.5 1 2 2 2.5s2 .8 2 2.2M12 4.5V7c0 1.5-1 2-2 2.5"/></svg>;
    case "lightbulb":     return <svg {...common}><path d="M5.5 9.5C4.5 8.8 4 7.5 4 6.2A4 4 0 018 2.2a4 4 0 014 4c0 1.3-.5 2.6-1.5 3.3V11H5.5z"/><path d="M6 13h4M6.5 14.5h3"/></svg>;
    case "alert":         return <svg {...common}><path d="M8 2L1.5 13h13z"/><path d="M8 6v3.5"/><circle cx="8" cy="11.5" r=".4" fill="currentColor"/></svg>;
    case "revert":        return <svg {...common}><path d="M4 7.5V4h3.5"/><path d="M4 4c3 3 7 2.5 8.5 5.5s-1 5-3.5 5"/></svg>;
    case "chevron-right": return <svg {...common}><path d="M6 3l5 5-5 5"/></svg>;
    case "chevron-down":  return <svg {...common}><path d="M3 6l5 5 5-5"/></svg>;
    case "close":         return <svg {...common}><path d="M4 4l8 8M12 4l-8 8"/></svg>;
    case "eye":           return <svg {...common}><path d="M1.5 8S4 3.5 8 3.5 14.5 8 14.5 8 12 12.5 8 12.5 1.5 8 1.5 8z"/><circle cx="8" cy="8" r="1.8"/></svg>;
    case "layers":        return <svg {...common}><path d="M8 2l6 3-6 3-6-3z"/><path d="M2 8l6 3 6-3M2 11l6 3 6-3"/></svg>;
    case "flag":          return <svg {...common}><path d="M4 14V2M4 3h8l-1.5 3L12 9H4"/></svg>;
    case "settings":      return <svg {...common}><circle cx="8" cy="8" r="2"/><path d="M8 1.5v2M8 12.5v2M1.5 8h2M12.5 8h2M3.3 3.3l1.4 1.4M11.3 11.3l1.4 1.4M3.3 12.7l1.4-1.4M11.3 4.7l1.4-1.4"/></svg>;
    case "filter":        return <svg {...common}><path d="M2 3h12l-5 6v4l-2 1V9z"/></svg>;
    default:              return null;
  }
};

window.Icon = Icon;
