# Phase 01 - Inventory scan paths

**Strategic spec:** [`../S0262_smb-host-scan-share-picker.md`](../S0262_smb-host-scan-share-picker.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 3 / 3
**Started:** 2026-05-20
**Completed:** 2026-05-20

---

## Objective

Map the actual SMB host-scan and share-scan paths, then decide which path stays authoritative for post-scan share selection.

---

## Prerequisites

- [x] Working tree is clean or on a feature branch.
- [x] Strategic §6.1 is still open and must be resolved in this phase.
- [x] Add Resource SMB flow is reproducible from current code.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceActivity.kt` | Modified | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceNetworkScanCoordinator.kt` | Modified | ≤ 180 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceSmbCoordinator.kt` | Modified | ≤ 220 |

> File projected >500 lines after change → backup step required (timestamped copy in `temp/`). File >1500 lines → split via Manager pattern first.

---

## Steps

### Step 01.1 - Trace SMB entry points

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceActivity.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Identify every SMB UI trigger that can lead to share discovery or host discovery. Mark which button launches host selection, which button launches direct share scan, and where each callback lands in the ViewModel layer.

**Verification:**

- `Grep` - `btnScanNetwork.setOnClickListener` exists in `AddResourceActivity.kt`.
- `Grep` - `btnScanShares.setOnClickListener` exists in `AddResourceActivity.kt`.
- `Grep` - both listeners call ViewModel or dialog logic on distinct lines.

**Status:** `[x]` done

---

### Step 01.2 - Resolve the authoritative post-scan path

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceNetworkScanCoordinator.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceSmbCoordinator.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Compare the two SMB result flows and choose one authoritative path for post-scan share selection. Keep the user-facing host-scan flow on the picker-based contract; remove or quarantine any parallel path that turns scan results into passive `resourcesToAdd` entries for the same scenario.

**Verification:**

- `Grep` - only one coordinator remains responsible for emitting share-selection UI events after SMB share discovery.
- `Grep` - no user-facing host-scan path appends discovered shares into `resourcesToAdd` without an explicit picker step.
- `Grep` - strategic blocker §6.1 can be marked Resolved in the spec.

**Status:** `[x]` done

---

### Step 01.3 - Record the chosen path in the spec

**Files:** `PLAN/S0262_smb-host-scan-share-picker.md`, `PLAN/S0262_smb-host-scan-share-picker/INDEX.md`
**Depends on:** Step 01.2

**Prompt for developer:**

> Update strategic §6.1 and the tactical blocker list to reflect the resolved authoritative SMB scan path. Do not start implementation work from later phases in the same step.

**Verification:**

- `Grep` - `## 6. Открытые вопросы / Research items` in the strategic spec shows §6.1 as `Resolved`.
- `Grep` - `INDEX.md` no longer lists the §6.1 blocker.
- `Grep` - Phase 01 still has exactly 3 steps.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] If public API changed: `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

Phase 02 must implement only the authoritative SMB share-selection path chosen here. Do not preserve duplicate UI result contracts for the same host-scan scenario.

---

## Rollback Plan

Revert phase commit(s). No schema or persisted-data change is expected in this phase.
