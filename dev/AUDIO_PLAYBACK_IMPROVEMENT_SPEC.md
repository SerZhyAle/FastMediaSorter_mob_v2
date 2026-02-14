# Audio Playback Improvement Specification

## 1. Purpose

To transform the audio playback experience from a "video player without video" into a full-featured music player. The core objective is to enable **background playback** (screen off / app minimized), provide rich lock screen controls, and enhance the visual experience during playback.

## 2. Scope

### In Scope
1.  **Background Playback Engine:**
    *   Implement Android `MediaSessionService` (Media3).
    *   Migrate `ExoPlayer` instance to run within the Service.
    *   Handle "Foreground Service" requirements for background execution.
2.  **System Integration:**
    *   **Media Notification:** System media controls in notification shade and lock screen.
    *   **Media Session:** Integration with external controls (Bluetooth headphones, car audio).
    *   **Audio Focus:** Proper handling of interruptions (calls, other apps).
3.  **Visual Experience:**
    *   **Dynamic Visualizer:** Real-time audio visualization (Waveform/Spectrum) when cover art is missing or as an overlay.
    *   **Cover Art:** Improved extraction and display (using `MediaMetadataRetriever` properly).
4.  **Playback Features:**
    *   Gapless playback support.
    *   Sleep Timer.
    *   Playback Speed (already exists, but needs UI unification).

### Out of Scope
1.  Streaming service integration (Spotify/SoundCloud APIs).
2.  Complex equalizer (DSP) beyond basic system EQ.

## 3. Current State Analysis

### 3.1 Architecture
*   **No Service:** Playback is tied to `PlayerActivity` lifecycle. Closing the screen or app stops the music.
*   **Video-Centric:** `VideoPlayerManager` handles audio. This is inefficient and tightly couples audio to the UI.
*   **Visuals:** `AudioBackgroundPhotosManager` shows random photos, but there's no rhythmic visualization or proper "Music Player" UI.

## 4. Objectives

1.  **True Backgrounding:** Music keeps playing when I turn off the screen.
2.  **System Control:** I can pause/skip from my headphones or car display.
3.  **Visual Delight:** The screen looks alive with audio visualizations.

## 5. Technical Proposal

### 5.1 Architecture: `MediaPlaybackService`

Introduce a `Service` that survives Activity destruction.
*   **Library:** Use `androidx.media3:media3-session`.
*   **Lifecycle:**
    *   Service starts on first play.
    *   Binds to `PlayerActivity` (or any Activity) for UI updates.
    *   Promotes to "Foreground Service" with a Notification to run in background.

### 5.2 Decoupling logic

1.  **Refactor:** Extract `ExoPlayer` management from `VideoPlayerManager` into a `MediaControllerWrapper`.
2.  **Communication:** The UI (`PlayerViewModel`) communicates with the `Service` via `MediaController` API, not direct method calls.

### 5.3 Audio Visualizer

Implement a lightweight visualizer using `android.media.audiofx.Visualizer` (requires RECORD_AUDIO permission, which might be a blocker) OR a compute-shader based visualizer using ExoPlayer's `AudioProcessor`.
*   *Recommendation:* Use ExoPlayer's `AudioProcessor` to extract FFT data. This is cleaner and doesn't require "Microphone" permission on newer Android versions.

## 6. Implementation Stages

### Phase 1: Service Core
*   Create `FastMediaPlaybackService`.
*   Move `ExoPlayer` initialization to the service.
*   Implement `MediaSession` callback handling.

### Phase 2: Background Support
*   Implement `MediaNotificationManager`.
*   Handle `startForeground` requirements.
*   Update `AndroidManifest.xml` with `FOREGROUND_SERVICE` permissions.

### Phase 3: UI Connection
*   Refactor `PlayerActivity` to connect to `MediaSession`.
*   Update `PlayerViewModel` to observe playback state via `MediaController`.

### Phase 4: Visuals & Polish
*   Integrate FFT extraction.
*   Create `AudioVisualizerView`.
*   Add Sleep Timer logic.

## 7. Migration Risks

*   **Risk:** `Media3` migration complexity.
    *   *Mitigation:* The app already uses Media3 packages (`androidx.media3`), so dependency is there. The challenge is architectural (Service vs Activity).
*   **Risk:** Permission for Visualizer.
    *   *Mitigation:* Use ExoPlayer-based visualization to avoid sensitive permissions.

## 8. Acceptance Criteria

1.  Audio continues playing when the app is swiped away (minimized) or screen is locked.
2.  Notification drawer shows Play/Pause/Skip/Seek controls.
3.  Bluetooth headset buttons control playback.
4.  Audio Visualizer reacts to music in real-time.
