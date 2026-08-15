# Phase 01 - foundation-xr-input

**Strategic spec:** [`../S0283_vr-immersive-interactive-controls-hud.md`](../S0283_vr-immersive-interactive-controls-hud.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02
**Steps done:** 6 / 6
**Started:** 2026-05-21
**Completed:** 2026-05-21

---

## Objective

Integrate `com.oculus.permission.HAND_TRACKING` permission in the Manifest and prepare dynamic runtime requests. Enable hand tracking, hand interaction, and hand aim extensions (`XR_EXT_hand_tracking`, `XR_EXT_hand_interaction`, `XR_FB_hand_tracking_aim`). Establish the C++ input translation unit `xr_input.cpp`/`h` to dynamically load OpenXR pointers, manage left/right trackers, and register Oculus Touch Grip and Thumbstick actions symmetrically.

---

## Prerequisites

- [x] Strategic §6.2 research item (AndroidManifest permissions for hand tracking) is Resolved.
- [x] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/vr/AndroidManifest.xml` | Modified | ≤ 100 |
| `app_v2/src/vr/cpp/xr_input.h` | New | ≤ 150 |
| `app_v2/src/vr/cpp/xr_input.cpp` | New | ≤ 500 |
| `app_v2/src/vr/cpp/xr_session.h` | Modified | ≤ 250 |
| `app_v2/src/vr/cpp/xr_session.cpp` | Modified | ≤ 1000 |
| `app_v2/src/vr/cpp/diagnostic_xr_runtime.cpp` | Modified | ≤ 200 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/DiagnosticXrActivity.kt` | Modified | ≤ 600 |

---

## Steps

### Step 01.0 - Declare Quest Hand Tracking Permissions in AndroidManifest

**Files:** `app_v2/src/vr/AndroidManifest.xml`, `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/DiagnosticXrActivity.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Declare the Quest-specific permission `<uses-permission android:name="com.oculus.permission.HAND_TRACKING"/>` and `<uses-feature android:name="oculus.software.handtracking" android:required="false"/>` inside `AndroidManifest.xml` (HorizonOS path). In `DiagnosticXrActivity.kt`, implement runtime prompt logic targeting the `com.oculus.permission.HAND_TRACKING` permission before launching the OpenXR session.

**Verification:**

- `Grep` - `com.oculus.permission.HAND_TRACKING` is declared in `app_v2/src/vr/AndroidManifest.xml`.
- `Grep` - Permission request dispatching for `com.oculus.permission.HAND_TRACKING` is handled in `DiagnosticXrActivity.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-21 - Verification 2/2 PASS. Files: AndroidManifest.xml, DiagnosticXrActivity.kt. Evidence: `com.oculus.permission.HAND_TRACKING` present in manifest and runtime permission request path.

---

### Step 01.1 - Enable Hand Tracking and Interaction Extensions in OpenXR Instance Creation

**Files:** `app_v2/src/vr/cpp/xr_session.cpp`
**Depends on:** Step 01.0

**Prompt for developer:**

> Add `XR_EXT_HAND_TRACKING_EXTENSION_NAME`, `XR_EXT_HAND_INTERACTION_EXTENSION_NAME` (or `"XR_EXT_hand_interaction"`), and `XR_FB_HAND_TRACKING_AIM_EXTENSION_NAME` (or `"XR_FB_hand_tracking_aim"`) to the vector of requested extensions inside instance initialization inside `xr_session.cpp`. This declaration enables joint posing, unified cross-vendor pinch actions, and aim-poses in the runtime.

**Verification:**

- `Grep` - `XR_EXT_HAND_TRACKING_EXTENSION_NAME` is used in `app_v2/src/vr/cpp/xr_session.cpp`.
- `Grep` - `XR_EXT_hand_interaction` or the corresponding macro is used in `app_v2/src/vr/cpp/xr_session.cpp`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-21 - Verification 2/2 PASS. Files: xr_session.cpp. Evidence: `XR_EXT_HAND_TRACKING_EXTENSION_NAME` and `XR_EXT_hand_interaction` present in OpenXR extension list.

---

### Step 01.2 - Resolve Dynamic Function Pointers for Hand Tracking

**Files:** `app_v2/src/vr/cpp/xr_input.cpp`
**Depends on:** Step 01.1

**Prompt for developer:**

> Create the new input module `xr_input.cpp` and `xr_input.h`. Declare function pointers `xrCreateHandTrackerEXT`, `xrDestroyHandTrackerEXT`, and `xrLocateHandJointsEXT`. Dynamically resolve these dynamic OpenXR function pointers using `xrGetInstanceProcAddr()` after instance initialization.

**Verification:**

- `Grep` - `xrGetInstanceProcAddr` is called for `xrCreateHandTrackerEXT` in `app_v2/src/vr/cpp/xr_input.cpp`.
- `Grep` - `xrGetInstanceProcAddr` is called for `xrLocateHandJointsEXT` in `app_v2/src/vr/cpp/xr_input.cpp`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-21 - Verification 2/2 PASS. Files: xr_input.cpp, xr_input.h. Evidence: `xrGetInstanceProcAddr` resolves `xrCreateHandTrackerEXT` and `xrLocateHandJointsEXT`.

---

### Step 01.3 - Initialize and Teardown Left and Right Hand Trackers

**Files:** `app_v2/src/vr/cpp/xr_input.cpp`, `app_v2/src/vr/cpp/xr_input.h`
**Depends on:** Step 01.2

**Prompt for developer:**

> Declare `XrHandTrackerEXT g_leftHandTracker` and `g_rightHandTracker` variables inside the translation unit. Inside `xr_input_init()`, invoke `xrCreateHandTrackerEXT` for left/right hands. In `xr_input_shutdown()`, clean up these resources by calling `xrDestroyHandTrackerEXT`. Integrate these calls into `xr_session`'s lifecycle.

**Verification:**

- `Grep` - `xrCreateHandTrackerEXT` is invoked in `app_v2/src/vr/cpp/xr_input.cpp`.
- `Grep` - `xrDestroyHandTrackerEXT` is invoked in `app_v2/src/vr/cpp/xr_input.cpp`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-21 - Verification 2/2 PASS. Files: xr_input.cpp, xr_input.h. Evidence: left/right hand trackers are created and destroyed through `xrCreateHandTrackerEXT` / `xrDestroyHandTrackerEXT`.

---

### Step 01.4 - Bind Oculus Touch Grip and Thumbstick Actions Symmetrically

**Files:** `app_v2/src/vr/cpp/xr_input.cpp`
**Depends on:** Step 01.3

**Prompt for developer:**

> Define OpenXR action handles `gripAction` (for panel dragging) and `thumbstickAction` (for volume and 3D-depth slider control). Register and bind these actions symmetrically to `/user/hand/left/input/squeeze/value` and `/user/hand/right/input/squeeze/value` (for grip), and `/user/hand/left/input/thumbstick` and `/user/hand/right/input/thumbstick` (for thumbsticks) to support swap capability for dominant hands (Dominant Hand Swap). Symmetrically bind both, but assign the active parameter control (volume/stereo-depth adjusters) to the right thumbstick by default in accordance with strategic §2.7.

**Verification:**

- `Grep` - `gripAction` declaration or usage exists inside `app_v2/src/vr/cpp/xr_input.cpp`.
- `Grep` - `thumbstickAction` declaration or usage exists inside `app_v2/src/vr/cpp/xr_input.cpp`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-21 - Verification 2/2 PASS. Files: xr_input.cpp. Evidence: `gripAction` and `thumbstickAction` are declared and bound symmetrically.

---

### Step 01.5 - Extract Action Wiring and Hand-Tracker Pointers Out of `xr_session.cpp`

**Files:** `app_v2/src/vr/cpp/xr_session.cpp`, `app_v2/src/vr/cpp/xr_session.h`, `app_v2/src/vr/cpp/xr_input.cpp`, `app_v2/src/vr/cpp/xr_input.h`, `app_v2/src/vr/cpp/diagnostic_xr_runtime.cpp`
**Depends on:** Step 01.4

**Prompt for developer:**

> The earlier rounds of Steps 01.1..01.4 left **duplicate file-scope symbols** in both `xr_session.cpp` and `xr_input.cpp` (PFN_* hand-tracking function pointers, `gripAction`/`thumbstickAction` declarations, action-binding suggestProfile blocks, `createActions()` body). Linker either rejects the duplicates as "multiple definition of …" or quietly merges them into a single global with non-deterministic init order. Resolve once: keep all hand-tracking pointers, action handles, and `createActions()` exclusively inside `xr_input.{h,cpp}`; in `xr_session.cpp` delete the duplicates and call the new `xr_input_init(instance, session)` / `xr_input_poll(...)` / `xr_input_shutdown()` entry points from the session lifecycle. `xr_session.h` exposes whatever minimal surface `xr_input` still needs from the session (e.g. handles for `XrInstance` / `XrSession` if not passed by parameter). Re-test `noLegalDebug` build to confirm no duplicate-symbol diagnostics and no broken JNI bindings.

**Verification:**

- `Grep` - `pfnCreateHandTrackerEXT`, `pfnDestroyHandTrackerEXT`, `pfnLocateHandJointsEXT` appear **only** in `app_v2/src/vr/cpp/xr_input.cpp` (zero hits in `xr_session.cpp`).
- `Grep` - `gripAction`, `thumbstickAction`, `createActions(` definitions appear **only** in `app_v2/src/vr/cpp/xr_input.cpp` (zero hits in `xr_session.cpp`).
- `Command` - `pwsh -NoProfile -File ./a.ps1 nd` (assembleNoLegalDebug) returns exit code 0; no `multiple definition` warning in build log.
- `Command` - LOC of `app_v2/src/vr/cpp/xr_session.cpp` is ≤ 1000.

**Status:** `[x] done`

**Step Log:**

- 2026-05-21 - Verification 4/4 PASS. Files: xr_session.cpp, xr_input.cpp, diagnostic_xr_runtime.cpp. Evidence: input-only symbols appear only in `xr_input.cpp`; `xr_session.cpp` LOC is 1000; `pwsh -NoProfile -File ./a.ps1 nd` exit code 0.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles successfully - `pwsh -NoProfile -File ./a.ps1 nd` exit code 0.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entries added for all changed/new C++ files, AndroidManifest, and Activity via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

OpenXR instance dynamically queries and enables hand tracking, interaction, and aim extensions, manages tracking lifecycles in the input module `xr_input.cpp`, and binds touch controller actions symmetrically to support seamless dominant-hand swops. Manifest permissions and runtime permission prompt are successfully configured.

---

## Rollback Plan

Revert phase commit(s) - no data migration or user-facing surface changed.
