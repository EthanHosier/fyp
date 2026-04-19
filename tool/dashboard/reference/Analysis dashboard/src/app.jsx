// App root — layout composition.

const ACCENT_HUES = {
  mint:   { accent: "#7ee8d4", accent2: "#548af7", accent3: "#c27bff" },
  blue:   { accent: "#548af7", accent2: "#7ee8d4", accent3: "#c27bff" },
  violet: { accent: "#c27bff", accent2: "#7ee8d4", accent3: "#548af7" },
};

function App() {
  const data = window.REFACTOR_DATA;
  const [primary, setPrimary] = React.useState("complexity");
  const [secondaries, setSecondaries] = React.useState(["duplication", "churn"]);
  const [filters, setFilters] = React.useState({
    intervals: true, annotations: true, suggested: true,
  });
  const [selection, setSelection] = React.useState({ kind: "checkpoint", i: 6 });
  const [tweaks, setTweaks] = React.useState(TWEAK_DEFAULTS);

  // apply accent hue from tweaks
  React.useEffect(() => {
    const hue = ACCENT_HUES[tweaks.primaryAccent] || ACCENT_HUES.mint;
    document.documentElement.style.setProperty("--accent", hue.accent);
    document.documentElement.style.setProperty("--accent-2", hue.accent2);
    document.documentElement.style.setProperty("--accent-3", hue.accent3);
  }, [tweaks.primaryAccent]);

  return (
    <div data-screen-label="01 Dashboard" data-om-validate="true" style={{
      display: "flex", flexDirection: "column", height: "100vh", width: "100vw",
      position: "relative"
    }}>
      <Header data={data}/>
      <div style={{ display: "flex", flex: 1, minHeight: 0, position: "relative" }}>
        <MetricRail
          data={data}
          primary={primary} setPrimary={setPrimary}
          secondaries={secondaries} setSecondaries={setSecondaries}
          filters={filters} setFilters={setFilters}
        />

        <main style={{ flex: 1, minWidth: 0, display: "flex", flexDirection: "column", overflow: "hidden" }}>
          {/* Graph area — takes remaining space; strip pinned below */}
          <div style={{ flex: 1, minHeight: 0, overflow: "auto", padding: "14px 20px 4px" }}>
            <GraphToolbar
              data={data}
              primary={primary}
              secondaries={secondaries}
            />
            <div style={{
              background: "var(--bg-1)", border: "1px solid var(--border)",
              borderRadius: 8, padding: "6px 10px 8px",
            }}>
              <TrajectoryGraph
                data={data}
                primary={primary}
                secondaries={secondaries}
                showIntervals={filters.intervals && tweaks.lineStyle === "segmented"}
                showAnnotations={filters.annotations}
                showSuggested={filters.suggested}
                selection={selection}
                onSelect={setSelection}
                density={tweaks.density}
              />
            </div>
            <GraphLegend secondaries={secondaries} data={data}/>
          </div>

          <BottomStrip data={data} selection={selection} onSelect={setSelection}/>
        </main>

        <DetailPanel data={data} selection={selection} onClose={() => setSelection(null)}/>
      </div>

      <TweaksPanel state={tweaks} setState={setTweaks}/>
    </div>
  );
}

function GraphToolbar({ data, primary, secondaries }) {
  const m = data.METRICS.find(x => x.id === primary);
  return (
    <div style={{
      display: "flex", alignItems: "center", gap: 12,
      padding: "4px 4px 10px",
    }}>
      <div>
        <div style={{ fontSize: 10.5, letterSpacing: 1.2, fontFamily: "var(--mono)", color: "var(--fg-4)" }}>
          TRAJECTORY · {primary.toUpperCase()}
        </div>
        <div style={{ fontSize: 17, color: "var(--fg)", fontWeight: 600, marginTop: 2 }}>
          {m.label} over {data.CHECKPOINTS.length} checkpoints
          <span style={{ fontSize: 12, color: "var(--fg-4)", fontFamily: "var(--mono)", marginLeft: 10, fontWeight: 400 }}>
            {data.CHECKPOINTS[0][primary]} → {data.CHECKPOINTS[data.CHECKPOINTS.length-1][primary]} {m.unit}
          </span>
        </div>
      </div>
      <div style={{ flex: 1 }}/>
      <FilterChip label="x · checkpoint" active/>
      <FilterChip label="zoom · fit"/>
      <FilterChip label="export"/>
    </div>
  );
}
function FilterChip({ label, active }) {
  return (
    <button style={{
      padding: "4px 10px",
      background: active ? "var(--bg-3)" : "var(--bg-2)",
      border: "1px solid " + (active ? "var(--border-strong)" : "var(--border)"),
      color: active ? "var(--fg)" : "var(--fg-3)",
      borderRadius: 4, fontSize: 11, fontFamily: "var(--mono)",
      cursor: "pointer"
    }}>{label}</button>
  );
}
function GraphLegend({ secondaries, data }) {
  return (
    <div style={{
      display: "flex", alignItems: "center", gap: 18, flexWrap: "wrap",
      padding: "10px 4px 4px", fontSize: 11, color: "var(--fg-3)", fontFamily: "var(--mono)"
    }}>
      <LegendSwatch label="primary" swatch={<span style={{ width: 18, height: 2, background: "var(--accent)", display: "inline-block" }}/>}/>
      {secondaries.map((sid, i) => {
        const m = data.METRICS.find(x => x.id === sid);
        const c = i === 0 ? "var(--accent-2)" : "var(--accent-3)";
        return (
          <LegendSwatch key={sid} label={m.label.toLowerCase()}
            swatch={<span style={{ width: 18, height: 2, background: `repeating-linear-gradient(90deg,${c} 0 3px,transparent 3px 6px)`, display: "inline-block" }}/>}
          />
        );
      })}
      <span style={{ width: 1, height: 14, background: "var(--border-strong)" }}/>
      <LegendSwatch label="build pass"   swatch={<span style={{ width: 10, height: 10, background: "var(--good)", borderRadius: 2, display: "inline-block" }}/>}/>
      <LegendSwatch label="build fail"   swatch={<span style={{ width: 10, height: 10, background: "var(--bad)", borderRadius: 2, display: "inline-block" }}/>}/>
      <LegendSwatch label="unknown"      swatch={<span style={{ width: 10, height: 10, background: "repeating-linear-gradient(45deg,rgba(100,116,139,0.7) 0 2px,transparent 2px 4px), var(--unknown)", opacity: 0.6, borderRadius: 2, display: "inline-block" }}/>}/>
      <span style={{ width: 1, height: 14, background: "var(--border-strong)" }}/>
      <LegendSwatch label="suggested path" swatch={<span style={{ width: 18, height: 0, borderBottom: "1.5px dashed var(--accent)", display: "inline-block" }}/>}/>
    </div>
  );
}
function LegendSwatch({ label, swatch }) {
  return (
    <span style={{ display: "inline-flex", alignItems: "center", gap: 6 }}>
      {swatch}<span>{label}</span>
    </span>
  );
}

ReactDOM.createRoot(document.getElementById("root")).render(<App/>);
