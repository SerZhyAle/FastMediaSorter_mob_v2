# Phase 01 - Crop action row: language cluster

**Ticket:** S0354
**Status:** Pending

## Steps

1. In `app_v2/src/main/res/layout/activity_camera_ocr_translate.xml`, inside the crop-state action row (the horizontal `LinearLayout` holding `btnCropRetry` and `btnCropConfirm`), insert a language cluster between the two buttons:
   - Horizontal `LinearLayout` id `layoutCropLangCluster`, `gravity=center_vertical`, horizontal margins.
   - `MaterialButton` id `btnCropOcrLang`, compact outlined style, `minWidth=0dp`, `minHeight=48dp`, `focusable=true`, `clickable=true`, single-line, `contentDescription=@string/camera_ocr_crop_lang_ocr_desc`. Text placeholder set at runtime (flag + code).
   - `ImageView` id `ivCropLangArrow`, `src=@drawable/ic_arrow_forward`, `importantForAccessibility=no`, tint matching the action-row foreground.
   - `MaterialButton` id `btnCropTargetLang`, same compact spec, `contentDescription=@string/camera_ocr_crop_lang_target_desc`.
   - **Verification:** `rg -n "btnCropOcrLang|ivCropLangArrow|btnCropTargetLang|layoutCropLangCluster" app_v2/src/main/res/layout/activity_camera_ocr_translate.xml` returns 4 ids. expected: 4+ matches | actual: record.

2. Set the focus chain in the action row: `btnCropRetry` `nextFocusRight=@+id/btnCropOcrLang`; `btnCropOcrLang` `nextFocusLeft=@+id/btnCropRetry` `nextFocusRight=@+id/btnCropTargetLang`; `btnCropTargetLang` `nextFocusLeft=@+id/btnCropOcrLang` `nextFocusRight=@+id/btnCropConfirm`; `btnCropConfirm` `nextFocusLeft=@+id/btnCropTargetLang`.
   - **Verification:** `rg -n "nextFocus" app_v2/src/main/res/layout/activity_camera_ocr_translate.xml` shows the chain across the four controls. expected: chain present | actual: record.

3. Confirm no landscape counterpart exists for this layout (single ConstraintLayout serves both orientations).
   - **Verification:** `Glob app_v2/src/main/res/layout-land/activity_camera_ocr_translate.xml` → none. expected: no file | actual: record. (Rule 12 satisfied.)

4. Add content-description strings via `scripts/utils/set-android-string.ps1 -Action add` across EN/RU/UK:
   - `camera_ocr_crop_lang_ocr_desc` - EN "OCR language", RU "Язык распознавания", UK "Мова розпізнавання".
   - `camera_ocr_crop_lang_target_desc` - EN "Translation language", RU "Язык перевода", UK "Мова перекладу".
   - **Verification:** `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "camera_ocr_crop_lang_"` exit 0. expected: 0 | actual: record.

## Done criteria

- Action row XML contains the three cluster controls with a complete focus chain.
- Two content-description keys present and localized in EN/RU/UK.
