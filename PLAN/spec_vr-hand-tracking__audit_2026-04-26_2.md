# Spec Audit: vr-hand-tracking

**Strategic spec:** [`spec_vr-hand-tracking.md`](spec_vr-hand-tracking.md)
**Tactical plan:** n/a (no `spec_vr-hand-tracking/INDEX.md`)
**Audit date:** 2026-04-26
**Mode:** strategic
**Flags:** —
**Outcome:** Broken

---

## 1. Summary

| Metric | Count |
|--------|------:|
| Checks total | 19 |
| PASS | 15 |
| WARN | 2 |
| FAIL | 1 |
| MANUAL | 1 |
| EXEMPT | 0 |

All core implementation items are verified: OpenXR extensions, `HandSystem`/`initHandTracking`/`syncHandTracking`, modality priority gate, pinch hysteresis with aim-freeze, microgestures, double-pinch, `VrControllerInputManager` SOURCE_HAND routing, `VrHandRayManager` NDC→MotionEvent bridge, audio feedback, and manifest permission. Trilingual FEATURES coverage now PASS (EN bullet + RU/UK TODO translate placeholders). One FAIL remains: `VrCheatsheetOverlayManager.buildContent()` has no hand-tracking section, violating §3.5.

---

## 2. Strategic Audit

### 2.1 Goals Coverage (§2.1)

| # | Goal | Key artefacts | Status | Evidence |
|---|------|---------------|:------:|----------|
| 1 | Automatic Modality Switching | `syncHandTracking` modality gate, `kControllerIdleSwitchNs` | PASS | `OpenXrNative.cpp:1863–1886` |
| 2 | Raycast Targeting | `XrHandTrackingAimStateFB`, `emitPointerMove`, `VrHandRayManager` | PASS | `OpenXrNative.cpp:1982–2016` |
| 3 | Pinch-to-Click | Hysteresis 0.9/0.6, aim-freeze, `XR_EVT_POINTER_CLICK_UP/DOWN` | PASS | `OpenXrNative.cpp:2021–2055`, `VrHandRayManager.kt:89–108` |
| 4 | Microgestures (seek/volume) | `XrHandMicrogestureFlagsMETA` swipe constants, dispatch in `syncHandTracking` | PASS | `OpenXrNative.cpp:129–139` |
| 5 | Double Pinch (toggle play/pause) | `XR_EVT_DOUBLE_PINCH = 23`, `CommandId.VR_DOUBLE_PINCH` | PASS | `XrInputEventType.kt:34`, `VrControllerInputManager.kt:169` |
| 6 | Input Feedback — audio | `AudioManager.FX_KEY_CLICK` | PASS | `VrControllerInputManager.kt:122` |
| 6 | Input Feedback — visual cheatsheet | Hand-tracking section absent from `VrCheatsheetOverlayManager` | **FAIL** | `buildContent()` lines 136–159: controllers/keyboard/mouse only |

### 2.2 Constraints (§3)

| # | Constraint | Status | Evidence |
|---|-----------|:------:|----------|
| C1 | `XR_EXT_hand_tracking` registered | PASS | `OpenXrNative.cpp:33` |
| C2 | `XR_META_hand_tracking_aim` registered | PASS | `OpenXrNative.cpp:36` |
| C3 | `XR_META_hand_tracking_microgestures` registered | PASS | `OpenXrNative.cpp:42` |
| C4 | `XrHandTrackerEXT` L + R | PASS | `initHandTracking():1813–1814` |
| C5 | Per-frame `xrLocateHandJointsEXT` + `XrHandTrackingAimStateFB` | PASS | `syncHandTracking():1908–1938` |
| C6 | Controller strict priority | PASS | `kControllerIdleSwitchNs` gate |
| C7 | `VrControllerInputManager` + `source: Int` | PASS | `onInputEvent(type,hand,value,source):77` |
| C8 | `XrInputCallback` updated | PASS | `XrInputCallback.kt:27` |
| C9 | `com.oculus.permission.HAND_TRACKING` | PASS | `AndroidManifest.xml:121` |
| C10 | `VrHandRayManager` NDC→MotionEvent | PASS | `VrHandRayManager.kt:46–108` |
| C11 | Audio feedback via `AudioManager` | PASS | `VrControllerInputManager.kt:122` |
| C12 | Hand-tracking cheatsheet section (§3.5) | **FAIL** | `VrCheatsheetOverlayManager.buildContent()` has no hands section |

### 2.3 User-Facing Text (FEATURES trilingual)

| Artefact | Status | Evidence |
|---------|:------:|----------|
| `docs/FEATURES.md` | PASS | line 162: `**VR hand tracking**:` |
| `docs/FEATURES_RU.md` | PASS | line 148: TODO translate placeholder contains keyword |
| `docs/FEATURES_UK.md` | PASS | line 148: TODO translate placeholder contains keyword |

### 2.4 Catalogue Coverage

| Class | Status | Note |
|-------|:------:|------|
| `VrHandRayManager` | **WARN** | Not in `app_v2.jsonl`; `src/vr/` outside scan.ps1 scope — use `set.ps1` |
| `XrInputSource` | **WARN** | Object in `XrInputEventType.kt`, not cataloged |

### 2.5 Manual Acceptance Signals

- [ ] **MANUAL** — Quest 3 device: controllers down → hand tracking auto-activates, ray cursor appears, pinch triggers click
- [ ] **MANUAL** — Double pinch toggles play/pause; thumb swipes adjust seek/volume; controllers resume priority on button press
- [ ] **MANUAL** — Cheatsheet overlay shows hand-tracking section (blocked until FAIL §C12 fixed)

---

## 3. Cross-Reference Checks

- Goal §2.1 ↔ modality gate: PASS
- Goal §2.3 ↔ aim-freeze + pinch hysteresis: PASS
- Goal §2.4 ↔ microgesture dispatch: PASS
- Goal §2.5 ↔ `XR_EVT_DOUBLE_PINCH`: PASS
- §3.5 cheatsheet ↔ `VrCheatsheetOverlayManager.buildContent()`: **FAIL**

---

## 4. Action Items (FAIL + WARN, priority order)

1. **[FAIL §C12 — §3.5 UX]** `VrCheatsheetOverlayManager.buildContent()` missing hand-tracking section. Add string resources (`vr_cheatsheet_section_hands`, `vr_cheatsheet_hands_pinch`, `vr_cheatsheet_hands_swipe`, `vr_cheatsheet_hands_double_pinch`) in EN/RU/UK `strings.xml`, then append section call to `buildContent()`.
2. **[WARN §2.4 — Catalog]** `VrHandRayManager` not in `app_v2.jsonl` — `set.ps1 -Class VrHandRayManager -Role "Layer E pointer bridge: NDC hand-ray → Android MotionEvent" -Status new`.
3. **[WARN §2.4 — Catalog]** `XrInputSource` (object in `XrInputEventType.kt`) not cataloged — add entry via `set.ps1`.
