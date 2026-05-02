// OpenXR native bridge for FastMediaSorter VR flavor.
//
// Owns one XR instance/system/session/swapchain pair, drives the event+render loop,
// and callbacks into Kotlin per-eye so VrStereoRenderer can draw into the provided FBO.
//
// All OpenXR calls live here; Kotlin just owns lifecycle + renderer + per-frame callback.
#include <jni.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
// AndroidBitmap_lockPixels / unlockPixels / getInfo for HUD upload (spec_vr-immersive-hud-gl).
#include <android/bitmap.h>
#include "OpenXrCtx.h"
#include "OpenXrFrame.h"
#include "OpenXrHandTracking.h"
#include "OpenXrInput.h"
#include "OpenXrSwapchain.h"
#include "OpenXrLifecycle.h"
#include "OpenXrLog.h"
#include <EGL/egl.h>
#include <GLES3/gl3.h>
#include <openxr/openxr.h>
#include <openxr/openxr_platform.h>

#include <dlfcn.h>
#include <atomic>
#include <algorithm>
#include <cstring>
#include <ctime>
#include <mutex>
#include <string>
#include <vector>

#define LOG_TAG "OpenXrNative"

using namespace xrnative;

#define LOGI(...) nativeLogEmit(ANDROID_LOG_INFO, __VA_ARGS__)
#define LOGW(...) nativeLogEmit(ANDROID_LOG_WARN, __VA_ARGS__)
#define LOGE(...) nativeLogEmit(ANDROID_LOG_ERROR, __VA_ARGS__)
#define LOGD(...) nativeLogEmit(ANDROID_LOG_DEBUG, __VA_ARGS__)

// Single process-wide context. The vr flavor only ever runs one XR session.
XrCtx g_ctx{};
std::mutex g_ctxMutex;

XrResult triggerHapticImpl(int hand, int64_t durationNs, float amplitude);

// HUD composition layer (spec_vr-immersive-hud-gl).
bool createHudSwapchainImpl(uint32_t requestedWidth, uint32_t requestedHeight);
void destroyHudSwapchainImpl();

// Interactive panel composition layer (spec_vr-immersive-controls-panel Phase 03).
bool createPanelSwapchainImpl(uint32_t requestedWidth, uint32_t requestedHeight);
void destroyPanelSwapchainImpl();

#define XR_CHECK(expr, tag)                                   \
    do                                                        \
    {                                                         \
        XrResult _r = (expr);                                 \
        if (XR_FAILED(_r))                                    \
        {                                                     \
            LOGE("%s failed: %d", tag, static_cast<int>(_r)); \
            return false;                                     \
        }                                                     \
    } while (0)

bool createSessionAndSwapchains()
{
    return xrnative::createSessionAndSwapchains(g_ctx);
}

// ── HUD swapchain helpers (spec_vr-immersive-hud-gl) ────────────────────

bool createHudSwapchainImpl(uint32_t requestedWidth, uint32_t requestedHeight)
{
    return xrnative::createHudSwapchain(g_ctx, requestedWidth, requestedHeight);
}

void destroyHudSwapchainImpl()
{
    xrnative::destroyHudSwapchain(g_ctx);
}

// ── Interactive panel swapchain helpers (spec_vr-immersive-controls-panel §5) ──

bool createPanelSwapchainImpl(uint32_t requestedWidth, uint32_t requestedHeight)
{
    return xrnative::createPanelSwapchain(g_ctx, requestedWidth, requestedHeight);
}

void destroyPanelSwapchainImpl()
{
    xrnative::destroyPanelSwapchain(g_ctx);
}

// Trigger haptic feedback on one hand. Guarded against inactive session.
XrResult triggerHapticImpl(int hand, int64_t durationNs, float amplitude)
{
    auto &io = g_ctx.input;
    if (!io.initialized || !g_ctx.sessionRunning)
        return XR_ERROR_SESSION_NOT_RUNNING;
    XrAction act = (hand == 0) ? io.hapticL : io.hapticR;
    if (act == XR_NULL_HANDLE)
        return XR_ERROR_HANDLE_INVALID;
    float clamped = amplitude < 0.0f ? 0.0f : (amplitude > 1.0f ? 1.0f : amplitude);
    XrHapticVibration vib{XR_TYPE_HAPTIC_VIBRATION};
    vib.duration = static_cast<XrDuration>(durationNs);
    vib.frequency = XR_FREQUENCY_UNSPECIFIED;
    vib.amplitude = clamped;
    XrHapticActionInfo hai{XR_TYPE_HAPTIC_ACTION_INFO};
    hai.action = act;
    return xrApplyHapticFeedback(g_ctx.session, &hai,
                                 reinterpret_cast<const XrHapticBaseHeader *>(&vib));
}

// ─── JNI entry points ─────────────────────────────────────────────────────────

extern "C" JNIEXPORT jboolean JNICALL
Java_com_sza_fastmediasorter_vr_openxr_OpenXrNative_nativeInitialize(
    JNIEnv *env, jclass, jobject activity, jobject callback)
{
    LOGI("nativeInitialize: ENTRY — locking g_ctxMutex");
    std::lock_guard<std::mutex> lock(g_ctxMutex);

    env->GetJavaVM(&g_ctx.vm);
    LOGD("nativeInitialize: JavaVM=%p", static_cast<void *>(g_ctx.vm));

    // Snapshot the EGL context the Kotlin GL thread already set up.
    g_ctx.eglDisplay = eglGetCurrentDisplay();
    g_ctx.eglContext = eglGetCurrentContext();
    LOGI("nativeInitialize: EGL display=%p context=%p",
         static_cast<void *>(g_ctx.eglDisplay),
         static_cast<void *>(g_ctx.eglContext));

    if (g_ctx.eglDisplay == EGL_NO_DISPLAY || g_ctx.eglContext == EGL_NO_CONTEXT)
    {
        LOGE("No current EGL context — nativeInitialize must be called from GL thread");
        return JNI_FALSE;
    }

    EGLint cfgId = 0;
    eglQueryContext(g_ctx.eglDisplay, g_ctx.eglContext, EGL_CONFIG_ID, &cfgId);
    LOGD("nativeInitialize: EGL_CONFIG_ID=%d", cfgId);
    EGLint cfgAttr[] = {EGL_CONFIG_ID, cfgId, EGL_NONE};
    EGLint numCfg = 0;
    eglChooseConfig(g_ctx.eglDisplay, cfgAttr, &g_ctx.eglConfig, 1, &numCfg);
    LOGD("nativeInitialize: eglChooseConfig numCfg=%d config=%p",
         numCfg, static_cast<void *>(g_ctx.eglConfig));
    if (numCfg == 0 || g_ctx.eglConfig == nullptr)
    {
        LOGW("nativeInitialize: eglChooseConfig returned no config — session binding may fail");
    }

    // Cache render callback (Kotlin object with an `onRenderEye(int,int,int,int)` method).
    jclass cbCls = env->GetObjectClass(callback);
    g_ctx.onRenderEyeMethod = env->GetMethodID(cbCls, "onRenderEye", "(IIII)V");
    if (!g_ctx.onRenderEyeMethod)
    {
        LOGE("Callback missing onRenderEye(IIII)V");
        return JNI_FALSE;
    }
    LOGD("nativeInitialize: onRenderEye method cached  cls=%p method=%p",
         static_cast<void *>(cbCls), static_cast<void *>(g_ctx.onRenderEyeMethod));
    g_ctx.callbackRef = env->NewGlobalRef(callback);
    LOGD("nativeInitialize: callbackRef=%p", static_cast<void *>(g_ctx.callbackRef));

    LOGI("nativeInitialize: calling enumerateAndCreateInstance");
    if (!xrnative::enumerateAndCreateInstance(g_ctx, env, activity))
    {
        LOGE("nativeInitialize: enumerateAndCreateInstance FAILED");
        xrnative::releaseCallback(g_ctx, env);
        return JNI_FALSE;
    }
    LOGI("nativeInitialize: calling createSessionAndSwapchains");
    if (!createSessionAndSwapchains())
    {
        LOGE("nativeInitialize: createSessionAndSwapchains FAILED — destroying partial state");
        xrnative::destroyAll(g_ctx);
        xrnative::releaseCallback(g_ctx, env);
        return JNI_FALSE;
    }
    LOGI("nativeInitialize: SUCCESS — OpenXR fully initialised");
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_sza_fastmediasorter_vr_openxr_OpenXrNative_nativeRunFrame(JNIEnv *env, jclass)
{
    std::lock_guard<std::mutex> lock(g_ctxMutex);
    if (g_ctx.instance == XR_NULL_HANDLE)
    {
        // Only log this once — if the session is gone we'll spam otherwise.
        static bool s_loggedNoInstance = false;
        if (!s_loggedNoInstance)
        {
            LOGW("nativeRunFrame: instance is NULL — skipping (will not log again)");
            s_loggedNoInstance = true;
        }
        return;
    }
    if (!xrnative::pollEvents(g_ctx))
        return;
    if (g_ctx.sessionRunning)
    {
        xrnative::renderFrame(g_ctx, env);
    }
    else
    {
        // Throttled — log every 120 calls so we know we're in the idle-wait loop.
        static uint64_t s_idleCount = 0;
        if (++s_idleCount % 120 == 1)
        {
            LOGD("nativeRunFrame: sessionRunning=false  state=%s  exitReq=%d  #%llu idle calls",
                 xrSessionStateName(g_ctx.sessionState),
                 static_cast<int>(g_ctx.exitRequested.load()),
                 static_cast<unsigned long long>(s_idleCount));
        }
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_sza_fastmediasorter_vr_openxr_OpenXrNative_nativeConfigureLayer(
    JNIEnv *, jclass,
    jint layerType,
    jfloat widthMeters,
    jfloat heightMeters,
    jfloat distanceMeters,
    jfloat radiusMeters,
    jfloat centralHorizontalAngleRadians,
    jfloat upperVerticalAngleRadians,
    jfloat lowerVerticalAngleRadians)
{
    std::lock_guard<std::mutex> lock(g_ctxMutex);

    LayerConfig config{};
    switch (layerType)
    {
    case 1:
        config.type = LayerType::QuadCinema;
        break;
    case 2:
        config.type = LayerType::Equirect2;
        break;
    case 3:
        config.type = LayerType::Cylinder;
        break;
    case 0:
    default:
        config.type = LayerType::Projection;
        break;
    }

    config.widthMeters = widthMeters;
    config.heightMeters = heightMeters;
    config.distanceMeters = distanceMeters;
    config.radiusMeters = radiusMeters;
    config.centralHorizontalAngle = centralHorizontalAngleRadians;
    config.upperVerticalAngle = upperVerticalAngleRadians;
    config.lowerVerticalAngle = lowerVerticalAngleRadians;
    g_ctx.layerConfig = config;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_sza_fastmediasorter_vr_openxr_OpenXrNative_nativeShouldContinue(JNIEnv *, jclass)
{
    return g_ctx.exitRequested.load() ? JNI_FALSE : JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_sza_fastmediasorter_vr_openxr_OpenXrNative_nativeRequestStereoSnapshot(JNIEnv *, jclass)
{
    std::lock_guard<std::mutex> lock(g_ctxMutex);
    if (!g_ctx.sessionRunning || g_ctx.instance == XR_NULL_HANDLE)
        return JNI_FALSE;
    if (g_ctx.stereoSnapshot.requested.load() || g_ctx.stereoSnapshot.ready.load())
        return JNI_FALSE;

    g_ctx.stereoSnapshot.width = 0;
    g_ctx.stereoSnapshot.height = 0;
    for (auto &eyePixels : g_ctx.stereoSnapshot.eyePixels)
    {
        eyePixels.clear();
    }
    g_ctx.stereoSnapshot.ready.store(false);
    g_ctx.stereoSnapshot.requested.store(true);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_sza_fastmediasorter_vr_openxr_OpenXrNative_nativeIsStereoSnapshotReady(JNIEnv *, jclass)
{
    std::lock_guard<std::mutex> lock(g_ctxMutex);
    // Acquire load pairs with the release store in the render thread, ensuring
    // pixel data written before ready=true is visible here.
    return g_ctx.stereoSnapshot.ready.load(std::memory_order_acquire) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jintArray JNICALL
Java_com_sza_fastmediasorter_vr_openxr_OpenXrNative_nativeConsumeStereoSnapshotPixels(JNIEnv *env, jclass, jint eye)
{
    std::lock_guard<std::mutex> lock(g_ctxMutex);
    if (!g_ctx.stereoSnapshot.ready.load(std::memory_order_acquire) || eye < 0 || eye >= static_cast<jint>(kViewCount))
        return nullptr;

    const auto &pixels = g_ctx.stereoSnapshot.eyePixels[eye];
    if (pixels.empty())
        return nullptr;

    jintArray result = env->NewIntArray(static_cast<jsize>(pixels.size()));
    if (result == nullptr)
        return nullptr;
    env->SetIntArrayRegion(result, 0, static_cast<jsize>(pixels.size()), pixels.data());
    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_com_sza_fastmediasorter_vr_openxr_OpenXrNative_nativeReleaseStereoSnapshot(JNIEnv *, jclass)
{
    std::lock_guard<std::mutex> lock(g_ctxMutex);
    g_ctx.stereoSnapshot.requested.store(false);
    g_ctx.stereoSnapshot.ready.store(false);
    g_ctx.stereoSnapshot.width = 0;
    g_ctx.stereoSnapshot.height = 0;
    for (auto &eyePixels : g_ctx.stereoSnapshot.eyePixels)
    {
        eyePixels.clear();
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_sza_fastmediasorter_vr_openxr_OpenXrNative_nativeRequestExit(JNIEnv *, jclass)
{
    g_ctx.exitRequested = true;
}

extern "C" JNIEXPORT void JNICALL
Java_com_sza_fastmediasorter_vr_openxr_OpenXrNative_nativeRelease(JNIEnv *env, jclass)
{
    std::lock_guard<std::mutex> lock(g_ctxMutex);
    xrnative::destroyAll(g_ctx);
    xrnative::releaseCallback(g_ctx, env);
    LOGI("OpenXR released");
}

extern "C" JNIEXPORT jint JNICALL
Java_com_sza_fastmediasorter_vr_openxr_OpenXrNative_nativeGetEyeWidth(JNIEnv *, jclass, jint eye)
{
    if (eye < 0 || eye >= static_cast<jint>(kViewCount))
        return 0;
    return static_cast<jint>(g_ctx.eyes[eye].width);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_sza_fastmediasorter_vr_openxr_OpenXrNative_nativeGetEyeHeight(JNIEnv *, jclass, jint eye)
{
    if (eye < 0 || eye >= static_cast<jint>(kViewCount))
        return 0;
    return static_cast<jint>(g_ctx.eyes[eye].height);
}

// Register / replace the Kotlin XrInputCallback that receives per-frame edge events.
// Safe to call multiple times — previous global ref is released on replacement.
extern "C" JNIEXPORT void JNICALL
Java_com_sza_fastmediasorter_vr_openxr_OpenXrNative_nativeSetInputCallback(
    JNIEnv *env, jclass, jobject callback)
{
    std::lock_guard<std::mutex> lock(g_ctxMutex);
    auto &io = g_ctx.input;

    // Swap out the old global ref if present.
    if (io.inputCallbackRef)
    {
        env->DeleteGlobalRef(io.inputCallbackRef);
        io.inputCallbackRef = nullptr;
    }
    io.onInputEventMethod = nullptr;
    io.onPointerMoveMethod = nullptr;

    if (callback == nullptr)
    {
        LOGI("nativeSetInputCallback: callback cleared");
        return;
    }

    jclass cls = env->GetObjectClass(callback);
    // XrInputCallback.onInputEvent(I, I, F, I)V — type, hand, value, source.
    io.onInputEventMethod = env->GetMethodID(cls, "onInputEvent", "(IIFI)V");
    if (!io.onInputEventMethod)
    {
        LOGE("nativeSetInputCallback: onInputEvent(IIFI)V not found on callback class");
        env->DeleteLocalRef(cls);
        return;
    }
    // XrInputCallback.onPointerMove(I, F, F)V — hand, ndcX, ndcY. Optional —
    // controller-only callbacks may omit it (default implementation is a no-op),
    // so a missing ID is non-fatal: pointer events are silently dropped.
    io.onPointerMoveMethod = env->GetMethodID(cls, "onPointerMove", "(IFF)V");
    if (!io.onPointerMoveMethod)
    {
        LOGW("nativeSetInputCallback: onPointerMove(IFF)V not found — pointer stream disabled");
        // Not fatal: leave io.onPointerMoveMethod null so emitPointerMove short-circuits.
        env->ExceptionClear();
    }
    // XrInputCallback.onControllerPointerMove(I, F, F)V — hand, ndcX, ndcY. Optional.
    io.onControllerPointerMoveMethod = env->GetMethodID(cls, "onControllerPointerMove", "(IFF)V");
    if (!io.onControllerPointerMoveMethod)
    {
        LOGW("nativeSetInputCallback: onControllerPointerMove(IFF)V not found — controller ray NDC disabled");
        env->ExceptionClear();
    }
    io.inputCallbackRef = env->NewGlobalRef(callback);
    env->DeleteLocalRef(cls);
    LOGI("nativeSetInputCallback: callback registered  ref=%p input=%p pointer=%p ctrl=%p",
         static_cast<void *>(io.inputCallbackRef),
         static_cast<void *>(io.onInputEventMethod),
         static_cast<void *>(io.onPointerMoveMethod),
         static_cast<void *>(io.onControllerPointerMoveMethod));
}

// Trigger haptic vibration on the specified hand (0 = left, 1 = right).
// Returns true if the call reached the runtime (does not guarantee perception).
extern "C" JNIEXPORT jboolean JNICALL
Java_com_sza_fastmediasorter_vr_openxr_OpenXrNative_nativeTriggerHaptic(
    JNIEnv *, jclass, jint hand, jlong durationNs, jfloat amplitude)
{
    std::lock_guard<std::mutex> lock(g_ctxMutex);
    XrResult r = triggerHapticImpl(static_cast<int>(hand),
                                   static_cast<int64_t>(durationNs),
                                   static_cast<float>(amplitude));
    if (XR_FAILED(r))
    {
        // Warn once per call level; not worth its own throttle since haptic is user-initiated.
        LOGD("nativeTriggerHaptic: xrApplyHapticFeedback returned %d", static_cast<int>(r));
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

// Enable / disable the GL ray line rendered along the controller aim direction.
// NDC is emitted regardless; only the visual primitive is gated.
extern "C" JNIEXPORT void JNICALL
Java_com_sza_fastmediasorter_vr_openxr_OpenXrNative_nativeSetControllerRayEnabled(
    JNIEnv *, jclass, jboolean enabled)
{
    g_ctx.controllerRayEnabled.store(static_cast<bool>(enabled));
    LOGI("nativeSetControllerRayEnabled: %s", enabled ? "true" : "false");
}

// Drain buffered native log entries (each prefixed with "X|" where X is the
// priority char V/D/I/W/E). Called from OpenXrSessionManager so the init
// trace lands in the app's Timber-backed log file even though C++ writes via
// __android_log_print. Returns an empty array when the buffer is empty.
extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_sza_fastmediasorter_vr_openxr_OpenXrNative_nativeDrainLog(JNIEnv *env, jclass)
{
    std::vector<std::string> drained = nativeLogBufferDrain();
    jclass stringCls = env->FindClass("java/lang/String");
    if (stringCls == nullptr)
    {
        return nullptr;
    }
    jobjectArray arr = env->NewObjectArray(static_cast<jsize>(drained.size()), stringCls, nullptr);
    if (arr == nullptr)
    {
        env->DeleteLocalRef(stringCls);
        return nullptr;
    }
    for (jsize i = 0; i < static_cast<jsize>(drained.size()); ++i)
    {
        jstring s = env->NewStringUTF(drained[static_cast<size_t>(i)].c_str());
        if (s != nullptr)
        {
            env->SetObjectArrayElement(arr, i, s);
            env->DeleteLocalRef(s);
        }
    }
    env->DeleteLocalRef(stringCls);
    return arr;
}

// ═══════════════════════════════════════════════════════════════════════════
// HUD composition layer JNI bridge (spec_vr-immersive-hud-gl).
// Phase 01: stubs only. Phase 02 fills in xrCreateSwapchain + xrEndFrame
// composition; Phase 03 turns nativeUploadHudBitmap into a real GL upload.
// ═══════════════════════════════════════════════════════════════════════════

extern "C" JNIEXPORT jboolean JNICALL
Java_com_sza_fastmediasorter_vr_openxr_OpenXrNative_nativeCreateHudSwapchain(
    JNIEnv *, jclass, jint width, jint height)
{
    std::lock_guard<std::mutex> lock(g_ctxMutex);
    if (g_ctx.session == XR_NULL_HANDLE)
    {
        // Session not yet up. Store the requested dimensions so createSessionAndSwapchains
        // picks them up on session bring-up. Caller will need to invoke this again after
        // session ready to receive a meaningful boolean status.
        g_ctx.hudSwapchainWidth = static_cast<uint32_t>(width);
        g_ctx.hudSwapchainHeight = static_cast<uint32_t>(height);
        LOGI("nativeCreateHudSwapchain: %dx%d stored — session not yet up", width, height);
        return JNI_FALSE;
    }
    return createHudSwapchainImpl(static_cast<uint32_t>(width),
                                  static_cast<uint32_t>(height))
               ? JNI_TRUE
               : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_sza_fastmediasorter_vr_openxr_OpenXrNative_nativeDestroyHudSwapchain(
    JNIEnv *, jclass)
{
    std::lock_guard<std::mutex> lock(g_ctxMutex);
    destroyHudSwapchainImpl();
}

extern "C" JNIEXPORT void JNICALL
Java_com_sza_fastmediasorter_vr_openxr_OpenXrNative_nativeSetHudLayerVisible(
    JNIEnv *, jclass, jboolean visible)
{
    std::lock_guard<std::mutex> lock(g_ctxMutex);
    g_ctx.hudLayerVisible.store(visible == JNI_TRUE);
    LOGD("nativeSetHudLayerVisible: visible=%d", visible == JNI_TRUE ? 1 : 0);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_sza_fastmediasorter_vr_openxr_OpenXrNative_nativeUploadHudBitmap(
    JNIEnv *env, jclass, jobject bitmap)
{
    std::lock_guard<std::mutex> lock(g_ctxMutex);
    if (bitmap == nullptr)
    {
        return JNI_FALSE;
    }
    if (g_ctx.hudSwapchain == XR_NULL_HANDLE || !g_ctx.sessionRunning)
    {
        return JNI_FALSE;
    }

    AndroidBitmapInfo info{};
    int infoR = AndroidBitmap_getInfo(env, bitmap, &info);
    if (infoR != ANDROID_BITMAP_RESULT_SUCCESS)
    {
        static bool s_warnedInfo = false;
        if (!s_warnedInfo)
        {
            LOGW("nativeUploadHudBitmap: AndroidBitmap_getInfo failed: %d", infoR);
            s_warnedInfo = true;
        }
        return JNI_FALSE;
    }
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888)
    {
        static bool s_warnedFormat = false;
        if (!s_warnedFormat)
        {
            LOGW("nativeUploadHudBitmap: unsupported bitmap format=%d (need RGBA_8888)",
                 static_cast<int>(info.format));
            s_warnedFormat = true;
        }
        return JNI_FALSE;
    }
    if (info.width != g_ctx.hudSwapchainWidth || info.height != g_ctx.hudSwapchainHeight)
    {
        static bool s_warnedDims = false;
        if (!s_warnedDims)
        {
            LOGW("nativeUploadHudBitmap: dimension mismatch %ux%u vs swapchain %ux%u",
                 info.width, info.height,
                 g_ctx.hudSwapchainWidth, g_ctx.hudSwapchainHeight);
            s_warnedDims = true;
        }
        return JNI_FALSE;
    }

    void *pixels = nullptr;
    int lockR = AndroidBitmap_lockPixels(env, bitmap, &pixels);
    if (lockR != ANDROID_BITMAP_RESULT_SUCCESS || pixels == nullptr)
    {
        static bool s_warnedLock = false;
        if (!s_warnedLock)
        {
            LOGW("nativeUploadHudBitmap: lockPixels failed: %d", lockR);
            s_warnedLock = true;
        }
        return JNI_FALSE;
    }

    // WHY: GL upload (glTexSubImage2D + xrAcquire/Release) must happen on the EGL-owning
    // render thread. This function is called from the Kotlin main thread which has no EGL
    // context — calling GL here silently does nothing, leaving the texture all-zeros.
    // Solution: copy pixel data to the pre-allocated staging buffer and set hudBitmapPending.
    // renderFrame() reads the flag on the correct thread and does the actual GL upload.
    if (!g_ctx.hudPendingPixels.empty())
    {
        std::memcpy(g_ctx.hudPendingPixels.data(), pixels, g_ctx.hudPendingPixels.size());
        g_ctx.hudBitmapPending.store(true);
    }
    AndroidBitmap_unlockPixels(env, bitmap);
    // Return true so Kotlin-side logging is consistent (real success/failure is visible
    // from the render-thread upload path via Timber in the HUD pump).
    return JNI_TRUE;
}

// ═══════════════════════════════════════════════════════════════════════════
// Interactive panel JNI bridge (spec_vr-immersive-controls-panel Phase 03).
// ═══════════════════════════════════════════════════════════════════════════

extern "C" JNIEXPORT jboolean JNICALL
Java_com_sza_fastmediasorter_vr_openxr_OpenXrNative_nativeCreatePanelSwapchain(
    JNIEnv *, jclass, jint width, jint height)
{
    std::lock_guard<std::mutex> lock(g_ctxMutex);
    // S0020: align with HUD JNI (line 3303). xrCreateSwapchain only requires a
    // valid session handle; the stricter `sessionRunning` check fired before
    // XR_SESSION_STATE_READY arrived, producing the user-visible "panel never
    // came up" symptom on every cold start. Strategic ADR-1: no deferred state.
    if (g_ctx.session == XR_NULL_HANDLE)
    {
        LOGW("nativeCreatePanelSwapchain: session handle null — request rejected (size=%dx%d)", width, height);
        return JNI_FALSE;
    }
    return createPanelSwapchainImpl(static_cast<uint32_t>(width),
                                    static_cast<uint32_t>(height))
               ? JNI_TRUE
               : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_sza_fastmediasorter_vr_openxr_OpenXrNative_nativeDestroyPanelSwapchain(
    JNIEnv *, jclass)
{
    std::lock_guard<std::mutex> lock(g_ctxMutex);
    destroyPanelSwapchainImpl();
}

extern "C" JNIEXPORT void JNICALL
Java_com_sza_fastmediasorter_vr_openxr_OpenXrNative_nativeSetPanelLayerVisible(
    JNIEnv *, jclass, jboolean visible)
{
    std::lock_guard<std::mutex> lock(g_ctxMutex);
    g_ctx.panelLayerVisible.store(visible == JNI_TRUE);
    LOGD("nativeSetPanelLayerVisible: visible=%d", visible == JNI_TRUE ? 1 : 0);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_sza_fastmediasorter_vr_openxr_OpenXrNative_nativeUploadPanelBitmap(
    JNIEnv *env, jclass, jobject bitmap)
{
    std::lock_guard<std::mutex> lock(g_ctxMutex);
    if (bitmap == nullptr)
        return JNI_FALSE;
    if (g_ctx.panelSwapchain == XR_NULL_HANDLE || !g_ctx.sessionRunning)
        return JNI_FALSE;

    AndroidBitmapInfo info{};
    if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS)
        return JNI_FALSE;
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888)
        return JNI_FALSE;
    if (info.width != g_ctx.panelSwapchainWidth || info.height != g_ctx.panelSwapchainHeight)
        return JNI_FALSE;

    void *pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS ||
        pixels == nullptr)
        return JNI_FALSE;

    if (!g_ctx.panelPendingPixels.empty())
    {
        std::memcpy(g_ctx.panelPendingPixels.data(), pixels, g_ctx.panelPendingPixels.size());
        g_ctx.panelBitmapPending.store(true);
    }
    AndroidBitmap_unlockPixels(env, bitmap);
    return JNI_TRUE;
}
