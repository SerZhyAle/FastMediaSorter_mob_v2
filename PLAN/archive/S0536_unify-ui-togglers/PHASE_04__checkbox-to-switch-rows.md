# Phase 04 - Checkbox to switch rows

**Strategic spec:** [`../S0536_unify-ui-togglers.md`](../S0536_unify-ui-togglers.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 05
**Steps done:** 4 / 4
**Started:** 2026-06-20
**Completed:** 2026-06-20

---

## Objective

Convert the on/off single-setting checkboxes (mis-rendered as `MaterialCheckBox`) into canonical `SettingsToggleRow` switch rows per ARCHITECTURE.md Pattern A; reuse existing strings.

---

## Prerequisites

- [ ] Phase 01 ✅ (component on `MaterialSwitch`).
- [ ] Each converted row reuses its existing `@string` label/description - no new strings expected. If a label is an inline literal, lift it to a `@string` (EN/RU/UK lockstep) as part of the step.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/dialog_player_settings.xml` | Modified | n/a |
| `app_v2/src/main/res/layout-land/dialog_player_settings.xml` | Modified | n/a |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/PlayerSettingsDialog.kt` | Modified | ≤ 210 |
| `app_v2/src/main/res/layout/dialog_slideshow_settings.xml` | Modified | n/a |
| `app_v2/src/main/res/layout-land/dialog_slideshow_settings.xml` | Modified | n/a |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/SlideshowSettingsDialogFragment.kt` | Modified | ≤ 215 |
| `app_v2/src/main/res/layout/dialog_translation_settings.xml` | Modified | n/a |
| `app_v2/src/main/res/layout-land/dialog_translation_settings.xml` | Modified | n/a |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/TranslationSettingsDialog.kt` | Modified | ≤ 270 |
| `app_v2/src/main/res/layout/dialog_camera_ocr_settings.xml` | Modified | n/a |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameraocr/CameraOcrTranslateActivity.kt` | Modified | ≤ 440 |

> Landscape parity: `dialog_player_settings`, `dialog_slideshow_settings`, `dialog_translation_settings` each have a `layout-land` counterpart - edit in lockstep. `dialog_camera_ocr_settings.xml` has NO `layout-land`.

---

## Steps

### Step 04.1 - Convert player-settings repeat/subtitles checkboxes

**Files:** `app_v2/src/main/res/layout/dialog_player_settings.xml`, `app_v2/src/main/res/layout-land/dialog_player_settings.xml`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/PlayerSettingsDialog.kt`

**Depends on:** - start of phase

**Prompt for developer:**

> Replace the `MaterialCheckBox` rows `cbRepeatVideo` and `cbShowSubtitles` (both portrait and landscape) with `com.sza.fastmediasorter.ui.common.widget.SettingsToggleRow`. Keep each `android:id` so the binding field names stay. Move the existing title `@string` into `app:str_title` and any helper/description text into `app:str_subtitle`; delete the redundant label/description `TextView`s the checkbox row used. In `PlayerSettingsDialog.kt` change the change-callback registration from the `MaterialCheckBox` two-arg form `{ _, isChecked -> .. }` to the `SettingsToggleRow` one-arg form `{ isChecked -> .. }`; `isChecked` reads/writes are unchanged. Leave the speed `ChipGroup` and 3D-mode `RadioGroup` in this dialog untouched.

**Verification:**

- `Grep` - `SettingsToggleRow` matches `cbRepeatVideo` and `cbShowSubtitles` in both `dialog_player_settings.xml` files.
- `Grep` - `MaterialCheckBox` returns zero hits for these two ids in both orientations.
- `Grep` - `android:id="@+id/cbRepeatVideo"` and `@+id/cbShowSubtitles` still present in both orientations.
- `.\a.ps1 fc` passes.

**Status:** `[x]` done

**Step Log:**

- 2026-06-20 - Verification PASS (greps). Both `dialog_player_settings.xml` (portrait + land): `cbRepeatVideo` + `cbShowSubtitles` now `SettingsToggleRow` (title -> `app:str_title`, desc -> `app:str_subtitle`, redundant label TextViews removed); `xmlns:app` added to root ScrollView; zero `MaterialCheckBox` for these ids. `PlayerSettingsDialog.kt`: `cbShowSubtitles` listener converted to one-arg `{ isChecked -> .. }` (line 80); `cbRepeatVideo` had no listener; populate/read `isChecked` unchanged. Speed ChipGroup + 3D RadioGroup untouched. Compile consolidated into the ticket's single end build.

---

### Step 04.2 - Convert slideshow play-to-end checkbox

**Files:** `app_v2/src/main/res/layout/dialog_slideshow_settings.xml`, `app_v2/src/main/res/layout-land/dialog_slideshow_settings.xml`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/SlideshowSettingsDialogFragment.kt`

**Depends on:** Step 04.1

**Prompt for developer:**

> Replace the `cbPlayToEnd` `MaterialCheckBox` row (portrait + landscape) with `SettingsToggleRow`, keeping the `android:id`, lifting the label `@string` into `app:str_title` and the hint into `app:str_subtitle`, and removing the redundant hint `TextView`. In `SlideshowSettingsDialogFragment.kt` adapt the checkbox callback to the one-arg `SettingsToggleRow` listener; `isChecked` access unchanged.

**Verification:**

- `Grep` - `SettingsToggleRow` matches `cbPlayToEnd` in both `dialog_slideshow_settings.xml` files.
- `Grep` - `MaterialCheckBox` returns zero hits for `cbPlayToEnd` in both orientations.
- `.\a.ps1 fc` passes.

**Status:** `[x]` done

**Step Log:**

- 2026-06-20 - Verification PASS (greps). Both `dialog_slideshow_settings.xml` (portrait + land): `cbPlayToEnd` now `SettingsToggleRow` (label -> `app:str_title`, hint -> `app:str_subtitle`); redundant `tvPlayToEndHint` removed and `tvMusicLabel` re-pointed `app:layout_constraintTop_toBottomOf="@id/cbPlayToEnd"` so the constraint chain holds; zero `MaterialCheckBox`. `SlideshowSettingsDialogFragment.kt`: `cbPlayToEnd` listener -> one-arg (line 84); populate `isChecked` unchanged. Compile consolidated into the ticket's single end build.

---

### Step 04.3 - Convert translation lens-style checkbox

**Files:** `app_v2/src/main/res/layout/dialog_translation_settings.xml`, `app_v2/src/main/res/layout-land/dialog_translation_settings.xml`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/TranslationSettingsDialog.kt`

**Depends on:** Step 04.1

**Prompt for developer:**

> Replace the `switchLensStyle` `MaterialCheckBox` row (portrait + landscape) with `SettingsToggleRow`. The `android:id` is already `switch*` - keep it. Lift label/description `@string`s into `app:str_title`/`app:str_subtitle` and delete the redundant `TextView`s. In `TranslationSettingsDialog.kt` adapt the change callback to the one-arg `SettingsToggleRow` listener.

**Verification:**

- `Grep` - `SettingsToggleRow` matches `switchLensStyle` in both `dialog_translation_settings.xml` files.
- `Grep` - `MaterialCheckBox` returns zero hits for `switchLensStyle` in both orientations.
- `.\a.ps1 fc` passes.

**Status:** `[x]` done

**Step Log:**

- 2026-06-20 - Verification PASS (greps). Both `dialog_translation_settings.xml` (portrait + land): `switchLensStyle` now `SettingsToggleRow` (label -> `app:str_title`, redundant TextView removed); zero `MaterialCheckBox`. `TranslationSettingsDialog.kt`: `findViewById` type changed `MaterialCheckBox` -> `SettingsToggleRow` (line 65); no listener; `isChecked` read/write (lines 119/152) unchanged. Compile consolidated into the ticket's single end build.

---

### Step 04.4 - Convert camera-OCR "OCR only" checkbox

**Files:** `app_v2/src/main/res/layout/dialog_camera_ocr_settings.xml`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameraocr/CameraOcrTranslateActivity.kt`

**Depends on:** Step 04.1

**Prompt for developer:**

> Replace the `cbOcrOnly` `MaterialCheckBox` row with `SettingsToggleRow`, keeping the `android:id` and lifting its title/description `@string`s into `app:str_title`/`app:str_subtitle`. Deleting the row's own label/description `TextView`s also removes their hardcoded `android:textColor="#FFFFFF"` (the component uses theme colors) - fixing that incidental Rule-19 violation for this row only; do not chase the dialog's other hardcoded colors here (tracked separately). In `CameraOcrTranslateActivity.kt` adapt the checkbox callback to the one-arg `SettingsToggleRow` listener. This dialog has no `layout-land` variant - single file.

**Verification:**

- `Grep` - `SettingsToggleRow` matches `cbOcrOnly` in `dialog_camera_ocr_settings.xml`.
- `Grep` - `MaterialCheckBox` returns zero hits for `cbOcrOnly`.
- `Grep` - no `android:textColor="#` on the converted row (the SettingsToggleRow replaces the hardcoded-color labels).
- `.\a.ps1 fc` passes.

**Status:** `[x]` done

**Step Log:**

- 2026-06-20 - Verification PASS (greps). `dialog_camera_ocr_settings.xml` (no land variant): `cbOcrOnly` now `SettingsToggleRow` (title -> `app:str_title`, desc -> `app:str_subtitle`, label TextViews removed), `xmlns:app` added to root; zero `MaterialCheckBox`, no `textColor="#"` on the row (the surrounding labels already used `?attr/` tokens, not the `#FFFFFF` the stale research noted). `CameraOcrTranslateActivity.kt`: `findViewById` type -> `SettingsToggleRow` (line 295), listener -> one-arg (line 340), unused `import android.widget.CheckBox` removed (Rule 21). Compile consolidated into the ticket's single end build.

---

## Phase Done Criteria

- [x] Every `Step 04.*` is `[x] done`.
- [x] Project compiles - consolidated `.\a.ps1 d` -> BUILD SUCCESSFUL in 58s (covers Phases 02-04).
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] If any label was lifted to a new `@string`: N/A - all converted rows reused existing `@string` keys (no new strings created).
- [ ] Dev log entry added for the touched files (batched). - batched in Phase 05.2.

---

## Handoff Notes to Next Phase

All on/off single-setting checkboxes are now `SettingsToggleRow` switch rows. The only remaining checkboxes in the app are selection/multiselect/consent controls, all deferred to S0537.

---

## Rollback Plan

Revert the phase commit. No persisted-state or schema change - each converted row keeps its original `isChecked` read/write seam; only the widget and its label TextViews changed.
