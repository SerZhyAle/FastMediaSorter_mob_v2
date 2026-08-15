# Phase 01 - Dispatcher Core

**Strategic spec:** [`../S0269_post_change_ritual_unification.md`](../S0269_post_change_ritual_unification.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04
**Steps done:** 2 / 2
**Started:** 2026-05-20
**Completed:** 2026-05-20

---

## Objective

Upgrade `scripts/post-change.ps1` into a change-type-aware dispatcher with fail-closed step reporting and compatibility-safe defaults.

---

## Prerequisites

- [x] All phases in "Depends on" are ✅ Done.
- [x] Strategic §6.1 is Resolved.
- [x] Strategic §6.2 is Resolved.
- [x] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/post-change.ps1` | Modified | ≤ 260 |

> File projected >500 lines after change → backup step required (timestamped copy in `temp/`). File >1500 lines → split via Manager pattern first.

---

## Steps

### Step 01.1 - Add the change-type contract and step reporter

**Files:** `scripts/post-change.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Extend `scripts/post-change.ps1` with an explicit `-ChangeType` router and compact step-reporting helpers. Preserve the existing mandatory dev-log inputs and keep the script readable as the single operator entry point.

**Verification:**

- `Grep` - `[ValidateSet('Doc', 'Script', 'Config', 'Kotlin', 'Xml', 'Mixed')]` appears in `scripts/post-change.ps1`.
- `Grep` - `function Write-StepResult` appears in `scripts/post-change.ps1`.
- `Grep` - `function Invoke-Step` appears in `scripts/post-change.ps1`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-20 - Verification 3/3 PASS. Files: scripts/post-change.ps1. Dev log recorded.

---

### Step 01.2 - Route catalog and strings steps through the new dispatcher

**Files:** `scripts/post-change.ps1`
**Depends on:** Step 01.1

**Prompt for developer:**

> Replace the legacy scan/render flow with `catalog_sync.ps1`, add `-NoProfile` to every child `pwsh` invocation, and keep backward compatibility for old callers that still rely on `-SkipScan` or omit `-ChangeType`.

**Verification:**

- `Grep` - `scripts/catalog_sync.ps1` appears in `scripts/post-change.ps1`.
- `Grep` - `-NoProfile -File` appears in `scripts/post-change.ps1`.
- `Grep` - `$resolvedChangeType =` appears in `scripts/post-change.ps1`.
- `Grep` - `Skip-Step "spec-catalog-sync"` appears in `scripts/post-change.ps1`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-20 - Verification 4/4 PASS. Files: scripts/post-change.ps1. Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] `pwsh -NoProfile -File scripts/post-change.ps1 -File "scripts/post-change.ps1" -Target "post-change.ps1" -Description "Add change-type routing and fail-closed step dispatch" -ChangeType Script` exits 0.
- [x] `Grep` for `dev/CATALOG/scripts/scan.ps1` in `scripts/post-change.ps1` returns zero hits.
- [x] Dev log entry added for `scripts/post-change.ps1` via `\.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

The dispatcher now owns the mechanical routing surface; the next phase aligns operator rules and agent guidance with that contract.

---

## Rollback Plan

Revert phase commit(s) - no data migration or user-facing surface changed.