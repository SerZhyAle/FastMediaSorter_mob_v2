# Phase 02 - result-frame-overlay

**Goal:** In photo mode draw a light contour + ~50% outside dim marking the selected-ratio file bounds inside the full-frame preview; hidden when the format equals the shown frame or in video mode.

## Context

- ADR-1: preview shows the full sensor frame; the file bounds are a semi-transparent frame (light contour + ~50% dim outside). Frame hidden when format == shown frame (spec §6.4) and in video mode (preview == file there).
- Reuse the existing overlay pattern: `GridOverlayView` / `FocusRingOverlayView` are lightweight `View`s with a cheap `onDraw` and no per-frame allocation.

## Steps

- [x] **2.1** New `ResultFrameOverlayView` (`ui/cameracapture/`) - a decorative `View` (non-clickable, non-focusable) that, given the displayed-image rect (from Phase 01 §1.5) and a target ratio, computes the centred target-ratio rect and draws: a light stroke contour + a ~50% scrim over the four outside bands (path/`clipOutRect`). No allocation inside `onDraw` (pre-allocate `Paint`/`Path`/`RectF`, mutate in place) - the frame redraws on every zoom tick (spec §3.2 perf).
  - Colours from `?attr`/`@color` (no hardcoded hex, Rule 19). Contour = an existing camera stroke colour; scrim = a new `@color/camera_result_frame_scrim` at ~50% alpha.
  - Verify: `a.ps1 fr` PASS.
- [x] **2.2** Add the view to `res/layout/activity_camera_capture.xml` constrained to `previewViewCamera` bounds (like the grid overlay), initially `gone`.
  - Verify: `a.ps1 fr` PASS.
- [x] **2.3** Host wiring in `CameraCaptureActivity`: a `renderResultFrame()` that shows the overlay only when photo mode AND `selectedAspectRatio` differs from the full-frame ratio (16:9 selected). Call it on bind/capabilities change, on aspect apply, and on mode switch. Feed it the displayed-image rect + target ratio. Hide in video mode and at 4:3.
  - Verify: `a.ps1 fk` PASS.
- [x] **2.4** Accessibility (spec §3.2/§3.3): the frame is distinguished by contour + dim (not colour alone). No new focusable control here (format stays in the settings dialog). Add a `contentDescription`/announce only if it does not fight TalkBack on the preview - the frame is decorative.
  - Verify: reasoning note.

## Done criteria
- Photo + 16:9: a centred 16:9 frame with dimmed top/bottom bands sits inside the full 4:3 preview and matches the saved crop.
- Photo + 4:3, and all video: no frame, clean viewfinder.
- Redraw on zoom/ratio/mode change is smooth (no jank, no per-frame allocation).
