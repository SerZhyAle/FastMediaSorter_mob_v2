# Phase 03 - Standalone settings dialog + non-local crop/compress

**Strategic spec:** [`../S0410_standalone-image-action-parity.md`](../S0410_standalone-image-action-parity.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** none
**Steps done:** 3 / 3
**Started:** 2026-06-13
**Completed:** 2026-06-13

---

## Objective

Surface the translation/OCR settings dialog in the standalone viewer, and make crop-to-file / compress available and working for non-local images by materializing the source and saving the output to Pictures.

---

## Prerequisites

- [ ] Phase 01 ✅ Done (materialization use case).
- [ ] Phase 02 ✅ Done (binding-free settings dialog).
- [ ] Strategic §6.2 research item Resolved (save target & temp lifecycle).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/menu/overflow_menu_standalone_player.xml` | Modified | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/PhotoVideoStandaloneActivity.kt` | Modified | ≤ 900 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StandalonePlayerViewModel.kt` | Modified | ≤ 400 |

> Menu resources have no `layout-land` counterpart - landscape parity rule not applicable.

---

## Steps

### Step 03.1 - Add the settings menu item

**Files:** `app_v2/src/main/res/menu/overflow_menu_standalone_player.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add an overflow item `menu_image_text_settings` reusing the existing string `@string/translation_settings` (no new string resource - it already exists, used by the in-app `menu_text_settings`). Place it next to the OCR/translate items.

**Verification:**

- `Grep` - `menu_image_text_settings` present in `overflow_menu_standalone_player.xml`.
- `Grep` - `@string/translation_settings` present in that file.

**Status:** `[x]` done

**Step Log:**

- 2026-06-13 - Verification 2/2 PASS. Added menu_image_text_settings reusing @string/translation_settings. Files: overflow_menu_standalone_player.xml.

---

### Step 03.2 - Wire the settings item to the binding-free dialog

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/PhotoVideoStandaloneActivity.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> In the overflow `setOnMenuItemClickListener`, handle `menu_image_text_settings` by showing the Phase 02 `TranslationSettingsDialog` with the activity, the activity as `lifecycleOwner`, and the injected `settingsRepository`. Gate visibility on `capabilityAvailability.isTranslationAvailable()` (hidden otherwise, matching the OCR/translate items). Add the visibility line in the same block that sets `menu_ocr_image` / `menu_translate_image`.

**Verification:**

- `Grep` - `R.id.menu_image_text_settings` present in `PhotoVideoStandaloneActivity.kt`.
- `Grep` - `TranslationSettingsDialog` referenced in the file.

**Status:** `[x]` done

**Step Log:**

- 2026-06-13 - Verification 2/2 PASS. Wired menu_image_text_settings -> TranslationSettingsDialog.show, gated on isTranslationAvailable. Files: PhotoVideoStandaloneActivity.kt.

---

### Step 03.3 - Materialize-on-demand for non-local static images

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StandalonePlayerViewModel.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/PhotoVideoStandaloneActivity.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Inject `MaterializeUriToFileUseCase` into `StandalonePlayerViewModel`. Add a suspend `ensureEditableImage()` that, when `editableImageFile` is null and the current file is a static image (IMAGE, not GIF/APNG), materializes the source URI via the use case and sets `_editableImageFile` to the resulting local path. In `PhotoVideoStandaloneActivity`, change the crop-to-file / compress overflow visibility from `editable` to `isStaticImage` (image type, not gif/apng) - matching the in-app `isStaticBitmap` gate - and, on tap, call `ensureEditableImage()` (await) before invoking `cropDelegate.enterCropMode(CROP_TO_FILE)` / `startCompressedCopy()`. Keep `menu_edit_image` (full edit) and `menu_google_lens` on `editable` (they overwrite in place / share a `File`). No save-target code is needed: with `actionCurrentResource = null`, `ImageCropManager` already writes the output to Downloads and scans it into the gallery (research §6.2).

**Verification:**

- `Grep` - `MaterializeUriToFileUseCase` injected in `StandalonePlayerViewModel.kt`.
- `Grep` - `fun ensureEditableImage` present in `StandalonePlayerViewModel.kt`.
- `Grep` - `ensureEditableImage` called in `PhotoVideoStandaloneActivity.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-13 - Verification 3/3 PASS. VM injects MaterializeUriToFileUseCase + ensureEditableImage(); crop-to-file/compress regated to isStaticImage with materialize-on-tap; save target = Downloads via unchanged ImageCropManager. Compile (fc) PASS.

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for every touched file via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

Non-local static images are now materializable on demand; crop-to-file/compress save to Downloads via the unchanged `ImageCropManager`. Phase 04 reuses the materialization for draw save-as (draw saves through its own merge + Downloads write).

---

## Rollback Plan

Revert phase commit(s). Menu item, host wiring and VM materialization are additive; reverting restores the prior `editable`-only gate. No data migration.
