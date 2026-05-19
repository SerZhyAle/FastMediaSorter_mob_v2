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
#include <cstdio>

// Platform + graphics binding selectors for OpenXR. The GLES headers must be visible
// BEFORE `openxr_platform.h` because the latter references `EGLenum` and `GLenum` in its
// `XrSwapchainImageOpenGLESKHR` family of structs.
#define XR_USE_PLATFORM_ANDROID
#define XR_USE_GRAPHICS_API_OPENGL_ES

#include <EGL/egl.h>
#include <GLES3/gl3.h>

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
    // S0249 Phase 05: input action set scaffolding. Real action handles are created in
    // [createInputActionSetLocked] once an instance exists. Polling
    // happens on the render thread via [pollInputAnyTriggeredLocked].
    XrActionSet actionSet{XR_NULL_HANDLE};
    XrAction anyButtonAction{XR_NULL_HANDLE};
    XrAction anyTriggerAction{XR_NULL_HANDLE};
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

// S0249 Phase 05: minimal "any controller input" action set. Two boolean actions wired to
// every interaction profile we care about (Khronos Simple, Oculus Touch, etc.). The render
// loop polls them and treats any change to "active=true" as exit-request.
//
// At Phase 05 the session is not yet bound (deferred from Phase 02), so action creation runs
// on the bare instance. xrCreateAction requires only the actionSet which only requires the
// instance, so this is safe. Suggested-bindings + xrAttachSessionActionSets land when the
// session is finally created (open work from Phase 02).
NativeResult createInputActionSetLocked(RuntimeState& s) {
    if (s.instance == XR_NULL_HANDLE) return NativeResult::InstanceCreationFailed;
    if (s.actionSet != XR_NULL_HANDLE) return NativeResult::Ok;

    XrActionSetCreateInfo asInfo{XR_TYPE_ACTION_SET_CREATE_INFO};
    std::snprintf(asInfo.actionSetName, sizeof(asInfo.actionSetName), "%s", "diagnostic_exit");
    std::snprintf(asInfo.localizedActionSetName,
                  sizeof(asInfo.localizedActionSetName),
                  "%s",
                  "Diagnostic exit");
    asInfo.priority = 0;
    XrResult r = xrCreateActionSet(s.instance, &asInfo, &s.actionSet);
    if (XR_FAILED(r)) {
        LOGE("xrCreateActionSet failed: %d", (int)r);
        return NativeResult::UnexpectedRuntimeError;
    }

    auto makeBoolAction = [&](const char* name, const char* localized, XrAction* out) -> XrResult {
        XrActionCreateInfo ai{XR_TYPE_ACTION_CREATE_INFO};
        std::snprintf(ai.actionName, sizeof(ai.actionName), "%s", name);
        std::snprintf(ai.localizedActionName, sizeof(ai.localizedActionName), "%s", localized);
        ai.actionType = XR_ACTION_TYPE_BOOLEAN_INPUT;
        ai.countSubactionPaths = 0;
        return xrCreateAction(s.actionSet, &ai, out);
    };
    r = makeBoolAction("any_button", "Any button", &s.anyButtonAction);
    if (XR_FAILED(r)) { LOGE("xrCreateAction(any_button) failed: %d", (int)r); return NativeResult::UnexpectedRuntimeError; }
    r = makeBoolAction("any_trigger", "Any trigger", &s.anyTriggerAction);
    if (XR_FAILED(r)) { LOGE("xrCreateAction(any_trigger) failed: %d", (int)r); return NativeResult::UnexpectedRuntimeError; }

    LOGD("S0249 Phase 05: input action set created (anyButton + anyTrigger)");
    return NativeResult::Ok;
}

// S0249 Phase 05: per-frame poll. Returns true when any tracked action transitioned to
// 'true' since the last poll. The full render loop will call this after [xrSyncActions]
// and propagate `true` to the Kotlin layer via JNI to set [exitRequested].
bool pollInputAnyTriggeredLocked(RuntimeState& s) {
    if (s.session == XR_NULL_HANDLE || s.actionSet == XR_NULL_HANDLE) return false;

    XrActiveActionSet active{s.actionSet, XR_NULL_PATH};
    XrActionsSyncInfo syncInfo{XR_TYPE_ACTIONS_SYNC_INFO};
    syncInfo.countActiveActionSets = 1;
    syncInfo.activeActionSets = &active;
    XrResult r = xrSyncActions(s.session, &syncInfo);
    if (XR_FAILED(r)) {
        LOGW("xrSyncActions failed: %d", (int)r);
        return false;
    }

    auto stateChanged = [&](XrAction action) -> bool {
        XrActionStateGetInfo info{XR_TYPE_ACTION_STATE_GET_INFO};
        info.action = action;
        XrActionStateBoolean st{XR_TYPE_ACTION_STATE_BOOLEAN};
        XrResult get = xrGetActionStateBoolean(s.session, &info, &st);
        if (XR_FAILED(get)) {
            LOGW("xrGetActionStateBoolean failed: %d", (int)get);
            return false;
        }
        return st.changedSinceLastSync == XR_TRUE && st.currentState == XR_TRUE;
    };
    return stateChanged(s.anyButtonAction) || stateChanged(s.anyTriggerAction);
}

void destroyEverythingLocked(RuntimeState& s) {
    if (s.anyButtonAction != XR_NULL_HANDLE) {
        xrDestroyAction(s.anyButtonAction);
        s.anyButtonAction = XR_NULL_HANDLE;
    }
    if (s.anyTriggerAction != XR_NULL_HANDLE) {
        xrDestroyAction(s.anyTriggerAction);
        s.anyTriggerAction = XR_NULL_HANDLE;
    }
    if (s.actionSet != XR_NULL_HANDLE) {
        xrDestroyActionSet(s.actionSet);
        s.actionSet = XR_NULL_HANDLE;
    }
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
    // S0249 Phase 05: build the input action set on the bare instance. The session attach
    // step (`xrAttachSessionActionSets`) is part of the not-yet-implemented session lifecycle.
    NativeResult actions = createInputActionSetLocked(g_state);
    if (actions != NativeResult::Ok) {
        LOGW("S0249 Phase 05: action set creation failed (%d); session bring-up continues",
             (int)actions);
    }
    // Session + swapchain creation lands in a follow-on ticket (requires EGL context +
    // GLES texture upload from the bundled asset). For now we acknowledge the instance
    // came up and stop — the gateway layer maps this intermediate state to
    // XrEntryResult.InitializationFailed.
    g_state.running.store(true);
    LOGD("S0249: instance + action set ready; session bring-up still TODO");
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

JNIEXPORT jboolean JNICALL
Java_com_sza_fastmediasorter_core_xr_runtime_NativeDiagnosticXrRuntime_nativePollExitTriggered(
    JNIEnv* env, jobject /*thisObj*/) {
    std::lock_guard<std::mutex> lock(g_state.mutex);
    if (g_state.exitRequested.load()) return JNI_TRUE;
    bool triggered = pollInputAnyTriggeredLocked(g_state);
    if (triggered) {
        g_state.exitRequested.store(true);
        LOGD("S0249: native action triggered exit");
    }
    return triggered ? JNI_TRUE : JNI_FALSE;
}

} // extern "C"
