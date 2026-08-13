# Phase 01 - python3 PATH shim

**Strategic spec:** [`../S1594_agent-mechanical-command-failures.md`](../S1594_agent-mechanical-command-failures.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** none
**Steps done:** 1 / 1
**Started:** 2026-08-12
**Completed:** 2026-08-12

---

## Objective

Make the command name `python3` resolve to the interpreter that exists on this machine, closing 91 of the week's 181 `exit 127` failures without any guard or refusal.

---

## Prerequisites

- [x] All phases in "Depends on" are ✅ Done - none.
- [x] Strategic §6 research items blocking this phase are Resolved - none block this phase.
- [x] `~/bin` is on PATH and already holds the `pwsh` shim used as the form precedent.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `~/bin/python3` | New | ≤ 10 |

> This file lives in the per-machine home directory, not in the repository, so it is not version-controlled with this checkout. That gap is recorded as a risk in strategic §7 and its mitigation is Phase 04's documentation step.

---

## Steps

### Step 01.1 - Add the python3 shim

**Files:** `~/bin/python3`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `~/bin/python3` as an executable bash shim that forwards all arguments to the `python` already on PATH and preserves its exit code, following the form of the existing `~/bin/pwsh` shim. Use `exec python "$@"` so the shim does not add a process layer and the exit code passes through untouched. Resolve `python` through PATH rather than hardcoding the venv path, so the shim keeps working if the interpreter moves. Mark the file executable.

**Why:**

Strategic ADR-1 rules that this class is cured by making the name resolvable rather than by refusing the call: a refusal costs the same turn as `exit 127` and removes nothing, while the shim costs zero turns and closes the whole 91-failure class.

**Verification:**

- `Bash` - `which python3` prints a path under `~/bin`.
- `Bash` - `python3 --version` exits 0 and prints a version string.
- `Bash` - `python3 -c "import sys; sys.exit(7)"` exits 7, proving the exit code passes through.
- `Bash` - `test -x ~/bin/python3` exits 0.

**Status:** `[x] done`

**Step Log:**

- 2026-08-12 - Verification 5/5 PASS. Files: `~/bin/python3` (New, 7 LOC). `which python3` -> `/c/Users/serzh/bin/python3`; `python3 --version` -> `Python 3.12.10` exit 0; `sys.exit(7)` -> exit 7, so the exit code passes through; executable bit set; argv passthrough confirmed with two positional args. Written via `printf` rather than the Write tool so the shebang line carries LF, not CRLF - a CRLF shebang fails as `bad interpreter`. Dev log recorded in Phase 04 batch.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - **not applicable**: this phase touches no Kotlin, resource or gradle file, so no build is run (CLAUDE.md section 12 validation ladder, Script rung: run it, exit 0).
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for the shim via `.\scripts\add_to_dev_log.ps1` - deferred to the Phase 04 batch, since CLAUDE.md section 12 wants one entry per logical change rather than one per file.
- [x] If public API changed: not applicable - no Kotlin touched.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings. Layer 1 only; Layers 2-4 have no surface here. One P3 noted and accepted: the shim resolves `python` through PATH, so adding a `python` shim to the same directory would create a loop - the header comment states the PATH-resolution choice so the next reader sees it.

---

## Handoff Notes to Next Phase

`python3` is a live command name on this machine. Phase 02's guard must therefore NOT list `python3` among the unavailable interpreters - the two phases would otherwise contradict each other, with the guard refusing a call the shim can serve.

---

## Rollback Plan

Delete `~/bin/python3`. No data migration, no user-facing surface, no repository file changed.
