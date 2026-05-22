# Experiments notebook

`experiments.ipynb` reproduces every table, headline number and plot from Chapter 5 of
the thesis. The experiment loops live in the notebook directly — sensitivity sweep,
multi-knob Monte Carlo, ablation, divergence experiment, and the κ / per-kind / agent
analyses — so the notebook is the single source of truth for the chapter's numbers.

The notebook depends on the analysis module's runtime classpath (about 200 MB of jars)
which it loads via `@file:DependsOn`. The `libs/` directory is gitignored.

## First-time setup

```bash
# 1. Build the analysis module + stage its runtime classpath next to the notebook.
./gradlew :analysis:notebookLibs

# 2. Build Phase-A dumps for the user-study and agent session sets if not already in
#    fixtures/user-corpus/ and fixtures/agent-corpus/. The injection set's dumps are
#    already committed under fixtures/corpus/. See fixtures/bulk-phase-a.sh for a
#    parameterised driver; one session takes ~30-60s.

# 3. Install the Kotlin Jupyter kernel and launch.
pip install kotlin-jupyter-kernel        # or conda-forge: kotlin-jupyter-kernel
jupyter notebook                         # then open experiments.ipynb
```

## Inputs

The notebook reads three Phase-A dump directories:

| Path | Sessions | Source |
| --- | ---: | --- |
| `fixtures/corpus/`       | 45 injection      | hand-recorded by the author (`fixtures/bulk-phase-a.sh`) |
| `fixtures/user-corpus/`  | 12 user-study     | P1 + P2 recordings, then `:analysis:phaseA` per session |
| `fixtures/agent-corpus/` | 6 agent           | Claude Code recordings, then `:analysis:phaseA` per session |

Plus three rater-manifest CSVs at `fixtures/sessions/manifest-v2{,-will,-yukie}.csv` for
the inter-rater κ computation.

## Regenerating the notebook

The `.ipynb` is generated from `build_notebook.py` (under version control so cell edits
produce small text diffs rather than mass-changing JSON):

```bash
python3 build_notebook.py
```

Add or remove jars under `libs/` and re-run the generator to refresh the
`@file:DependsOn` block.

## Relationship to the Kotlin experiment classes under `analysis/`

The classes under `analysis/src/main/kotlin/.../experiment/`
(`SensitivityExperiment.kt`, `MultiKnobMonteCarloExperiment.kt`, `AblationExperiment.kt`,
`DivergenceExperiment.kt`) and their CLI Gradle tasks
(`:analysis:sensitivity`, `:analysis:multiKnobMC`, `:analysis:ablation`,
`:analysis:divergence`) are the canonical command-line interface. The notebook reimplements
the same sweep logic inline so it can produce dataframes and plots without a CSV
intermediate. The two are intended to produce identical numbers; the `data/` directory
holds CSV snapshots from the Gradle tasks for cross-checking.
