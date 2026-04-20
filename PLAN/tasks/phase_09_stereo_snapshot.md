# Phase 9 — Stereo Snapshot (SBS PNG)

**Status:** Implemented · **Depends on:** Phase 1 · **Parent:** [../spec_vr-master.md](../spec_vr-master.md)

## Goal

Capture a single frame from the active VR render loop as an SBS PNG (left half = left eye, right half = right eye), save to Pictures with a timestamped filename.

## Current State

- `VrStereoSnapshotManager` requests a one-shot stereo capture from the active OpenXR render loop.
- The native bridge reads both eye swapchain images, exposes them as ARGB buffers, and Kotlin composes a single SBS PNG.
- Snapshots are written to `Pictures/FastMediaSorter_VR` with a timestamped `*_SBS.png` filename and an immediate open action.

## Work

1. Create `VrStereoSnapshotManager` in `vr/capture/`.
2. JNI hook: after `xrEndFrame`, read back both eye swapchain images via `glReadPixels` into two bitmaps.
3. Compose into one wide bitmap (2×width × height) on Dispatchers.Default.
4. Encode PNG → `Environment.DIRECTORY_PICTURES/FastMediaSorter_VR/<filename>_<timestamp>_SBS.png`.
5. Scoped storage compliance (MediaStore API for API 30+).
6. Show toast + trigger the existing `Snackbar` action to open the saved file.

## Acceptance Criteria

- On Quest 3 during 360° SBS playback, pressing Save Frame produces a `*_SBS.png` with visible stereo disparity.
- On flat MONO Cinema playback, snapshot is effectively 2D (both halves identical).
- Snapshot for a 4K equirect source is ≤ 16 MB.
- No render-loop stall > 2 frames during capture.

## Files Touched

- `app_v2/src/vr/java/com/sza/fastmediasorter/vr/capture/VrStereoSnapshotManager.kt` (new)
- `app_v2/src/vr/cpp/OpenXrNative.cpp` — readback helper
- [vr/VrPlayerActivity.kt](../../app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt) — expose capture method to command overrides
- `app_v2/src/main/res/values{,-ru,-uk}/strings.xml` — toast labels

## Validation

- `./gradlew.bat :app_v2:assembleVrDebug`
- `./gradlew.bat :app_v2:testVrDebugUnitTest --tests com.sza.fastmediasorter.vr.capture.VrStereoSnapshotManagerTest`

## Out of Scope

- Video recording (would need OpenXR screen capture extension + MediaRecorder integration).
- Mono PNG snapshot (the standard player already covers flat content).
