# Phase 07 - Equirect2 Layer + FFR Experiment (Feature-Flagged)

**Strategic spec:** [`../S0290_vr_test_quality_overhaul.md`](../S0290_vr_test_quality_overhaul.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 06
**Blocks:** Phase 08
**Steps done:** 0 / 4
**Started:** -
**Completed:** -

---

## Objective

Add an opt-in compositor-side rendering path using `XR_KHR_composition_layer_equirect2` for 360°-projection material, and enable Fixed Foveated Rendering via `XR_FB_foveation`. Both are feature-flagged in native — they can be A/B-tested against the Phase 06 baseline without removing the sphere-mesh path.

---

## Prerequisites

- [ ] Phase 06 ✅ Done.
- [ ] Strategic §6.2 (Equirect2 layer) — Resolved. Owner decided default flag value (default ON / OFF / try-and-see).
- [ ] Strategic §6.3 (FFR preset) — Resolved. Owner picked Medium / High / off.
- [ ] Working tree is clean or on a feature branch.
- [ ] Quest 3 device available for bench comparison.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/vr/cpp/xr_session.cpp` | Modified | ≤ 1500 (current 1275 + Phase 01 + Phase 06 + Phase 07 delta). If exceeds 1500 — split via the Manager pattern first (out-of-scope; flag in Blockers Log). |

> Backup at start of this phase (cumulative changes are now substantial).

---

## Steps

### Step 07.1 - Backup xr_session.cpp before Phase 07 edits

**Files:** `temp/xr_session.cpp.*.bak`
**Depends on:** start of phase

**Prompt for developer:**

> Fresh timestamped copy to `temp/`. Verify LOC budget — if file would exceed 1500 LOC after Phase 07 edits, stop and split out Equirect2 / FFR logic into a sibling `.cpp` (e.g. `xr_session_compositor.cpp`) via the Manager pattern before continuing.

**Verification:**

- `Glob` - `temp/xr_session.cpp.*.bak` returns one match newer than Phase 06 backup.
- Manual: `wc -l xr_session.cpp` reports < 1300 (room for ~200 LOC of Phase 07 additions before split is needed).

**Status:** `[ ]` not done

---

### Step 07.2 - Add native feature flags for Equirect2 + FFR

**Files:** `app_v2/src/vr/cpp/xr_session.cpp`
**Depends on:** Step 07.1

**Prompt for developer:**

> Add two `static const bool` flags at the top of `xr_session.cpp` (after includes, before `State` struct): `kUseEquirect2Layer` (default per §6.2 resolution) and `kUseFFR` with `kFfrLevel` enum (default per §6.3 resolution). These are compile-time constants — no runtime UI to toggle them. Document in a comment block above the flags that they are A/B benchmark switches and the chosen default values were decided in S0290 §6.2 / §6.3. At runtime, gate both flags by actual extension availability as well. Log the active flag values at session start via a neutral native log line (for example, `DiagnosticXrSession: feature flags: equirect2=%d ffr=%d ffr_level=%d`).

**Verification:**

- `Grep` - `kUseEquirect2Layer` matches at least three times in `xr_session.cpp` (declaration + at least two reads in conditional branches).
- `Grep` - `kUseFFR` matches at least three times.
- `Grep` - `feature flags: equirect2` matches exactly once in `xr_session.cpp`.

**Status:** `[ ]` not done

---

### Step 07.3 - Implement Equirect2 layer path for 360-mode

**Files:** `app_v2/src/vr/cpp/xr_session.cpp`
**Depends on:** Step 07.2

**Prompt for developer:**

> When `kUseEquirect2Layer && g.renderProjection == 0` (SPHERE_360 mode), instead of rendering the sphere mesh into the eye swapchain in `renderEye`, submit an `XrCompositionLayerEquirect2KHR` directly to the compositor. Reference docs: <https://registry.khronos.org/OpenXR/specs/1.0/man/html/XR_KHR_composition_layer_equirect2.html>. Required setup:
> - Check `XR_KHR_composition_layer_equirect2` extension availability during instance creation (extend the existing extension request list). If unavailable, keep the sphere baseline and record the skip in bench notes.
> - Allocate and manage a **dedicated swapchain** for the equirect media texture. Do **not** assume a raw GL texture like `g.texture` can be submitted directly to an OpenXR composition layer; the layer must reference a valid swapchain sub-image.
> - Build `XrCompositionLayerEquirect2KHR` with the right pose (origin at viewer head), radius, and central horizontal/vertical angles (`-PI..PI`, `-PI/2..PI/2` for full sphere).
> - In the frame submission, replace the 360° background part of `XrCompositionLayerProjection` with the equirect layer when the flag and projection are both active. Keep the projection layer for non-360 paths.
> - The eye-buffer rendering for HUD is still needed — combine projection layer (HUD only) + equirect layer (background) by submitting two layers, equirect first.
>
> For stereo TB material rendered through Equirect2: submit **two** equirect layers or one per-eye sub-image configuration, depending on what keeps the code smallest and most correct with the chosen swapchain layout. Each eye path must reference the correct half of the swapchain image and carry `eyeVisibility = XR_EYE_VISIBILITY_LEFT/RIGHT`. The goal is to eliminate the manual UV-shift in the shader entirely for the 360-mode case.

**Verification:**

- `Grep` - `XR_KHR_composition_layer_equirect2` matches at least twice in `xr_session.cpp` (extension request + struct usage).
- `Grep` - `XR_TYPE_COMPOSITION_LAYER_EQUIRECT2_KHR` matches at least once.
- `Grep` - `XR_EYE_VISIBILITY_LEFT` matches at least once.
- On-device: with `kUseEquirect2Layer = true` and the extension available, 360° material renders with visibly sharper polar regions than sphere-mesh path; stereo-TB material shows correct eye separation without shader involvement. If the extension is unavailable, the skip is logged and the sphere baseline remains functional.

**Status:** `[ ]` not done

---

### Step 07.4 - Implement FFR via XR_FB_foveation

**Files:** `app_v2/src/vr/cpp/xr_session.cpp`
**Depends on:** Step 07.3

**Prompt for developer:**

> When `kUseFFR` is true, apply Fixed Foveated Rendering to all eye-buffer swapchains after creation. Steps:
> - Extend extension request list with `XR_FB_foveation`, `XR_FB_foveation_configuration`, `XR_FB_swapchain_update_state`.
> - Load function pointers `xrCreateFoveationProfileFB`, `xrDestroyFoveationProfileFB`, `xrUpdateSwapchainFB` via `xrGetInstanceProcAddr`.
> - After creating each eye swapchain, build an `XrFoveationLevelProfileCreateInfoFB { level = kFfrLevel, dynamic = XR_FALSE }` and apply via `xrUpdateSwapchainFB`.
> - `kFfrLevel` is one of `XR_FOVEATION_LEVEL_NONE_FB`, `_LOW_FB`, `_MEDIUM_FB`, `_HIGH_FB` per §6.3 resolution.
>
> Reference: <https://developers.meta.com/horizon/documentation/native/android/mobile-foveated-rendering/>. If any of the FB extensions are unavailable on the device runtime, log a neutral warning and skip FFR — degrades to no foveation, app still works.

**Verification:**

- `Grep` - `XR_FB_foveation` matches at least twice in `xr_session.cpp`.
- `Grep` - `xrCreateFoveationProfileFB` matches at least once.
- `Grep` - `xrUpdateSwapchainFB` matches at least once.
- On-device: VrApi log shows `Fov=...` higher than baseline (foveation active), GPU% drops by ≥10% on the same 8K equirect material.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 07.*` above is `[x] done`.
- [ ] Project compiles — run `/build` (target: `nd`).
- [ ] `Grep` for `TODO(phase-07)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] Bench results documented in Blockers Log: with `kUseEquirect2Layer = true` vs false, measured FPS / GPU% on bundled lakeside.jpg; with `kUseFFR` at chosen level, measured GPU% delta. If an extension is unavailable, the skip is documented as an accepted outcome, not silent failure.

---

## Handoff Notes to Next Phase

The diagnostic VR stack now has a baseline (sphere mesh) and an opt-in compositor-side path. The feature flags are compile-time; future work could expose them in a debug menu for live A/B. Phase 08 finalises catalog sync and changelog entries.

---

## Rollback Plan

Both Equirect2 and FFR are guarded by their compile-time flags. Setting `kUseEquirect2Layer = false` + `kUseFFR = false` reverts to Phase 06 baseline without code revert. For full revert: restore `xr_session.cpp` from Phase 06 backup.

## Revision History

- **2026-05-22** - by `/spec-update` (`GPT-5.4`, focus: consistency, completeness, verifiability, stability)
	- Applied: 3. Proposed (DISCUSS): 0.
