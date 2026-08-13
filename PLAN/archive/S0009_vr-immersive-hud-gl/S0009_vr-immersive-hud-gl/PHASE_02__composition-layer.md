# Phase 02 — HUD Composition Layer (XrCompositionLayerQuad + swapchain)

**Strategic spec:** [`../spec_vr-immersive-hud-gl.md`](../spec_vr-immersive-hud-gl.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 04, Phase 05, Phase 06, Phase 07
**Steps done:** 0 / 5
**Started:** —
**Completed:** —

---

## Objective

Create a real `XrSwapchain` for the HUD, destroy it on session teardown, and include an `XrCompositionLayerQuad` in `xrEndFrame` — head-locked at bottom-center, 1.0 m × 0.3 m, distance 1.5 m, vertical offset −20° from gaze — whenever `hudLayerVisible` is true. Layer content at the end of this phase is whatever the swapchain image contains (zeros / garbage); visual result is a dark rectangle. No upload path yet.

---

## Prerequisites

Check each before starting Step 1:

- [ ] Phase 01 is `✅ Done`.
- [ ] Strategic spec §6.1 start-default (premultiplied alpha + runtime blend) is captured in the ADR.
- [ ] `OpenXrNative.cpp` freshly backed up to `temp/` for this phase.
- [ ] Developer has a Quest 3 (or another OpenXR runtime with `XrCompositionLayerQuad` support) to run the debug rectangle check.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/vr/cpp/OpenXrNative.cpp` | Modified | ≤ 2800 |

> Single-file phase on the native side. Backup is mandatory (file already > 2500 LOC).

---

## Steps

### Step 2.1 — Backup `OpenXrNative.cpp`

**Files:** `app_v2/src/vr/cpp/OpenXrNative.cpp`
**Depends on:** — start of phase

**Prompt for developer:**

> Run `Copy-Item app_v2/src/vr/cpp/OpenXrNative.cpp temp/OpenXrNative_phase02_$(Get-Date -Format yyyyMMdd_HHmm).cpp.bak` before any edit.

**Verification:**

- `Glob` — `temp/OpenXrNative_phase02_*.cpp.bak` exists.

**Status:** `[x]` done

---

### Step 2.2 — Create the HUD swapchain at session startup

**Files:** `app_v2/src/vr/cpp/OpenXrNative.cpp`
**Depends on:** Step 2.1

**Prompt for developer:**

> Locate `createSessionAndSwapchains()` in `OpenXrNative.cpp`. After the eye swapchains are created and their images enumerated (search anchor: `Eye %d swapchain: %dx%d, %d images  handle=`), add a new block that creates the HUD swapchain:
>
> - Dimensions: use the values stored in `g_ctx.hudSwapchainWidth/Height` if non-zero, otherwise default to `1024` × `256`. Ignore Phase-01 stub values if zero.
> - `XrSwapchainCreateInfo`: `createFlags = 0`, `usageFlags = XR_SWAPCHAIN_USAGE_COLOR_ATTACHMENT_BIT | XR_SWAPCHAIN_USAGE_SAMPLED_BIT`, `format = GL_RGBA8` (use the selected color format constant the eye swapchains use — grep for `GL_SRGB8_ALPHA8` in the file first; if the project uses sRGB for eye swapchains, pick the matching non-sRGB alias that supports premultiplied alpha blending; default to `GL_RGBA8` `0x8058`).
> - `sampleCount = 1`, `arraySize = 1`, `faceCount = 1`, `mipCount = 1`.
> - Enumerate swapchain images via `xrEnumerateSwapchainImages`, store GL texture IDs into `g_ctx.hudSwapchainImageHandles` (same pattern as eye swapchains use).
> - Log via `LOGI("HUD swapchain: %dx%d, %zu images  handle=%p", width, height, count, swapchainHandle)`.
>
> If swapchain creation fails, log a warning but DO NOT fail session bring-up — HUD is optional, video layer must still work.

**Verification:**

- `Grep` — pattern `xrCreateSwapchain` in `OpenXrNative.cpp` returns at least three hits (two eye + one HUD).
- `Grep` — pattern `g_ctx.hudSwapchain = ` returns exactly one hit.
- `Grep` — pattern `HUD swapchain:` returns exactly one hit inside a `LOGI(..)` line.
- `/build` skill compiles `vrDebug` without errors.
- Device test: launching an immersive video session produces the new `HUD swapchain: ..` line in the log; existing video layer is unchanged.

**Status:** `[x]` done

---

### Step 2.3 — Destroy the HUD swapchain on session teardown

**Files:** `app_v2/src/vr/cpp/OpenXrNative.cpp`
**Depends on:** Step 2.2

**Prompt for developer:**

> Locate the `destroyAll()` function and the `TODO(phase-02): xrDestroySwapchain(hudSwapchain)` marker left by Phase 01. Replace the TODO with a guarded destroy block: if `g_ctx.hudSwapchain != XR_NULL_HANDLE` then call `xrDestroySwapchain(g_ctx.hudSwapchain)`, clear `g_ctx.hudSwapchainImageHandles`, reset `hudSwapchainWidth/Height` to zero, reset `hudLayerVisible` to false. Log via `LOGI("HUD swapchain destroyed")` on success or `LOGW("HUD swapchain destroy failed: %d", r)` on error.
>
> Also update the JNI entry `nativeDestroyHudSwapchain` added in Phase 01 Step 1.3 — instead of the stub body, call a shared static helper `destroyHudSwapchain()` that does the same thing (reuse between lifecycle teardown and explicit Kotlin-side destroy).

**Verification:**

- `Grep` — pattern `TODO(phase-02)` in `OpenXrNative.cpp` returns zero hits.
- `Grep` — pattern `xrDestroySwapchain(g_ctx.hudSwapchain)` returns exactly one hit inside the shared helper.
- `Grep` — pattern `HUD swapchain destroyed` returns exactly one hit.
- `/build` skill compiles `vrDebug` without errors.
- Device test: exiting immersive logs `HUD swapchain destroyed`; subsequent re-entry logs a fresh `HUD swapchain: ..` line.

**Status:** `[x]` done

---

### Step 2.4 — Include `XrCompositionLayerQuad` in `xrEndFrame` when HUD is visible

**Files:** `app_v2/src/vr/cpp/OpenXrNative.cpp`
**Depends on:** Step 2.3

**Prompt for developer:**

> Locate the `renderFrame()` function and specifically the section that builds the layer array passed to `xrEndFrame`. Add a new local `XrCompositionLayerQuad hudLayer{XR_TYPE_COMPOSITION_LAYER_QUAD};` constructed as follows when `g_ctx.hudLayerVisible.load()` is true AND `g_ctx.hudSwapchain != XR_NULL_HANDLE`:
>
> - `layerFlags = XR_COMPOSITION_LAYER_BLEND_TEXTURE_SOURCE_ALPHA_BIT` (premultiplied alpha start-default per strategic §6.1).
> - `space = g_ctx.viewSpace` (use the existing view/head-locked reference space handle; grep for `xrCreateReferenceSpace.*VIEW` to confirm variable name — if only `appSpace` exists, create a new `XrReferenceSpace` of type `XR_REFERENCE_SPACE_TYPE_VIEW` during session init and store it alongside `appSpace`).
> - `eyeVisibility = XR_EYE_VISIBILITY_BOTH`.
> - `subImage.swapchain = g_ctx.hudSwapchain`, `subImage.imageRect = {{0,0}, {w, h}}`, `subImage.imageArrayIndex = 0`.
> - `pose.orientation = {x:0, y:0, z:0, w:1}` (identity) rotated around X axis by −20° (use quaternion `qx = sin(-10° in rad), qw = cos(-10° in rad)` — note half-angle for quaternion; verify sign convention gives downward tilt).
> - `pose.position = {x:0, y:0, z:-1.5f}` (1.5 m in front of the head-locked origin).
> - `size = {width: 1.0f, height: 0.3f}` (metres).
>
> Append `reinterpret_cast<const XrCompositionLayerBaseHeader*>(&hudLayer)` to the layer pointer array AFTER the existing video layer. The HUD must draw on top.
>
> Before the `xrEndFrame` call, if `hudLayerVisible && hudSwapchain != XR_NULL_HANDLE`, call `xrAcquireSwapchainImage` + `xrWaitSwapchainImage` + `xrReleaseSwapchainImage` on the HUD swapchain with a null source — this keeps the swapchain image index advancing even if no one uploaded new content this frame (so the compositor shows the last uploaded frame, or zeros on first frame).

**Verification:**

- `Grep` — pattern `XR_TYPE_COMPOSITION_LAYER_QUAD` returns exactly one hit in `OpenXrNative.cpp`.
- `Grep` — pattern `XR_COMPOSITION_LAYER_BLEND_TEXTURE_SOURCE_ALPHA_BIT` returns exactly one hit.
- `Grep` — pattern `hudLayerVisible.load()` inside `renderFrame` returns at least one hit.
- `Grep` — pattern `xrAcquireSwapchainImage.*hudSwapchain` returns at least one hit.
- `/build` skill compiles `vrDebug` without errors.
- Device test: after Step 2.5 forces visibility on, a dark rectangle appears bottom-centre in immersive mode.

**Status:** `[x]` done

---

### Step 2.5 — Debug visibility toggle: default `hudLayerVisible = true` under build-type `debug` only

**Files:** `app_v2/src/vr/cpp/OpenXrNative.cpp`
**Depends on:** Step 2.4

**Prompt for developer:**

> At the end of `createSessionAndSwapchains()`, after a successful HUD swapchain creation, add a one-time diagnostic toggle gated by `#ifndef NDEBUG`:
>
> ```cpp
> #ifndef NDEBUG
>     // Phase-02 debug: flip HUD visible so the dark quad confirms composition ordering.
>     // Phase-05 routes this flag through Kotlin and this block becomes a no-op.
>     g_ctx.hudLayerVisible.store(true);
>     LOGI("HUD: phase-02 debug visibility = true");
> #endif
> ```
>
> This line exists only so the developer can verify the rectangle is drawn at the right place on the first device run. It MUST be removed (not just commented out) as part of Phase 05 Step 5.4. Add a TODO marker next to it: `// TODO(phase-05): remove debug visibility toggle`.

**Verification:**

- `Grep` — pattern `TODO(phase-05): remove debug visibility toggle` returns exactly one hit.
- `Grep` — pattern `HUD: phase-02 debug visibility = true` returns exactly one hit.
- `/build` skill compiles `vrDebug` without errors.
- Device test: launch immersive video — a dark rectangle is visible in the lower-centre field of view, does not obstruct the video layer centre, and remains head-locked when the user turns the head. If the rectangle is not visible, revisit Step 2.4 (pose/orientation/size/space).

**Status:** `[x]` done

---

## Phase Done Criteria

All of the following must hold for this phase to flip to `✅ Done`:

- [ ] Every `Step 2.*` above is `[x] done`.
- [ ] Project compiles — `/build` on `vrDebug`.
- [ ] `Grep` for `TODO(phase-02)` in `OpenXrNative.cpp` returns zero hits.
- [ ] `Grep` for `TODO(phase-05): remove debug visibility toggle` returns exactly one hit (kept for Phase 05 to delete).
- [ ] On-device smoke: immersive session renders video AND the dark HUD rectangle simultaneously; exiting immersive does not crash; re-entering recreates both layers.
- [ ] Dev log entry added for `OpenXrNative.cpp` via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

Phase 03 assumes:

- HUD swapchain lifecycle is stable across session cycles — create on session ready, destroy on session stop.
- The HUD quad layer renders on top of video when `hudLayerVisible = true`; zero overdraw when false.
- Acquire/wait/release runs even without upload — so an actual Bitmap upload in Phase 03 only needs to `glTexSubImage2D` into the acquired image between `xrWaitSwapchainImage` and `xrReleaseSwapchainImage`.
- The debug visibility toggle (`NDEBUG` block) is still in the code — Phase 05 deletes it when the Kotlin path comes online.

---

## Rollback Plan

Revert the phase commit(s). Phase 01 scaffolding remains — JNI stubs still link, Kotlin pass-through still compiles. No data changed, no user-facing feature shipped yet.
