// Side panels, headers, explanation cards.

const { useState: _useState2 } = React;

/* ============================================================
   HEADER
============================================================ */
function Header({ data }) {
  const s = data.SESSION;
  const pScoreDelta = s.processScore - s.prevProcessScore;
  return (
    <header style={{
      display: "flex", alignItems: "center", gap: 16,
      padding: "10px 18px",
      borderBottom: "1px solid var(--border)",
      background: "var(--bg-1)",
      flexShrink: 0,
    }}>
      <div style={{
        width: 22, height: 22, borderRadius: 4,
        background: "linear-gradient(135deg,var(--accent) 0%,#548af7 100%)",
        display: "grid", placeItems: "center",
        fontFamily: "var(--mono)", fontSize: 11, fontWeight: 700, color: "#1e1f22"
      }}>rt</div>
      <div style={{ fontFamily: "var(--mono)", fontSize: 12, color: "var(--fg-2)" }}>
        refactor-trajectory
      </div>
      <div style={{ width: 1, height: 20, background: "var(--border)" }}/>
      <HeaderMeta icon="git-branch" label={`${s.repo} · ${s.branch}`}/>
      <HeaderMeta icon="user" label={s.author}/>
      <HeaderMeta icon="clock" label={`${s.startedAt} · ${s.durationMin} min`}/>
      <HeaderMeta icon="layers" label={`${s.checkpointCount} checkpoints`}/>

      <div style={{ flex: 1 }}/>

      <ScorePill label="Process score" value={s.processScore} delta={pScoreDelta}/>
      <button style={btnGhost}>
        <Icon name="settings"/> <span style={{ marginLeft: 6 }}>Settings</span>
      </button>
      <button style={{ ...btnGhost, background: "var(--bg-3)" }}>
        <Icon name="eye"/> <span style={{ marginLeft: 6 }}>Sessions</span>
      </button>
    </header>
  );
}
function HeaderMeta({ icon, label }) {
  return (
    <div style={{ display: "inline-flex", alignItems: "center", gap: 6, color: "var(--fg-3)", fontSize: 12 }}>
      <span style={{ color: "var(--fg-4)" }}><Icon name={icon}/></span>
      <span style={{ fontFamily: icon === "git-branch" || icon === "user" ? "var(--mono)" : "inherit" }}>{label}</span>
    </div>
  );
}
function ScorePill({ label, value, delta }) {
  const good = delta >= 0;
  return (
    <div style={{
      display: "flex", alignItems: "center", gap: 10,
      padding: "5px 10px",
      border: "1px solid var(--border-strong)",
      borderRadius: 6, background: "var(--bg-2)"
    }}>
      <span style={{ fontSize: 11, color: "var(--fg-3)" }}>{label}</span>
      <span style={{ fontFamily: "var(--mono)", fontSize: 14, fontWeight: 600, color: "var(--fg)" }}>
        {value}<span style={{ color: "var(--fg-4)", fontWeight: 400 }}>/100</span>
      </span>
      <span style={{
        fontFamily: "var(--mono)", fontSize: 11,
        color: good ? "var(--good)" : "var(--bad)"
      }}>
        {good ? "▲" : "▼"}{Math.abs(delta)}
      </span>
    </div>
  );
}

const btnGhost = {
  display: "inline-flex", alignItems: "center", height: 28,
  padding: "0 10px",
  background: "transparent",
  border: "1px solid var(--border-strong)",
  color: "var(--fg-2)",
  borderRadius: 6,
  fontSize: 12,
  cursor: "pointer",
};

/* ============================================================
   LEFT RAIL — Metric selector
============================================================ */
function MetricRail({ data, primary, setPrimary, secondaries, setSecondaries, filters, setFilters }) {
  const { METRICS, CHECKPOINTS } = data;
  const codeMetrics = METRICS.filter(m => m.group === "code");
  const procMetrics = METRICS.filter(m => m.group === "process");

  function toggleSecondary(id) {
    setSecondaries(prev =>
      prev.includes(id) ? prev.filter(x => x !== id) :
      prev.length >= 2 ? [prev[1], id] : [...prev, id]
    );
  }

  return (
    <aside style={{
      width: 240, flexShrink: 0,
      background: "var(--bg-1)", borderRight: "1px solid var(--border)",
      display: "flex", flexDirection: "column",
      overflow: "auto"
    }}>
      <RailSection title="PRIMARY METRIC">
        {METRICS.map(m => (
          <MetricRow key={m.id} metric={m} data={CHECKPOINTS}
                     active={primary === m.id}
                     onClick={() => setPrimary(m.id)}/>
        ))}
      </RailSection>

      <RailSection title="OVERLAY (MAX 2)">
        <div style={{ padding: "2px 12px 10px", fontSize: 11, color: "var(--fg-4)" }}>
          Dashed secondary lines on the graph
        </div>
        {METRICS.filter(m => m.id !== primary).map(m => {
          const active = secondaries.includes(m.id);
          const colorIdx = active ? secondaries.indexOf(m.id) : -1;
          return (
            <OverlayRow key={m.id} metric={m} active={active} colorIdx={colorIdx}
                        onClick={() => toggleSecondary(m.id)}/>
          );
        })}
      </RailSection>

      <RailSection title="LAYERS">
        <Check label="Build/test intervals" on={filters.intervals} onChange={v => setFilters(f => ({...f, intervals: v}))}/>
        <Check label="Annotation lane"       on={filters.annotations} onChange={v => setFilters(f => ({...f, annotations: v}))}/>
        <Check label="Suggested path"        on={filters.suggested} onChange={v => setFilters(f => ({...f, suggested: v}))} accent/>
      </RailSection>

      <RailSection title="ANNOTATION TYPES">
        {Object.entries(ANNOT_META).map(([k, meta]) => (
          <div key={k} style={{ display: "flex", alignItems: "center", gap: 8, padding: "5px 12px", fontSize: 12, color: "var(--fg-2)" }}>
            <span style={{ width: 8, height: 8, borderRadius: 2, background: meta.color }}/>
            <span>{meta.label}</span>
            <span style={{ marginLeft: "auto", color: "var(--fg-4)", fontFamily: "var(--mono)", fontSize: 10 }}>
              {data.ANNOTATIONS.filter(a => a.kind === k).length}
            </span>
          </div>
        ))}
      </RailSection>

      <div style={{ flex: 1 }}/>

      <div style={{ borderTop: "1px solid var(--border)", padding: "10px 12px", fontSize: 10.5, color: "var(--fg-4)", fontFamily: "var(--mono)", lineHeight: 1.5 }}>
        rendered with visx-style<br/>
        scales · custom svg
      </div>
    </aside>
  );
}
function RailSection({ title, children }) {
  return (
    <div style={{ padding: "14px 0 6px", borderBottom: "1px solid var(--border)" }}>
      <div style={{ fontSize: 10.5, letterSpacing: 1.2, fontFamily: "var(--mono)", color: "var(--fg-4)", padding: "0 12px 8px" }}>
        {title}
      </div>
      {children}
    </div>
  );
}
function MetricRow({ metric, data, active, onClick }) {
  // mini sparkline
  const vals = data.map(d => d[metric.id]);
  const mn = Math.min(...vals), mx = Math.max(...vals);
  const W = 60, H = 18;
  const pad = 1;
  const pts = vals.map((v, i) => {
    const x = (i / (vals.length - 1)) * (W - 2 * pad) + pad;
    const y = H - pad - ((v - mn) / (mx - mn || 1)) * (H - 2 * pad);
    return [x, y];
  });
  const d = pts.map((p, i) => `${i === 0 ? "M" : "L"}${p[0].toFixed(1)},${p[1].toFixed(1)}`).join(" ");
  const delta = vals[vals.length - 1] - vals[0];
  const improved = metric.better === "lower" ? delta < 0 : delta > 0;

  return (
    <button onClick={onClick} style={{
      display: "flex", alignItems: "center", gap: 8,
      width: "100%", padding: "7px 12px",
      background: active ? "var(--bg-3)" : "transparent",
      border: "none", borderLeft: `2px solid ${active ? "var(--accent)" : "transparent"}`,
      color: active ? "var(--fg)" : "var(--fg-2)",
      cursor: "pointer", textAlign: "left"
    }}>
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ fontSize: 12.5, fontWeight: active ? 600 : 500 }}>{metric.label}</div>
        <div style={{ fontSize: 10, color: "var(--fg-4)", fontFamily: "var(--mono)" }}>
          {metric.unit} · {metric.better === "lower" ? "lower = better" : "higher = better"}
        </div>
      </div>
      <svg width={W} height={H} style={{ flexShrink: 0 }}>
        <path d={d} fill="none" stroke={active ? "var(--accent)" : "var(--fg-4)"} strokeWidth={1.3}/>
      </svg>
      <span style={{
        fontFamily: "var(--mono)", fontSize: 10,
        color: improved ? "var(--good)" : "var(--bad)",
        width: 28, textAlign: "right"
      }}>
        {improved ? "▼" : "▲"}{Math.abs(delta).toFixed(metric.unit === "%" ? 1 : 0)}
      </span>
    </button>
  );
}
function OverlayRow({ metric, active, colorIdx, onClick }) {
  const color = colorIdx === 0 ? "var(--accent-2)" : colorIdx === 1 ? "var(--accent-3)" : "var(--fg-4)";
  return (
    <button onClick={onClick} style={{
      display: "flex", alignItems: "center", gap: 10,
      width: "100%", padding: "5px 12px",
      background: "transparent", border: "none",
      color: active ? "var(--fg)" : "var(--fg-3)",
      cursor: "pointer", textAlign: "left", fontSize: 12
    }}>
      <span style={{
        width: 14, height: 14, borderRadius: 3,
        border: `1px solid ${active ? color : "var(--border-strong)"}`,
        background: active ? color : "transparent",
        display: "grid", placeItems: "center",
      }}>
        {active && <Icon name="check" size={10} style={{ color: "#0b0e13" }}/>}
      </span>
      <span style={{ flex: 1 }}>{metric.label}</span>
      <span style={{ fontFamily: "var(--mono)", fontSize: 10, color: "var(--fg-4)" }}>{metric.unit}</span>
    </button>
  );
}
function Check({ label, on, onChange, accent }) {
  return (
    <label style={{
      display: "flex", alignItems: "center", gap: 10,
      padding: "5px 12px", fontSize: 12, color: "var(--fg-2)",
      cursor: "pointer"
    }}>
      <span style={{
        width: 14, height: 14, borderRadius: 3,
        border: `1px solid ${on ? (accent ? "var(--accent)" : "var(--fg-2)") : "var(--border-strong)"}`,
        background: on ? (accent ? "var(--accent)" : "var(--fg-2)") : "transparent",
        display: "grid", placeItems: "center",
      }}>
        {on && <Icon name="check" size={10} style={{ color: "#0b0e13" }}/>}
      </span>
      <span>{label}</span>
      <input type="checkbox" checked={on} onChange={e => onChange(e.target.checked)} style={{ display: "none" }}/>
    </label>
  );
}

/* ============================================================
   BOTTOM STRIP — Checkpoint timeline + explanation card
============================================================ */
function BottomStrip({ data, selection, onSelect }) {
  const { CHECKPOINTS, EXPLANATIONS } = data;
  const exp = getExplanation(selection, data);

  return (
    <div style={{
      flexShrink: 0, display: "grid", gridTemplateColumns: "1fr 420px",
      gap: 0, borderTop: "1px solid var(--border)", background: "var(--bg-1)",
      minHeight: 150, maxHeight: 200
    }}>
      {/* Checkpoint filmstrip */}
      <div style={{ padding: "10px 14px", borderRight: "1px solid var(--border)", overflow: "hidden" }}>
        <SectionTitle>CHECKPOINTS</SectionTitle>
        <div style={{ display: "flex", gap: 6, overflowX: "auto", paddingBottom: 6 }}>
          {CHECKPOINTS.map(c => {
            const isSel = selection?.kind === "checkpoint" && selection.i === c.i;
            return (
              <button key={c.i} onClick={() => onSelect({ kind: "checkpoint", i: c.i })}
                      style={{
                        flexShrink: 0, width: 110, padding: "7px 8px",
                        background: isSel ? "var(--bg-3)" : "var(--bg-2)",
                        border: `1px solid ${isSel ? "var(--accent)" : "var(--border)"}`,
                        borderRadius: 5, textAlign: "left", color: "var(--fg-2)", cursor: "pointer"
                      }}>
                <div style={{ display: "flex", alignItems: "center", gap: 5 }}>
                  <span style={{ width: 7, height: 7, borderRadius: "50%", background: STATUS_COLOR[c.status] }}/>
                  <span style={{ fontFamily: "var(--mono)", fontSize: 11, color: "var(--fg)" }}>c{c.i}</span>
                  <span style={{ fontFamily: "var(--mono)", fontSize: 9.5, color: "var(--fg-4)" }}>t+{c.t}</span>
                </div>
                <div style={{ fontSize: 10.5, color: "var(--fg-3)", marginTop: 4, whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>
                  {c.label.replace(/^c\d+\s+/, "")}
                </div>
              </button>
            );
          })}
        </div>
      </div>

      {/* Explanation card */}
      <div style={{ padding: "10px 14px 12px", overflow: "auto" }}>
        <SectionTitle>
          <Icon name="lightbulb" size={11} style={{ verticalAlign: -1, marginRight: 6, color: "var(--accent)" }}/>
          WHY IT MATTERS
        </SectionTitle>
        {exp ? (
          <div style={{ fontSize: 12, color: "var(--fg-2)", lineHeight: 1.55 }}>
            <div style={{ fontWeight: 600, color: "var(--fg)", marginBottom: 6 }}>{exp.title}</div>
            <ExpBlock label="WHAT"   text={exp.what}/>
            <ExpBlock label="WHY"    text={exp.why}/>
            <ExpBlock label="BETTER" text={exp.better} accent/>
          </div>
        ) : (
          <div style={{ fontSize: 12, color: "var(--fg-3)", lineHeight: 1.55, paddingTop: 4 }}>
            Click a <b style={{ color: "var(--fg-2)" }}>checkpoint</b>, a <b style={{ color: "var(--fg-2)" }}>build interval</b>,
            or an <b style={{ color: "var(--fg-2)" }}>annotation</b> to see a narrated explanation here.
          </div>
        )}
      </div>
    </div>
  );
}
function SectionTitle({ children }) {
  return (
    <div style={{ fontSize: 10.5, letterSpacing: 1.2, fontFamily: "var(--mono)", color: "var(--fg-4)", marginBottom: 8 }}>
      {children}
    </div>
  );
}
function ExpBlock({ label, text, accent }) {
  return (
    <div style={{ marginBottom: 6, display: "grid", gridTemplateColumns: "58px 1fr", gap: 10 }}>
      <span style={{ fontSize: 10, fontFamily: "var(--mono)", color: accent ? "var(--accent)" : "var(--fg-4)", paddingTop: 1 }}>
        {label}
      </span>
      <span>{text}</span>
    </div>
  );
}
function getExplanation(selection, data) {
  if (!selection) return null;
  if (selection.kind === "checkpoint") return data.EXPLANATIONS.ck[selection.i] || null;
  if (selection.kind === "interval")   return data.EXPLANATIONS.iv[`${selection.from}-${selection.to}`] || {
    title: `Interval c${selection.from}→c${selection.to} · ${selection.status}`,
    what: selection.status === "pass"
      ? "Build and tests remained green across this interval."
      : selection.status === "fail"
        ? "Build or tests were failing across this interval."
        : "Build/test status was not captured for this interval.",
    why: selection.status === "pass"
      ? "Short green intervals are the healthy baseline."
      : "Red time correlates with revert-probability; aim to keep intervals short.",
    better: selection.status === "pass"
      ? "No change needed."
      : "Split the refactor into smaller verifiable steps.",
  };
  if (selection.kind === "annotation") {
    const a = data.ANNOTATIONS.find(x => x.id === selection.id);
    if (!a) return null;
    return {
      title: a.title,
      what: a.detail,
      why: a.kind === "divergence"
        ? "This is where an alternative path would have diverged from the taken trajectory."
        : a.kind === "detected-refactor"
          ? "Identified by RefactoringMiner from the diff pattern across adjacent checkpoints."
          : a.kind === "ide-refactor"
            ? "Captured from the IDE refactoring history."
            : "A notable event in the session.",
      better: a.kind === "divergence"
        ? "Enable the suggested path in the left rail to compare."
        : "—",
    };
  }
  return null;
}

/* ============================================================
   RIGHT SLIDE-OUT — Detail panel
============================================================ */
function DetailPanel({ data, selection, onClose }) {
  const open = !!selection;
  return (
    <aside style={{
      position: "absolute", top: 0, right: 0, bottom: 0,
      width: 380,
      background: "var(--bg-1)", borderLeft: "1px solid var(--border)",
      transform: open ? "translateX(0)" : "translateX(100%)",
      transition: "transform 240ms cubic-bezier(.2,.9,.2,1)",
      display: "flex", flexDirection: "column",
      boxShadow: open ? "-18px 0 40px rgba(0,0,0,0.45)" : "none",
      zIndex: 10,
    }}>
      {open && <DetailContent data={data} selection={selection} onClose={onClose}/>}
    </aside>
  );
}
function DetailContent({ data, selection, onClose }) {
  let title, subtitle, body;
  if (selection.kind === "checkpoint") {
    const c = data.CHECKPOINTS[selection.i];
    title = c.label;
    subtitle = `t+${c.t}`;
    body = <CheckpointBody c={c} data={data}/>;
  } else if (selection.kind === "interval") {
    title = `Interval c${selection.from} → c${selection.to}`;
    subtitle = <><span style={{ color: STATUS_COLOR[selection.status] }}>● {selection.status}</span></>;
    body = <IntervalBody selection={selection} data={data}/>;
  } else if (selection.kind === "annotation") {
    const a = data.ANNOTATIONS.find(x => x.id === selection.id);
    title = a.title;
    subtitle = ANNOT_META[a.kind].label;
    body = <AnnotationBody a={a}/>;
  }

  return (
    <>
      <div style={{
        display: "flex", alignItems: "start", gap: 10,
        padding: "14px 16px", borderBottom: "1px solid var(--border)"
      }}>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ fontFamily: "var(--mono)", fontSize: 10.5, color: "var(--fg-4)", letterSpacing: 1.1 }}>
            {selection.kind.toUpperCase()}
          </div>
          <div style={{ fontSize: 15, fontWeight: 600, color: "var(--fg)", marginTop: 2 }}>{title}</div>
          <div style={{ fontSize: 11.5, color: "var(--fg-3)", fontFamily: "var(--mono)", marginTop: 2 }}>
            {subtitle}
          </div>
        </div>
        <button onClick={onClose} style={{
          background: "transparent", border: "1px solid var(--border-strong)",
          color: "var(--fg-3)", borderRadius: 4, width: 26, height: 26, cursor: "pointer",
          display: "grid", placeItems: "center"
        }}>
          <Icon name="close" size={12}/>
        </button>
      </div>
      <div style={{ flex: 1, overflow: "auto", padding: "12px 16px 18px" }}>
        {body}
      </div>
    </>
  );
}

function CheckpointBody({ c, data }) {
  // All metrics at this checkpoint
  const annotsHere = data.ANNOTATIONS.filter(a =>
    a.atCheckpoint === c.i || (a.spanFrom !== undefined && c.i >= a.spanFrom && c.i <= a.spanTo));
  return (
    <>
      <Subhead>METRICS AT c{c.i}</Subhead>
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 8, marginBottom: 14 }}>
        {data.METRICS.map(m => <MetricTile key={m.id} metric={m} value={c[m.id]} data={data}/>)}
      </div>

      <Subhead>STATUS</Subhead>
      <StatusRow c={c}/>

      <Subhead style={{ marginTop: 16 }}>ANNOTATIONS ({annotsHere.length})</Subhead>
      {annotsHere.length === 0 && <Empty>No annotations at this checkpoint.</Empty>}
      {annotsHere.map(a => <AnnotItem key={a.id} a={a}/>)}

      <Subhead style={{ marginTop: 16 }}>FILES TOUCHED (preview)</Subhead>
      <div style={{ border: "1px solid var(--border)", borderRadius: 5, overflow: "hidden" }}>
        {["src/order/CheckoutService.ts", "src/order/computeDiscount.ts", "test/order/checkout.spec.ts"]
          .map((f, i) => (
          <div key={f} style={{
            display: "flex", alignItems: "center", gap: 8,
            padding: "7px 10px", fontFamily: "var(--mono)", fontSize: 11,
            borderBottom: i < 2 ? "1px solid var(--border)" : "none",
            background: i % 2 ? "var(--bg-1)" : "var(--bg-2)"
          }}>
            <Icon name="file" size={11} style={{ color: "var(--fg-4)" }}/>
            <span style={{ color: "var(--fg-2)", flex: 1, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{f}</span>
            <span style={{ color: "var(--good)" }}>+{[34, 12, 7][i]}</span>
            <span style={{ color: "var(--bad)" }}>-{[11, 3, 2][i]}</span>
          </div>
        ))}
      </div>
    </>
  );
}
function IntervalBody({ selection, data }) {
  const from = data.CHECKPOINTS[selection.from];
  const to = data.CHECKPOINTS[selection.to];
  const deltas = data.METRICS.map(m => ({
    metric: m,
    from: from[m.id], to: to[m.id], diff: (to[m.id] - from[m.id]).toFixed(m.unit === "%" ? 1 : 0)
  }));
  return (
    <>
      <Subhead>DELTAS</Subhead>
      <div style={{ border: "1px solid var(--border)", borderRadius: 5, overflow: "hidden" }}>
        {deltas.map((d, i) => {
          const num = parseFloat(d.diff);
          const improved = d.metric.better === "lower" ? num < 0 : num > 0;
          const color = num === 0 ? "var(--fg-3)" : improved ? "var(--good)" : "var(--bad)";
          return (
            <div key={d.metric.id} style={{
              display: "grid", gridTemplateColumns: "1fr auto auto auto", gap: 10, alignItems: "center",
              padding: "8px 10px",
              borderBottom: i < deltas.length - 1 ? "1px solid var(--border)" : "none",
              background: i % 2 ? "var(--bg-1)" : "var(--bg-2)", fontSize: 12
            }}>
              <span style={{ color: "var(--fg-2)" }}>{d.metric.label}</span>
              <span style={{ fontFamily: "var(--mono)", color: "var(--fg-4)", fontSize: 11 }}>{d.from}</span>
              <span style={{ color: "var(--fg-4)" }}>→</span>
              <span style={{ fontFamily: "var(--mono)", color, fontSize: 11.5, width: 54, textAlign: "right" }}>
                {d.to} <span style={{ opacity: 0.65 }}>({num > 0 ? "+" : ""}{d.diff})</span>
              </span>
            </div>
          );
        })}
      </div>

      <Subhead style={{ marginTop: 16 }}>TIMING</Subhead>
      <KV k="Duration" v={`${(parseTime(to.t) - parseTime(from.t))} min`}/>
      <KV k="Status"   v={<span style={{ color: STATUS_COLOR[selection.status] }}>● {selection.status}</span>}/>
      <KV k="Churn"    v={`${to.churn} LOC`}/>
    </>
  );
}
function AnnotationBody({ a }) {
  const meta = ANNOT_META[a.kind];
  return (
    <>
      <Subhead>KIND</Subhead>
      <div style={{
        display: "inline-flex", alignItems: "center", gap: 8,
        padding: "5px 10px", borderRadius: 20,
        background: "var(--bg-2)", border: `1px solid ${meta.color}`, color: meta.color, fontSize: 12,
        marginBottom: 14
      }}>
        <Icon name={meta.iconName} size={12}/> {meta.label}
      </div>

      <Subhead>DETAIL</Subhead>
      <div style={{ fontSize: 12.5, color: "var(--fg-2)", lineHeight: 1.6, marginBottom: 14 }}>
        {a.detail}
      </div>

      {a.atCheckpoint !== undefined && <KV k="At" v={`c${a.atCheckpoint}`}/>}
      {a.spanFrom !== undefined && <KV k="Span" v={`c${a.spanFrom} → c${a.spanTo}`}/>}

      {a.kind === "divergence" && (
        <div style={{
          marginTop: 16, padding: 12,
          background: "rgba(110,231,199,0.06)", border: "1px solid rgba(110,231,199,0.3)",
          borderRadius: 6
        }}>
          <div style={{ display: "flex", alignItems: "center", gap: 8, color: "var(--accent)", fontSize: 12, marginBottom: 6, fontWeight: 600 }}>
            <Icon name="lightbulb"/> Suggested alternative available
          </div>
          <div style={{ fontSize: 11.5, color: "var(--fg-2)", lineHeight: 1.55 }}>
            Toggle <b>Suggested path</b> in the left rail to overlay the alternative trajectory starting from this checkpoint.
          </div>
        </div>
      )}
    </>
  );
}

function MetricTile({ metric, value, data }) {
  const vals = data.CHECKPOINTS.map(c => c[metric.id]);
  const mn = Math.min(...vals), mx = Math.max(...vals);
  const frac = (value - mn) / (mx - mn || 1);
  return (
    <div style={{
      padding: "8px 10px", background: "var(--bg-2)",
      border: "1px solid var(--border)", borderRadius: 5
    }}>
      <div style={{ fontSize: 10, color: "var(--fg-4)", fontFamily: "var(--mono)", letterSpacing: 0.6 }}>
        {metric.label.toUpperCase()}
      </div>
      <div style={{ fontFamily: "var(--mono)", fontSize: 17, color: "var(--fg)", marginTop: 2 }}>
        {value}<span style={{ fontSize: 11, color: "var(--fg-4)", marginLeft: 4 }}>{metric.unit}</span>
      </div>
      <div style={{ marginTop: 6, height: 3, background: "var(--bg-3)", borderRadius: 2, overflow: "hidden" }}>
        <div style={{
          width: `${frac * 100}%`, height: "100%",
          background: metric.better === "lower"
            ? `linear-gradient(90deg,var(--good),var(--bad))`
            : `linear-gradient(90deg,var(--bad),var(--good))`
        }}/>
      </div>
    </div>
  );
}
function StatusRow({ c }) {
  const badge = (ok, label) => (
    <div style={{
      display: "inline-flex", alignItems: "center", gap: 6,
      padding: "4px 8px", borderRadius: 4, fontSize: 11,
      background: ok === "pass" ? "rgba(74,222,128,0.1)" : ok === "fail" ? "rgba(248,113,113,0.1)" : "rgba(100,116,139,0.15)",
      color: ok === "pass" ? "var(--good)" : ok === "fail" ? "var(--bad)" : "var(--fg-3)",
      border: `1px solid ${ok === "pass" ? "rgba(74,222,128,0.3)" : ok === "fail" ? "rgba(248,113,113,0.3)" : "rgba(100,116,139,0.25)"}`
    }}>
      <Icon name={ok === "pass" ? "check" : ok === "fail" ? "x" : "question"} size={10}/>
      {label}
    </div>
  );
  return (
    <div style={{ display: "flex", gap: 6, marginBottom: 6 }}>
      {badge(c.status, "build " + c.status)}
      {badge(c.status === "fail" ? "fail" : "pass", "tests " + (c.status === "fail" ? "fail" : "pass"))}
    </div>
  );
}
function AnnotItem({ a }) {
  const meta = ANNOT_META[a.kind];
  return (
    <div style={{ display: "flex", gap: 8, padding: "6px 0", fontSize: 12 }}>
      <span style={{ color: meta.color, marginTop: 2 }}><Icon name={meta.iconName} size={11}/></span>
      <div style={{ flex: 1 }}>
        <div style={{ color: "var(--fg-2)" }}>{a.title}</div>
        <div style={{ color: "var(--fg-4)", fontFamily: "var(--mono)", fontSize: 10.5 }}>{a.detail}</div>
      </div>
    </div>
  );
}
function Subhead({ children, style }) {
  return <div style={{ fontSize: 10.5, letterSpacing: 1.2, fontFamily: "var(--mono)", color: "var(--fg-4)", marginBottom: 6, ...style }}>{children}</div>;
}
function KV({ k, v }) {
  return (
    <div style={{ display: "grid", gridTemplateColumns: "100px 1fr", gap: 10, fontSize: 12, padding: "3px 0" }}>
      <span style={{ color: "var(--fg-4)", fontFamily: "var(--mono)", fontSize: 11 }}>{k}</span>
      <span style={{ color: "var(--fg-2)" }}>{v}</span>
    </div>
  );
}
function Empty({ children }) {
  return <div style={{ fontSize: 11.5, color: "var(--fg-4)", fontStyle: "italic" }}>{children}</div>;
}
function parseTime(t) {
  const [h, m] = t.split(":").map(Number);
  return h * 60 + m;
}

Object.assign(window, {
  Header, MetricRail, BottomStrip, DetailPanel,
});
