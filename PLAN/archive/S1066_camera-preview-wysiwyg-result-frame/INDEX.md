# Tactical Plan: S1066 - camera-preview-wysiwyg-result-frame

**Strategic spec:** [`../S1066_camera-preview-wysiwyg-result-frame.md`](../S1066_camera-preview-wysiwyg-result-frame.md)
**Research:** [`research/03__aspect-ratio-source.md`](research/03__aspect-ratio-source.md)
**Feature:** The in-app camera preview equals the saved file (WYSIWYG); a result frame marks the file bounds when the format is narrower than the shown frame.
**Tier:** 4 - Strategic (ad-hoc)
**Priority:** 70
**Status:** BlockNeedUserTest
**Phases:** 5 / 5 done
**Last updated:** 2026-07-16

---

## Resolved design (owner decisions 2026-07-15)

- Aspect set is strictly camera-native 4:3 and 16:9 (no app crop ratios). Default 4:3.
- Unify preview + capture field of view via a CameraX `UseCaseGroup` + `ViewPort` so all bound use cases share one crop rect (WYSIWYG).
- Photo mode: `ViewPort` = native 4:3 (full frame). Preview shows the full frame via `PreviewView` `FIT_CENTER` (letterbox bands sit under the control bars). A result-frame overlay draws the selected ratio (16:9 dimmed inside 4:3); hidden at 4:3. The saved JPEG is centre-cropped to the selected ratio (reusing the S0765 EXIF-preserving crop path).
- Video mode: `ViewPort` = selected ratio. Preview shows exactly the recorded region; no result frame (preview == file, spec §6.4).
- Zoom (owner Q1 = "keep extended digital zoom, honestly crop video"): the app-level digital zoom beyond the CameraX native max stays reachable AND is baked into the video file, not just the preview - approach fixed in Phase 04 from the verification brief.
- Selected aspect ratio persists between capture sessions (spec §3.1.4).

---

## Phase Overview

| # | Phase | Depends on | Status | File |
|---|-------|-----------|--------|------|
| 01 | viewport-coverage-and-aspect | - | ✅ Done | [PHASE_01__viewport-coverage-and-aspect.md](PHASE_01__viewport-coverage-and-aspect.md) |
| 02 | result-frame-overlay | 01 | ✅ Done | [PHASE_02__result-frame-overlay.md](PHASE_02__result-frame-overlay.md) |
| 03 | aspect-ratio-persistence | 01 | ✅ Done | [PHASE_03__aspect-ratio-persistence.md](PHASE_03__aspect-ratio-persistence.md) |
| 04 | honest-video-digital-zoom | 01 | ✅ Done | [PHASE_04__honest-video-digital-zoom.md](PHASE_04__honest-video-digital-zoom.md) |
| 05 | strings-docs-gates-device | 02,03,04 | ✅ Done | [PHASE_05__strings-docs-gates-device.md](PHASE_05__strings-docs-gates-device.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Completion Gate

- [ ] For each selectable ratio the saved photo contains exactly the region inside the on-screen frame (within pixel tolerance).
- [ ] The recorded video contains exactly the region shown in the preview.
- [ ] Preview always shows at least the final frame - the result frame fits inside the screen.
- [ ] Digital zoom matches in preview, frame and file - photo and video.
- [ ] Changing ratio / zoom / photo-video mode rebuilds the frame and file bounds consistently.
- [ ] Optical zom and quality paths (night/HDR/manual/metadata) work without regression.
- [ ] Orientation, date and geotag survive the new crop path exactly as before.
- [ ] standard debug build PASS; noLegal debug build PASS (camera lives in shared `src/main`).
- [ ] Device verification (measurable file-vs-frame overlay per ratio, photo and video) - `BlockNeedUserTest`.

---

## Change Log

- 2026-07-15 - Tactical plan authored; owner Q1/Q2 resolved (keep extended digital zoom + honest video crop; video preview == record).
- 2026-07-15 - Phases 01-03 implemented: ViewPort/UseCaseGroup + fitCenter preview, photo 16:9 JPEG crop, video ViewPort=selected, ResultFrameOverlayView, aspect persistence. standard + noLegal compile PASS; fast gates + detekt (scoped) + settings-doc-sync green. Ticket stays In Progress (Phase 04 video re-encode + Phase 05 finalize pending); increment ready for an interim on-device WYSIWYG check.
- 2026-07-15 - Tech-debt noted: `CameraCaptureSessionManager` is over detekt `TooManyFunctions` (47) / `CyclomaticComplexMethod` (bindToLifecycle 22) as pre-existing un-baselined debt worsened by this change; decomposition is out of S1066 scope (candidate `/spec-draft`).
- 2026-07-16 - Phase 05 done, 5/5. Strings: no new keys (result frame is decorative; `@color/camera_result_frame_scrim` is the only new resource). Rule 11 land counterpart n/a - the screen is portrait-locked (S0918/S0754/S0924), no `layout-land` variant exists. Settings-doc gate green without regen (the aspect ratio lives in DataStore behind the in-capture dialog, not on the Settings screen). 7 fast gates PASS; `assert-detekt -Gate` PASS [scoped] - 0 new findings project-wide, so the debt above is in fact baselined, not un-baselined. `standard` compile PASS (23s) and `noLegal` compile PASS (39s) - the latter closes the completion gate for the Phase 04 media3-transformer dependency, which had not been re-verified on noLegal since the 2026-07-15 entry. Feature inventory: `camera.wysiwyg-preview-result-frame` (area Camera, `standard,lite,photos,legacy` - matching the sibling records for this screen; the phase's original `standard,noLegal,vr` was wrong, the package has no BuildConfig gate and lives in `src/main`). 3 probe tags inserted; ticket -> `BlockNeedUserTest` for the on-device file-vs-frame measurement. Debt parked as S1071.
