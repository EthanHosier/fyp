# Experiments notebook

`experiments.ipynb` reproduces every table, headline number and plot from Chapter 5 of
the thesis. The experiment loops live in the notebook directly — sensitivity sweep,
multi-knob Monte Carlo, ablation, divergence experiment, and the κ / per-kind / agent
analyses — so the notebook is the single source of truth for the chapter's numbers.

This directory lives at `tool/fixtures/notebooks/` (it sits inside the larger
`tool/fixtures/` data tree — see `../README.md` for an overview of the other
subdirectories). The notebook is intended to be launched **from the project root
`tool/`** so its relative paths to `fixtures/sessions/`, `fixtures/user-sessions/`, and
`fixtures/agent-sessions/` resolve.

The notebook depends on the analysis module's runtime classpath (about 200 MB of jars)
which it loads via `@file:DependsOn`. The `libs/` directory is gitignored.

## First-time setup

```bash
# 1. Build the analysis module + stage its runtime classpath under
#    fixtures/notebooks/libs/.
./gradlew :analysis:notebookLibs

# 2. Build Phase-A dumps for every recorded session. The injection set's dumps are
#    already committed under fixtures/sessions/NNN/phase-a.json; the user-study and
#    agent sessions get their Phase-A dumps via
#    `tool/scripts/regenerate-phase-a-dumps.sh`. One session takes ~30-60s.

# 3. Install the Kotlin Jupyter kernel and launch.
pip install kotlin-jupyter-kernel        # or conda-forge: kotlin-jupyter-kernel
cd tool                                  # important: launch from project root
jupyter notebook fixtures/notebooks/experiments.ipynb
```

## Inputs

The notebook reads per-session Phase-A dumps directly from the session directories:

| Path | Sessions | Source |
| --- | ---: | --- |
| `fixtures/sessions/NNN/phase-a.json`       | 45 injection           | hand-recorded by the author against `library-fixture/` |
| `fixtures/user-sessions/<name>-NN/phase-a.json` | 30 user-study          | 5 participants × 6 sessions on `user-study-fixture/` |
| `fixtures/agent-sessions/<stack>/NN/phase-a.json` | 48 agent-comparison    | 8 agent stacks × 6 sessions on `user-study-fixture/` |

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
