# Research 02 - Zoom presets: how they are built and clamped

**Spec:** S0753
**§6 item:** 5 (guaranteed-maximum behaviour)
**Status:** Resolved
**Date:** 2026-06-27

## Question

Where do the current presets come from, and how should the new candidate set 0.5/1/2/3/10/20/30 behave on devices whose max zoom falls between table values?

## Findings

- Presets are computed at runtime, not hardcoded in a resource. The capability snapshot's `buildZoomPresets(minZoom, maxZoom)` builds from candidates `listOf(minZoom, 1f, 2f, maxZoom)`, filters to `it in minZoom..maxZoom`, rounds to one decimal, de-duplicates, sorts.
- So the on-screen "0.5 / 1 / 2 / 10" is derived: 0.5 = `minZoom` (ultra-wide in combined range), 10 = `maxZoom` (device max). The literal table values are not stored anywhere.
- Device zoom range comes from `ZoomState` (`minZoomRatio` / `maxZoomRatio`) read once per bind by the capability probe. Zoom is applied via `cameraControl.setZoomRatio(...)`; no `setLinearZoom` anywhere. Pinch goes through the same ratio path.
- Adding 3/10/20/30 to the candidate list and keeping the existing `it in minZoom..maxZoom` filter automatically hides unreachable presets - the mechanism already exists.

## The maximum-button trap

If the candidate list is changed to the fixed table `listOf(minZoom, 1f, 2f, 3f, 10f, 20f, 30f)` and `maxZoom` is dropped, then on a device whose `maxZoom` is e.g. 8x the highest surviving candidate is 3x (10/20/30 filtered out). The user can no longer reach the real 8x ceiling from a preset.

**Resolution (adopted in spec §5.1.A / ADR-1):** always append the lens `maxZoom` to the candidate list before filtering, so there is always a "ceiling" button even when it sits between table values. De-dup handles the case where `maxZoom` equals a table value.

## 0.5x lens note

`minZoom < 1.0` surfaces the 0.5 candidate only when the lens combined range dips below 1x. There is no lens-switch logic for ultra-wide; on devices where ultra-wide is a separate camera not in the combined range, `minZoom` is 1.0 and 0.5 never appears. This is acceptable capability-gating, not a bug to fix.

## Test gap (in-scope for S0753)

`buildZoomPresets` is pure logic with no unit test today. Since S0753 modifies it, the tactical plan should add a unit test covering the new candidate set, clamping, the guaranteed-maximum append, and rounding. Not parked as a separate ticket - it is part of changing this function.

## Sources

- Codebase: `ui/cameracapture/model/CameraRuntimeCapabilities.kt` (`buildZoomPresets`); `ui/cameracapture/helpers/CameraCapabilityProbe.kt` (`ZoomState` read); `ui/cameracapture/helpers/CameraCaptureSessionManager.kt` (`setZoomRatio`); `res/layout/activity_camera_capture.xml` + `res/layout-land/...` (`cameraZoomPresetGroup` ChipGroup).
