# Tactical Plan: S0563 - Unified "Camera" entry with in-screen mode switch

**Strategic spec:** [`../S0563_camera-unified-entry-mode-switch.md`](../S0563_camera-unified-entry-mode-switch.md)
**Status:** Implemented (BlockNeedUserTest)
**Created:** 2026-06-20 by `/spec-tech` (via `/spec-all`)

> All four phases implemented and the standard debug build passes. Video path awaits a real-device
> pass (AVD insufficient, inherited S0545 constraint).

> Builds the in-screen `PHOTO|VIDEO` switch on top of the S0545 unified capture host and merges the
> main-activity overflow "Photo"/"Video" actions into one "Camera" action. Fixed-mode entry points
> (OCR, resource/browse, widget) are untouched.

---

## Phase Overview

| Phase | Title | Depends on | Blocks |
|------:|-------|------------|--------|
| 01 | Host contract + flow + session: mode-switch capability | none | 02, 03 |
| 02 | Host UI: in-screen PHOTO\|VIDEO switch | 01 | 03 |
| 03 | Main-menu unified "Camera" entry | 01, 02 | 04 |
| 04 | Docs, catalog, cleanup, device-test handoff | 03 | - |

---

## Design anchors (from research)

- Contract owner: `app_v2/.../ui/cameracapture/CameraCaptureContract.kt` - already carries
  `EXTRA_CAPTURE_MODE` and `EXTRA_RESULT_MEDIA_KIND`. Add `EXTRA_ALLOW_MODE_SWITCH`,
  `EXTRA_OUTPUT_DIR`, `EXTRA_OUTPUT_BASENAME`, `EXTRA_RESULT_OUTPUT_PATH`.
- Flow owner: `CameraCaptureFlowManager.kt` - holds `mode` (make `var`), `isVideoMode`,
  `microphoneEnabled`. Add `allowModeSwitch`, mode-aware output resolution, `switchMode()`.
- Session owner: `CameraCaptureSessionManager.kt` - `videoMode` already selects the CameraX
  use-case at `bindToLifecycle`; `switchCamera()` already rebinds. Add `applyMode(videoMode)`.
- Host view: `CameraCaptureActivity.kt` + `res/layout/activity_camera_capture.xml` +
  `res/layout-land/activity_camera_capture.xml` - add a `MaterialButtonToggleGroup` above the
  shutter (`cameraActionBar`).
- Main menu: `MainQuickCaptureMenuManager.kt` (merge photo+video item), `MainCameraCaptureManager.kt`
  (single launch + generic result), `MainActivity.kt` (single callback + combined gating).

---

## Validation strategy

- Phase 01: `.\a.ps1 fk` (compile-only contract/flow/session change).
- Phase 02: `.\a.ps1 fc` (layout + activity + strings).
- Phase 03: `.\a.ps1 fc`, then insert `Timber.d("S0563: ..")` device-test tags and `.\a.ps1 d`.
- Phase 04: docs/catalog only.

Video path is not reliably testable on AVD (inherited S0545 constraint) - final status is
`BlockNeedUserTest` pending a real-device pass.
