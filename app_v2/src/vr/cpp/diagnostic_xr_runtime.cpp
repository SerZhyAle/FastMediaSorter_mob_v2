// S0249 Phase 02: minimal OpenXR diagnostic runtime bridge.
//
// Purpose: open an OpenXR session that renders one bundled stereoscopic top-bottom
// equirectangular image, exits on any input event (Phase 05 wires the action set), and
// returns structured outcomes through JNI.
//
// Scope of this file at Phase 02:
//   - Extension probing (xrEnumerateInstanceExtensionProperties).
//   - Instance / session / swapchain lifecycle scaffolding.
//   - JNI surface: probeExtensions, startSession, presentStaticImage, requestExit.
//
// Out of scope for Phase 02 (lands in later phases):
//   - Actual frame-loop rendering of the composition layer (Phase 03 wires the asset).
//   - Input action set and exit-on-any-input handler (Phase 05).
//   - Native ↔ Settings activity surface coupling (Phase 04).
//
// All public JNI methods return Java-side enum ordinals so the Kotlin facade can map
// them to XrEntryResult without inspecting native exception detail.

#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>
#include <atomic>
#include <mutex>

#define XR_USE_PLATFORM_ANDROID
#define XR_USE_GRAPHICS_API_OPENGL_ES
#include <openxr/openxr.h>
#include <openxr/openxr_platform.h>

namespace {

constexpr const char* kLogTag = "S0249.DiagXR";

#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, kLogTag, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,  kLogTag, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, kLogTag, __VA_ARGS__)

// Native result ordinals must stay in sync with
// `app_v2/src/vr/java/.../runtime/DiagnosticXrNativeResult.kt`.
enum class NativeResult : jint {
    Ok                            = 0,
    LoaderUnavailable             = 1,
    InstanceCreationFailed        = 2,
    SystemNotFound                = 3,
    SessionCreationFailed         = 4,
    SwapchainCreationFailed       = 5,
    FramePresentFailed            = 6,
    AlreadyRunning                = 7,
    NotRunning                    = 8,
    UnexpectedRuntimeError        = 99
};

// Diagnostic runtime state. Single-instance — the diagnostic session is exclusive.
struct RuntimeState {
    std::mutex mutex;
    XrInstance instance{XR_NULL_HANDLE};
    XrSession session{XR_NULL_HANDLE};
    XrSystemId systemId{XR_NULL_SYSTEM_ID};
    std::atomic<bool> running{false};
    std::atomic<bool> exitRequested{false};
    bool equirect2Supported{false};
    std::vector<std::string> presentExtensions;
};

RuntimeState g_state;

bool extensionPresent(const std::vector<std::string>& names, const char* needle) {
    for (const auto& n : names) {
        if (n == needle) return true;
    }
    return false;
}

NativeResult enumerateAndCacheExtensions(RuntimeState& s) {
    uint32_t count = 0;
    XrResult r = xrEnumerateInstanceExtensionProperties(nullptr, 0, &count, nullptr);
    if (XR_FAILED(r) || count == 0) {
        LOGW("xrEnumerateInstanceExtensionProperties initial call failed: %d", (int)r);
        return NativeResult::LoaderUnavailable;
    }
    std::vector<XrExtensionProperties> props(count, {XR_TYPE_EXTENSION_PROPERTIES});
    r = xrEnumerateInstanceExtensionProperties(nullptr, count, &count, props.data());
    if (XR_FAILED(r)) {
        LOGE("xrEnumerateInstanceExtensionProperties fill call failed: %d", (int)r);
        return NativeResult::LoaderUnavailable;
    }
    s.presentExtensions.clear();
    s.presentExtensions.reserve(count);
    for (uint32_t i = 0; i < count; ++i) {
        s.presentExtensions.emplace_back(props[i].extensionName);
    }
    // S0249 §6 item 2: probe XR_KHR_composition_layer_equirect2 — if available we use the
    // system compositor for the projection; otherwise Phase 03 falls back to a sphere mesh.
    s.equirect2Supported = extensionPresent(s.presentExtensions, "XR_KHR_composition_layer_equirect2");
    LOGD("Extensions probed: %u total, equirect2=%d", count, s.equirect2Supported ? 1 : 0);
    return NativeResult::Ok;
}

NativeResult createInstanceLocked(RuntimeState& s, JavaVM* vm, jobject contextOrApplication) {
    if (s.instance != XR_NULL_HANDLE) {
        return NativeResult::Ok;
    }

    std::vector<const char*> required = {
        XR_KHR_ANDROID_CREATE_INSTANCE_EXTENSION_NAME,
        XR_KHR_OPENGL_ES_ENABLE_EXTENSION_NAME
    };
    for (const char* ext : required) {
        if (!extensionPresent(s.presentExtensions, ext)) {
            LOGE("Required extension '%s' not available on runtime", ext);
            return NativeResult::InstanceCreationFailed;
        }
    }

    XrInstanceCreateInfoAndroidKHR androidInfo{XR_TYPE_INSTANCE_CREATE_INFO_ANDROID_KHR};
    androidInfo.applicationVM = vm;
    androidInfo.applicationActivity = contextOrApplication;

    XrInstanceCreateInfo createInfo{XR_TYPE_INSTANCE_CREATE_INFO};
    createInfo.next = &androidInfo;
    createInfo.enabledExtensionCount = static_cast<uint32_t>(required.size());
    createInfo.enabledExtensionNames = required.data();
    std::string appName = "FastMediaSorter-Diag";
    std::snprintf(createInfo.applicationInfo.applicationName,
                  sizeof(createInfo.applicationInfo.applicationName),
                  "%s", appName.c_str());
    createInfo.applicationInfo.applicationVersion = 1;
    std::snprintf(createInfo.applicationInfo.engineName,
                  sizeof(createInfo.applicationInfo.engineName),
                  "FastMediaSorter");
    createInfo.applicationInfo.engineVersion = 1;
    createInfo.applicationInfo.apiVersion = XR_CURRENT_API_VERSION;

    XrResult r = xrCreateInstance(&createInfo, &s.instance);
    if (XR_FAILED(r)) {
        LOGE("xrCreateInstance failed: %d", (int)r);
        return NativeResult::InstanceCreationFailed;
    }

    XrSystemGetInfo systemInfo{XR_TYPE_SYSTEM_GET_INFO};
    systemInfo.formFactor = XR_FORM_FACTOR_HEAD_MOUNTED_DISPLAY;
    r = xrGetSystem(s.instance, &systemInfo, &s.systemId);
    if (XR_FAILED(r) || s.systemId == XR_NULL_SYSTEM_ID) {
        LOGW("xrGetSystem failed: %d", (int)r);
        return NativeResult::SystemNotFound;
    }
    LOGD("Instance + system acquired (systemId=%llu)", (unsigned long long)s.systemId);
    return NativeResult::Ok;
}

void destroyEverythingLocked(RuntimeState& s) {
    if (s.session != XR_NULL_HANDLE) {
        xrDestroySession(s.session);
        s.session = XR_NULL_HANDLE;
    }
    if (s.instance != XR_NULL_HANDLE) {
        xrDestroyInstance(s.instance);
        s.instance = XR_NULL_HANDLE;
    }
    s.systemId = XR_NULL_SYSTEM_ID;
    s.running.store(false);
    s.exitRequested.store(false);
}

} // namespace

// ============================================================================
// JNI surface — keep method signatures stable; the Kotlin side binds them.
// ============================================================================

extern "C" {

JNIEXPORT jint JNICALL
Java_com_sza_fastmediasorter_core_xr_runtime_NativeDiagnosticXrRuntime_nativeProbeExtensions(
    JNIEnv* env, jobject /*thisObj*/) {
    std::lock_guard<std::mutex> lock(g_state.mutex);
    return static_cast<jint>(enumerateAndCacheExtensions(g_state));
}

JNIEXPORT jboolean JNICALL
Java_com_sza_fastmediasorter_core_xr_runtime_NativeDiagnosticXrRuntime_nativeHasEquirect2(
    JNIEnv* env, jobject /*thisObj*/) {
    std::lock_guard<std::mutex> lock(g_state.mutex);
    return g_state.equirect2Supported ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_com_sza_fastmediasorter_core_xr_runtime_NativeDiagnosticXrRuntime_nativeStartSession(
    JNIEnv* env, jobject /*thisObj*/, jobject contextObj) {
    if (g_state.running.load()) {
        return static_cast<jint>(NativeResult::AlreadyRunning);
    }
    JavaVM* vm = nullptr;
    if (env->GetJavaVM(&vm) != JNI_OK) {
        return static_cast<jint>(NativeResult::UnexpectedRuntimeError);
    }
    std::lock_guard<std::mutex> lock(g_state.mutex);
    NativeResult extResult = enumerateAndCacheExtensions(g_state);
    if (extResult != NativeResult::Ok) {
        return static_cast<jint>(extResult);
    }
    NativeResult inst = createInstanceLocked(g_state, vm, contextObj);
    if (inst != NativeResult::Ok) {
        destroyEverythingLocked(g_state);
        return static_cast<jint>(inst);
    }
    // Session + swapchain creation lands when Phase 03 wires the GLES context and asset.
    // For Phase 02 we acknowledge the instance came up and stop — the gateway layer maps
    // this intermediate state to XrEntryResult.InitializationFailed.
    g_state.running.store(true);
    LOGD("Phase 02: instance ready, session deferred to Phase 03");
    return static_cast<jint>(NativeResult::SessionCreationFailed);
}

JNIEXPORT jint JNICALL
Java_com_sza_fastmediasorter_core_xr_runtime_NativeDiagnosticXrRuntime_nativePresentStaticImage(
    JNIEnv* env, jobject /*thisObj*/, jbyteArray /*imageBytes*/, jint /*width*/, jint /*height*/) {
    if (!g_state.running.load()) {
        return static_cast<jint>(NativeResult::NotRunning);
    }
    // Phase 03 ships the asset bridge + GLES texture upload; Phase 02 leaves a structured
    // not-ready response.
    LOGW("nativePresentStaticImage called before Phase 03 wires the asset pipeline");
    return static_cast<jint>(NativeResult::FramePresentFailed);
}

JNIEXPORT void JNICALL
Java_com_sza_fastmediasorter_core_xr_runtime_NativeDiagnosticXrRuntime_nativeRequestExit(
    JNIEnv* env, jobject /*thisObj*/) {
    g_state.exitRequested.store(true);
    std::lock_guard<std::mutex> lock(g_state.mutex);
    destroyEverythingLocked(g_state);
    LOGD("nativeRequestExit: session torn down");
}

JNIEXPORT jboolean JNICALL
Java_com_sza_fastmediasorter_core_xr_runtime_NativeDiagnosticXrRuntime_nativeIsRunning(
    JNIEnv* env, jobject /*thisObj*/) {
    return g_state.running.load() ? JNI_TRUE : JNI_FALSE;
}

} // extern "C"
