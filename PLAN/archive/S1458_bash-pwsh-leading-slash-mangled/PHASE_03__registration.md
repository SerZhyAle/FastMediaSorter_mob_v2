# Phase 03 - Registration

**Strategic spec:** [`../S1458_bash-pwsh-leading-slash-mangled.md`](../S1458_bash-pwsh-leading-slash-mangled.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04
**Steps done:** 1 / 1
**Started:** 2026-08-09
**Completed:** 2026-08-09

---

## Objective

Make the guard live for this repository by registering it in the project settings, then prove it fires on a real tool call rather than only in the harness.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done - Phase 02.
- [ ] Strategic §6 research items blocking this phase are Resolved - all three are answered by the end of Phase 02.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `.claude/settings.json` | Modified | ≤ 40 |

> Backup / split thresholds: see Constraints (>500 LOC → backup step, >1500 LOC → split via Manager pattern).
>
> No Kotlin and no flavor source set is touched in this phase; the flavor-placement rule does not apply.

---

## Steps

### Step 03.1 - Register the guard as a Bash PreToolUse hook

**Files:** `.claude/settings.json`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a `PreToolUse` entry with matcher `Bash` invoking `pwsh -NoProfile -File "$CLAUDE_PROJECT_DIR/.claude/hooks/guard-bash-slash-arg.ps1"`, in the same form as the existing `Grep|Glob` entry for the catalog guard. Leave that entry and both other hook registrations untouched.

**Why:**

Strategic §3.2 fixes the artifact's home as the project settings so the guard stays under version control and scoped to the repository whose lock and lease reasons are the ones being corrupted.

**Verification:**

- `Grep` - `guard-bash-slash-arg.ps1` matches in `.claude/settings.json`.
- `Grep` - the new entry carries matcher `Bash`.
- `Grep` - `guard-catalog-before-kt-search.ps1` still matches in the same file.
- The file parses as JSON.
- Live call from the Bash tool carrying a slash-leading value is refused before bash runs, and the message names the offending value.
- The same call with a doubled leading slash runs, and the receiving script prints the value with a single leading slash.

**Status:** `[x] done`

**Step Log:**

- 2026-08-09 - Verification 6\6 PASS. Registered as a second `PreToolUse` entry with matcher `Bash`; the catalog guard's entry is untouched and the file parses. Live refusal observed on `pwsh -NoProfile -File scripts/utils/lock-status.ps1 -Reason "/spec-dev S1458 live check"` - blocked before bash ran, message named `/spec-dev` and all three replacements. Live allowed side on `temp/S1458/echo-arg.ps1`: `//spec-dev S1458 phase 03` arrived as `/spec-dev S1458 phase 03`, the `MSYS2_ARG_CONV_EXCL` form arrived identically, and `/c/Users/serzh` still converted to `C:/Users/serzh`, which is the conversion a real path is passed for.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - not applicable, no Kotlin or build file is modified in this phase.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] If public API changed: not applicable - no Kotlin touched.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13 / `docs/CODE_AUDIT_PROTOCOL.md` "Phase-boundary audits"; see `/spec-dev` "Phase-boundary audit" step).

---

## Handoff Notes to Next Phase

From this point the guard refuses live calls in this repository, including calls made by sibling sessions. A false refusal reported after this phase is a perimeter fault and belongs to step 01.1's list, not to the registration.

---

## Rollback Plan

Remove the `PreToolUse` entry added in step 03.1 - the hook returns to inert and no other behaviour changes.
