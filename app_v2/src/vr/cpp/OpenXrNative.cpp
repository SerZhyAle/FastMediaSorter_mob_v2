// OpenXR native bridge for FastMediaSorter VR flavor.
//
// Owns one XR instance/system/session/swapchain pair, drives the event+render loop,
// and callbacks into Kotlin per-eye so VrStereoRenderer can draw into the provided FBO.
//
// All OpenXR calls live here; Kotlin just owns lifecycle + renderer + per-frame callback.

#include <jni.h>
#include <android/log.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
// AndroidBitmap_lockPixels / unlockPixels / getInfo for HUD upload (spec_vr-immersive-hud-gl).
#include <android/bitmap.h>

#include <EGL/egl.h>
#include <GLES3/gl3.h>

#include <openxr/openxr.h>
#include <openxr/openxr_platform.h>

// ─── Hand-tracking extensions — defensive local declarations ──────────────────
// XR_EXT_hand_tracking is part of mainline openxr.h since 1.0.21; XR_META_* are
// vendor-specific and ship in the Meta OpenXR Mobile SDK headers which are NOT
// guaranteed to be in the Khronos AAR. To keep the build independent of SDK
// header availability, declare the Meta structures locally when missing, and
// dispatch every function via xrGetInstanceProcAddr at runtime.
// Extension-name macros are referenced unconditionally below, so guard each with
// an individual #ifndef — openxr.h defines them inside the same extension block
// as the types, but ordering of `#define` guard vs name-string varies by SDK
// version, so duplicating them here is the only way to guarantee the symbol
// exists regardless of header state.
#ifndef XR_EXT_HAND_TRACKING_EXTENSION_NAME
#define XR_EXT_HAND_TRACKING_EXTENSION_NAME "XR_EXT_hand_tracking"
#endif
#ifndef XR_META_HAND_TRACKING_AIM_EXTENSION_NAME
#define XR_META_HAND_TRACKING_AIM_EXTENSION_NAME "XR_META_hand_tracking_aim"
#endif
#ifndef XR_FB_HAND_TRACKING_AIM_EXTENSION_NAME
#define XR_FB_HAND_TRACKING_AIM_EXTENSION_NAME "XR_FB_hand_tracking_aim"
#endif
#ifndef XR_META_HAND_TRACKING_MICROGESTURES_EXTENSION_NAME
#define XR_META_HAND_TRACKING_MICROGESTURES_EXTENSION_NAME "XR_META_hand_tracking_microgestures"
#endif

#ifndef XR_EXT_hand_tracking
#define XR_TYPE_SYSTEM_HAND_TRACKING_PROPERTIES_EXT static_cast<XrStructureType>(1000051000)
#define XR_TYPE_HAND_TRACKER_CREATE_INFO_EXT static_cast<XrStructureType>(1000051001)
#define XR_TYPE_HAND_JOINTS_LOCATE_INFO_EXT static_cast<XrStructureType>(1000051002)
#define XR_TYPE_HAND_JOINT_LOCATIONS_EXT static_cast<XrStructureType>(1000051003)
#define XR_TYPE_HAND_JOINT_VELOCITIES_EXT static_cast<XrStructureType>(1000051004)
XR_DEFINE_HANDLE(XrHandTrackerEXT)
typedef enum XrHandEXT
{
    XR_HAND_LEFT_EXT = 1,
    XR_HAND_RIGHT_EXT = 2,
} XrHandEXT;
typedef enum XrHandJointSetEXT
{
    XR_HAND_JOINT_SET_DEFAULT_EXT = 0,
} XrHandJointSetEXT;
#define XR_HAND_JOINT_COUNT_EXT 26
typedef struct XrHandTrackerCreateInfoEXT
{
    XrStructureType type;
    const void *next;
    XrHandEXT hand;
    XrHandJointSetEXT handJointSet;
} XrHandTrackerCreateInfoEXT;
typedef struct XrHandJointsLocateInfoEXT
{
    XrStructureType type;
    const void *next;
    XrSpace baseSpace;
    XrTime time;
} XrHandJointsLocateInfoEXT;
typedef struct XrHandJointLocationEXT
{
    XrSpaceLocationFlags locationFlags;
    XrPosef pose;
    float radius;
} XrHandJointLocationEXT;
typedef struct XrHandJointLocationsEXT
{
    XrStructureType type;
    void *next;
    XrBool32 isActive;
    uint32_t jointCount;
    XrHandJointLocationEXT *jointLocations;
} XrHandJointLocationsEXT;
#endif // XR_EXT_hand_tracking

// XR_META_hand_tracking_aim / XR_FB_hand_tracking_aim — ray + pinch strength
// chained onto joint locations. Meta and FB variants share the same struct +
// flag layout, so either header makes the types available. Guard on BOTH so we
// never redeclare types when only the FB alias ships in the Khronos headers.
#if !defined(XR_META_hand_tracking_aim) && !defined(XR_FB_hand_tracking_aim)
#define XR_TYPE_HAND_TRACKING_AIM_STATE_FB static_cast<XrStructureType>(1000111001)
typedef XrFlags64 XrHandTrackingAimFlagsFB;
static constexpr XrHandTrackingAimFlagsFB XR_HAND_TRACKING_AIM_COMPUTED_BIT_FB = 0x00000001;
static constexpr XrHandTrackingAimFlagsFB XR_HAND_TRACKING_AIM_VALID_BIT_FB = 0x00000002;
static constexpr XrHandTrackingAimFlagsFB XR_HAND_TRACKING_AIM_INDEX_PINCHING_BIT_FB = 0x00000004;
static constexpr XrHandTrackingAimFlagsFB XR_HAND_TRACKING_AIM_MIDDLE_PINCHING_BIT_FB = 0x00000008;
static constexpr XrHandTrackingAimFlagsFB XR_HAND_TRACKING_AIM_RING_PINCHING_BIT_FB = 0x00000010;
static constexpr XrHandTrackingAimFlagsFB XR_HAND_TRACKING_AIM_LITTLE_PINCHING_BIT_FB = 0x00000020;
static constexpr XrHandTrackingAimFlagsFB XR_HAND_TRACKING_AIM_SYSTEM_GESTURE_BIT_FB = 0x00000040;
static constexpr XrHandTrackingAimFlagsFB XR_HAND_TRACKING_AIM_DOMINANT_HAND_BIT_FB = 0x00000080;
static constexpr XrHandTrackingAimFlagsFB XR_HAND_TRACKING_AIM_MENU_PRESSED_BIT_FB = 0x00000100;
typedef struct XrHandTrackingAimStateFB
{
    XrStructureType type;
    void *next;
    XrHandTrackingAimFlagsFB status;
    XrPosef aimPose;
    float pinchStrengthIndex;
    float pinchStrengthMiddle;
    float pinchStrengthRing;
    float pinchStrengthLittle;
} XrHandTrackingAimStateFB;
#endif // !XR_META_hand_tracking_aim && !XR_FB_hand_tracking_aim

// XR_META_hand_tracking_microgestures — per-hand swipe enum chained onto joint locations.
// Horizon OS v62+ only; declare defensively and disable at runtime when missing.
// Some Khronos-distributed Android headers advertise the extension name macro
// but omit the actual META typedefs/structure constants. Guard on the concrete
// structure symbol instead so our local fallback still compiles in that case.
#ifndef XR_TYPE_HAND_MICROGESTURES_STATE_META
#define XR_TYPE_HAND_MICROGESTURES_STATE_META static_cast<XrStructureType>(1000265000)
typedef XrFlags64 XrHandMicrogestureFlagsMETA;
static constexpr XrHandMicrogestureFlagsMETA XR_HAND_MICROGESTURE_SWIPE_LEFT_META = 0x00000001;
static constexpr XrHandMicrogestureFlagsMETA XR_HAND_MICROGESTURE_SWIPE_RIGHT_META = 0x00000002;
static constexpr XrHandMicrogestureFlagsMETA XR_HAND_MICROGESTURE_SWIPE_UP_META = 0x00000004;
static constexpr XrHandMicrogestureFlagsMETA XR_HAND_MICROGESTURE_SWIPE_DOWN_META = 0x00000008;
typedef struct XrHandMicrogesturesStateMETA
{
    XrStructureType type;
    void *next;
    XrHandMicrogestureFlagsMETA currentGestures;
    XrHandMicrogestureFlagsMETA gesturesSinceLastSync;
} XrHandMicrogesturesStateMETA;
#endif // XR_TYPE_HAND_MICROGESTURES_STATE_META

// Function pointer typedefs for runtime resolution via xrGetInstanceProcAddr.
typedef XrResult(XRAPI_PTR *PFN_xrCreateHandTrackerEXT_LOCAL)(
    XrSession, const XrHandTrackerCreateInfoEXT *, XrHandTrackerEXT *);
typedef XrResult(XRAPI_PTR *PFN_xrDestroyHandTrackerEXT_LOCAL)(XrHandTrackerEXT);
typedef XrResult(XRAPI_PTR *PFN_xrLocateHandJointsEXT_LOCAL)(
    XrHandTrackerEXT, const XrHandJointsLocateInfoEXT *, XrHandJointLocationsEXT *);

#include <dlfcn.h>

#include <atomic>
#include <algorithm>
#include <cstdarg>
#include <cstring>
#include <ctime>
#include <mutex>
#include <string>
#include <vector>

#define LOG_TAG "OpenXrNative"

// Native log forwarding buffer: every LOG* emits to Android logcat (as before)
// AND appends to this ring. Kotlin drains it via nativeDrainLog() so OpenXR
// diagnostics land in fastmediasorter_*.log alongside Timber output — without
// that, `nativeInitialize returned false` arrives at the user without any
// trace of which xr* call actually failed.
static std::mutex g_logBufferMutex;
static std::vector<std::string> g_logBuffer;
static constexpr size_t kLogBufferMaxEntries = 512;

static void nativeLogBufferAppend(char prioChar, const char *msg)
{
    std::lock_guard<std::mutex> lock(g_logBufferMutex);
    if (g_logBuffer.size() >= kLogBufferMaxEntries)
    {
        g_logBuffer.erase(g_logBuffer.begin(),
                          g_logBuffer.begin() + (g_logBuffer.size() - kLogBufferMaxEntries + 1));
    }
    std::string entry;
    entry.reserve(2 + std::strlen(msg));
    entry.push_back(prioChar);
    entry.push_back('|');
    entry.append(msg);
    g_logBuffer.emplace_back(std::move(entry));
}

static void nativeLogEmit(int androidPrio, const char *fmt, ...) __attribute__((format(printf, 2, 3)));
static void nativeLogEmit(int androidPrio, const char *fmt, ...)
{
    char buf[1024];
    va_list args;
    va_start(args, fmt);
    const int written = vsnprintf(buf, sizeof(buf), fmt, args);
    va_end(args);
    if (written < 0)
    {
        return;
    }
    __android_log_write(androidPrio, LOG_TAG, buf);
    char prioChar;
    switch (androidPrio)
    {
    case ANDROID_LOG_ERROR:
        prioChar = 'E';
        break;
    case ANDROID_LOG_WARN:
        prioChar = 'W';
        break;
    case ANDROID_LOG_INFO:
        prioChar = 'I';
        break;
    case ANDROID_LOG_DEBUG:
        prioChar = 'D';
        break;
    default:
        prioChar = 'V';
        break;
    }
    nativeLogBufferAppend(prioChar, buf);
}

#define LOGI(...) nativeLogEmit(ANDROID_LOG_INFO, __VA_ARGS__)
#define LOGW(...) nativeLogEmit(ANDROID_LOG_WARN, __VA_ARGS__)
#define LOGE(...) nativeLogEmit(ANDROID_LOG_ERROR, __VA_ARGS__)
#define LOGD(...) nativeLogEmit(ANDROID_LOG_DEBUG, __VA_ARGS__)

// ─── Debug helpers ────────────────────────────────────────────────────────────

/** Human-readable XrSessionState name — used in every state log so grep is easy. */
static constexpr const char *xrSessionStateName(XrSessionState s)
{
    switch (s)
    {
    case XR_SESSION_STATE_UNKNOWN:
        return "UNKNOWN";
    case XR_SESSION_STATE_IDLE:
        return "IDLE";
    case XR_SESSION_STATE_READY:
        return "READY";
    case XR_SESSION_STATE_SYNCHRONIZED:
        return "SYNCHRONIZED";
    case XR_SESSION_STATE_VISIBLE:
        return "VISIBLE";
    case XR_SESSION_STATE_FOCUSED:
        return "FOCUSED";
    case XR_SESSION_STATE_STOPPING:
        return "STOPPING";
    case XR_SESSION_STATE_LOSS_PENDING:
        return "LOSS_PENDING";
    case XR_SESSION_STATE_EXITING:
        return "EXITING";
    default:
        return "?UNKNOWN_STATE";
    }
}

/** Human-readable XrStructureType name for event debug. */
static const char *xrEventTypeName(XrStructureType t)
{
    switch (t)
    {
    case XR_TYPE_EVENT_DATA_SESSION_STATE_CHANGED:
        return "SESSION_STATE_CHANGED";
    case XR_TYPE_EVENT_DATA_INSTANCE_LOSS_PENDING:
        return "INSTANCE_LOSS_PENDING";
    case XR_TYPE_EVENT_DATA_INTERACTION_PROFILE_CHANGED:
        return "INTERACTION_PROFILE_CHANGED";
    case XR_TYPE_EVENT_DATA_REFERENCE_SPACE_CHANGE_PENDING:
        return "REFERENCE_SPACE_CHANGE_PENDING";
    default:
        return "OTHER";
    }
}

namespace
{

    constexpr XrViewConfigurationType kViewConfig = XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO;
    constexpr XrFormFactor kFormFactor = XR_FORM_FACTOR_HEAD_MOUNTED_DISPLAY;
    constexpr XrReferenceSpaceType kSpaceType = XR_REFERENCE_SPACE_TYPE_LOCAL;
    constexpr uint32_t kViewCount = 2;

    enum class LayerType : int32_t
    {
        Projection = 0,
        QuadCinema = 1,
        Equirect2 = 2,
        Cylinder = 3,
    };

    struct LayerConfig
    {
        LayerType type = LayerType::QuadCinema;
        float widthMeters = 4.0f;
        float heightMeters = 2.25f;
        float distanceMeters = 4.0f;
        float radiusMeters = 1.0f;
        float centralHorizontalAngle = 6.2831855f;
        float upperVerticalAngle = 1.5707964f;
        float lowerVerticalAngle = -1.5707964f;
    };

    struct SwapchainImage
    {
        uint32_t imageId = 0; // GL texture ID from XrSwapchainImageOpenGLESKHR
        uint32_t fbo = 0;     // FBO with imageId bound as color attachment 0
    };

    struct EyeSwapchain
    {
        XrSwapchain handle = XR_NULL_HANDLE;
        uint32_t width = 0;
        uint32_t height = 0;
        std::vector<SwapchainImage> images;
    };

    struct XrCtx
    {
        // JavaVM for thread-attachment when calling back into Kotlin.
        JavaVM *vm = nullptr;

        // Global ref to the Kotlin callback instance; invoked per eye.
        jobject callbackRef = nullptr;
        jmethodID onRenderEyeMethod = nullptr;

        XrInstance instance = XR_NULL_HANDLE;
        XrSystemId systemId = XR_NULL_SYSTEM_ID;
        XrSession session = XR_NULL_HANDLE;
        XrSpace appSpace = XR_NULL_HANDLE;
        // Head-locked reference space — owns the HUD quad pose so the HUD travels with the
        // user's head independently of the LOCAL/STAGE-anchored video layer.
        XrSpace viewSpace = XR_NULL_HANDLE;

        XrSessionState sessionState = XR_SESSION_STATE_UNKNOWN;
        bool sessionRunning = false;
        std::atomic<bool> exitRequested{false};
        // Cached from xrWaitFrame; read by syncControllerAimRay on the next frame.
        XrTime lastPredictedDisplayTime = 0;

        // Shared EGL (created by Kotlin GL thread before initialize()).
        EGLDisplay eglDisplay = EGL_NO_DISPLAY;
        EGLContext eglContext = EGL_NO_CONTEXT;
        EGLConfig eglConfig = nullptr;

        bool supportsEquirect2 = false;
        bool supportsCylinder = false;
        bool warnedMissingEquirect2 = false;
        bool warnedMissingCylinder = false;
        // Hand-tracking extension availability — populated in enumerateAndCreateInstance()
        // and gated on during session setup / per-frame sync.
        bool supportsHandTracking = false;
        bool supportsHandAim = false;
        bool supportsMicrogestures = false;
        LayerConfig layerConfig{};

        EyeSwapchain eyes[kViewCount];

        // === OPENXR INPUT SYSTEM ===
        // Handles and state for the per-frame action sync + edge detection performed
        // on the xr-render-thread. Events are pushed to Kotlin via a global-ref
        // callback; all XrAction handles live for the lifetime of the session.
        struct InputSystem
        {
            XrActionSet actionSet = XR_NULL_HANDLE;
            XrAction aPauseToggle = XR_NULL_HANDLE;
            XrAction bExit = XR_NULL_HANDLE;
            XrAction xExit = XR_NULL_HANDLE;
            XrAction yFileOps = XR_NULL_HANDLE;
            XrAction menuCtrl = XR_NULL_HANDLE;
            XrAction thumbClickL = XR_NULL_HANDLE;
            XrAction thumbClickR = XR_NULL_HANDLE;
            XrAction gripL = XR_NULL_HANDLE;
            XrAction gripR = XR_NULL_HANDLE;
            XrAction stickL = XR_NULL_HANDLE;
            XrAction stickR = XR_NULL_HANDLE;
            XrAction hapticL = XR_NULL_HANDLE;
            XrAction hapticR = XR_NULL_HANDLE;

            // Edge-detection state (previous frame).
            bool prevA = false, prevB = false, prevX = false, prevY = false;
            bool prevMenu = false, prevThumbL = false, prevThumbR = false;
            float prevGripL = 0.0f, prevGripR = 0.0f;

            // Stick hysteresis: separate edge flag for X and Y axes of each stick.
            bool stickLEdgeXActive = false, stickLEdgeYActive = false;
            bool stickREdgeXActive = false, stickREdgeYActive = false;

            // Y long-press detection for short (file ops) vs long (cheatsheet) release.
            int64_t yPressTimeNs = 0;
            bool yLongPressEmitted = false;

            // Both-grips hold → zoom reset (1 s guard).
            int64_t bothGripsPressTimeNs = 0;
            bool bothGripsResetEmitted = false;

            // Kotlin callback — set via nativeSetInputCallback JNI entry point.
            jobject inputCallbackRef = nullptr;
            jmethodID onInputEventMethod = nullptr;
            jmethodID onPointerMoveMethod = nullptr;
            // spec_vr-immersive-controls-panel Phase 02: controller aim ray.
            jmethodID onControllerPointerMoveMethod = nullptr;

            // Controller aim spaces (one per hand) — created after session init.
            XrAction aimPoseL = XR_NULL_HANDLE;
            XrAction aimPoseR = XR_NULL_HANDLE;
            XrSpace  aimSpaceL = XR_NULL_HANDLE;
            XrSpace  aimSpaceR = XR_NULL_HANDLE;
            // Trigger actions — used to detect trigger hold for seek-drag (Phase 05).
            XrAction triggerL = XR_NULL_HANDLE;
            XrAction triggerR = XR_NULL_HANDLE;

            // Last controller-edge emission timestamp (monotonic ns). Drives the
            // modality gate: while controllers are active (events within 2 s),
            // hand tracking is suppressed to avoid duplicate UI triggers.
            int64_t lastControllerEventNs = 0;

            bool initialized = false;
        } input;

        // Controller ray visibility flag (spec_vr-immersive-controls-panel Phase 02).
        // When false the GL ray primitive is skipped but NDC is still emitted.
        std::atomic<bool> controllerRayEnabled{true};

        // ═════════════════════════════════════════════════════════════════
        // Hand-tracking subsystem (spec_vr-hand-tracking-tech §5).
        // Parallel input surface alongside InputSystem. C++ polls aim + pinch
        // + microgestures per frame, hit-tests against the UI composition
        // plane, and emits pointer/click/swipe events through the same JNI
        // callback (distinguished by `source=SOURCE_HAND`).
        // ═════════════════════════════════════════════════════════════════
        struct HandSystem
        {
            // Dynamically resolved function pointers (nullptr when the runtime
            // does not advertise the extension — hand tracking stays off).
            PFN_xrCreateHandTrackerEXT_LOCAL pfnCreate = nullptr;
            PFN_xrDestroyHandTrackerEXT_LOCAL pfnDestroy = nullptr;
            PFN_xrLocateHandJointsEXT_LOCAL pfnLocate = nullptr;

            XrHandTrackerEXT trackerL = XR_NULL_HANDLE;
            XrHandTrackerEXT trackerR = XR_NULL_HANDLE;

            // Joint buffers — reused every frame to avoid per-frame alloc.
            XrHandJointLocationEXT jointsL[XR_HAND_JOINT_COUNT_EXT];
            XrHandJointLocationEXT jointsR[XR_HAND_JOINT_COUNT_EXT];

            // Pinch state with hysteresis — press at 0.9, release at 0.6.
            bool isPinchingL = false;
            bool isPinchingR = false;
            bool suppressClickReleaseL = false;
            bool suppressClickReleaseR = false;

            // DOUBLE_PINCH is a session-level gesture, not a per-hand command.
            // Keep one shared timestamp so a second pinch on either hand within the
            // gesture window can toggle pause/play without depending on Kotlin timing.
            int64_t lastPinchDownNs = 0;

            // Aim-ray freeze latch: XY held from the pre-pinch frame to prevent
            // cursor shift during pinch closure (mitigation for §6 risk).
            bool aimFrozenL = false, aimFrozenR = false;
            float frozenAimXL = 0.0f, frozenAimYL = 0.0f;
            float frozenAimXR = 0.0f, frozenAimYR = 0.0f;

            // Microgestures cached for one-shot edge detection (gesturesSinceLastSync
            // accumulates flags; we emit each bit once per frame it becomes set).
            XrHandMicrogestureFlagsMETA prevGesturesL = 0;
            XrHandMicrogestureFlagsMETA prevGesturesR = 0;

            bool initialized = false;
        } hands;

        struct StereoSnapshot
        {
            std::atomic<bool> requested{false};
            // ready is atomic to allow the render thread to set it without holding
            // g_ctxMutex, while JNI polling reads it under the mutex. A release
            // store on the render side and acquire load on the JNI side guarantee
            // that all pixel writes are visible before ready is observed as true.
            std::atomic<bool> ready{false};
            uint32_t width = 0;
            uint32_t height = 0;
            std::vector<jint> eyePixels[kViewCount];
        } stereoSnapshot;

        // HUD composition layer (spec_vr-immersive-hud-gl).
        // Phase 01 declares fields only; Phase 02 wires xrCreateSwapchain +
        // XrCompositionLayerQuad in xrEndFrame; Phase 03 fills the texture from
        // an Android Bitmap. While hudLayerVisible is false the layer is omitted
        // from xrEndFrame for zero overdraw.
        XrSwapchain hudSwapchain = XR_NULL_HANDLE;
        uint32_t hudSwapchainWidth = 0;
        uint32_t hudSwapchainHeight = 0;
        std::atomic<bool> hudLayerVisible{false};
        std::vector<SwapchainImage> hudSwapchainImages;
        // The runtime requires exactly one xrAcquire/xrRelease pair per swapchain
        // per frame. The render-thread drain block sets this true after each GL upload;
        // the HUD pump reads and clears it to decide whether a dummy cycle is needed.
        std::atomic<bool> hudFrameUploaded{false};
        // WHY: glTexSubImage2D must run on the EGL-owning render thread. nativeUploadHudBitmap
        // is called from the Kotlin main thread (no EGL context there), so it copies pixel data
        // into this staging buffer. renderFrame reads it on the correct thread and does the
        // actual GL upload. Access to both fields is serialised by g_ctxMutex.
        std::vector<uint8_t> hudPendingPixels;
        std::atomic<bool> hudBitmapPending{false};

        // Interactive GL panel swapchain (spec_vr-immersive-controls-panel Phase 03).
        // Mirrors the HUD swapchain pattern with identical upload/drain/pump logic.
        XrSwapchain panelSwapchain = XR_NULL_HANDLE;
        uint32_t panelSwapchainWidth = 0;
        uint32_t panelSwapchainHeight = 0;
        std::atomic<bool> panelLayerVisible{false};
        std::vector<SwapchainImage> panelSwapchainImages;
        std::atomic<bool> panelFrameUploaded{false};
        std::vector<uint8_t> panelPendingPixels;
        std::atomic<bool> panelBitmapPending{false};
    };

    // Single process-wide context. The vr flavor only ever runs one XR session.
    XrCtx g_ctx{};
    std::mutex g_ctxMutex;

    // Forward declarations for input-system helpers defined later in this translation unit.
    // createSessionAndSwapchains() calls setupActionSet() before the full definition appears.
    bool setupActionSet();
    void syncInputActions(JNIEnv *env);
    void destroyInputHandles();
    void releaseInputCallback(JNIEnv *env);
    XrResult triggerHapticImpl(int hand, int64_t durationNs, float amplitude);
    void emitInputEvent(JNIEnv *env, int32_t type, int32_t hand, float value, int32_t source);
    void emitPointerMove(JNIEnv *env, int32_t hand, float ndcX, float ndcY);
    void emitControllerPointerMove(JNIEnv *env, int32_t hand, float ndcX, float ndcY);
    bool createControllerAimSpaces();
    void syncControllerAimRay(JNIEnv *env);
    bool initHandTracking();
    void syncHandTracking(JNIEnv *env);
    void destroyHandTracking();

    // HUD composition layer (spec_vr-immersive-hud-gl).
    bool createHudSwapchainImpl(uint32_t requestedWidth, uint32_t requestedHeight);
    void destroyHudSwapchainImpl();

    // Interactive panel composition layer (spec_vr-immersive-controls-panel Phase 03).
    bool createPanelSwapchainImpl(uint32_t requestedWidth, uint32_t requestedHeight);
    void destroyPanelSwapchainImpl();

    // Event source identifiers — lockstep with Kotlin `XrInputSource`.
    constexpr int32_t XR_SRC_CONTROLLER = 0;
    constexpr int32_t XR_SRC_HAND = 1;

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

    bool enumerateAndCreateInstance(JNIEnv *env, jobject activity)
    {
        LOGD("enumerateAndCreateInstance: entry  vm=%p activity=%p", g_ctx.vm, activity);

        // Pull in Android loader init extension; without it the loader on Quest refuses to create an instance.
        PFN_xrInitializeLoaderKHR initLoader = nullptr;
        XrResult loaderLookup = xrGetInstanceProcAddr(
            XR_NULL_HANDLE,
            "xrInitializeLoaderKHR",
            reinterpret_cast<PFN_xrVoidFunction *>(&initLoader));
        LOGD("xrGetInstanceProcAddr(xrInitializeLoaderKHR): result=%d ptr=%p",
             static_cast<int>(loaderLookup), reinterpret_cast<void *>(initLoader));

        if (XR_SUCCEEDED(loaderLookup) && initLoader)
        {
            XrLoaderInitInfoAndroidKHR loaderInit{XR_TYPE_LOADER_INIT_INFO_ANDROID_KHR};
            loaderInit.applicationVM = g_ctx.vm;
            loaderInit.applicationContext = activity;
            XrResult r = initLoader(reinterpret_cast<XrLoaderInitInfoBaseHeaderKHR *>(&loaderInit));
            if (XR_FAILED(r))
            {
                LOGE("xrInitializeLoaderKHR failed: %d", static_cast<int>(r));
                return false;
            }
            LOGI("xrInitializeLoaderKHR: OK");
        }
        else
        {
            LOGW("xrInitializeLoaderKHR not available (result=%d); continuing best-effort",
                 static_cast<int>(loaderLookup));
        }

        // Enumerate runtime-supported extensions, pick the ones we need.
        uint32_t extCount = 0;
        XR_CHECK(xrEnumerateInstanceExtensionProperties(nullptr, 0, &extCount, nullptr),
                 "xrEnumerateInstanceExtensionProperties(count)");
        std::vector<XrExtensionProperties> props(extCount, {XR_TYPE_EXTENSION_PROPERTIES});
        XR_CHECK(xrEnumerateInstanceExtensionProperties(nullptr, extCount, &extCount, props.data()),
                 "xrEnumerateInstanceExtensionProperties(data)");

        LOGI("Runtime extensions available: %u (key extensions checked below)", extCount);

        auto hasExt = [&](const char *name)
        {
            for (const auto &p : props)
            {
                if (std::string(p.extensionName) == name)
                    return true;
            }
            return false;
        };

        std::vector<const char *> enabledExts;
        const char *kGraphicsExt = XR_KHR_OPENGL_ES_ENABLE_EXTENSION_NAME;
        const char *kAndroidCreateExt = XR_KHR_ANDROID_CREATE_INSTANCE_EXTENSION_NAME;
        const char *kEquirect2Ext = XR_KHR_COMPOSITION_LAYER_EQUIRECT2_EXTENSION_NAME;
        const char *kCylinderExt = XR_KHR_COMPOSITION_LAYER_CYLINDER_EXTENSION_NAME;
        // Hand-tracking trio (spec_vr-hand-tracking-tech §5.1). XR_EXT_hand_tracking is
        // cross-vendor; XR_META_hand_tracking_aim has a legacy XR_FB_* alias on older
        // Horizon builds; XR_META_hand_tracking_microgestures only on v62+ runtimes.
        const char *kHandTrackingExt = XR_EXT_HAND_TRACKING_EXTENSION_NAME;
        const char *kHandAimMetaExt = XR_META_HAND_TRACKING_AIM_EXTENSION_NAME;
        const char *kHandAimFbExt = XR_FB_HAND_TRACKING_AIM_EXTENSION_NAME;
        const char *kMicrogesturesExt = XR_META_HAND_TRACKING_MICROGESTURES_EXTENSION_NAME;

        LOGI("Extension check: GraphicsES=%d AndroidCreate=%d Equirect2=%d Cylinder=%d "
             "HandTracking=%d HandAim(META=%d FB=%d) Microgestures=%d",
             hasExt(kGraphicsExt),
             hasExt(kAndroidCreateExt),
             hasExt(kEquirect2Ext),
             hasExt(kCylinderExt),
             hasExt(kHandTrackingExt),
             hasExt(kHandAimMetaExt),
             hasExt(kHandAimFbExt),
             hasExt(kMicrogesturesExt));

        if (!hasExt(kGraphicsExt))
        {
            LOGE("Required extension missing: %s", kGraphicsExt);
            return false;
        }
        enabledExts.push_back(kGraphicsExt);
        if (hasExt(kAndroidCreateExt))
        {
            enabledExts.push_back(kAndroidCreateExt);
        }
        g_ctx.supportsEquirect2 = hasExt(kEquirect2Ext);
        g_ctx.supportsCylinder = hasExt(kCylinderExt);
        if (g_ctx.supportsEquirect2)
        {
            enabledExts.push_back(kEquirect2Ext);
        }
        if (g_ctx.supportsCylinder)
        {
            enabledExts.push_back(kCylinderExt);
        }
        g_ctx.supportsHandTracking = hasExt(kHandTrackingExt);
        if (g_ctx.supportsHandTracking)
        {
            enabledExts.push_back(kHandTrackingExt);
            // Aim extension: prefer META, fall back to FB — spec §6 mitigation.
            if (hasExt(kHandAimMetaExt))
            {
                enabledExts.push_back(kHandAimMetaExt);
                g_ctx.supportsHandAim = true;
            }
            else if (hasExt(kHandAimFbExt))
            {
                enabledExts.push_back(kHandAimFbExt);
                g_ctx.supportsHandAim = true;
            }
            // Horizon OS v74+ rejects xrCreateInstance with XR_ERROR_EXTENSION_DEPENDENCY_NOT_ENABLED
            // when XR_META_hand_tracking_microgestures is enabled without XR_META_hand_tracking_aim
            // — the runtime no longer accepts the FB aim alias as a microgestures dependency.
            // Gate microgestures on the META aim being present so older runtimes (FB-only) still
            // get hand tracking + aim, just without microgestures.
            if (hasExt(kMicrogesturesExt) && hasExt(kHandAimMetaExt))
            {
                enabledExts.push_back(kMicrogesturesExt);
                g_ctx.supportsMicrogestures = true;
            }
        }
        LOGI("Enabled extensions (%zu): GraphicsES Equirect2=%d Cylinder=%d "
             "HandTracking=%d HandAim=%d Microgestures=%d",
             enabledExts.size(),
             static_cast<int>(g_ctx.supportsEquirect2),
             static_cast<int>(g_ctx.supportsCylinder),
             static_cast<int>(g_ctx.supportsHandTracking),
             static_cast<int>(g_ctx.supportsHandAim),
             static_cast<int>(g_ctx.supportsMicrogestures));

        // Android create info must be in the chain so the runtime has VM + Activity.
        XrInstanceCreateInfoAndroidKHR androidCreate{XR_TYPE_INSTANCE_CREATE_INFO_ANDROID_KHR};
        androidCreate.applicationVM = g_ctx.vm;
        androidCreate.applicationActivity = activity;

        XrInstanceCreateInfo ci{XR_TYPE_INSTANCE_CREATE_INFO};
        ci.next = &androidCreate;
        ci.enabledExtensionCount = static_cast<uint32_t>(enabledExts.size());
        ci.enabledExtensionNames = enabledExts.data();
        // WHY 1.1.0:
        // Quest runtime builds have shown mixed legacy behavior around the graphics-requirements
        // precondition on xrCreateSession. Requesting 1.1.0 keeps us on the current spec path,
        // but we still call xrGetOpenGLESGraphicsRequirementsKHR explicitly below because some
        // loader/runtime combinations continue to enforce the pre-session check.
        ci.applicationInfo.apiVersion = XR_MAKE_VERSION(1, 1, 0);
        std::strncpy(ci.applicationInfo.applicationName,
                     "FastMediaSorter-VR",
                     XR_MAX_APPLICATION_NAME_SIZE - 1);
        std::strncpy(ci.applicationInfo.engineName,
                     "FMS-OpenXR",
                     XR_MAX_ENGINE_NAME_SIZE - 1);
        LOGD("xrCreateInstance: apiVersion=0x%llx app='%s' engine='%s'",
             static_cast<unsigned long long>(ci.applicationInfo.apiVersion),
             ci.applicationInfo.applicationName,
             ci.applicationInfo.engineName);

        XrResult createResult = xrCreateInstance(&ci, &g_ctx.instance);
        // Retry path for XR_ERROR_EXTENSION_DEPENDENCY_NOT_ENABLED (-1000710001):
        // a future runtime change may flag yet another optional extension dependency we
        // can't predict here. Drop everything except the base required pair so users keep
        // a working immersive view (no equirect/cylinder/hand-tracking) instead of being
        // stranded on the flat-cinema fallback. Each dropped capability is logged so the
        // regression is obvious in fastmediasorter_*.log.
        if (createResult == XR_ERROR_EXTENSION_DEPENDENCY_NOT_ENABLED)
        {
            LOGE("xrCreateInstance: XR_ERROR_EXTENSION_DEPENDENCY_NOT_ENABLED — retrying with minimal extension set");
            for (const char *name : enabledExts)
            {
                LOGW("  was-enabled: %s", name);
            }
            enabledExts.clear();
            enabledExts.push_back(kGraphicsExt);
            if (hasExt(kAndroidCreateExt))
            {
                enabledExts.push_back(kAndroidCreateExt);
            }
            g_ctx.supportsEquirect2 = false;
            g_ctx.supportsCylinder = false;
            g_ctx.supportsHandTracking = false;
            g_ctx.supportsHandAim = false;
            g_ctx.supportsMicrogestures = false;
            ci.enabledExtensionCount = static_cast<uint32_t>(enabledExts.size());
            ci.enabledExtensionNames = enabledExts.data();
            LOGI("xrCreateInstance: retry with %zu extensions (graphics-only)", enabledExts.size());
            createResult = xrCreateInstance(&ci, &g_ctx.instance);
        }
        if (XR_FAILED(createResult))
        {
            LOGE("xrCreateInstance failed: %d", static_cast<int>(createResult));
            return false;
        }
        LOGI("XR instance created: handle=0x%llx  extensions=%zu  Equirect2=%d Cylinder=%d",
             static_cast<unsigned long long>(reinterpret_cast<uintptr_t>(g_ctx.instance)),
             enabledExts.size(),
             static_cast<int>(g_ctx.supportsEquirect2),
             static_cast<int>(g_ctx.supportsCylinder));
        return true;
    }

    bool createSessionAndSwapchains()
    {
        LOGD("createSessionAndSwapchains: entry  instance=0x%llx",
             static_cast<unsigned long long>(reinterpret_cast<uintptr_t>(g_ctx.instance)));

        // Get HMD system.
        XrSystemGetInfo sysInfo{XR_TYPE_SYSTEM_GET_INFO};
        sysInfo.formFactor = kFormFactor;
        XR_CHECK(xrGetSystem(g_ctx.instance, &sysInfo, &g_ctx.systemId), "xrGetSystem");
        LOGI("xrGetSystem: systemId=0x%llx",
             static_cast<unsigned long long>(g_ctx.systemId));

        // Log system properties so we know exactly what headset we're on.
        {
            XrSystemProperties sysProps{XR_TYPE_SYSTEM_PROPERTIES};
            if (XR_SUCCEEDED(xrGetSystemProperties(g_ctx.instance, g_ctx.systemId, &sysProps)))
            {
                LOGI("System: name='%s'  vendorId=%u",
                     sysProps.systemName, sysProps.vendorId);
                LOGI("  gfx maxSwapchainW=%u maxSwapchainH=%u maxLayers=%u",
                     sysProps.graphicsProperties.maxSwapchainImageWidth,
                     sysProps.graphicsProperties.maxSwapchainImageHeight,
                     sysProps.graphicsProperties.maxLayerCount);
                LOGI("  tracking: orientation=%d position=%d",
                     static_cast<int>(sysProps.trackingProperties.orientationTracking),
                     static_cast<int>(sysProps.trackingProperties.positionTracking));
            }
            else
            {
                LOGW("xrGetSystemProperties failed — continuing without system info");
            }
        }

        // MANDATORY per OpenXR spec §7.1: call xrGetOpenGLESGraphicsRequirementsKHR
        // BEFORE xrCreateSession. Skipping this causes XR_ERROR_GRAPHICS_REQUIREMENTS_CALL_MISSING (-50).
        //
        // ROOT CAUSE FIX:
        // The previous implementation looked up the non-existent symbol
        // `xrGetGraphicsRequirementsOpenGLESKHR` (word order swapped), so every lookup path
        // necessarily failed and Quest returned XR_ERROR_GRAPHICS_REQUIREMENTS_CALL_MISSING (-50)
        // from xrCreateSession.
        //
        // STRATEGY (three-step fallback):
        //   1. xrGetInstanceProcAddr(instance, ...) — standard path.
        //   2. xrGetInstanceProcAddr(XR_NULL_HANDLE, ...) — loader-level lookup (some loaders
        //      keep extension functions accessible at global scope).
        //   3. dlsym(libopenxr_loader.so, ...) — directly calls the loader's own exported
        //      symbol, bypassing the runtime dispatch. The loader's exported implementation
        //      lets us reach runtimes that expose the entry point through the loader symbol.
        // If all three fail we log a warning; xrCreateSession will fail with -50 (fatal).
        // The PFN_ typedef is defined locally to stay portable across OpenXR SDK versions.
        {
            typedef XrResult(XRAPI_PTR * PFN_GetGfxReqsGLES)(
                XrInstance, XrSystemId, XrGraphicsRequirementsOpenGLESKHR *);

            PFN_GetGfxReqsGLES pfnGetGfxReqs = nullptr;

            // Step 1: instance-level lookup (standard path).
            XrResult lookupResult = xrGetInstanceProcAddr(
                g_ctx.instance,
                "xrGetOpenGLESGraphicsRequirementsKHR",
                reinterpret_cast<PFN_xrVoidFunction *>(&pfnGetGfxReqs));
            LOGD("xrGetInstanceProcAddr(xrGetOpenGLESGraphicsRequirementsKHR): result=%d ptr=%p",
                 static_cast<int>(lookupResult), reinterpret_cast<void *>(pfnGetGfxReqs));

            // Step 2: loader-level lookup via XR_NULL_HANDLE.
            if (pfnGetGfxReqs == nullptr)
            {
                lookupResult = xrGetInstanceProcAddr(
                    XR_NULL_HANDLE,
                    "xrGetOpenGLESGraphicsRequirementsKHR",
                    reinterpret_cast<PFN_xrVoidFunction *>(&pfnGetGfxReqs));
                LOGD("xrGetInstanceProcAddr(XR_NULL_HANDLE, xrGetOpenGLESGraphicsRequirementsKHR): result=%d ptr=%p",
                     static_cast<int>(lookupResult), reinterpret_cast<void *>(pfnGetGfxReqs));
            }

            // Step 3: dlsym on the OpenXR loader shared library.
            // Some Android builds expose the entry point via the loader symbol rather than via
            // xrGetInstanceProcAddr, so probe the already-mapped loader as the last fallback.
            if (pfnGetGfxReqs == nullptr)
            {
                // Try both common loader library names on Meta Quest Android.
                static const char *const kLoaderLibs[] = {
                    "libopenxr_loader.so",
                    "libopenxr.so",
                    nullptr};
                for (int li = 0; kLoaderLibs[li] != nullptr && pfnGetGfxReqs == nullptr; ++li)
                {
                    // RTLD_NOLOAD: only succeed if the library is already mapped; we never
                    // want to load a new SO here — the loader must already be in process.
                    void *loaderLib = dlopen(kLoaderLibs[li], RTLD_NOW | RTLD_NOLOAD);
                    if (loaderLib)
                    {
                        pfnGetGfxReqs = reinterpret_cast<PFN_GetGfxReqsGLES>(
                            dlsym(loaderLib, "xrGetOpenGLESGraphicsRequirementsKHR"));
                        LOGD("dlsym(%s, xrGetOpenGLESGraphicsRequirementsKHR): ptr=%p",
                             kLoaderLibs[li], reinterpret_cast<void *>(pfnGetGfxReqs));
                        dlclose(loaderLib);
                    }
                }
            }

            if (pfnGetGfxReqs != nullptr)
            {
                XrGraphicsRequirementsOpenGLESKHR gfxReqs{XR_TYPE_GRAPHICS_REQUIREMENTS_OPENGL_ES_KHR};
                XrResult reqResult = pfnGetGfxReqs(g_ctx.instance, g_ctx.systemId, &gfxReqs);
                if (XR_FAILED(reqResult))
                {
                    // Non-fatal on modern runtimes: we already attempted the required query and
                    // apiVersion 1.1.0 avoids the legacy hard-fail path on compliant loaders.
                    LOGW("xrGetOpenGLESGraphicsRequirementsKHR returned %d — proceeding",
                         static_cast<int>(reqResult));
                }
                else
                {
                    LOGI("OpenGL ES version range: min=%u.%u  max=%u.%u",
                         XR_VERSION_MAJOR(gfxReqs.minApiVersionSupported),
                         XR_VERSION_MINOR(gfxReqs.minApiVersionSupported),
                         XR_VERSION_MAJOR(gfxReqs.maxApiVersionSupported),
                         XR_VERSION_MINOR(gfxReqs.maxApiVersionSupported));
                }
            }
            else
            {
                // All three lookup methods failed — xrCreateSession will return -50.
                LOGW("xrGetOpenGLESGraphicsRequirementsKHR unavailable via all methods (instance/null/dlsym) — xrCreateSession will likely fail with XR_ERROR_GRAPHICS_REQUIREMENTS_CALL_MISSING (-50)");
            }
        }

        // GL ES graphics binding — shares the Kotlin-side EGL context so the swapchain
        // GL textures live in the same context our renderer draws with.
        LOGD("EGL binding: display=%p config=%p context=%p",
             static_cast<void *>(g_ctx.eglDisplay),
             static_cast<void *>(g_ctx.eglConfig),
             static_cast<void *>(g_ctx.eglContext));

        XrGraphicsBindingOpenGLESAndroidKHR gfx{XR_TYPE_GRAPHICS_BINDING_OPENGL_ES_ANDROID_KHR};
        gfx.display = g_ctx.eglDisplay;
        gfx.config = g_ctx.eglConfig;
        gfx.context = g_ctx.eglContext;

        XrSessionCreateInfo sci{XR_TYPE_SESSION_CREATE_INFO};
        sci.next = &gfx;
        sci.systemId = g_ctx.systemId;
        LOGD("xrCreateSession: systemId=0x%llx  EGL display=%p context=%p",
             static_cast<unsigned long long>(g_ctx.systemId),
             static_cast<void *>(g_ctx.eglDisplay),
             static_cast<void *>(g_ctx.eglContext));

        XR_CHECK(xrCreateSession(g_ctx.instance, &sci, &g_ctx.session), "xrCreateSession");
        LOGI("xrCreateSession: SUCCESS  session=0x%llx",
             static_cast<unsigned long long>(reinterpret_cast<uintptr_t>(g_ctx.session)));

        // LOCAL reference space — anchored at session start; enough for seated viewing.
        XrReferenceSpaceCreateInfo rsci{XR_TYPE_REFERENCE_SPACE_CREATE_INFO};
        rsci.referenceSpaceType = kSpaceType;
        rsci.poseInReferenceSpace.orientation.w = 1.0f;
        XR_CHECK(xrCreateReferenceSpace(g_ctx.session, &rsci, &g_ctx.appSpace),
                 "xrCreateReferenceSpace");
        LOGI("xrCreateReferenceSpace: appSpace=0x%llx  type=LOCAL",
             static_cast<unsigned long long>(reinterpret_cast<uintptr_t>(g_ctx.appSpace)));

        // VIEW reference space — head-locked. Used by the HUD composition layer so the
        // HUD always sits in the user's view, independent of the video layer's anchor.
        {
            XrReferenceSpaceCreateInfo viewRsci{XR_TYPE_REFERENCE_SPACE_CREATE_INFO};
            viewRsci.referenceSpaceType = XR_REFERENCE_SPACE_TYPE_VIEW;
            viewRsci.poseInReferenceSpace.orientation.w = 1.0f;
            XrResult viewSpaceResult =
                xrCreateReferenceSpace(g_ctx.session, &viewRsci, &g_ctx.viewSpace);
            if (XR_FAILED(viewSpaceResult))
            {
                LOGW("xrCreateReferenceSpace(VIEW) failed: %d — HUD layer will be disabled",
                     static_cast<int>(viewSpaceResult));
                g_ctx.viewSpace = XR_NULL_HANDLE;
            }
            else
            {
                LOGI("xrCreateReferenceSpace: viewSpace=0x%llx  type=VIEW",
                     static_cast<unsigned long long>(reinterpret_cast<uintptr_t>(g_ctx.viewSpace)));
            }
        }

        // View configuration views — gives us recommended swapchain dimensions per eye.
        uint32_t viewCount = 0;
        XR_CHECK(xrEnumerateViewConfigurationViews(
                     g_ctx.instance, g_ctx.systemId, kViewConfig, 0, &viewCount, nullptr),
                 "xrEnumerateViewConfigurationViews(count)");
        LOGI("ViewConfigurationViews count=%u (expected %u)", viewCount, kViewCount);
        if (viewCount != kViewCount)
        {
            LOGE("Expected %u views, runtime reports %u", kViewCount, viewCount);
            return false;
        }
        std::vector<XrViewConfigurationView> views(viewCount, {XR_TYPE_VIEW_CONFIGURATION_VIEW});
        XR_CHECK(xrEnumerateViewConfigurationViews(
                     g_ctx.instance, g_ctx.systemId, kViewConfig, viewCount, &viewCount, views.data()),
                 "xrEnumerateViewConfigurationViews(data)");
        for (uint32_t i = 0; i < viewCount; ++i)
        {
            LOGI("  view[%u]: recommended=%ux%u  sampleCount=%u  max=%ux%u",
                 i,
                 views[i].recommendedImageRectWidth, views[i].recommendedImageRectHeight,
                 views[i].recommendedSwapchainSampleCount,
                 views[i].maxImageRectWidth, views[i].maxImageRectHeight);
        }

        // Create one swapchain per eye.
        for (uint32_t eye = 0; eye < kViewCount; ++eye)
        {
            const auto &v = views[eye];

            XrSwapchainCreateInfo sc{XR_TYPE_SWAPCHAIN_CREATE_INFO};
            sc.usageFlags = XR_SWAPCHAIN_USAGE_COLOR_ATTACHMENT_BIT | XR_SWAPCHAIN_USAGE_SAMPLED_BIT;
            sc.format = GL_SRGB8_ALPHA8;
            sc.sampleCount = v.recommendedSwapchainSampleCount;
            sc.width = v.recommendedImageRectWidth;
            sc.height = v.recommendedImageRectHeight;
            sc.faceCount = 1;
            sc.arraySize = 1;
            sc.mipCount = 1;
            LOGD("xrCreateSwapchain eye=%u  %ux%u  samples=%u  format=0x%x",
                 eye, sc.width, sc.height, sc.sampleCount, static_cast<unsigned>(sc.format));

            XR_CHECK(xrCreateSwapchain(g_ctx.session, &sc, &g_ctx.eyes[eye].handle), "xrCreateSwapchain");
            g_ctx.eyes[eye].width = sc.width;
            g_ctx.eyes[eye].height = sc.height;

            uint32_t imgCount = 0;
            XR_CHECK(xrEnumerateSwapchainImages(g_ctx.eyes[eye].handle, 0, &imgCount, nullptr),
                     "xrEnumerateSwapchainImages(count)");
            std::vector<XrSwapchainImageOpenGLESKHR> xrImages(
                imgCount, {XR_TYPE_SWAPCHAIN_IMAGE_OPENGL_ES_KHR});
            XR_CHECK(xrEnumerateSwapchainImages(
                         g_ctx.eyes[eye].handle,
                         imgCount,
                         &imgCount,
                         reinterpret_cast<XrSwapchainImageBaseHeader *>(xrImages.data())),
                     "xrEnumerateSwapchainImages(data)");

            g_ctx.eyes[eye].images.resize(imgCount);
            for (uint32_t i = 0; i < imgCount; ++i)
            {
                g_ctx.eyes[eye].images[i].imageId = xrImages[i].image;
                // Build a matching FBO so the Kotlin renderer can bind by ID.
                GLuint fbo = 0;
                glGenFramebuffers(1, &fbo);
                glBindFramebuffer(GL_FRAMEBUFFER, fbo);
                glFramebufferTexture2D(GL_FRAMEBUFFER,
                                       GL_COLOR_ATTACHMENT0,
                                       GL_TEXTURE_2D,
                                       xrImages[i].image,
                                       0);
                GLenum status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
                if (status != GL_FRAMEBUFFER_COMPLETE)
                {
                    LOGE("FBO incomplete eye=%u idx=%u status=0x%x", eye, i, status);
                }
                else
                {
                    LOGD("FBO ok eye=%u idx=%u fbo=%u texId=%u", eye, i, fbo, xrImages[i].image);
                }
                g_ctx.eyes[eye].images[i].fbo = fbo;
            }
            glBindFramebuffer(GL_FRAMEBUFFER, 0);
            LOGI("Eye %u swapchain: %ux%u, %u images  handle=0x%llx",
                 eye, sc.width, sc.height, imgCount,
                 static_cast<unsigned long long>(reinterpret_cast<uintptr_t>(g_ctx.eyes[eye].handle)));
        }

        // OpenXR input system: create ActionSet + bindings BEFORE xrBeginSession.
        // Per spec: xrAttachSessionActionSets must run in the window between
        // xrCreateSession and the first xrBeginSession (triggered on STATE_READY).
        if (!setupActionSet())
        {
            LOGW("createSessionAndSwapchains: setupActionSet failed — controllers will not respond; continuing");
        }

        // Hand tracking (Layer E) — optional, silently disabled if the runtime / user
        // permission declines it. Controllers stay functional regardless.
        if (!initHandTracking())
        {
            LOGI("createSessionAndSwapchains: hand tracking disabled — continuing with controllers only");
        }

        // HUD composition layer (spec_vr-immersive-hud-gl). Auto-create with default
        // dimensions if Kotlin did not pre-allocate via nativeCreateHudSwapchain.
        // Failure is non-fatal — video rendering must continue without HUD.
        {
            const uint32_t hudW = g_ctx.hudSwapchainWidth != 0
                                      ? g_ctx.hudSwapchainWidth
                                      : 1024u;
            const uint32_t hudH = g_ctx.hudSwapchainHeight != 0
                                      ? g_ctx.hudSwapchainHeight
                                      : 256u;
            if (!createHudSwapchainImpl(hudW, hudH))
            {
                LOGW("createSessionAndSwapchains: HUD swapchain creation failed — HUD disabled");
            }
        }

        LOGI("createSessionAndSwapchains: complete — session ready for runtime events");
        return true;
    }

    void handleSessionStateChange(XrEventDataSessionStateChanged *e)
    {
        const XrSessionState prevState = g_ctx.sessionState;
        g_ctx.sessionState = e->state;
        LOGI("Session state: %s(%d) -> %s(%d)",
             xrSessionStateName(prevState), static_cast<int>(prevState),
             xrSessionStateName(e->state), static_cast<int>(e->state));

        switch (e->state)
        {
        case XR_SESSION_STATE_READY:
        {
            XrSessionBeginInfo bi{XR_TYPE_SESSION_BEGIN_INFO};
            bi.primaryViewConfigurationType = kViewConfig;
            XrResult r = xrBeginSession(g_ctx.session, &bi);
            if (XR_SUCCEEDED(r))
            {
                g_ctx.sessionRunning = true;
                LOGI("xrBeginSession: OK — render loop will start next frame");
            }
            else
            {
                LOGE("xrBeginSession FAILED: %d — session will not render", static_cast<int>(r));
            }
            break;
        }
        case XR_SESSION_STATE_STOPPING:
        {
            LOGI("xrEndSession: calling — sessionRunning was %d", static_cast<int>(g_ctx.sessionRunning));
            XrResult r = xrEndSession(g_ctx.session);
            if (XR_FAILED(r))
            {
                LOGE("xrEndSession FAILED: %d", static_cast<int>(r));
            }
            g_ctx.sessionRunning = false;
            break;
        }
        case XR_SESSION_STATE_EXITING:
        case XR_SESSION_STATE_LOSS_PENDING:
            LOGI("Session %s — requesting exit", xrSessionStateName(e->state));
            g_ctx.exitRequested = true;
            break;
        default:
            LOGD("Session state %s — no action needed", xrSessionStateName(e->state));
            break;
        }
    }

    // ── HUD swapchain helpers (spec_vr-immersive-hud-gl) ────────────────────

    bool createHudSwapchainImpl(uint32_t requestedWidth, uint32_t requestedHeight)
    {
        if (g_ctx.session == XR_NULL_HANDLE)
        {
            LOGW("createHudSwapchainImpl: session is null — skipped");
            return false;
        }
        if (g_ctx.hudSwapchain != XR_NULL_HANDLE)
        {
            // Already created. Treat as idempotent success when dimensions match.
            if (g_ctx.hudSwapchainWidth == requestedWidth &&
                g_ctx.hudSwapchainHeight == requestedHeight)
            {
                return true;
            }
            destroyHudSwapchainImpl();
        }

        XrSwapchainCreateInfo sc{XR_TYPE_SWAPCHAIN_CREATE_INFO};
        sc.usageFlags = XR_SWAPCHAIN_USAGE_COLOR_ATTACHMENT_BIT | XR_SWAPCHAIN_USAGE_SAMPLED_BIT;
        // GL_RGBA8 (0x8058) — non-sRGB so Canvas premultiplied alpha colours land linearly.
        // Phase 03 uploads via glTexSubImage2D with GL_RGBA / GL_UNSIGNED_BYTE.
        sc.format = 0x8058;
        sc.sampleCount = 1;
        sc.width = requestedWidth;
        sc.height = requestedHeight;
        sc.faceCount = 1;
        sc.arraySize = 1;
        sc.mipCount = 1;

        XrResult r = xrCreateSwapchain(g_ctx.session, &sc, &g_ctx.hudSwapchain);
        if (XR_FAILED(r))
        {
            LOGE("HUD swapchain xrCreateSwapchain failed: %d", static_cast<int>(r));
            g_ctx.hudSwapchain = XR_NULL_HANDLE;
            return false;
        }
        g_ctx.hudSwapchainWidth = requestedWidth;
        g_ctx.hudSwapchainHeight = requestedHeight;
        // Pre-allocate staging buffer so nativeUploadHudBitmap can memcpy without
        // reallocating on every frame. Size = RGBA_8888 = 4 bytes per pixel.
        g_ctx.hudPendingPixels.assign(
            static_cast<size_t>(requestedWidth) * requestedHeight * 4u, 0u);
        g_ctx.hudBitmapPending.store(false);

        uint32_t imgCount = 0;
        r = xrEnumerateSwapchainImages(g_ctx.hudSwapchain, 0, &imgCount, nullptr);
        if (XR_FAILED(r) || imgCount == 0)
        {
            LOGE("HUD xrEnumerateSwapchainImages(count) failed: %d count=%u",
                 static_cast<int>(r), imgCount);
            destroyHudSwapchainImpl();
            return false;
        }
        std::vector<XrSwapchainImageOpenGLESKHR> xrImages(
            imgCount, {XR_TYPE_SWAPCHAIN_IMAGE_OPENGL_ES_KHR});
        r = xrEnumerateSwapchainImages(
            g_ctx.hudSwapchain,
            imgCount,
            &imgCount,
            reinterpret_cast<XrSwapchainImageBaseHeader *>(xrImages.data()));
        if (XR_FAILED(r))
        {
            LOGE("HUD xrEnumerateSwapchainImages(data) failed: %d", static_cast<int>(r));
            destroyHudSwapchainImpl();
            return false;
        }

        g_ctx.hudSwapchainImages.resize(imgCount);
        for (uint32_t i = 0; i < imgCount; ++i)
        {
            g_ctx.hudSwapchainImages[i].imageId = xrImages[i].image;
            // Build a matching FBO so any future GL render-to-HUD path works without
            // additional setup. Phase 03's glTexSubImage2D path does not need the FBO,
            // but keeping it parity with eye swapchains keeps the door open.
            GLuint fbo = 0;
            glGenFramebuffers(1, &fbo);
            glBindFramebuffer(GL_FRAMEBUFFER, fbo);
            glFramebufferTexture2D(GL_FRAMEBUFFER,
                                   GL_COLOR_ATTACHMENT0,
                                   GL_TEXTURE_2D,
                                   xrImages[i].image,
                                   0);
            GLenum status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
            if (status != GL_FRAMEBUFFER_COMPLETE)
            {
                LOGW("HUD FBO incomplete idx=%u status=0x%x", i, status);
            }
            g_ctx.hudSwapchainImages[i].fbo = fbo;
        }
        glBindFramebuffer(GL_FRAMEBUFFER, 0);

        LOGI("HUD swapchain: %ux%u, %u images  handle=0x%llx",
             requestedWidth, requestedHeight, imgCount,
             static_cast<unsigned long long>(reinterpret_cast<uintptr_t>(g_ctx.hudSwapchain)));
        return true;
    }

    void destroyHudSwapchainImpl()
    {
        for (auto &img : g_ctx.hudSwapchainImages)
        {
            if (img.fbo)
            {
                GLuint f = img.fbo;
                glDeleteFramebuffers(1, &f);
                img.fbo = 0;
            }
        }
        g_ctx.hudSwapchainImages.clear();
        if (g_ctx.hudSwapchain != XR_NULL_HANDLE)
        {
            XrResult r = xrDestroySwapchain(g_ctx.hudSwapchain);
            if (XR_FAILED(r))
            {
                LOGW("HUD swapchain destroy failed: %d", static_cast<int>(r));
            }
            else
            {
                LOGI("HUD swapchain destroyed");
            }
            g_ctx.hudSwapchain = XR_NULL_HANDLE;
        }
        g_ctx.hudSwapchainWidth = 0;
        g_ctx.hudSwapchainHeight = 0;
        g_ctx.hudLayerVisible.store(false);
        g_ctx.hudPendingPixels.clear();
        g_ctx.hudBitmapPending.store(false);
    }

    // ── Interactive panel swapchain helpers (spec_vr-immersive-controls-panel §5) ──

    bool createPanelSwapchainImpl(uint32_t requestedWidth, uint32_t requestedHeight)
    {
        if (g_ctx.session == XR_NULL_HANDLE)
        {
            LOGW("createPanelSwapchainImpl: session is null — skipped");
            return false;
        }
        if (g_ctx.panelSwapchain != XR_NULL_HANDLE)
        {
            if (g_ctx.panelSwapchainWidth == requestedWidth &&
                g_ctx.panelSwapchainHeight == requestedHeight)
                return true;
            destroyPanelSwapchainImpl();
        }

        XrSwapchainCreateInfo sc{XR_TYPE_SWAPCHAIN_CREATE_INFO};
        sc.usageFlags = XR_SWAPCHAIN_USAGE_COLOR_ATTACHMENT_BIT | XR_SWAPCHAIN_USAGE_SAMPLED_BIT;
        sc.format = GL_RGBA8;
        sc.width = requestedWidth;
        sc.height = requestedHeight;
        sc.faceCount = 1;
        sc.arraySize = 1;
        sc.mipCount = 1;

        XrResult r = xrCreateSwapchain(g_ctx.session, &sc, &g_ctx.panelSwapchain);
        if (XR_FAILED(r))
        {
            LOGE("Panel swapchain xrCreateSwapchain failed: %d", static_cast<int>(r));
            g_ctx.panelSwapchain = XR_NULL_HANDLE;
            return false;
        }
        g_ctx.panelSwapchainWidth = requestedWidth;
        g_ctx.panelSwapchainHeight = requestedHeight;
        g_ctx.panelPendingPixels.assign(
            static_cast<size_t>(requestedWidth) * requestedHeight * 4u, 0u);

        uint32_t imgCount = 0;
        r = xrEnumerateSwapchainImages(g_ctx.panelSwapchain, 0, &imgCount, nullptr);
        if (XR_FAILED(r))
        {
            LOGE("Panel xrEnumerateSwapchainImages(count) failed: %d count=%u",
                 static_cast<int>(r), imgCount);
            destroyPanelSwapchainImpl();
            return false;
        }
        std::vector<XrSwapchainImageOpenGLESKHR> imgs(imgCount);
        for (auto &img : imgs)
            img.type = XR_TYPE_SWAPCHAIN_IMAGE_OPENGL_ES_KHR;
        r = xrEnumerateSwapchainImages(
            g_ctx.panelSwapchain, imgCount, &imgCount,
            reinterpret_cast<XrSwapchainImageBaseHeader *>(imgs.data()));
        if (XR_FAILED(r))
        {
            LOGE("Panel xrEnumerateSwapchainImages(data) failed: %d", static_cast<int>(r));
            destroyPanelSwapchainImpl();
            return false;
        }
        g_ctx.panelSwapchainImages.resize(imgCount);
        for (uint32_t i = 0; i < imgCount; ++i)
            g_ctx.panelSwapchainImages[i].imageId = imgs[i].image;

        LOGI("createPanelSwapchainImpl: %ux%u swapchain created imgCount=%u",
             requestedWidth, requestedHeight, imgCount);
        return true;
    }

    void destroyPanelSwapchainImpl()
    {
        g_ctx.panelSwapchainImages.clear();
        if (g_ctx.panelSwapchain != XR_NULL_HANDLE)
        {
            XrResult r = xrDestroySwapchain(g_ctx.panelSwapchain);
            if (XR_FAILED(r))
                LOGW("Panel swapchain destroy failed: %d", static_cast<int>(r));
            else
                LOGI("Panel swapchain destroyed");
            g_ctx.panelSwapchain = XR_NULL_HANDLE;
        }
        g_ctx.panelSwapchainWidth = 0;
        g_ctx.panelSwapchainHeight = 0;
        g_ctx.panelLayerVisible.store(false);
        g_ctx.panelPendingPixels.clear();
        g_ctx.panelBitmapPending.store(false);
    }

    bool pollEvents()
    {
        XrEventDataBuffer evt{XR_TYPE_EVENT_DATA_BUFFER};
        while (xrPollEvent(g_ctx.instance, &evt) == XR_SUCCESS)
        {
            LOGD("pollEvents: received event type=%d (%s)",
                 static_cast<int>(evt.type), xrEventTypeName(evt.type));
            switch (evt.type)
            {
            case XR_TYPE_EVENT_DATA_SESSION_STATE_CHANGED:
                handleSessionStateChange(
                    reinterpret_cast<XrEventDataSessionStateChanged *>(&evt));
                break;
            case XR_TYPE_EVENT_DATA_INSTANCE_LOSS_PENDING:
                LOGW("pollEvents: INSTANCE_LOSS_PENDING — stopping render loop");
                g_ctx.exitRequested = true;
                return false;
            case XR_TYPE_EVENT_DATA_INTERACTION_PROFILE_CHANGED:
            {
                LOGD("pollEvents: interaction profile changed — querying active profiles");
                const char *topLevelPaths[] = {"/user/hand/left", "/user/hand/right"};
                for (const auto *path : topLevelPaths)
                {
                    XrPath topLevel = XR_NULL_PATH;
                    xrStringToPath(g_ctx.instance, path, &topLevel);
                    XrInteractionProfileState state{XR_TYPE_INTERACTION_PROFILE_STATE};
                    XrResult res = xrGetCurrentInteractionProfile(g_ctx.session, topLevel, &state);
                    if (XR_SUCCEEDED(res) && state.interactionProfile != XR_NULL_PATH)
                    {
                        uint32_t len = 0;
                        char profileName[256] = {};
                        xrPathToString(g_ctx.instance, state.interactionProfile,
                                       sizeof(profileName), &len, profileName);
                        LOGD("pollEvents: active profile for %s = %s", path, profileName);
                    }
                }
                break;
            }
            default:
                LOGD("pollEvents: unhandled event type=%d", static_cast<int>(evt.type));
                break;
            }
            evt = {XR_TYPE_EVENT_DATA_BUFFER};
        }
        if (g_ctx.exitRequested.load())
        {
            LOGI("pollEvents: exitRequested — render loop will terminate");
        }
        return !g_ctx.exitRequested.load();
    }

    void invokeRenderCallback(JNIEnv *env, int eye, int fbo, int width, int height)
    {
        if (!g_ctx.callbackRef || !g_ctx.onRenderEyeMethod)
            return;
        env->CallVoidMethod(g_ctx.callbackRef, g_ctx.onRenderEyeMethod,
                            static_cast<jint>(eye),
                            static_cast<jint>(fbo),
                            static_cast<jint>(width),
                            static_cast<jint>(height));
        if (env->ExceptionCheck())
        {
            env->ExceptionDescribe();
            env->ExceptionClear();
        }
    }

    XrSwapchainSubImage buildFullFrameSubImage(const EyeSwapchain &chain)
    {
        XrSwapchainSubImage subImage{};
        subImage.swapchain = chain.handle;
        subImage.imageRect = {
            {0, 0},
            {static_cast<int32_t>(chain.width), static_cast<int32_t>(chain.height)}};
        subImage.imageArrayIndex = 0;
        return subImage;
    }

    XrEyeVisibility eyeVisibilityForIndex(uint32_t eye)
    {
        return eye == 0 ? XR_EYE_VISIBILITY_LEFT : XR_EYE_VISIBILITY_RIGHT;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // OpenXR Input System helpers.
    // Event-type enum is kept in lockstep with Kotlin `XrInputEventType`.
    // ═══════════════════════════════════════════════════════════════════════

    enum XrInputEvt : int32_t
    {
        XR_EVT_PAUSE_TOGGLE = 0,
        XR_EVT_EXIT = 1,
        XR_EVT_FILE_OPS = 2,
        XR_EVT_MENU = 3,
        XR_EVT_SEEK_BACKWARD = 4,
        XR_EVT_SEEK_FORWARD = 5,
        XR_EVT_FILE_PREV = 6,
        XR_EVT_FILE_NEXT = 7,
        XR_EVT_VOLUME_UP = 8,
        XR_EVT_VOLUME_DOWN = 9,
        XR_EVT_RECENTER = 10,
        XR_EVT_TOGGLE_IMMERSIVE = 11,
        XR_EVT_CHEATSHEET = 12,
        XR_EVT_ZOOM_START = 13,
        XR_EVT_ZOOM_DELTA = 14,
        XR_EVT_ZOOM_END = 15,
        XR_EVT_ZOOM_RESET = 16,
        // Hand-tracking — mirror of XrInputEventType.POINTER_* / SWIPE_* / DOUBLE_PINCH.
        XR_EVT_POINTER_CLICK_DOWN = 17,
        XR_EVT_POINTER_CLICK_UP = 18,
        XR_EVT_SWIPE_LEFT = 19,
        XR_EVT_SWIPE_RIGHT = 20,
        XR_EVT_SWIPE_UP = 21,
        XR_EVT_SWIPE_DOWN = 22,
        XR_EVT_DOUBLE_PINCH = 23,
    };

    // Modality switch: hand tracking is suppressed while controllers emit events
    // within this window. 2 s matches spec §3.3 ("inactive for > 2.0 seconds").
    constexpr int64_t kControllerIdleSwitchNs = 2'000'000'000LL;

    // Pinch hysteresis thresholds (spec §5.1 / §6).
    constexpr float kPinchPressThreshold = 0.9f;
    constexpr float kPinchReleaseThreshold = 0.6f;
    constexpr float kPinchAimFreezeThreshold = 0.5f; // Freeze raycast once past this.
    constexpr int64_t kDoublePinchWindowNs = 300'000'000LL;

    // Analog thresholds tuned per UX spec §4.1.1 / §4.1.2.
    constexpr float kStickThreshold = 0.7f;
    constexpr float kStickReturnZone = 0.15f; // Hysteresis: must return near zero before re-trigger.
    constexpr float kGripPressThresh = 0.7f;
    constexpr float kGripReleaseThresh = 0.4f;
    constexpr float kGripDeltaMin = 0.01f;
    constexpr int64_t kYLongPressNs = 800'000'000LL;      // 0.8 s
    constexpr int64_t kBothGripsHoldNs = 1'000'000'000LL; // 1.0 s

    inline int64_t monotonicNowNs()
    {
        struct timespec ts;
        clock_gettime(CLOCK_MONOTONIC, &ts);
        return static_cast<int64_t>(ts.tv_sec) * 1'000'000'000LL + static_cast<int64_t>(ts.tv_nsec);
    }

    void emitInputEvent(JNIEnv *env, int32_t type, int32_t hand, float value, int32_t source)
    {
        auto &io = g_ctx.input;
        if (!io.inputCallbackRef || !io.onInputEventMethod || !env)
            return;
        // Track controller-edge activity for the modality gate: any controller-sourced
        // edge event refreshes the idle timer so hand polling stays suppressed.
        if (source == XR_SRC_CONTROLLER)
            io.lastControllerEventNs = monotonicNowNs();
        env->CallVoidMethod(io.inputCallbackRef, io.onInputEventMethod,
                            static_cast<jint>(type),
                            static_cast<jint>(hand),
                            static_cast<jfloat>(value),
                            static_cast<jint>(source));
        if (env->ExceptionCheck())
        {
            env->ExceptionDescribe();
            env->ExceptionClear();
        }
    }

    void emitPointerMove(JNIEnv *env, int32_t hand, float ndcX, float ndcY)
    {
        auto &io = g_ctx.input;
        if (!io.inputCallbackRef || !io.onPointerMoveMethod || !env)
            return;
        env->CallVoidMethod(io.inputCallbackRef, io.onPointerMoveMethod,
                            static_cast<jint>(hand),
                            static_cast<jfloat>(ndcX),
                            static_cast<jfloat>(ndcY));
        if (env->ExceptionCheck())
        {
            env->ExceptionDescribe();
            env->ExceptionClear();
        }
    }

    // Create ActionSet, all 13 XrActions, suggest bindings for Touch + TouchPro profiles,
    // and attach the set to the session. MUST be called AFTER xrCreateSession and BEFORE
    // xrBeginSession (which runs in handleSessionStateChange on XR_SESSION_STATE_READY).
    bool setupActionSet()
    {
        auto &io = g_ctx.input;
        if (io.initialized)
        {
            LOGW("setupActionSet: already initialized, skipping");
            return true;
        }
        if (g_ctx.instance == XR_NULL_HANDLE || g_ctx.session == XR_NULL_HANDLE)
        {
            LOGE("setupActionSet: instance/session missing");
            return false;
        }

        XrActionSetCreateInfo asci{XR_TYPE_ACTION_SET_CREATE_INFO};
        std::strncpy(asci.actionSetName, "playback", XR_MAX_ACTION_SET_NAME_SIZE - 1);
        std::strncpy(asci.localizedActionSetName, "Playback controls", XR_MAX_LOCALIZED_ACTION_SET_NAME_SIZE - 1);
        asci.priority = 0;
        XrResult r = xrCreateActionSet(g_ctx.instance, &asci, &io.actionSet);
        if (XR_FAILED(r))
        {
            LOGE("setupActionSet: xrCreateActionSet failed: %d", static_cast<int>(r));
            return false;
        }

        XrPath handLeft = XR_NULL_PATH, handRight = XR_NULL_PATH;
        xrStringToPath(g_ctx.instance, "/user/hand/left", &handLeft);
        xrStringToPath(g_ctx.instance, "/user/hand/right", &handRight);

        auto makeAction = [&](const char *name, const char *loc, XrActionType kind, XrPath subaction) -> XrAction
        {
            XrAction act = XR_NULL_HANDLE;
            XrActionCreateInfo aci{XR_TYPE_ACTION_CREATE_INFO};
            std::strncpy(aci.actionName, name, XR_MAX_ACTION_NAME_SIZE - 1);
            std::strncpy(aci.localizedActionName, loc, XR_MAX_LOCALIZED_ACTION_NAME_SIZE - 1);
            aci.actionType = kind;
            if (subaction != XR_NULL_PATH)
            {
                aci.countSubactionPaths = 1;
                aci.subactionPaths = &subaction;
            }
            XrResult rr = xrCreateAction(io.actionSet, &aci, &act);
            if (XR_FAILED(rr))
                LOGE("setupActionSet: xrCreateAction(%s) failed: %d", name, static_cast<int>(rr));
            return act;
        };

        io.aPauseToggle = makeAction("a_pause", "Pause/Play (A)", XR_ACTION_TYPE_BOOLEAN_INPUT, handRight);
        io.bExit = makeAction("b_exit", "Exit (B)", XR_ACTION_TYPE_BOOLEAN_INPUT, handRight);
        io.xExit = makeAction("x_exit", "Exit dup (X)", XR_ACTION_TYPE_BOOLEAN_INPUT, handLeft);
        io.yFileOps = makeAction("y_fileops", "File ops / cheatsheet (Y)", XR_ACTION_TYPE_BOOLEAN_INPUT, handLeft);
        io.menuCtrl = makeAction("menu_ctrl", "Playback control dialog (Menu)", XR_ACTION_TYPE_BOOLEAN_INPUT, handLeft);
        io.thumbClickL = makeAction("thumb_l", "Toggle immersive (L stick click)", XR_ACTION_TYPE_BOOLEAN_INPUT, handLeft);
        io.thumbClickR = makeAction("thumb_r", "Recenter (R stick click)", XR_ACTION_TYPE_BOOLEAN_INPUT, handRight);
        io.gripL = makeAction("grip_l", "Zoom grip (L)", XR_ACTION_TYPE_FLOAT_INPUT, handLeft);
        io.gripR = makeAction("grip_r", "Zoom grip (R)", XR_ACTION_TYPE_FLOAT_INPUT, handRight);
        io.stickL = makeAction("stick_l", "Seek / volume (L stick)", XR_ACTION_TYPE_VECTOR2F_INPUT, handLeft);
        io.stickR = makeAction("stick_r", "File / volume (R stick)", XR_ACTION_TYPE_VECTOR2F_INPUT, handRight);
        io.hapticL = makeAction("haptic_l", "Haptic (L)", XR_ACTION_TYPE_VIBRATION_OUTPUT, handLeft);
        io.hapticR = makeAction("haptic_r", "Haptic (R)", XR_ACTION_TYPE_VIBRATION_OUTPUT, handRight);
        // spec_vr-immersive-controls-panel Phase 02: aim pose + trigger for controller ray.
        io.aimPoseL = makeAction("aim_l", "Aim pose (L)", XR_ACTION_TYPE_POSE_INPUT, handLeft);
        io.aimPoseR = makeAction("aim_r", "Aim pose (R)", XR_ACTION_TYPE_POSE_INPUT, handRight);
        io.triggerL = makeAction("trigger_l", "Trigger (L)", XR_ACTION_TYPE_FLOAT_INPUT, handLeft);
        io.triggerR = makeAction("trigger_r", "Trigger (R)", XR_ACTION_TYPE_FLOAT_INPUT, handRight);

        XrPath pLeftX, pLeftY, pLeftMenu, pLeftThumb, pLeftThumbClk, pLeftGrip, pLeftHaptic;
        XrPath pRightA, pRightB, pRightThumb, pRightThumbClk, pRightGrip, pRightHaptic;
        XrPath pLeftAim, pRightAim, pLeftTrigger, pRightTrigger;
        xrStringToPath(g_ctx.instance, "/user/hand/left/input/x/click", &pLeftX);
        xrStringToPath(g_ctx.instance, "/user/hand/left/input/y/click", &pLeftY);
        xrStringToPath(g_ctx.instance, "/user/hand/left/input/menu/click", &pLeftMenu);
        xrStringToPath(g_ctx.instance, "/user/hand/left/input/thumbstick", &pLeftThumb);
        xrStringToPath(g_ctx.instance, "/user/hand/left/input/thumbstick/click", &pLeftThumbClk);
        xrStringToPath(g_ctx.instance, "/user/hand/left/input/squeeze/value", &pLeftGrip);
        xrStringToPath(g_ctx.instance, "/user/hand/left/output/haptic", &pLeftHaptic);
        xrStringToPath(g_ctx.instance, "/user/hand/right/input/a/click", &pRightA);
        xrStringToPath(g_ctx.instance, "/user/hand/right/input/b/click", &pRightB);
        xrStringToPath(g_ctx.instance, "/user/hand/right/input/thumbstick", &pRightThumb);
        xrStringToPath(g_ctx.instance, "/user/hand/right/input/thumbstick/click", &pRightThumbClk);
        xrStringToPath(g_ctx.instance, "/user/hand/right/input/squeeze/value", &pRightGrip);
        xrStringToPath(g_ctx.instance, "/user/hand/right/output/haptic", &pRightHaptic);
        xrStringToPath(g_ctx.instance, "/user/hand/left/input/aim/pose", &pLeftAim);
        xrStringToPath(g_ctx.instance, "/user/hand/right/input/aim/pose", &pRightAim);
        xrStringToPath(g_ctx.instance, "/user/hand/left/input/trigger/value", &pLeftTrigger);
        xrStringToPath(g_ctx.instance, "/user/hand/right/input/trigger/value", &pRightTrigger);

        std::vector<XrActionSuggestedBinding> bindings = {
            {io.aPauseToggle, pRightA},
            {io.bExit, pRightB},
            {io.xExit, pLeftX},
            {io.yFileOps, pLeftY},
            {io.menuCtrl, pLeftMenu},
            {io.thumbClickL, pLeftThumbClk},
            {io.thumbClickR, pRightThumbClk},
            {io.gripL, pLeftGrip},
            {io.gripR, pRightGrip},
            {io.stickL, pLeftThumb},
            {io.stickR, pRightThumb},
            {io.hapticL, pLeftHaptic},
            {io.hapticR, pRightHaptic},
            {io.aimPoseL, pLeftAim},
            {io.aimPoseR, pRightAim},
            {io.triggerL, pLeftTrigger},
            {io.triggerR, pRightTrigger},
        };

        auto suggestProfile = [&](const char *profileStr)
        {
            XrPath profilePath = XR_NULL_PATH;
            xrStringToPath(g_ctx.instance, profileStr, &profilePath);
            XrInteractionProfileSuggestedBinding sugg{XR_TYPE_INTERACTION_PROFILE_SUGGESTED_BINDING};
            sugg.interactionProfile = profilePath;
            sugg.countSuggestedBindings = static_cast<uint32_t>(bindings.size());
            sugg.suggestedBindings = bindings.data();
            XrResult sr = xrSuggestInteractionProfileBindings(g_ctx.instance, &sugg);
            if (XR_FAILED(sr))
            {
                LOGD("setupActionSet: suggest %s failed: %d (non-fatal if profile unsupported)",
                     profileStr, static_cast<int>(sr));
            }
            else
            {
                LOGI("setupActionSet: suggested bindings for %s (%zu)", profileStr, bindings.size());
            }
        };
        suggestProfile("/interaction_profiles/oculus/touch_controller");      // Quest 2 / 3
        suggestProfile("/interaction_profiles/oculus/touch_pro_controller");  // Quest Pro
        suggestProfile("/interaction_profiles/meta/touch_plus_controller");   // Quest 3 Touch Plus

        XrSessionActionSetsAttachInfo asai{XR_TYPE_SESSION_ACTION_SETS_ATTACH_INFO};
        asai.countActionSets = 1;
        asai.actionSets = &io.actionSet;
        XrResult ar = xrAttachSessionActionSets(g_ctx.session, &asai);
        if (XR_FAILED(ar))
        {
            LOGE("setupActionSet: xrAttachSessionActionSets failed: %d", static_cast<int>(ar));
            return false;
        }

        io.initialized = true;
        // Create aim pose action spaces for controller ray NDC projection.
        createControllerAimSpaces();
        LOGI("setupActionSet: input system ready (17 actions, 3 profiles attached)");
        return true;
    }

    // Create XrActionSpace for each hand's aim pose action.
    // Called after xrAttachSessionActionSets when session + action set are both ready.
    bool createControllerAimSpaces()
    {
        auto &io = g_ctx.input;
        auto createSpace = [&](XrAction act, XrSpace &space) -> bool
        {
            if (act == XR_NULL_HANDLE) return false;
            XrActionSpaceCreateInfo asci{XR_TYPE_ACTION_SPACE_CREATE_INFO};
            asci.action = act;
            asci.subactionPath = XR_NULL_PATH;
            asci.poseInActionSpace.orientation = {0.0f, 0.0f, 0.0f, 1.0f};
            asci.poseInActionSpace.position    = {0.0f, 0.0f, 0.0f};
            XrResult r = xrCreateActionSpace(g_ctx.session, &asci, &space);
            if (XR_FAILED(r))
            {
                LOGW("createControllerAimSpaces: xrCreateActionSpace failed: %d", static_cast<int>(r));
                return false;
            }
            return true;
        };
        bool okL = createSpace(io.aimPoseL, io.aimSpaceL);
        bool okR = createSpace(io.aimPoseR, io.aimSpaceR);
        LOGI("createControllerAimSpaces: L=%d R=%d", static_cast<int>(okL), static_cast<int>(okR));
        return okL && okR;
    }

    // Per-frame: xrSyncActions, read each action state, detect edges, emit events.
    // Called from renderFrame(); runs on xr-render-thread; g_ctxMutex already held.
    void syncInputActions(JNIEnv *env)
    {
        auto &io = g_ctx.input;
        if (!io.initialized || io.actionSet == XR_NULL_HANDLE || !g_ctx.sessionRunning)
            return;

        XrActiveActionSet activeSet{};
        activeSet.actionSet = io.actionSet;
        activeSet.subactionPath = XR_NULL_PATH;
        XrActionsSyncInfo sync{XR_TYPE_ACTIONS_SYNC_INFO};
        sync.countActiveActionSets = 1;
        sync.activeActionSets = &activeSet;
        if (XR_FAILED(xrSyncActions(g_ctx.session, &sync)))
            return;

        auto readBool = [&](XrAction act) -> bool
        {
            if (act == XR_NULL_HANDLE)
                return false;
            XrActionStateGetInfo gi{XR_TYPE_ACTION_STATE_GET_INFO};
            gi.action = act;
            XrActionStateBoolean st{XR_TYPE_ACTION_STATE_BOOLEAN};
            if (XR_FAILED(xrGetActionStateBoolean(g_ctx.session, &gi, &st)))
                return false;
            return st.isActive && st.currentState;
        };
        auto readFloat = [&](XrAction act) -> float
        {
            if (act == XR_NULL_HANDLE)
                return 0.0f;
            XrActionStateGetInfo gi{XR_TYPE_ACTION_STATE_GET_INFO};
            gi.action = act;
            XrActionStateFloat st{XR_TYPE_ACTION_STATE_FLOAT};
            if (XR_FAILED(xrGetActionStateFloat(g_ctx.session, &gi, &st)))
                return 0.0f;
            return st.isActive ? st.currentState : 0.0f;
        };
        auto readVec2 = [&](XrAction act) -> XrVector2f
        {
            XrVector2f zero{0.0f, 0.0f};
            if (act == XR_NULL_HANDLE)
                return zero;
            XrActionStateGetInfo gi{XR_TYPE_ACTION_STATE_GET_INFO};
            gi.action = act;
            XrActionStateVector2f st{XR_TYPE_ACTION_STATE_VECTOR2F};
            if (XR_FAILED(xrGetActionStateVector2f(g_ctx.session, &gi, &st)))
                return zero;
            return st.isActive ? st.currentState : zero;
        };

        // ── Boolean rising-edge detection ───────────────────────────────────
        bool a = readBool(io.aPauseToggle);
        if (a && !io.prevA)
            emitInputEvent(env, XR_EVT_PAUSE_TOGGLE, 1, 0.0f, XR_SRC_CONTROLLER);
        io.prevA = a;

        bool b = readBool(io.bExit);
        if (b && !io.prevB)
            emitInputEvent(env, XR_EVT_EXIT, 1, 0.0f, XR_SRC_CONTROLLER);
        io.prevB = b;

        bool x = readBool(io.xExit);
        if (x && !io.prevX)
            emitInputEvent(env, XR_EVT_EXIT, 0, 0.0f, XR_SRC_CONTROLLER);
        io.prevX = x;

        bool menu = readBool(io.menuCtrl);
        if (menu && !io.prevMenu)
            emitInputEvent(env, XR_EVT_MENU, 0, 0.0f, XR_SRC_CONTROLLER);
        io.prevMenu = menu;

        bool tL = readBool(io.thumbClickL);
        if (tL && !io.prevThumbL)
            emitInputEvent(env, XR_EVT_TOGGLE_IMMERSIVE, 0, 0.0f, XR_SRC_CONTROLLER);
        io.prevThumbL = tL;

        bool tR = readBool(io.thumbClickR);
        if (tR && !io.prevThumbR)
            emitInputEvent(env, XR_EVT_RECENTER, 1, 0.0f, XR_SRC_CONTROLLER);
        io.prevThumbR = tR;

        // Y button: press timestamps + short(<0.8s)=FILE_OPS / long(>=0.8s)=CHEATSHEET.
        bool y = readBool(io.yFileOps);
        if (y && !io.prevY)
        {
            io.yPressTimeNs = monotonicNowNs();
            io.yLongPressEmitted = false;
        }
        else if (y && !io.yLongPressEmitted)
        {
            if ((monotonicNowNs() - io.yPressTimeNs) >= kYLongPressNs)
            {
                emitInputEvent(env, XR_EVT_CHEATSHEET, 0, 0.0f, XR_SRC_CONTROLLER);
                io.yLongPressEmitted = true;
            }
        }
        else if (!y && io.prevY)
        {
            if (!io.yLongPressEmitted && (monotonicNowNs() - io.yPressTimeNs) < kYLongPressNs)
                emitInputEvent(env, XR_EVT_FILE_OPS, 0, 0.0f, XR_SRC_CONTROLLER);
        }
        io.prevY = y;

        // ── Grip: analog zoom ──────────────────────────────────────────────
        auto processGrip = [&](float current, float &prev, int hand)
        {
            if (current > kGripPressThresh && prev <= kGripPressThresh)
                emitInputEvent(env, XR_EVT_ZOOM_START, hand, current, XR_SRC_CONTROLLER);
            else if (current < kGripReleaseThresh && prev >= kGripReleaseThresh)
                emitInputEvent(env, XR_EVT_ZOOM_END, hand, current, XR_SRC_CONTROLLER);
            else if (current > kGripPressThresh)
            {
                float d = current - prev;
                if (d > kGripDeltaMin || d < -kGripDeltaMin)
                    emitInputEvent(env, XR_EVT_ZOOM_DELTA, hand, d, XR_SRC_CONTROLLER);
            }
            prev = current;
        };
        float gL = readFloat(io.gripL);
        float gR = readFloat(io.gripR);
        processGrip(gL, io.prevGripL, 0);
        processGrip(gR, io.prevGripR, 1);

        // Both grips held ≥ 1 s → zoom reset.
        if (gL > kGripPressThresh && gR > kGripPressThresh)
        {
            if (io.bothGripsPressTimeNs == 0)
                io.bothGripsPressTimeNs = monotonicNowNs();
            else if (!io.bothGripsResetEmitted &&
                     (monotonicNowNs() - io.bothGripsPressTimeNs) >= kBothGripsHoldNs)
            {
                emitInputEvent(env, XR_EVT_ZOOM_RESET, -1, 0.0f, XR_SRC_CONTROLLER);
                io.bothGripsResetEmitted = true;
            }
        }
        else
        {
            io.bothGripsPressTimeNs = 0;
            io.bothGripsResetEmitted = false;
        }

        // ── Sticks: threshold + hysteresis → discrete edge events ──────────
        auto processStick = [&](XrAction act, bool &edgeX, bool &edgeY, bool isLeft)
        {
            XrVector2f v = readVec2(act);
            // X axis
            if (!edgeX)
            {
                if (v.x > kStickThreshold)
                {
                    emitInputEvent(env,
                                   isLeft ? XR_EVT_SEEK_FORWARD : XR_EVT_FILE_NEXT,
                                   isLeft ? 0 : 1, v.x, XR_SRC_CONTROLLER);
                    edgeX = true;
                }
                else if (v.x < -kStickThreshold)
                {
                    emitInputEvent(env,
                                   isLeft ? XR_EVT_SEEK_BACKWARD : XR_EVT_FILE_PREV,
                                   isLeft ? 0 : 1, -v.x, XR_SRC_CONTROLLER);
                    edgeX = true;
                }
            }
            else if (v.x > -kStickReturnZone && v.x < kStickReturnZone)
            {
                edgeX = false;
            }
            // Y axis → volume (both sticks duplicate)
            if (!edgeY)
            {
                if (v.y > kStickThreshold)
                {
                    emitInputEvent(env, XR_EVT_VOLUME_UP, isLeft ? 0 : 1, v.y, XR_SRC_CONTROLLER);
                    edgeY = true;
                }
                else if (v.y < -kStickThreshold)
                {
                    emitInputEvent(env, XR_EVT_VOLUME_DOWN, isLeft ? 0 : 1, -v.y, XR_SRC_CONTROLLER);
                    edgeY = true;
                }
            }
            else if (v.y > -kStickReturnZone && v.y < kStickReturnZone)
            {
                edgeY = false;
            }
        };
        processStick(io.stickL, io.stickLEdgeXActive, io.stickLEdgeYActive, /*isLeft=*/true);
        processStick(io.stickR, io.stickREdgeXActive, io.stickREdgeYActive, /*isLeft=*/false);
    }

    // Destroy OpenXR action handles (no env required).
    void destroyInputHandles()
    {
        auto &io = g_ctx.input;
        if (io.actionSet != XR_NULL_HANDLE)
        {
            xrDestroyActionSet(io.actionSet);
            io.actionSet = XR_NULL_HANDLE;
        }
        io.aPauseToggle = io.bExit = io.xExit = io.yFileOps = io.menuCtrl = XR_NULL_HANDLE;
        io.thumbClickL = io.thumbClickR = XR_NULL_HANDLE;
        io.gripL = io.gripR = XR_NULL_HANDLE;
        io.stickL = io.stickR = XR_NULL_HANDLE;
        io.hapticL = io.hapticR = XR_NULL_HANDLE;
        // Controller aim spaces (Phase 02).
        if (io.aimSpaceL != XR_NULL_HANDLE) { xrDestroySpace(io.aimSpaceL); io.aimSpaceL = XR_NULL_HANDLE; }
        if (io.aimSpaceR != XR_NULL_HANDLE) { xrDestroySpace(io.aimSpaceR); io.aimSpaceR = XR_NULL_HANDLE; }
        io.aimPoseL = io.aimPoseR = XR_NULL_HANDLE;
        io.triggerL = io.triggerR = XR_NULL_HANDLE;
        io.initialized = false;
        io.prevA = io.prevB = io.prevX = io.prevY = false;
        io.prevMenu = io.prevThumbL = io.prevThumbR = false;
        io.prevGripL = io.prevGripR = 0.0f;
        io.stickLEdgeXActive = io.stickLEdgeYActive = false;
        io.stickREdgeXActive = io.stickREdgeYActive = false;
        io.yPressTimeNs = 0;
        io.yLongPressEmitted = false;
        io.bothGripsPressTimeNs = 0;
        io.bothGripsResetEmitted = false;
    }

    // Release the Kotlin callback global ref. Requires a valid env.
    void releaseInputCallback(JNIEnv *env)
    {
        auto &io = g_ctx.input;
        if (io.inputCallbackRef && env)
            env->DeleteGlobalRef(io.inputCallbackRef);
        io.inputCallbackRef = nullptr;
        io.onInputEventMethod = nullptr;
        io.onPointerMoveMethod = nullptr;
        io.onControllerPointerMoveMethod = nullptr;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Hand-tracking subsystem (Layer E) — spec_vr-hand-tracking-tech §5.1.
    // ═══════════════════════════════════════════════════════════════════════

    bool initHandTracking()
    {
        auto &h = g_ctx.hands;
        if (h.initialized)
            return true;
        if (!g_ctx.supportsHandTracking)
        {
            LOGI("initHandTracking: XR_EXT_hand_tracking unsupported on this runtime — Layer E disabled");
            return false;
        }
        if (g_ctx.instance == XR_NULL_HANDLE || g_ctx.session == XR_NULL_HANDLE)
        {
            LOGE("initHandTracking: instance/session missing");
            return false;
        }

        // Resolve function pointers lazily — if any entry is missing we treat hand
        // tracking as absent rather than aborting session creation.
        auto resolveProc = [&](const char *name) -> PFN_xrVoidFunction
        {
            PFN_xrVoidFunction raw = nullptr;
            XrResult r = xrGetInstanceProcAddr(g_ctx.instance, name, &raw);
            if (XR_FAILED(r) || raw == nullptr)
            {
                LOGW("initHandTracking: xrGetInstanceProcAddr(%s) result=%d ptr=%p",
                     name, static_cast<int>(r), reinterpret_cast<void *>(raw));
                return nullptr;
            }
            return raw;
        };
        h.pfnCreate = reinterpret_cast<PFN_xrCreateHandTrackerEXT_LOCAL>(resolveProc("xrCreateHandTrackerEXT"));
        h.pfnDestroy = reinterpret_cast<PFN_xrDestroyHandTrackerEXT_LOCAL>(resolveProc("xrDestroyHandTrackerEXT"));
        h.pfnLocate = reinterpret_cast<PFN_xrLocateHandJointsEXT_LOCAL>(resolveProc("xrLocateHandJointsEXT"));
        if (!h.pfnCreate || !h.pfnDestroy || !h.pfnLocate)
        {
            LOGW("initHandTracking: required entry points missing — Layer E disabled");
            return false;
        }

        auto createOne = [&](XrHandEXT hand, XrHandTrackerEXT &outTracker) -> bool
        {
            XrHandTrackerCreateInfoEXT ci{};
            ci.type = XR_TYPE_HAND_TRACKER_CREATE_INFO_EXT;
            ci.hand = hand;
            ci.handJointSet = XR_HAND_JOINT_SET_DEFAULT_EXT;
            XrResult r = h.pfnCreate(g_ctx.session, &ci, &outTracker);
            if (XR_FAILED(r))
            {
                LOGW("initHandTracking: xrCreateHandTrackerEXT(hand=%d) failed: %d "
                     "(permission missing or user disabled hand tracking)",
                     static_cast<int>(hand), static_cast<int>(r));
                outTracker = XR_NULL_HANDLE;
                return false;
            }
            return true;
        };
        bool left = createOne(XR_HAND_LEFT_EXT, h.trackerL);
        bool right = createOne(XR_HAND_RIGHT_EXT, h.trackerR);
        if (!left && !right)
        {
            LOGW("initHandTracking: no trackers created — Layer E disabled at runtime");
            return false;
        }

        h.initialized = true;
        LOGI("initHandTracking: ready  left=%d right=%d  aim=%d microgestures=%d",
             static_cast<int>(left), static_cast<int>(right),
             static_cast<int>(g_ctx.supportsHandAim),
             static_cast<int>(g_ctx.supportsMicrogestures));
        return true;
    }

    void destroyHandTracking()
    {
        auto &h = g_ctx.hands;
        if (h.pfnDestroy)
        {
            if (h.trackerL != XR_NULL_HANDLE)
            {
                h.pfnDestroy(h.trackerL);
                h.trackerL = XR_NULL_HANDLE;
            }
            if (h.trackerR != XR_NULL_HANDLE)
            {
                h.pfnDestroy(h.trackerR);
                h.trackerR = XR_NULL_HANDLE;
            }
        }
        h.pfnCreate = nullptr;
        h.pfnDestroy = nullptr;
        h.pfnLocate = nullptr;
        h.isPinchingL = h.isPinchingR = false;
        h.aimFrozenL = h.aimFrozenR = false;
        h.prevGesturesL = h.prevGesturesR = 0;
        h.initialized = false;
    }

    void emitControllerPointerMove(JNIEnv *env, int32_t hand, float ndcX, float ndcY)
    {
        auto &io = g_ctx.input;
        if (!io.inputCallbackRef || !io.onControllerPointerMoveMethod || !env)
            return;
        env->CallVoidMethod(io.inputCallbackRef, io.onControllerPointerMoveMethod,
                            static_cast<jint>(hand),
                            static_cast<jfloat>(ndcX),
                            static_cast<jfloat>(ndcY));
    }

    // Project controller aim pose onto the UI plane and emit NDC.
    // UI plane: flat quad at g_uiPlaneDistance m forward in local space (same as hand ray).
    void syncControllerAimRay(JNIEnv *env)
    {
        auto &io = g_ctx.input;
        if (!io.initialized || !g_ctx.sessionRunning || !g_ctx.viewSpace)
            return;
        // WHY: use the most recent predicted display time cached by renderFrame.
        // Aim space location must be queried with the frame's predicted time for low latency.
        XrTime t = g_ctx.lastPredictedDisplayTime;
        if (t == 0) return;

        // UI plane distance in metres — must match the value used for hand tracking NDC.
        constexpr float kPlaneDistance = 1.5f;

        auto processHand = [&](XrSpace aimSpace, int handIdx)
        {
            if (aimSpace == XR_NULL_HANDLE)
            {
                emitControllerPointerMove(env, handIdx, 2.0f, 2.0f);
                return;
            }
            XrSpaceLocation loc{XR_TYPE_SPACE_LOCATION};
            if (XR_FAILED(xrLocateSpace(aimSpace, g_ctx.viewSpace, t, &loc)))
            {
                emitControllerPointerMove(env, handIdx, 2.0f, 2.0f);
                return;
            }
            const bool valid =
                (loc.locationFlags & XR_SPACE_LOCATION_POSITION_VALID_BIT) &&
                (loc.locationFlags & XR_SPACE_LOCATION_ORIENTATION_VALID_BIT);
            if (!valid)
            {
                emitControllerPointerMove(env, handIdx, 2.0f, 2.0f);
                return;
            }
            // Build ray direction from aim quaternion.
            const XrQuaternionf &q = loc.pose.orientation;
            // OpenXR aim: forward = -Z in aim space, transform to view space.
            // Ray dir = rotate (0,0,-1) by aim quaternion.
            float rx = 2.0f * (q.x * q.z - q.w * q.y);
            float ry = 2.0f * (q.y * q.z + q.w * q.x);
            float rz = -(1.0f - 2.0f * (q.x * q.x + q.y * q.y));

            // Ray origin = aim pose position.
            const XrVector3f &origin = loc.pose.position;

            // Intersect with plane z = -kPlaneDistance (in view/head space).
            // Solve: origin.z + t*rz = -kPlaneDistance → t = (-kPlaneDistance - origin.z) / rz
            if (fabsf(rz) < 1e-6f)
            {
                emitControllerPointerMove(env, handIdx, 2.0f, 2.0f);
                return;
            }
            float hitT = (-kPlaneDistance - origin.z) / rz;
            if (hitT < 0.0f)
            {
                emitControllerPointerMove(env, handIdx, 2.0f, 2.0f);
                return;
            }
            float hitX = origin.x + hitT * rx;
            float hitY = origin.y + hitT * ry;

            // Normalise to NDC: the HUD quad is 1.0 m × 0.3 m centred at (0, -0.35, -1.5).
            // Map hit position relative to quad centre to NDC [-1,1].
            constexpr float kHudHalfW = 0.5f;   // half of 1.0 m width
            constexpr float kHudHalfH = 0.15f;  // half of 0.3 m height
            constexpr float kHudCentreY = -0.35f;
            float ndcX = hitX / kHudHalfW;
            float ndcY = (hitY - kHudCentreY) / kHudHalfH;
            emitControllerPointerMove(env, handIdx, ndcX, ndcY);

            // TODO(Phase 03): draw a visual ray using a GLES3 VBO + passthrough shader.
            // GLES3 has no fixed-function pipeline; a proper vertex+fragment program is
            // required. Deferred — NDC emission above is the Phase 02 deliverable.
            (void)g_ctx.controllerRayEnabled.load(); // suppress unused-read warning
        };

        processHand(io.aimSpaceL, 0);
        processHand(io.aimSpaceR, 1);
    }

    // Per-frame hand polling. Runs AFTER syncInputActions so the controller modality
    // gate sees the freshest controller edge timestamp.
    void syncHandTracking(JNIEnv *env)
    {
        auto &h = g_ctx.hands;
        auto &io = g_ctx.input;
        if (!h.initialized || !g_ctx.sessionRunning)
            return;

        // Modality gate: suppress hand polling while controllers emit edge events.
        // This is the strict priority lock from §3.3 — controllers unconditionally
        // win; hand output is dropped during/for 2 s after any controller event.
        const int64_t nowNs = monotonicNowNs();
        if (io.lastControllerEventNs != 0 &&
            (nowNs - io.lastControllerEventNs) < kControllerIdleSwitchNs)
        {
            // If a pinch was in flight when the controller re-engaged we must still
            // emit the matching CLICK_UP so the Kotlin view hierarchy does not stay
            // in a pressed state. Off-plane pointer hides the cursor dot too.
            if (h.isPinchingL && !h.suppressClickReleaseL)
                emitInputEvent(env, XR_EVT_POINTER_CLICK_UP, 0, 0.0f, XR_SRC_HAND);
            if (h.isPinchingR && !h.suppressClickReleaseR)
                emitInputEvent(env, XR_EVT_POINTER_CLICK_UP, 1, 0.0f, XR_SRC_HAND);
            if (h.isPinchingL || h.isPinchingR || h.aimFrozenL || h.aimFrozenR)
            {
                emitPointerMove(env, 0, 2.0f, 2.0f);
                emitPointerMove(env, 1, 2.0f, 2.0f);
            }
            h.isPinchingL = h.isPinchingR = false;
            h.suppressClickReleaseL = h.suppressClickReleaseR = false;
            h.aimFrozenL = h.aimFrozenR = false;
            h.lastPinchDownNs = 0;
            return;
        }

        if (g_ctx.appSpace == XR_NULL_HANDLE)
            return;

        // Frame predicted display time is unavailable here — use an approximation
        // via xrLocateHandJointsEXT with current monotonic time converted to XrTime.
        // Meta runtimes accept XrTime == CLOCK_MONOTONIC nanoseconds directly because
        // XR_KHR_convert_timespec_time is the canonical conversion; our approximation
        // is accurate to within a render frame which is sufficient for UI targeting.
        const XrTime predictedTime = static_cast<XrTime>(nowNs);

        auto processHand = [&](XrHandTrackerEXT tracker, int handIdx,
                               XrHandJointLocationEXT *jointsBuf,
                               bool &pinchState, bool &suppressClickRelease,
                               bool &frozen, float &frozenX, float &frozenY,
                               XrHandMicrogestureFlagsMETA &prevGestures)
        {
            if (tracker == XR_NULL_HANDLE)
                return;

            XrHandJointLocationsEXT locations{};
            locations.type = XR_TYPE_HAND_JOINT_LOCATIONS_EXT;
            locations.jointCount = XR_HAND_JOINT_COUNT_EXT;
            locations.jointLocations = jointsBuf;

            // Chain aim state + microgestures onto the next pointer so we get them
            // atomically with the joints in one runtime call.
            XrHandTrackingAimStateFB aimState{};
            aimState.type = XR_TYPE_HAND_TRACKING_AIM_STATE_FB;
            XrHandMicrogesturesStateMETA mgState{};
            mgState.type = XR_TYPE_HAND_MICROGESTURES_STATE_META;

            void *nextChain = nullptr;
            if (g_ctx.supportsHandAim)
            {
                aimState.next = nextChain;
                nextChain = &aimState;
            }
            if (g_ctx.supportsMicrogestures)
            {
                mgState.next = nextChain;
                nextChain = &mgState;
            }
            locations.next = nextChain;

            XrHandJointsLocateInfoEXT locateInfo{};
            locateInfo.type = XR_TYPE_HAND_JOINTS_LOCATE_INFO_EXT;
            locateInfo.baseSpace = g_ctx.appSpace;
            locateInfo.time = predictedTime;

            XrResult r = h.pfnLocate(tracker, &locateInfo, &locations);
            if (XR_FAILED(r) || !locations.isActive)
            {
                // Tracking lost or hand out of view — reset pinch state so we don't
                // emit a dangling CLICK_UP once it comes back, and emit an off-plane
                // pointer so the Kotlin ray manager hides the cursor dot.
                if (pinchState && !suppressClickRelease)
                {
                    emitInputEvent(env, XR_EVT_POINTER_CLICK_UP, handIdx, 0.0f, XR_SRC_HAND);
                }
                pinchState = false;
                suppressClickRelease = false;
                frozen = false;
                prevGestures = 0;
                emitPointerMove(env, handIdx, 2.0f, 2.0f);
                return;
            }

            // Suppress hand input while the user is performing the Meta system gesture
            // (palm up → system UI summon). Emitting clicks here would interact with
            // the overlay while the user clearly intends to open the OS menu.
            // COMPUTED must be set before any other status bit can be trusted.
            const bool aimComputed = g_ctx.supportsHandAim &&
                                     (aimState.status & XR_HAND_TRACKING_AIM_COMPUTED_BIT_FB) != 0;
            if (aimComputed &&
                (aimState.status & XR_HAND_TRACKING_AIM_SYSTEM_GESTURE_BIT_FB) != 0)
            {
                if (pinchState && !suppressClickRelease)
                {
                    emitInputEvent(env, XR_EVT_POINTER_CLICK_UP, handIdx, 0.0f, XR_SRC_HAND);
                }
                pinchState = false;
                suppressClickRelease = false;
                frozen = false;
                emitPointerMove(env, handIdx, 2.0f, 2.0f);
                return;
            }

            // Ray-plane intersection against the UI quad layer.
            // LayerConfig positions the quad at z = -distanceMeters, facing the user,
            // centred in the LOCAL reference frame (see createProjectionLayer below).
            // Plane normal = (0,0,1) pointing at the camera; plane point = (0,0,-d).
            // Ray origin = aimPose.position; direction = aimPose.orientation * (0,0,-1).
            float ndcX = 2.0f, ndcY = 2.0f; // Off-plane sentinel: > 1.0 means "no hit".
            if (aimComputed && (aimState.status & XR_HAND_TRACKING_AIM_VALID_BIT_FB))
            {
                const XrPosef &p = aimState.aimPose;
                // Rotate (0,0,-1) by quaternion q to get forward direction.
                // forward = q * (0,0,-1) * q^-1 — expanded below.
                const float qx = p.orientation.x;
                const float qy = p.orientation.y;
                const float qz = p.orientation.z;
                const float qw = p.orientation.w;
                // Derived from standard quaternion-vector rotation of (0,0,-1):
                const float fx = -2.0f * (qx * qz + qw * qy);
                const float fy = -2.0f * (qy * qz - qw * qx);
                const float fz = -(1.0f - 2.0f * (qx * qx + qy * qy));

                const float planeDist = g_ctx.layerConfig.distanceMeters;
                const float halfW = g_ctx.layerConfig.widthMeters * 0.5f;
                const float halfH = g_ctx.layerConfig.heightMeters * 0.5f;
                // Intersection of ray(origin + t*forward) with plane z = -planeDist.
                if (fz < -1e-4f) // Pointing forward.
                {
                    const float t = (-planeDist - p.position.z) / fz;
                    if (t > 0.0f)
                    {
                        const float ix = p.position.x + t * fx;
                        const float iy = p.position.y + t * fy;
                        // Normalise to NDC; UI Y convention: +Y up in world, but Android
                        // view coords put +Y down — the Kotlin ray manager flips it.
                        if (halfW > 1e-4f && halfH > 1e-4f)
                        {
                            ndcX = ix / halfW;
                            ndcY = iy / halfH;
                        }
                    }
                }
            }

            // Aim-freeze: once pinch strength crosses mid-threshold, lock the NDC XY
            // so the last-frame click lands on the button the user was targeting
            // (§6 mitigation against jitter during pinch closure).
            const float pinchStrength = aimComputed ? aimState.pinchStrengthIndex : 0.0f;
            if (pinchStrength >= kPinchAimFreezeThreshold)
            {
                if (!frozen)
                {
                    frozen = true;
                    frozenX = ndcX;
                    frozenY = ndcY;
                }
                // Override the reported NDC with the latched value while pinch is active.
                ndcX = frozenX;
                ndcY = frozenY;
            }
            else
            {
                frozen = false;
            }

            // Always emit pointer position so the Kotlin ray manager can render the
            // cursor and drive hover/move into the view hierarchy.
            emitPointerMove(env, handIdx, ndcX, ndcY);

            // Pinch hysteresis — CLICK_DOWN on rising edge past 0.9, CLICK_UP when
            // dropping below 0.6 (spec §5.1).
            if (!pinchState && pinchStrength >= kPinchPressThreshold)
            {
                pinchState = true;
                if (h.lastPinchDownNs != 0 &&
                    (nowNs - h.lastPinchDownNs) <= kDoublePinchWindowNs)
                {
                    // Native owns the double-pinch gesture now: the second pinch should
                    // toggle play/pause globally, not deliver a second UI click pair.
                    suppressClickRelease = true;
                    h.lastPinchDownNs = 0;
                    emitInputEvent(env, XR_EVT_DOUBLE_PINCH, handIdx, pinchStrength, XR_SRC_HAND);
                }
                else
                {
                    suppressClickRelease = false;
                    h.lastPinchDownNs = nowNs;
                    emitInputEvent(env, XR_EVT_POINTER_CLICK_DOWN, handIdx, pinchStrength, XR_SRC_HAND);
                }
            }
            else if (pinchState && pinchStrength <= kPinchReleaseThreshold)
            {
                pinchState = false;
                if (!suppressClickRelease)
                    emitInputEvent(env, XR_EVT_POINTER_CLICK_UP, handIdx, pinchStrength, XR_SRC_HAND);
                suppressClickRelease = false;
            }

            // Microgestures — emit one event per bit that became set this sync.
            // We use `gesturesSinceLastSync` rather than `currentGestures` so a short
            // swipe fires exactly once even if it spans multiple frames.
            if (g_ctx.supportsMicrogestures && mgState.gesturesSinceLastSync != prevGestures)
            {
                XrHandMicrogestureFlagsMETA rising = mgState.gesturesSinceLastSync & ~prevGestures;
                if (rising & XR_HAND_MICROGESTURE_SWIPE_LEFT_META)
                    emitInputEvent(env, XR_EVT_SWIPE_LEFT, handIdx, 0.0f, XR_SRC_HAND);
                if (rising & XR_HAND_MICROGESTURE_SWIPE_RIGHT_META)
                    emitInputEvent(env, XR_EVT_SWIPE_RIGHT, handIdx, 0.0f, XR_SRC_HAND);
                if (rising & XR_HAND_MICROGESTURE_SWIPE_UP_META)
                    emitInputEvent(env, XR_EVT_SWIPE_UP, handIdx, 0.0f, XR_SRC_HAND);
                if (rising & XR_HAND_MICROGESTURE_SWIPE_DOWN_META)
                    emitInputEvent(env, XR_EVT_SWIPE_DOWN, handIdx, 0.0f, XR_SRC_HAND);
                prevGestures = mgState.gesturesSinceLastSync;
            }
        };

        processHand(h.trackerL, 0, h.jointsL, h.isPinchingL, h.suppressClickReleaseL, h.aimFrozenL, h.frozenAimXL, h.frozenAimYL, h.prevGesturesL);
        processHand(h.trackerR, 1, h.jointsR, h.isPinchingR, h.suppressClickReleaseR, h.aimFrozenR, h.frozenAimXR, h.frozenAimYR, h.prevGesturesR);
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

    void renderFrame(JNIEnv *env)
    {
        // Static frame counter — never wraps in practice but uint64 just in case.
        static uint64_t s_frameCount = 0;
        ++s_frameCount;

        // Poll OpenXR controller input FIRST so edge events for the current frame
        // are delivered before rendering happens. Guarded internally on initialized+running.
        syncInputActions(env);

        // Then poll hand tracking — the modality gate inside syncHandTracking uses
        // controller edge timestamps updated by the previous call to enforce the
        // strict priority (§3.3): controllers always override hands.
        syncHandTracking(env);
        syncControllerAimRay(env);

        XrFrameWaitInfo waitInfo{XR_TYPE_FRAME_WAIT_INFO};
        XrFrameState frameState{XR_TYPE_FRAME_STATE};
        XrResult waitResult = xrWaitFrame(g_ctx.session, &waitInfo, &frameState);
        if (XR_FAILED(waitResult))
        {
            LOGE("xrWaitFrame FAILED: %d (frame #%llu)",
                 static_cast<int>(waitResult),
                 static_cast<unsigned long long>(s_frameCount));
            return;
        }
        g_ctx.lastPredictedDisplayTime = frameState.predictedDisplayTime;

        XrFrameBeginInfo beginInfo{XR_TYPE_FRAME_BEGIN_INFO};
        XrResult beginResult = xrBeginFrame(g_ctx.session, &beginInfo);
        if (XR_FAILED(beginResult))
        {
            LOGE("xrBeginFrame FAILED: %d (frame #%llu)",
                 static_cast<int>(beginResult),
                 static_cast<unsigned long long>(s_frameCount));
            return;
        }

        // Throttled per-frame diagnostics — log every 300 frames to keep logcat readable.
        if (s_frameCount % 300 == 1)
        {
            LOGI("renderFrame #%llu: shouldRender=%d  state=%s  layerType=%d  "
                 "exitReq=%d  sessionRunning=%d",
                 static_cast<unsigned long long>(s_frameCount),
                 static_cast<int>(frameState.shouldRender),
                 xrSessionStateName(g_ctx.sessionState),
                 static_cast<int>(g_ctx.layerConfig.type),
                 static_cast<int>(g_ctx.exitRequested.load()),
                 static_cast<int>(g_ctx.sessionRunning));
        }

        const LayerConfig layerConfig = g_ctx.layerConfig;
        std::vector<XrCompositionLayerProjectionView> projViews(kViewCount);
        bool projectionViewsValid = (layerConfig.type != LayerType::Projection);
        bool frameValid = false;

        if (frameState.shouldRender == XR_TRUE)
        {
            uint32_t viewCount = 0;
            std::vector<XrView> views(kViewCount, {XR_TYPE_VIEW});
            if (layerConfig.type == LayerType::Projection)
            {
                XrViewState viewState{XR_TYPE_VIEW_STATE};
                XrViewLocateInfo locate{XR_TYPE_VIEW_LOCATE_INFO};
                locate.viewConfigurationType = kViewConfig;
                locate.displayTime = frameState.predictedDisplayTime;
                locate.space = g_ctx.appSpace;
                projectionViewsValid =
                    XR_SUCCEEDED(xrLocateViews(g_ctx.session, &locate, &viewState,
                                               kViewCount, &viewCount, views.data())) &&
                    (viewState.viewStateFlags & XR_VIEW_STATE_POSITION_VALID_BIT) &&
                    (viewState.viewStateFlags & XR_VIEW_STATE_ORIENTATION_VALID_BIT);
            }

            if (projectionViewsValid)
            {
                uint32_t renderedEyes = 0;
                for (uint32_t eye = 0; eye < kViewCount; ++eye)
                {
                    auto &chain = g_ctx.eyes[eye];

                    XrSwapchainImageAcquireInfo acq{XR_TYPE_SWAPCHAIN_IMAGE_ACQUIRE_INFO};
                    uint32_t imageIdx = 0;
                    if (XR_FAILED(xrAcquireSwapchainImage(chain.handle, &acq, &imageIdx)))
                        break;

                    XrSwapchainImageWaitInfo wait{XR_TYPE_SWAPCHAIN_IMAGE_WAIT_INFO};
                    wait.timeout = XR_INFINITE_DURATION;
                    xrWaitSwapchainImage(chain.handle, &wait);

                    glBindFramebuffer(GL_FRAMEBUFFER, chain.images[imageIdx].fbo);
                    glViewport(0, 0, static_cast<GLsizei>(chain.width),
                               static_cast<GLsizei>(chain.height));
                    invokeRenderCallback(env, static_cast<int>(eye),
                                         static_cast<int>(chain.images[imageIdx].fbo),
                                         static_cast<int>(chain.width),
                                         static_cast<int>(chain.height));

                    if (g_ctx.stereoSnapshot.requested.load())
                    {
                        const uint32_t pixelCount = chain.width * chain.height;
                        std::vector<uint8_t> rgba(pixelCount * 4u);
                        glReadPixels(0,
                                     0,
                                     static_cast<GLsizei>(chain.width),
                                     static_cast<GLsizei>(chain.height),
                                     GL_RGBA,
                                     GL_UNSIGNED_BYTE,
                                     rgba.data());

                        auto &dst = g_ctx.stereoSnapshot.eyePixels[eye];
                        dst.resize(pixelCount);
                        for (uint32_t y = 0; y < chain.height; ++y)
                        {
                            const uint32_t srcRow = chain.height - 1u - y;
                            const uint32_t dstRowOffset = y * chain.width;
                            const uint32_t srcRowOffset = srcRow * chain.width * 4u;
                            for (uint32_t x = 0; x < chain.width; ++x)
                            {
                                const uint32_t srcIndex = srcRowOffset + (x * 4u);
                                const uint8_t r = rgba[srcIndex + 0u];
                                const uint8_t g = rgba[srcIndex + 1u];
                                const uint8_t b = rgba[srcIndex + 2u];
                                const uint8_t a = rgba[srcIndex + 3u];
                                dst[dstRowOffset + x] =
                                    (static_cast<jint>(a) << 24) |
                                    (static_cast<jint>(r) << 16) |
                                    (static_cast<jint>(g) << 8) |
                                    static_cast<jint>(b);
                            }
                        }
                        g_ctx.stereoSnapshot.width = chain.width;
                        g_ctx.stereoSnapshot.height = chain.height;
                    }

                    XrSwapchainImageReleaseInfo rel{XR_TYPE_SWAPCHAIN_IMAGE_RELEASE_INFO};
                    xrReleaseSwapchainImage(chain.handle, &rel);

                    renderedEyes++;

                    if (layerConfig.type == LayerType::Projection)
                    {
                        projViews[eye] = {XR_TYPE_COMPOSITION_LAYER_PROJECTION_VIEW};
                        projViews[eye].pose = views[eye].pose;
                        projViews[eye].fov = views[eye].fov;
                        projViews[eye].subImage = buildFullFrameSubImage(chain);
                    }
                }
                frameValid = (renderedEyes == kViewCount);
                if (frameValid && g_ctx.stereoSnapshot.requested.load())
                {
                    // Release store: guarantees all pixel writes above are visible
                    // to any thread that observes ready via an acquire load.
                    g_ctx.stereoSnapshot.ready.store(true, std::memory_order_release);
                    g_ctx.stereoSnapshot.requested.store(false, std::memory_order_relaxed);
                }
            }
        }

        XrCompositionLayerProjection projectionLayer{XR_TYPE_COMPOSITION_LAYER_PROJECTION};
        std::vector<XrCompositionLayerQuad> quadLayers;
        std::vector<XrCompositionLayerEquirect2KHR> equirectLayers;
        std::vector<XrCompositionLayerCylinderKHR> cylinderLayers;
        std::vector<const XrCompositionLayerBaseHeader *> layers;
        if (frameValid)
        {
            if (layerConfig.type == LayerType::Projection)
            {
                projectionLayer.space = g_ctx.appSpace;
                projectionLayer.viewCount = kViewCount;
                projectionLayer.views = projViews.data();
                layers.push_back(reinterpret_cast<const XrCompositionLayerBaseHeader *>(&projectionLayer));
            }
            else if (layerConfig.type == LayerType::QuadCinema)
            {
                quadLayers.resize(kViewCount);
                for (uint32_t eye = 0; eye < kViewCount; ++eye)
                {
                    quadLayers[eye] = {XR_TYPE_COMPOSITION_LAYER_QUAD};
                    quadLayers[eye].space = g_ctx.appSpace;
                    quadLayers[eye].eyeVisibility = eyeVisibilityForIndex(eye);
                    quadLayers[eye].subImage = buildFullFrameSubImage(g_ctx.eyes[eye]);
                    quadLayers[eye].pose.orientation.w = 1.0f;
                    quadLayers[eye].pose.position.z = -layerConfig.distanceMeters;
                    quadLayers[eye].size = {layerConfig.widthMeters, layerConfig.heightMeters};
                }
                for (auto &layer : quadLayers)
                {
                    layers.push_back(reinterpret_cast<const XrCompositionLayerBaseHeader *>(&layer));
                }
            }
            else if (layerConfig.type == LayerType::Equirect2 && g_ctx.supportsEquirect2)
            {
                equirectLayers.resize(kViewCount);
                for (uint32_t eye = 0; eye < kViewCount; ++eye)
                {
                    equirectLayers[eye] = {XR_TYPE_COMPOSITION_LAYER_EQUIRECT2_KHR};
                    equirectLayers[eye].space = g_ctx.appSpace;
                    equirectLayers[eye].eyeVisibility = eyeVisibilityForIndex(eye);
                    equirectLayers[eye].subImage = buildFullFrameSubImage(g_ctx.eyes[eye]);
                    equirectLayers[eye].pose.orientation.w = 1.0f;
                    equirectLayers[eye].radius = layerConfig.radiusMeters;
                    equirectLayers[eye].centralHorizontalAngle = layerConfig.centralHorizontalAngle;
                    equirectLayers[eye].upperVerticalAngle = layerConfig.upperVerticalAngle;
                    equirectLayers[eye].lowerVerticalAngle = layerConfig.lowerVerticalAngle;
                }
                for (auto &layer : equirectLayers)
                {
                    layers.push_back(reinterpret_cast<const XrCompositionLayerBaseHeader *>(&layer));
                }
            }
            else if (layerConfig.type == LayerType::Cylinder && g_ctx.supportsCylinder)
            {
                const float aspectRatio = layerConfig.heightMeters > 0.0f
                                              ? (layerConfig.widthMeters / layerConfig.heightMeters)
                                              : 1.0f;
                cylinderLayers.resize(kViewCount);
                for (uint32_t eye = 0; eye < kViewCount; ++eye)
                {
                    cylinderLayers[eye] = {XR_TYPE_COMPOSITION_LAYER_CYLINDER_KHR};
                    cylinderLayers[eye].space = g_ctx.appSpace;
                    cylinderLayers[eye].eyeVisibility = eyeVisibilityForIndex(eye);
                    cylinderLayers[eye].subImage = buildFullFrameSubImage(g_ctx.eyes[eye]);
                    cylinderLayers[eye].pose.orientation.w = 1.0f;
                    cylinderLayers[eye].pose.position.z = -layerConfig.distanceMeters;
                    cylinderLayers[eye].radius = layerConfig.radiusMeters;
                    cylinderLayers[eye].centralAngle = layerConfig.centralHorizontalAngle;
                    cylinderLayers[eye].aspectRatio = aspectRatio;
                }
                for (auto &layer : cylinderLayers)
                {
                    layers.push_back(reinterpret_cast<const XrCompositionLayerBaseHeader *>(&layer));
                }
            }
            else
            {
                if (layerConfig.type == LayerType::Equirect2 && !g_ctx.warnedMissingEquirect2)
                {
                    LOGW("Equirect2 layer requested but XR_KHR_composition_layer_equirect2 is unavailable; falling back to cinema quad");
                    g_ctx.warnedMissingEquirect2 = true;
                }
                if (layerConfig.type == LayerType::Cylinder && !g_ctx.warnedMissingCylinder)
                {
                    LOGW("Cylinder layer requested but XR_KHR_composition_layer_cylinder is unavailable; falling back to cinema quad");
                    g_ctx.warnedMissingCylinder = true;
                }

                quadLayers.resize(kViewCount);
                for (uint32_t eye = 0; eye < kViewCount; ++eye)
                {
                    quadLayers[eye] = {XR_TYPE_COMPOSITION_LAYER_QUAD};
                    quadLayers[eye].space = g_ctx.appSpace;
                    quadLayers[eye].eyeVisibility = eyeVisibilityForIndex(eye);
                    quadLayers[eye].subImage = buildFullFrameSubImage(g_ctx.eyes[eye]);
                    quadLayers[eye].pose.orientation.w = 1.0f;
                    quadLayers[eye].pose.position.z = -4.0f;
                    quadLayers[eye].size = {4.0f, 2.25f};
                    layers.push_back(reinterpret_cast<const XrCompositionLayerBaseHeader *>(&quadLayers[eye]));
                }
            }
        }

        // ── HUD staging-buffer drain (render-thread GL upload) ──────────────
        // WHY: nativeUploadHudBitmap cannot call glTexSubImage2D because the EGL context
        // is only current on this render thread. It copies pixel data to hudPendingPixels
        // instead. Here (on the render thread, inside g_ctxMutex) we pick up the pending
        // data and do the real GL upload before the frame is submitted.
        if (g_ctx.hudBitmapPending.exchange(false) &&
            g_ctx.hudSwapchain != XR_NULL_HANDLE &&
            !g_ctx.hudPendingPixels.empty())
        {
            uint32_t uploadImgIdx = 0;
            XrSwapchainImageAcquireInfo uploadAcqInfo{XR_TYPE_SWAPCHAIN_IMAGE_ACQUIRE_INFO};
            XrResult uploadR = xrAcquireSwapchainImage(
                g_ctx.hudSwapchain, &uploadAcqInfo, &uploadImgIdx);
            if (XR_SUCCEEDED(uploadR))
            {
                XrSwapchainImageWaitInfo uploadWait{XR_TYPE_SWAPCHAIN_IMAGE_WAIT_INFO};
                uploadWait.timeout = XR_INFINITE_DURATION;
                uploadR = xrWaitSwapchainImage(g_ctx.hudSwapchain, &uploadWait);
                if (XR_SUCCEEDED(uploadR) &&
                    uploadImgIdx < g_ctx.hudSwapchainImages.size())
                {
                    const uint32_t texId = g_ctx.hudSwapchainImages[uploadImgIdx].imageId;
                    glBindTexture(GL_TEXTURE_2D, texId);
                    glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
                    glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0,
                                    static_cast<GLsizei>(g_ctx.hudSwapchainWidth),
                                    static_cast<GLsizei>(g_ctx.hudSwapchainHeight),
                                    GL_RGBA, GL_UNSIGNED_BYTE,
                                    g_ctx.hudPendingPixels.data());
                    glBindTexture(GL_TEXTURE_2D, 0);
                }
                XrSwapchainImageReleaseInfo uploadRelInfo{XR_TYPE_SWAPCHAIN_IMAGE_RELEASE_INFO};
                xrReleaseSwapchainImage(g_ctx.hudSwapchain, &uploadRelInfo);
                // Signal that a fresh texture was uploaded this interval so the pump
                // below skips the dummy acquire/release cycle.
                if (XR_SUCCEEDED(uploadR))
                {
                    g_ctx.hudFrameUploaded.store(true);
                }
            }
        }

        // ── HUD composition layer (spec_vr-immersive-hud-gl §5.1.1) ────────
        // Head-locked quad rendered on top of the video layer.
        // Runtime demands exactly one acquire/release per swapchain per frame.
        // nativeUploadHudBitmap stages pixels; the drain block above does the real upload.
        // The flag below prevents a second acquire/release in the same frame.
        XrCompositionLayerQuad hudLayer{XR_TYPE_COMPOSITION_LAYER_QUAD};
        const bool hudActive = g_ctx.hudLayerVisible.load() &&
                               g_ctx.hudSwapchain != XR_NULL_HANDLE &&
                               g_ctx.viewSpace != XR_NULL_HANDLE;
        const bool hudUploadedThisInterval = g_ctx.hudFrameUploaded.exchange(false);
        if (hudActive && !hudUploadedThisInterval)
        {
            // No upload happened — pump the swapchain so the index advances and the
            // compositor reuses the previously-uploaded image (or zeros on the very
            // first frame).
            uint32_t hudImgIdx = 0;
            XrSwapchainImageAcquireInfo acqInfo{XR_TYPE_SWAPCHAIN_IMAGE_ACQUIRE_INFO};
            XrResult acqR = xrAcquireSwapchainImage(g_ctx.hudSwapchain, &acqInfo, &hudImgIdx);
            if (XR_SUCCEEDED(acqR))
            {
                XrSwapchainImageWaitInfo waitInfo{XR_TYPE_SWAPCHAIN_IMAGE_WAIT_INFO};
                waitInfo.timeout = XR_INFINITE_DURATION;
                XrResult waitR = xrWaitSwapchainImage(g_ctx.hudSwapchain, &waitInfo);
                if (XR_SUCCEEDED(waitR))
                {
                    XrSwapchainImageReleaseInfo relInfo{XR_TYPE_SWAPCHAIN_IMAGE_RELEASE_INFO};
                    xrReleaseSwapchainImage(g_ctx.hudSwapchain, &relInfo);
                }
            }
        }
        if (hudActive)
        {

            hudLayer.layerFlags = XR_COMPOSITION_LAYER_BLEND_TEXTURE_SOURCE_ALPHA_BIT;
            hudLayer.space = g_ctx.viewSpace;
            hudLayer.eyeVisibility = XR_EYE_VISIBILITY_BOTH;
            hudLayer.subImage.swapchain = g_ctx.hudSwapchain;
            hudLayer.subImage.imageRect.offset = {0, 0};
            hudLayer.subImage.imageRect.extent = {
                static_cast<int32_t>(g_ctx.hudSwapchainWidth),
                static_cast<int32_t>(g_ctx.hudSwapchainHeight),
            };
            // WHY: arraySize=1 for this HUD swapchain, so imageArrayIndex must stay 0.
            // The runtime picks the currently released swapchain image internally.
            hudLayer.subImage.imageArrayIndex = 0;
            // Pose: head-locked, 1.5 m forward, ~20° below gaze. Half-angle for the
            // X-axis quaternion gives downward tilt: qx = sin(-10°), qw = cos(-10°).
            // sin(-10°) = -0.17365, cos(-10°) = 0.98481.
            hudLayer.pose.orientation = {-0.17365f, 0.0f, 0.0f, 0.98481f};
            hudLayer.pose.position = {0.0f, 0.0f, -1.5f};
            // Size: 1.0 m wide × 0.3 m tall (strategic §6.4 start-default).
            hudLayer.size = {1.0f, 0.3f};
            layers.push_back(reinterpret_cast<const XrCompositionLayerBaseHeader *>(&hudLayer));
        }

        // ── Panel staging-buffer drain (render-thread GL upload) ────────────
        if (g_ctx.panelBitmapPending.exchange(false) &&
            g_ctx.panelSwapchain != XR_NULL_HANDLE &&
            !g_ctx.panelPendingPixels.empty())
        {
            uint32_t uploadImgIdx = 0;
            XrSwapchainImageAcquireInfo uploadAcqInfo{XR_TYPE_SWAPCHAIN_IMAGE_ACQUIRE_INFO};
            XrResult uploadR = xrAcquireSwapchainImage(
                g_ctx.panelSwapchain, &uploadAcqInfo, &uploadImgIdx);
            if (XR_SUCCEEDED(uploadR))
            {
                XrSwapchainImageWaitInfo uploadWait{XR_TYPE_SWAPCHAIN_IMAGE_WAIT_INFO};
                uploadWait.timeout = XR_INFINITE_DURATION;
                uploadR = xrWaitSwapchainImage(g_ctx.panelSwapchain, &uploadWait);
                if (XR_SUCCEEDED(uploadR) &&
                    uploadImgIdx < g_ctx.panelSwapchainImages.size())
                {
                    const uint32_t texId = g_ctx.panelSwapchainImages[uploadImgIdx].imageId;
                    glBindTexture(GL_TEXTURE_2D, texId);
                    glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
                    glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0,
                                    static_cast<GLsizei>(g_ctx.panelSwapchainWidth),
                                    static_cast<GLsizei>(g_ctx.panelSwapchainHeight),
                                    GL_RGBA, GL_UNSIGNED_BYTE,
                                    g_ctx.panelPendingPixels.data());
                    glBindTexture(GL_TEXTURE_2D, 0);
                }
                XrSwapchainImageReleaseInfo uploadRelInfo{XR_TYPE_SWAPCHAIN_IMAGE_RELEASE_INFO};
                xrReleaseSwapchainImage(g_ctx.panelSwapchain, &uploadRelInfo);
                if (XR_SUCCEEDED(uploadR))
                    g_ctx.panelFrameUploaded.store(true);
            }
        }

        // ── Interactive panel composition layer ──────────────────────────────
        XrCompositionLayerQuad panelLayer{XR_TYPE_COMPOSITION_LAYER_QUAD};
        const bool panelActive = g_ctx.panelLayerVisible.load() &&
                                 g_ctx.panelSwapchain != XR_NULL_HANDLE &&
                                 g_ctx.viewSpace != XR_NULL_HANDLE;
        const bool panelUploadedThisInterval = g_ctx.panelFrameUploaded.exchange(false);
        if (panelActive && !panelUploadedThisInterval)
        {
            uint32_t panImgIdx = 0;
            XrSwapchainImageAcquireInfo acqInfo{XR_TYPE_SWAPCHAIN_IMAGE_ACQUIRE_INFO};
            if (XR_SUCCEEDED(xrAcquireSwapchainImage(g_ctx.panelSwapchain, &acqInfo, &panImgIdx)))
            {
                XrSwapchainImageWaitInfo waitInfo{XR_TYPE_SWAPCHAIN_IMAGE_WAIT_INFO};
                waitInfo.timeout = XR_INFINITE_DURATION;
                if (XR_SUCCEEDED(xrWaitSwapchainImage(g_ctx.panelSwapchain, &waitInfo)))
                {
                    XrSwapchainImageReleaseInfo relInfo{XR_TYPE_SWAPCHAIN_IMAGE_RELEASE_INFO};
                    xrReleaseSwapchainImage(g_ctx.panelSwapchain, &relInfo);
                }
            }
        }
        if (panelActive)
        {
            panelLayer.layerFlags = XR_COMPOSITION_LAYER_BLEND_TEXTURE_SOURCE_ALPHA_BIT;
            panelLayer.space = g_ctx.viewSpace;
            panelLayer.eyeVisibility = XR_EYE_VISIBILITY_BOTH;
            panelLayer.subImage.swapchain = g_ctx.panelSwapchain;
            panelLayer.subImage.imageRect.offset = {0, 0};
            panelLayer.subImage.imageRect.extent = {
                static_cast<int32_t>(g_ctx.panelSwapchainWidth),
                static_cast<int32_t>(g_ctx.panelSwapchainHeight),
            };
            panelLayer.subImage.imageArrayIndex = 0;
            // Same pose as HUD (head-locked, 1.5 m forward, 10° downward tilt).
            // Wider aspect to accommodate the multi-row controls layout.
            panelLayer.pose.orientation = {-0.17365f, 0.0f, 0.0f, 0.98481f};
            panelLayer.pose.position = {0.0f, -0.35f, -1.5f};
            // 1.0 m × 0.5 m — 2:1 aspect for 1024×512 swapchain.
            panelLayer.size = {1.0f, 0.5f};
            layers.push_back(reinterpret_cast<const XrCompositionLayerBaseHeader *>(&panelLayer));
        }

        XrFrameEndInfo endInfo{XR_TYPE_FRAME_END_INFO};
        endInfo.displayTime = frameState.predictedDisplayTime;
        endInfo.environmentBlendMode = XR_ENVIRONMENT_BLEND_MODE_OPAQUE;
        endInfo.layerCount = static_cast<uint32_t>(layers.size());
        endInfo.layers = layers.data();

        // Log every 300 frames: how many layers we're submitting and whether frame was valid.
        if (s_frameCount % 300 == 1)
        {
            LOGI("xrEndFrame #%llu: layerCount=%u frameValid=%d",
                 static_cast<unsigned long long>(s_frameCount),
                 endInfo.layerCount,
                 static_cast<int>(frameValid));
        }

        XrResult endResult = xrEndFrame(g_ctx.session, &endInfo);
        if (XR_FAILED(endResult))
        {
            LOGE("xrEndFrame FAILED: %d  layerCount=%u  frame=#%llu",
                 static_cast<int>(endResult),
                 endInfo.layerCount,
                 static_cast<unsigned long long>(s_frameCount));
        }
    }

    void releaseCallback(JNIEnv *env)
    {
        if (g_ctx.callbackRef)
        {
            env->DeleteGlobalRef(g_ctx.callbackRef);
            g_ctx.callbackRef = nullptr;
        }
        g_ctx.onRenderEyeMethod = nullptr;
        // Release the input callback alongside the render callback so JNI global refs
        // do not leak across session teardown.
        releaseInputCallback(env);
    }

    void destroyAll()
    {
        // Destroy hand trackers first — they hold references to the session handle.
        destroyHandTracking();
        // Destroy OpenXR input handles before swapchains/session so xrDestroyActionSet
        // runs while the session/instance handles are still valid.
        destroyInputHandles();
        // HUD + panel composition layers. Destroy before the eye swapchains so the
        // runtime sees layers torn down in reverse-creation order.
        destroyHudSwapchainImpl();
        destroyPanelSwapchainImpl();
        for (auto &eye : g_ctx.eyes)
        {
            for (auto &img : eye.images)
            {
                if (img.fbo)
                {
                    GLuint f = img.fbo;
                    glDeleteFramebuffers(1, &f);
                    img.fbo = 0;
                }
            }
            eye.images.clear();
            if (eye.handle)
            {
                xrDestroySwapchain(eye.handle);
                eye.handle = XR_NULL_HANDLE;
            }
        }
        if (g_ctx.appSpace != XR_NULL_HANDLE)
        {
            xrDestroySpace(g_ctx.appSpace);
            g_ctx.appSpace = XR_NULL_HANDLE;
        }
        if (g_ctx.viewSpace != XR_NULL_HANDLE)
        {
            xrDestroySpace(g_ctx.viewSpace);
            g_ctx.viewSpace = XR_NULL_HANDLE;
        }
        if (g_ctx.session != XR_NULL_HANDLE)
        {
            xrDestroySession(g_ctx.session);
            g_ctx.session = XR_NULL_HANDLE;
        }
        if (g_ctx.instance != XR_NULL_HANDLE)
        {
            xrDestroyInstance(g_ctx.instance);
            g_ctx.instance = XR_NULL_HANDLE;
        }
        g_ctx.supportsEquirect2 = false;
        g_ctx.supportsCylinder = false;
        g_ctx.warnedMissingEquirect2 = false;
        g_ctx.warnedMissingCylinder = false;
        g_ctx.supportsHandTracking = false;
        g_ctx.supportsHandAim = false;
        g_ctx.supportsMicrogestures = false;
        g_ctx.layerConfig = LayerConfig{};
        g_ctx.stereoSnapshot.requested.store(false);
        g_ctx.stereoSnapshot.ready.store(false);
        g_ctx.stereoSnapshot.width = 0;
        g_ctx.stereoSnapshot.height = 0;
        for (auto &eyePixels : g_ctx.stereoSnapshot.eyePixels)
        {
            eyePixels.clear();
        }
        g_ctx.sessionRunning = false;
        g_ctx.exitRequested = false;
        g_ctx.sessionState = XR_SESSION_STATE_UNKNOWN;
    }

} // namespace

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
    if (!enumerateAndCreateInstance(env, activity))
    {
        LOGE("nativeInitialize: enumerateAndCreateInstance FAILED");
        releaseCallback(env);
        return JNI_FALSE;
    }
    LOGI("nativeInitialize: calling createSessionAndSwapchains");
    if (!createSessionAndSwapchains())
    {
        LOGE("nativeInitialize: createSessionAndSwapchains FAILED — destroying partial state");
        destroyAll();
        releaseCallback(env);
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
    if (!pollEvents())
        return;
    if (g_ctx.sessionRunning)
    {
        renderFrame(env);
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
    destroyAll();
    releaseCallback(env);
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
    std::vector<std::string> drained;
    {
        std::lock_guard<std::mutex> lock(g_logBufferMutex);
        drained.swap(g_logBuffer);
    }
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
    if (!g_ctx.sessionRunning)
    {
        g_ctx.panelSwapchainWidth = static_cast<uint32_t>(width);
        g_ctx.panelSwapchainHeight = static_cast<uint32_t>(height);
        LOGI("nativeCreatePanelSwapchain: %dx%d stored — session not yet up", width, height);
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
    if (bitmap == nullptr) return JNI_FALSE;
    if (g_ctx.panelSwapchain == XR_NULL_HANDLE || !g_ctx.sessionRunning) return JNI_FALSE;

    AndroidBitmapInfo info{};
    if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS)
        return JNI_FALSE;
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) return JNI_FALSE;
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
