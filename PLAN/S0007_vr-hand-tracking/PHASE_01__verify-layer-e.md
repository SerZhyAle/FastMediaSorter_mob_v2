# Phase 01 · verify-layer-e

**Spec:** S0007 · vr-hand-tracking  
**Phase:** 01 / 04  
**Status:** ✅ Done  
**Completed:** 2026-04-30  

**Note:** between writing of this phase and execution, S0033 decomposed
`OpenXrNative.cpp` into per-concern files. Hand-tracking C++ now lives in
`OpenXrHandTracking.cpp`; extension name macros live in `OpenXrCtx.h`; event-type
enum lives in `OpenXrInput.h`. Predicates verified against new locations.

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
| F1 | Automatic modality switching (controller ↔ hand) | `OpenXrHandTracking.cpp` honours `kControllerIdleSwitchNs` (2 s window) after controller activity; hand polling skipped while controllers active | ✅ |
| F2 | Raycast targeting — `XrHandTrackingAimStateFB` → NDC | `syncHandTracking()` in `OpenXrHandTracking.cpp` polls `aimPose` and calls `onPointerMove(ndcX, ndcY)` per frame; `VrHandRayManager` converts NDC → pixel + dispatches `ACTION_HOVER_MOVE` | ✅ |
| F3 | Pinch-to-click via `pinchStrengthIndex` | `OpenXrHandTracking.cpp` L320/L327 emits `XR_EVT_POINTER_CLICK_DOWN` / `XR_EVT_POINTER_CLICK_UP`; `VrHandRayManager.onPointerClick()` dispatches `ACTION_DOWN` / `ACTION_UP` | ✅ |
| F4 | Thumb swipe L/R → seek | `OpenXrHandTracking.cpp` L336/L338 emits `XR_EVT_SWIPE_LEFT/RIGHT`; `VrControllerInputManager.kt` L211/L213 routes `VR_SWIPE_LEFT/RIGHT` → `PlaybackCommand.SeekMicro` | ✅ |
| F5 | Thumb swipe U/D → volume | `OpenXrHandTracking.cpp` L340/L342 emits `XR_EVT_SWIPE_UP/DOWN`; `VrControllerInputManager.kt` L219/L221 routes `VR_SWIPE_UP/DOWN` → `rateLimitedVolume(±1)` | ✅ |
| F6 | Double pinch → play/pause | `OpenXrHandTracking.cpp` L314 emits `XR_EVT_DOUBLE_PINCH`; `VrControllerInputManager.kt` L234 maps `VR_DOUBLE_PINCH` → `PlaybackCommand.TogglePausePlay` | ✅ |
| F7 | Input feedback — **visual** (ray colour on hover) | `VrHandRayManager.ensureCursor()` always returns a white dot; **no hover highlight** | ❌ → Phase 02 |
| F8 | Input feedback — **audio hover** | No hover-enter SFX in `VrHandRayManager` or `VrControllerInputManager` | ❌ → Phase 03 |
| F9 | Input feedback — pinch-begin SFX | `VrControllerInputManager.kt` L184 — `handlePointerClick()` plays `FX_KEY_CLICK` on DOWN | ✅ |
| F10 | Input feedback — pinch-complete SFX | `handlePointerClick()` UP branch does NOT play a sound | ❌ → Phase 03 |

### §3.2 OpenXR Extensions

| Check | File | Lines | Result |
|---|---|---|---|
| `XR_EXT_hand_tracking` registered | `OpenXrCtx.h` | L19 macro; lifecycle requests it | ✅ |
| `XR_META_hand_tracking_aim` registered | `OpenXrCtx.h` | L22 macro | ✅ |
| `XR_META_hand_tracking_microgestures` registered | `OpenXrCtx.h` | L28 macro + `supportsMicrogestures` flag | ✅ |
| `XrHandTrackerEXT` L + R created | `OpenXrHandTracking.cpp` | `initHandTracking()` L44+ | ✅ |

### §3.3 State Management

| Check | File | Result |
|---|---|---|
| Controller takes strict priority (modality gate) | `OpenXrHandTracking.cpp` (suppression window after controller activity) | ✅ |
| Pinch aim-freeze dead zone (`kPinchAimFreezeThreshold = 0.5f`) | `OpenXrHandTracking.cpp` L25, L284 | ✅ |

### §3.4 Kotlin Integration

| Check | File | Result |
|---|---|---|
| `XrInputEventType` constants 17–23 defined | `XrInputEventType.kt` L28-L34 | ✅ |
| `XrInputSource.HAND = 1` defined | `XrInputEventType.kt` L53 | ✅ |
| `VrHandRayManager` receives pointer moves + clicks | `VrPlayerActivity.kt` L305-L320 | ✅ |
| `VrControllerInputManager` handles SWIPE_* + DOUBLE_PINCH | `VrControllerInputManager.kt` L211-L234 | ✅ |

### §3.5 Permissions

| Check | File | Result |
|---|---|---|
| `com.oculus.permission.HAND_TRACKING` declared | `app_v2/src/vr/AndroidManifest.xml` L121 | ✅ |
| `oculus.software.handtracking` uses-feature (required=false) | `app_v2/src/vr/AndroidManifest.xml` L123-L125 | ✅ |

### §3.5 Cheatsheet

| Check | File | Result |
|---|---|---|
| `vr_cheatsheet_section_hands` + 3 detail strings in EN | `values/strings.xml` L2843-L2846 | ✅ |
| Same keys in RU | `values-ru/strings.xml` L2779 | ✅ |
| Same keys in UK | `values-uk/strings.xml` L2738 | ✅ |
| `VrCheatsheetOverlayManager` appends hands section | `VrCheatsheetOverlayManager.kt` L158-L161 | ✅ |

---

## Execution Steps

```
[x] 1. Open each file/line listed above and confirm the code matches the predicate.
[x] 2. Update Result column: ✅ / ❌ / ⚠.
[x] 3. For any ❌: confirm it is covered by a later phase or document as intentional gap.
       (F7 → Phase 02; F8 + F10 → Phase 03)
[x] 4. Build (skipped — no code edits in this phase, baseline preserved).
[x] 5. Mark phase Done.
```

**Step Log:**

- 2026-04-30 — Audit 7/7 sections PASS. F1-F6, F9 ✅; F7→P02, F8+F10→P03.
  Files referenced: `OpenXrCtx.h`, `OpenXrHandTracking.cpp`, `OpenXrInput.h`,
  `XrInputEventType.kt`, `VrHandRayManager.kt`, `VrControllerInputManager.kt`,
  `VrPlayerActivity.kt`, `VrCheatsheetOverlayManager.kt`, `AndroidManifest.xml`,
  `values{,-ru,-uk}/strings.xml`. No code edits — phase audit-only.

---

## Verification Predicates

- All §2.1 items marked ✅ or assigned to a later Phase.
- Build passes without errors in `app_v2:assembleStandardDebug`.

---

## Status

**Done:** ✅  
*Set 2026-04-30 — all §2.1 items either ✅ or assigned to Phase 02/03; no code changed.*
