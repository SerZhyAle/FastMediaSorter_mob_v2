# Phase 04 - Settings tab entry point

**Strategic spec:** [`../S1035_edge-gesture-config-dialog.md`](../S1035_edge-gesture-config-dialog.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 03
**Blocks:** Phase 05
**Steps done:** 0 / 3
**Started:** -
**Completed:** -

---

## Objective

Reduce the Operations-tab gesture card to just the master toggle + a "Configure gestures" launcher button (disabled while the master toggle is off, hidden when the capability is absent), remove the relocated rows from the settings layout, and trim `OperationsGesturesManager` to the master-toggle + launcher concern.

---

## Prerequisites

- [ ] Phase 03 is ✅ Done (dialog launchable in code).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/fragment_settings_destinations.xml` | Modified | ≤ 1205 |
| `app_v2/src/main/res/layout-land/fragment_settings_destinations.xml` | Modified | ≤ 1427 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/OperationsGesturesManager.kt` | Modified | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/OperationsSettingsFragment.kt` | Modified | ≤ 700 |

> Landscape counterpart edited in the same phase (Step 04.2). The gesture capability gate stays the injected `Set<ScreenGestureOverlayController>` - no `BuildConfig` guard added to `src/main`.

---

## Steps

### Step 04.1 - Slim the gesture card in the portrait settings layout

**Files:** `res/layout/fragment_settings_destinations.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> In the `groupScreenGestures` card keep `rowGestureOverlayEnabled` (master) and add a launcher button `@+id/btnOpenEdgeGestureConfig` (label `@string/setting_edge_gesture_config_button`, existing settings-button style). Remove the four zone blocks (`rowZone*`, `containerZone*`, `rowGesture*`) and the general rows now hosted by the dialog (`rowCopyScreenshotToClipboard`, `btnSelectScreenshotDestination`+`tvScreenshotDestination`, `btnEditAppPanel`, `tvAccessibilityShortcutHint`, `btnOpenAccessibilitySettings`). Keep `btnTakeScreenshotNow` (owned by `OperationsCaptureManager`, not part of this move) unless it belongs with capture - leave it where `OperationsCaptureManager` expects it.

**Verification:**

- `Grep` - `@+id/btnOpenEdgeGestureConfig` present in the portrait file.
- `Grep` - `@+id/rowGestureLeftTopUp` returns zero hits in the portrait file (moved out).
- `Grep` - `@+id/rowZoneRightBottomEnabled` returns zero hits in the portrait file.
- `Grep` - `@+id/rowGestureOverlayEnabled` still present (master retained).

**Status:** `[ ]` not done

---

### Step 04.2 - Mirror the slim card in the landscape settings layout

**Files:** `res/layout-land/fragment_settings_destinations.xml`
**Depends on:** Step 04.1

**Prompt for developer:**

> Apply the identical structural change to the landscape variant: keep `rowGestureOverlayEnabled`, add `btnOpenEdgeGestureConfig`, remove the same relocated zone/general ids.

**Verification:**

- `Grep` - `@+id/btnOpenEdgeGestureConfig` present in the land file.
- `Grep` - `@+id/rowGestureRightBottomDown` returns zero hits in the land file.
- `Grep` - `@+id/rowGestureOverlayEnabled` still present in the land file.

**Status:** `[ ]` not done

---

### Step 04.3 - Wire launcher + gate; trim OperationsGesturesManager

**Files:** `ui/settings/helpers/OperationsGesturesManager.kt`, `ui/settings/fragments/OperationsSettingsFragment.kt`
**Depends on:** Step 04.2

**Prompt for developer:**

> In `OperationsGesturesManager.setup()`/`render()` keep only: the capability gate (hide `groupScreenGestures` when `screenGestureControllers` empty), the master `rowGestureOverlayEnabled` toggle + permission flow (`showGesturePermissionDialog`, `onOverlayPermissionResult`), and gating the new button - `binding.btnOpenEdgeGestureConfig.isEnabled = settings.gestureOverlayEnabled` (disabled while gestures off, per owner decision §6.4) and visible under the same capability gate. In `OperationsSettingsFragment`, wire `binding.btnOpenEdgeGestureConfig.setOnClickListener { EdgeGestureConfigDialogFragment().show(childFragmentManager, EdgeGestureConfigDialogFragment.TAG) }` (mirror the `btnOpenDefaultAppsDialog` pattern). Delete now-dead references to the moved rows from the fragment/manager. Keep the `overlayPermissionLauncher` registration; route its result to whichever host now owns the master (the tab manager for the master toggle, the dialog manager for in-dialog permission entry).

**Verification:**

- `Grep` - `btnOpenEdgeGestureConfig` referenced in `OperationsSettingsFragment.kt`.
- `Grep` - `EdgeGestureConfigDialogFragment` referenced in `OperationsSettingsFragment.kt`.
- `Grep` - `btnOpenEdgeGestureConfig.isEnabled` present in `OperationsGesturesManager.kt`.
- `Grep` - `bindZone` returns zero hits in `OperationsGesturesManager.kt` (fully moved).
- Project compiles - `/build` standard debug (no unresolved binding refs).

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (standard debug).
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

The settings tab now shows only master toggle + launcher; all detail lives in the dialog. Phase 05 re-points settings-search at the entry point and regenerates the settings docs manifest.

---

## Rollback Plan

Revert phase commit(s). Restores the inline gesture block; the dialog (Phase 03) becomes orphaned but harmless.
