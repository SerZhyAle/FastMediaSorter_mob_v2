# Spec Audit: vr-hand-tracking

**Strategic spec:** [`spec_vr-hand-tracking.md`](spec_vr-hand-tracking.md)
**Tactical plan:** n/a (no `spec_vr-hand-tracking/INDEX.md`)
**Audit date:** 2026-04-26
**Mode:** strategic
**Flags:** —
**Outcome:** Partial

---

## 1. Summary

| Metric | Count |
|--------|------:|
| Checks total | 19 |
| PASS | 17 |
| WARN | 2 |
| FAIL | 0 |
| MANUAL | 1 |
| EXEMPT | 0 |

All implementation goals are verified: OpenXR extensions, hand-tracking pipeline (C++), modality switching, pinch/microgesture/double-pinch dispatch, `VrHandRayManager` NDC→MotionEvent bridge, audio feedback, manifest permission, cheatsheet hand-tracking section (EN/RU/UK strings + `buildContent()` update), and trilingual FEATURES coverage. Two WARNs remain: `VrHandRayManager` and `XrInputSource` are not in `dev/CATALOG/app_v2.jsonl` because `scan.ps1` does not cover `src/vr/`. Device acceptance tests are manual.

---

## 2. Strategic Audit

### 2.1 Goals Coverage (§2.1)

| # | Goal | Key artefacts | Status | Evidence |
|---|------|---------------|:------:|----------|
| 1 | Automatic Modality Switching | `syncHandTracking` gate, `kControllerIdleSwitchNs` | PASS | `OpenXrNative.cpp:1863–1886` |
| 2 | Raycast Targeting | `XrHandTrackingAimStateFB`, `VrHandRayManager` | PASS | `OpenXrNative.cpp:1982–2016`, `VrHandRayManager.kt` |
| 3 | Pinch-to-Click | Hysteresis 0.9/0.6, aim-freeze, `XR_EVT_POINTER_CLICK_UP/DOWN` | PASS | `OpenXrNative.cpp:2021–2055` |
| 4 | Microgestures (seek/volume) | `XrHandMicrogestureFlagsMETA` swipe dispatch | PASS | `OpenXrNative.cpp:129–139` |
| 5 | Double Pinch (toggle play/pause) | `XR_EVT_DOUBLE_PINCH = 23`, `CommandId.VR_DOUBLE_PINCH` | PASS | `XrInputEventType.kt:34` |
| 6 | Input Feedback — audio | `AudioManager.FX_KEY_CLICK` | PASS | `VrControllerInputManager.kt:122` |
| 6 | Input Feedback — visual cheatsheet | `vr_cheatsheet_section_hands` + 3 additional keys; `buildContent()` updated | PASS | `VrCheatsheetOverlayManager.kt:158`, `values/strings.xml:2797` |

### 2.2 Constraints (§3)

| # | Constraint | Status | Evidence |
|---|-----------|:------:|----------|
| C1 | `XR_EXT_hand_tracking` registered | PASS | `OpenXrNative.cpp:33` |
| C2 | `XR_META_hand_tracking_aim` registered | PASS | `OpenXrNative.cpp:36` |
| C3 | `XR_META_hand_tracking_microgestures` registered | PASS | `OpenXrNative.cpp:42` |
| C4 | `XrHandTrackerEXT` L + R | PASS | `initHandTracking():1813–1814` |
| C5 | Per-frame aim + microgesture polling | PASS | `syncHandTracking():1908–1938` |
| C6 | Controller strict priority | PASS | `kControllerIdleSwitchNs` gate |
| C7 | `VrControllerInputManager` + `source: Int` | PASS | `onInputEvent(type,hand,value,source):77` |
| C8 | `XrInputCallback` updated | PASS | `XrInputCallback.kt:27` |
| C9 | `com.oculus.permission.HAND_TRACKING` | PASS | `AndroidManifest.xml:121` |
| C10 | `VrHandRayManager` NDC→MotionEvent | PASS | `VrHandRayManager.kt:46–108` |
| C11 | Audio feedback via `AudioManager` | PASS | `VrControllerInputManager.kt:122` |
| C12 | Hand-tracking cheatsheet section (§3.5) | PASS | `VrCheatsheetOverlayManager.kt:158`, EN/RU/UK `vr_cheatsheet_section_hands` |

### 2.3 User-Facing Text (FEATURES trilingual)

| Artefact | Status | Evidence |
|---------|:------:|----------|
| `docs/FEATURES.md` | PASS | line 162: `**VR hand tracking**:` |
| `docs/FEATURES_RU.md` | PASS | line 148: TODO translate placeholder contains keyword |
| `docs/FEATURES_UK.md` | PASS | line 148: TODO translate placeholder contains keyword |

### 2.4 Catalogue Coverage

| Class | Status | Note |
|-------|:------:|------|
| `VrHandRayManager` | **WARN** | Not in `app_v2.jsonl`; `src/vr/` outside `scan.ps1` scope |
| `XrInputSource` | **WARN** | Object in `XrInputEventType.kt`; not in catalog |

### 2.5 Manual Acceptance Signals

- [ ] **MANUAL** — Quest 3: controllers down → hand tracking auto-activates; cursor dot appears; pinch triggers click; cheatsheet shows hand section
- [ ] **MANUAL** — Double pinch toggles play/pause; thumb swipes adjust seek/volume; controllers resume instantly

---

## 3. Cross-Reference Checks

- §2.1–§2.6 Goals ↔ implementation: all PASS
- §3.5 cheatsheet ↔ `VrCheatsheetOverlayManager.buildContent()`: PASS

---

## 4. Action Items (WARN only — no FAIL)

1. **[WARN §2.4 — Catalog]** `VrHandRayManager` not in `app_v2.jsonl` — extend `scan.ps1` to include `src/vr/` or add manually via `set.ps1 -Class VrHandRayManager -Role "Layer E pointer bridge: NDC hand-ray → Android MotionEvent" -Status new`.
2. **[WARN §2.4 — Catalog]** `XrInputSource` (object in `XrInputEventType.kt`) not cataloged — add entry manually.
