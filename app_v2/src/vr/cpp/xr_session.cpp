// OpenXR diagnostic session host. Owns input, haptics, raycasting, and HUD placement.
// S1270: the public xr_session_* API and its lifecycle only - the setup, render and JNI
// halves live in xr_session_setup.cpp, xr_session_render.cpp and xr_session_jni.cpp, and the
// state they share is declared in xr_session_internal.h.

#include "xr_session_internal.h"

namespace fms::xr
{
    namespace detail
    {

        State g;

        // S0607: set once, after the OpenXR loader has been initialized with the process-stable
        // Application context. The loader holds its own ref to whatever Context it is given for the
        // whole process; a finish()-ed Activity ref is what aborted the next xrEnumerate* under
        // CheckJNI (S0291). With the Application context that ref never goes stale, which is what
        // makes destroying and recreating the XrInstance per entry safe.
        bool g_loaderInitialized = false;
        // S0607: process-lifetime GLOBAL ref to the Application context passed to the OpenXR loader.
        // The Quest loader lazily NewGlobalRef's this context during the first xrEnumerate*, so it
        // must outlive every Activity and must never be a local ref (deleting it crashed CheckJNI).
        // Intentionally never released - it is the Application context, alive for the whole process.
        jobject g_appContextGlobal = nullptr;

        // Smoothed frame rate (Hz) measured from `XrFrameState::predictedDisplayTime` deltas.
        // EMA with alpha = 0.1 -> effective 10-frame window. Updated only on the render thread,
        // read atomically by `xr_session_get_fps()` from any thread.
        std::atomic<float> g_currentFps{0.0f};
        // Previous predicted display time (nanoseconds, monotonic). 0 marks "no previous frame yet".
        int64_t g_prevPredictedDisplayTimeNs{0};
        XrTime g_lastNavigateActionTime[2] = {0, 0};
        // S0290 Phase 10 (revised 2026-05-22 by owner): navigation moved off trigger/pinch
        // (those collide with Quest 3 system gestures like screenshot) to the thumbstick X axis.
        // Per-hand last-known stick deflection state: -1 = pushed left, 0 = neutral, +1 = right.
        // A transition 0 -> ±1 is the navigate event; we re-arm when stick returns to neutral.
        // triggerClicked is preserved as the kept-as-default-mapping signal for ray interaction
        // (HUD click) - only the prev/next navigation is moved to the stick.
        int g_prevStickState[2] = {0, 0};
        bool g_prevTriggerClicked[2] = {false, false};
        // S0290 Phase 10 Step 10.3: running count of accepted navigations per hand for in-log
        // self-diagnosis. The count appears in the LOGD navigation line so the operator can
        // distinguish "system saw 5 distinct gestures" vs "system saw 1 gesture but I think I
        // did 5". Reset alongside the time stamps in xr_session_start.
        int g_navigateCounter[2] = {0, 0};
        // S1133: contextual thumbstick semantics (see kXrInputModePlayer / kXrInputModeBrowse in the
        // header). Written from the Kotlin fascade on any thread, read on the render thread each
        // frame, so it is atomic rather than guarded by g.mutex - pollActions must not contend with
        // the upload path for a single int.
        std::atomic<int> g_inputMode{kXrInputModePlayer};
        // S1133: per-hand Y-axis edge-detection snapshot, the vertical twin of g_prevStickState.
        // Browse mode needs one discrete step per firm push, which the player's continuous zoom
        // accumulator cannot express.
        int g_prevStickStateY[2] = {0, 0};

    } // namespace detail

    using namespace detail;

    bool xr_session_is_running() { return g.running.load(); }

    bool xr_session_is_initialized()
    {
        std::lock_guard<std::mutex> lock(g.mutex);
        // S0291 owner round 4 (2026-05-22 21:51): "initialized" now means "session is active",
        // not "instance exists". S0607: the XrInstance is recreated per entry and only the EGL
        // context persists across exit/re-enter. A fresh session is detected by g.session being
        // XR_NULL_HANDLE.
        return g.session != XR_NULL_HANDLE;
    }

    jobject_opaque g_activity_jobject()
    {
        std::lock_guard<std::mutex> lock(g.mutex);
        return static_cast<jobject_opaque>(g.activity);
    }

    void xr_session_clear_activity_jobject()
    {
        std::lock_guard<std::mutex> lock(g.mutex);
        g.activity = nullptr;
    }

    NativeResult xr_session_init(JavaVM *vm, jobject_opaque activity)
    {
        std::lock_guard<std::mutex> lock(g.mutex);
        if (g.running.load())
        {
            LOGW("xr_session_init: already running; rejecting");
            return NativeResult::AlreadyRunning;
        }
        g.vm = vm;
        g.activity = static_cast<jobject>(activity);
        // S0607: the XrInstance is recreated every entry, bound to the CURRENT Activity (it is
        // destroyed in xr_session_shutdown). The original S0291 reason for keeping it alive - a
        // CheckJNI abort on re-init - came from the loader holding a finish()-ed Activity ref;
        // createInstance now initializes the loader once with the process-stable Application
        // context, so recreating the instance is safe. Reusing the instance bound it to a dead
        // Activity, which made the Meta runtime resolve a null VolumetricWindowInfo and hang the
        // re-entry session in IDLE. The EGL context is Activity-agnostic and is kept across entries.
        LOGD("xr_session_init: g.instance=%p g.eglContext=%p (instance recreated per entry, EGL reused)",
             (void *)g.instance, (void *)g.eglContext);
        NativeResult r;
        if (g.instance == XR_NULL_HANDLE)
        {
            LOGD("xr_session_init: creating XrInstance (cold start)");
            r = createInstance(vm, static_cast<jobject>(activity));
            if (r != NativeResult::Ok)
            {
                LOGE("xr_session_init: createInstance failed -> %d", (int)r);
                return r;
            }
        }
        else
        {
            LOGD("xr_session_init: reusing existing XrInstance %p", (void *)g.instance);
        }
        if (g.eglContext == EGL_NO_CONTEXT)
        {
            LOGD("xr_session_init: creating EGL context (cold start)");
            r = createEgl();
            if (r != NativeResult::Ok)
            {
                LOGE("xr_session_init: createEgl failed -> %d", (int)r);
                return r;
            }
        }
        else
        {
            LOGD("xr_session_init: reusing existing EGL context %p", (void *)g.eglContext);
        }
        return NativeResult::Ok;
    }

    NativeResult xr_session_attach_surface(ANativeWindow *window)
    {
        std::lock_guard<std::mutex> lock(g.mutex);
        g.window = window;
        NativeResult r = bindEglSurface();
        return r;
    }

    NativeResult xr_session_start()
    {
        std::lock_guard<std::mutex> lock(g.mutex);
        if (g.session != XR_NULL_HANDLE)
            return NativeResult::AlreadyRunning;
        NativeResult r = createSessionAndSpaces();
        if (r != NativeResult::Ok)
            return r;
        r = createSwapchains();
        if (r != NativeResult::Ok)
            return r;
        r = createGlAssets();
        if (r != NativeResult::Ok)
            return r;
        xr_input_init(g.instance, g.session);
        xr_hud_init();
        xr_subtitle_init();
        g.running.store(true);
        g.exitRequested.store(false);
        // Reset FPS accumulator so a previous session's value does not bleed into the new one.
        g_currentFps.store(0.0f, std::memory_order_relaxed);
        g_prevPredictedDisplayTimeNs = 0;
        g_lastNavigateActionTime[0] = 0;
        g_lastNavigateActionTime[1] = 0;
        // S0290 Phase 10: clear per-hand edge-detection snapshot and per-hand counters so a
        // freshly started session does not inherit stale state from the previous one.
        g_prevStickState[0] = 0;
        g_prevStickState[1] = 0;
        g_prevStickStateY[0] = 0;
        g_prevStickStateY[1] = 0;
        g_prevTriggerClicked[0] = false;
        g_prevTriggerClicked[1] = false;
        // S1133: the runtime outlives a single mode, so a session that starts without an explicit
        // assertion must be the player - the browser sets its own on session-ready.
        g_inputMode.store(kXrInputModePlayer, std::memory_order_relaxed);
        // S0291 round 5: reset zoom on each new session so re-enter starts at 1.0 (no carry-over).
        g.zoom = 1.0f;
        g_navigateCounter[0] = 0;
        g_navigateCounter[1] = 0;
        LOGD("xr_session_start: complete");
        return NativeResult::Ok;
    }

    NativeResult xr_session_upload_texture(const uint8_t *rgba, int width, int height)
    {
        std::lock_guard<std::mutex> lock(g.mutex);
        if (g.texture == 0 || !rgba || width <= 0 || height <= 0)
            return NativeResult::NotRunning;
        if (!uploadStaticTexturePixels(rgba, width, height, "upload_texture"))
            return NativeResult::FramePresentFailed;
        LOGD("texture uploaded: %dx%d", width, height);
        return NativeResult::Ok;
    }

    NativeResult xr_session_queue_frame(const uint8_t *rgba, int width, int height)
    {
        if (!rgba || width <= 0 || height <= 0)
            return NativeResult::UnexpectedRuntimeError;
        std::lock_guard<std::mutex> lock(g.frameMutex);
        size_t size = width * height * 4;
        g.pendingFrameData.assign(rgba, rgba + size);
        g.pendingFrameWidth = width;
        g.pendingFrameHeight = height;
        g.pendingFrameReady = true;
        return NativeResult::Ok;
    }

    jobject_opaque xr_session_get_video_surface()
    {
        return static_cast<jobject_opaque>(g.videoSurface);
    }

    void xr_session_set_video_surface_enabled(bool enabled)
    {
        g.videoTextureEnabled.store(enabled, std::memory_order_relaxed);
    }

    void xr_session_set_render_config(int projection, int layout)
    {
        std::lock_guard<std::mutex> lock(g.mutex);
        g.renderProjection = projection;
        g.stereoLayout = layout;
        // S0291 owner round 8 (2026-05-22 22:53): reset zoom to 1.0 on every media
        // switch so each new slide starts at default framing ("при переключении на
        // следующую картинку/видео приближение/удаление сбрасывается к норме").
        g.zoom = 1.0f;
        LOGD("set_render_config: projection=%d, layout=%d, zoom reset to 1.0", projection, layout);
    }

    void xr_session_set_parallax_shift(float value)
    {
        std::lock_guard<std::mutex> lock(g.mutex);
        float clamped = value < 0.0f ? 0.0f : (value > 1.0f ? 1.0f : value);
        g.parallaxShift = (clamped - 0.5f) * 0.04f;
    }

    void xr_session_set_input_mode(int mode)
    {
        if (mode != kXrInputModePlayer && mode != kXrInputModeBrowse)
        {
            LOGW("set_input_mode: unknown mode %d, ignored", mode);
            return;
        }
        // Clear the vertical edge snapshot on every switch: a stick still deflected across the
        // change must be released before the new mode accepts it as a step.
        g_prevStickStateY[0] = 0;
        g_prevStickStateY[1] = 0;
        g_inputMode.store(mode, std::memory_order_relaxed);
        LOGD("set_input_mode: %d", mode);
    }

    void xr_session_set_hud_quad_size(float widthMeters, float heightMeters, float verticalOffsetMeters)
    {
        // S0964: thin forwarder keeps the JNI surface uniform (everything goes through
        // xr_session_*); sizing logic lives with the quad state in xr_hud_world.
        xr_hud_set_quad_size(widthMeters, heightMeters, verticalOffsetMeters);
    }

    void xr_session_set_hud_quad_distance(float distanceMeters)
    {
        // S1271: thin forwarder keeps the JNI surface uniform (everything goes through
        // xr_session_*); the distance state lives with the quad in xr_hud_world.
        xr_hud_set_quad_distance(distanceMeters);
    }

    void xr_session_set_hud_visible(bool visible)
    {
        // S1232: thin forwarder keeps the JNI surface uniform (everything goes through
        // xr_session_*); the visibility state lives with the quad in xr_hud_world.
        xr_hud_set_visible(visible);
    }

    void xr_session_queue_hud(const uint8_t *rgba, int width, int height)
    {
        if (!rgba || width <= 0 || height <= 0)
        {
            LOGW("xr_session_queue_hud REJECTED: rgba=%p w=%d h=%d", rgba, width, height);
            return;
        }
        std::lock_guard<std::mutex> lock(g.hudMutex);
        size_t size = width * height * 4;
        g.pendingHudData.assign(rgba, rgba + size);
        g.pendingHudWidth = width;
        g.pendingHudHeight = height;
        g.pendingHudReady = true;
        LOGD("xr_session_queue_hud STORED %dx%d (%zu bytes); first pixel RGBA=%d,%d,%d,%d",
             width, height, size, (int)rgba[0], (int)rgba[1], (int)rgba[2], (int)rgba[3]);
    }

    void xr_session_queue_subtitle(const uint8_t *rgba, int width, int height)
    {
        std::lock_guard<std::mutex> lock(g.subtitleMutex);
        if (!rgba || width <= 0 || height <= 0)
        {
            // S0986: empty cue - hide on the next frame; keep the last texture untouched.
            g.pendingSubtitleReady = false;
            g.pendingSubtitleHide = true;
            return;
        }
        size_t size = (size_t)width * height * 4;
        g.pendingSubtitleData.assign(rgba, rgba + size);
        g.pendingSubtitleWidth = width;
        g.pendingSubtitleHeight = height;
        g.pendingSubtitleReady = true;
        g.pendingSubtitleHide = false;
        LOGD("xr_session_queue_subtitle STORED %dx%d (%zu bytes)", width, height, size);
    }

    void xr_session_request_exit()
    {
        g.exitRequested.store(true);
    }

    float xr_session_get_fps()
    {
        return g_currentFps.load(std::memory_order_relaxed);
    }

    void xr_session_shutdown()
    {
        std::lock_guard<std::mutex> lock(g.mutex);
        LOGD("xr_session_shutdown: begin (instance=%p session=%p activity=%p)",
             (void *)g.instance, (void *)g.session, (void *)g.activity);
        // Drop running flag FIRST so any concurrent render-thread iteration observes the change
        // and exits the frame loop before we tear down GL/EGL/OpenXR resources underneath it.
        g.running.store(false);
        g.exitRequested.store(true);
        bool attached = false;
        JNIEnv *env = getAttachedEnv(attached);
        releaseVideoSurfaceObjects(env);
        if (attached && g.vm)
            g.vm->DetachCurrentThread();
        g.videoTextureEnabled.store(false, std::memory_order_relaxed);

        for (auto &eye : g.eyes)
        {
            if (eye.fbo)
                glDeleteFramebuffers(1, &eye.fbo);
            if (eye.depthRb)
                glDeleteRenderbuffers(1, &eye.depthRb);
            if (eye.handle != XR_NULL_HANDLE)
                xrDestroySwapchain(eye.handle);
        }
        g.eyes.clear();
        if (g.texture)
        {
            glDeleteTextures(1, &g.texture);
            g.texture = 0;
        }
        if (g.videoTexture)
        {
            glDeleteTextures(1, &g.videoTexture);
            g.videoTexture = 0;
        }
        if (g.hudTexture)
        {
            glDeleteTextures(1, &g.hudTexture);
            g.hudTexture = 0;
        }
        if (g.subtitleTexture)
        {
            glDeleteTextures(1, &g.subtitleTexture);
            g.subtitleTexture = 0;
        }

        if (g.vbo)
        {
            glDeleteBuffers(1, &g.vbo);
            g.vbo = 0;
        }
        if (g.ibo)
        {
            glDeleteBuffers(1, &g.ibo);
            g.ibo = 0;
        }
        if (g.vao)
        {
            glDeleteVertexArrays(1, &g.vao);
            g.vao = 0;
        }

        if (g.hemiVbo)
        {
            glDeleteBuffers(1, &g.hemiVbo);
            g.hemiVbo = 0;
        }
        if (g.hemiIbo)
        {
            glDeleteBuffers(1, &g.hemiIbo);
            g.hemiIbo = 0;
        }
        if (g.hemiVao)
        {
            glDeleteVertexArrays(1, &g.hemiVao);
            g.hemiVao = 0;
        }

        if (g.quadVbo)
        {
            glDeleteBuffers(1, &g.quadVbo);
            g.quadVbo = 0;
        }
        if (g.quadIbo)
        {
            glDeleteBuffers(1, &g.quadIbo);
            g.quadIbo = 0;
        }
        if (g.quadVao)
        {
            glDeleteVertexArrays(1, &g.quadVao);
            g.quadVao = 0;
        }

        if (g.program)
        {
            glDeleteProgram(g.program);
            g.program = 0;
        }
        if (g.videoProgram)
        {
            glDeleteProgram(g.videoProgram);
            g.videoProgram = 0;
        }

        xr_input_shutdown();
        xr_hud_shutdown();
        if (g.localSpace != XR_NULL_HANDLE)
        {
            xrDestroySpace(g.localSpace);
            g.localSpace = XR_NULL_HANDLE;
        }
        if (g.session != XR_NULL_HANDLE)
        {
            xrDestroySession(g.session);
            g.session = XR_NULL_HANDLE;
        }
        // S0607: destroy the XrInstance on every exit so the next entry recreates it bound to the
        // CURRENT Activity. The instance is bound to the Activity passed at creation; reusing it
        // across entries (S0291) left it pointing at a finish()-ed Activity, so the Meta runtime
        // resolved a null VolumetricWindowInfo and the re-entry session hung in IDLE. The loader
        // stays initialized with the Application context (see createInstance), so recreating the
        // instance no longer triggers the S0291 CheckJNI abort. The EGL context is Activity-agnostic
        // and is kept; only the per-Activity EGLSurface is destroyed below.
        if (g.instance != XR_NULL_HANDLE)
        {
            xrDestroyInstance(g.instance);
            g.instance = XR_NULL_HANDLE;
            g.systemId = XR_NULL_SYSTEM_ID;
            g.viewConfigs.clear();
        }
        LOGD("xr_session_shutdown: destroyed XrInstance, kept EGL context %p across exit",
             (void *)g.eglContext);
        if (g.eglSurface != EGL_NO_SURFACE && g.eglDisplay != EGL_NO_DISPLAY)
        {
            eglMakeCurrent(g.eglDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
            eglDestroySurface(g.eglDisplay, g.eglSurface);
            g.eglSurface = EGL_NO_SURFACE;
        }
        if (g.window)
        {
            ANativeWindow_release(g.window);
            g.window = nullptr;
        }
        g.sessionRunning = false;
        g.sessionState = XR_SESSION_STATE_UNKNOWN;
        // g.running already false (set at function top). Reset frame buffers too so a fresh
        // session does not inherit stale pendingFrameReady from the previous one.
        {
            std::lock_guard<std::mutex> fl(g.frameMutex);
            g.pendingFrameReady = false;
            g.pendingFrameData.clear();
            g.pendingFrameWidth = 0;
            g.pendingFrameHeight = 0;
        }
        {
            std::lock_guard<std::mutex> hl(g.hudMutex);
            g.pendingHudReady = false;
            g.pendingHudData.clear();
            g.pendingHudWidth = 0;
            g.pendingHudHeight = 0;
            // S0961: next session starts with a fresh grey placeholder texture, so the
            // content-uploaded gate must re-arm as well.
            g.hudContentUploaded = false;
        }
        {
            // S0986: re-arm the subtitle gate/visibility and drop stale pending cue data.
            std::lock_guard<std::mutex> sl(g.subtitleMutex);
            g.pendingSubtitleReady = false;
            g.pendingSubtitleHide = false;
            g.pendingSubtitleData.clear();
            g.pendingSubtitleWidth = 0;
            g.pendingSubtitleHeight = 0;
            g.subtitleContentUploaded = false;
            g.subtitleVisible = false;
        }

        // S0291 owner round 3 (2026-05-22 21:19): do NOT DeleteGlobalRef on g.activity at
        // shutdown. The Activity global ref must persist across the immersive enter/exit
        // boundary for the lifetime of the process - see the load-bearing rationale in
        // diagnostic_xr_runtime.cpp::nativeInitSession (CheckJNI "stale reference with
        // serial number" crash on re-entry when the ref was deleted between sessions).
        // g.activity is reused by the JNI bridge on next nativeInitSession via IsSameObject;
        // it is released only if a truly different Activity instance arrives, or implicitly
        // when the process dies. g.vm is cleared because it has no associated allocation.
        g.vm = nullptr;
        LOGD("xr_session_shutdown: complete (activity globalref intentionally retained for process lifetime)");
    }

} // namespace fms::xr
