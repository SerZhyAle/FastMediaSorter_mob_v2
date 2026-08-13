# S1354 research - capture SAF write paths

**Date:** 2026-08-03
**Question:** Which configured capture destinations currently reach a SAF-capable write path?

## Evidence

- `CaptureDestinationPolicy` returns `File` for all four configured capture categories and accepts a resource when it is local, writable and not virtual. A `content://` tree therefore passes the eligibility check but is converted to `File`.
- `MicRecordingSaver` builds the local target with `File(targetResource.path, name)` and sends its absolute path to the MediaStore-aware local writer. It has no SAF branch.
- `BrowseCameraCaptureManager` maps configured photo and video resources into `CameraCaptureTarget.Resource`; `CameraCaptureSaver` handles a local resource by building an absolute `File` path and likewise has no SAF branch.
- `LocalCopyFileOperation` already detects a `content:/` destination, resolves the tree through `SafHelper`, creates a writable child document and writes through `ContentResolver.openOutputStream`. This is the in-repo pattern to reuse rather than treating a URI as a filesystem path.
- `ScreenVideoRecordingService` consumes `screenRecordingDestinationResourceId`, resolves it through `CaptureDestinationPolicy`, builds `File(destDir, tempFile.name)` and sends the resulting path to the same kind of local writer. It therefore has the same confirmed SAF defect.
- `videoSnapshotResourceId` and `screenshotDestinationResourceId` are separate settings and are intentionally outside S1354 until their file-level paths are proven shared.

## Decision

S1354 covers microphone, camera photo, camera video and screen recording only. The implementation should centralise the SAF branch for local captured-media writes, retain existing local-file and network routing, and add each confirmed caller to the shared path.
