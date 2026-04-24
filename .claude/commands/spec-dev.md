# Specification Developer Executor

Execute a tactical specification step by step. The skill reads `PLAN/spec_<short-name>/INDEX.md` + `PHASE_NN__*.md`, follows `Prompt for developer:` instructions in dependency order, runs each step's `Verification:` predicate before flipping the step to `[x] done`, and maintains progress markers (step status, phase status, INDEX counter, strategic `Status:`).

This skill is the **agentic developer** for an already-frozen tactical plan. It is conservative by design — every ambiguity, verification miss, or out-of-scope action triggers a hard stop with a status report, never a guess.

## Usage

```text
/spec-dev <short-name>                    # continue from the first non-done step
/spec-dev <short-name> --phase <NN>       # execute every remaining step in one phase
/spec-dev <short-name> --step <NN.M>      # execute a single specific step
/spec-dev <short-name> --until <NN.M>     # execute up to and including this step
/spec-dev <short-name> --resume           # re-scan state, then continue
/spec-dev <short-name> --dry-run          # print the plan without writing files
```

Examples:

- `/spec-dev player-keybinding-remapping`
- `/spec-dev background-thumbnail-preload --phase 02`
- `/spec-dev vr-hand-tracking --step 03.4`
- `/spec-dev decompose-giant-files --until 02.5`

The strategic spec must exist; the tactical folder must exist. If the strategic `Status:` is `Implemented`/`Verified`/`Partial`/`Broken`, abort — the work is closed. If `Status:` is `Draft`/`Approved` (no tactical folder), abort and recommend `/spec-tech` first.

---

## Status Gate

Read the strategic spec's `Status:` field. Allowed values:

| Strategic `Status:` | Behavior |
|---------------------|----------|
| `Tactical` | Allowed. On first executed step, advance to `In Progress`. |
| `In Progress` | Allowed. Continue. |
| `Draft` / `Approved` | Abort: tactical folder missing or not authored. Recommend `/spec-tech`. |
| `Implemented` / `Verified` | Abort: feature is closed. Recommend `/spec-check` or a new spec. |
| `Partial` / `Broken` | Abort: audit found problems. Recommend `/spec-fix` or `/spec-update --force-locked`. |

`/spec-dev` never operates on a locked spec.

---

## Process

When this command is invoked with `$ARGUMENTS`:

**Step 1 — Parse arguments and load state.**

- Extract `<short-name>` and flags. Compute the target step set:
  - No flags → first non-done step in the first non-done phase.
  - `--phase NN` → every non-done step in phase NN, in numeric order.
  - `--step NN.M` → exactly one step (must currently be `[ ] not done` or `[~] in progress`).
  - `--until NN.M` → every non-done step from current cursor to NN.M inclusive.
  - `--resume` → re-read all phase files; cursor = first non-done step regardless of prior session state.
- Read strategic `PLAN/spec_<short-name>.md`, INDEX, and every phase file in scope. Verify the Status Gate above.
- Verify `Pre-Implementation Blockers` in INDEX are all ticked. If any unchecked → abort with the blocker text.

**Step 2 — Dry-run preview (always).**

Print the planned step set as a table:

| # | Phase | Step | Title | Files | Status (current) |
|---|------:|-----:|-------|-------|:----------------:|
| 1 | 02 | 02.3 | Wire FooManager into Hilt module | `di/AppModule.kt` | `[ ]` |
| 2 | 02 | 02.4 | Inject FooManager into PlayerActivity | `ui/player/PlayerActivity.kt` | `[ ]` |

If `--dry-run`, stop here. Otherwise continue.

**Step 3 — Execute the step set, one step at a time.**

For each step in plan order:

1. **Re-read the phase file.** State may have changed (concurrent edits). Cancel the step if its `Status:` is no longer `[ ] not done` or `[~] in progress` — log "PRE-RESOLVED — skipped".
2. **Verify dependencies.** If `Depends on:` references an earlier step, that step's `Status:` must be `[x] done`. If not — abort with "Dependency violation: step NN.M depends on NN.K which is not done".
3. **Read the `Prompt for developer:` block** verbatim, plus the §Files Touched row(s) for this step.
4. **Read the source files** referenced in the prompt. For each reference to an existing class/method, confirm it exists at the expected path. Missing → abort with "Prompt references `<symbol>` at `<path>`, not found".
5. **Ambiguity check** — see Stop Signals below. If the prompt contains `<TODO>`, `<choose ..>`, `<name X>`, `???`, or any unresolved placeholder → abort with the placeholder and request the user to update the spec via `/spec-update`.
6. **Pre-edit guards:**
   - If a touched file is in a read-only zone (`V1/`, `v2_6/`, `spec_v2/`, `dev/archive/`) → abort.
   - If a touched file is currently > 500 lines AND not already backed up → create a timestamped copy in `temp/` first.
   - If the projected post-edit size > 1000 lines → abort with "line budget violation, split via Manager pattern".
7. **Flip step status to `[~] in progress`** in the phase file before any code change.
8. **Apply the edit** — `Edit` or `Write` per the Prompt. Stay strictly within the prompt's scope. Do not refactor surrounding code, do not add comments beyond what the prompt specifies, do not adjust unrelated imports beyond making the new code compile syntactically.
9. **Run the `Verification:` predicates** for the step:
   - File-existence checks via `Glob`.
   - Declaration checks via `Grep` for `class <Name>` / `fun <name>` (confirm hit is on a declaration line, not a comment / string).
   - Forbidden-call checks (e.g. zero hits of `Log\.d\(`).
   - Value-equality checks (e.g. `@Database(version = N)`).
   - Trilingual key checks where applicable.
10. **Outcome:**
    - All Verification predicates PASS → flip step status to `[x] done`. Append a one-line note to the phase file's "Step log" (see Phase File Update below) with date + brief summary.
    - Any Verification predicate FAIL → leave step status at `[~] in progress`. Append a "FAIL on verification: <which predicate>" note. **Hard stop.** Do not proceed to the next step. Do not attempt to "fix forward".
11. **Run dev log** for every modified source file (single `add_to_dev_log.ps1` invocation per file at the end of the step).

After all planned steps in the current phase complete:

12. **Phase Done Criteria check.** Read the phase's `Phase Done Criteria` block. For each checkbox:
    - Mechanically verifiable (grep / file existence / value equality) → run the check, tick if passes, leave unticked + log if fails.
    - Requires build (`Project compiles — run /build`) → leave unticked, mark as `BUILD-REQUIRED` in the chat summary, hard stop.
    - Requires manual review (visual UI check, runtime test) → leave unticked, mark `MANUAL-REQUIRED`.
13. **If every Phase Done Criterion is ticked:** flip the phase file's `Status:` header to `✅ Done`, set `Completed: <YYYY-MM-DD>`, update the INDEX row + counter.
14. **If any criterion unticked:** leave phase as `🚧 In Progress`. Update step counter only. Hard stop.

After all phases in scope are `✅ Done`:

15. **Strategic Status advance:**
    - First time stepping into the spec from `Tactical` → flip strategic `Status:` to `In Progress`.
    - All phases done after this run → flip strategic `Status:` to `Implemented`. Add `**Implemented date:** <YYYY-MM-DD>` line under Status. Recommend `/spec-check` in the summary.

**Step 4 — Final summary to the user** (Russian).

Always include:

- How many steps were planned, attempted, completed, deferred (BUILD/MANUAL required), aborted (stop signal triggered).
- Current cursor: which step is next on the next `/spec-dev` invocation.
- Any stop-signal that fired, with the exact reason.
- Recommended next command: `/build`, `/spec-dev <name> --resume`, `/spec-update <name>`, `/spec-check <name>`.

---

## Stop Signals (Hard Stops)

The skill stops immediately and reports — never tries to recover, never guesses — on any of:

1. **Ambiguous prompt** — placeholder text (`<TODO>`, `<choose>`, `???`), missing class/method name, unspecified Hilt scope, unspecified coroutine dispatcher.
2. **Verification predicate FAIL** after applying the edit. The step is left `[~] in progress`. The user investigates.
3. **Read-only zone touch** — any file path resolves under `V1/`, `v2_6/`, `spec_v2/`, `dev/archive/`.
4. **Line budget violation** — post-edit projected size > 1000 lines.
5. **Build required** — Phase Done Criterion `Project compiles` cannot be ticked by static analysis. Stop, recommend `/build`.
6. **Room schema change** — any prompt mentions bumping `@Database(version = ..)`, adding a `Migration`, or modifying an `@Entity`. Always stop and require explicit user confirmation, since irreversible data shape changes are too risky for autonomous execution.
7. **Hilt module touch** — any prompt that adds `@Module`, `@Provides`, or modifies a Hilt graph node beyond a single `@Inject constructor(..)` annotation. Stop, ask user to confirm scope/qualifier.
8. **Step references missing symbol** — prompt names a class/method that does not exist at the stated path and the prompt does not also create it.
9. **Dependency violation** — `Depends on:` step is not `[x] done`.
10. **Catalog-affecting change without scan plan** — public API change in a touched file, but the phase file does not include a `dev/CATALOG/<module>.jsonl` regen step. Stop, suggest amending the phase via `/spec-update --tactical --phase NN`.
11. **External system touch** — prompt mentions network, database migration, file deletion outside `temp/`, kill process, force push, edit CI. Stop, require explicit user permission per the project's executing-actions-with-care rule.
12. **Trilingual gap** — step adds a UI string but the prompt names only one of `values/` / `values-ru/` / `values-uk/`. Stop, refuse to fabricate translations.

---

## Phase File Update Conventions

The phase file has step blocks with a `**Status:**` line. `/spec-dev` is the only writer to:

- `**Status:**` per step: `[ ] not done` → `[~] in progress` → `[x] done`.
- Phase header `Status:` and `Steps done:` and `Started:` / `Completed:`.

Append a "Step Log" block at the end of each step section (creating it on first execution):

```markdown
**Status:** `[x] done`

**Step Log:**

- 2026-04-25 — applied edit, Verification 4/4 PASS. Files: path/Foo.kt (+42 LOC). Dev log entry recorded.
```

Step Log entries are append-only. Never rewrite a previous entry. If a step had to be re-executed (e.g. user reverted and re-ran), append a new line, do not edit the old one.

---

## INDEX Update Conventions

`/spec-dev` is also the writer for INDEX rows during execution:

- After step completion → bump the row's `Steps` counter (e.g. `3/7`).
- After phase completion → flip status icon to `✅ Done` and bump `Phases: X/N done` at the top.
- Do NOT touch other INDEX sections (`Pre-Implementation Blockers`, `Completion Gate`, `Blockers Log`, `Change Log`) — those are owned by the user / `/spec-tech`.
- If the user has manually flipped a phase status to `⛔ Blocked` between runs, do not silently override. Stop and ask whether to resume.

---

## What `/spec-dev` Will Never Do

- Run `gradle`, `./gradlew`, `npm`, build commands. Always delegate to `/build`.
- Invoke `git commit`, `git push`, `git rebase`. Staging discipline belongs to the user / `/git`.
- Run tests autonomously. If a step's Verification calls for a test run, stop and ask.
- Modify specs (`/spec`, `/spec-tech`, `/spec-update` own those).
- Modify audit reports (`/spec-check`, `/spec-fix` own those).
- Skip steps "because they look trivial".
- Combine consecutive steps into a single edit.
- Refactor neighbouring code "while we're here".
- Add comments, docstrings, or logging beyond what the prompt specifies.
- Choose a name (class, variable, file) that the prompt did not explicitly state.
- Translate UI strings into RU/UK without the user.
- Mass-rewrite imports, even if a linter would.

---

## Quality Rules

- **One step, one edit.** Each step's edit is committable in isolation. The skill does not bundle.
- **Verification before flip.** A step is `[x] done` only when every predicate in its Verification block returned PASS.
- **Hard stop is the safe default.** When in doubt, stop. The user can always `/spec-update` and re-run.
- **Idempotency.** Running `/spec-dev` twice with no intervening user changes is a no-op on the second run — every step is `[x] done` and there is no work to do.
- **No rollback decisions.** If an edit lands but Verification fails, do NOT auto-revert. The user reads the phase file's Step Log and decides.
- **Author style** in any free-text appended (Step Log lines, phase notes): `..` not `...`. English in tactical bodies.
- **Dev log per file, per step.** Run `add_to_dev_log.ps1` immediately after each step completes — do not batch across steps.
- **Progress markers are the source of truth.** Do not "remember" cursor across runs. Every invocation re-reads phase files and recomputes the cursor from `Status:` lines.
- **Catalog discipline.** If a step changes public API, include the catalog regen as part of the same step's "Files Touched" + Verification — never as a separate untracked side effect.
- **No silent strategic Status flip back.** If the user manually rolls strategic Status from `In Progress` back to `Tactical`, treat it as a deliberate pause — do not auto-advance again until the user runs `/spec-dev --resume`.

---

## Failure Modes to Watch

- **Phantom done flip.** Never mark `[x] done` based on intent. Verification predicate must have actually returned PASS in the current run, not in a prior run.
- **Verification too weak.** A step with one shallow predicate (e.g. only `Glob` for file existence) may flip `[x]` when the body is wrong. Trust the spec author — but if the predicate is suspiciously thin, surface a WARN line in the Step Log: "Verification minimal — recommend manual review before merge".
- **Cursor confusion when phases run in parallel.** If multiple phases have `🚧 In Progress` rows in INDEX, choose the lowest-numbered one. If the user wants a different one, they pass `--phase NN`.
- **Drift between INDEX and phase file.** If INDEX says phase NN is `⬜ Not started` but the phase file's first step is `[x] done`, do not silently fix — surface as a WARN, ask user to confirm via `/spec-fix` or manual edit.
- **Stale Pre-Implementation Blockers.** If a blocker is unchecked but the relevant strategic §6 item is now `Status: Resolved`, surface this and ask the user whether to tick the blocker (do not auto-tick).
- **Excess context.** Reading every phase file at every invocation is expensive. For `--step NN.M`, read only that phase file. For full runs, read INDEX + the active phase file only — load subsequent phases lazily.
- **Hilt graph drift.** Adding `@Inject` somewhere that already has a manual `@Provides` causes duplicate-binding compile errors. The Stop Signal #7 catches the obvious cases, but verify by `Grep` for the class name in `di/` modules before applying.
