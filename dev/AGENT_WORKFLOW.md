# ENGINEERING WORKFLOW (5-STEP PROCESS)

**Rule**: Strict phase separation. No coding before Step 4.

### 8.0 TASK DEFINITION (Постановка задачи)
- **Action**: Ask clarifying questions. Expand and refine the task.
- **Output**: Detailed task description in **RUSSIAN** inside a file in the `dev/` directory.
- **Gate**: DO NOT proceed until the task is perfectly clarified and aligned with the user.
- **Branch check**: Run `git branch --show-current`. Confirm the session is on the expected branch before any file edit. If on `main`, proceed only for hotfixes or pre-branch tooling setup; all other development belongs on a `DEBUG-v00N` branch.
- **UI/UX Gate**: For any user-facing change, explicitly enumerate all ambiguous UI decisions before continuing. Minimum checklist: placement per orientation, direct button vs overflow vs top menu, visibility predicates by media/file type, action priority, hidden vs disabled behavior, labels/icons/tooltips/help text, empty/error/loading states, confirmation/overwrite/fallback behavior, and accessibility. If any item is unresolved, implementation is blocked.

### 8.1 RESEARCH PHASE (Исследование)
- **Action**: Analyze current "AS-IS" state. Launch multiple sub-agents in parallel for independent lookups - always prefer concurrent over sequential.
- **Parallel pattern**: local catalog/grep agents + `WebSearch`/`WebFetch` agents for the same question run simultaneously in one message; do not wait for one before starting the other.
- **Catalog before an unnarrowed Kotlin search is mechanical (S1344)**: `dev/CATALOG/scripts/query.ps1 -Sector <name>` maps a product sector across `ui`/`domain`/`data` in one call, and a `PreToolUse` hook refuses a `.kt` `Grep`/`Glob` that names no subtree until the catalog has been queried once in the session. A search already scoped to a directory is never blocked.
- **Web search is default ON**: use `WebSearch`/`WebFetch` freely for Android API behaviour, library docs, best practices, open bugs - no permission needed, no need to announce it.
- **Focus**: Collect files, classes, current solutions, exact line numbers, and relevant external references (docs, changelogs, known issues).
- **Output**: A comprehensive temporary file in `temp/` with the full context.

### 8.2 DESIGN PHASE (Дизайн решения)
- **Action**: Prepare an architecture and solution design using C4 model approach.
- **Focus**: What to improve, fix, and add. Data flow, ADRs, testing requirements, API contracts.
- **Output**: Design document in `dev/` directory in **RUSSIAN**.
- **Gate**: Wait for human REVIEW and confirmation.
- **UI/UX Deliverable**: For UI tasks, include a decision table with approved behavior for portrait, landscape, overflow, visibility, fallback, and accessibility. Missing decisions keep the task blocked.

### 8.3 PLANNING PHASE (Планирование)
- **Action**: Break down the approved design into an execution plan (sequence, priorities). All planning artifacts MUST be in **ENGLISH**.
- **Focus**: 
  - For large tasks: Create a strategic plan + separate tactical files (phases) for specific steps.
  - For each step: Include clear instructions and detailed prompts.
- **Output**: Work plan files (Markdown) with checkboxes.
- **Branch note**: If this task includes work not intended for the upcoming release, identify those steps explicitly and flag them for the "future" DEBUG branch.

### 8.4 IMPLEMENTATION PHASE (Имплементация)
- **Action**: Execute the plan step-by-step AFTER human review and adjustments.
- **Workflow**: 
  - Write code and other objects iteratively.
  - Build and commit after each non-trivial step.
  - Mark progress directly in the planning files (`[x]`).
  - **FLAVOR RULES**: If the task involves a specific flavor (e.g., `noLegal`, `vr`), strictly follow the isolation rules in `dev/FLAVOR_DEVELOPMENT_RULES.md`. DO NOT use `BuildConfig` checks in `src/main`.
  - **FEATURES UPDATE (MANDATORY)**: After implementing any new user-facing feature, add a description entry to ALL THREE files: `docs/FEATURES.md` (EN), `docs/FEATURES_RU.md` (RU), `docs/FEATURES_UK.md` (UK). Do this before marking the step complete. Use consistent bullet style matching existing entries.
  - **PHASE-BOUNDARY AUDIT (MANDATORY)**: before starting the next phase, audit the phase just finished against `docs/CODE_AUDIT_PROTOCOL.md` (Layer 1 always; Layers 2-4 when lifecycle/coroutine/listener/player/Room was touched) and fix P0/P1 findings immediately - see CLAUDE.md §13 "Phase-boundary audits". A defect caught here costs this phase's rework; left for the end of the task it costs every later phase's rework too. `/spec-dev` runs this automatically per tactical phase; for work driven directly from this document, run it by hand at each phase boundary, right before the session-snapshot call below.
- **Validation ladder (mandatory):** Every implementation step closes with the level of evidence appropriate to its change type - see CLAUDE.md `## Validation Requirements` table. Grep-only is sufficient only for doc-only steps. Code, config, or script changes must close with the corresponding build/test/run gate. A step is NOT done until evidence passes.

### 9. PROGRESS JOURNAL

The human-readable progress journal lives at `logs/dev_progress.log`. It records only the essential signal per step; raw command output belongs in `temp/sessions/`.

#### 9.1 Session markers

Every journal file begins with a session-start marker and ends (when closed cleanly) with a session-end marker:

```
=== SESSION START [YYYY-MM-DD HH:MM:SS] branch=<branch> spec=<Sxxxx|ad-hoc> ===
...entries...
=== SESSION END [YYYY-MM-DD HH:MM:SS] result=<PASS|PARTIAL|BLOCKED> ===
```

#### 9.2 Step entry schema

Each step produces exactly one concise journal entry:

```
[STEP <phase>.<step>] <verb> <target>
changed: <comma-separated file paths or "doc-only">
validation: <command or predicate> → <PASS|FAIL|SKIP>
evidence: <temp/sessions/<artifact> or "inline">
blocker: <description or "none">
next: <next step id or "phase done">
```

- `validation` must name the actual command or predicate, not just "verified" or "checked".
- `FAIL` on any line means the step is NOT done - add `blocker:` and stop.
- `SKIP` is allowed only for doc-only steps where a grep-only preflight is the correct closure level.

#### 9.3 Raw evidence separation

Full build logs, grep dumps, and verbose command output are NOT written into the journal. They go to `temp/sessions/` with a filename of the form `<YYYYMMDD_HHMMSS>_<step-id>_<type>.txt` (e.g. `20260514_183000_04-1_build.txt`). Reference them from the `evidence:` field in the step entry.

#### 9.4 Rotation

At the start of every new session, rename the current `logs/dev_progress.log` to `logs/dev_progress_<YYYYMMDD_HHMMSS>.log` (timestamp = session start time), then create a fresh `logs/dev_progress.log` with the new session-start marker. This keeps each session independently readable and the active file short.

Rotated files are kept in `logs/` alongside timestamped logcat files. No automatic purge - manual cleanup only.

---

## Agent Continuity Layer (S0268)

The continuity layer is composed of five independent PowerShell utilities under `scripts/agent_continuity/`. Each one runs in isolation (ADR-4) and may be invoked alone; together they cover bootstrap, resume, request logging, request digest, and dirty-tree classification. Tactical decisions for every research item (§6.1..§6.6) are recorded in `scripts/agent_continuity/README.md`; consult that file when behaviour seems ambiguous.

### Bootstrap packet

Invoke at the start of any significant session.

```
pwsh -NoProfile -File scripts/agent_continuity/start-packet.ps1 [-Ticket S####]
```

Prints seven blocks: `## branch`, `## dirty-tree`, `## active-ticket`, `## modules`, `## prompt-routing`, `## docs-vs-gradle`, `## ux-volatility`. Output is fact-only; routing decisions stay with the agent.

### Resume layer

Invoke `session-snapshot.ps1` at every phase boundary (and at task end), `session-resume.ps1` at session start when continuing prior work.

```
pwsh -NoProfile -File scripts/agent_continuity/session-snapshot.ps1 -Goal "<title>" [-Ticket S####] [-FilesTouched @(...)] [-Decisions ...] [-Blockers ...] [-NextStep ...] [-Agent <id>]
pwsh -NoProfile -File scripts/agent_continuity/session-resume.ps1 [-Agent <id>]
```

Snapshots land at `temp/sessions/<yyyyMMddHHmmss>_<agent>_state.md` with six sections: `## goal`, `## ticket`, `## files-touched`, `## decisions`, `## blockers`, `## next-step`. Reader prints the most recent snapshot or `NO-SNAPSHOT` when none exists.

### Request logger

Invoke at the end of a significant session (or at every phase boundary inside `/spec-dev`).

```
pwsh -NoProfile -File scripts/agent_continuity/request-log.ps1 -Request "<text>" [-Route /...] [-Module app_v2|wear] [-Flavor ...] [-Ticket S####] [-FilesTouched @(...)] [-ValidationKind ...] [-ValidationExit <int>] [-InterruptionMarker ...] [-Outcome done|partial|aborted|escalated]
```

Appends one JSONL line to `dev/agent-continuity/requests.jsonl` (gitignored) with eleven keys: `ts`, `request`, `route`, `module`, `flavor`, `ticket`, `files_touched`, `validation_kind`, `validation_exit`, `interruption_marker`, `outcome`.

### Request digest

Invoke on demand for periodic review or audit.

```
pwsh -NoProfile -File scripts/agent_continuity/request-digest.ps1 [-Window 30]
```

Prints `# Request Digest` header plus five sections: `## top-routes`, `## top-modules`, `## validation-cost`, `## interruptions`, `## ux-volatility`. Tolerates an absent request log and falls back to `dev/FUNCTIONALITY.log` plus recent `PLAN/S*.md` modification times.

### Dirty-tree guard

Invoke before editing `CLAUDE.md`, `AGENTS.md`, `app_v2/build.gradle.kts`, or any other shared-infrastructure file when working on a non-empty branch.

```
pwsh -NoProfile -File scripts/agent_continuity/dirty-tree-guard.ps1 -Paths @("path1","path2",...) [-ExtraHighRiskPaths @(...)]
```

Prints one of four categories: `clean`, `same area`, `same file`, `high-risk overlap`. The guard never blocks - it informs and the agent owns the decision.
