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
| PASS | 12 |
| WARN | 2 |
| FAIL | 4 |
| MANUAL | 1 |
| EXEMPT | 0 |

Core implementation is complete and functional: all three OpenXR extensions requested, `HandSystem` / `initHandTracking` / `syncHandTracking` in `OpenXrNative.cpp`, controller modality priority gate, pinch hysteresis + aim-freeze, microgestures, double-pinch, `VrControllerInputManager` with `SOURCE_HAND`, `VrHandRayManager`, audio feedback, and manifest permission all verified. Four items remain unresolved: trilingual feature documentation (3 files) and the hand-tracking section of `VrCheatsheetOverlayManager` specified in §3.5.

---

## 2. Strategic Audit

### 2.1 Goals Coverage (§2.1)

| # | Goal | Key artefacts | Status | Evidence |
|---|------|---------------|:------:|----------|
| 1 | Automatic Modality Switching | `syncHandTracking` modality gate, `kControllerIdleSwitchNs` | PASS | `OpenXrNative.cpp:1863–1886` |
| 2 | Raycast Targeting | `XrHandTrackingAimStateFB`, `emitPointerMove`, `VrHandRayManager` | PASS | `OpenXrNative.cpp:1982–2016`, `VrHandRayManager.kt` |
| 3 | Pinch-to-Click | Pinch hysteresis (0.9/0.6), aim-freeze, `XR_EVT_POINTER_CLICK_UP/DOWN` | PASS | `OpenXrNative.cpp:2021–2055`, `VrHandRayManager.kt:89–108` |
| 4 | Microgestures (seek/volume) | `XrHandMicrogestureFlagsMETA` swipe constants, dispatch in `syncHandTracking` | PASS | `OpenXrNative.cpp:129–139`, swipe dispatch block |
| 5 | Double Pinch (toggle play/pause) | `XR_EVT_DOUBLE_PINCH = 23`, `CommandId.VR_DOUBLE_PINCH` | PASS | `XrInputEventType.kt:34`, `VrControllerInputManager.kt:169` |
| 6 | Input Feedback (visual + audio) | Cursor dot in `VrHandRayManager`, `AudioManager.FX_KEY_CLICK`; cheatsheet section **missing** | PARTIAL | Audio PASS; hand-tracking cheatsheet FAIL (§3.5) |

### 2.2 Constraints (§3 Technical Requirements)

| # | Constraint | Status | Evidence | Action |
|---|-----------|:------:|----------|--------|
| C1 | `XR_EXT_hand_tracking` registered at instance creation | PASS | `OpenXrNative.cpp:33` + extension list |
| C2 | `XR_META_hand_tracking_aim` registered | PASS | `OpenXrNative.cpp:36` |
| C3 | `XR_META_hand_tracking_microgestures` registered | PASS | `OpenXrNative.cpp:42` |
| C4 | `XrHandTrackerEXT` for L + R hands | PASS | `initHandTracking()` L:1813 R:1814 |
| C5 | Per-frame `xrLocateHandJointsEXT` + `XrHandTrackingAimStateFB` | PASS | `syncHandTracking` :1908–1938 |
| C6 | Controller strict priority (§3.3) | PASS | `kControllerIdleSwitchNs` gate, dangling-click release :1873–1886 |
| C7 | `VrControllerInputManager` extended with `source: Int` | PASS | `onInputEvent(type,hand,value,source)` :77 |
| C8 | `XrInputCallback` updated with `source` param | PASS | `XrInputCallback.kt:27` |
| C9 | `com.oculus.permission.HAND_TRACKING` in VR manifest | PASS | `AndroidManifest.xml:121` |
| C10 | `VrHandRayManager` translates NDC → `MotionEvent` | PASS | `VrHandRayManager.kt:46–108` |
| C11 | Audio feedback via `AudioManager` for hand events | PASS | `VrControllerInputManager.kt:122` |
| C12 | Hand-tracking cheatsheet section in overlay (§3.5) | **FAIL** | `VrCheatsheetOverlayManager.buildContent()` has no hands section; no `vr_cheatsheet_section_hands` strings |

### 2.3 Open Research Items

None defined in this spec format.

### 2.4 User-Facing Text (FEATURES trilingual)

| Artefact | Status | Evidence | Action |
|---------|:------:|----------|--------|
| `docs/FEATURES.md` | **FAIL** | No match for `hand.track\|HandTrack` | Add VR hand tracking bullet |
| `docs/FEATURES_RU.md` | **FAIL** | No match | Add RU mirror bullet |
| `docs/FEATURES_UK.md` | **FAIL** | No match | Add UK mirror bullet |

### 2.5 Catalogue Coverage

| Class | File | In catalog | Status | Action |
|-------|------|:----------:|:------:|--------|
| `VrHandRayManager` | `vr/ui/VrHandRayManager.kt` | No | **WARN** | Run `scan.ps1`, set role/status via `set.ps1` |
| `XrInputSource` | `vr/openxr/XrInputEventType.kt` (object) | No | **WARN** | Add entry to `app_v2.jsonl` |

### 2.6 Manual Acceptance Signals

- [ ] **MANUAL** — Quest 3 device test: controller → put down controllers → hand tracking auto-activates, ray cursor appears, pinch triggers playback click.
- [ ] **MANUAL** — Quest 3 device test: double pinch toggles play/pause; thumb swipes seek/volume; cursor disappears when controllers resume.
- [ ] **MANUAL** — Cheatsheet overlay includes hand-tracking section (blocked until FAIL §C12 fixed).

---

## 3. Cross-Reference Checks

- Goal §2.1 (modality switching) ↔ `syncHandTracking` + `kControllerIdleSwitchNs` — PASS.
- Goal §2.3 (pinch-to-click) ↔ `onPointerClick` + aim-freeze — PASS.
- Goal §2.4 (microgestures) ↔ `XrHandMicrogestureFlagsMETA` swipe dispatch — PASS.
- Goal §2.5 (double pinch) ↔ `XR_EVT_DOUBLE_PINCH` + `CommandId.VR_DOUBLE_PINCH` — PASS.
- §3.5 (cheatsheet) ↔ `VrCheatsheetOverlayManager.buildContent()` — **FAIL** (no hand section).

---

## 4. Action Items (FAIL + WARN, priority order)

1. **[FOLLOW-UP §C12 — §3.5 UX]** `VrCheatsheetOverlayManager.buildContent()` has no hand-tracking section. Add string resources (`vr_cheatsheet_section_hands`, `vr_cheatsheet_hands_*`) in EN/RU/UK and append section to `buildContent()`.
2. **[FIXED §2.4 — FEATURES.md]** VR hand tracking bullet added to `docs/FEATURES.md` (line 162).
3. **[FIXED §2.4 — FEATURES_RU.md]** TODO translate placeholder added.
4. **[FIXED §2.4 — FEATURES_UK.md]** TODO translate placeholder added.
5. **[PARTIAL §2.5 — Catalog]** `VrHandRayManager` — `scan.ps1` ran but `src/vr/` outside scan scope; use `set.ps1` manually (see fix log Follow-up 2).
6. **[PARTIAL §2.5 — Catalog]** `XrInputSource` (object in `XrInputEventType.kt`) — same scope issue; add manually.
