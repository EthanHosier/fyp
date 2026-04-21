// TrajectoryGraph — the centerpiece.
// Custom SVG rendering, visx-style (linear scales, d3-shape curve approximation with straight lines).
//
// Features:
//  - primary metric line + points
//  - up to 2 overlaid secondary metrics (dashed, muted)
//  - interval status color segments on the x-axis rail
//  - pass/fail color on the line SEGMENT between checkpoints (same palette)
//  - annotation lane below the graph (IDE, detected, event, divergence)
//  - suggested "better path" as dashed ghost line branching from divergence
//  - hover crosshair + tooltip
//  - click checkpoint / interval → selects (opens detail panel)

const { useMemo, useState, useRef, useEffect } = React;

const STATUS_COLOR = {
  pass:    "var(--good)",
  fail:    "var(--bad)",
  unknown: "var(--unknown)",
};
const STATUS_DIM = {
  pass:    "rgba(95,184,101,0.22)",
  fail:    "rgba(229,87,101,0.26)",
  unknown: "rgba(111,115,122,0.28)",
};

const ANNOT_META = {
  "ide-refactor":     { color: "#548af7", label: "IDE refactor",     iconName: "wrench" },
  "detected-refactor":{ color: "#c27bff", label: "Detected refactor",iconName: "search" },
  "event":            { color: "#e8a33d", label: "Event",            iconName: "alert" },
  "divergence":       { color: "#7ee8d4", label: "Divergence",       iconName: "fork" },
};

function linearScale(domain, range) {
  const [d0, d1] = domain;
  const [r0, r1] = range;
  const fn = (v) => r0 + ((v - d0) / (d1 - d0)) * (r1 - r0);
  fn.invert = (r) => d0 + ((r - r0) / (r1 - r0)) * (d1 - d0);
  return fn;
}

function TrajectoryGraph({
  data,
  primary,
  secondaries,
  showIntervals,
  showAnnotations,
  showSuggested,
  selection,
  onSelect,
  density,
}) {
  const { CHECKPOINTS, INTERVALS, ANNOTATIONS, SUGGESTED_PATH, METRICS } = data;

  // Layout — measure container for width.
  const hostRef = useRef(null);
  const [w, setW] = useState(1000);
  useEffect(() => {
    const el = hostRef.current; if (!el) return;
    const ro = new ResizeObserver(([entry]) => setW(entry.contentRect.width));
    ro.observe(el);
    return () => ro.disconnect();
  }, []);

  const H = density === "compact" ? 360 : 440;
  const laneH = showAnnotations ? 54 : 0;
  const railH = 18;
  const margin = { top: 24, right: 28, bottom: 28 + railH + laneH, left: 58 };
  const innerW = Math.max(200, w - margin.left - margin.right);
  const innerH = H - margin.top - margin.bottom;

  // Scales — x by checkpoint index, y by primary metric.
  const xs = linearScale([0, CHECKPOINTS.length - 1], [0, innerW]);
  const primaryMetric = METRICS.find(m => m.id === primary);
  const yValues = CHECKPOINTS.map(c => c[primary]);
  const yMin = Math.min(...yValues);
  const yMax = Math.max(...yValues);
  const yPad = (yMax - yMin) * 0.22 || 1;
  const ys = linearScale([yMin - yPad, yMax + yPad], [innerH, 0]);

  // Per-secondary scale (independent, so each overlays meaningfully)
  const secScales = secondaries.map(sid => {
    const vals = CHECKPOINTS.map(c => c[sid]);
    const mn = Math.min(...vals), mx = Math.max(...vals);
    const pad = (mx - mn) * 0.22 || 1;
    return { id: sid, scale: linearScale([mn - pad, mx + pad], [innerH, 0]) };
  });

  // Y ticks
  const yTicks = useMemo(() => {
    const n = 5, lo = yMin - yPad, hi = yMax + yPad;
    const step = (hi - lo) / (n - 1);
    return Array.from({length: n}, (_, i) => lo + i * step);
  }, [primary]);

  // Hover state
  const [hoverIdx, setHoverIdx] = useState(null);
  const svgRef = useRef(null);

  function onMouseMove(e) {
    const rect = svgRef.current.getBoundingClientRect();
    const mx = e.clientX - rect.left - margin.left;
    if (mx < -4 || mx > innerW + 4) { setHoverIdx(null); return; }
    const raw = xs.invert(mx);
    const idx = Math.max(0, Math.min(CHECKPOINTS.length - 1, Math.round(raw)));
    setHoverIdx(idx);
  }
  function onMouseLeave() { setHoverIdx(null); }

  // Path generator (polyline with straight segments — crisp, IDE-y)
  const linePath = (getY) =>
    CHECKPOINTS.map((c, i) => `${i === 0 ? "M" : "L"}${xs(i).toFixed(1)},${getY(c).toFixed(1)}`).join(" ");

  const primaryPath = linePath(c => ys(c[primary]));

  // Suggested better path (connects from fromCheckpoint)
  const suggestedPath = showSuggested
    ? SUGGESTED_PATH.points
        .map((p, i) => `${i === 0 ? "M" : "L"}${xs(p.i).toFixed(1)},${ys(p.v).toFixed(1)}`)
        .join(" ")
    : null;

  // Annotation lane rows
  const laneOrder = ["ide-refactor", "detected-refactor", "event", "divergence"];
  const rowY = (kind) => laneOrder.indexOf(kind) * 12 + 6;

  return (
    <div ref={hostRef} style={{ width: "100%", position: "relative" }}>
      <svg
        ref={svgRef}
        width={w}
        height={H}
        onMouseMove={onMouseMove}
        onMouseLeave={onMouseLeave}
        style={{ display: "block", userSelect: "none" }}
      >
        <defs>
          <linearGradient id="primary-area" x1="0" x2="0" y1="0" y2="1">
            <stop offset="0%" stopColor="var(--accent)" stopOpacity="0.18"/>
            <stop offset="100%" stopColor="var(--accent)" stopOpacity="0"/>
          </linearGradient>
          <pattern id="unk-hatch" width="6" height="6" patternUnits="userSpaceOnUse" patternTransform="rotate(45)">
            <rect width="6" height="6" fill="rgba(100,116,139,0.18)"/>
            <rect width="2" height="6" fill="rgba(100,116,139,0.45)"/>
          </pattern>
        </defs>

        <g transform={`translate(${margin.left},${margin.top})`}>
          {/* Y gridlines + ticks */}
          {yTicks.map((t, i) => (
            <g key={i} transform={`translate(0,${ys(t)})`}>
              <line x1={0} x2={innerW} stroke="var(--border)" strokeDasharray="2 4" />
              <text x={-8} y={3} textAnchor="end" fontSize={10.5} fontFamily="var(--mono)" fill="var(--fg-4)">
                {t.toFixed(primaryMetric.unit === "%" ? 1 : 0)}
              </text>
            </g>
          ))}

          {/* Y axis label */}
          <text x={-margin.left + 6} y={-8} fontSize={10.5} fontFamily="var(--mono)" fill="var(--fg-3)">
            {primaryMetric.label.toUpperCase()} · {primaryMetric.unit}
          </text>

          {/* Checkpoint vertical guides */}
          {CHECKPOINTS.map(c => (
            <line key={c.i} x1={xs(c.i)} x2={xs(c.i)} y1={0} y2={innerH}
                  stroke="var(--border)" strokeOpacity={hoverIdx === c.i ? 0.9 : 0.25} strokeWidth={hoverIdx === c.i ? 1 : 1}/>
          ))}

          {/* Interval segments colored on the LINE itself (primary metric) */}
          {showIntervals && INTERVALS.map((iv, i) => {
            const c0 = CHECKPOINTS[iv.from];
            const c1 = CHECKPOINTS[iv.to];
            return (
              <line
                key={i}
                x1={xs(c0.i)} y1={ys(c0[primary])}
                x2={xs(c1.i)} y2={ys(c1[primary])}
                stroke={STATUS_COLOR[iv.status]}
                strokeOpacity={iv.status === "pass" ? 0.55 : 0.9}
                strokeWidth={3}
                strokeLinecap="round"
                style={{ cursor: "pointer" }}
                onClick={() => onSelect({ kind: "interval", from: iv.from, to: iv.to, status: iv.status })}
              />
            );
          })}

          {/* Primary line (thin, over the colored segments as a subtle core) */}
          <path d={primaryPath} fill="none" stroke="var(--accent)" strokeOpacity={showIntervals ? 0.0 : 0.9} strokeWidth={1.6}/>

          {/* Secondary metrics (dashed, muted) */}
          {secScales.map((s, i) => {
            const c = i === 0 ? "var(--accent-2)" : "var(--accent-3)";
            const d = linePath(row => s.scale(row[s.id]));
            return (
              <g key={s.id}>
                <path d={d} fill="none" stroke={c} strokeOpacity={0.55} strokeDasharray="3 3" strokeWidth={1.3}/>
                {CHECKPOINTS.map(row => (
                  <circle key={row.i} cx={xs(row.i)} cy={s.scale(row[s.id])} r={1.6} fill={c} fillOpacity={0.7}/>
                ))}
              </g>
            );
          })}

          {/* Suggested better path (ghost, dashed) */}
          {suggestedPath && (
            <g>
              <path d={suggestedPath} fill="none" stroke="var(--accent)"
                    strokeDasharray="5 4" strokeWidth={1.4} strokeOpacity={0.75} />
              {SUGGESTED_PATH.points.map((p, i) => (
                <circle key={i} cx={xs(p.i)} cy={ys(p.v)} r={2.6}
                        fill="var(--bg)" stroke="var(--accent)" strokeWidth={1.2} strokeDasharray="0"/>
              ))}
              {/* end label */}
              {(() => {
                const last = SUGGESTED_PATH.points[SUGGESTED_PATH.points.length - 1];
                return (
                  <g transform={`translate(${xs(last.i) + 8},${ys(last.v) - 6})`}>
                    <text fontSize={10} fontFamily="var(--mono)" fill="var(--accent)">suggested</text>
                  </g>
                );
              })()}
            </g>
          )}

          {/* Checkpoint points */}
          {CHECKPOINTS.map(c => {
            const isSel = selection?.kind === "checkpoint" && selection.i === c.i;
            const isHov = hoverIdx === c.i;
            const r = isSel ? 5.5 : isHov ? 4.5 : 3.4;
            const fill = "var(--bg)";
            const stroke = "var(--accent)";
            const cx = xs(c.i);
            const cy = ys(c[primary]);
            // glyph cluster y: just above the dot (with tooltip-safe offset)
            const gy = cy - 14;
            return (
              <g key={c.i} style={{ cursor: "pointer" }}
                 onClick={() => onSelect({ kind: "checkpoint", i: c.i })}>
                {/* test-run indicator: small circle with tick / dash / slashed */}
                {c.tests === "run" && (
                  <g transform={`translate(${cx - 8},${gy})`}>
                    <title>Tests run after this checkpoint</title>
                    <circle r={4.2} fill="rgba(95,184,101,0.18)" stroke="#5fb865" strokeWidth={1}/>
                    <path d="M-2 0 L-0.5 1.6 L2 -1.5" fill="none" stroke="#5fb865" strokeWidth={1.2} strokeLinecap="round" strokeLinejoin="round"/>
                  </g>
                )}
                {c.tests === "partial" && (
                  <g transform={`translate(${cx - 8},${gy})`}>
                    <title>Only a subset of tests run</title>
                    <circle r={4.2} fill="rgba(232,163,61,0.18)" stroke="#e8a33d" strokeWidth={1}/>
                    <path d="M-2 0 L2 0" stroke="#e8a33d" strokeWidth={1.2} strokeLinecap="round"/>
                  </g>
                )}
                {c.tests === "skipped" && (
                  <g transform={`translate(${cx - 8},${gy})`}>
                    <title>No tests were run after this refactoring</title>
                    <circle r={4.2} fill="rgba(229,87,101,0.14)" stroke="#e55765" strokeWidth={1} strokeDasharray="1.6 1.4"/>
                    <path d="M-2 -2 L2 2 M2 -2 L-2 2" stroke="#e55765" strokeWidth={1.1} strokeLinecap="round"/>
                  </g>
                )}
                {c.tests === "none" && (
                  <g transform={`translate(${cx - 8},${gy})`}>
                    <title>No tests exist in the touched area</title>
                    <circle r={4.2} fill="transparent" stroke="var(--fg-4)" strokeWidth={1} strokeDasharray="1.4 1.4"/>
                  </g>
                )}
                {/* IDE-able: lightning bolt marking a manual refactor the IDE could have done */}
                {c.manualIdeAble && (
                  <g transform={`translate(${cx + 8},${gy})`}>
                    <title>{`Manual refactor — IDE action available: ${c.manualIdeAble.action}`}</title>
                    <circle r={4.6} fill="rgba(232,163,61,0.18)" stroke="#e8a33d" strokeWidth={1}/>
                    <path d="M0.4 -2.6 L-1.6 0.3 L-0.1 0.3 L-0.6 2.6 L1.6 -0.4 L0.1 -0.4 Z"
                          fill="#e8a33d"/>
                  </g>
                )}
                {/* Vertical tick connecting glyph row to dot */}
                {(c.tests !== undefined || c.manualIdeAble) && (
                  <line x1={cx} x2={cx} y1={gy + 5} y2={cy - r - 1}
                        stroke="var(--border-strong)" strokeOpacity={0.55} strokeWidth={0.8}/>
                )}
                <circle cx={cx} cy={cy} r={r + 6} fill="transparent"/>
                <circle cx={cx} cy={cy} r={r} fill={fill} stroke={stroke} strokeWidth={1.6}/>
                {isSel && (
                  <circle cx={cx} cy={cy} r={r + 4}
                          fill="none" stroke="var(--accent)" strokeOpacity={0.35} strokeWidth={1}/>
                )}
              </g>
            );
          })}

          {/* X axis baseline */}
          <line x1={0} x2={innerW} y1={innerH} y2={innerH} stroke="var(--border-strong)"/>

          {/* X labels (checkpoint index, every other one if dense) */}
          {CHECKPOINTS.map(c => {
            const show = CHECKPOINTS.length <= 10 || c.i % 2 === 0 || c.i === CHECKPOINTS.length - 1;
            if (!show) return null;
            return (
              <text key={c.i} x={xs(c.i)} y={innerH + 14} textAnchor="middle"
                    fontSize={10} fontFamily="var(--mono)" fill="var(--fg-3)">
                c{c.i}
              </text>
            );
          })}

          {/* Interval status RAIL (under x-axis) */}
          {showIntervals && (
            <g transform={`translate(0,${innerH + 22})`}>
              <text x={-8} y={railH/2 + 3} textAnchor="end" fontSize={9.5} fontFamily="var(--mono)" fill="var(--fg-4)">BUILD</text>
              {INTERVALS.map((iv, i) => {
                const x0 = xs(iv.from), x1 = xs(iv.to);
                const isUnknown = iv.status === "unknown";
                return (
                  <g key={i} style={{ cursor: "pointer" }}
                     onClick={() => onSelect({ kind: "interval", from: iv.from, to: iv.to, status: iv.status })}>
                    <rect x={x0} y={0} width={x1 - x0} height={railH}
                          fill={isUnknown ? "url(#unk-hatch)" : STATUS_DIM[iv.status]}
                          stroke={STATUS_COLOR[iv.status]} strokeOpacity={0.4} strokeWidth={0.75}/>
                  </g>
                );
              })}
            </g>
          )}

          {/* Annotation lane */}
          {showAnnotations && (
            <g transform={`translate(0,${innerH + 22 + railH + 8})`}>
              {laneOrder.map((k, i) => (
                <g key={k}>
                  <text x={-8} y={rowY(k) + 4} textAnchor="end" fontSize={9} fontFamily="var(--mono)" fill="var(--fg-4)">
                    {ANNOT_META[k].label.toUpperCase()}
                  </text>
                  <line x1={0} x2={innerW} y1={rowY(k)} y2={rowY(k)} stroke="var(--border)" strokeOpacity={0.5}/>
                </g>
              ))}
              {ANNOTATIONS.map(a => {
                const m = ANNOT_META[a.kind];
                const isSel = selection?.kind === "annotation" && selection.id === a.id;
                if (a.spanFrom !== undefined) {
                  const x0 = xs(a.spanFrom), x1 = xs(a.spanTo);
                  return (
                    <g key={a.id} style={{ cursor: "pointer" }}
                       onClick={() => onSelect({ kind: "annotation", id: a.id })}>
                      <rect x={x0} y={rowY(a.kind) - 4} width={x1 - x0} height={8}
                            fill={m.color} fillOpacity={isSel ? 0.5 : 0.28} rx={2}
                            stroke={m.color} strokeOpacity={isSel ? 1 : 0.6} strokeWidth={1}/>
                    </g>
                  );
                }
                return (
                  <g key={a.id} transform={`translate(${xs(a.atCheckpoint)},${rowY(a.kind)})`}
                     style={{ cursor: "pointer" }}
                     onClick={() => onSelect({ kind: "annotation", id: a.id })}>
                    <circle r={isSel ? 4.5 : 3.2} fill={m.color} fillOpacity={isSel ? 1 : 0.85}/>
                    {isSel && <circle r={7} fill="none" stroke={m.color} strokeOpacity={0.5}/>}
                  </g>
                );
              })}
            </g>
          )}

          {/* Hover crosshair + tooltip */}
          {hoverIdx !== null && (() => {
            const c = CHECKPOINTS[hoverIdx];
            const tx = xs(c.i);
            const ty = ys(c[primary]);
            const tooltipW = 220;
            const tLeft = tx + tooltipW + 16 > innerW ? tx - tooltipW - 12 : tx + 12;
            return (
              <g>
                <line x1={tx} x2={tx} y1={0} y2={innerH} stroke="var(--accent)" strokeOpacity={0.35} strokeDasharray="3 3"/>
                <circle cx={tx} cy={ty} r={5} fill="var(--accent)" fillOpacity={0.15} stroke="var(--accent)"/>
                <foreignObject x={tLeft} y={Math.max(4, ty - 70)} width={tooltipW} height={120}>
                  <div style={{
                    background: "var(--bg-2)", border: "1px solid var(--border-strong)", borderRadius: 6,
                    padding: "8px 10px", fontSize: 11.5, color: "var(--fg-2)",
                    boxShadow: "0 6px 24px rgba(0,0,0,0.4)"
                  }}>
                    <div style={{ fontFamily: "var(--mono)", color: "var(--fg)", marginBottom: 2, fontSize: 11 }}>
                      {c.label}
                    </div>
                    <div style={{ fontFamily: "var(--mono)", color: "var(--fg-4)", fontSize: 10, marginBottom: 6 }}>
                      t+{c.t} · <span style={{ color: STATUS_COLOR[c.status] }}>● {c.status}</span>
                    </div>
                    <div style={{ display: "grid", gridTemplateColumns: "1fr auto", gap: "2px 10px", fontSize: 11 }}>
                      <span style={{ color: "var(--accent)" }}>● {primaryMetric.label}</span>
                      <span style={{ fontFamily: "var(--mono)" }}>{c[primary]}</span>
                      {secondaries.map((sid, i) => {
                        const mm = METRICS.find(m => m.id === sid);
                        const col = i === 0 ? "var(--accent-2)" : "var(--accent-3)";
                        return (
                          <React.Fragment key={sid}>
                            <span style={{ color: col }}>● {mm.label}</span>
                            <span style={{ fontFamily: "var(--mono)" }}>{c[sid]}</span>
                          </React.Fragment>
                        );
                      })}
                    </div>
                  </div>
                </foreignObject>
              </g>
            );
          })()}
        </g>
      </svg>
    </div>
  );
}

window.TrajectoryGraph = TrajectoryGraph;
window.ANNOT_META = ANNOT_META;
window.STATUS_COLOR = STATUS_COLOR;
