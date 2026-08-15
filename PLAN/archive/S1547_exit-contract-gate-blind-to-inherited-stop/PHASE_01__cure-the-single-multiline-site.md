# Phase 01 - Cure the single multi-line site

**Strategic spec:** [`../S1547_exit-contract-gate-blind-to-inherited-stop.md`](../S1547_exit-contract-gate-blind-to-inherited-stop.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02
**Steps done:** 1 / 1
**Started:** 2026-08-14
**Completed:** 2026-08-14

---

## Objective

Rewrite the one call site that the extended rule would flag falsely, so Phase 02 can ship the rule fail-closed with no baseline.

---

## Prerequisites

- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] `temp/CODE.LOCK` acquired before the edit and released right after.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/spec_catalog/purge-probe-records.ps1` | Modified | ≤ 120 |

---

## Steps

### Step 01.1 - Hoist the two-line abort message into a variable

**Files:** `scripts/spec_catalog/purge-probe-records.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> At the abort branch near line 72, the message passed to `Write-Error` is built from two string literals across two physical lines, which puts `-ErrorAction Continue` on the continuation line. Build that message into a local variable first, then call `Write-Error $msg -ErrorAction Continue` on one line, leaving `exit 3` where it is. Change no behaviour: the same text, the same stream, the same exit code.

**Why:**

The gate's scan is line-based, so a `-ErrorAction` sitting on a continuation line is invisible to it and the site reads as a defect although the exit is reachable - strategic §4 measured this as the single hit the extended rule produces, and ADR-2 chose to cure the site with the workaround the gate's own header documents rather than teach a line scanner where a multi-line statement ends.

**Verification:**

- `Grep` - `Write-Error \$` matches on one line in that file, and that line also carries `-ErrorAction Continue`. PASS - line 79 is the only `Write-Error` call left, and it carries the switch.
- `Grep` - no line in that file matches `Write-Error \(` any more. PASS - zero hits.
- The script has no `-WhatIf`, and its only other terminating path mutates the archive, so the smoke test is the `-Help` path plus an AST parse. `[Parser]::ParseFile` reports no errors, and `-Help` prints the header - `expected: 0 | actual: 0`.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - not applicable, no Kotlin touched.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry deferred to Phase 03 - one entry per logical change, not per file.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings. The change is one statement split into two, same text, same stream, same exit code; the guard branch it sits in is unreachable by design and untouched.

---

## Handoff Notes to Next Phase

The tree now carries zero rule-A findings among the scripts that inherit `Stop`, so Phase 02 may wire the extension straight into the fail-closed path without introducing a ratchet.

---

## Rollback Plan

Revert the single hunk - no data migration and no user-facing surface changed.
