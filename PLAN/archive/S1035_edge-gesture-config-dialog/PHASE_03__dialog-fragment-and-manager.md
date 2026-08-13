# Phase 03 - Dialog fragment and config manager

**Strategic spec:** [`../S1035_edge-gesture-config-dialog.md`](../S1035_edge-gesture-config-dialog.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 02
**Blocks:** Phase 04
**Steps done:** 0 / 4
**Started:** -
**Completed:** -

---

## Objective

Introduce `EdgeGestureConfigDialogFragment` (full-screen, mirrors `DefaultAppsDialogFragment`) hosting `EdgeGestureConfigManager` (zone + general-group binding extracted from `OperationsGesturesManager`), with the schema view wired to state and taps, and four tabs switching the visible zone block. No settings-tab change yet.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done (dialog layout + binding exist).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/gesture/EdgeGestureConfigManager.kt` | New | ≤ 400 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/gesture/EdgeGestureConfigDialogFragment.kt` | New | ≤ 250 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/OperationsGesturesManager.kt` | Modified | ≤ 369 |

> The dialog + manager live in `src/main` and consume the injected `Set<ScreenGestureOverlayController>` (existing capability seam) - no `BuildConfig` flavor guard. The dialog is only launched from the gated button (Phase 04), so an empty controller set never reaches it.

---

## Steps

### Step 03.1 - Extract zone + general binding into EdgeGestureConfigManager

**Files:** `ui/settings/gesture/EdgeGestureConfigManager.kt`, `ui/settings/helpers/OperationsGesturesManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `class EdgeGestureConfigManager` that binds the dialog's zone blocks and general group against `DialogEdgeGestureConfigBinding`, delegating to the existing `ScreenshotGestureActionPickerManager` and `SettingsViewModel` exactly as `OperationsGesturesManager` does today. Move the zone wiring (`setupZones`/`bindZone`/`bindPicker`/`renderZone`, the `ZoneViews` bundle) and the general-group wiring (clipboard toggle, screenshot destination picker, edit-app-panel button, accessibility rows + `showGesturePermissionDialog`, `onOverlayPermissionResult`) out of `OperationsGesturesManager` into this manager, re-targeting them at the dialog binding ids. Keep the constructor shape close to `OperationsGesturesManager` (binding, viewModel, fragment, controllers, picker manager, overlayPermissionLauncher, isUpdatingFromSettings, pickDestination, refreshLabel). `OperationsGesturesManager` retains ONLY the master-toggle concern (Phase 04 trims it).

**Verification:**

- `Glob` - `ui/settings/gesture/EdgeGestureConfigManager.kt` exists.
- `Grep` - `class EdgeGestureConfigManager` matches exactly once.
- `Grep` - `fun setup(` and `fun render(` present in the new manager.
- `Grep` - `bindZone` present in `EdgeGestureConfigManager.kt` and NOT in `OperationsGesturesManager.kt`.

**Status:** `[ ]` not done

---

### Step 03.2 - Wire schema state + taps in the manager

**Files:** `ui/settings/gesture/EdgeGestureConfigManager.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> In `EdgeGestureConfigManager.render(settings)`, build a `SchemaState` from the current `AppSettings` (per-zone enabled + per-direction assigned = action is not the "none" default) and call `binding.edgeGestureSchema.setState(state)` so grey/red reflect live state. In `setup()`, set the schema's direction-tap listener to open the same picker used by the rows (`gestureActionPickerManager.showPicker(...)` for that zone+direction, writing back via `viewModel.updateSettings`), and the zone-tap listener to switch the active tab to that zone. Reading assigned-state uses `settings.screenshotGestureAction(zone, direction)` and the zone helpers already on `AppSettings`.

**Verification:**

- `Grep` - `edgeGestureSchema.setState(` present in `EdgeGestureConfigManager.kt`.
- `Grep` - `setOnDirectionTapListener` referenced in `EdgeGestureConfigManager.kt`.
- `Grep` - `screenshotGestureAction(` referenced in `EdgeGestureConfigManager.kt`.

**Status:** `[ ]` not done

---

### Step 03.3 - Create EdgeGestureConfigDialogFragment

**Files:** `ui/settings/gesture/EdgeGestureConfigDialogFragment.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Create `@AndroidEntryPoint class EdgeGestureConfigDialogFragment : DialogFragment()` mirroring `DefaultAppsDialogFragment`: full-screen (`FEATURE_NO_TITLE`, full-screen style if the precedent uses one), inflate `DialogEdgeGestureConfigBinding`, `btnClose` dismisses, `viewModel: SettingsViewModel by activityViewModels()`. Register the overlay-permission `ActivityResultLauncher` in the fragment and pass it to the manager. Instantiate `EdgeGestureConfigManager`, call `setup()`, and collect `viewModel.settings` via `collectOnLifecycle` to call `manager.render(settings)`. Add `companion object { const val TAG = "EdgeGestureConfigDialog" }`. Null out `_binding` in `onDestroyView`.

**Verification:**

- `Glob` - `ui/settings/gesture/EdgeGestureConfigDialogFragment.kt` exists.
- `Grep` - `class EdgeGestureConfigDialogFragment : DialogFragment` matches exactly once.
- `Grep` - `const val TAG` present.
- `Grep` - `collectOnLifecycle` present (no bare `lifecycleScope.launch { collect }`).
- `Grep` - `Log\.d\(` returns zero hits in the file.

**Status:** `[ ]` not done

---

### Step 03.4 - Tab setup: four zones switch the visible block

**Files:** `ui/settings/gesture/EdgeGestureConfigDialogFragment.kt`, `ui/settings/gesture/EdgeGestureConfigManager.kt`
**Depends on:** Step 03.3

**Prompt for developer:**

> Populate `tabsEdgeGestureZones` with four tabs (titles `edge_gesture_tab_left_top` / `_left_bottom` / `_right_top` / `_right_bottom`). On tab select, show the matching zone block in `containerZoneTabContent` and hide the others (default: first tab). Expose a `fun selectZone(zone: ScreenshotGestureZone)` path so the schema's zone-tap listener (Step 03.2) can drive the tab selection. Default the first tab expanded/visible on open.

**Verification:**

- `Grep` - `tabsEdgeGestureZones` referenced in the fragment or manager.
- `Grep` - `addOnTabSelectedListener` or `TabLayoutMediator` present.
- `Grep` - `fun selectZone(` present.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (standard debug).
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] `dev/CATALOG/app_v2.jsonl` regeneration deferred to Phase 06 (batch).

---

## Handoff Notes to Next Phase

The dialog is fully functional but not yet launchable from settings. Phase 04 adds the launcher button + gate in the settings tab and removes the moved rows from the settings layout, trimming `OperationsGesturesManager` to the master toggle.

---

## Rollback Plan

Revert phase commit(s). The dialog is unreferenced by the settings tab until Phase 04, so reverting leaves the existing inline gesture block intact.
