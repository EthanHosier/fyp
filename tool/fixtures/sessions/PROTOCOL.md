# Recording protocol — `library-fixture` corpus

This directory holds the recorded IDE plugin sessions used as ground
truth for the divergence-detection / sensitivity / ablation
experiments. The full plan — including the 45-row session table, what
each session does, and how to interpret the results — lives at
[`../../PLAN-phase-3-recording-playbook.md`](../../PLAN-phase-3-recording-playbook.md).
Per-session step-by-step playbooks live at
[`../playbook/`](../playbook/).

This file documents the **mechanics** of getting from "the fixture is
checked in" to "the corpus is on disk".

## Directory layout

```
fixtures/
├── library-fixture/        Frozen Java source — what IntelliJ opens.
│   └── src/                Edited freely during a recording, then
│                           reset via reset-for-session.sh.
├── playbook/               45 self-contained per-session walkthroughs.
├── sessions/               Captured recordings (one dir per session).
│   ├── PROTOCOL.md         This file.
│   ├── manifest.csv        Ground-truth rows for the 41 injection
│   │                       sessions. Controls 042-045 don't appear.
│   ├── 001/                Each session's .refactoring-traces/<uuid>
│   ├── ...                 dir, moved here by end-session.sh.
│   └── 045/
├── corpus/                 Phase-A dumps (output of bulk-phase-a.sh).
│   ├── 001.json
│   └── ...
├── reset-for-session.sh    Resets library-fixture/ to baseline.
├── end-session.sh          Captures the trace; appends manifest row.
└── bulk-phase-a.sh         Runs :phaseA over every captured session.
```

## Bootstrap (run once, before the first session)

```bash
cd fixtures/library-fixture
git init
git add -A && git commit -m "library-fixture v1 baseline"
git branch baseline                  # frozen baseline marker
git checkout -b recording            # recording branch — sessions edit this
```

The `baseline` branch is sacred; never check out and commit on it.
All session-time commits go on `recording` and are blown away by
`reset-for-session.sh`.

## Per-session workflow

```bash
cd fixtures
./reset-for-session.sh               # nukes prior edits + .refactoring-traces
# IntelliJ is already open on library-fixture/. Press the
# "Reload from disk" toolbar button (or wait a couple of seconds).
# Plugin: Start record mode.
# ... perform the per-session script from playbook/<id>-*.md ...
# Plugin: End session.
./end-session.sh <id>                # mv trace + append manifest row
```

That's it. No /tmp dance, no reopening the IDE.

## Manifest schema

`sessions/manifest.csv` is appended to by `end-session.sh`. Header:

```
session_id,pattern,strength,target_step,expected_kind,fixture_path,control_session_id
```

- `session_id`: zero-padded 3-digit string.
- `pattern`: label from the plan (`ManualExtractMethod`, etc.).
- `strength`: `loud` / `borderline` / `subthreshold`.
- `target_step`: 1-indexed user-step where the bad pattern anchors.
- `expected_kind`: one of `IDE_REPLAY`, `ORDERING`, `REWORK`, `HYGIENE`.
- `fixture_path`: `sessions/<id>/`.
- `control_session_id`: paired control's session_id (042 / 043 / 044 /
  045 per the plan). Empty cell to skip control measurement.

Controls (042-045) don't appear in the manifest; they live as
`sessions/<id>/` directories and are referenced by `control_session_id`
from injection rows.

## After all 45 sessions are recorded

```bash
./bulk-phase-a.sh                    # produces corpus/<id>.json for each
```

Then run the experiment drivers from the repo root per the plan's
verification section.
