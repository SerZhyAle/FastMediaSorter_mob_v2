# Phase 02 — wire-into-managers

**Strategic spec:** [`../S0126_image-editor-output-autoname.md`](../S0126_image-editor-output-autoname.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 2 / 2
**Started:** 2026-05-09
**Completed:** 2026-05-09

---

## Objective

Replace the two independent inline filename-building expressions in `ImageCropManager` and `ImageDrawOverlayManager` with calls to `ImageEditorFileNamer.buildName`.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (`ImageEditorFileNamer.kt` compiles and is reachable from the same package).
- [ ] Working tree is clean or on the same feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/ImageCropManager.kt` | Modified | ≤ 497 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/ImageDrawOverlayManager.kt` | Modified | ≤ 349 |

> Neither file exceeds 500 lines — no backup step required.

Landscape parity: no layout XML is touched in this phase.

---

## Steps

### Step 2.1 — Update ImageCropManager.showCropFilenameDialog

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/ImageCropManager.kt`
**Depends on:** Phase 01 done

**Prompt for developer:**

> In `ImageCropManager.showCropFilenameDialog()` (line ~301):
>
> 1. Remove the `val ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyMMdd_HHmm"))` line and the two unused imports it requires (`LocalDateTime`, `DateTimeFormatter`) if they are no longer referenced elsewhere in the file.
> 2. Replace the `val defaultName = when (mode) { ... }` block with:
>    ```kotlin
>    val defaultName = when (mode) {
>        CropMode.CROP_TO_FILE -> ImageEditorFileNamer.buildName(baseName, ext, ImageEditorFileNamer.CROP)
>        CropMode.COMPRESS_COPY -> ImageEditorFileNamer.buildName(baseName, ext, ImageEditorFileNamer.COMPRESS)
>        CropMode.CROP -> ImageEditorFileNamer.buildName(baseName, ext, ImageEditorFileNamer.CROP)
>    }
>    ```
> 3. Add import `com.sza.fastmediasorter.ui.player.helpers.ImageEditorFileNamer` if not auto-imported (same package — may not need an import).
> 4. Do not touch any other method.

**Verification:**

- `Grep` — `_shrink_` returns zero hits in `ImageCropManager.kt`.
- `Grep` — `_crop_` (underscore-crop-underscore) returns zero hits in `ImageCropManager.kt`.
- `Grep` — `yyMMdd_HHmm` (underscore) returns zero hits in `ImageCropManager.kt`.
- `Grep` — `ImageEditorFileNamer.buildName` matches at least twice in `ImageCropManager.kt`.
- `Grep -n "Log\.d\("` — zero hits in `ImageCropManager.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-09 — Verification: `_shrink_`=0 PASS, `yyMMdd_HHmm`=0 PASS, `buildName`=3 PASS, `Log.d`=0 PASS. Note: `_crop_` predicate matched `R.string.dialog_crop_filename_hint` (pre-existing resource key, unrelated to filename construction — confirmed benign via `yyMMdd_HHmm`=0). Old filename template removed. Dev log recorded.

---

### Step 2.2 — Update ImageDrawOverlayManager.handleSaveRequest

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/ImageDrawOverlayManager.kt`
**Depends on:** Step 2.1

**Prompt for developer:**

> In `ImageDrawOverlayManager.handleSaveRequest()` (line ~105):
>
> 1. Remove the `val stamp = SimpleDateFormat("yyMMdd_HHmm", Locale.US).format(Date())` line.
> 2. Remove unused imports `java.text.SimpleDateFormat`, `java.util.Date`, `java.util.Locale` if they are not used elsewhere in the file. Keep `java.util.Locale` if referenced by other code.
> 3. Replace:
>    ```kotlin
>    val defaultFilename = "${baseName}_draw_${stamp}${ext}"
>    ```
>    with:
>    ```kotlin
>    // ext already contains the leading dot (e.g. ".jpg") — strip it before passing
>    val extNoDot = ext.trimStart('.')
>    val defaultFilename = ImageEditorFileNamer.buildName(baseName, extNoDot, ImageEditorFileNamer.DRAW)
>    ```
> 4. Do not touch any other method.

**Verification:**

- `Grep` — `_draw_` (underscore-draw-underscore) returns zero hits in `ImageDrawOverlayManager.kt`.
- `Grep` — `SimpleDateFormat` returns zero hits in `ImageDrawOverlayManager.kt`.
- `Grep` — `yyMMdd_HHmm` (underscore) returns zero hits in `ImageDrawOverlayManager.kt`.
- `Grep` — `ImageEditorFileNamer.buildName` matches once in `ImageDrawOverlayManager.kt`.
- `Grep -n "Log\.d\("` — zero hits in `ImageDrawOverlayManager.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-09 — Verification: `SimpleDateFormat`=0 PASS, `yyMMdd_HHmm`=0 PASS, `buildName`=1 PASS, `Log.d`=0 PASS. Note: `_draw_` matched 8 resource IDs (btn_draw_tool_*, btn_draw_save, btn_draw_cancel) — pre-existing, unrelated to filename construction. Old template removed. Dev log recorded.

---

## Phase Done Criteria

- [x] Steps 2.1 and 2.2 above are `[x] done`.
- [x] Project compiles — `.\build-debug.PS1` returned BUILD SUCCESSFUL on 2026-05-09.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entries added for both modified files via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

Both managers now delegate filename construction to `ImageEditorFileNamer`. The format `name_operation-YYMMDD-hhmm.ext` is active for all three operations. Phase 03 updates visible documentation.

---

## Rollback Plan

Revert phase commit(s) — no data migration or user-facing surface changed. Old `_shrink_` / `_draw_` filenames already on disk are not affected.
