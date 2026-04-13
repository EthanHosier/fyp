# Plan: IntelliJ Refactoring Trajectory Data Collection Plugin

## Context

This plugin is the data collection component for an MEng thesis on trajectory-level refactoring analysis. It captures a single ordered stream of IDE events. For any event that directly changes a file, the full file contents are stored inline with the event. The analysis stage reconstructs project state by replaying events in timestamp order — no separate checkpoint concept.

See `INSTRUCTIONS.md` for the full requirements spec.

---

## Target Package Structure

```
com.github.ethanhosier.ideplugin/
├── model/            ← pure data classes + JSON schema
├── services/         ← core business logic services
├── listeners/        ← IDE event listeners
├── actions/          ← user-triggered actions (toolbar/menu)
├── startup/          ← plugin startup activity
└── toolWindow/       ← status UI panel
```

---

## Implementation Order

1. **Stage 1** — Data model
2. **Stage 3a** — Stub listeners
3. **Stage 2** — Core services
4. **Stage 3b** — Real listener implementations
5. **Stage R** — Refactor: simplify model (merge checkpoints into events) ← current
6. **Stage 5** — Actions
7. **Stage 8** — Startup wiring (session end on project close)
8. **Stage 6** — Tool window
9. **Stage 9** — README

---

## Stage 1 — Data Model Layer
**Status:** [x] Complete

---

## Stage 3a — Stub Listeners
**Status:** [x] Complete

---

## Stage 2 — Core Services
**Status:** [x] Complete

---

## Stage 3b — Real Listener Implementations
**Status:** [x] Complete

Notes:
- `SessionService.addEvent(type, ...)` convenience overload eliminates emit() duplication
- `EditBurstTracker` service owns debounce scheduler, per-file accumulators, burst flush logic
- `EditorEventListener` wired programmatically from `MyProjectActivity` (not plugin.xml) — restored editors bypass EditorFactory events
- `VfsListener` filters out directories and `.refactoring-traces/` paths to avoid feedback loops

---

## Stage R — Simplify Model: Merge Checkpoints into Events
**Status:** [ ] Not started

The original checkpoint model (separate checkpoint files, FileStateTracker, CheckpointService) is replaced by storing file contents inline with the events that cause changes.

### Delete
- `model/Checkpoint.kt`
- `model/CheckpointFile.kt` → replaced by `FileSnapshot`
- `model/ValidationSummary.kt`
- `services/CheckpointService.kt`
- `services/FileStateTracker.kt`

### Add
- `model/FileSnapshot.kt` — path, contents (null for DELETED), previousPath, changeType enum (CREATED/MODIFIED/RENAMED/MOVED/DELETED)

### Modify: `model/TraceEvent.kt`
- Add `changedFiles: List<FileSnapshot> = emptyList()`
- Keep `relatedFiles` for non-state-changing events

### Modify: `model/Session.kt`
- Remove `checkpoints: List<Checkpoint>`

### Modify: `services/SessionService.kt`
- Remove `addCheckpoint()`, `checkpoints` list, `getRecentEventIds()`
- Add `changedFiles` param to `addEvent(type, ...)` convenience overload

### Modify: `services/StorageService.kt`
- Remove `flushCheckpoint()`, `checkpointDir`, `checkpointSeq`
- Remove checkpoint dir creation from `init()`

### Modify: `services/EditBurstTracker.kt`
- Capture `event.document.text` into `acc.latestContents` on each `onDocumentChanged()` call
- Use `acc.latestContents` in `flush()` to populate `changedFiles` — no disk read needed
- Remove `FileStateTracker.markDirty()` call

### Modify: `listeners/VfsListener.kt`
- Populate `changedFiles` in each event:
  - `FILE_CREATED` → read file contents from VFS
  - `FILE_DELETED` → `FileSnapshot(path, contents=null, changeType=DELETED)`
  - `FILE_MOVED` / `FILE_RENAMED` → read contents, set `previousPath`
- Remove `FileStateTracker.markDirty()` and `CheckpointService.createCheckpoint()` calls

### Modify: `listeners/RefactoringListener.kt`
- In `refactoringDone()`: resolve primary affected file from `afterData` PSI element, read its contents, populate `changedFiles` on `REFACTORING_FINISHED`
- Note: multi-file refactorings emit one VFS event per changed file — correlate with `REFACTORING_FINISHED` by timestamp at analysis time
- Remove `CheckpointService.createCheckpoint()` call

### Modify: `listeners/BuildListener.kt`
- Remove `CheckpointService.createCheckpoint()` call

### Modify: `listeners/TestRunListener.kt`
- Remove `CheckpointService.createCheckpoint()` call

### Storage layout after this stage
```
.refactoring-traces/<sessionId>/
├── session.json     ← written on session end
└── events.jsonl     ← one JSON object per line, appended live
```

---

## Stage 5 — Actions
**Status:** [ ] Not started

- `actions/MarkCheckpointAction.kt` — emits `MANUAL_CHECKPOINT` event (no file snapshot needed)
- `actions/StartTaskAction.kt` — input dialog for label, emits `TASK_STARTED`
- `actions/EndTaskAction.kt` — emits `TASK_ENDED`

Register in plugin.xml under Tools menu.

---

## Stage 8 — Startup & Session End
**Status:** [ ] Not started

- Add `ProjectManagerListener` to call `SessionService.endSession()` on project close
- Ensure `session.json` is flushed cleanly

---

## Stage 6 — Tool Window
**Status:** [ ] Not started

Replace `toolWindow/MyToolWindowFactory.kt` with a status panel:
- Session ID + start time
- Live event count
- "Mark Checkpoint" button
- "Start Task" / "End Task" buttons
- Output directory path

---

## Stage 9 — README
**Status:** [ ] Not started

`README.md` covering: what is captured, output location, export format, and how offline reconstruction works.

---

## Verification

- `./gradlew runIde` → open a Java project → make edits, trigger IntelliJ rename
- Confirm `.refactoring-traces/` is created with `events.jsonl` only (no `checkpoints/` dir)
- Confirm EDIT_BURST events have `changedFiles` with file contents
- Confirm VFS events (FILE_RENAMED etc.) have `changedFiles` populated
- Confirm `session.json` written on project close
- `./gradlew test` — all tests pass
