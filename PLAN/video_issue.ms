### Video UI Mode Issue - Root Cause Analysis

#### Symptoms
- Video files opened from `BrowseActivity` (likely via "Play All" or from a Video Library resource) appear in "Fullscreen Mode" (command buttons hidden).
- However, they respond to 3-zone touch gestures (Command Panel layout) instead of 9-zone (Fullscreen layout).

#### Root Cause
The issue is a logic conflict between `PlayerDialogAndUiStateManager` and `TouchZoneGestureManager` regarding how `isSlideShowActive` affects the UI:

1. **State Initialization**: When a video is opened from a `VIDEO_LIBRARY` resource, `PlayerViewModel` sets `isSlideShowActive = true` to enable auto-advance-on-finish behavior.
2. **UI Visibility Logic**: In `PlayerDialogAndUiStateManager.updatePanelVisibility()`, the code hides the top command panel if `isSlideshowActive` is true, UNLESS the file is `AUDIO`. 
   - Since `VIDEO` is not explicitly excluded, the command panel is hidden.
   - RESULT: UI looks like "Fullscreen Mode".
3. **Gesture Layout Logic**: `TouchZoneGestureManager.createGestureDetector()` (used for video/audio) determines the zone layout based ONLY on `state.showCommandPanel`.
   - It ignores `state.isSlideShowActive` (unlike `createImageTouchGestureDetector`).
   - Since `showCommandPanel` remains `true` (the default), it selects the 3-zone layout (`REG_375`).
   - RESULT: Gestures use "Command Panel Mode".

#### Proposed Fix
Update `PlayerDialogAndUiStateManager.updatePanelVisibility` to exclude `VIDEO` from the automatic slideshow hiding logic:

```kotlin
// Change this:
val shouldHideForSlideshow = isSlideshowActive && !isAudioFile

// To this:
val isVideoFile = state.currentFile?.type == MediaType.VIDEO
val shouldHideForSlideshow = isSlideshowActive && !isAudioFile && !isVideoFile
```

This ensures that for both Audio and Video, the "slideshow" state (auto-advance) does not force-hide the command panel, respecting the user's preference and maintaining consistency between UI appearance and gesture zones.