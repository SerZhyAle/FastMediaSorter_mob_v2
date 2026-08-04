// S0249 Phase 02 step 02.6: thin JNI bridge to the C++ OpenXR session host.
//
// All real work lives in `xr_session.cpp` (instance, EGL, session, swapchains, frame loop).
// This file holds only the `extern "C"` JNI entry points called from `NativeDiagnosticXrRuntime.kt`
// and forwards them to the C++ surface in `fms::xr::*`.
//
// Lifecycle expected from Kotlin (each call happens on the render thread except `RequestExit`
// which is UI-thread safe):
//   nativeInitSession(activity)         -> instance + EGL context
//   nativeAttachSurface(surface)        -> EGL surface bound (ANativeWindow from SurfaceHolder)
//   nativeStartSession()                -> session + swapchains + reference space + actions
//   nativeUploadTexture(rgba, w, h)     -> populate the sphere texture
//   nativeRunFrameLoop()                -> blocks until exit
//   nativeShutdown()                    -> tear down everything
//   nativeRequestExit()                 -> async exit signal (UI thread)
//
// JNI method binding is by-name (no JNI_OnLoad RegisterNatives) so the Kotlin side keeps the
// `external fun` signatures it has today. Method symbols match
// `com.sza.fastmediasorter.core.xr.runtime.NativeDiagnosticXrRuntime`.

#include "xr_session.h"
#include "xr_input.h"

#include <android/log.h>
#include <android/native_window_jni.h>
#include <jni.h>

namespace {
constexpr const char* kLogTag = "S0249.JniBridge";
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, kLogTag, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,  kLogTag, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, kLogTag, __VA_ARGS__)
} // namespace

extern "C" {

JNIEXPORT jint JNICALL
Java_com_sza_fastmediasorter_core_xr_runtime_NativeDiagnosticXrRuntime_nativeInitSession(
        JNIEnv* env, jobject /*thiz*/, jobject activity) {
    JavaVM* vm = nullptr;
    if (env->GetJavaVM(&vm) != JNI_OK || !vm) {
        LOGE("nativeInitSession: GetJavaVM failed");
        return static_cast<jint>(fms::xr::NativeResult::UnexpectedRuntimeError);
    }
    if (fms::xr::xr_session_is_initialized()) {
        if (fms::xr::xr_session_is_running()) {
            LOGW("nativeInitSession: session already running; rejecting concurrent init");
            return static_cast<jint>(fms::xr::NativeResult::AlreadyRunning);
        }
        LOGW("nativeInitSession: stale initialized state detected; forcing shutdown before re-init");
        fms::xr::xr_session_shutdown();
        if (fms::xr::xr_session_is_initialized()) {
            LOGE("nativeInitSession: stale native state survived forced shutdown");
            return static_cast<jint>(fms::xr::NativeResult::UnexpectedRuntimeError);
        }
    }
    // S0291 owner round 3 fix (2026-05-22 21:19): the original S0249 comment "the global ref
    // intentionally leaks for the session's lifetime" was load-bearing and the S0290 attempt
    // to balance NewGlobalRef/DeleteGlobalRef across the immersive enter/exit cycle backfired.
    // Two symptoms observed in successive logs:
    //   1. After DeleteGlobalRef the JNI table reused the same encoded jobject value for a
    //      new local ref on the SAME persistent Activity instance, then the next NewGlobalRef
    //      tripped the ART CheckJNI "stale reference with serial 4 v current 6" assertion
    //      (logcat 21:18:26 line 1886). Crashes the whole process before the new session can
    //      run.
    //   2. Even before that, with detach mid-shutdown, DeleteGlobalRef was silently skipped
    //      and the ref leaked anyway, eventually exhausting the table.
    // Resolution: keep the Activity global ref alive for the WHOLE process lifetime. If the
    // caller passes the same Activity instance (typical when user re-enters immersive without
    // recreating the Activity), reuse the existing global ref. If they pass a different
    // instance (extreme corner case - recreate(), config change), drop the old ref before
    // taking a new one. The native ref count grows by at most one per real Activity instance.
    if (fms::xr::g_activity_jobject() != nullptr) {
        jobject existing = static_cast<jobject>(fms::xr::g_activity_jobject());
        if (env->IsSameObject(activity, existing)) {
            LOGD("nativeInitSession: reusing existing activity global ref (same Activity instance)");
            auto r = fms::xr::xr_session_init(vm, static_cast<void*>(existing));
            return static_cast<jint>(r);
        }
        LOGW("nativeInitSession: Activity instance changed across sessions; replacing global ref");
        env->DeleteGlobalRef(existing);
        fms::xr::xr_session_clear_activity_jobject();
    }
    jobject globalActivity = env->NewGlobalRef(activity);
    if (!globalActivity) {
        LOGE("nativeInitSession: NewGlobalRef returned null");
        return static_cast<jint>(fms::xr::NativeResult::UnexpectedRuntimeError);
    }
    auto r = fms::xr::xr_session_init(vm, static_cast<void*>(globalActivity));
    return static_cast<jint>(r);
}

JNIEXPORT jint JNICALL
Java_com_sza_fastmediasorter_core_xr_runtime_NativeDiagnosticXrRuntime_nativeAttachSurface(
        JNIEnv* env, jobject /*thiz*/, jobject surface) {
    ANativeWindow* window = surface ? ANativeWindow_fromSurface(env, surface) : nullptr;
    return static_cast<jint>(fms::xr::xr_session_attach_surface(window));
}

JNIEXPORT jint JNICALL
Java_com_sza_fastmediasorter_core_xr_runtime_NativeDiagnosticXrRuntime_nativeStartSession(
        JNIEnv* /*env*/, jobject /*thiz*/) {
    return static_cast<jint>(fms::xr::xr_session_start());
}

JNIEXPORT jint JNICALL
Java_com_sza_fastmediasorter_core_xr_runtime_NativeDiagnosticXrRuntime_nativeUploadTexture(
        JNIEnv* env, jobject /*thiz*/, jbyteArray rgba, jint width, jint height) {
    if (!rgba) return static_cast<jint>(fms::xr::NativeResult::NotRunning);
    jbyte* bytes = env->GetByteArrayElements(rgba, nullptr);
    if (!bytes) return static_cast<jint>(fms::xr::NativeResult::UnexpectedRuntimeError);
    auto r = fms::xr::xr_session_upload_texture(reinterpret_cast<const uint8_t*>(bytes), width, height);
    env->ReleaseByteArrayElements(rgba, bytes, JNI_ABORT);
    return static_cast<jint>(r);
}

JNIEXPORT jint JNICALL
Java_com_sza_fastmediasorter_core_xr_runtime_NativeDiagnosticXrRuntime_nativeRunFrameLoop(
        JNIEnv* /*env*/, jobject /*thiz*/) {
    return static_cast<jint>(fms::xr::xr_session_run_frame_loop());
}

JNIEXPORT void JNICALL
Java_com_sza_fastmediasorter_core_xr_runtime_NativeDiagnosticXrRuntime_nativeRequestExit(
        JNIEnv* /*env*/, jobject /*thiz*/) {
    fms::xr::xr_session_request_exit();
}

JNIEXPORT void JNICALL
Java_com_sza_fastmediasorter_core_xr_runtime_NativeDiagnosticXrRuntime_nativeShutdown(
        JNIEnv* /*env*/, jobject /*thiz*/) {
    fms::xr::xr_session_shutdown();
}

JNIEXPORT void JNICALL
Java_com_sza_fastmediasorter_core_xr_runtime_NativeDiagnosticXrRuntime_nativeQueueFrame(
        JNIEnv* env, jobject /*thiz*/, jbyteArray rgba, jint width, jint height) {
    if (!rgba) return;
    jbyte* bytes = env->GetByteArrayElements(rgba, nullptr);
    if (!bytes) return;
    fms::xr::xr_session_queue_frame(reinterpret_cast<const uint8_t*>(bytes), width, height);
    env->ReleaseByteArrayElements(rgba, bytes, JNI_ABORT);
}

JNIEXPORT jobject JNICALL
Java_com_sza_fastmediasorter_core_xr_runtime_NativeDiagnosticXrRuntime_nativeGetVideoSurface(
        JNIEnv* env, jobject /*thiz*/) {
    jobject surface = static_cast<jobject>(fms::xr::xr_session_get_video_surface());
    return surface ? env->NewLocalRef(surface) : nullptr;
}

JNIEXPORT void JNICALL
Java_com_sza_fastmediasorter_core_xr_runtime_NativeDiagnosticXrRuntime_nativeSetVideoSurfaceEnabled(
        JNIEnv* /*env*/, jobject /*thiz*/, jboolean enabled) {
    fms::xr::xr_session_set_video_surface_enabled(enabled == JNI_TRUE);
}

JNIEXPORT void JNICALL
Java_com_sza_fastmediasorter_core_xr_runtime_NativeDiagnosticXrRuntime_nativeSetRenderConfig(
        JNIEnv* /*env*/, jobject /*thiz*/, jint projection, jint layout) {
    fms::xr::xr_session_set_render_config(projection, layout);
}

JNIEXPORT void JNICALL
Java_com_sza_fastmediasorter_core_xr_runtime_NativeDiagnosticXrRuntime_nativeSetParallaxShift(
        JNIEnv* /*env*/, jobject /*thiz*/, jfloat value) {
    fms::xr::xr_session_set_parallax_shift(value);
}

JNIEXPORT void JNICALL
Java_com_sza_fastmediasorter_core_xr_runtime_NativeDiagnosticXrRuntime_nativeQueueHud(
        JNIEnv* env, jobject /*thiz*/, jbyteArray rgba, jint width, jint height) {
    if (!rgba) return;
    jbyte* bytes = env->GetByteArrayElements(rgba, nullptr);
    if (!bytes) return;
    fms::xr::xr_session_queue_hud(reinterpret_cast<const uint8_t*>(bytes), width, height);
    env->ReleaseByteArrayElements(rgba, bytes, JNI_ABORT);
}

JNIEXPORT void JNICALL
Java_com_sza_fastmediasorter_core_xr_runtime_NativeDiagnosticXrRuntime_nativeQueueSubtitle(
        JNIEnv* env, jobject /*thiz*/, jbyteArray rgba, jint width, jint height) {
    // S0986: null/empty (width|height <= 0) is a hide request - forward without touching the array.
    if (!rgba || width <= 0 || height <= 0) {
        fms::xr::xr_session_queue_subtitle(nullptr, 0, 0);
        return;
    }
    jbyte* bytes = env->GetByteArrayElements(rgba, nullptr);
    if (!bytes) return;
    fms::xr::xr_session_queue_subtitle(reinterpret_cast<const uint8_t*>(bytes), width, height);
    env->ReleaseByteArrayElements(rgba, bytes, JNI_ABORT);
}

JNIEXPORT void JNICALL
Java_com_sza_fastmediasorter_core_xr_runtime_NativeDiagnosticXrRuntime_nativeSetHudQuadSize(
        JNIEnv* /*env*/, jobject /*thiz*/, jfloat widthMeters, jfloat heightMeters,
        jfloat verticalOffsetMeters) {
    fms::xr::xr_session_set_hud_quad_size(widthMeters, heightMeters, verticalOffsetMeters);
}

JNIEXPORT void JNICALL
Java_com_sza_fastmediasorter_core_xr_runtime_NativeDiagnosticXrRuntime_nativeSetHudVisible(
        JNIEnv* /*env*/, jobject /*thiz*/, jboolean visible) {
    fms::xr::xr_session_set_hud_visible(visible == JNI_TRUE);
}

JNIEXPORT jboolean JNICALL
Java_com_sza_fastmediasorter_core_xr_runtime_NativeDiagnosticXrRuntime_nativeIsRunning(
        JNIEnv* /*env*/, jobject /*thiz*/) {
    return fms::xr::xr_session_is_running() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_sza_fastmediasorter_core_xr_runtime_NativeDiagnosticXrRuntime_nativeIsInitialized(
        JNIEnv* /*env*/, jobject /*thiz*/) {
    return fms::xr::xr_session_is_initialized() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_sza_fastmediasorter_core_xr_runtime_NativeDiagnosticXrRuntime_nativeApplyHaptic(
        JNIEnv* /*env*/, jobject /*thiz*/, jint hand, jfloat durationSeconds, jfloat frequency, jfloat amplitude) {
    fms::xr::xr_input_apply_haptic(hand, durationSeconds, frequency, amplitude);
}

JNIEXPORT jfloat JNICALL
Java_com_sza_fastmediasorter_core_xr_runtime_NativeDiagnosticXrRuntime_nativeGetCurrentFps(
        JNIEnv* /*env*/, jobject /*thiz*/) {
    return static_cast<jfloat>(fms::xr::xr_session_get_fps());
}

} // extern "C"
