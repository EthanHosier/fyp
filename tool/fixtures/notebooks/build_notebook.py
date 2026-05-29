"""
Build experiments.ipynb from a list of (markdown, code) cells.

The first code cell auto-discovers every .jar under notebooks/libs/ (populated by
`./gradlew :analysis:notebookLibs`) and emits one @file:DependsOn per jar so the
notebook can import ReportAssembler, ScoringConfig and friends from the analysis
module directly.

Run from this directory:   python3 build_notebook.py
Output:                    experiments.ipynb
"""
import json
import pathlib

HERE = pathlib.Path(__file__).parent
LIBS = sorted(p.name for p in (HERE / "libs").glob("*.jar"))
if not LIBS:
    raise SystemExit(
        "notebooks/libs/ is empty. Stage runtime jars first:\n"
        "    ./gradlew :analysis:notebookLibs"
    )

cells = []

def md(text):
    cells.append({"cell_type": "markdown", "metadata": {}, "source": text.splitlines(keepends=True)})

def code(text):
    cells.append({
        "cell_type": "code",
        "metadata": {},
        "execution_count": None,
        "outputs": [],
        "source": text.splitlines(keepends=True),
    })

# ---------------------------------------------------------------- title

md(r"""# Experiments — reproducibility notebook

Reproduces every table, headline number and plot from Chapter 5 of the thesis. The
experiment loops live in this notebook directly: each cell loads the per-session
Phase-A dumps from `fixtures/{sessions,user-sessions,agent-sessions}/<id>/phase-a.json`
and produces an in-memory dataframe, no CSV intermediate.

The notebook depends on the analysis module's runtime classpath (about 200 MB of jars
including Equinox, JDT, RefactoringMiner, PMD). The `libs/` directory is gitignored;
regenerate it whenever a dependency changes with:

```bash
./gradlew :analysis:notebookLibs
```
""")

# ---------------------------------------------------------------- setup

md("## Setup — classpath, imports, helpers")

depends_block = "\n".join(f'@file:DependsOn("libs/{name}")' for name in LIBS)

code(f"""{depends_block}

import com.github.ethanhosier.analysis.metrics.derived.CleanlinessWeights
import com.github.ethanhosier.analysis.metrics.derived.ProcessScoreWeights
import com.github.ethanhosier.analysis.metrics.derived.ScoringConfig
import com.github.ethanhosier.analysis.pipeline.AnalysisReport
import com.github.ethanhosier.analysis.pipeline.PhaseAResult
import com.github.ethanhosier.analysis.pipeline.ReportAssembler
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.math.exp
import kotlin.math.ln
import kotlin.random.Random""")

code(r"""%use dataframe
%use kandy""")

code(r"""val readJson = Json {{ ignoreUnknownKeys = true; isLenient = true }}
// Walk up from cwd until we find a directory containing `fixtures/sessions`. This
// makes the notebook robust to being launched either from `tool/` (the documented
// setup) or from `tool/fixtures/notebooks/` (nbconvert's default cwd).
val repoRoot: File = run {{
    var d = File(".").canonicalFile
    while (d != null && !File(d, "fixtures/sessions").isDirectory) d = d.parentFile
    requireNotNull(d) {{ "could not find repo root (no ancestor contains fixtures/sessions)" }}
}}

// Phase-A dumps live per-session as `<root>/.../<session-dir>/phase-a.json`.
// Walk the tree to find every dump and use the session dir's path relative to
// `root` (with `/` → `-`) as the fixture id, so agent-sessions/<stack>/NN stays
// disambiguated across stacks while flat sessions/NNN or user-sessions/<name>-NN
// keep their natural ids.
fun loadCorpus(dir: String): Map<String, PhaseAResult> {{
    val root = File(repoRoot, dir)
    return root.walk()
        .filter {{ it.isFile && it.name == "phase-a.json" }}
        .map {{ it.parentFile }}
        .sortedBy {{ it.relativeTo(root).path }}
        .associate {{ sessionDir ->
            val sid = sessionDir.relativeTo(root).path.replace(File.separatorChar, '-')
            sid to readJson.decodeFromString(
                PhaseAResult.serializer(),
                File(sessionDir, "phase-a.json").readText(),
            )
        }}
}}

val injection = loadCorpus("fixtures/sessions")
val userStudy = loadCorpus("fixtures/user-sessions")
val agent     = loadCorpus("fixtures/agent-sessions")

println("loaded: injection=${{injection.size}}, user-study=${{userStudy.size}}, agent=${{agent.size}}")""".replace("{{", "{").replace("}}", "}"))

code(r"""// Helpers — Kendall τ-b and top-N hit rate over divergence-point rankings.
// Match RankingMetrics.kt in the analysis module (inlined here to keep
// the notebook self-contained for verification).

// Kendall τ-b: correlation between two rankings of the same items.
// −1 means fully inverted, 0 means independent, 1 means identical.
// Counts concordant vs discordant pairs across all i < j; the -b
// variant adjusts the denominator for ties so short rankings still
// score in [-1, 1].
fun kendallTauB(a: List<Int>, b: List<Int>): Double {
    if (a.size < 2 || b.size < 2) return if (a == b) 1.0 else Double.NaN
    val n = a.size.coerceAtMost(b.size)
    var concordant = 0; var discordant = 0; var tiesA = 0; var tiesB = 0
    for (i in 0 until n - 1) for (j in i + 1 until n) {
        val da = a[i].compareTo(a[j])
        val db = b[i].compareTo(b[j])
        when {
            da == 0 && db == 0 -> { tiesA++; tiesB++ }
            da == 0 -> tiesA++
            db == 0 -> tiesB++
            da * db > 0 -> concordant++
            else -> discordant++
        }
    }
    val denom = kotlin.math.sqrt(
        ((concordant + discordant + tiesA).toDouble()) *
        ((concordant + discordant + tiesB).toDouble())
    )
    return if (denom == 0.0) Double.NaN else (concordant - discordant) / denom
}

// Top-N hit rate: fraction of the baseline's top-N items that also
// appear in the perturbed top-N. 1.0 = the top-N set is preserved
// (internal order irrelevant). On a ranking shorter than N, both
// sides are taken as their full ranking, so the metric trivially
// returns 1.0 unless the underlying set actually changed.
fun topNHitRate(baseline: List<Int>, perturbed: List<Int>, n: Int): Double {
    if (baseline.isEmpty()) return 1.0
    val k = minOf(n, baseline.size)
    val a = baseline.take(k).toSet()
    val b = perturbed.take(k).toSet()
    return (a intersect b).size.toDouble() / k
}

// Ranking — the production ordering the two metrics above consume.
// Divergence points sorted by descending magnitude; ties broken by
// ascending stepIndex so the ranking is a deterministic function of
// the report. Ties matter on this corpus because COMMIT_GAP DPs all
// carry magnitude exactly W_cg (the commit-flip alt drops the gap
// count from 1 to 0 at trajectory-final), and saturated DPs share a
// clamp-induced magnitude.
fun rankingOf(report: AnalysisReport): List<Int> =
    report.divergencePoints
        .sortedWith(
            compareByDescending<com.github.ethanhosier.analysis.pipeline.DivergencePoint> { it.magnitude }
                .thenBy { it.stepIndex }
        )
        .map { it.stepIndex }

// Sessions whose ranking has at least two items, so τ-b and top-1 are not
// mechanically 1.0. Uses raw divergencePoints.size — matching what the
// dashboard shows and what rankingOf(report) ranks over.
fun rankableSubset(corpus: Map<String, PhaseAResult>): Map<String, PhaseAResult> =
    corpus.filter { (_, phaseA) ->
        ReportAssembler.assemble(phaseA, ScoringConfig.PRODUCTION)
            .divergencePoints.size >= 2
    }""")

# ---------------------------------------------------------------- §5.1.1 sensitivity

md(r"""## §5.1 Testing the score formula

### §5.1.1 Single-knob sensitivity

For each (fixture, weight, factor) triple, scales one weight in ScoringConfig.PRODUCTION
by `factor`, reassembles the report via Phase B, and compares the perturbed divergence-point
ranking against production.""")

code(r"""val FACTORS = listOf(0.1, 0.25, 0.5, 0.75, 1.25, 1.5, 2.0, 4.0, 10.0)

data class Knob(val name: String, val group: String, val perturb: (ScoringConfig, Double) -> ScoringConfig)

val KNOBS = listOf(
    Knob("gain", "process")        { c, f -> c.copy(process = c.process.copy(gain = c.process.gain * f)) },
    Knob("broken", "process")      { c, f -> c.copy(process = c.process.copy(broken = c.process.broken * f)) },
    Knob("skipTests", "process")   { c, f -> c.copy(process = c.process.copy(skipTests = c.process.skipTests * f)) },
    Knob("manualIde", "process")   { c, f -> c.copy(process = c.process.copy(manualIde = c.process.manualIde * f)) },
    Knob("length", "process")      { c, f -> c.copy(process = c.process.copy(length = c.process.length * f)) },
    Knob("commitGap", "process")   { c, f -> c.copy(process = c.process.copy(commitGap = c.process.commitGap * f)) },
    Knob("lag", "process")         { c, f -> c.copy(process = c.process.copy(lag = c.process.lag * f)) },
    Knob("cognitive", "cleanliness")   { c, f -> c.copy(cleanliness = c.cleanliness.copy(cognitive = c.cleanliness.cognitive * f)) },
    Knob("coupling", "cleanliness")    { c, f -> c.copy(cleanliness = c.cleanliness.copy(coupling = c.cleanliness.coupling * f)) },
    Knob("duplication", "cleanliness") { c, f -> c.copy(cleanliness = c.cleanliness.copy(duplication = c.cleanliness.duplication * f)) },
    Knob("readability", "cleanliness") { c, f -> c.copy(cleanliness = c.cleanliness.copy(readability = c.cleanliness.readability * f)) },
    Knob("smells", "cleanliness")      { c, f -> c.copy(cleanliness = c.cleanliness.copy(smells = c.cleanliness.smells * f)) },
    Knob("cohesion", "cleanliness")    { c, f -> c.copy(cleanliness = c.cleanliness.copy(cohesion = c.cleanliness.cohesion * f)) },
)

// Saturation: a divergence point is "saturated" if any of its
// referenced alts' terminal process score or the matching user
// terminal score sits at the [0, 100] clamp boundary. Saturated DPs
// have weight-invariant magnitudes (the clamp eats any perturbation),
// so they inflate τ artificially when included; reported separately
// to bound the clipping-as-confound concern in §5.1.
fun saturatedDpCount(report: AnalysisReport): Int {
    val userProc = report.checkpoints.associate { it.sha to it.derivedMetrics.process.total }
    var n = 0
    for (dp in report.divergencePoints) {
        val touches = dp.altTrajectoryIndexes.mapNotNull { report.alternativeTrajectories.getOrNull(it) }.any { alt ->
            val a = alt.altCheckpoints.lastOrNull()?.derivedMetrics?.process?.total
            val u = userProc[alt.userToSha]
            (a != null && (a == 0 || a == 100)) || (u != null && (u == 0 || u == 100))
        }
        if (touches) n += 1
    }
    return n
}

fun sensitivitySweep(name: String, corpus: Map<String, PhaseAResult>) =
    corpus.flatMap { (fixture, phaseA) ->
        val baseline = ReportAssembler.assemble(phaseA, ScoringConfig.PRODUCTION)
        val baselineRanking = rankingOf(baseline)
        val baselineSat = saturatedDpCount(baseline)
        KNOBS.flatMap { knob ->
            FACTORS.map { factor ->
                val perturbed = ReportAssembler.assemble(phaseA, knob.perturb(ScoringConfig.PRODUCTION, factor))
                val pr = rankingOf(perturbed)
                mapOf(
                    "set" to name, "fixture" to fixture, "group" to knob.group, "weight" to knob.name, "factor" to factor,
                    "tau" to kendallTauB(baselineRanking, pr),
                    "top1" to topNHitRate(baselineRanking, pr, 1),
                    "top3" to topNHitRate(baselineRanking, pr, 3),
                    "top5" to topNHitRate(baselineRanking, pr, 5),
                    "baseline_size" to baselineRanking.size,
                    "baseline_sat" to baselineSat,
                )
            }
        }
    }.toDataFrame()

val sensInj    = sensitivitySweep("Injection (rankable, 15)",   rankableSubset(injection))
val sensUser   = sensitivitySweep("User study (rankable, 25)",  rankableSubset(userStudy))
val sensAgent  = sensitivitySweep("Agent (rankable, 20)",       rankableSubset(agent))""")

md("Headline table — top-1 stability, τ = 1.0 share, and baseline saturation per session set (reproduces Table 5.1).")

code(r"""fun pct(p: Double) = "%.1f%%".format(p * 100)

val sensHeadline = listOf(sensInj, sensUser, sensAgent).map { df ->
    val n = df.rowsCount()
    val baselineDps = df["baseline_size"].cast<Int>().sum()
    mapOf(
        "session set" to (df["set"][0] as String),
        "rows" to n,
        "tau=1.0" to pct(df.filter { "tau"<Double>() == 1.0 }.rowsCount().toDouble() / n),
        "top-1=1" to pct(df.filter { "top1"<Double>() == 1.0 }.rowsCount().toDouble() / n),
        "tau<0"   to pct(df.filter { "tau"<Double>() < 0.0 }.rowsCount().toDouble() / n),
        "baseline saturation" to pct(df["baseline_sat"].cast<Int>().sum().toDouble() / baselineDps.coerceAtLeast(1)),
    )
}.toDataFrame()
sensHeadline""")

md("Per-knob top-1 disruption counts on the user-study session set (reproduces Table 5.2).")

code(r"""val perKnob = sensUser
    .groupBy("weight")
    .aggregate {
        count { "top1"<Double>() < 1.0 } into "top1_disrupt"
        count() into "rows"
    }
    .sortByDesc("top1_disrupt")
perKnob""")

code(r"""perKnob.plot {
    bars {
        x("weight"); y("top1_disrupt")
    }
    layout { title = "Per-knob top-1 disruption counts (user-study set)" }
}""")

# ---------------------------------------------------------------- §5.1 multi-knob MC

md(r"""### Multi-knob Monte Carlo

Every weight scaled by an independent log-normal $\exp(\sigma Z)$, $\sigma = \ln 2$, drawn
from a per-fixture seeded RNG. 200 samples per fixture; mean τ and top-N hit rate against
production. The headline dataframe at the end of this cell reproduces Table 5.3.""")

code(r"""fun nextGaussian(rng: Random): Double {
    var u: Double; var v: Double; var s: Double
    do {
        u = rng.nextDouble(-1.0, 1.0); v = rng.nextDouble(-1.0, 1.0); s = u * u + v * v
    } while (s >= 1.0 || s == 0.0)
    return u * kotlin.math.sqrt(-2.0 * ln(s) / s)
}

// Multi-knob perturbation: every weight gets an independent
// multiplicative factor drawn from exp(σ · Z), Z ~ N(0, 1). With
// σ = ln 2 the factor lands inside [×0.25, ×4] for ~95% of samples
// and the distribution is symmetric in log-space, so up-scalings and
// down-scalings get the same weight.
fun perturbAll(rng: Random, sigma: Double): ScoringConfig {
    fun f() = exp(sigma * nextGaussian(rng))
    val p = ScoringConfig.PRODUCTION
    return p.copy(
        process = ProcessScoreWeights(
            gain = p.process.gain * f(), broken = p.process.broken * f(),
            skipTests = p.process.skipTests * f(), manualIde = p.process.manualIde * f(),
            length = p.process.length * f(), commitGap = p.process.commitGap * f(),
            lag = p.process.lag * f(),
        ),
        cleanliness = CleanlinessWeights(
            cognitive = p.cleanliness.cognitive * f(), coupling = p.cleanliness.coupling * f(),
            duplication = p.cleanliness.duplication * f(), readability = p.cleanliness.readability * f(),
            smells = p.cleanliness.smells * f(), cohesion = p.cleanliness.cohesion * f(),
        ),
    )
}

fun mcSweep(name: String, corpus: Map<String, PhaseAResult>, samples: Int = 200, seed: Long = 1L) =
    corpus.entries.withIndex().flatMap { (idx, entry) ->
        val (fixture, phaseA) = entry
        val baseline = rankingOf(ReportAssembler.assemble(phaseA, ScoringConfig.PRODUCTION))
        val rng = Random(seed + idx * 1_000_003L)
        (0 until samples).map { s ->
            val cfg = perturbAll(rng, ln(2.0))
            val pr = rankingOf(ReportAssembler.assemble(phaseA, cfg))
            mapOf(
                "set" to name, "fixture" to fixture, "sample" to s,
                "tau" to kendallTauB(baseline, pr),
                "top1" to topNHitRate(baseline, pr, 1),
                "top3" to topNHitRate(baseline, pr, 3),
                "top5" to topNHitRate(baseline, pr, 5),
                "baseline_size" to baseline.size,
            )
        }
    }.toDataFrame()

val mcInj   = mcSweep("Injection (rankable, 15)",   rankableSubset(injection))
val mcUser  = mcSweep("User study (rankable, 25)",  rankableSubset(userStudy))
val mcAgent = mcSweep("Agent (rankable, 20)",       rankableSubset(agent))

val mcHeadline = listOf(mcInj, mcUser, mcAgent).map { df ->
    val n = df.rowsCount()
    mapOf(
        "session set" to (df["set"][0] as String),
        "samples" to n,
        "mean tau" to "%.3f".format(df["tau"].cast<Double>().mean()),
        "top-1=1" to pct(df.filter { "top1"<Double>() == 1.0 }.rowsCount().toDouble() / n),
        "top-3=1" to pct(df.filter { "top3"<Double>() == 1.0 }.rowsCount().toDouble() / n),
        "top-5=1" to pct(df.filter { "top5"<Double>() == 1.0 }.rowsCount().toDouble() / n),
    )
}.toDataFrame()
mcHeadline""")

code(r"""// τ distribution per session set — three histograms in series, one per set.
// (Faceting / per-aesthetic colouring varies across Kandy versions; emitting
// one plot per call is the version-robust choice.)
listOf(mcInj, mcUser, mcAgent).forEach { df ->
    val setName = df["set"][0] as String
    df.plot {
        histogram("tau")
        layout { title = "τ distribution — $setName" }
    }
}""")

# ---------------------------------------------------------------- §5.1.2 ablation

md(r"""### §5.1.2 Ablation

Power-set sweep over the six process-side weights ($2^6 = 64$ variants per fixture).
Cleanliness weights stay at production across all variants because the sensitivity sweep
already established they don't shift the ranking.""")

code(r"""val PROC_TERMS = listOf("gain", "broken", "skipTests", "manualIde", "length", "commitGap", "lag")
val allTerms = PROC_TERMS.toSortedSet()

// Ablation: build a ScoringConfig in which the named process-side
// weights keep their production values and every weight outside the
// set is zeroed. Cleanliness weights stay at production across all
// variants (sensitivity sweep showed they don't move the ranking).
// Iterating over the 2^6 power-set of process weights asks which
// subsets recover the production ranking under Kendall τ.
fun ablate(active: Set<String>): ScoringConfig {
    val p = ScoringConfig.PRODUCTION.process
    return ScoringConfig.PRODUCTION.copy(
        process = ProcessScoreWeights(
            gain      = if ("gain"      in active) p.gain      else 0.0,
            broken    = if ("broken"    in active) p.broken    else 0.0,
            skipTests = if ("skipTests" in active) p.skipTests else 0.0,
            manualIde = if ("manualIde" in active) p.manualIde else 0.0,
            length    = if ("length"    in active) p.length    else 0.0,
            commitGap = if ("commitGap" in active) p.commitGap else 0.0,
            lag       = if ("lag"       in active) p.lag       else 0.0,
        )
    )
}

fun powerSet(items: List<String>): List<Set<String>> {
    val out = mutableListOf<Set<String>>()
    val n = items.size
    for (mask in 0 until (1 shl n)) {
        val s = mutableSetOf<String>()
        for (i in 0 until n) if ((mask shr i) and 1 == 1) s += items[i]
        out += s
    }
    return out
}

fun ablationSweep(name: String, corpus: Map<String, PhaseAResult>) =
    corpus.flatMap { (fixture, phaseA) ->
        val baseline = ReportAssembler.assemble(phaseA, ScoringConfig.PRODUCTION)
        val baselineRanking = rankingOf(baseline)
        val baselineMag = baseline.divergencePoints.sumOf { kotlin.math.abs(it.magnitude) }
        powerSet(PROC_TERMS).map { active ->
            val perturbed = ReportAssembler.assemble(phaseA, ablate(active))
            val pr = rankingOf(perturbed)
            val perturbedMag = perturbed.divergencePoints.sumOf { kotlin.math.abs(it.magnitude) }
            mapOf(
                "set" to name, "fixture" to fixture,
                "variant" to if (active.isEmpty()) "(none)" else active.toSortedSet().joinToString("+"),
                "active_count" to active.size,
                "tau" to kendallTauB(baselineRanking, pr),
                "perturbed_mag" to perturbedMag,
                "baseline_mag" to baselineMag,
            )
        }
    }.toDataFrame()

val ablInj   = ablationSweep("Injection (45)",  injection)
val ablUser  = ablationSweep("User study (30)", userStudy)
val ablAgent = ablationSweep("Agent (48)",      agent)

// Rankable-subset re-runs for the cross-set τ comparison in Table 5.7.
// The magnitude-side tables 5.4/5.5/5.6 stay on the full corpus.
val ablInjRankable  = ablationSweep("Injection (rankable, 15)",  rankableSubset(injection))
val ablUserRankable = ablationSweep("User study (rankable, 25)", rankableSubset(userStudy))""")

md("Monotone-by-active-count on the injection set (reproduces Table 5.4).")

code(r"""ablInj.groupBy("active_count").aggregate {
    val sumPert = sum { "perturbed_mag"<Double>() }
    val sumBase = sum { "baseline_mag"<Double>() }
    (if (sumBase > 0.0) sumPert / sumBase else 0.0) into "sum_over_sum"
    count() into "n"
}.sortBy("active_count")""")

md(r"""Per-term single-knob recovery on the injection set (reproduces Table 5.5): each
process-side term active alone, the other five zeroed. Columns: mean $\tau$ against
production ranking, mean per-fixture recovery, and sum-over-sum recovery.""")

code(r"""fun soloRow(df: AnyFrame, term: String): Map<String, Any> {
    val rows = df.filter { "variant"<String>() == term }
    // mean per-fixture recovery: |perturbed_mag| / |baseline_mag|, averaged
    val perFixture = (0 until rows.rowsCount()).map { i ->
        val b = rows["baseline_mag"][i] as Double
        val p = rows["perturbed_mag"][i] as Double
        if (b > 0.0) p / b else 1.0
    }
    val meanRec = perFixture.average()
    val sumPert = rows["perturbed_mag"].cast<Double>().sum()
    val sumBase = rows["baseline_mag"].cast<Double>().sum()
    val sumOverSum = if (sumBase > 0.0) sumPert / sumBase else 0.0
    return mapOf(
        "term" to term,
        "mean recovery" to "%.3f".format(meanRec),
        "sum/sum" to "%.3f".format(sumOverSum),
    )
}

PROC_TERMS.map { soloRow(ablInj, it) }
    .sortedByDescending { (it["sum/sum"] as String).toDouble() }
    .toDataFrame()""")

md(r"""Per-term leave-one-out recovery on the injection set (reproduces Table 5.6): five
process-side terms active, one removed. Removing $W_{\textsc{l}}$ should produce the
largest sum/sum drop; removing $W_{\textsc{g}}$ should push sum/sum above 1.0 (the
regulariser-against-penalties effect discussed in the chapter).""")

code(r"""fun looRow(df: AnyFrame, removed: String): Map<String, Any> {
    val target = (allTerms - removed).toSortedSet()
    val rows = df.filter { "variant"<String>().split("+").toSortedSet() == target }
    val perFixture = (0 until rows.rowsCount()).map { i ->
        val b = rows["baseline_mag"][i] as Double
        val p = rows["perturbed_mag"][i] as Double
        if (b > 0.0) p / b else 1.0
    }
    val meanRec = perFixture.average()
    val sumPert = rows["perturbed_mag"].cast<Double>().sum()
    val sumBase = rows["baseline_mag"].cast<Double>().sum()
    val sumOverSum = if (sumBase > 0.0) sumPert / sumBase else 0.0
    return mapOf(
        "removed term" to removed,
        "mean recovery" to "%.3f".format(meanRec),
        "sum/sum" to "%.3f".format(sumOverSum),
    )
}

PROC_TERMS.map { looRow(ablInj, it) }
    .sortedBy { (it["sum/sum"] as String).toDouble() }
    .toDataFrame()""")

md("Cross-set leave-one-out τ (reproduces Table 5.7).")

code(r"""fun looTau(df: AnyFrame, removed: String): Double {
    val target = (allTerms - removed).toSortedSet()
    val rows = df.filter { "variant"<String>().split("+").toSortedSet() == target }
    return rows["tau"].cast<Double>().mean()
}

PROC_TERMS.sorted().map { removed ->
    mapOf(
        "removed" to removed,
        "injection τ (n=15)"   to "%.3f".format(looTau(ablInjRankable,  removed)),
        "user-study τ (n=25)" to "%.3f".format(looTau(ablUserRankable, removed)),
    )
}.toDataFrame()""")

# ---------------------------------------------------------------- §5.2 detector

md(r"""## §5.2 Testing the detector

### §5.2.1 Inter-rater κ

Loads the three rater manifests, computes pair-wise Cohen's κ per kind across the 45
sessions (reproduces Table 5.8).""")

code(r"""fun parseCsvRow(line: String): List<String> {
    val out = mutableListOf<String>()
    val cur = StringBuilder()
    var inQuotes = false; var i = 0
    while (i < line.length) {
        val c = line[i]
        when {
            inQuotes && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> { cur.append('"'); i += 2; continue }
            inQuotes && c == '"' -> inQuotes = false
            !inQuotes && c == '"' -> inQuotes = true
            !inQuotes && c == ',' -> { out += cur.toString(); cur.setLength(0) }
            else -> cur.append(c)
        }
        i++
    }
    out += cur.toString()
    return out
}

fun loadManifest(path: String): Map<String, Set<String>> {
    val lines = File(repoRoot, path).readLines()
    val header = parseCsvRow(lines[0])
    val sid = header.indexOf("session_id")
    val kinds = header.indexOf("expected_kinds")
    return lines.drop(1).filter { it.isNotBlank() }
        .associate {
            val row = parseCsvRow(it)
            row[sid] to row[kinds].split(";").mapNotNull { k -> k.trim().ifEmpty { null } }.toSet()
        }
}

// Cohen's κ: inter-rater agreement on a binary judgement,
// adjusted for chance. κ = (po − pe) / (1 − pe), where po is the
// fraction of items the two raters agreed on, and pe is the
// agreement expected if both rated independently with their own
// marginals. Landis-Koch interpretation: <0.20 slight, 0.21-0.40
// fair, 0.41-0.60 moderate, 0.61-0.80 substantial, 0.81-1.00 almost
// perfect. NaN when both raters labelled every item the same way
// (denominator collapses).
fun cohenKappa(yy: Int, yn: Int, ny: Int, nn: Int): Double {
    val n = yy + yn + ny + nn
    if (n == 0) return Double.NaN
    val po = (yy + nn).toDouble() / n
    val r1y = (yy + yn).toDouble() / n
    val r2y = (yy + ny).toDouble() / n
    val pe = r1y * r2y + (1 - r1y) * (1 - r2y)
    return if (pe == 1.0) if (po == 1.0) 1.0 else Double.NaN else (po - pe) / (1 - pe)
}

val raters = mapOf(
    "Author" to loadManifest("fixtures/sessions/manifest-v2.csv"),
    "P1"     to loadManifest("fixtures/sessions/manifest-v2-will.csv"),
    "P2"     to loadManifest("fixtures/sessions/manifest-v2-yukie.csv"),
)
val pairs = listOf("Author" to "P1", "Author" to "P2", "P1" to "P2")
val kinds = listOf("ORDERING", "IDE_REPLAY", "REWORK", "HYGIENE")

kinds.map { kind ->
    val row = mutableMapOf<String, Any>("kind" to kind)
    for ((a, b) in pairs) {
        val ax = raters[a]!!; val bx = raters[b]!!
        val common = ax.keys intersect bx.keys
        var yy = 0; var yn = 0; var ny = 0; var nn = 0
        for (sid in common) {
            val ai = kind in (ax[sid] ?: emptySet())
            val bi = kind in (bx[sid] ?: emptySet())
            when { ai && bi -> yy++; ai && !bi -> yn++; !ai && bi -> ny++; else -> nn++ }
        }
        row["$a vs $b"] = "%.3f".format(cohenKappa(yy, yn, ny, nn))
    }
    row
}.toDataFrame()""")

# ---------------------------------------------------------------- §5.2.2 divergence experiment

md(r"""### §5.2.2 Divergence experiment

Per-kind precision and recall against the author manifest, on the 45-session injection set.""")

code(r"""// Decode each session's report and check against the manifest's expected_kinds.
val authorManifest = raters["Author"]!!

val divRows = injection.map { (sid, phaseA) ->
    val report = ReportAssembler.assemble(phaseA, ScoringConfig.PRODUCTION)
    val observed = report.divergencePoints.groupBy { it.kind.name }.mapValues { it.value.size }
    val expected = authorManifest[sid] ?: emptySet()
    mapOf(
        "session_id" to sid,
        "expected" to expected.sorted().joinToString(";"),
    ) + kinds.associateWith { k -> observed[k] ?: 0 }
}.toDataFrame()
divRows.head(5)""")

md("Precision / recall per kind (reproduces Table 5.9).")

code(r"""kinds.map { kind ->
    var tp = 0; var fp = 0; var fn = 0
    for (i in 0 until divRows.rowsCount()) {
        val row = divRows[i]
        val isExp = kind in (row["expected"] as String).split(";").mapNotNull { it.trim().ifEmpty { null } }.toSet()
        val obs = (row[kind] as Int) > 0
        when {
            isExp && obs -> tp++
            isExp && !obs -> fn++
            !isExp && obs -> fp++
        }
    }
    val p = if (tp + fp > 0) tp.toDouble() / (tp + fp) else Double.NaN
    val r = if (tp + fn > 0) tp.toDouble() / (tp + fn) else Double.NaN
    mapOf(
        "kind" to kind, "TP" to tp, "FP" to fp, "FN" to fn,
        "precision" to "%.3f".format(p),
        "recall (any-expected)" to "%.3f".format(r),
    )
}.toDataFrame()""")

md(r"""Detection quality (reproduces Table 5.10) — divergence points fired per kind and the
fraction whose best alternative beats the user's trajectory under production weights.""")

code(r"""kinds.map { kind ->
    val divKind = com.github.ethanhosier.analysis.pipeline.DivergenceKind.valueOf(kind)
    var fires = 0
    var beats = 0
    for ((_, phaseA) in injection) {
        val report = ReportAssembler.assemble(phaseA, ScoringConfig.PRODUCTION)
        for (dp in report.divergencePoints) {
            if (dp.kind == divKind) {
                fires += 1
                if (dp.magnitude > 0.0) beats += 1
            }
        }
    }
    val frac = if (fires > 0) beats.toDouble() / fires else Double.NaN
    mapOf(
        "kind" to kind,
        "DPs fired" to fires,
        "beats user" to beats,
        "beats fraction" to "%.3f".format(frac),
    )
}.toDataFrame()""")

md(r"""Injection prominence (reproduces Table 5.11) — the playbook's `pattern` column maps each
session to one deliberately-injected divergence kind; for each session where the detector caught
that kind with a positive-magnitude alternative, record the rank of the highest-magnitude DP of
the injection kind within the full sorted ranking. The chapter's headline claim is that every
caught injection ranks at top-1.""")

code(r"""// Pattern -> injection kind, matching INJECTION_KIND_BY_PATTERN in
// DivergenceExperiment.kt:176. Controls map to null and are skipped.
val INJECTION_KIND_BY_PATTERN: Map<String, String?> = mapOf(
    "ManualExtractMethod"  to "IDE_REPLAY",
    "ManualRenameMethod"   to "IDE_REPLAY",
    "ManualInlineMethod"   to "IDE_REPLAY",
    "ManualMoveMethod"     to "IDE_REPLAY",
    "ManualExtractVariable" to "IDE_REPLAY",
    "SuboptimalOrdering"   to "ORDERING",
    "AddThenRevert"        to "REWORK",
    "SkippedTests"         to "HYGIENE",
    "NoCommitStretch"      to "HYGIENE",
    "Control"              to null,
)

// Load the pattern column alongside expected_kinds.
fun loadPatterns(path: String): Map<String, String> {
    val lines = File(repoRoot, path).readLines()
    val header = parseCsvRow(lines[0])
    val sid = header.indexOf("session_id")
    val pat = header.indexOf("pattern")
    return lines.drop(1).filter { it.isNotBlank() }
        .associate {
            val row = parseCsvRow(it)
            row[sid] to row[pat]
        }
}
val patternsBySid = loadPatterns("fixtures/sessions/manifest-v2.csv")

// Build the ranking once per session, then look up the injection kind's
// top rank. magnitude > 0 + matching kind = "caught"; rank uses the same
// tie-broken-by-stepIndex order as the rest of the chapter.
fun rankedDps(report: AnalysisReport) =
    report.divergencePoints.sortedWith(
        compareByDescending<com.github.ethanhosier.analysis.pipeline.DivergencePoint> { it.magnitude }
            .thenBy { it.stepIndex }
    )

val prominenceByKind = mutableMapOf<String, MutableList<Int>>()
for ((sid, phaseA) in injection) {
    val pattern = patternsBySid[sid] ?: continue
    val injKind = INJECTION_KIND_BY_PATTERN[pattern] ?: continue
    val report = ReportAssembler.assemble(phaseA, ScoringConfig.PRODUCTION)
    val ranked = rankedDps(report)
    val injKindEnum = com.github.ethanhosier.analysis.pipeline.DivergenceKind.valueOf(injKind)
    val firstHitRank = ranked.indexOfFirst { it.kind == injKindEnum && it.magnitude > 0.0 } + 1
    if (firstHitRank > 0) {
        prominenceByKind.getOrPut(injKind) { mutableListOf() }.add(firstHitRank)
    }
}

kinds.map { kind ->
    val ranks = prominenceByKind[kind] ?: emptyList()
    mapOf(
        "kind" to kind,
        "caught" to ranks.size,
        "mean rank" to (if (ranks.isNotEmpty()) "%.2f".format(ranks.average()) else "—"),
        "all at rank 1?" to (if (ranks.isNotEmpty()) (ranks.all { it == 1 }).toString() else "—"),
    )
}.toDataFrame()""")

md(r"""Best-alt magnitude range across the injection sessions — gives an absolute scale for the
per-kind beat fractions above. Cited in the detection-quality paragraph of §5.2.2.""")

code(r"""val bestAlts = injection.values.mapNotNull { phaseA ->
    ReportAssembler.assemble(phaseA, ScoringConfig.PRODUCTION)
        .divergencePoints.maxOfOrNull { it.magnitude }
}
mapOf(
    "n sessions with any DP" to bestAlts.size,
    "mean best-alt magnitude" to "%.2f".format(bestAlts.average()),
    "min" to bestAlts.min().toInt(),
    "max" to bestAlts.max().toInt(),
)""")

# ---------------------------------------------------------------- §5.3 user + agent

md(r"""## §5.3 User and agent studies

### Per-session × kind distribution

Strict `magnitude > 0` filter, matching the §3.4 ORDERING-detection paragraph. The
`userRows` dataframe printed at the end of this cell reproduces Table 5.12.""")

code(r"""fun perSessionRow(sid: String, phaseA: PhaseAResult): Map<String, Any> {
    val report = ReportAssembler.assemble(phaseA, ScoringConfig.PRODUCTION)
    val counts = kinds.associateWith { k ->
        report.divergencePoints.count { it.kind.name == k && it.magnitude > 0.0 }
    }
    return mapOf("session" to sid) + counts + ("total" to counts.values.sum())
}

val userRows  = userStudy.map { (k, v) -> perSessionRow(k, v) }.toDataFrame()
val agentRows = agent.map { (k, v) -> perSessionRow(k, v) }.toDataFrame()
userRows""")

md("Per-kind trajectory across the six-session arc, summed across P1 + P2 (reproduces Table 5.13).")

code(r"""val combinedTraj = (1..6).map { idx ->
    val matching = (0 until userRows.rowsCount())
        .map { userRows[it] }
        .filter { (it["session"] as String).endsWith("-%02d".format(idx)) }
    val totals = kinds.associateWith { k -> matching.sumOf { (it[k] as Int) } }
    mapOf("session" to "S$idx") + totals + ("total" to totals.values.sum())
}.toDataFrame()
combinedTraj""")

code(r"""val trajLong = kinds.flatMap { k ->
    (0 until combinedTraj.rowsCount()).map { i ->
        mapOf("kind" to k, "session" to combinedTraj["session"][i] as String, "count" to combinedTraj[k][i] as Int)
    }
}.toDataFrame()

trajLong.plot {
    line   { x("session"); y("count"); color("kind") }
    points { x("session"); y("count"); color("kind") }
    layout { title = "Per-kind trajectory (P1 + P2 combined)" }
}""")

md("### §5.3.2 Agent extension corpus (per-session DP counts)")

code(r"""agentRows""")

md(r"""Arm-classification and per-participant/per-cell helpers used by the rest of this
section. The `-baseline` suffix on the participant or stack stem marks the no-feedback
arm; the trailing `-NN` of every session id is the in-arc session index 1..6.""")

code(r"""// `-baseline` suffix on the participant (user-sessions) or stack (agent-sessions)
// stem marks the no-feedback control arm.
fun isBaseline(sid: String): Boolean =
    sid.substringBeforeLast('-').endsWith("-baseline")

// Strip the trailing -NN to get participant or cell name.
fun participantOf(sid: String): String = sid.substringBeforeLast('-')
fun cellOf(sid: String): String = sid.substringBeforeLast('-')

// Extract the trailing 1..6 in-arc index.
fun sessionIdx(sid: String): Int? =
    sid.substringAfterLast('-').toIntOrNull()

// Pretty arm labels. P1..P5 anonymise the user-study participants; agent cells are
// reported by their model x harness names.
val userPretty = mapOf(
    "will" to "P1", "yukie" to "P2", "bobby" to "P3",
    "alex-baseline" to "P4", "vlad-baseline" to "P5",
)
// Accepts either a session id or a participant key (e.g. "will" or "will-01").
fun prettyUser(s: String): String {
    val key = if (sessionIdx(s) != null) participantOf(s) else s
    return userPretty[key] ?: key
}""")

md(r"""Per-arm trajectory aggregator. Given a corpus (`userStudy` or `agent`) and a
`group` function (e.g. `participantOf` for users, `cellOf` for agent stacks), produces a
DataFrame of per-group DP counts at S1..S6 plus a total column. The arm-aware variant
groups by arm (`with-feedback` / `baseline`) by summing across the groups that share an
arm.""")

code(r"""// Per-group arc of DP totals. df is `userRows` or `agentRows` (one row per session).
fun arcByGroup(df: AnyFrame, group: (String) -> String): List<Map<String, Any>> {
    val arcs = mutableMapOf<String, IntArray>()
    for (i in 0 until df.rowsCount()) {
        val sid = df["session"][i] as String
        val idx = sessionIdx(sid) ?: continue
        val g = group(sid)
        val arr = arcs.getOrPut(g) { IntArray(6) }
        arr[idx - 1] += df["total"][i] as Int
    }
    return arcs.toSortedMap().map { (g, arr) ->
        mapOf<String, Any>("group" to g) +
            (1..6).associate { "S$it" to arr[it - 1] } +
            ("Δ" to (arr[5] - arr[0])) +
            ("Σ" to arr.sum())
    }
}

val userArcByPpt  = arcByGroup(userRows,  ::participantOf).toDataFrame()
val agentArcByCell = arcByGroup(agentRows, ::cellOf).toDataFrame()
userArcByPpt""")

code(r"""agentArcByCell""")

md(r"""Per-arm DP trajectory summed across all participants (user-study) or all cells
(agent extension). Reproduces the arm rows of the chapter's trajectory tables.""")

code(r"""fun armArc(df: AnyFrame): List<Map<String, Any>> {
    val arcs = mapOf("with-feedback" to IntArray(6), "baseline" to IntArray(6))
    for (i in 0 until df.rowsCount()) {
        val sid = df["session"][i] as String
        val idx = sessionIdx(sid) ?: continue
        val arm = if (isBaseline(sid)) "baseline" else "with-feedback"
        arcs[arm]!![idx - 1] += df["total"][i] as Int
    }
    return arcs.map { (arm, arr) ->
        mapOf<String, Any>("arm" to arm) +
            (1..6).associate { "S$it" to arr[it - 1] } +
            ("Δ" to (arr[5] - arr[0])) +
            ("Σ" to arr.sum())
    }
}

armArc(userRows).toDataFrame()""")

code(r"""armArc(agentRows).toDataFrame()""")

md(r"""### Process score trajectory: gain-stripped weights

For each session, the trajectory-final process score is computed under a copy of the
production weighting with the cleanliness-gain weight `W_g` set to zero. The
gain-stripped view isolates the process-discipline terms (broken-time, skipped-tests,
manual-when-IDE, length bonus, commit-gap) from the cleanliness-gain term, mirroring
the §5.1.2 ablation finding that `W_g` acts as a regulariser on divergence-point
ranking. Reproduces Table 5.14 (user-study) and Table 5.16 (agent extension).""")

code(r"""// Per-group arc of trajectory-final process scores under the given config.
fun arcScoresBy(
    sessions: Map<String, PhaseAResult>,
    group: (String) -> String,
    cfg: ScoringConfig,
): Map<String, IntArray> {
    val out = mutableMapOf<String, IntArray>()
    for ((sid, pa) in sessions) {
        val idx = sessionIdx(sid) ?: continue
        val g = group(sid)
        val arr = out.getOrPut(g) { IntArray(6) { -1 } }
        val score = ReportAssembler.assemble(pa, cfg).checkpoints.last()
            .derivedMetrics?.process?.total ?: -1
        arr[idx - 1] = score
    }
    return out.toSortedMap()
}

// Re-derive a gain-stripped config in-memory so the notebook is self-contained.
val gainZero = ScoringConfig.PRODUCTION.copy(
    process = ScoringConfig.PRODUCTION.process.copy(gain = 0.0),
)

fun scoreRows(
    scores: Map<String, IntArray>,
    armOf: (String) -> String,
    pretty: (String) -> String,
): List<Map<String, Any>> = scores.toSortedMap().map { (g, arr) ->
    val s1 = arr[0]; val s6 = arr[5]
    mapOf<String, Any>("arm" to armOf(g), "group" to pretty(g)) +
        (1..6).associate { "S$it" to arr[it - 1] } +
        ("ΔJ" to (s6 - s1)) +
        ("slope" to "%.2f".format((s6 - s1) / 5.0))
}

val userScores  = arcScoresBy(userStudy, ::participantOf, gainZero)
val agentScores = arcScoresBy(agent,     ::cellOf,        gainZero)

scoreRows(
    userScores,
    armOf = { if (it.endsWith("-baseline")) "baseline" else "with-feedback" },
    pretty = ::prettyUser,
).toDataFrame()""")

code(r"""scoreRows(
    agentScores,
    armOf = { if (it.endsWith("-baseline")) "baseline" else "with-feedback" },
    pretty = { it },
).toDataFrame()""")

md(r"""### Per-session broken-build percentage by participant

Broken wall-clock time divided by total session wall-clock time, parsed from the
final checkpoint's `process.contributions[broken].detail` string. These percentages
surface on the dashboard as the `BUILD_OFTEN_BROKEN` advice item when they cross the
$15\%$ warning threshold or the $30\%$ critical threshold. Reproduces the per-session
broken-build numbers cited in the §5.4 cell-1 Thread 1.""")

code(r"""// Parse details like "1m04s of 4m44s broken (23%) - 4 of 10 checkpoint".
val brokenPctRe = Regex("\\((\\d+)%\\)")

fun brokenPctBy(
    sessions: Map<String, PhaseAResult>,
    group: (String) -> String,
): Map<String, IntArray> {
    val out = mutableMapOf<String, IntArray>()
    for ((sid, pa) in sessions) {
        val idx = sessionIdx(sid) ?: continue
        val g = group(sid)
        val arr = out.getOrPut(g) { IntArray(6) { -1 } }
        val report = ReportAssembler.assemble(pa, ScoringConfig.PRODUCTION)
        val last = report.checkpoints.lastOrNull() ?: continue
        val contrib = last.derivedMetrics?.process?.contributions?.firstOrNull { it.id == "broken" }
        val detail = contrib?.detail ?: ""
        arr[idx - 1] = brokenPctRe.find(detail)?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }
    return out.toSortedMap()
}

fun brokenRows(
    pcts: Map<String, IntArray>,
    pretty: (String) -> String,
): List<Map<String, Any>> = pcts.map { (g, arr) ->
    val present = arr.filter { it >= 0 }
    val mean = if (present.isNotEmpty()) present.average() else 0.0
    val peak = present.maxOrNull() ?: 0
    mapOf<String, Any>("actor" to pretty(g)) +
        (1..6).associate { "S$it" to "${arr[it - 1]}%" } +
        ("mean" to "%.1f%%".format(mean)) +
        ("peak" to "$peak%")
}

brokenRows(brokenPctBy(userStudy, ::participantOf), ::prettyUser).toDataFrame()""")

code(r"""brokenRows(brokenPctBy(agent, ::cellOf), { it }).toDataFrame()""")

# ---------------------------------------------------------------- corpus expansion dump

md("### Corpus-expansion findings dump\n\nAppended cell: emits per-session production J, gain-stripped J, and DP counts by kind for every user + agent session as plain TSV so downstream tools (e.g. the corpus-expansion comparison report) get the full corpus without dataframe-display truncation.")

code(r"""val expansionGainZero = ScoringConfig.PRODUCTION.copy(
    process = ScoringConfig.PRODUCTION.process.copy(gain = 0.0),
)

fun emitDump(label: String, sessions: Map<String, PhaseAResult>) {
    println("### $label ###")
    println("session\tORDERING\tIDE_REPLAY\tREWORK\tHYGIENE\ttotal\tJ_prod\tJ_gain0")
    for ((sid, phaseA) in sessions.toSortedMap()) {
        val prod = ReportAssembler.assemble(phaseA, ScoringConfig.PRODUCTION)
        val g0   = ReportAssembler.assemble(phaseA, expansionGainZero)
        val counts = kinds.associateWith { k -> prod.divergencePoints.count { it.kind.name == k && it.magnitude > 0.0 } }
        val jProd = prod.checkpoints.lastOrNull()?.derivedMetrics?.process?.total ?: -1
        val jG0   = g0  .checkpoints.lastOrNull()?.derivedMetrics?.process?.total ?: -1
        println("$sid\t${counts["ORDERING"]}\t${counts["IDE_REPLAY"]}\t${counts["REWORK"]}\t${counts["HYGIENE"]}\t${counts.values.sum()}\t$jProd\t$jG0")
    }
    println()
}

emitDump("INJECTION", injection)
emitDump("USER-STUDY", userStudy)
emitDump("AGENT", agent)

// Per-set DP-count histogram for the small-ranking-floor caveat.
fun dpCounts(label: String, sessions: Map<String, PhaseAResult>) {
    val counts = sessions.values.map { phaseA ->
        ReportAssembler.assemble(phaseA, ScoringConfig.PRODUCTION)
            .divergencePoints.count { it.magnitude > 0.0 }
    }
    val histo = counts.groupingBy { it }.eachCount().toSortedMap()
    println("### $label DP-count histogram ###")
    println("dp_count\tn_sessions")
    for ((k, v) in histo) println("$k\t$v")
    val total = counts.size
    val le1 = counts.count { it <= 1 }
    val ge2 = counts.count { it >= 2 }
    println("total=$total, <=1 (no ranking)=$le1, >=2 (rankable)=$ge2 (${"%.1f".format(100.0 * ge2 / total)}%)")
    println()
}

dpCounts("INJECTION", injection)
dpCounts("USER-STUDY", userStudy)
dpCounts("AGENT", agent)""")

# ---------------------------------------------------------------- write

nb = {
    "cells": cells,
    "metadata": {
        "kernelspec": {"display_name": "Kotlin", "language": "kotlin", "name": "kotlin"},
        "language_info": {
            "codemirror_mode": "text/x-kotlin",
            "file_extension": ".kt",
            "mimetype": "text/x-kotlin",
            "name": "kotlin",
            "pygments_lexer": "kotlin",
            "version": "1.9.0",
        },
    },
    "nbformat": 4,
    "nbformat_minor": 5,
}
out = HERE / "experiments.ipynb"
out.write_text(json.dumps(nb, indent=1))
print(f"wrote {out} with {len(cells)} cells, {len(LIBS)} libs on classpath")
