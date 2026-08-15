# S1261 research 01 - Galaxy S25 FE camera diagnostics report (strategic §6.1)

Owner-captured from the in-app System info report, 2026-07-28, app 2.60.7280.204-NoLegal-DEBUG (260728020), Android 16 / API 36, SM-S731B.

## Raw camera section

```text
[0]   Camera 0, back,  focal 5.40 mm, zoom 0.57 - 10.00, min focus 10.0 dpt, max photo 4080x3060 (12.5 MP)
[0/2] physical of 0,   focal 1.74 mm, zoom 1.00 - 8.00,  fixed focus,        max photo 4000x3000 (12.0 MP)
[0/5] physical of 0,   focal 5.40 mm, zoom 1.00 - 8.00,  min focus 10.0 dpt, max photo 4080x3060 (12.5 MP)
[0/6] physical of 0,   focal 7.00 mm, zoom 1.00 - 8.00,  min focus 2.0 dpt,  max photo 3264x2448 (8.0 MP)
[1]   Camera 1, front, focal 3.22 mm, zoom 1.00 - 8.00,  fixed focus,        max photo 4000x3000 (12.0 MP)
[2]   Camera 2, back,  focal 1.74 mm, zoom 1.00 - 8.00,  fixed focus,        max photo 4000x3000 (12.0 MP)
[3]   Camera 3, front, focal 3.22 mm, zoom 1.00 - 8.00,  fixed focus,        max photo 3392x2544 (8.6 MP)
```

## Facts established

1. **The 0.5 the owner used to see was real.** Logical back camera 0 declares zoom ratio range **0.57 - 10.00**: the platform fuses ultra-wide + main + tele behind one camera and switches optics itself below 1x. Pre-S1189 the app bound this camera by default, so its floor (0.57, truncated to 0.5 by the preset step) appeared in the zoom row.
2. **Every other back path has a floor of 1.00 in its own coordinate system**: the physical sub-lenses [0/2], [0/5], [0/6] and the standalone ultra-wide logical camera [2]. Any of them bound directly can never produce a sub-1x native ratio.
3. **Both Samsung exposure paths coexist on this device**: ultra-wide is reachable as a physical sub-lens (0/2) AND as its own logical camera (2). The strategic §5.3 requirement (support both) is confirmed necessary.

## Defects this report proves in current code

- **D1 - initial lens is "widest back", not "main back"** (`CameraCaptureSessionManager.bind`, `activeCameraIndex = indexOfFirst { back }` over a list sorted widest-first by `lensOrder`): on any expanded multi-lens set the screen opens on the ultra-wide entry (floor 1.00) instead of logical camera 0 (floor 0.57). Sub-1x vanishes from the row exactly as reported.
- **D2 - raw focal-length ratios are wrong across different sensor sizes** (`CameraCapabilityProbe`, `zoomMultiplier = thisFocal / referenceFocal`): ultra-wide 1.74/5.40 = 0.32 while the true equivalent floor is 0.57; tele 7.00/5.40 = 1.30 while a 7 mm lens on its smaller sensor is ~3x. Correct sources: field-of-view normalization (`focal / SENSOR_INFO_PHYSICAL_SIZE.width`, not captured in the current report - add to diagnostics) and, for the widest lens inside a logical camera, the containing logical camera's own floor (0.57 - present in the report).
- **D3 - diagnostics stops at the platform view.** The report shows what Camera2 declares but not what the app derived from it: the selected lens set, the entry actually bound, per-entry multiplier and the resulting preset labels. Without that section every device round-trip needs adb (the owner's phone has ADB off) - extend the Cameras section with the app-side view (S1189 ADR-2, "diagnostics precedes functionality").

## Consequence for the fix

- Zoom-row floor must come from the device (`minEquivalentZoomRatio` already computed in S1189) with a tap that auto-switches to the lens that reaches it - and the bound-lens default must be the main back camera, not the widest.
- Equivalents must be sensor-normalized (FOV) with the logical-floor cross-check; raw focal ratio stays only as the last-resort fallback.

**Status:** Resolved (device facts captured; app-side view to be added by the tactical plan's diagnostics phase).
