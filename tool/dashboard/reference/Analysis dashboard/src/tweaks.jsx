// Tweaks panel — follows the host's edit-mode protocol.

const TWEAK_DEFAULTS = /*EDITMODE-BEGIN*/{
  "density": "comfortable",
  "lineStyle": "segmented",
  "showMiniMap": true,
  "primaryAccent": "mint"
}/*EDITMODE-END*/;

function TweaksPanel({ state, setState }) {
  const [open, setOpen] = React.useState(false);

  React.useEffect(() => {
    function onMsg(e) {
      const d = e.data;
      if (!d || typeof d !== "object") return;
      if (d.type === "__activate_edit_mode") setOpen(true);
      if (d.type === "__deactivate_edit_mode") setOpen(false);
    }
    window.addEventListener("message", onMsg);
    window.parent.postMessage({ type: "__edit_mode_available" }, "*");
    return () => window.removeEventListener("message", onMsg);
  }, []);

  function set(key, value) {
    setState(s => ({ ...s, [key]: value }));
    window.parent.postMessage({ type: "__edit_mode_set_keys", edits: { [key]: value } }, "*");
  }

  if (!open) return null;

  return (
    <div style={{
      position: "fixed", bottom: 16, right: 16, zIndex: 100,
      width: 260, background: "var(--bg-1)", border: "1px solid var(--border-strong)",
      borderRadius: 8, boxShadow: "0 18px 50px rgba(0,0,0,0.5)",
      fontSize: 12, color: "var(--fg-2)"
    }}>
      <div style={{
        display: "flex", alignItems: "center", gap: 8,
        padding: "10px 12px", borderBottom: "1px solid var(--border)"
      }}>
        <Icon name="settings" size={12} style={{ color: "var(--accent)" }}/>
        <span style={{ fontFamily: "var(--mono)", color: "var(--fg)", fontWeight: 600 }}>Tweaks</span>
        <span style={{ flex: 1 }}/>
        <button onClick={() => setOpen(false)} style={{
          background: "transparent", border: "none", color: "var(--fg-4)", cursor: "pointer"
        }}>
          <Icon name="close" size={12}/>
        </button>
      </div>

      <TwSection label="Density">
        <TwSegmented value={state.density} onChange={v => set("density", v)}
                     options={[["compact","Compact"],["comfortable","Comfortable"]]}/>
      </TwSection>

      <TwSection label="Primary line style">
        <TwSegmented value={state.lineStyle} onChange={v => set("lineStyle", v)}
                     options={[["segmented","Segmented"],["plain","Plain"]]}/>
      </TwSection>

      <TwSection label="Accent hue">
        <TwSegmented value={state.primaryAccent} onChange={v => set("primaryAccent", v)}
                     options={[["mint","Mint"],["blue","Blue"],["violet","Violet"]]}/>
      </TwSection>

      <TwSection label="Mini-map rail">
        <TwToggle on={state.showMiniMap} onChange={v => set("showMiniMap", v)}/>
      </TwSection>
    </div>
  );
}

function TwSection({ label, children }) {
  return (
    <div style={{ padding: "10px 12px", borderBottom: "1px solid var(--border)" }}>
      <div style={{ fontSize: 10, color: "var(--fg-4)", fontFamily: "var(--mono)", letterSpacing: 1, marginBottom: 6 }}>
        {label.toUpperCase()}
      </div>
      {children}
    </div>
  );
}
function TwSegmented({ value, onChange, options }) {
  return (
    <div style={{ display: "flex", background: "var(--bg-2)", borderRadius: 5, padding: 2, gap: 2 }}>
      {options.map(([v, l]) => (
        <button key={v} onClick={() => onChange(v)} style={{
          flex: 1, padding: "4px 8px",
          background: value === v ? "var(--bg-3)" : "transparent",
          border: "1px solid " + (value === v ? "var(--border-strong)" : "transparent"),
          color: value === v ? "var(--fg)" : "var(--fg-3)",
          fontSize: 11, borderRadius: 4, cursor: "pointer"
        }}>{l}</button>
      ))}
    </div>
  );
}
function TwToggle({ on, onChange }) {
  return (
    <button onClick={() => onChange(!on)} style={{
      width: 40, height: 20, borderRadius: 12,
      background: on ? "var(--accent)" : "var(--bg-3)",
      border: "1px solid var(--border-strong)", padding: 0, cursor: "pointer", position: "relative"
    }}>
      <span style={{
        position: "absolute", top: 2, left: on ? 20 : 2,
        width: 14, height: 14, borderRadius: "50%",
        background: on ? "var(--bg)" : "var(--fg-3)",
        transition: "left 140ms ease"
      }}/>
    </button>
  );
}

window.TweaksPanel = TweaksPanel;
window.TWEAK_DEFAULTS = TWEAK_DEFAULTS;
