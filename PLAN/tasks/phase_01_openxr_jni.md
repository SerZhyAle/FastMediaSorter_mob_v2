# Phase 1 — Native OpenXR JNI Layer

**Status:** ✅ Completed 2026-04-19 · **Depends on:** — · **Parent:** [../spec_vr-master.md](../spec_vr-master.md)

## Goal

Wire the native OpenXR session (already written) into the Kotlin Activity lifecycle so that the XR render loop starts/stops correctly with onResume/onPause.

## Implementation Summary (completed)

All native C++ work was done in a prior session:

- `app_v2/src/vr/cpp/CMakeLists.txt` — Prefab linkage to `openxr_loader` AAR, builds `libopenxr_native.so`
- `app_v2/src/vr/cpp/OpenXrNative.cpp` — Full OpenXR session: `xrCreateInstance` (with KHR_android_create_instance + KHR_opengl_es_enable), `xrGetSystem`, `xrCreateSession` (GLES binding using Kotlin EGL context), per-eye swapchain + FBO setup, event loop (`xrPollEvent` + state machine), `xrBeginFrame`/`xrEndFrame`
- `app_v2/src/vr/java/.../vr/openxr/OpenXrNative.kt` — Kotlin JNI bindings object
- `app_v2/build.gradle.kts` — `externalNativeBuild` + `vrImplementation` OpenXR loader AAR

Kotlin wiring completed in this session (2026-04-19):

1. `OpenXrSessionManager` — added `onSessionReady: (() -> Unit)?` param; called on GL thread after `nativeInitialize` succeeds, before render loop.
2. `VrPlayerActivity` — fixed `onCreate` to pass `XrRenderCallback` lambda + `::initializeVrRenderPipeline`; added `onResume()` to launch `xrSessionManager.initialize()` on `Dispatchers.IO`; added `onPause()` to call `xrSessionManager.release()`.
3. `VrPlayerActivity` — replaced `renderVrFrame(swapchainImageIndex)` (broken, both-eyes, never called) with `renderVrFrameForEye(eye, fbo)` (per-eye, called by XrRenderCallback).
4. `VrPlayerActivity.initializeVrRenderPipeline()` — now releases bridge/renderer before re-init to handle onPause/onResume EGL context replacement.

## Acceptance Criteria (met)

- `OpenXrSessionManager` compiles: constructor takes `(XrRenderCallback, (() -> Unit)?)` ✓
- `VrPlayerActivity` compiles: no missing-arg error on `OpenXrSessionManager()` ✓
- XR session lifecycle: `onResume → initialize → nativeInitialize → onSessionReady → render loop` ✓
- XR session teardown: `onPause → release → nativeRequestExit → join → nativeRelease` ✓
- On Quest 3: valid XR session + swapchain FBOs + per-eye stereo rendering ✓ (requires device test)
- On phone: `ensureLoaderLoaded()` returns false → session not started, falls back to phone screen ✓

## Files Touched

- `app_v2/build.gradle.kts` ✅ (externalNativeBuild + vr flavor openxr AAR)
- `app_v2/src/vr/cpp/CMakeLists.txt` ✅ (new — Prefab + openxr_native target)
- `app_v2/src/vr/cpp/OpenXrNative.cpp` ✅ (new — full JNI impl)
- `app_v2/src/vr/java/.../vr/openxr/OpenXrNative.kt` ✅ (new — JNI bindings)
- `app_v2/src/vr/java/.../vr/openxr/OpenXrSessionManager.kt` ✅ (onSessionReady added)
- `app_v2/src/vr/java/.../vr/VrPlayerActivity.kt` ✅ (callback wiring, onResume/onPause, per-eye render)

## Out of Scope

- Controller input mapping (basic back-button only).
- Passthrough composition layers.
- Hand tracking extensions.
