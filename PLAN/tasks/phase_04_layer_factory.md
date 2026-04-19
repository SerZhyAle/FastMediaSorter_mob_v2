# Phase 4 — VrLayerFactory + Equirect/Cylinder Layers

**Status:** ✅ Completed 2026-04-19 · **Depends on:** Phase 1, 2 · **Parent:** [../spec_vr-master.md](../spec_vr-master.md)

> **Implementation notes:**
>
> - Added `VrLayerType`, `VrLayerDescriptor`, `VrRenderingMode`, and injectable `VrLayerFactory` in `vr/render/`.
> - Added legacy-safe rendering-mode parsing so both stored values (`FULL_STEREO`) and older settings strings (`FULL_SBS` / `FULL_OU`) resolve to the same full-stereo path.
> - `VrPlayerActivity` now rebuilds the active layer descriptor whenever stereo detection or the VR rendering-mode preference changes, and pushes it into `OpenXrSessionManager`.
> - `OpenXrSessionManager` now forwards the active descriptor to the native bridge before the render loop and on live updates.
> - `OpenXrNative.cpp` now enables `XR_KHR_composition_layer_equirect2` and `XR_KHR_composition_layer_cylinder` when the runtime exposes them, and submits `Projection`, `Quad`, `Equirect2KHR`, or `CylinderKHR` layers at `xrEndFrame` based on the configured descriptor.
> - `VrStereoRenderer` now uses descriptor-driven per-eye UV rectangles so flat stereo and spherical stereo share one source of truth.
> - Added `VrLayerFactoryTest` coverage for the acceptance mapping and unsupported-combination fallback.

## Goal

Introduce a layer factory that maps a `StereoMode` + `VrRenderingMode` pair to the correct OpenXR composition layer type (Projection / Quad / Equirect2KHR / CylinderKHR) and produces the native layer struct consumed by `xrEndFrame`.

## Current State

- Only `VrStereoRenderer` exists and implicitly assumes a projection layer.
- No abstraction for layer selection; `VrControlOverlayManager` directly uses a QuadLayer for UI.
- No Equirect2 or Cylinder layer builders.

## Work

1. Define `VrLayerType` enum: `PROJECTION`, `QUAD_CINEMA`, `EQUIRECT_2`, `CYLINDER`.
2. Define data class `VrLayerDescriptor` with: type, poseOverride, size/aspect, per-eye UV rects (for baked stereo like SBS equirect), depth range.
3. Create `VrLayerFactory` interface in `vr/render/`:

   ```
   fun describe(stereo: StereoMode, renderMode: VrRenderingMode): VrLayerDescriptor
   fun buildNativeLayer(descriptor: VrLayerDescriptor, swapchainImageIndex: Int): Long // native handle
   ```

4. Mapping table:
   - `MONO` + `CINEMA` → `QUAD_CINEMA`.
   - `SBS_*` / `OU` + `FULL_STEREO` → `PROJECTION` (existing behaviour).
   - `EQUIRECT_360_*` → `EQUIRECT_2` (KHR layer, yaw/pitch/roll = 0).
   - `EQUIRECT_180_*` / `VR180_*` → `EQUIRECT_2` with half-sphere UV bounds.
   - `CYLINDER_180` → `CYLINDER`.
5. Native JNI methods to build each layer struct (`XrCompositionLayerProjection`, `XrCompositionLayerQuad`, `XrCompositionLayerEquirect2KHR`, `XrCompositionLayerCylinderKHR`).
6. `OpenXrSessionManager` accepts a live `VrLayerDescriptor` and forwards it to native before `xrEndFrame()`.
7. Enable required OpenXR extensions at `xrCreateInstance` (phase 1): `XR_KHR_composition_layer_equirect2`, `XR_KHR_composition_layer_cylinder`.

## Acceptance Criteria

- `VrLayerFactory.describe(EQUIRECT_360_SBS, FULL_STEREO)` returns an `EQUIRECT_2` descriptor with SBS UV bounds split.
- `xrEndFrame` accepts the native Equirect2 layer without validation error (check Meta validation layer logs).
- QuadLayer (Cinema) still works for `MONO` content (no regression).
- Unsupported combinations fall back to `QUAD_CINEMA` with a Timber warning.

## Validation

- IDE diagnostics are clean for all touched Kotlin and native files.
- Focused test pass: `./gradlew.bat :app_v2:testVrDebugUnitTest --tests com.sza.fastmediasorter.vr.render.VrLayerFactoryTest` ✅
- Remaining device/runtime verification stays in Phase 5 because Meta validation-layer checks still require Quest hardware.

## Files Touched

- `app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrLayerFactory.kt` (new — interface + impl)
- `app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrLayerType.kt` (new enum)
- `app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrLayerDescriptor.kt` (new data class)
- `app_v2/src/vr/cpp/OpenXrNative.cpp` — extend with layer builders
- [vr/openxr/OpenXrSessionManager.kt](../../app_v2/src/vr/java/com/sza/fastmediasorter/vr/openxr/OpenXrSessionManager.kt) — accept layer list in `endFrame`
- [vr/VrPlayerActivity.kt](../../app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt) — pass factory result to session
- `vr/di/VrModule.kt` — bind factory

## Out of Scope

- Actually rendering content into equirect/cylinder — that is phase 5.
- Cubemap layer (`XR_KHR_composition_layer_cube`) — deferred.
