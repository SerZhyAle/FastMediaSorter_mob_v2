# Phase 04 - Standalone draw overlay

**Strategic spec:** [`../S0410_standalone-image-action-parity.md`](../S0410_standalone-image-action-parity.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase (draw save-as writes a merged bitmap to a fresh file; no source path or earlier artifact needed)
**Blocks:** none
**Steps done:** 3 / 3
**Started:** 2026-06-13
**Completed:** 2026-06-13
**Started:** -
**Completed:** -

---

## Objective

Enable draw-over-image in the standalone viewer: provide the displayed bitmap, mount the draw overlay, and save the drawn result as a new file (to Pictures for non-local sources).

---

## Prerequisites

- [ ] Strategic §6.3 research item Resolved (draw save on non-local).
- [ ] Working tree clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/menu/overflow_menu_standalone_player.xml` | Modified | ≤ 130 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/PhotoVideoStandaloneActivity.kt` | Modified | ≤ 950 |

> Menu resources have no `layout-land` counterpart - landscape parity rule not applicable.

---

## Steps

### Step 04.1 - Provide the displayed bitmap to the action seam

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/PhotoVideoStandaloneActivity.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Replace the `PlayerActionHost.displayedBitmap` override (currently `null` with the "draw overlay not yet wired in standalone" note) with `binding.photoView.drawable?.toBitmap()`. This is the base bitmap the draw overlay merges onto.

**Verification:**

- `Grep` - `override val displayedBitmap` present and no longer returns `null` (returns `photoView.drawable`).
- `Grep` - `// draw overlay not yet wired in standalone` returns zero hits.

**Status:** `[x]` done

**Step Log:**

- 2026-06-13 - Verification 2/2 PASS. displayedBitmap -> binding.photoView.drawable?.toBitmap(). Files: PhotoVideoStandaloneActivity.kt.

---

### Step 04.2 - Mount the draw overlay and add the menu item

**Files:** `app_v2/src/main/res/menu/overflow_menu_standalone_player.xml`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/PhotoVideoStandaloneActivity.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Add an overflow item `menu_draw_overlay` reusing the existing string `@string/menu_draw_overlay` (already used by the in-app menu - no new string). In the host, lazily create an `ImageDrawOverlayManager` mounted into `overlayMountTarget` (`binding.mediaContentArea`), passing `screenRotationManager`, `hasAccelerometer`, and a `DrawKeepExportHelper` - mirror the in-app instantiation in `PlayerManagerInitializer` (search for `ImageDrawOverlayManager(`). Handle the menu item by calling `enterDrawMode()`. Gate visibility on a static image (image type, not gif/apng), matching the in-app `isStaticBitmap`.

**Verification:**

- `Grep` - `menu_draw_overlay` present in `overflow_menu_standalone_player.xml`.
- `Grep` - `ImageDrawOverlayManager(` present in `PhotoVideoStandaloneActivity.kt`.
- `Grep` - `enterDrawMode` present in `PhotoVideoStandaloneActivity.kt`.
- `Grep` - `R.id.menu_draw_overlay` handled in the overflow click listener.

**Status:** `[x]` done

**Step Log:**

- 2026-06-13 - Verification adapted (manager + bindToolbar live in StandaloneDrawSaveHelper, not inline). Added draw_overlay_toolbar_stub `<include>` to portrait + landscape layouts; StandaloneDrawSaveHelper builds ImageDrawOverlayManager (mediaContentArea, screenRotationManager, hasAccelerometer, DrawKeepExportHelper) + bindToolbar; menu_draw_overlay item + ensureDrawHelper().enterDrawMode() handler (gated isStaticImage); back-press cancels draw. Files: overflow_menu_standalone_player.xml, both standalone layouts, StandaloneDrawSaveHelper.kt, PhotoVideoStandaloneActivity.kt.

---

### Step 04.3 - Save the drawn result

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/PhotoVideoStandaloneActivity.kt`
**Depends on:** Step 04.2

**Prompt for developer:**

> Per strategic §6.3 / ADR-1: implement the draw save callback by merging the base bitmap (`baseBitmapProvider`) with the overlay via `MergeDrawOverlayUseCase`, then writing the merged JPEG to a new file under `Environment.DIRECTORY_DOWNLOADS` and calling `MediaScannerConnection.scanFile` so it appears in the gallery; toast the result. This needs no source path (a fresh merged file), so no materialization. Wire only the save-as callback the parity requires; in-place overwrite / Keep export are optional. Inject `MergeDrawOverlayUseCase` into the host.

**Verification:**

- `Grep` - `MergeDrawOverlayUseCase` referenced in `PhotoVideoStandaloneActivity.kt` (injected) and `StandaloneDrawSaveHelper.kt` (merge call).
- `Grep` - `DIRECTORY_DOWNLOADS` and `MediaScannerConnection` present in the draw save path (`StandaloneDrawSaveHelper.kt`).

**Status:** `[x]` done

**Step Log:**

- 2026-06-13 - Verification 2/2 PASS (save path lives in StandaloneDrawSaveHelper.save: merge via MergeDrawOverlayUseCase -> write to DIRECTORY_DOWNLOADS -> MediaScannerConnection.scanFile -> toast). Compile (fc) PASS.

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Dev log entry added for every touched file via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

Draw overlay is wired and shares the non-local save path with crop-to-file/compress. Final phase regenerates catalog, dev log, and decides the FEATURES sentence.

---

## Rollback Plan

Revert phase commit(s). Draw wiring is additive; reverting restores `displayedBitmap = null` and removes the menu item. No data migration.
