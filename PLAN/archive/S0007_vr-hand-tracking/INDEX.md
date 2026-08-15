# S0007 · vr-hand-tracking — Tactical Plan

**Spec:** `PLAN/S0007_vr-hand-tracking.md`  
**Status:** Implemented  
**Priority:** 60  
**Created:** 2026-04-30  

---

## Context

The bulk of Layer E (hand-tracking) was implemented alongside S0008 (VR Immersive Controls
Panel). The C++ side (`OpenXrNative.cpp`) already registers all three required extensions,
runs `initHandTracking()`, polls `XrHandTrackingAimStateFB` + microgesture state per frame,
and dispatches the full set of `XrInputEventType` constants (17–23) through the
`XrInputCallback` channel. On the Kotlin side `VrControllerInputManager` routes swipe and
double-pinch events to playback commands, and `VrHandRayManager` translates aim-pose NDC
coordinates into `MotionEvent`s.

The `HAND_TRACKING` permission + feature are declared in the VR flavor manifest and all
three cheatsheet languages reference the hands section.

**Remaining gaps (per spec §3.5):**

1. Cursor **colour** does not change when hovering over an interactive element.
2. **Hover-enter audio** SFX is absent (spec requires distinct sound on entering a UI target).
3. **Pinch-complete (UP)** audio SFX is absent (only DOWN plays `FX_KEY_CLICK`).

---

## Phase Graph

| Phase | Slug | Status |
|---|---|---|
| Phase 01 | `verify-layer-e` | ✅ Done |
| Phase 02 | `hover-visual-feedback` | ✅ Done |
| Phase 03 | `hover-click-audio` | ✅ Done |
| Phase 04 | `docs-catalog-cleanup` | ✅ Done |

---

## Phase Files

| Phase | File |
|---|---|
| 01 | `PLAN/S0007_vr-hand-tracking/PHASE_01__verify-layer-e.md` |
| 02 | `PLAN/S0007_vr-hand-tracking/PHASE_02__hover-visual-feedback.md` |
| 03 | `PLAN/S0007_vr-hand-tracking/PHASE_03__hover-click-audio.md` |
| 04 | `PLAN/S0007_vr-hand-tracking/PHASE_04__docs-catalog-cleanup.md` |

---

## Key Files Touched by This Spec

| File | Role |
|---|---|
| `app_v2/src/vr/cpp/OpenXrNative.cpp` | C++ hand-tracking init + per-frame polling (already complete) |
| `app_v2/src/vr/java/.../openxr/OpenXrNative.kt` | JNI bindings (already complete) |
| `app_v2/src/vr/java/.../openxr/XrInputEventType.kt` | Event constants 17–23 (already complete) |
| `app_v2/src/vr/java/.../ui/VrHandRayManager.kt` | Cursor dot + MotionEvent bridge — **Phase 02/03 touch this** |
| `app_v2/src/vr/java/.../helpers/VrControllerInputManager.kt` | Swipe + pinch routing — **Phase 03 touch this** |
| `app_v2/src/vr/AndroidManifest.xml` | `HAND_TRACKING` permission (already complete) |
| `app_v2/src/main/res/values*/strings.xml` | Cheatsheet strings (already complete) |
| `docs/FEATURES.md` + `_RU.md` + `_UK.md` | Phase 04 update |

---

## Definition of Done

- Phase 01 verification table shows all S0007 §2.1 items ✅ or documented as intentional gap.
- Phase 02: cursor dot turns blue when hovering an interactive element, white when off.
- Phase 03: pinch-complete emits audio SFX; hover-enter emits a hover SFX.
- Phase 04: FEATURES.md × 3 updated; catalog regenerated; CHANGELOG entries logged;
  spec catalog status → `Implemented`.
