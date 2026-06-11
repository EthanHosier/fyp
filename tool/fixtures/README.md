# Fixtures

This directory holds the project's data, recordings, fixtures, and reproducibility
material. Everything Chapter 5 of the thesis cites is rooted here.

## Layout

| Path | Contents |
|---|---|
| `sessions/`              | The 45 hand-recorded **injection sessions** used in §5.2 (controlled bad-behaviour playbook). Each `sessions/NNN/` contains `initial-src/`, `events.jsonl`, `session.json`, `analysis-report.json`, and `phase-a.json`. |
| `user-sessions/`         | The 30 **user-study sessions** (5 participants × 6 sessions): `p1-NN`, `p2-NN`, `p3-NN`, `p4-baseline-NN`, `p5-baseline-NN`. The `-baseline-` infix marks the no-feedback control arm; the others are with-feedback. Same per-session file layout as `sessions/`. |
| `agent-sessions/`        | The 48 **agent-comparison sessions** (8 agent stacks × 6 sessions). The 8 stacks vary by model (Claude, GPT, Gemini) × harness (Claude Code, OpenCode, Cursor); 2 of the 8 are no-feedback control arms (`*-baseline`). Same per-session file layout as `sessions/`. |
| `playbook/`              | The 45 playbook prompts used to seed the injection sessions in `sessions/`. |
| `user-study-playbook/`   | The 6 task prompts (S01..S06) used in `user-sessions/`. |
| `user-study-interviews/` | Pseudonymised post-session interview notes from the user study. |
| `user-study-fixture/`    | The Java order-processing project used as the working tree for user-study and agent recordings. Has a `baseline` and `recording` git branch; see `reset-for-user-study-session.sh`. |
| `library-fixture/`       | The Java library project used as the working tree for the injection sessions. Same baseline/recording branch pattern; see `reset-for-session.sh`. |
| `notebooks/`             | Jupyter notebook `experiments.ipynb` that reproduces every numerical claim in Chapter 5, plus the supporting CSV snapshots under `notebooks/data/`. Launch Jupyter from the project root `tool/` so the notebook's relative paths to `fixtures/sessions/`, `fixtures/user-sessions/`, `fixtures/agent-sessions/` resolve. See `notebooks/README.md` for setup. |
| `PROTOCOL.md`            | Session-recording protocol (what the recorder runs, in what order). |
| `reset-for-session.sh`            | Resets `library-fixture/` to baseline between injection sessions. |
| `reset-for-user-study-session.sh` | Resets `user-study-fixture/` to baseline between user-study and agent sessions. Preserves the agent-flow helper scripts inside that fixture. |
| `end-session.sh`         | Hook called from the IDE plugin at session end. |

## Large per-session JSON artefacts

The `phase-a.json` and `analysis-report.json` files in each session directory can be
tens to hundreds of megabytes. They are tracked via Git LFS under the `.gitattributes`
patterns at the repository root. A fresh clone needs:

```bash
git lfs install
git lfs pull
```

before the notebook can load anything.

## Reproducing Chapter 5

Every number, table, headline finding, and plot in Chapter 5 is reproduced by
`notebooks/experiments.ipynb`. The one-time setup (`./gradlew :analysis:notebookLibs`
to stage runtime jars, then `jupyter notebook fixtures/notebooks/experiments.ipynb`
from the project root) is documented in `notebooks/README.md`.
