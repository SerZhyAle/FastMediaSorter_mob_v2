# Phase 03 - Gesture Toggle Wiring (manager + controller + host)

**Strategic spec:** [`../S0662_welcome-default-gestures.md`](../S0662_welcome-default-gestures.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02 (needs the `rowGestures` binding field)
**Blocks:** Phase 04
**Steps done:** 4 / 4
**Started:** 2026-06-24
**Completed:** 2026-06-24

---

## Objective

Make the `rowGestures` toggle drive `gestureOverlayEnabled` through the same permission flow as the Settings gesture card, gated by capability presence, reusing the controller abstraction (no flavor `BuildConfig` checks in `src/main`).

---

## Prerequisites

- [ ] Phase 02 is ✅ Done (`binding.rowGestures` exists).
- [ ] Reference implementation read: `ui/settings/helpers/OperationsGesturesManager.kt` (toggle + permission dialog + `onOverlayPermissionResult`).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/helpers/WelcomeGesturesManager.kt` | New | ≤ 150 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/helpers/WelcomeFunctionalityController.kt` | Modified | ≤ 470 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/WelcomeActivity.kt` | Modified | ≤ 820 |
| `temp/WelcomeActivity_<timestamp>.kt` | New (backup) | n/a |

> `WelcomeActivity.kt` is > 500 LOC - back it up before editing (Rule 5). The permission/dialog logic lives in the new `WelcomeGesturesManager` (not the controller) so `WelcomeFunctionalityController` stays under 500 LOC. All files are `src/main` (shared contract); the capability gate is the controller-set presence, so no flavor source-set split is needed.

---

## Steps

### Step 03.1 - Back up WelcomeActivity.kt

**Files:** `temp/WelcomeActivity_<timestamp>.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Copy `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/WelcomeActivity.kt` to `temp/` with a timestamped filename before editing (Rule 5, file > 500 LOC).

**Verification:**

- `Glob` - `temp/WelcomeActivity_*.kt` exists.

**Status:** `[x] done`

**Step Log:**

- 2026-06-24 - Backed up to temp/WelcomeActivity_20260624_170604.kt before editing (781 LOC > 500).

---

### Step 03.2 - Create WelcomeGesturesManager

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/helpers/WelcomeGesturesManager.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Create a manager mirroring the gesture-toggle subset of `OperationsGesturesManager`, adapted to the Welcome page. Constructor: the `rowGestures` `SettingsToggleRow`, `Set<ScreenGestureOverlayController>`, an `ActivityResultLauncher<Intent>` overlay-permission launcher, the host `FragmentActivity`, a `currentSettings: () -> AppSettings` snapshot accessor, and a `persist: ((AppSettings) -> AppSettings) -> Unit` callback. `setup()`: when the controller set is empty, set the row `GONE` and return; otherwise set the row checked from `gestureOverlayEnabled` and install a checked-change listener - on ON, if `controller.isOverlayPermissionGranted` is false show the permission dialog (`MaterialAlertDialogBuilder` with `controller.permissionRationaleResId()`, positive routes `overlayPermissionLauncher.launch(controller.permissionSettingsIntent(...))`, neutral "old method" only when `controller.isFallbackCaptureAvailable()`, `onDismiss` reverts the row when still not granted), else `controller.setEnabled(true)` and `persist { it.copy(gestureOverlayEnabled = true) }`; on OFF, `controller.setEnabled(false)` and `persist { it.copy(gestureOverlayEnabled = false) }`. Add `onOverlayPermissionResult()`: if granted, `setEnabled(true)` + persist true, else `row.setCheckedSilently(false)`. Reuse existing strings (`screenshot_gesture_permission_dialog_title`, `screenshot_gesture_open_settings`, `screenshot_gesture_use_old_method`, `cancel`). Timber only; no broad catch.

**Verification:**

- `Glob` - `WelcomeGesturesManager.kt` exists.
- `Grep` - `class WelcomeGesturesManager` matches exactly once.
- `Grep` - `isOverlayPermissionGranted`, `permissionSettingsIntent`, and `onOverlayPermissionResult` all present.
- `Grep` - `Log\.d\(` returns zero hits in this file.

**Status:** `[x] done`

**Step Log:**

- 2026-06-24 - Verification 5/5 PASS. Files: ui/welcome/helpers/WelcomeGesturesManager.kt (New, ~100 LOC). Mirrors OperationsGesturesManager gesture-overlay subset; capability gate hides the row when controller set empty.

---

### Step 03.3 - Inject controllers and delegate rowGestures in WelcomeFunctionalityController

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/helpers/WelcomeFunctionalityController.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Add a constructor parameter `screenGestureControllers: Set<@JvmSuppressWildcards ScreenGestureOverlayController>`. Add `private fun bindGesturesRow(binding, owner, settings)` and call it from `bindRows`; it constructs/holds a `WelcomeGesturesManager` for `binding.rowGestures` wired to the existing `persist(..)` and a current-settings snapshot, then calls `setup()`. Add `fun attachGesturePermissionLauncher(launcher: ActivityResultLauncher<Intent>)` to receive the host launcher (re-attached on each bind) and `fun onGesturePermissionResult()` delegating to the held manager. The empty-set case is handled inside the manager (`row` GONE).

**Verification:**

- `Grep` - `screenGestureControllers` present as a constructor parameter.
- `Grep` - `bindGesturesRow`, `attachGesturePermissionLauncher`, and `onGesturePermissionResult` all present.
- `Grep` - `binding.rowGestures` present.

**Status:** `[x] done`

**Step Log:**

- 2026-06-24 - Verification 3/3 PASS. Files: ui/welcome/helpers/WelcomeFunctionalityController.kt (+~45 LOC, ~443 total). Injected controller set; delegates rowGestures to WelcomeGesturesManager; launcher attach + result pass-through.

---

### Step 03.4 - Register the overlay-permission launcher and wire it in WelcomeActivity

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/WelcomeActivity.kt`
**Depends on:** Step 03.3

**Prompt for developer:**

> Register `gestureOverlayPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { functionalityController.onGesturePermissionResult() }` as an Activity field (registration must happen before STARTED). Call `functionalityController.attachGesturePermissionLauncher(gestureOverlayPermissionLauncher)` where the functionality page is wired (near the existing `onBindFunctionality = { b -> functionalityController.bind(b, this) }`). Keep the Activity a thin host - no gesture business logic beyond launcher registration and delegation.

**Verification:**

- `Grep` - `gestureOverlayPermissionLauncher` and `registerForActivityResult` both present.
- `Grep` - `attachGesturePermissionLauncher` invoked in `WelcomeActivity`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-24 - Verification 2/2 PASS. Files: ui/welcome/WelcomeActivity.kt (+~10 LOC). Registered overlay-permission launcher (construction-time) + attached to controller before functionality page bind.

---

## Phase Done Criteria

- [x] Every `Step 03.*` is `[x] done`.
- [x] Project compiles - `.\a.ps1 fc` (BUILD SUCCESSFUL, kapt/Hilt + dataBinding validated).
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] `Grep` for `Log\.d\(` returns zero hits in the touched `.kt` files.
- [~] Dev log entry - batched in Phase 04 finalization.
- [~] `dev/CATALOG/app_v2.jsonl` regenerated - batched in Phase 04 finalization.

---

## Handoff Notes to Next Phase

The Welcome toggle now reflects and drives `gestureOverlayEnabled` through the same permission path as Settings, and is hidden on flavors without the capability. Combined with Phase 01 seeding, enabling on a fresh install activates the three preconfigured gestures. Phase 04 records the capability and regenerates the catalog.

---

## Rollback Plan

Revert the phase commit(s) and restore `WelcomeActivity.kt` from the `temp/` backup if needed. No data migration or persisted-schema change - only a UI entry point and host wiring are removed.
