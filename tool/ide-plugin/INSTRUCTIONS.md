You are building the **final-form data collection plugin** for my MEng thesis project.

Project context:
The thesis is about **trajectory-level refactoring analysis**. The plugin's job is **only to collect rich, structured trace data from the IDE** so that a separate offline analysis pipeline can later:

1. reconstruct the developer's refactoring trajectory,
2. segment it into meaningful steps,
3. score the observed trajectory under a process-quality metric,
4. compare it against higher-scoring alternative trajectories.

The plugin is **not** responsible for scoring, counterfactual generation, or deep semantic analysis. It should focus on **capturing the right raw data cleanly and reliably**.

The target IDE is IntelliJ IDEA. Write the plugin in Kotlin. It will be doing analysis of Java programs.

---

## Core design principle

The plugin captures a single ordered stream of **events**. For any event that directly changes a file, the full contents of that file are stored **inline with the event itself**. There is no separate checkpoint concept — the analysis stage reconstructs project state by replaying events in timestamp order.

We are intentionally prioritising **completeness and clarity of collected data over storage efficiency**.

---

## Main responsibilities of the plugin

1. **Event collection** — capture all meaningful IDE activity
2. **File-state capture** — for file-changing events, record the full file contents at the moment of the change
3. **Session metadata capture**
4. **Export/storage** in a clean structured format

---

## Event model

Every event has:

* `id` — UUID
* `type` — enum (see below)
* `timestamp` — epoch milliseconds, set at the moment the event handler fires
* `sessionId`
* `changedFiles` — list of `FileSnapshot` (non-empty only for file-changing events)
* `payload` — flat `Map<String, String>` for type-specific metadata

### FileSnapshot

Attached to file-changing events:

* `path` — current file path
* `contents` — full file text (null for DELETED)
* `previousPath` — populated for RENAMED / MOVED
* `changeType` — CREATED / MODIFIED / RENAMED / MOVED / DELETED

### File-changing events (changedFiles non-empty)

| Event type | What triggers it | File contents source |
|---|---|---|
| `EDIT_BURST` | 2s idle after document edits | `event.document.text` captured at time of last edit (in-memory, pre-save) |
| `FILE_CREATED` | VFS create | `VirtualFile.contentsToByteArray()` |
| `FILE_DELETED` | VFS delete | null (file is gone) |
| `FILE_RENAMED` | VFS property change (name) | `FileDocumentManager.getDocument()?.text` or VFS |
| `FILE_MOVED` | VFS move | `FileDocumentManager.getDocument()?.text` or VFS |
| `REFACTORING_FINISHED` | IntelliJ refactoring done | Primary affected file from `afterData` PSI element. Note: multi-file refactorings (e.g. rename used in N files) emit one VFS event per changed file — correlate with `REFACTORING_FINISHED` by timestamp proximity at analysis time. |

### Non-file-changing events (changedFiles empty)

`FILE_OPENED`, `FILE_CLOSED`, `FILE_FOCUSED`, `FILE_UNFOCUSED`, `FILE_SAVED`, `REFACTORING_STARTED`, `BUILD_STARTED`, `BUILD_FINISHED`, `TEST_RUN_STARTED`, `TEST_RUN_FINISHED`, `TASK_STARTED`, `TASK_ENDED`, `SESSION_STARTED`, `SESSION_ENDED`

These use `relatedFiles: List<String>` and `payload` for context.

---

## Session metadata

Record:

* session id (UUID)
* project name + path
* git branch + commit hash if available
* session start/end timestamps
* IDE version
* plugin version

---

## Storage layout

```
<projectDir>/.refactoring-traces/<sessionId>/
├── session.json     ← written on session end (metadata + full event list)
└── events.jsonl     ← one JSON object per line, appended live (crash-safe)
```

No separate checkpoints directory. All file-state data lives inline in events.

---

## Reconstruction requirement

The offline analysis tool reconstructs project state by:

1. Reading `events.jsonl` in timestamp order
2. For each event with `changedFiles`, applying those file snapshots to the running state
3. Rename/move/delete semantics are explicit in `FileSnapshot.changeType` and `previousPath`

This means:
* events must have accurate timestamps
* file contents must reflect the exact state at the moment the event handler fires
* rename/move/delete must always be represented with correct paths

---

## Non-goals for this plugin

Do **not** implement:

* RefactoringMiner integration
* trajectory scoring
* smell detection
* readability metrics
* counterfactual trajectory generation
* implicit checkpoint heuristics (inactivity gap, focus shift, etc.) — the raw event stream is sufficient for offline analysis

---

## Engineering expectations

* Clean, modular plugin structure
* Production-quality Kotlin, IntelliJ platform conventions
* Prefer explicitness and reliability over cleverness
* Make reasonable IntelliJ API choices without asking for confirmation
* Prioritise clean architecture and complete data capture over UI polish
