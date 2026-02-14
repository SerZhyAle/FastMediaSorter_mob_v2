# Video Playback Improvement Specification

## 1. Purpose

To upgrade the video playback experience in FastMediaSorter to current industry standards (similar to MX Player, YouTube, or modern gallery apps). The improvement focuses on adding intuitive gesture controls, unifying the UI with the rest of the app, and ensuring rock-solid playback stability for network streams.

## 2. Scope

### In Scope
1.  **Immersive Gestures:**
    *   Vertical Drag (Left): Brightness control.
    *   Vertical Drag (Right): Volume control.
    *   Horizontal Drag: Precise seeking (scrubbing) with preview (if possible).
    *   Double Tap (Left/Right): Seek -10s / +10s.
2.  **UI Unification:** Custom transport controls (Play/Pause, Seekbar, Time) that match the app's design language, replacing or skinning the default `StyledPlayerView`.
3.  **Advanced Playback:**
    *   Playback speed control (already in Manager, needs prominent UI).
    *   Audio Track / Subtitle quick switcher.
    *   Background Playback (Picture-in-Picture) support.
4.  **Performance:** Hardware acceleration optimization and "Seamless" looping.

### Out of Scope
1.  Video Editor features (Trimming, Transcoding).
2.  FFmpeg software decoders (unless native ExoPlayer fails, which is handled by fallback).

## 3. Current State Analysis

### 3.1 Architecture
*   **Manager:** `VideoPlayerManager` wraps ExoPlayer efficiently, handling network buffers and errors well.
*   **Gestures:** `PlayerGestureHelper` is too generic (Swipe for Next/Prev). It lacks "Video Mode" awareness.
*   **UI:** Relies heavily on standard `PlayerView` or basic visibility toggles.
*   **Missing:** No visual feedback for Volume/Brightness changes, no easy specific seeking without the tiny seekbar.

## 4. Objectives

1.  **One-Handed Control:** Users should be able to control all core playback functions (Vol/Bright/Seek) without looking for buttons.
2.  **Visual Consistency:** The "Play/Pause" and "Seekbar" should look identical to the Audio player and Animated Image player.
3.  **Seamless Experience:** Transitioning from an Image to a Video should feel native, not like launching a separate "Video Mode".

## 5. Technical Proposal

### 5.1 Gesture Engine: `VideoGestureController`

Create a specialized gesture detector that overlays the `PlayerView` when active.
*   **Interception:** Consumes touch events before they hit `PlayerView`.
*   **Logic:**
    *   `onScroll` (Vertical Left): `WindowManager.LayoutParams.screenBrightness`.
    *   `onScroll` (Vertical Right): `AudioManager.STREAM_MUSIC`.
    *   `onScroll` (Horizontal): `ExoPlayer.seekTo(current + delta)`.
    *   `onDoubleTap`: Check X coordinate (<35% Left = Rewind, >65% Right = Fast Forward). Center = Play/Pause.

### 5.2 UI Overlay improvement

1.  **Custom Control View:** Replace/Customize `app:controller_layout_id` for `PlayerView`.
2.  **Gesture Indicators:**
    *   Center overlay showing "☀ 50%" or "🔊 80%" during drag.
    *   "⏪ 10s" / "⏩ 10s" animation on double tap.
3.  **Subtitle/Audio styling:** Use `VideoPlayerManager.applySubtitleStyle` to ensure user font preferences are respected (already implemented, needs verification in UI).

### 5.3 Picture-in-Picture (PiP)

Implement Android's native PiP mode.
*   **Trigger:** Home button (Android 12+ auto) or specific "PiP" button.
*   **Lifecycle:** Ensure `VideoPlayerManager` doesn't release the player in `onPause` if `isInPictureInPictureMode` is true.

## 6. Implementation Stages

### Phase 1: Gesture Core
*   Implement `VideoTouchDelegate` class.
*   Add logic for Volume/Brightness modification.
*   Add visual overlay for feedback.

### Phase 2: Seeking & Playback
*   Implement Double-Tap to seek.
*   Implement Horizontal Drag to scrub.
*   Update `VideoPlayerManager` to expose precise seek methods if needed.

### Phase 3: PiP & Background
*   Update `PlayerActivity` manifest (`android:supportsPictureInPicture="true"`).
*   Handle `onUserLeaveHint` to trigger PiP.
*   Adjust `PlayerActivity` lifecycle to keep ExoPlayer alive in PiP.

## 7. Migration Risks

*   **Risk:** Gesture conflict with `PlayerView`'s internal handling (e.g., standard toggling of controls).
    *   *Mitigation:* Disable default `use_controller` click handling and implement custom toggle logic.
*   **Risk:** PiP lifecycle complexity.
    *   *Mitigation:* Strict testing on different Android versions (PiP behavior varies significantly between Android 8 and 12).

## 8. Acceptance Criteria

1.  Swiping up/down on right side changes volume with visual indicator.
2.  Swiping up/down on left side changes brightness with visual indicator.
3.  Double tapping edges skips 10s.
4.  App enters PiP mode when Home is pressed during video playback (if enabled).
