# Research 02 - Capture host adaptation for the main-screen menu

Resolves strategic §6.2.

## Question

The existing capture managers are bound to the Browse host and a browsed resource. How are voice, video, and photo captured from the main-screen overflow menu, where there is no resource and no network/upload path?

## Findings

- `BrowseMicRecordingManager` and `BrowseCameraCaptureManager` are coupled to a `MediaResource` plus Browse callbacks (`onUploadFile`, `networkStateMonitor`, `saveFallbackNotifier`, `resourceRepository`, `onCapturedForEditing`, `onVideoCaptured`, `onRecordingStateChanged`). Quick capture has none of these: destination is always a public device folder, never a resource or a remote upload.
- `ActivityResultLauncher` (camera result) and runtime-permission launchers must be registered before the host is `STARTED`. Both Browse managers register their launcher at construction inside `onCreate`. `MainActivity.setupViews()` runs in `onCreate` before `onStart` (see existing `MainMiniGameMenuManager(this)` construction there) - a valid registration point.
- `CameraCaptureActivity` self-manages CAMERA permission (`checkSelfPermission` + a `RequestPermission` launcher, CameraCaptureActivity.kt:42,106-109). The photo path needs no CAMERA wiring from the menu host.
- `MediaStore.ACTION_VIDEO_CAPTURE` (system camera) requires no CAMERA permission from the caller.
- `RECORD_AUDIO` must be requested by the voice path. The manifest already declares `CAMERA` and `RECORD_AUDIO`.
- `CameraCaptureSaver.save(temp, name, CameraCaptureTarget.CameraFolder, upload)` writes to `DCIM/Camera` (public, indexed). For video, the proven `localVideoFallbackTarget()` pattern passes `CameraCaptureTarget.Resource(id=-1, name=DIRECTORY_MOVIES, path=<MoviesDir>, type=LOCAL)`, which routes through `saveLocal` -> `writeToDevice` -> Movies public collection. The `upload` lambda is never called for these local targets.

## Decision

- Do **not** reuse the Browse managers. Introduce two lean host-neutral managers under `ui/main/helpers/`, reusing the proven backends:
  - `MainVoiceCaptureManager`: `MediaRecorder` (MIC -> MPEG_4/AAC, mono, 44.1 kHz, 128 kbps, `.m4a`) mirroring `BrowseMicRecordingManager`'s recorder + audio-focus + too-short-artifact guard; writes the finished file to `CaptureDestinationPolicy.resolveQuickVoiceDestination()` through `LocalDestinationClassifier` + `LocalDestinationWriter`; records a `StatsEvent.Capture(CaptureKind.VOICE)`; owns the RECORD_AUDIO request and the recording UI; releases the recorder on stop/cancel and on host pause.
  - `MainCameraCaptureManager`: registers one `ActivityResultLauncher`; `capturePhoto()` -> `CameraCaptureActivity` (in-app CameraX) -> `CameraCaptureSaver(CameraFolder)`; `captureVideo()` -> `ACTION_VIDEO_CAPTURE` -> `CameraCaptureSaver(Movies target)`.
- Filenames are timestamped (`REC_`/`CAP_` + `yyyyMMdd_HHmmss`); no rename dialog on the quick path (the "quick" intent favors zero friction; the Browse rename dialog stays Browse-only).
- Flavor scope is pure runtime gating via the injected `MediaCapabilities` surface; the new classes live in `src/main` and are never invoked on a flavor lacking the capability. No `src/<flavor>` source set is introduced.

## Open owner gate (Rule 10 - resolve before Phase 03)

The voice start/stop mechanism from a popup-menu entry has no persistent on-screen button (unlike the Browse record button). Recommended default: a modal recording dialog (elapsed indicator + Stop-and-save + Cancel). Alternative: a foreground service with notification controls (the `QuickAudioRecorderService` shape). This is the one UI-placement decision the owner must confirm; it changes only `MainVoiceCaptureManager`'s internals (Phase 03), not the rest of the plan.
