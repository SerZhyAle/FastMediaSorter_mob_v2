# Phase 02 - Harness proving both sides

**Strategic spec:** [`../S1458_bash-pwsh-leading-slash-mangled.md`](../S1458_bash-pwsh-leading-slash-mangled.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 3 / 3
**Started:** 2026-08-09
**Completed:** 2026-08-09

---

## Objective

Prove the guard on both sides before it is allowed to refuse anything: the mangled call is blocked, and every form that must pass does pass.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done - Phase 01.
- [ ] Strategic §6 research items blocking this phase are Resolved - §6.3 is discharged by step 01.1.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `.claude/hooks/guard-bash-slash-arg.tests/Run-Tests.ps1` | New | ≤ 220 |
| `.claude/hooks/guard-bash-slash-arg.ps1` | Modified | ≤ 220 |

> Backup / split thresholds: see Constraints (>500 LOC → backup step, >1500 LOC → split via Manager pattern).
>
> No Kotlin and no flavor source set is touched in this phase; the flavor-placement rule does not apply.

---

## Steps

### Step 02.1 - Cases for the refused form

**Files:** `.claude/hooks/guard-bash-slash-arg.tests/Run-Tests.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Create the harness next to the hook, following the shape of `scripts/quality/assert-shared-test-flavor-scope.tests/Run-Tests.ps1`: a small assert helper, one case per row, a pass/fail tally and a non-zero exit when any case fails. Add the refused cases first - a payload carrying a skill-name value such as `-Reason "/spec-dev S1458 phase 02"`, and one carrying a different parameter with the same value shape - each asserting exit code 2 and an error message naming the offending value.

**Why:**

Strategic §5.1 requires the harness to run the refused call as well as the allowed ones, on the same reasoning §12 of the neighbouring gate ticket records: a guard seen only in one state proves nothing about the other.

**Verification:**

- `Glob` - `.claude/hooks/guard-bash-slash-arg.tests/Run-Tests.ps1` exists.
- Run the harness; the refused cases report PASS.
- `Grep` - each refused case asserts on the exit code, not only on the message text.

**Status:** `[x] done`

**Step Log:**

- 2026-08-09 - Verification 3\3 PASS. Four refused cases: a reason carrying a skill name, a second parameter with the same value shape, the colliding `release` name used as a name, and a single-quoted value. Each asserts the exit code.

---

### Step 02.2 - Cases for every allowed form

**Files:** `.claude/hooks/guard-bash-slash-arg.tests/Run-Tests.ps1`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add one allowed case per row: the doubled leading slash, a command prefixed with `MSYS2_ARG_CONV_EXCL='*'`, every entry of the exempt array written in step 01.1, a command with no `pwsh` at all, and a payload whose tool is not Bash. Each asserts exit code 0.

**Why:**

Strategic §2 goals 3 and 4 require both verified workarounds to keep working after the guard lands and the PowerShell tool to stay untouched, and §11 criterion 5 requires a case per exempt form rather than a claim that the list is safe.

**Verification:**

- Run the harness; every allowed case reports PASS.
- `Grep` - the harness contains one case per entry of the exempt array from step 01.1.
- `Grep` - a case covering a non-Bash payload is present.

**Status:** `[x] done`

**Step Log:**

- 2026-08-09 - Verification 3\3 PASS, with one predicate re-read against step 01.1's inverted design: there is no exempt array to enumerate, so the allowed side covers the corpus classes instead - doubled slash, the environment-variable prefix, an absolute POSIX path, a device path, a `/dev/null` redirect, the colliding name as a real path segment, a command without pwsh, and a non-Bash payload. Eight allowed cases.

---

### Step 02.3 - Fix what the harness catches

**Files:** `.claude/hooks/guard-bash-slash-arg.ps1`
**Depends on:** Step 02.2

**Prompt for developer:**

> Run the harness and repair the hook until every case passes. Correct the hook, not the expectation, unless a case itself encodes a form the measurement in step 01.1 did not observe - in that case delete the case and record why in the step log.

**Why:**

Strategic §7 rates over-blocking as the middle-probability risk whose consequence is the guard being switched off, so a red case is evidence about the perimeter and has to be resolved before the guard is registered.

**Verification:**

- Run the harness; exit code equals 0 and the tally reports zero failures.

**Status:** `[x] done`

**Step Log:**

- 2026-08-09 - Verification 1\1 PASS after two real guard defects were caught and fixed, which is the whole reason the phase exists. A2 failed because a command already carrying `MSYS2_ARG_CONV_EXCL` was still refused, though that measure is exactly what makes the value safe; A8 failed because the hook judged a payload whose tool was not Bash. Both were fixed in the hook, not in the expectations. Harness now 12 passed, 0 failed, exit 0.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - not applicable, no Kotlin or build file is modified in this phase.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] If public API changed: not applicable - no Kotlin touched.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13 / `docs/CODE_AUDIT_PROTOCOL.md` "Phase-boundary audits"; see `/spec-dev` "Phase-boundary audit" step).

---

## Handoff Notes to Next Phase

The guard's behaviour is pinned on both sides while it is still inert. Phase 03 only registers it, so a regression after that point is a registration fault rather than a detection fault.

---

## Rollback Plan

Delete the harness directory - the hook stays inert until phase 03 registers it.
