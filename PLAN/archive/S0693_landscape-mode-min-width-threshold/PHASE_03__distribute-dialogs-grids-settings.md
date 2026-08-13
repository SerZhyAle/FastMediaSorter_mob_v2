# Phase 03 - Distribute to Dialogs, Grids, Settings

**Strategic spec:** [`../S0693_landscape-mode-min-width-threshold.md`](../S0693_landscape-mode-min-width-threshold.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** -
**Steps done:** 8 / 8
**Started:** -
**Completed:** -

---

## Objective

Route the remaining non-player decision sites (grid dialogs, calculator/scheduled dialogs, settings tabs and the general-settings sync container) through the shared node, including the lone raw-pixel `widthPixels > heightPixels` check.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (`isWideLayout` extensions exist).
- [ ] Working tree clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/applaunchpanel/AppLaunchPanelDialogFragment.kt` | Modified | edit only |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/applaunchpanel/edit/EditAppLaunchPanelActivity.kt` | Modified | edit only |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/keybinding/KeybindingRemapActivity.kt` | Modified | edit only |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceFormManager.kt` | Modified | edit only |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/calculator/helpers/CalculatorInputManager.kt` | Modified | edit only |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/ScheduledOperationDialog.kt` | Modified | edit only |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsActivity.kt` | Modified | edit only |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/GeneralSettingsFragment.kt` | Modified | edit only |

> No `res/layout/*.xml` edited in this phase - landscape-parity rule not triggered.

---

## Steps

### Step 03.1 - AppLaunchPanelDialogFragment: grid span

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/applaunchpanel/AppLaunchPanelDialogFragment.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Replace the `resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE` check (~line 55) that chooses grid span 5 vs 3 with `resources.configuration.isWideLayout()`. Import `com.sza.fastmediasorter.core.orientation.isWideLayout`.

**Verification:**

- `Grep` - `isWideLayout()` present in `AppLaunchPanelDialogFragment.kt`.
- `Grep` - `orientation == Configuration.ORIENTATION_LANDSCAPE` no longer present in `AppLaunchPanelDialogFragment.kt`.

**Status:** `[x]` done

---

### Step 03.2 - EditAppLaunchPanelActivity: grid span

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/applaunchpanel/edit/EditAppLaunchPanelActivity.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Replace the `resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE` check (~line 44, grid span 5 vs 3) with `resources.configuration.isWideLayout()`. Import the extension.

**Verification:**

- `Grep` - `isWideLayout()` present in `EditAppLaunchPanelActivity.kt`.
- `Grep` - `orientation == Configuration.ORIENTATION_LANDSCAPE` no longer present in `EditAppLaunchPanelActivity.kt`.

**Status:** `[x]` done

---

### Step 03.3 - KeybindingRemapActivity: grid span

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/keybinding/KeybindingRemapActivity.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Replace `val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE` (~line 92, grid span 2 vs 1) with `val isWide = resources.configuration.isWideLayout()` and update its single usage. Import the extension.

**Verification:**

- `Grep` - `isWideLayout()` present in `KeybindingRemapActivity.kt`.
- `Grep` - `orientation == Configuration.ORIENTATION_LANDSCAPE` no longer present in `KeybindingRemapActivity.kt`.

**Status:** `[x]` done

---

### Step 03.4 - AddResourceFormManager: resource-type grid columns

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceFormManager.kt`
**Depends on:** Step 03.3

**Prompt for developer:**

> Replace the `activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE` check (~line 60, `2 else 1` columns) with `activity.resources.configuration.isWideLayout()`. Import the extension.

**Verification:**

- `Grep` - `isWideLayout()` present in `AddResourceFormManager.kt`.
- `Grep` - `orientation == Configuration.ORIENTATION_LANDSCAPE` no longer present in `AddResourceFormManager.kt`.

**Status:** `[x]` done

---

### Step 03.5 - CalculatorInputManager: function-dialog width/height

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/calculator/helpers/CalculatorInputManager.kt`
**Depends on:** Step 03.4

**Prompt for developer:**

> Replace both `context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE` checks (~lines 301 and 315, function-picker dialog max width and height) with `context.resources.configuration.isWideLayout()`. Import the extension. The `*_LAND_DP` / `*_PORT_DP` constants stay; only the selector switches to the node.

**Verification:**

- `Grep` - `isWideLayout()` matches at least twice in `CalculatorInputManager.kt`.
- `Grep` - `orientation == Configuration.ORIENTATION_LANDSCAPE` no longer present in `CalculatorInputManager.kt`.

**Status:** `[x]` done

---

### Step 03.6 - ScheduledOperationDialog: migrate raw-pixel aspect check

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/ScheduledOperationDialog.kt`
**Depends on:** Step 03.5

**Prompt for developer:**

> Replace the raw `dm.widthPixels > dm.heightPixels` check (~line 51, dialog height 90% vs WRAP_CONTENT) with `resources.configuration.isWideLayout()`. This is the only raw-pixel aspect check in the app; it must read the same node as everything else. Import the extension. If `dm` (DisplayMetrics) is then unused, remove its now-dead retrieval.

**Verification:**

- `Grep` - `isWideLayout()` present in `ScheduledOperationDialog.kt`.
- `Grep` - `widthPixels > dm.heightPixels` no longer present in `ScheduledOperationDialog.kt`.
- `Grep` - `widthPixels > heightPixels` no longer present in `ScheduledOperationDialog.kt`.

**Status:** `[x]` done

---

### Step 03.7 - SettingsActivity: tab widths + compact toolbar

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsActivity.kt`
**Depends on:** Step 03.6

**Prompt for developer:**

> Replace the three `resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE` checks (~lines 341, 392, 403 - `forceEqualTabWidths`, insets callback, `applyCompactToolbar`) with `resources.configuration.isWideLayout()`. Import the extension.

**Verification:**

- `Grep` - `isWideLayout()` matches at least 3 times in `SettingsActivity.kt`.
- `Grep` - `orientation == Configuration.ORIENTATION_LANDSCAPE` no longer present in `SettingsActivity.kt`.

**Status:** `[x]` done

---

### Step 03.8 - GeneralSettingsFragment: sync-container orientation

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/GeneralSettingsFragment.kt`
**Depends on:** Step 03.7

**Prompt for developer:**

> Replace `val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE` (~line 334, sync container HORIZONTAL vs VERTICAL) with `val isWide = resources.configuration.isWideLayout()` and update its usages. Import the extension.

**Verification:**

- `Grep` - `isWideLayout()` present in `GeneralSettingsFragment.kt`.
- `Grep` - `orientation == Configuration.ORIENTATION_LANDSCAPE` no longer present in `GeneralSettingsFragment.kt`.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] App-wide check: `Grep -n "orientation == Configuration.ORIENTATION_LANDSCAPE"` returns hits only in player-family files (out of scope) - no non-player site remains.
- [ ] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

- All non-player runtime decision sites now read the node. Remaining aspect-ratio dependence is the `res/layout-land/` + `res/values-land/` resource layer, aligned in Phase 04.

---

## Rollback Plan

Revert the phase commit(s). Each site reverts to its prior independent check; no data or user-facing surface migrated.
