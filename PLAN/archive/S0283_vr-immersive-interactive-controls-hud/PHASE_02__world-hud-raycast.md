# Phase 02 - world-hud-raycast

**Strategic spec:** [`../S0283_vr-immersive-interactive-controls-hud.md`](../S0283_vr-immersive-interactive-controls-hud.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 5 / 5
**Started:** 2026-05-21
**Completed:** 2026-05-21

---

## Objective

Transition HUD rendering to a World Space 3D Quad, implement controller/hand ray calculations, compute UV intersection coordinates (Ray-to-Quad math), apply exponential smoothing (Ray Jitter Filter), and render the physical pointer rays, utilizing the specialized translation units `xr_raycast.cpp` and `xr_hud_world.cpp` to ensure rigid LOC limits.

---

## Prerequisites

- [x] Phase 01 is ✅ Done.
- [x] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/vr/cpp/CMakeLists.txt` | Modified | ≤ 80 |
| `app_v2/src/vr/cpp/xr_raycast.h` | New | ≤ 150 |
| `app_v2/src/vr/cpp/xr_raycast.cpp` | New | ≤ 350 |
| `app_v2/src/vr/cpp/xr_hud_world.h` | New | ≤ 150 |
| `app_v2/src/vr/cpp/xr_hud_world.cpp` | New | ≤ 450 |
| `app_v2/src/vr/cpp/xr_session.cpp` | Modified | ≤ 1000 |

---

## Steps

### Step 02.0 - Register New Translation Units in CMake

**Files:** `app_v2/src/vr/cpp/CMakeLists.txt`, `app_v2/src/vr/cpp/xr_raycast.h`, `app_v2/src/vr/cpp/xr_raycast.cpp`, `app_v2/src/vr/cpp/xr_hud_world.h`, `app_v2/src/vr/cpp/xr_hud_world.cpp`
**Depends on:** - start of phase

**Prompt for developer:**

> Ensure both `xr_raycast.{h,cpp}` and `xr_hud_world.{h,cpp}` exist (at minimum as `namespace fms::xr { }` skeletons — Steps 02.1..02.4 fill the bodies) and are registered inside `add_library(fms_diagnostic_xr SHARED ...)` in `app_v2/src/vr/cpp/CMakeLists.txt`. If either file is missing, create the stub now; if `xr_session.cpp` already invokes symbols from them (e.g. `xr_hud_render`, `xr_hud_shutdown`), provide matching empty no-op definitions in the stub `.cpp` so the link succeeds before Step 02.1 begins. Run `pwsh -NoProfile -File ./a.ps1 nd` and confirm the noLegal native target links cleanly.

**Verification:**

- `Grep` - `xr_raycast.cpp` and `xr_hud_world.cpp` are listed inside `add_library(fms_diagnostic_xr SHARED ...)` in `app_v2/src/vr/cpp/CMakeLists.txt`.
- `Command` - `pwsh -NoProfile -File ./a.ps1 nd` returns exit code 0 (no `undefined symbol` errors).

**Status:** `[x] done`

**Step Log:**

- 2026-05-21 - Verification 2/2 PASS. Files: CMakeLists.txt, xr_raycast.*, xr_hud_world.*. Evidence: new translation units listed in CMake; `pwsh -NoProfile -File ./a.ps1 nd` exit code 0.

---

### Step 02.1 - Render HUD on World Space 3D Quad with Lazy-Follow

**Files:** `app_v2/src/vr/cpp/xr_hud_world.cpp`, `app_v2/src/vr/cpp/xr_hud_world.h`
**Depends on:** Step 02.0

**Prompt for developer:**

> Transition the HUD rendering from flat screen space to world space using the camera's `viewMat` inside the rendering routines in `xr_hud_world.cpp`. Implement a gaze-follow smoothing algorithm using exponential interpolation for the HUD's world transformation matrix to keep it 1.5–2.0 meters away from the viewer.

**Verification:**

- `Grep` - The camera's view matrix is applied to the HUD's transformation matrix in `app_v2/src/vr/cpp/xr_hud_world.cpp`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-21 - Verification 1/1 PASS. Files: xr_hud_world.cpp, xr_hud_world.h. Evidence: HUD world transform is multiplied through the camera view matrix.

---

### Step 02.2 - Compute Ray-to-Quad Interaction Math

**Files:** `app_v2/src/vr/cpp/xr_raycast.cpp`, `app_v2/src/vr/cpp/xr_raycast.h`
**Depends on:** Step 02.1

**Prompt for developer:**

> Write the mathematical calculations for intersecting a 3D ray with the flat 3D plane of the HUD Quad inside `xr_raycast.cpp`. The ray is defined by the origin and direction of the active controller or the hand index finger aim space obtained from `XR_EXT_hand_interaction` `/input/aim_ext/pose` (or `XR_FB_hand_tracking_aim` aim-pose). Project the intersection point into normalized UV coordinates spanning from $(0.0, 0.0)$ to $(1.0, 1.0)$.

**Verification:**

- `Grep` - Plane intersection calculation exists in `app_v2/src/vr/cpp/xr_raycast.cpp`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-21 - Verification 1/1 PASS. Files: xr_raycast.cpp, xr_raycast.h. Evidence: `ray_quad_intersect` implements ray-to-plane intersection and UV projection.

---

### Step 02.3 - Implement Exponential Ray Jitter Filtering

**Files:** `app_v2/src/vr/cpp/xr_raycast.cpp`
**Depends on:** Step 02.2

**Prompt for developer:**

> Incorporate an exponential moving average filter ($UV_{smooth} = \alpha UV_{new} + (1 - \alpha) UV_{old}$) with $\alpha = 0.25$ inside the frame loop inside `xr_raycast.cpp` to filter out micro-tremor and coordinate jitter before transmitting coordinates up to Kotlin.

**Verification:**

- `Grep` - Smoothing logic or dampening variables are applied to UV coordinates in `app_v2/src/vr/cpp/xr_raycast.cpp`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-21 - Verification 1/1 PASS. Files: xr_raycast.cpp. Evidence: `filter_uv_jitter` applies exponential smoothing with alpha support.

---

### Step 02.4 - Render Visual Laser Rays and Cursor Dots

**Files:** `app_v2/src/vr/cpp/xr_hud_world.cpp`
**Depends on:** Step 02.3

**Prompt for developer:**

> Compile a basic shader for drawing 3D lines. Draw a fading visual laser ray originating from the controller/hand and terminating near the HUD in `xr_hud_world.cpp`. Render a tiny visual cursor circle at the smoothed UV intersection point directly on the C++ thread to guarantee zero-latency cursor feedback (ADR-1).

**Verification:**

- `Grep` - Laser line rendering or cursor dot drawing function is present in `app_v2/src/vr/cpp/xr_hud_world.cpp`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-21 - Verification 1/1 PASS. Files: xr_hud_world.cpp. Evidence: laser line rendering and cursor dot drawing are present.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles successfully - `pwsh -NoProfile -File ./a.ps1 nd` exit code 0.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entries added for all changed/new C++ files via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

HUD is rendered in 3D world space with gazekept lazy-follow. Rays and cursor intersections are computed in the dedicated modules `xr_raycast.cpp` and `xr_hud_world.cpp` with exponential tremor filtration and rendered in GLES with zero latency.

---

## Rollback Plan

Revert phase commit(s) - no data migration or user-facing surface changed.
