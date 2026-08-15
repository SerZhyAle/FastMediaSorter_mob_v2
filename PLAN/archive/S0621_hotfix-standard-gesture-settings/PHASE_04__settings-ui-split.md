# Phase 04 - Settings UI split

**Strategic spec:** [`../S0621_hotfix-standard-gesture-settings.md`](../S0621_hotfix-standard-gesture-settings.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** -
**Steps done:** 2 / 2
**Started:** 2026-06-22
**Completed:** 2026-06-22

**Step Log:** 04.1 PASS - OperationsGesturesManager.setup gates tvAccessibilityShortcutHint + btnOpenAccessibilitySettings on controller.isFallbackCaptureAvailable() (hidden on standard, shown on noLegal API30+). 04.2 PASS - SettingsSearchCapabilityGate adds the btnOpenAccessibilitySettings key branch keyed on controller capability; 2 new tests + helper added; `:app_v2:testStandardDebugUnitTest --tests *SettingsSearchCapabilityGateTest*` BUILD SUCCESSFUL.

---

## Objective

Now that the gesture group is visible on standard, hide the accessibility-specific rows there. The accessibility-shortcut hint + "Open accessibility settings" button are meaningful only where the silent a11y path exists (noLegal API 30+); gate them on `controller.isFallbackCaptureAvailable()`. Mirror the gate in the settings-search index so search does not surface the button on standard.

---

## Prerequisites

- [ ] Phase 02 ✅ Done (standard controller present; `isFallbackCaptureAvailable() == false` on standard, `== true` on noLegal API 30+).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/OperationsGesturesManager.kt` | Modified | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/search/SettingsSearchCapabilityGate.kt` | Modified | ≤ 100 |
| `app_v2/src/test/java/com/sza/fastmediasorter/ui/settings/search/SettingsSearchCapabilityGateTest.kt` | Modified | ≤ 240 |

> Landscape parity: the two affected views (`tvAccessibilityShortcutHint`, `btnOpenAccessibilitySettings`) exist in BOTH `res/layout/fragment_settings_destinations.xml` and `res/layout-land/fragment_settings_destinations.xml`. Visibility is set in code (the manager), so no layout edit is required - the single code gate covers both orientations. No `?attr`/color or layout change here.

---

## Steps

### Step 04.1 - Gate the accessibility-shortcut rows in the gestures manager

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/OperationsGesturesManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> In `OperationsGesturesManager.setup()`, after resolving the non-null `controller`, set the visibility of the two accessibility-specific views from the controller capability: `val supportsA11ySilent = controller.isFallbackCaptureAvailable()`; `binding.tvAccessibilityShortcutHint.isVisible = supportsA11ySilent`; `binding.btnOpenAccessibilitySettings.isVisible = supportsA11ySilent`. Add a short comment: on standard the controller exposes only the MediaProjection consent path (`isFallbackCaptureAvailable() == false`), so the accessibility-shortcut rows are hidden; noLegal (API 30+) keeps them. Keep the existing click wiring (it is harmless when the button is gone). Use `androidx.core.view.isVisible` (already imported).

**Verification:**

- `Grep` - `binding.tvAccessibilityShortcutHint.isVisible` present in the file.
- `Grep` - `binding.btnOpenAccessibilitySettings.isVisible` present.
- `Grep` - `isFallbackCaptureAvailable` referenced in `setup(`.

**Status:** `[ ]` not done

---

### Step 04.2 - Mirror the gate in the settings-search index + test

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/search/SettingsSearchCapabilityGate.kt`, `app_v2/src/test/java/com/sza/fastmediasorter/ui/settings/search/SettingsSearchCapabilityGateTest.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> The `groupScreenGestures` container gate now passes on standard (controller set non-empty), so the search index would surface `btnOpenAccessibilitySettings` there even though it is hidden. Add a per-row branch in `SettingsSearchCapabilityGate.isKeyCapabilityAvailable`: `"btnOpenAccessibilitySettings" -> screenGestureControllers.any { it.isFallbackCaptureAvailable() }`. This mirrors the runtime gate from Step 04.1 (true only where the a11y silent path exists). Add a unit test asserting the row is suppressed when the only controller reports `isFallbackCaptureAvailable() == false` and kept when it reports `true` (stub the controller mock's `isFallbackCaptureAvailable()`).

**Verification:**

- `Grep` - `"btnOpenAccessibilitySettings"` present in `SettingsSearchCapabilityGate.kt`.
- `Grep` - `isFallbackCaptureAvailable` present in `SettingsSearchCapabilityGate.kt`.
- `.\gradlew.bat :app_v2:testStandardDebugUnitTest --tests "*SettingsSearchCapabilityGateTest*"` exits 0.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Accessibility-shortcut hint + button are gated on `isFallbackCaptureAvailable()` in the manager.
- [ ] Search gate suppresses `btnOpenAccessibilitySettings` when no controller offers the a11y silent path.
- [ ] `SettingsSearchCapabilityGateTest` green.
- [ ] `assembleStandardDebug` green.
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Dev log entry added for every modified file.

---

## Handoff Notes to Next Phase

On standard the group shows: overlay toggle, three gesture-action selectors, screenshot destination, copy-to-clipboard, screenshot-test button - and NOT the accessibility-shortcut rows. Phase 05 records the capability in docs + catalog.

---

## Rollback Plan

Revert the manager + gate + test edits. The accessibility-shortcut rows reappear on standard (cosmetic only). No data migration.
