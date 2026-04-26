# Phase 04 — Player Wiring

**Strategic spec:** [`../spec_camera-capture-command.md`](../spec_camera-capture-command.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Todo
**Depends on:** Phase 02, Phase 03
**Blocks:** Phase 05, 06

---

## Objective

Wire `CameraCaptureManager` into `PlayerActivity` — add the callback method to
`CommandPanelCallback`, instantiate the manager in the activity (or delegate to a helper),
and handle the `onCameraCaptureClicked()` dispatch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `ui/player/CommandPanelController.kt` | Modified | ≤ 1000 |
| `ui/player/PlayerActivity.kt` | Modified | ≤ 1000 |

---

## Steps

### Step 04.1 — Add onCameraCaptureClicked to CommandPanelCallback

**File:** `ui/player/CommandPanelController.kt`
**Depends on:** Phase 02

In the `CommandPanelCallback` interface, after `onPrintClicked()`:
```kotlin
fun onCameraCaptureClicked()
```

**Verification:** `Grep "onCameraCaptureClicked" ui/player/CommandPanelController.kt` → ≥ 1 hit.

---

### Step 04.2 — Instantiate CameraCaptureManager in PlayerActivity

**File:** `ui/player/PlayerActivity.kt`
**Depends on:** Phase 03

1. In `PlayerActivity`, add a field:
   ```kotlin
   private lateinit var cameraCaptureManager: CameraCaptureManager
   ```
2. In `onCreate()`, before `setContentView` (so the launcher registers before Activity starts):
   ```kotlin
   cameraCaptureManager = CameraCaptureManager(
       activity = this,
       settingsRepository = settingsRepository, // already injected
       coroutineScope = lifecycleScope,
       onSaveComplete = { _, _ -> /* refresh file list if needed */ }
   )
   ```
3. Note: `CameraCaptureManager.cameraLauncher` is registered in the constructor via
   `activity.registerForActivityResult(...)`, which requires the call before `super.onCreate()`
   completes. Verify that `FragmentActivity.registerForActivityResult` is allowed in
   `onCreate()` (it is, as long as the activity has not yet called `super.onStart()`).

**Verification:** `Grep "cameraCaptureManager" ui/player/PlayerActivity.kt` → ≥ 2 hits.

---

### Step 04.3 — Implement CommandPanelCallback.onCameraCaptureClicked in PlayerActivity

**File:** `ui/player/PlayerActivity.kt`
**Depends on:** Steps 04.1, 04.2

PlayerActivity implements `CommandPanelCallback` (directly or via a callback class). Locate
the implementation and add:
```kotlin
override fun onCameraCaptureClicked() {
    val resource = viewModel.state.value.resource ?: return
    cameraCaptureManager.launch(resource)
}
```

If `PlayerActivity` delegates callbacks to `PlayerUiStateCoordinatorCallbackImpl` or a similar
class, add the method there instead, following the same pattern as `onPrintClicked`.

**Verification:** `Grep "onCameraCaptureClicked" ui/player/PlayerActivity.kt` → ≥ 1 hit
OR `Grep "onCameraCaptureClicked" ui/player/callbacks/` → ≥ 1 hit.

---

### Step 04.4 — Handle overflow menu item click for CAMERA_CAPTURE

**File:** `ui/player/CommandPanelController.kt`
**Depends on:** Phase 02 (menu item added), Step 04.1

In `showOverflowMenu()` / the `PopupMenu.OnMenuItemClickListener` (or wherever
`R.id.menu_print` is handled), add the case:
```kotlin
R.id.menu_camera_capture -> callback.onCameraCaptureClicked()
```

**Verification:** `Grep "menu_camera_capture" ui/player/CommandPanelController.kt` → ≥ 1 hit.

---

## Phase Done Criteria

- [ ] `Grep "onCameraCaptureClicked" ui/player/CommandPanelController.kt` → ≥ 1 hit
- [ ] `Grep "cameraCaptureManager" ui/player/PlayerActivity.kt` → ≥ 2 hits
- [ ] `Grep "onCameraCaptureClicked" ui/player/PlayerActivity.kt` → ≥ 1 hit (or callbacks/ subfolder)
- [ ] `Grep "menu_camera_capture" ui/player/CommandPanelController.kt` → ≥ 1 hit
- [ ] BUILD-REQUIRED: standard-debug must pass before Phase 05 begins
