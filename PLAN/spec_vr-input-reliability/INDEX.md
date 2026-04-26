# Tactical Index: vr-input-reliability

**Strategic spec:** [../spec_vr-input-reliability.md](../spec_vr-input-reliability.md)
**Status:** Verified
**Last updated:** 2026-04-26

---

## Pre-Implementation Blockers

None. Research items from §6 resolved inline:

- **Haptic path (§6.1):** `meta/touch_plus_controller` is missing from the ActionSet bindings. Quest 3 uses this profile, not `oculus/touch_pro_controller`. Adding it eliminates XR_ERROR_FEATURE_UNSUPPORTED (-16).
- **Cinema UX (§6.2):** Automatic without prompt — owner preference §3.1.1. Show brief toast "Cinema mode". No dialog.
- **Debounce for continuous commands (§6.3):** Toggle commands (play/pause, open-file) get 500 ms window; volume (already 150 ms rate-limited) and zoom (120 ms rate-limited) are excluded from the new debouncer.

---

## Phases

| # | Slug | Status | Steps |
| - | ---- | ------ | ----- |
| 01 | cinema-fallback | Implemented | 3 |
| 02 | command-debounce | Implemented | 3 |
| 03 | native-openxr-fixes | Implemented | 3 |
| 04 | hud-lifecycle-guard | Implemented | 1 |

**Implementation order:** phases are independent; implement 01 → 02 → 03 → 04.

---

## Phase Done Criteria (summary)

- **Phase 01:** `CINEMA_IMMERSIVE` route exists; plain-2D VIDEO enters QUAD_CINEMA layer; XR session not destroyed.
- **Phase 02:** `VrCommandDebouncer` class exists; toggle commands are deduplicated; volume/zoom unaffected.
- **Phase 03:** `meta/touch_plus_controller` in ActionSet bindings; INTERACTION_PROFILE_CHANGED (type 52) handled; Touch Pro failure logged at DEBUG level.
- **Phase 04:** `setHudLayerVisible()` guarded with session-running check; no swapchain W-logs after XR shutdown.
