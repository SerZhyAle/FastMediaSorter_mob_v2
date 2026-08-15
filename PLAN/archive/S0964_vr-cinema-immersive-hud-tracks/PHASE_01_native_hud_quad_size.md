# S0964 PHASE 01 - Native HUD quad size setter

**Goal:** the HUD quad can display a 2:1 panel texture without letterboxing; banner default stays 0.3x0.113 m (S0291 owner decision).

## Steps

- [x] 01.1 `xr_hud_world.h`: add `overrideWidth`/`overrideHeight` (float, 0 = no override) to `HUDWorldState`; declare `void xr_hud_set_quad_size(float widthMeters, float heightMeters);`.
- [x] 01.2 `xr_hud_world.cpp`: implement `xr_hud_set_quad_size` - store override AND apply to `g_hudState.quad.width/height` immediately (mid-session calls take effect next frame). In `xr_hud_init`, apply override when non-zero, else keep 0.3/0.113 defaults (re-entry sessions re-run `xr_hud_init`; override must survive).
- [x] 01.3 `xr_session.h`/`xr_session.cpp`: export `xr_session_set_hud_quad_size(float, float)` forwarding to `xr_hud_set_quad_size` (keeps JNI surface uniform with other `xr_session_*` calls).
- [x] 01.4 `diagnostic_xr_runtime.cpp`: add JNI `nativeSetHudQuadSize(FF)V` following the existing `nativeQueueHud` registration pattern.
- [x] 01.5 `core/xr/runtime/DiagnosticXrRuntime.kt`: add `fun setHudQuadSize(widthMeters: Float, heightMeters: Float)` to the interface; implement in `NativeDiagnosticXrRuntime.kt` with the same `runCatching` + `Timber.w` guard as `queueHud`.

## Verification

- `xr_hud_set_quad_size` referenced from JNI bridge; no other call sites yet (Phase 03 wires it).
- Project compiles: `.\a.ps1 nd` (CMake target included) - deferred to Phase 03 single build if no interim build needed.
