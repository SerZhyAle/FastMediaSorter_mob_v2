# Phase 04 — Player Integration

**Strategic spec:** [`../S0106_player-image-crop.md`](../S0106_player-image-crop.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02, Phase 03
**Blocks:** Phase 05
**Steps done:** 6 / 6
**Started:** —
**Completed:** —

---

## Objective

Wire the three crop commands end-to-end: add them to the overflow menu planner, implement callbacks, instantiate `ImageCropManager` in `PlayerManagerInitializer`, and add the crop overlay attach/detach logic to `PlayerActivity`.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done (`ImageCropManager` implemented).
- [ ] Phase 03 is ✅ Done (`CropOverlayView` + layouts exist).
- [ ] Pre-Implementation Blocker §6.2 resolved: post-"Crop to file" navigation decision confirmed.
- [ ] Working tree is clean or on a feature branch.
- [ ] `PlayerActivity.kt` backup created in `temp/` (file > 500 lines).
- [ ] `CommandPanelController.kt` backup created in `temp/` (file > 500 lines).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CommandPanelLayoutPlanner.kt` | Modified | ≤ 350 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/CommandPanelController.kt` | Modified | ≤ 1100 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerCommandPanelCallbackImpl.kt` | Modified | ≤ 330 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerManagerInitializer.kt` | Modified | — |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt` | Modified | ≤ 1120 |

---

## Steps

### Step 4.1 — Add CROP, CROP_TO_FILE, COMPRESS_COPY to CommandPanelLayoutPlanner

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CommandPanelLayoutPlanner.kt`
**Depends on:** — start of phase

**Status:** `[x] done`
**Step Log:** Three enum entries CROP/CROP_TO_FILE/COMPRESS_COPY added at priority 620/630/640. All verified.

---

### Step 4.2 — Add conditions for the three commands in buildActiveCommands()

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CommandPanelLayoutPlanner.kt`
**Depends on:** Step 4.1

**Status:** `[x] done`
**Step Log:** isStaticBitmap + three add() calls after OPEN_IN_SEPARATE_WINDOW in buildActiveCommands. All verified.

---

### Step 4.3 — Add callbacks to CommandPanelController.CommandPanelCallback and overflow handler

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/CommandPanelController.kt`
**Depends on:** Step 4.1

**Status:** `[x] done`
**Step Log:** Three callback methods added to interface + three when-cases in showOverflowMenu. All verified.

---

### Step 4.4 — Implement callbacks in PlayerCommandPanelCallbackImpl

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerCommandPanelCallbackImpl.kt`
**Depends on:** Step 4.3

**Status:** `[x] done`
**Step Log:** Three override implementations delegate to activity.enterImageCropMode / startCompressedCopy. All verified.

---

### Step 4.5 — Instantiate ImageCropManager in PlayerManagerInitializer and expose to PlayerActivity

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerManagerInitializer.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt`
**Depends on:** Step 4.4

**Status:** `[x] done`
**Step Log:** imageCropManager lateinit field + init in PlayerManagerInitializer + enterImageCropMode + startCompressedCopy + imageCropCallback. All verified.

---

### Step 4.6 — Implement showCropOverlay / hideCropOverlay in PlayerActivity

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt`
**Depends on:** Step 4.5

**Status:** `[x] done`
**Step Log:** showCropOverlay/hideCropOverlay implemented inline with Step 4.5. cropOverlayView field, R.layout reference, btn_crop_confirm/cancel wired. All verified.

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.
- [ ] String locale parity: `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "menu_crop"` exits 0.

---

## Handoff Notes to Next Phase

- All three crop commands are wired end-to-end: overflow menu → callback → manager → overlay → I/O.
- `Timber.d("S0106: ...")` tag fires when `enterCropMode` is called — verifiable in logcat during device test.
- Final phase is docs/catalog cleanup only.

---

## Rollback Plan

Revert phase commits. Crop overlay was added dynamically — no layout changes to the existing player layout. `CommandPanelLayoutPlanner` and `CommandPanelController` changes are isolated additions. No data migration.
