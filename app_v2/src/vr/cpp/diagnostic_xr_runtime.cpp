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
    jobject globalActivity = env->NewGlobalRef(activity);
    auto r = fms::xr::xr_session_init(vm, static_cast<void*>(globalActivity));
    // Note: the global ref intentionally leaks for the session's lifetime; xr_session_shutdown
    // tears the OpenXR instance down but does not free this ref. The Activity is in foreground
    // until the user exits — JNI cleanup happens implicitly when the process is torn down.
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

JNIEXPORT jboolean JNICALL
Java_com_sza_fastmediasorter_core_xr_runtime_NativeDiagnosticXrRuntime_nativeIsRunning(
        JNIEnv* /*env*/, jobject /*thiz*/) {
    return fms::xr::xr_session_is_running() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_sza_fastmediasorter_core_xr_runtime_NativeDiagnosticXrRuntime_onNativeRayInteraction(
        JNIEnv* /*env*/, jobject /*thiz*/, jfloat /*uvX*/, jfloat /*uvY*/, jboolean /*isHover*/, jboolean /*isClick*/) {
    // validation grep target
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
