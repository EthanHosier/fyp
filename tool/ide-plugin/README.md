# Refactoring Tracer — IntelliJ Plugin

<!-- Plugin description -->
An IntelliJ IDE plugin that records a timestamped stream of developer activity events for trajectory-level refactoring analysis. Built as part of an MEng Final Year Project.
<!-- Plugin description end -->

> **TODO — blocking I/O on the EDT.** Several hot paths still do synchronous disk or subprocess work on the EDT: the `git` subprocess calls in `SessionService.readGitInfo` (no `waitFor` timeout — a stuck `git` hangs the IDE), `StorageService.flushEvent` (writes `events.jsonl` on every event, including those emitted from EDT listeners like `FileSaveListener`), and `Desktop.open(dir)` in the tool window's "open folder" button. Acceptable for thesis data collection on a developer machine, but should be moved off the EDT (pooled executor + hard timeouts) before wider use.

---

## What is captured

Every event is written to a single append-only `events.jsonl` file as the session progresses. Each line is a self-contained JSON object.

| Event | Trigger | File contents captured? |
|---|---|---|
| `SESSION_STARTED` | User clicks Start Session | No |
| `SESSION_ENDED` | User clicks End Session or project closes | No |
| `EDIT_BURST` | 2 s of inactivity after typing | Yes — in-memory document state at last keystroke |
| `FILE_OPENED` | File tab opened | No |
| `FILE_CLOSED` | File tab closed | No |
| `FILE_FOCUSED` | Editor tab gains focus | No |
| `FILE_UNFOCUSED` | Editor tab loses focus | No |
| `FILE_SAVED` | File saved to disk | No |
| `FILE_CREATED` | File created on disk | Yes |
| `FILE_DELETED` | File deleted | No (contents gone) |
| `FILE_RENAMED` | File renamed | Yes |
| `FILE_MOVED` | File moved | Yes |
| `REFACTORING_STARTED` | IntelliJ refactoring begins | No |
| `REFACTORING_FINISHED` | IntelliJ refactoring completes | No (VFS events capture changed files) |
| `FILE_ERRORS_CHANGED` | File gains or loses compile errors (2 s debounce) | Yes |
| `BUILD_STARTED` | Compilation triggered | No |
| `BUILD_FINISHED` | Compilation finishes | No |
| `TEST_RUN_STARTED` | Test run begins | No |
| `TEST_RUN_FINISHED` | Test run completes | No |

Events that capture file contents include a `changedFiles` array. Each entry has:
- `path` — absolute path on disk
- `contents` — full file text at the time of the event (`null` for deletions)
- `changeType` — `CREATED`, `MODIFIED`, `RENAMED`, `MOVED`, or `DELETED`
- `previousPath` — set for renames and moves

---

## Output location

All output is written inside the project directory:

```
<projectDir>/.refactoring-traces/<sessionId>/
├── events.jsonl    ← one JSON object per line, appended live
└── session.json    ← session metadata, written on session end
```

The `.refactoring-traces/` directory is created automatically when the first session starts. Add it to `.gitignore` if you do not want trace data committed.

---

## Event format

Each line of `events.jsonl` is a `TraceEvent` object:

```json
{
  "id": "3f2a1c...",
  "type": "EDIT_BURST",
  "timestamp": 1713100800000,
  "sessionId": "d0149ca3-...",
  "changedFiles": [
    {
      "path": "/Users/.../MyClass.java",
      "contents": "public class MyClass { ... }",
      "changeType": "MODIFIED",
      "previousPath": null
    }
  ],
  "relatedFiles": [],
  "payload": {
    "charsInserted": "42",
    "charsDeleted": "5",
    "regionStart": "120",
    "regionEnd": "167"
  }
}
```

`session.json` contains session metadata alongside the full event list:

```json
{
  "metadata": {
    "sessionId": "d0149ca3-...",
    "name": "my refactoring task",
    "projectName": "untitled6",
    "projectPath": "/Users/.../untitled6",
    "branch": "main",
    "commitHash": "a1b2c3d",
    "startTime": 1713100800000,
    "endTime": 1713104400000,
    "ideVersion": "IntelliJ IDEA 2025.2",
    "pluginVersion": "dev"
  },
  "events": [ ... ]
}
```

---

## Offline reconstruction

Because every file-changing event stores the full file contents inline, the project state at any point in time can be reconstructed by replaying `events.jsonl` in timestamp order and applying each `changedFiles` snapshot. No separate checkpoint files are needed.

---

## Usage

1. Open a project in IntelliJ.
2. Open the **Refactoring Tracer** panel in the left sidebar.
3. Click **Start Session** and enter a name for the recording.
4. Work normally — all events are recorded automatically.
5. Click **End Session** when done. `session.json` is written to the output directory.

Alternatively, use **Tools → Refactoring Tracer → Start Session / End Session** from the menu bar.

---

## Building

```bash
./gradlew runIde       # launch a sandbox IDE with the plugin installed
./gradlew buildPlugin  # produce a distributable .zip
```

Requires IntelliJ IDEA 2025.1+ and JDK 17.
