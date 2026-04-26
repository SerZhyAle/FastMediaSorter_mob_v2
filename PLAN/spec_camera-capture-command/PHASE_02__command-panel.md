# Phase 02 — Command Panel

**Strategic spec:** [`../spec_camera-capture-command.md`](../spec_camera-capture-command.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Todo
**Depends on:** Phase 01
**Blocks:** Phase 03, 04

---

## Objective

Register `CAMERA_CAPTURE` as a low-priority `PlayerCommand`, add the button view to the
player layout, expose it via `PlayerBindingSafeViews`, wire the click listener in
`CommandPanelController`, and implement visibility logic.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `ui/player/helpers/CommandPanelLayoutPlanner.kt` | Modified | ≤ 300 |
| `app_v2/src/main/res/layout/activity_player_unified.xml` | Modified | — |
| `ui/player/helpers/PlayerBindingSafeViews.kt` | Modified | ≤ 100 |
| `ui/player/CommandPanelController.kt` | Modified | ≤ 1000 |
| `app_v2/src/main/res/menu/player_overflow_menu.xml` | Modified | — |

---

## Steps

### Step 02.1 — Add PlayerCommand.CAMERA_CAPTURE to CommandPanelLayoutPlanner

**File:** `ui/player/helpers/CommandPanelLayoutPlanner.kt`
**Depends on:** Phase 01

1. After `PRINT(600, ...)` in the enum, add:
   ```kotlin
   // Low-priority camera-capture command — bar-capable, spills to overflow when bar is full.
   CAMERA_CAPTURE(610, R.id.menu_camera_capture, true, R.string.cmd_camera_capture,
       R.drawable.ic_camera_capture);
   ```
2. In `buildActiveCommands()`, after `if (isPdf || isText || isImage) add(PlayerCommand.PRINT)`:
   ```kotlin
   // Camera-capture: visible for resources that support IMAGE or VIDEO,
   // and for virtual ALL_VIDEO / ALL_IMAGES paths only.
   if (state.enableCameraCapture && isCameraCaptureEligible(state)) {
       add(PlayerCommand.CAMERA_CAPTURE)
   }
   ```
3. Add private helper:
   ```kotlin
   private fun isCameraCaptureEligible(state: PlayerViewModel.PlayerState): Boolean {
       val resource = state.resource ?: return false
       val supportsImageOrVideo = resource.allFiles ||
           resource.supportedMediaTypes.any {
               it == com.sza.fastmediasorter.domain.model.MediaType.IMAGE ||
               it == com.sza.fastmediasorter.domain.model.MediaType.VIDEO ||
               it == com.sza.fastmediasorter.domain.model.MediaType.GIF
           }
       if (!supportsImageOrVideo) return false
       val path = resource.path
       return !com.sza.fastmediasorter.util.VirtualPathUtils.isVirtualPath(path) ||
           path == com.sza.fastmediasorter.data.local.LocalMediaScanner.VIRTUAL_PATH_ALL_VIDEO ||
           path == com.sza.fastmediasorter.data.local.LocalMediaScanner.VIRTUAL_PATH_ALL_IMAGES
   }
   ```

**Verification:**
- `Grep "CAMERA_CAPTURE" ui/player/helpers/CommandPanelLayoutPlanner.kt` → ≥ 3 hits (enum, buildActiveCommands, helper call)

---

### Step 02.2 — Add camera-capture icon drawable

**File:** `app_v2/src/main/res/drawable/ic_camera_capture.xml` (new)
**Depends on:** —

Create a 24dp vector drawable using Android's built-in camera icon path. Use the Material
Icons "photo_camera" path (viewportWidth="24", viewportHeight="24"):
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="?attr/colorControlNormal">
  <path
      android:fillColor="@android:color/white"
      android:pathData="M12,12m-3.2,0a3.2,3.2 0,1 1,6.4 0a3.2,3.2 0,1 1,-6.4 0"/>
  <path
      android:fillColor="@android:color/white"
      android:pathData="M9,2L7.17,4H4C2.9,4 2,4.9 2,6v12c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V6c0,-1.1 -0.9,-2 -2,-2h-3.17L15,2H9zm3,15c-2.76,0 -5,-2.24 -5,-5s2.24,-5 5,-5 5,2.24 5,5 -2.24,5 -5,5z"/>
</vector>
```

**Verification:** `Glob "app_v2/src/main/res/drawable/ic_camera_capture.xml"` → 1 hit.

---

### Step 02.3 — Add overflow menu item

**File:** `app_v2/src/main/res/menu/player_overflow_menu.xml`
**Depends on:** Step 02.2

Add an item after the `menu_print` item:
```xml
<item
    android:id="@+id/menu_camera_capture"
    android:icon="@drawable/ic_camera_capture"
    android:title="@string/cmd_camera_capture"
    android:showAsAction="never" />
```

**Verification:** `Grep "menu_camera_capture" app_v2/src/main/res/menu/player_overflow_menu.xml` → 1 hit.

---

### Step 02.4 — Add button view to player layout

**File:** `app_v2/src/main/res/layout/activity_player_unified.xml`
**Depends on:** Step 02.2

Locate the `btnPrintCmd` `ImageButton` in the layout. Add an identical `ImageButton` immediately
after it, using `btnCameraCaptureCmd` as the ID and `@drawable/ic_camera_capture` as src.
Match the existing button's layout attributes exactly (same style, width, height, padding,
visibility="gone" initial state).

**Verification:** `Grep "btnCameraCaptureCmd" app_v2/src/main/res/layout/activity_player_unified.xml` → 1 hit.

---

### Step 02.5 — Expose button in PlayerBindingSafeViews

**File:** `ui/player/helpers/PlayerBindingSafeViews.kt`
**Depends on:** Step 02.4

After `val btnPrintCmd`:
```kotlin
val btnCameraCaptureCmd: ImageButton get() = required(R.id.btnCameraCaptureCmd)
```

**Verification:** `Grep "btnCameraCaptureCmd" ui/player/helpers/PlayerBindingSafeViews.kt` → 1 hit.

---

### Step 02.6 — Wire click listener in CommandPanelController

**File:** `ui/player/CommandPanelController.kt`
**Depends on:** Steps 02.4, 02.5, Phase 01 (callback interface updated in Phase 04)

> **Note:** The `onCameraCaptureClicked()` callback method is added to `CommandPanelCallback`
> in Phase 04 Step 04.1. For now, add a TODO comment; Phase 04 will replace it.

In `setupCommandPanelControls()`, after the `btnPrintCmd` listener:
```kotlin
safeViews.btnCameraCaptureCmd.setOnClickListener {
    callback.onCameraCaptureClicked()
}
```

In `updateCommandAvailability()`, after the block that sets `btnPrintCmd` visibility, add:
```kotlin
// Camera-capture: low-priority command; visibility is computed by CommandPanelLayoutPlanner.
// Adaptive portrait layout handles it via getOverflowableButtons() — just set initial gone.
// Landscape explicit visibility:
if (showInLandscape) {
    safeViews.btnCameraCaptureCmd.isVisible =
        state.enableCameraCapture && isCameraCaptureEligibleLandscape(state)
}
```

Add private helper mirroring the planner check:
```kotlin
private fun isCameraCaptureEligibleLandscape(state: PlayerViewModel.PlayerState): Boolean {
    val resource = state.resource ?: return false
    val supportsImageOrVideo = resource.allFiles ||
        resource.supportedMediaTypes.any {
            it == MediaType.IMAGE || it == MediaType.VIDEO || it == MediaType.GIF
        }
    if (!supportsImageOrVideo) return false
    val path = resource.path
    return !VirtualPathUtils.isVirtualPath(path) ||
        path == LocalMediaScanner.VIRTUAL_PATH_ALL_VIDEO ||
        path == LocalMediaScanner.VIRTUAL_PATH_ALL_IMAGES
}
```

Also add `safeViews.btnCameraCaptureCmd` to the `getOverflowableButtons()` list so portrait
adaptive layout can hide it.

**Verification:**
- `Grep "btnCameraCaptureCmd" ui/player/CommandPanelController.kt` → ≥ 3 hits
- `Grep "isCameraCaptureEligibleLandscape" ui/player/CommandPanelController.kt` → ≥ 1 hit

---

## Phase Done Criteria

- [ ] `Grep "CAMERA_CAPTURE" ui/player/helpers/CommandPanelLayoutPlanner.kt` → ≥ 3 hits
- [ ] `Glob "app_v2/src/main/res/drawable/ic_camera_capture.xml"` → 1 hit
- [ ] `Grep "menu_camera_capture" app_v2/src/main/res/menu/player_overflow_menu.xml` → 1 hit
- [ ] `Grep "btnCameraCaptureCmd" app_v2/src/main/res/layout/activity_player_unified.xml` → 1 hit
- [ ] `Grep "btnCameraCaptureCmd" ui/player/helpers/PlayerBindingSafeViews.kt` → 1 hit
- [ ] `Grep "btnCameraCaptureCmd" ui/player/CommandPanelController.kt` → ≥ 3 hits
