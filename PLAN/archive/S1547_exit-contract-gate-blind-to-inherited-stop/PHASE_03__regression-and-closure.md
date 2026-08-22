# Phase 03 - Regression and closure

**Strategic spec:** [`../S1547_exit-contract-gate-blind-to-inherited-stop.md`](../S1547_exit-contract-gate-blind-to-inherited-stop.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** none - final phase
**Steps done:** 3 / 3
**Started:** 2026-08-14
**Completed:** 2026-08-14

---

## Objective

Pin the new behaviour with regression cases that fail if the extension is reverted, and close the ticket through the mechanical facade.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/quality/assert-exit-contract.tests/Run-Tests.ps1` | Modified | ≤ 300 |
| `scripts/utils/help.ps1` | Modified | ≤ 400 |
| `docs/SCRIPT_CHEATSHEET.md` | Regenerated | n/a - generated |

---

## Steps

### Step 03.1 - Add the inherited-Stop cases to the regression suite

**Files:** `scripts/quality/assert-exit-contract.tests/Run-Tests.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a case group that writes a sub-directory into the existing sandbox holding a library which sets `Stop` plus sibling scripts that dot-source it: one with a bare `Write-Error` before `exit 2`, one with `-ErrorAction Continue`, one written in the quoted `"$PSScriptRoot\_lib.ps1"` form, and one whose dot-source path comes from a variable rather than a literal. Point the gate at each script alone with `-Path` so one fixture cannot mask another, and assert exit 1, 0, 1, 0 respectively. Reuse `Invoke-Gate`; keep the sandbox teardown in the existing `finally` block.

**Why:**

The suite's own header states that a gate which only ever goes green proves nothing, and strategic §11 criteria 1 to 3 are exactly these three shapes - flag the inherited defect, spare the cured form, and refuse to carry the mode across a path the gate cannot resolve statically.

**Verification:**

- `pwsh -NoProfile -File scripts/quality/assert-exit-contract.tests/Run-Tests.ps1` - `expected: 0 | actual: 0`, pass count 12 -> 16, group `K` all green.
- The cases fail without Step 02.2: a copy of the gate with the pre-change condition-1 block restored scores `expected: 0 | actual: 0` on the K1 fixture - it does not see the defect - while the current gate scores `expected: 1 | actual: 1` on the same file. Copy and fixtures removed after the run.

**Status:** `[x]` done

---

### Step 03.3 - Widen the cheatsheet's header window to the end of the header

**Files:** `scripts/utils/help.ps1`, `docs/SCRIPT_CHEATSHEET.md`
**Depends on:** Step 03.1

**Prompt for developer:**

> The cheatsheet generator reads a fixed 60 lines when looking for a script's `Exit codes` block. Replace that flat cap with a window that runs to the end of a leading `<# .. #>` block and never falls below 60, so a header longer than the old cap keeps its exit codes. Regenerate `docs/SCRIPT_CHEATSHEET.md` afterwards and compare against its pre-ticket state.

**Why:**

Step 02.3 grew this gate's header past the 60-line cap, and the generated cheatsheet responded by dropping the `Exit:` line entirely rather than reporting that it could not find one - the same failure shape this whole ticket is about, a summary that reads clean while the answer is missing.

**Verification:**

- `pwsh -NoProfile -File scripts/utils/help.ps1 -Generate` - `expected: 0 | actual: 0`.
- Diff against the pre-ticket cheatsheet: 8 additions, 1 replacement, 0 losses. Seven scripts regained an `Exit:` line they had lost silently (`adb.ps1`, the detekt gate, the plan-step rewriter, the VSCode entry cleanup, the res/values writer, the locale writer, the string tool) and `session-bootstrap.ps1` regained its truncated exit 3. This gate's own line is unchanged, which is the point of the fix.
- `pwsh -NoProfile -File scripts/utils/help.ps1 -Check` - `expected: 0 | actual: 0`.

**Status:** `[x]` done

---

### Step 03.2 - Close through the mechanical facade

**Files:** none - closure only
**Depends on:** Step 03.1

**Prompt for developer:**

> Run `scripts/post-change.ps1` once for the whole changed set with `-ChangeType Script -ScopeToFile`, naming every file this ticket touched. Read the verdict line and the exit code, and fix whatever a failing gate names before re-running. Then flip the ticket to `Implemented`.

**Why:**

CLAUDE.md section 12 routes mechanical closure through the facade and requires the whole changed set to be named, because a closure that certifies one file of several certifies exactly what it was passed.

**Verification:**

- First run returned `PASS WITH ADVISORIES (1)`, `expected: 0 | actual: 0` - the advisory was `script-cheatsheet-sync-gate`, and Step 03.3 discharged it. The second run repeats the description byte for byte so the dev-log guard collapses it into the one existing row instead of writing a near-duplicate.
- `pwsh -NoProfile -File scripts/spec_catalog/select.ps1 -Id S1547 -Format json` shows `Implemented`.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - not applicable, no Kotlin touched.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for the whole changed set via `scripts/post-change.ps1`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings. The header-window fix is monotone by construction - the window never falls below the old 60 lines - so it can only recover exit-code lines, and the regenerated cheatsheet confirms it: eight gains, zero losses.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Revert the suite's new case group - the gate itself is unaffected.
