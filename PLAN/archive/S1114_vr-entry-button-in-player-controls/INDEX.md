# S1114 - Tactical INDEX

**Ticket:** S1114
**Strategic spec:** `PLAN/S1114_vr-entry-button-in-player-controls.md`
**Status:** BlockNeedUserTest
**Phases:** 3/3 done

## Pre-Implementation Blockers

- [x] Both hosts share the controller layout (`?attr/customPlayerControlsLayout` -> `custom_player_controls[_large].xml`), so one button XML serves both.
- [x] Main host VR launch already exists (`PlayerVrLaunchManager`, badge). Standalone had none: reuse the Browse/Resource VR-cinema cold-launch pattern (`returnTarget = null`).
- [x] XR availability gate is the runtime `XrDetectionFacade` seam (no BuildConfig guard needed).

## Phases

| Phase | Title | Status | Steps |
| --- | --- | --- | --- |
| 01 | shared-button-and-enum | ✅ Done | 2/2 |
| 02 | main-host-wiring | ✅ Done | 3/3 |
| 03 | standalone-wiring | ✅ Done | 2/2 |

## Completion Gate

- Project compiles (standard debug).
- VR-entry button appears in the video controls row of both hosts when XR is available; hidden otherwise.

## Change Log

- 2026-07-19 - tactical plan created (scope widened to include standalone per owner).
