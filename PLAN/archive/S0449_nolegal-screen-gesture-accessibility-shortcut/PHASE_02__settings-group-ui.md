# Phase 02 - Settings group UI

**Strategic spec:** [`../S0449_nolegal-screen-gesture-accessibility-shortcut.md`](../S0449_nolegal-screen-gesture-accessibility-shortcut.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 3 / 3
**Started:** 2026-06-15
**Completed:** 2026-06-16

---

## Objective

Add a hint + accessibility-shortcut button to the Left-edge screen gestures group in both orientations, and wire the button to open accessibility settings directly via the existing gesture controller, with the educational dialog kept as fallback.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/fragment_settings_destinations.xml` | Modified | +~12 |
| `app_v2/src/main/res/layout-land/fragment_settings_destinations.xml` | Modified | +~12 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/OperationsSettingsFragment.kt` | Modified | ≤ +25 |

> Landscape parity: both `layout/` and `layout-land/` variants of `fragment_settings_destinations.xml` exist and BOTH carry the `containerScreenGestures` group - edit both (steps 02.1 and 02.2).
> Flavor placement: no new flavor files. The group `groupScreenGestures` is already hidden when no `ScreenGestureOverlayController` is injected (non-noLegal), so the new views inherit noLegal-only visibility. No `BuildConfig.IS_*` guard.

---

## Steps

### Step 02.1 - Add hint + button to portrait layout

**Files:** `app_v2/src/main/res/layout/fragment_settings_destinations.xml`
**Depends on:** Phase 01

**Prompt for developer:**

> Inside `LinearLayout` `@+id/containerScreenGestures`, after the `rowScreenshotDestination` block and before the container's closing tag, add a `TextView` showing `@string/setting_screenshot_accessibility_shortcut_hint` (use `@dimen/toggler_desc_text_size`, `@color/text_color_secondary`) and below it a `com.google.android.material.button.MaterialButton` with `android:id="@+id/btnOpenAccessibilitySettings"`, `android:text="@string/setting_screenshot_accessibility_shortcut_button"`, `android:focusable="true"`, `android:clickable="true"`. Use theme attributes / `@color` / `@dimen` only - no hardcoded `="#hex"` colors. Give the button a non-default `contentDescription` only if its visible text is insufficient; otherwise the text label suffices.

**Verification:**

- `Grep` - `@+id/btnOpenAccessibilitySettings` matches exactly once in `app_v2/src/main/res/layout/fragment_settings_destinations.xml`.
- `Grep` - `setting_screenshot_accessibility_shortcut_hint` present in that file.
- `Grep` - no `="#` hex literal added in the edited block.

**Status:** `[x]` done

**Step Log:**

- 2026-06-15 - Verification 3/3 PASS. Added hint TextView + `@+id/btnOpenAccessibilitySettings` (SettingsButton.Outlined) to portrait `containerScreenGestures`. No hex literals.

---

### Step 02.2 - Add hint + button to landscape layout

**Files:** `app_v2/src/main/res/layout-land/fragment_settings_destinations.xml`
**Depends on:** Step 02.1

**Prompt for developer:**

> Mirror step 02.1 inside the `@+id/containerScreenGestures` group of the landscape variant: same `TextView` hint and same `@+id/btnOpenAccessibilitySettings` MaterialButton with identical ids, strings, and attributes. Theme attributes / `@color` / `@dimen` only.

**Verification:**

- `Grep` - `@+id/btnOpenAccessibilitySettings` matches exactly once in `app_v2/src/main/res/layout-land/fragment_settings_destinations.xml`.
- `Grep` - `setting_screenshot_accessibility_shortcut_hint` present in that file.

**Status:** `[x]` done

**Step Log:**

- 2026-06-15 - Verification 2/2 PASS. Mirrored hint + button into landscape `containerScreenGestures` (identical ids/strings/attrs).

---

### Step 02.3 - Wire the button click to open accessibility settings directly

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/OperationsSettingsFragment.kt`
**Depends on:** Step 02.1, Step 02.2

**Prompt for developer:**

> In `setupSystemAppsSection()` (the block guarded by the non-null `controller`, alongside the existing `rowScreenshotDestination` click handler), add a click listener for `binding.btnOpenAccessibilitySettings` that launches `controller.permissionSettingsIntent(requireContext())` through the existing `overlayPermissionLauncher` - opening the accessibility list directly, bypassing `showGesturePermissionDialog`. Guard the launch with a try/catch on `ActivityNotFoundException`; on failure fall back to `showGesturePermissionDialog(controller)` (strategic §6.1 / ADR-1). Do not alter the toggle handlers or the first-enable dialog path. Timber only for any log; no `android.util.Log`.

**Verification:**

- `Grep` - `binding.btnOpenAccessibilitySettings` matches in `OperationsSettingsFragment.kt`.
- `Grep` - `permissionSettingsIntent` referenced inside the new click handler.
- `Grep -n "Log\.d\("` returns zero hits in `OperationsSettingsFragment.kt`.
- `/build` (noLegal debug) compiles.

**Status:** `[x]` done

**Step Log:**

- 2026-06-16 - Verification 4/4 PASS. Added click handler launching `controller.permissionSettingsIntent` via `overlayPermissionLauncher`, `ActivityNotFoundException` fallback to educational dialog, new import. Build (`a.ps1 fc`) SUCCESSFUL. Zero `Log.d`.

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Both portrait and landscape layouts contain `@+id/btnOpenAccessibilitySettings`.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

The hint + button are present in both orientations and the button opens accessibility settings directly (noLegal-only via existing group gating). Final phase records the user-facing entry and regenerates catalog/dev-log.

---

## Rollback Plan

Revert phase commit(s) - layout additions and one click handler; no data migration or persisted state changed.
