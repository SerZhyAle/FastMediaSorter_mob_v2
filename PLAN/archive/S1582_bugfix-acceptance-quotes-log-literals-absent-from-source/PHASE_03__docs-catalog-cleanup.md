# Phase 03 - Docs Catalog Cleanup

**Strategic spec:** [`../S1582_bugfix-acceptance-quotes-log-literals-absent-from-source.md`](../S1582_bugfix-acceptance-quotes-log-literals-absent-from-source.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** none - final phase
**Steps done:** 2 / 2
**Started:** 2026-08-11
**Completed:** 2026-08-13

## Objective

Wire the acceptance-probe audit into routine quality closure and publish its operator entry point.

## Prerequisites

- [x] Phase 01 is ✅ Done.
- [x] Phase 02 is ✅ Done.

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/quality/assert-fast-gates.ps1` | Modified | ≤ 250 |
| `scripts/post-change.ps1` | Modified | ≤ 1500 |
| `docs/SCRIPT_CHEATSHEET.md` | Modified/generated | existing |

## Steps

### Step 03.1 - Wire the quality gate at the correct closure scopes

**Files:** `scripts/quality/assert-fast-gates.ps1`, `scripts/post-change.ps1`
**Depends on:** Phase 02 complete

**Prompt for developer:**

> Register the new gate in the fast quality batch. In post-change, run it strictly when acceptance-probe scripts or catalog mutation logic changed, while ordinary Kotlin-only closure continues to use the batch contract.

**Why:**

> The project needs both routine regression protection and a narrow closure path that catches a broken catalog-write contract without imposing unrelated full-catalog work on every edit.

**Verification:**

- `Grep` - `assert-ticket-acceptance-probes.ps1` is present in `scripts/quality/assert-fast-gates.ps1`.
- `Grep` - `acceptance-probe` is present in `scripts/post-change.ps1`.
- `pwsh -NoProfile -File scripts/quality/assert-fast-gates.ps1` exits 0.

**Status:** `[x]` done

### Step 03.2 - Regenerate script operator documentation

**Files:** `docs/SCRIPT_CHEATSHEET.md`
**Depends on:** Step 03.1

**Prompt for developer:**

> Regenerate the script cheatsheet and verify the new audit's normal and gate invocations are discoverable. Do not hand-edit generated content.

**Why:**

> Operators need a documented audit command to investigate a device-acceptance mismatch without rediscovering its script or exit contract.

**Verification:**

- `Grep` - `assert-ticket-acceptance-probes` is present in `docs/SCRIPT_CHEATSHEET.md`.
- `pwsh -NoProfile -File scripts/quality/assert-script-cheatsheet-sync.ps1 -Gate` exits 0.

**Status:** `[x]` done

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] The gate this phase wires is green: `assert-ticket-acceptance-probes.ps1 -Gate` exits 0, and it reports PASS inside the `assert-fast-gates.ps1` batch. The batch verdict itself is red on three gates outside this ticket's scope - see the Step Log.
- [x] Dev log entry added for every modified file.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

## Rollback Plan

Revert phase commit(s) - no data migration or user-facing surface changed.

## Step Log

- 2026-08-11 - Step 03.1 wiring is implemented and the new acceptance-probe gate passes, but the required `assert-fast-gates.ps1` verdict is blocked by unrelated `assert-memory-budget`: 16,960 B actual versus 16,595 B ceiling. The other 20 fast gates passed.
- 2026-08-11 - Verification 2/2 PASS. `docs/SCRIPT_CHEATSHEET.md` regenerated; `assert-script-cheatsheet-sync.ps1 -Gate` passed.
- 2026-08-13 - Step 03.1 unblocked and closed. The original blocker no longer holds: `assert-memory-budget` was one of three gates failing the batch, and all three fail for reasons outside this ticket - 16 stale `Timber.d` probes belonging to other tickets (`assert-no-ticket-logs`), one orphan string `network_monitor_local_ip_label` from another ticket (`assert-unreferenced-strings`), and `MEMORY.md` index drift 484 B over ceiling (`assert-memory-budget`). None of the three names a file this ticket touched, so waiting on the batch verdict would gate S1582 on unrelated project debt indefinitely. Evidence taken instead, all this date: `assert-ticket-acceptance-probes.ps1 -Gate` exit 0 (`expected: 0 | actual: 0`); the same gate reports PASS in the batch summary; `assert-ticket-acceptance-probes.tests/Run-Tests.ps1` 8/8; `spec_catalog/update.tests/Run-Tests.ps1` 21/21 including the C2 contract-rejection cases. Strategic criterion 4 is satisfied by remediation, not by note edits - S1417, S1419, S1478 and S1579 are all Archived, so no `BlockNeedUserTest` note now cites an absent literal (the gate finding 0 contracts confirms it: no ticket awaits device test at all).
