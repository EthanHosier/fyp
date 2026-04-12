You are building the **final-form data collection plugin** for my MEng thesis project.

Project context:
The thesis is about **trajectory-level refactoring analysis**. The plugin’s job is **only to collect rich, structured trace data from the IDE** so that a separate offline analysis pipeline can later:

1. reconstruct the developer’s refactoring trajectory,
2. segment it into meaningful steps/checkpoints,
3. score the observed trajectory under a process-quality metric,
4. compare it against higher-scoring alternative trajectories.

The plugin is **not** responsible for scoring, counterfactual generation, or deep semantic analysis. It should focus on **capturing the right raw data and checkpoint states cleanly and reliably**.

The target IDE is Intellij IDEA. Write the plugin in kotlin. It will be doing analysis of Java Programs.

## Core design principle

The plugin must capture:

* **raw IDE/process events**
* **explicit and implicit checkpoint boundaries**
* **enough file-state data at each checkpoint to reconstruct project states later**

We are intentionally prioritising **completeness and clarity of collected data over storage efficiency** for now.

At every checkpoint, store the **full contents of all files changed since the previous checkpoint**. We are not trying to optimise storage yet.

---

## Main responsibilities of the plugin

Implement a plugin that continuously records developer activity during a session and emits a structured trace log plus checkpoint records.

The plugin should support:

1. **event collection**
2. **checkpoint triggering**
3. **changed-file capture at each checkpoint**
4. **session metadata capture**
5. **export/storage in a clean structured format**

---

## Conceptual model

There are three layers:

### 1. Raw events

Low-level things that happen in the IDE:

* edit bursts
* file focus changes
* saves
* explicit IntelliJ refactoring operations
* file renames/moves/creates/deletes
* builds/tests starting and finishing
* manual checkpoint markers

### 2. Checkpoints

Meaningful project-state boundaries where we snapshot the changed files since the previous checkpoint.

### 3. Later offline analysis

Not part of this plugin. That separate pipeline will later:

* reconstruct full states from checkpoints,
* run tools like RefactoringMiner,
* compute metrics,
* segment transitions more semantically,
* score trajectories.

So the plugin should collect data in a way that makes that later analysis easy.

---

## Checkpoints we want to capture

We want both **explicit** and **implicit** checkpoints.

### Explicit checkpoints

Create a checkpoint immediately when one of these happens:

* an IntelliJ automated refactoring operation completes successfully
* the user manually triggers a “mark checkpoint” action from the plugin
* a major file structure operation occurs such as file rename or move
* a build finishes
* a test run finishes

### Implicit checkpoints

Also create checkpoints when inferred from behaviour:

* an inactivity gap after editing, with configurable threshold
* a clear task/entity/file focus shift after a burst of edits
* the code returns to a more stable state after being temporarily broken, if detectable from build/test/compile feedback
* possibly after significant manual edit bursts even without explicit IDE refactorings

The plugin should collect enough information for these implicit boundaries to be identified and justified.

Even if some implicit boundary logic is heuristic, implement it now rather than leaving it out.

---

## What to record

### A. Session metadata

Record:

* session id
* project identifier
* project path if needed locally
* branch name if available
* current git commit hash if available
* session start timestamp
* session end timestamp
* IDE version
* plugin version
* language/build-system metadata if available

### B. Editor / file interaction events

Record:

* file opened
* file closed
* file focused / unfocused
* save events
* edit bursts, not necessarily every keystroke individually

For edit bursts, capture:

* event id
* file path
* start timestamp
* end timestamp
* inserted/deleted character counts if available
* affected line ranges if available
* whether edits were contiguous or scattered if feasible

Do not optimise for tiny event volume. It is acceptable to log fairly richly.

### C. Explicit IntelliJ refactoring events

Record whenever IntelliJ refactoring tools are used:

* refactoring type
* timestamp start/end
* success/cancel/failure
* source entities/files
* destination entities/files where relevant
* any metadata IntelliJ exposes about affected usages

Examples:

* rename
* move
* extract method
* inline
* change signature
* safe delete
* other built-in refactoring actions if interceptable

These are especially important.

### D. File structure / project structure events

Record:

* file create
* file delete
* file rename
* file move
* package/directory move if available
* other structural project operations if observable

### E. Validation / stability events

Record:

* build started
* build finished
* build success/failure
* test run started
* test run finished
* test success/failure summary
* compile error count if available
* warnings/inspection summaries if available and easy to access
* durations for build/test runs
* scope if known, e.g. project/module/test class

These are important because later analysis will use them as stability/safety signals.

### F. Manual plugin actions

Provide and record:

* a manual “mark checkpoint” action
* optionally a “start task” / “end task” marker if easy to implement
* plugin enable/disable events
* session start/stop events

---

## What a checkpoint record must contain

Whenever a checkpoint is created, store:

* checkpoint id
* timestamp
* checkpoint trigger type
* session id
* branch name if available
* commit hash if available
* summary of recent events since previous checkpoint
* list of files changed since previous checkpoint
* for each changed file:

    * path
    * full file contents at checkpoint time
    * whether created / modified / renamed / moved / deleted
    * previous path if renamed/moved
* build/test/compile status summary at checkpoint time
* optional current active file / focused entity if available

Important:
We are **not** storing the full project at every checkpoint.
We are storing the **full contents of each file changed since the previous checkpoint**, so that later analysis can reconstruct the sequence of project states by replaying checkpoints from the initial baseline.

Also store an initial baseline state reference for the session if needed for later reconstruction.

---

## Reconstruction requirement

Design the stored data so that later, an offline analysis tool can reconstruct project state over time.

This means:

* there must be a clear session baseline
* checkpoints must be ordered
* each checkpoint must contain enough changed-file data to update reconstructed state
* rename/move/delete semantics must be represented explicitly
* file contents must reflect the exact state at checkpoint time

Prefer correctness and reconstructability over storage efficiency.

---

## Event and checkpoint architecture

Implement a clean internal model with at least:

### Event

Generic event with:

* id
* type
* timestamps
* session id
* related files
* related entities if known
* payload

### Checkpoint

With:

* id
* trigger type
* timestamp
* changed files payload
* validation summary
* metadata snapshot

### Session

With:

* session metadata
* ordered events
* ordered checkpoints

Please make the schema structured and serialisable, ideally JSON-based or something similarly easy to export and inspect.

---

## Non-goals for this plugin

Do **not** implement:

* RefactoringMiner integration
* trajectory scoring
* smell detection
* readability metrics
* counterfactual trajectory generation
* natural-language explanations of bad steps

Those happen later in analysis.

However, structure the collected data so those later stages are straightforward.

---

## Engineering expectations

I want production-quality plugin structure, not a throwaway prototype.

Please:

* design the plugin cleanly and modularly
* use sensible abstractions for event capture, checkpointing, storage, and export
* make checkpoint trigger logic configurable where sensible
* include clear data classes / schemas
* expose a simple way to inspect exported traces
* prefer explicitness and reliability over cleverness

---

## Deliverables

Build the plugin code and include:

1. the IntelliJ plugin implementation
2. the event model / checkpoint model
3. storage/export logic
4. checkpoint trigger logic for both explicit and implicit checkpoints
5. a brief README explaining:

    * what is captured
    * checkpoint rules
    * where data is stored
    * export format
    * how later reconstruction works conceptually

If you need to make a design choice, prefer the option that preserves **more information for later offline analysis**.

A couple of optional lines you could append, depending on how much autonomy you want to give the other agent:

* “Make reasonable IntelliJ API choices without asking me for confirmation.”
* “Prioritise clean architecture and complete data capture over UI polish.”
