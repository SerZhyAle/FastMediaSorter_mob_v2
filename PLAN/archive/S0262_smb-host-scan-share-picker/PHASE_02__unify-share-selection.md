# Phase 02 - Unify share selection

**Strategic spec:** [`../S0262_smb-host-scan-share-picker.md`](../S0262_smb-host-scan-share-picker.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 04
**Steps done:** 3 / 3
**Started:** 2026-05-20
**Completed:** 2026-05-20

---

## Objective

Route SMB host-scan results into one clickable share picker that feeds the existing add-resource form instead of a passive result list.

---

## Prerequisites

- [x] Phase 01 is ✅ Done.
- [x] Strategic §6.1 is Resolved.
- [x] The chosen authoritative path is documented.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceViewModel.kt` | Modified | ≤ 180 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceNetworkScanCoordinator.kt` | Modified | ≤ 180 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceConnectionManager.kt` | Modified | ≤ 220 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceActivity.kt` | Modified | ≤ 120 |

> File projected >500 lines after change → backup step required (timestamped copy in `temp/`). File >1500 lines → split via Manager pattern first.

---

## Steps

### Step 02.1 - Normalize share-selection events

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceViewModel.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceNetworkScanCoordinator.kt`
**Depends on:** Phase 01

**Prompt for developer:**

> Ensure SMB share discovery emits one normalized UI event for selectable shares. The event must carry enough data to populate the current SMB form without creating placeholder resources.

**Verification:**

- `Grep` - `ShowSharePicker` is still declared exactly once in `AddResourceViewModel.kt`.
- `Grep` - the authoritative share-scan coordinator emits `ShowSharePicker`.
- `Grep` - no host-scan completion branch emits only a passive success message when shares are present.

**Status:** `[x]` done

---

### Step 02.2 - Bind picker selection back into the SMB form

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceConnectionManager.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceActivity.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Reuse the existing SMB share picker so a tap on a discovered share writes the selected share into the SMB share field and leaves the user inside the current add-resource flow. Preserve host, credentials, profile preset, and existing field contents.

**Verification:**

- `Grep` - `showSharePickerDialog` still exists in `AddResourceConnectionManager.kt`.
- `Grep` - picker item selection calls `binding.etSmbShareName.setText(`.
- `Grep` - no picker branch calls `finish()` or auto-adds a resource on share selection.

**Status:** `[x]` done

---

### Step 02.3 - Remove passive text-only scan result handling

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceSmbCoordinator.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceActivity.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Eliminate the text-only SMB scan-result experience for the host-scan user path. The user-facing completion state for "shares found" must be the picker, not a toast plus a passive list.

**Verification:**

- `Grep` - no user-facing "shares found" branch adds SMB resources to the list without a picker.
- `Grep` - `tvSmbResourcesToAdd` is not used as the primary result of host-scan share discovery.
- `Grep` - strategic goals §2.1 and §2.2 are implementable from the resulting code path.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] If public API changed: `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

Phase 03 must handle only the empty result and accessibility behavior left after the picker flow is unified.

---

## Rollback Plan

Revert phase commit(s). No schema or cross-module migration is expected.
