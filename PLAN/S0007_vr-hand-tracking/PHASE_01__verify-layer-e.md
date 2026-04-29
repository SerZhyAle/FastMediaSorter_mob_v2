# Phase 01 · verify-layer-e

**Spec:** S0007 · vr-hand-tracking  
**Phase:** 01 / 04  
**Status:** ⬜ Not started  

---

## Objective

Audit the existing Layer E implementation against every requirement in S0007 §2.1 and §3.
Mark each item ✅ Done / ❌ Missing / ⚠ Partial. Document intentional gaps.

---

## Verification Checklist

Execute all checks **before** starting Phase 02. Mark as Done here once confirmed.

### §2.1 Core Features

| # | Requirement | Predicate | Result |
|---|---|---|---|
| F1 | Automatic modality switching (controller ↔ hand) | `OpenXrNative.cpp` modality gate at L413/L2131 is active; C++ suppresses hand polling within 2 s of any controller edge event | ⬜ |
| F2 | Raycast targeting — `XrHandTrackingAimStateFB` → NDC | `syncHandTracking()` in C++ polls `aimPose` and calls `onPointerMove(ndcX, ndcY)` per frame; `VrHandRayManager` converts NDC → pixel + dispatches `ACTION_HOVER_MOVE` | ⬜ |
| F3 | Pinch-to-click via `pinchStrengthIndex` | C++ emits `POINTER_CLICK_DOWN` / `POINTER_CLICK_UP` at thresholds 0.9 / 0.6; `VrHandRayManager.onPointerClick()` dispatches `ACTION_DOWN` / `ACTION_UP` | ⬜ |
| F4 | Thumb swipe L/R → seek | C++ `XR_HAND_MICROGESTURE_SWIPE_LEFT_META` → `XR_EVT_SWIPE_LEFT`; `VrControllerInputManager` routes to `PlaybackCommand.SeekMicro` | ⬜ |
| F5 | Thumb swipe U/D → volume | C++ `XR_HAND_MICROGESTURE_SWIPE_UP/DOWN_META` → `XR_EVT_SWIPE_UP/DOWN`; `VrControllerInputManager` routes to `rateLimitedVolume(±1)` | ⬜ |
| F6 | Double pinch → play/pause | C++ `XR_EVT_DOUBLE_PINCH`; `VrControllerInputManager` maps `VR_DOUBLE_PINCH` → `PlaybackCommand.TogglePausePlay` | ⬜ |
| F7 | Input feedback — **visual** (ray colour on hover) | `VrHandRayManager.ensureCursor()` always returns a white dot; **no hover highlight** | ❌ → Phase 02 |
| F8 | Input feedback — **audio hover** | No hover-enter SFX in `VrHandRayManager` or `VrControllerInputManager` | ❌ → Phase 03 |
| F9 | Input feedback — pinch-begin SFX | `VrControllerInputManager.handlePointerClick()` plays `FX_KEY_CLICK` on DOWN | ✅ |
| F10 | Input feedback — pinch-complete SFX | `handlePointerClick()` UP branch does NOT play a sound | ❌ → Phase 03 |

### §3.2 OpenXR Extensions

| Check | File | Lines | Result |
|---|---|---|---|
| `XR_EXT_hand_tracking` registered | `OpenXrNative.cpp` | L619–L634 extension capability check | ⬜ |
| `XR_META_hand_tracking_aim` registered | `OpenXrNative.cpp` | same block | ⬜ |
| `XR_META_hand_tracking_microgestures` registered | `OpenXrNative.cpp` | same block + L353 `supportsMicrogestures` | ⬜ |
| `XrHandTrackerEXT` L + R created | `OpenXrNative.cpp` | `initHandTracking()` L1932 | ⬜ |

### §3.3 State Management

| Check | File | Result |
|---|---|---|
| Controller takes strict priority (modality gate) | `OpenXrNative.cpp` L413, L2131 | ⬜ |
| Pinch aim-freeze dead zone (`kPinchAimFreezeThreshold = 0.5f`) | `OpenXrNative.cpp` L1460, L2290 | ⬜ |

### §3.4 Kotlin Integration

| Check | File | Result |
|---|---|---|
| `XrInputEventType` constants 17–23 defined | `XrInputEventType.kt` | ⬜ |
| `XrInputSource.HAND = 1` defined | `XrInputEventType.kt` | ⬜ |
| `VrHandRayManager` receives pointer moves + clicks | `VrPlayerActivity.kt` L305–L322 | ⬜ |
| `VrControllerInputManager` handles SWIPE_* + DOUBLE_PINCH | `VrControllerInputManager.kt` L210–L231 | ⬜ |

### §3.5 Permissions

| Check | File | Result |
|---|---|---|
| `com.oculus.permission.HAND_TRACKING` declared | `app_v2/src/vr/AndroidManifest.xml` | ⬜ |
| `oculus.software.handtracking` uses-feature (required=false) | same | ⬜ |

### §3.5 Cheatsheet

| Check | File | Result |
|---|---|---|
| `vr_cheatsheet_section_hands` + 3 detail strings in EN | `values/strings.xml` L2842–L2845 | ⬜ |
| Same keys in RU | `values-ru/strings.xml` L2778 | ⬜ |
| Same keys in UK | `values-uk/strings.xml` L2737 | ⬜ |
| `VrCheatsheetOverlayManager` appends hands section | `VrCheatsheetOverlayManager.kt` L158–L161 | ⬜ |

---

## Execution Steps

```
[ ] 1. Open each file/line listed above and confirm the code matches the predicate.
[ ] 2. Update Result column: ✅ / ❌ / ⚠.
[ ] 3. For any ❌: confirm it is covered by a later phase or document as intentional gap.
[ ] 4. Build project (standard debug) and confirm zero new errors in the vr source set.
     Command: .\scripts\builders\build-debug.PS1
[ ] 5. Mark phase Done.
```

---

## Verification Predicates

- All §2.1 items marked ✅ or assigned to a later Phase.
- Build passes without errors in `app_v2:assembleStandardDebug`.

---

## Status

**Done:** ⬜  
*(Set to ✅ Done after all checklist items are confirmed.)*
