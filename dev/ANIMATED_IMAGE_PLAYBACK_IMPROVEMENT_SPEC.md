# Animated Image Playback Improvement Specification

## 1. Purpose

To bring "Video-Class" playback controls to animated images (GIF, WEBP, APNG) in FastMediaSorter. The goal is to allow users to pause, scrub, and adjust the playback speed of animations *in real-time* without modifying the underlying file, while maintaining the existing "Save Permanent Speed Change" functionality.

## 2. Scope

### In Scope
1.  **Playback Control:** Implement Play/Pause toggle for animated drawables.
2.  **Speed Control:** Real-time playback speed adjustment (0.25x - 4.0x) without rewriting the file.
3.  **Seek/Scrub:** (Optional/Advanced) Frame-by-frame seeking if technically feasible with current decoder.
4.  **Gesture Support:** Full Zoom/Pan support for playing animations (parity with static images).
5.  **Format Support:** GIF, Animated WEBP, Animated PNG (if supported by decoder).

### Out of Scope
1.  Video playback (handled by ExoPlayer).
2.  Editing specific frames (pixel editing).
3.  Changing the actual file encoding engine (switching away from Glide/Android default unless necessary).

## 3. Current Pain Points

1.  **No Playback Control:** Animations play on loop; user cannot pause to see a specific frame.
2.  **Destructive Speed Change:** To see an animation faster/slower, the user must use the "Edit Speed" dialog which rewrites the file. This is slow and storage-intensive for simple viewing.
3.  **Inconsistent Gestures:** Animations sometimes load in `ImageView` (no zoom) depending on settings/size, unlike static visuals which are moving towards consistent `PhotoView` usage.

## 4. Objectives

1.  **Interactive Playback:** Tap to pause/play (separate from "toggle UI").
2.  **Non-Destructive Speed:** "Playback Speed" slider that affects rendering only.
3.  **Performance:** Efficient memory usage even for large GIFs.
4.  **Legacy Compatibility:** Keep "Save Speed to File" as an explicit "Export" action, reusing the existing `ChangeGifSpeedUseCase`.

## 5. Technical Proposal

### 5.1 Playback Engine

Leverage `androidx.vectordrawable.graphics.drawable.Animatable2Compat` or Glide's `GifDrawable`.

*   **Play/Pause:** 
    *   Glide's `GifDrawable` implements `Animatable`. calls `start()` and `stop()`.
    *   To implement Pause (freeze on frame) vs Stop (reset), we need to check if `GifDrawable` supports pausing. *Note: Standard Glide GifDrawable stops and resets. We may need a custom `FrameLoader` or a wrapper that manages the frame delay.*
    *   **Alternative:** Migrate to `Android 9+ ImageDecoder` + `AnimatedImageDrawable` which supports `stop()` (freeze) better, or use a library like `android-gif-drawable` for advanced control if Glide is too limiting.
    *   *Decision:* Research standard Glide first. If insufficient, wrap `GifDrawable` to intercept frame delays.

### 5.2 Real-time Speed Control

Instead of rewriting the bytes:
1.  **Method A (Glide Hook):** Hook into Glide's `GifFrameLoader` to modify the delay between frames dynamically.
2.  **Method B (Custom Drawable):** Wrap the standard GIF drawable and intercept the `scheduleSelf` calls to accelerate/decelerate the timing.
    *   *Algorithm:* `nextFrameDelay = originalFrameDelay / speedMultiplier`.

### 5.3 UI Changes

1.  **Overlay Controls:** When an animated image is detected, show a subtle "GIF" badge.
2.  **Playback Overlay:** Tapping reveals a specific "Animation Control" bar (distinct from Video controls, but similar aesthetic):
    *   Play/Pause toggle.
    *   Speed selector (0.5x, 1x, 2x, etc.).
    *   "Export Speed" button (links to existing `ChangeGifSpeedUseCase`).

### 5.4 Unified Zoom

Ensure `ImageLoadingManager` always uses `PhotoView` for animated content, regardless of "Full Size" setting, OR ensure `ImageView` implementation handles scaling correctly.
*   *Requirement:* `PhotoView` must support `Animatable` drawables (it usually does).

## 6. Implementation Plan

### Phase 1: Controller Abstraction
*   Create `AnimatedImageController` class to manage the `Drawable`.
*   Abstract the "Start/Stop" logic away from `ImageLoadingManager`.

### Phase 2: Play/Pause Implementation
*   Implement `togglePlayback()` in the controller.
*   Update UI to clear overlay on Pause so user can inspect the frame.

### Phase 3: Variable Speed Rendering
*   Implement the "Delay Interceptor" logic.
*   Connect UI slider/buttons to `controller.setPlaybackSpeed(float)`.

### Phase 4: Export Integration
*   Wire the "Save" button to the existing logic, pre-filling the dialog with the currently selected playback speed.

## 7. Migration Risks

*   **Risk:** `GifDrawable` implementation details are private/internal in Glide.
    *   *Mitigation:* Use reflection carefully OR copy the necessary caching classes to create a `SpeedAwareGifDrawable`.
*   **Risk:** Performance drop on older devices with custom frame scheduling.
    *   *Mitigation:* Disable variable speed on low-end devices if frame drops occur.

## 8. Acceptance Criteria

1.  User can pause a playing GIF and zoom in on the frozen frame.
2.  User can change speed to 2x or 0.5x instantly without file IO.
3.  User can permanent save the current speed to the file (Export).
4.  Memory usage does not spike significantly compared to current implementation.
