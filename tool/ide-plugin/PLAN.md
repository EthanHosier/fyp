# Plan: IntelliJ Refactoring Trajectory Data Collection Plugin

## Context

This plugin is the data collection component for an MEng thesis on trajectory-level refactoring analysis. It must record rich, structured IDE activity (events, checkpoints, file states) from IntelliJ IDEA during Java development sessions so that a separate offline pipeline can later reconstruct refactoring trajectories, score them, and compare against alternatives.

The existing codebase is the standard IntelliJ Platform Plugin Template. We follow its conventions: Kotlin, service/listener/action layering, plugin.xml registration, and the same package root `com.github.ethanhosier.ideplugin`.

See `INSTRUCTIONS.md` for the full requirements spec.

---

## Target Package Structure

```
com.github.ethanhosier.ideplugin/
├── model/            ← pure data classes + JSON schema
├── services/         ← core business logic services
├── listeners/        ← IDE event listeners (wired via plugin.xml)
├── actions/          ← user-triggered actions (toolbar/menu)
├── checkpoint/       ← checkpoint trigger logic
├── storage/          ← serialization + disk I/O
├── startup/          ← plugin startup activity
└── toolWindow/       ← status UI panel
```

---

## Implementation Order

1. **Stage 1** — Data model (no dependencies)
2. **Stage 3a** — Stub listeners: wire all listeners in plugin.xml but only log to `thisLogger()`. Goal: verify IntelliJ API interception works for every event type before writing any real logic.
3. **Stage 2** — Core services (depends on model)
4. **Stage 7** — Storage (depends on model + services)
5. **Stage 3b** — Real listener implementations: replace stubs with calls to services (depends on Stage 2 + 3a)
6. **Stage 4** — Implicit checkpoint detector (depends on listeners + services)
7. **Stage 5** — Actions
8. **Stage 8** — plugin.xml wiring + startup
9. **Stage 6** — Tool window
10. **Stage 9** — README

> One stage at a time. If a stage is large, split further before implementing.

---

## Stage 1 — Data Model Layer
**Status:** [x] Complete

**build.gradle.kts changes:**
- Add `kotlin("plugin.serialization")` plugin
- Add `implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")`

**Files to create:**
- `model/EventType.kt` — enum of all event types (EDIT_BURST, FILE_OPENED, FILE_CLOSED, FILE_FOCUSED, FILE_UNFOCUSED, FILE_SAVED, FILE_CREATED, FILE_DELETED, FILE_RENAMED, FILE_MOVED, REFACTORING_STARTED, REFACTORING_FINISHED, BUILD_STARTED, BUILD_FINISHED, TEST_RUN_STARTED, TEST_RUN_FINISHED, MANUAL_CHECKPOINT, TASK_STARTED, TASK_ENDED, SESSION_STARTED, SESSION_ENDED)
- `model/TraceEvent.kt` — id, type, timestamp, sessionId, relatedFiles, payload (Map<String, String>), all `@Serializable`
- `model/CheckpointFile.kt` — path, fullContents, changeType enum (CREATED/MODIFIED/RENAMED/MOVED/DELETED), previousPath?
- `model/ValidationSummary.kt` — buildStatus?, testStatus?, compileErrorCount?, scope?, durationMs?
- `model/Checkpoint.kt` — id, triggerType, timestamp, sessionId, changedFiles, validationSummary?, recentEventIds, branch?, commitHash?
- `model/SessionMetadata.kt` — sessionId, projectName, projectPath, branch?, commitHash?, startTime, endTime?, ideVersion, pluginVersion
- `model/Session.kt` — metadata, events: List<TraceEvent>, checkpoints: List<Checkpoint>

---

## Stage 3a — Stub Listeners
**Status:** [x] Complete

Wire all listeners in `plugin.xml`. Each listener only calls `thisLogger().info(...)` to confirm the event fires. No service calls yet.

**Files to create:**
- `listeners/EditorEventListener.kt` — `DocumentListener` + `EditorFactoryListener` — logs document changes and editor open/close
- `listeners/FileEditorListener.kt` — `FileEditorManagerListener` — logs file open/close/focus
- `listeners/FileSaveListener.kt` — `FileDocumentManagerListener` — logs file saves
- `listeners/RefactoringListener.kt` — `RefactoringEventListener` — logs refactoring start/finish/cancel
- `listeners/VfsListener.kt` — `BulkFileListener` — logs file create/delete/rename/move
- `listeners/BuildListener.kt` — `BuildManagerListener` — logs build start/finish
- `listeners/TestRunListener.kt` — `SMTRunnerEventsListener` (or `TestStatusListener`) — logs test run start/finish

**plugin.xml additions** for each listener at appropriate extension points.

---

## Stage 2 — Core Services
**Status:** [ ] Not started

- `services/SessionService.kt` — `@Service(PROJECT)`. `startSession()`, `endSession()`, `addEvent(TraceEvent)`, `getSession()`. Generates UUID session ID. Reads git branch/commit via `git rev-parse`. Captures IDE version via `ApplicationInfo`.
- `services/FileStateTracker.kt` — `@Service(PROJECT)`. Maintains set of dirty files since last checkpoint. `markDirty(path, changeType)`, `getDirtyFiles(): List<ChangedFile>`, `reset()`.
- `services/CheckpointService.kt` — `@Service(PROJECT)`. `createCheckpoint(trigger)`: reads dirty files from `FileStateTracker`, reads their VFS contents, builds `Checkpoint`, appends to session, calls `StorageService.flushCheckpoint()`.
- `services/StorageService.kt` — `@Service(PROJECT)`. Output dir: `<projectDir>/.refactoring-traces/<sessionId>/`. `flushEvent(event)` → appends to `events.jsonl`. `flushCheckpoint(cp)` → writes `checkpoints/<seq>_<id>.json`. `flushSession(session)` → writes `session.json`.

---

## Stage 7 — Storage & Export
**Status:** [ ] Not started

Output layout:
```
.refactoring-traces/
└── <sessionId>/
    ├── session.json          ← written on session end
    ├── events.jsonl          ← appended live (crash-safe)
    └── checkpoints/
        ├── 001_<id>.json
        └── ...
```

Each checkpoint JSON contains full `Checkpoint` object including full file contents of all changed files.

---

## Stage 3b — Real Listener Implementations
**Status:** [ ] Not started

Replace stub log calls with real service calls:
- `EditorEventListener` — debounce edits into `EDIT_BURST` events (2s idle gap), capture char counts + line ranges, call `SessionService.addEvent()` + `FileStateTracker.markDirty()`
- `FileEditorListener` — emit `FILE_OPENED/CLOSED/FOCUSED/UNFOCUSED`
- `FileSaveListener` — emit `FILE_SAVED`
- `RefactoringListener` — emit `REFACTORING_STARTED/FINISHED`, trigger checkpoint on success via `CheckpointService`
- `VfsListener` — emit `FILE_CREATED/DELETED/RENAMED/MOVED`, trigger checkpoint on rename/move
- `BuildListener` — emit `BUILD_STARTED/FINISHED`, trigger checkpoint on finish
- `TestRunListener` — emit `TEST_RUN_STARTED/FINISHED`, trigger checkpoint on finish

---

## Stage 4 — Implicit Checkpoint Detector
**Status:** [ ] Not started

- `checkpoint/CheckpointTrigger.kt` — sealed class: `ManualMarker`, `RefactoringCompleted`, `FileStructureChange`, `BuildFinished`, `TestRunFinished`, `InactivityGap`, `FocusShift`, `EditBurstThreshold`, `StabilityRecovered`
- `checkpoint/ImplicitCheckpointDetector.kt` — `@Service(PROJECT)`. Consumes events, implements:
  - **Inactivity gap**: timer reset on edit, fires after 5 min (configurable)
  - **Focus shift**: file focus change to different package after edit burst
  - **Edit burst threshold**: >50 lines changed in single burst
  - **Stability recovered**: previous checkpoint had compile errors + new build succeeds

---

## Stage 5 — Actions
**Status:** [ ] Not started

- `actions/MarkCheckpointAction.kt` — calls `CheckpointService.createCheckpoint(ManualMarker)`
- `actions/StartTaskAction.kt` — input dialog for label, emits `TASK_STARTED` event
- `actions/EndTaskAction.kt` — emits `TASK_ENDED` event

Register in plugin.xml under Tools menu. Add keyboard shortcut for MarkCheckpoint.

---

## Stage 8 — Startup & plugin.xml Wiring
**Status:** [ ] Not started

- `startup/MyProjectActivity.kt` — calls `SessionService.startSession()` on project open
- Add `ProjectManagerListener` to call `SessionService.endSession()` on project close
- Ensure all services, listeners, and actions are registered in `plugin.xml`

---

## Stage 6 — Tool Window
**Status:** [ ] Not started

Replace `toolWindow/MyToolWindowFactory.kt` with a status panel:
- Session ID + start time
- Live event count + checkpoint count
- "Mark Checkpoint" button
- "Start Task" / "End Task" buttons
- Output directory path

---

## Stage 9 — README
**Status:** [ ] Not started

`README.md` covering: what is captured, checkpoint rules, output location, export format, and how offline reconstruction works.

---

## Verification

- `./gradlew runIde` → open a Java project → make edits, trigger IntelliJ rename
- Confirm `.refactoring-traces/` is created and populated
- Confirm `events.jsonl` streams live
- Confirm checkpoint JSON contains full file contents
- Manually trigger Mark Checkpoint from tool window
- `./gradlew test` — all tests pass
