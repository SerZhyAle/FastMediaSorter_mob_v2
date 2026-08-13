# Tactical Plan: S0041 — debug-vr180-fisheye-quality-regression

**Strategic spec:** `PLAN/S0041_debug-vr180-fisheye-quality-regression.md`
**Status:** Tactical
**Ticket:** S0041
**Priority:** 90

---

## Summary

3-phase investigation plan. Phase 1 adds `VR_QUALITY_DEBUG` logging to the VR playback stack (code change + build). Phases 2-3 are manual: device session + root-cause analysis and fix.

---

## Phase Progress

| Phase | Slug | Status | Completed |
|------:|------|:------:|-----------|
| 01 | add-debug-logging | `✅ Done` | 2026-04-30 |
| 02 | build-and-device-test | `🚧 In Progress` | build done; on-device capture deferred to human |
| 03 | analyze-and-fix | `⬜ Not Started` | depends on Phase 02 device log |
| 04 | docs-catalog-cleanup | `⬜ Not Started` | depends on Phase 03 fix |

Phases done: 1 / 4 (build gate cleared; remaining gates require Quest 3)

---

## Pre-Implementation Blockers

- [x] Key classes located: `VideoPlayerManager.kt`, `VrStereoRenderer.kt`
- [x] Current logging confirmed via log `fastmediasorter_20260430_031429.log`
- [x] Fisheye shader reviewed — only one uniform `uFisheyeUOffset`; no FOV/UV-scale uniforms (FOV hardcoded as `PI * 0.5`)
- [x] `VideoLayerGeometry` logging ALREADY exists in `OpenXrSessionManager.applyLayerDescriptor` (no action needed)

---

## Discovered facts (from codebase read — Phase 01 precondition)

- `VideoPlayerManager.onTracksChanged` at line 480 already obtains `videoFormat` but does NOT log it — **this is the missing log**.
- `VrStereoRenderer.renderFisheyeQuad` sets `glUniform1f(fUFisheyeUOffsetLoc, fisheyeUOffset)` but does NOT log it — **log needed once per session**.
- Fisheye shader FOV is hardcoded to `PI * 0.5` (90° rho limit). This correctly covers the full hemisphere for a 180° fisheye lens — shader math is correct on inspection.
- `dbgRenderEyeCount` field is available in `VrStereoRenderer` — reuse as guard for one-shot log.
