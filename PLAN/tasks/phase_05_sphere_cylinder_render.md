# Phase 5 — Sphere/Cylinder Renderer Dispatch

**Status:** ✅ Completed 2026-04-19 · **Depends on:** Phase 4 · **Parent:** [../spec_vr-master.md](../spec_vr-master.md)

> **Implementation notes:**
>
> - Added `VrRenderContext` so the XR callback now passes layer type, stereo mode, eye, render target size, rendering mode, and source aspect into the GL renderer as one immutable object.
> - `VrStereoRenderer` now builds a layer-aware `RenderPlan` instead of assuming projection-only output. Projection, equirect, and cylinder paths intentionally stay flat because the OpenXR compositor performs the spatial warp.
> - `QUAD_CINEMA` now uses a centred, aspect-preserving viewport with explicit black-bar clear, so flat 2D content no longer stretches to square XR swapchains.
> - `VrPlayerActivity` now resolves source aspect ratio from the active ExoPlayer video size and injects it into each XR eye callback.
> - Added focused unit coverage for projection UV dispatch, spherical stereo UV dispatch, cylinder full-frame dispatch, and cinema viewport math.

## Goal

Extend `VrStereoRenderer` to render into Equirect2 and Cylinder swapchain targets, not just projection. For Equirect2/Cylinder, the OpenXR compositor does the sphere warping — the renderer just writes flat equirect pixels to the swapchain colour attachment with correct per-eye cropping.

## Current State

- [VrStereoRenderer.kt](../../app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrStereoRenderer.kt) handles `SBS_FULL`/`SBS_HALF`/`OU`/`MONO` for projection layers only.
- Shader uses OES sampler + UV offset per eye — works for SBS/OU projection.
- No branching based on layer type.

## Work

1. Accept `VrLayerType` in the render path via `VrRenderContext` and use it to build a per-eye render plan.
2. Dispatch:
   - `PROJECTION` → existing per-eye UV shader (no change).
   - `EQUIRECT_2` with MONO → write full equirect frame to both eye swapchain images.
   - `EQUIRECT_2` with SBS → left half → left eye swapchain, right half → right eye swapchain.
   - `EQUIRECT_2` with OU → top half → left eye, bottom → right eye.
   - `CYLINDER` → same as projection MONO but into cylinder swapchain (compositor wraps).
   - `QUAD_CINEMA` → centred, aspect-preserving blit to quad swapchain.
3. Confirm no double-warping (OpenXR compositor warps; shader must output flat pixels).
4. Add `VrRenderContext` class to carry frame state (layer type, stereo mode, eye index, swapchain index, render mode).
5. Update `VrPlayerActivity.renderVrFrame()` to pass context.

## Validation

- IDE diagnostics are clean for all touched VR Kotlin files.
- Focused VR unit tests pass:
  - `./gradlew.bat :app_v2:testVrDebugUnitTest --tests com.sza.fastmediasorter.vr.render.VrLayerFactoryTest --tests com.sza.fastmediasorter.vr.render.VrStereoRendererTest` ✅
- Quest runtime validation is still pending and remains the next hardware-only step.

## Acceptance Criteria

- SBS projection content still renders correctly after refactor (no regression).
- 360° equirect SBS video displays full sphere with correct left/right eye per half.
- 180° content displays only the front hemisphere; user's back is a solid colour (compositor default).
- Cylinder 180° video wraps correctly at the configured cylinder radius/angle (45° test).
- No GL errors in logcat at steady state.

## Files Touched

- [vr/render/VrStereoRenderer.kt](../../app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrStereoRenderer.kt)
- `app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrRenderContext.kt` (new)
- [vr/VrPlayerActivity.kt](../../app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt)
- Shader files (GLSL constants or separate `.glsl` — pick based on existing convention)

## Out of Scope

- Custom sphere meshes (not needed — compositor handles projection).
- Fisheye dewarping for `VR180_FISHEYE_SBS` — either compositor supports it or the renderer manually converts to equirect before submission. Decide during implementation based on Meta runtime behaviour; if manual, file a sub-task.
