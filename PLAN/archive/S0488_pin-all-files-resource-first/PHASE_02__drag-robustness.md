# Phase 02 - Drag Robustness

**Strategic spec:** [`../S0488_pin-all-files-resource-first.md`](../S0488_pin-all-files-resource-first.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 3 / 3
**Started:** 2026-06-17
**Completed:** 2026-06-17

---

## Objective

Keep the pinned All-files row immovable during drag-to-reorder: hide its drag handle (mirroring the existing Favorites `-100L` pattern) and block dropping any other resource above index 0, so manual reordering of the other resources never displaces it.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (`MediaResource.isAllFilesPredefined` available).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/ResourceAdapter.kt` | Modified | ≤ 835 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/ResourceItemTouchCallback.kt` | Modified | ≤ 130 |

> `ResourceAdapter.kt` is 824 LOC (> 500) - Step 02.1 takes a timestamped backup before editing.

---

## Steps

### Step 02.1 - Back up `ResourceAdapter.kt`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/ResourceAdapter.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> `ResourceAdapter.kt` exceeds 500 LOC. Copy it to `temp/ResourceAdapter.kt.<yyyyMMdd-HHmmss>.bak` before any edit, per CLAUDE.md safety rule. No source change in this step.

**Verification:**

- `Glob` - `temp/ResourceAdapter.kt.*.bak` matches at least one file.

**Status:** `[x]` done

**Step Log:**

- 2026-06-17 - Verification 1/1 PASS. Backup: temp/ResourceAdapter.kt.20260617-172258.bak. No source change.

---

### Step 02.2 - Make the All-files row non-draggable

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/ResourceAdapter.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Extend the per-row draggability gate that currently reads `dragStartListener != null && resource.id != -100L` so it also excludes the pinned resource: `&& !resource.isAllFilesPredefined`. Apply the same exclusion to every view holder that exposes a drag handle (list and grid, if both bind one). The All-files row must show no drag handle, exactly like the Favorites `-100L` row.

**Verification:**

- `Grep` - `isAllFilesPredefined` present in `ResourceAdapter.kt`.
- `Grep` - `resource.id != -100L && !resource.isAllFilesPredefined` (or equivalent on one line) present at each draggability gate.

**Status:** `[x]` done

**Step Log:**

- 2026-06-17 - Verification 2/2 PASS (both grid + list gates exclude the pinned row; import added). Files: ui/main/ResourceAdapter.kt (+4 LOC). Dev log recorded.

---

### Step 02.3 - Block drops above the pinned row in the touch callback

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/ResourceItemTouchCallback.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> In `ResourceItemTouchCallback`, read the live list via `adapter.getDragOrderedList()`. Override `getMovementFlags` to return `makeMovementFlags(0, 0)` when the item at the dragged position `isAllFilesPredefined` (the pinned row itself cannot be dragged). Override `canDropOver` to return `false` when the target position is 0 and the first item `isAllFilesPredefined` (no other row can be dropped above the pinned row). Leave the existing list/grid direction flags and animation untouched for all other rows.

**Verification:**

- `Grep` - `isAllFilesPredefined` present in `ResourceItemTouchCallback.kt`.
- `Grep` - `canDropOver` present in `ResourceItemTouchCallback.kt`.
- `Grep` - `makeMovementFlags(0, 0)` present.

**Status:** `[x]` done

**Step Log:**

- 2026-06-17 - Verification 3/3 PASS (getMovementFlags guard + canDropOver override + import). Files: ui/main/helpers/ResourceItemTouchCallback.kt (+18 LOC). Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - `check-standard-fast.ps1 -Mode Code` exit 0 (reported UP-TO-DATE; authoritative clean build runs at finalization to remove incremental doubt).
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

Drag-to-reorder no longer displaces the pinned All-files row in list or grid; the pin from Phase 01 plus these gates fully satisfy strategic §11 criterion 5 and wish §3.1.1. Phase 03 records dev log, regenerates the catalog, and inserts the `BlockNeedUserTest` debug tags.

---

## Rollback Plan

Revert phase commit(s) and restore `ResourceAdapter.kt` from the `temp/` backup if needed - no data migration or user-facing string changed.
