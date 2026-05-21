// S0249 Phase 02 step 02.6: OpenXR diagnostic session host (C++ surface).
//
// The header declares the slim C++ API consumed by `diagnostic_xr_runtime.cpp` (JNI bridge).
// All OpenXR + EGL + GLES bring-up lives in `xr_session.cpp`; the JNI bridge only forwards
// lifecycle events that originate on the Java side (Activity created, surface available,
// texture upload, frame loop request, exit request).
//
// Threading: every method must be invoked from the dedicated render thread created by
// `DiagnosticXrRenderThread.kt`. The only exception is `requestExit()` which is safe to
// call from the UI thread (sets an atomic flag the render thread polls every frame).
//
// Lifecycle (typical call order on the render thread):
//   1. xr_session_init(vm, activity)        -> instance + system + EGL context
//   2. xr_session_attach_surface(window)    -> EGLSurface bound, session creatable
//   3. xr_session_start()                   -> session + swapchain + reference space + actions
//   4. xr_session_upload_texture(rgba, w,h) -> sphere texture
//   5. xr_session_run_frame_loop()          -> blocks until exit requested
//   6. xr_session_shutdown()                -> everything torn down
//
// All functions return an int matching `NativeResult` ordinals (see diagnostic_xr_runtime.cpp).

#pragma once

#include <cstdint>

struct ANativeWindow;
struct _JavaVM;
typedef struct _JavaVM JavaVM;
typedef void* jobject_opaque;

namespace fms::xr {

// Native result codes shared with Kotlin DiagnosticXrNativeResult.
enum class NativeResult : int {
    Ok                      = 0,
    LoaderUnavailable       = 1,
    InstanceCreationFailed  = 2,
    SystemNotFound          = 3,
    SessionCreationFailed   = 4,
    SwapchainCreationFailed = 5,
    FramePresentFailed      = 6,
    AlreadyRunning          = 7,
    NotRunning              = 8,
    UnexpectedRuntimeError  = 99
};

// Returns true if an instance was created earlier and survived (idempotent guard for the
// gateway probe).
bool xr_session_is_running();

// Stage 1: bring up XrInstance + XrSystem + EGL display/config/context (no surface yet).
// `vm` and `activity` are the JavaVM and the Activity jobject passed across JNI.
NativeResult xr_session_init(JavaVM* vm, jobject_opaque activity);

// Stage 2: bind an EGLSurface to the Activity-supplied ANativeWindow so subsequent GL calls
// have a draw destination. Required before `xr_session_start`.
NativeResult xr_session_attach_surface(ANativeWindow* window);

// Stage 3: create XrSession (OpenGL ES binding), enumerate view configurations, create the
// per-eye swapchains, build the local reference space, build the sphere mesh + shaders, and
// attach the input action set.
NativeResult xr_session_start();

// Stage 4: replace the sphere texture with the supplied RGBA pixels. Must be called after
// `xr_session_start` (the GL texture object is created there). `rgba` is a `width*height*4`
// byte buffer in `GL_RGBA` / `GL_UNSIGNED_BYTE` format (sRGB encoded image data).
NativeResult xr_session_upload_texture(const uint8_t* rgba, int width, int height);

// Queue a dynamic frame update thread-safely for the next frame render.
NativeResult xr_session_queue_frame(const uint8_t* rgba, int width, int height);

// Set render configuration (projection and layout).
void xr_session_set_render_config(int projection, int layout);

// Set the stereo parallax shift used by the diagnostic fragment shader.
void xr_session_set_parallax_shift(float value);

// Queue a new HUD frame (text overlay).
void xr_session_queue_hud(const uint8_t* rgba, int width, int height);

// Stage 5: blocking frame loop. Returns Ok when the loop exits cleanly (user requested exit
// or runtime asked the session to stop), or an error code if the loop aborted on a hard
// failure. The render thread should call this once per session.
NativeResult xr_session_run_frame_loop();

// Returns the current smoothed frame rate (FPS) measured from the OpenXR frame loop's
// `predictedDisplayTime` deltas. Updated every frame using an EMA filter (~10-frame window).
// Returns 0.0 before the second frame has been delivered. Thread-safe.
float xr_session_get_fps();

// UI-thread safe: signal the render thread to leave the frame loop. Idempotent.
void xr_session_request_exit();

// Stage 6: tear everything down. Safe to call from the render thread after the loop has
// returned, even if earlier stages failed mid-way.
void xr_session_shutdown();

} // namespace fms::xr
