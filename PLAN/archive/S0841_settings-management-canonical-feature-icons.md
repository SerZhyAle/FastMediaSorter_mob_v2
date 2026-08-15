# S0841 - Settings Management canonical feature icons

**Ticket:** S0841
**Status:** Archived
**Priority:** 50
**Date:** 2026-07-01
**Tier:** 1 - Quick Win
**Source:** User request 2026-07-01 (`/spec-draft`)

<!-- auto-approved by /spec-all - 2026-07-01 -->

## Goal

На странице настроек «Управление» (fragment_settings_destinations) три строки сейчас без иконок, хотя у этих функций уже есть канонические иконки в приложении. Присвоить строкам их канонические иконки, как у соседней строки «Калькулятор» (`ic_calculator`). Только визуальные иконки - поведение, тексты, порядок, enable/disable не меняются. Portrait + landscape.

## 1. Confirmed scope (research 2026-07-01)

All three rows live in `app_v2/src/main/res/layout/fragment_settings_destinations.xml` (+ `layout-land/` counterpart), "Additional programs" group. Canonical icons come from `core/panel/InternalRouteCatalog.kt` - the authoritative feature -> icon registry ("the label/icon the matching widget already uses"). The sibling `rowEnableCalculator` already follows this pattern (`app:str_icon="@drawable/ic_calculator"` == the calculator route icon).

- `rowCameraOcrTranslationEnabled` (`SettingsToggleRow`) - missing icon -> `ic_camera_ocr_translate` (InternalRouteCatalog `KEY_OCR`).
- `rowEmbeddedGame` (`SettingsToggleRow`) - missing icon -> `ic_game_kryvavitsa` (InternalRouteCatalog `KEY_GAME`).
- `btnEditAppPanel` (`MaterialButton`) - "Редакция панели программ", no icon -> `ic_apps` (the app-launch-panel icon; also the `csh_icon` of this very group's header).

`SettingsToggleRow` exposes `app:str_icon` (reference); `MaterialButton` uses `app:icon`. All three drawables exist.

## 2. Phase 1 - Assign canonical icons (portrait + landscape)

In BOTH `layout/fragment_settings_destinations.xml` and `layout-land/fragment_settings_destinations.xml`:

1. `rowCameraOcrTranslationEnabled`: add `app:str_icon="@drawable/ic_camera_ocr_translate"`.
2. `rowEmbeddedGame`: add `app:str_icon="@drawable/ic_game_kryvavitsa"`.
3. `btnEditAppPanel`: add `app:icon="@drawable/ic_apps"` + `app:iconGravity="textStart"`.

**Verification:** `.\a.ps1 fr` (resource/manifest compile) passes; both layouts reference existing drawables; no behavior/label/order change.

## 3. Open points

Resolved during research:

1. Canonical source when multiple icons exist - `InternalRouteCatalog` is authoritative (matches how `rowEnableCalculator` already picks `ic_calculator`).
2. Rows already support per-row icons - yes, `SettingsToggleRow app:str_icon` / `MaterialButton app:icon`; no mapping extension needed.
3. Second sentence means the camera-translation icon (not a game icon) - confirmed by feature name; used `ic_camera_ocr_translate`.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0836, S0838 (sibling icon-unification tickets).

## Related

- S0836 (settings-general-remote-resource-icons-unify), S0838 (send-to-icons-unify-settings-and-menus) - same icon-unification family.

## Last Audit

**Date:** 2026-07-01 (via /spec-next -> /spec-all)
**Verdict:** Verified

- Portrait `layout/fragment_settings_destinations.xml` + landscape `layout-land/fragment_settings_destinations.xml`, both edited (Rule 11):
  - `rowCameraOcrTranslationEnabled` -> `app:str_icon="@drawable/ic_camera_ocr_translate"`
  - `rowEmbeddedGame` -> `app:str_icon="@drawable/ic_game_kryvavitsa"`
  - `btnEditAppPanel` -> `app:icon="@drawable/ic_apps"` + `app:iconGravity="textStart"`
- Icons are the canonical `InternalRouteCatalog` feature icons (KEY_OCR / KEY_GAME) and `ic_apps` (the app-launch-panel icon, also this group's `csh_icon`), matching the existing `rowEnableCalculator` -> `ic_calculator` pattern.
- No behavior/label/order/enable-disable change; only `app:str_icon` / `app:icon` added.
- `a.ps1 fr` (processStandardDebugResources, merge + process executed) -> BUILD SUCCESSFUL, all three drawables resolve.
- `assert-settings-doc-sync.ps1 -Gate` -> OK (manifest fresh, reference up to date); icon-only change does not affect settings metadata, so Rule 22 needs no regen.
- No ALL_FEATURES record: cosmetic icon assignment to existing settings, not a new capability.
