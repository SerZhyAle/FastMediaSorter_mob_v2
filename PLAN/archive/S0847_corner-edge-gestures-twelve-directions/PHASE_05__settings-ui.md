# Phase 05 - settings-ui

**Goal:** In Operations settings, present 4 zone-enable toggles, each revealing its 3 direction action-pickers (12 pickers total), reusing the existing `ScreenshotGestureActionPickerManager` / canonical settings picker. Pickers for a disabled zone stay hidden.

**Depends on:** 01, 02.
**Source set:** `src/main` (settings UI + layout + strings).

---

## Steps

### [ ] 05.1 - Read the current binding

- Read `OperationsGesturesManager.kt` + `OperationsSettingsFragment.kt` + the gesture rows in the operations settings layout (`res/layout/` and `res/layout-land/` counterpart). Note how the current 3 pickers bind to `screenshotGestureActionDown/Right/Up` and how `screenshotGestureStripVisible` / overlay-enable toggles render (reuse that toggle style).
- **Verification:** current 3-picker binding + toggle pattern identified.

### [ ] 05.2 - Layout: 4 zone groups

- Replace the 3-picker block with 4 zone groups. Each group = one enable toggle (SwitchMaterial or the canonical settings toggle row) + 3 action-picker rows (down/right/up), the 3 rows wrapped in a container toggled `View.GONE`/`VISIBLE` by the zone switch. Follow the landscape multi-column convention where applicable; edit the `layout-land/` counterpart in the same change (Rule 11).
- **Verification:** portrait + landscape layouts both carry 4 toggles + 12 picker rows; no portrait-only edit.

### [ ] 05.3 - Manager wiring

- In `OperationsGesturesManager`, bind each zone toggle to its `screenshotGestureZone*Enabled` setting and each picker to its `screenshotGesture<Zone><Direction>` slot via the resolver helper. Toggling a zone shows/hides its 3 rows and persists the flag. Reuse `ScreenshotGestureActionPickerManager` for all 12 (single shared catalog).
- **Verification:** toggling a zone persists the flag and reveals/hides its 3 pickers; each picker writes its slot; D-pad focus order set across the new rows (Rule 16).

### [ ] 05.4 - Strings EN/RU/UK

- Add zone labels + toggle titles via `scripts/utils/set-android-string.ps1 -Action add` (EN/RU/UK parity): e.g. `settings_gesture_zone_left_top`, `_left_bottom`, `_right_top`, `_right_bottom`, and a section subtitle if the current single-strip label needs generalizing. RU/UK use `..`, hyphen, Ё per policy.
- **Verification:** `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "settings_gesture_zone"` exit 0.

### [ ] 05.5 - Compile

- **Verification:** `.\a.ps1 fc` (code + resources, standard) passes.

---

## Phase Done Criteria

- [ ] 4 zone toggles + 12 pickers render (portrait + landscape), disabled-zone pickers hidden.
- [ ] Each toggle/picker persists its setting via the resolver.
- [ ] Strings localized EN/RU/UK (audit exit 0).
- [ ] `.\a.ps1 fc` passes.
