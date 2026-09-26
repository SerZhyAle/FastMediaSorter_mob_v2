# Pointer - `CAPTURE-OUTPUT`

| | |
| --- | --- |
| **Id** | `CAPTURE-OUTPUT` |
| **Version** | 0.2, draft. Owner: this product |
| **Home** | `capture-output/README.md` in the shared contracts catalog |
| **Role here** | owner and producer (every screenshot, recording, photo, frame, text and translation file the app writes) |

## What this repository must do to stay conformant

- Name every automatic output `<prefix>_<yyMMdd>_<HHmmss>[ (n)].<ext>` with the kind's prefix from rule 1, formatted in an invariant locale.
- Choose the final name before writing; on a collision in the destination append ` (n)` from 2, never overwrite and never leave the suffix to the media store.
- Default each kind to its rule 9 folder; let the user choose a destination per kind; tell the user about every fallback.
- Write the rule 1 format per kind; text as UTF-8 without BOM, LF; the translation layout of rule 15.
- Add a new kind to the contract before the code writes it.

## Where it lives here

- `app_v2/.../util/CaptureFileNamer.kt` (prefixes and the name grammar).
- `app_v2/.../util/CaptureDestinationPolicy.kt`, `util/ScreenshotDestinationPolicy.kt` (default folders).
- `app_v2/.../data/capture/CameraCaptureSaver.kt`, `MicRecordingSaver.kt`, `domain/usecase/SaveScreenshotUseCase.kt`, `ui/player/helpers/SaveVideoFrameManager.kt`, `screencapture/ScreenVideoRecordingService.kt` (writers).
- `app_v2/.../ui/cameraocr/helpers/CameraOcrStorageManager.kt`, `domain/usecase/SaveTextNoteUseCase.kt` (not yet aligned - S3746).
