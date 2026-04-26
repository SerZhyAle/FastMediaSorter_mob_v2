# Phase 02 — Browse Button & Layout

**Strategic spec:** [`../spec_camera-capture-command.md`](../spec_camera-capture-command.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Completed:** 2026-04-25
**Depends on:** Phase 01
**Blocks:** Phase 03, 04

---

## Objective

Add a `btnCameraCapture` MaterialButton to the Browse top command bar (`layoutControls`),
wire the click listener in `BrowseButtonSetupHelper`, and implement visibility logic in
`BrowseStateUiUpdater` (or `BrowseObserverManager`).

---

## Files Touched

| File | New/Mod | Budget |
| ---- | :-----: | -----: |
| `app_v2/src/main/res/layout/activity_browse.xml` | Mod | — |
| `app_v2/src/main/res/drawable/ic_camera_capture.xml` | New | — |
| `ui/browse/managers/BrowseButtonSetupHelper.kt` | Mod | ≤ 350 |
| `ui/browse/managers/BrowseStateUiUpdater.kt` | Mod | ≤ 700 |
| `ui/browse/managers/BrowseObserverManager.kt` | Mod | ≤ 500 |

---

## Steps

### Step 02.1 — Create camera icon drawable

**File:** `app_v2/src/main/res/drawable/ic_camera_capture.xml` (new)

Material Icons "photo_camera" 24dp vector:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="?attr/colorControlNormal">
  <path
      android:fillColor="@android:color/white"
      android:pathData="M9,2L7.17,4H4C2.9,4 2,4.9 2,6v12c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V6c0,-1.1 -0.9,-2 -2,-2h-3.17L15,2H9zm3,15c-2.76,0 -5,-2.24 -5,-5s2.24,-5 5,-5 5,2.24 5,5 -2.24,5 -5,5z"/>
  <path
      android:fillColor="@android:color/white"
      android:pathData="M12,12m-3.2,0a3.2,3.2 0,1 1,6.4 0a3.2,3.2 0,1 1,-6.4 0"/>
</vector>
```

**Verification:** `Glob "app_v2/src/main/res/drawable/ic_camera_capture.xml"` → 1 hit.

---

### Step 02.2 — Add button to activity_browse.xml

**File:** `app_v2/src/main/res/layout/activity_browse.xml`
**Depends on:** Step 02.1

Locate `btnPlay` (last button in `layoutControls`). Insert an identical `MaterialButton`
immediately **before** `btnPlay` (so camera capture sits after the selection/ops cluster but
before Play, maintaining low priority):

```xml
<!-- Camera Capture (low-priority; hidden for audio/doc resources) -->
<com.google.android.material.button.MaterialButton
    android:id="@+id/btnCameraCapture"
    style="?attr/materialIconButtonStyle"
    android:layout_width="wrap_content"
    android:layout_height="@dimen/control_button_size"
    android:contentDescription="@string/cmd_camera_capture"
    android:insetLeft="0dp" android:insetTop="0dp"
    android:insetRight="0dp" android:insetBottom="0dp"
    android:maxWidth="@dimen/activity_browse_btnSort_maxWidth"
    android:maxHeight="@dimen/activity_browse_btnDeselectAll_maxHeight"
    android:minWidth="@dimen/activity_browse_btnBack_minWidth"
    android:minHeight="@dimen/activity_browse_btnDeselectAll_minHeight"
    android:padding="0dp"
    android:paddingStart="@dimen/activity_browse_btnDeselectAll_paddingStart"
    android:paddingTop="0dp"
    android:paddingEnd="@dimen/activity_player_unified_moveToPanelIndicator_paddingEnd"
    android:paddingBottom="0dp"
    android:textSize="@dimen/activity_browse_btnSort_textSize"
    android:visibility="gone"
    app:icon="@drawable/ic_camera_capture"
    app:iconGravity="textStart"
    app:iconPadding="@dimen/spacing_none"
    app:iconTint="?attr/colorControlNormal" />
```

**Verification:** `Grep "btnCameraCapture" app_v2/src/main/res/layout/activity_browse.xml` → 1 hit.

---

### Step 02.3 — Add callback to BrowseButtonSetupHelper

**File:** `ui/browse/managers/BrowseButtonSetupHelper.kt`
**Depends on:** Step 02.2

1. In `ButtonCallbacks` interface, after `onPlayClicked()`:

   ```kotlin
   fun onCameraCaptureClicked()
   ```

2. In `setupAllButtons()`, after the `btnPlay` listener block:

   ```kotlin
   binding.btnCameraCapture.setOnClickListener {
       callbacks.onCameraCaptureClicked()
   }
   ```

**Verification:** `Grep "onCameraCaptureClicked" ui/browse/managers/BrowseButtonSetupHelper.kt` → 2 hits.

---

### Step 02.4 — Visibility logic in BrowseStateUiUpdater

**Status:** `[x] done`

**File:** `ui/browse/managers/BrowseStateUiUpdater.kt`
**Depends on:** Steps 01.1, 02.2

In the method that updates button visibility based on `BrowseState` (wherever `btnPlay`,
`btnToggleView`, etc. visibility is set), add:

```kotlin
binding.btnCameraCapture.isVisible = isCameraCaptureVisible(state, settings)
```

Add private helper at the bottom of the class:

```kotlin
private fun isCameraCaptureVisible(
    state: BrowseState,
    settings: AppSettings
): Boolean {
    if (settings.disableCameraCapture) return false
    val resource = state.resource ?: return false
    val supportsImageOrVideo = resource.allFiles ||
        resource.supportedMediaTypes.any {
            it == MediaType.IMAGE || it == MediaType.VIDEO || it == MediaType.GIF
        }
    if (!supportsImageOrVideo) return false
    val path = resource.path
    return !VirtualPathUtils.isVirtualPath(path) ||
        path == LocalMediaScanner.VIRTUAL_PATH_ALL_VIDEO ||
        path == LocalMediaScanner.VIRTUAL_PATH_ALL_IMAGES ||
        path == LocalMediaScanner.VIRTUAL_PATH_CAMERA_PHOTOS
}
```

If `BrowseStateUiUpdater` does not receive `AppSettings` directly, read it from the
`BrowseObserverManager` (which already subscribes to the settings flow) and pass the relevant
flag in. Do not inline settings collection inside `BrowseStateUiUpdater` — keep the flow in
the observer layer.

**Verification:** `Grep "isCameraCaptureVisible" ui/browse/managers/BrowseStateUiUpdater.kt` → ≥ 2 hits.

**Step Log:**

- 2026-04-25 — added `companion object { isCameraCaptureVisible() }` + `updateCameraCaptureVisibility()` instance method; BrowseStateUiUpdater doesn't receive AppSettings directly so companion is called from BrowseObserverManager. Verification 2/2 hits PASS.

---

### Step 02.5 — Subscribe to disableCameraCapture in BrowseObserverManager

**Status:** `[x] done`

**File:** `ui/browse/managers/BrowseObserverManager.kt`
**Depends on:** Steps 01.1, 02.4

In the settings observer (where `enableFavorites` is already observed), re-trigger button
visibility update when `disableCameraCapture` changes. If the observer already calls
`updateButtonVisibility(state, settings)` after every settings emission, no extra code is
needed — just verify it does.

**Verification:** `Grep "disableCameraCapture\|btnCameraCapture\|isCameraCaptureVisible" ui/browse/managers/BrowseObserverManager.kt` → ≥ 1 hit (or confirm existing observer already covers it).

**Step Log:**

- 2026-04-25 — added `observeCameraCaptureVisibility()` using `combine(settingsRepository.getSettings(), viewModel.state)` → calls `BrowseStateUiUpdater.isCameraCaptureVisible()`; called from `startAll()`. Verification 2 hits PASS.

---

## Phase Done Criteria

- [x] `Glob "app_v2/src/main/res/drawable/ic_camera_capture.xml"` → 1 hit
- [x] `Grep "btnCameraCapture" app_v2/src/main/res/layout/activity_browse.xml` → 1 hit
- [x] `Grep "onCameraCaptureClicked" ui/browse/managers/BrowseButtonSetupHelper.kt` → 2 hits
- [x] `Grep "isCameraCaptureVisible" ui/browse/managers/BrowseStateUiUpdater.kt` → ≥ 2 hits
