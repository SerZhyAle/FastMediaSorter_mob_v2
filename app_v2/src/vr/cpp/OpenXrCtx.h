#pragma once

#include <jni.h>
#include <android/native_window.h>

#include <EGL/egl.h>
#include <GLES3/gl3.h>

#include <openxr/openxr.h>
#include <openxr/openxr_platform.h>

#include <atomic>
#include <cstdint>
#include <vector>

// XR_EXT_hand_tracking is part of mainline openxr.h since 1.0.21; XR_META_* are
// vendor-specific and are not guaranteed to exist in every shipped header set.
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
    XR_HAND_RIGHT_EXT = 2
} XrHandEXT;
typedef enum XrHandJointSetEXT
{
    XR_HAND_JOINT_SET_DEFAULT_EXT = 0
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
#endif

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
#endif

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
#endif

typedef XrResult(XRAPI_PTR *PFN_xrCreateHandTrackerEXT_LOCAL)(
    XrSession, const XrHandTrackerCreateInfoEXT *, XrHandTrackerEXT *);
typedef XrResult(XRAPI_PTR *PFN_xrDestroyHandTrackerEXT_LOCAL)(XrHandTrackerEXT);
typedef XrResult(XRAPI_PTR *PFN_xrLocateHandJointsEXT_LOCAL)(
    XrHandTrackerEXT, const XrHandJointsLocateInfoEXT *, XrHandJointLocationsEXT *);

namespace xrnative
{

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
        uint32_t imageId = 0;
        uint32_t fbo = 0;
    };

    struct EyeSwapchain
    {
        XrSwapchain handle = XR_NULL_HANDLE;
        uint32_t width = 0;
        uint32_t height = 0;
        std::vector<SwapchainImage> images;
    };

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
        bool prevA = false, prevB = false, prevX = false, prevY = false;
        bool prevMenu = false, prevThumbL = false, prevThumbR = false;
        float prevGripL = 0.0f, prevGripR = 0.0f;
        bool stickLEdgeXActive = false, stickLEdgeYActive = false;
        bool stickREdgeXActive = false, stickREdgeYActive = false;
        int64_t yPressTimeNs = 0;
        bool yLongPressEmitted = false;
        int64_t bothGripsPressTimeNs = 0;
        bool bothGripsResetEmitted = false;
        jobject inputCallbackRef = nullptr;
        jmethodID onInputEventMethod = nullptr;
        jmethodID onPointerMoveMethod = nullptr;
        jmethodID onControllerPointerMoveMethod = nullptr;
        XrAction aimPoseL = XR_NULL_HANDLE;
        XrAction aimPoseR = XR_NULL_HANDLE;
        XrSpace aimSpaceL = XR_NULL_HANDLE;
        XrSpace aimSpaceR = XR_NULL_HANDLE;
        XrAction triggerL = XR_NULL_HANDLE;
        XrAction triggerR = XR_NULL_HANDLE;
        int64_t lastControllerEventNs = 0;
        bool initialized = false;
    };

    struct HandSystem
    {
        PFN_xrCreateHandTrackerEXT_LOCAL pfnCreate = nullptr;
        PFN_xrDestroyHandTrackerEXT_LOCAL pfnDestroy = nullptr;
        PFN_xrLocateHandJointsEXT_LOCAL pfnLocate = nullptr;
        XrHandTrackerEXT trackerL = XR_NULL_HANDLE;
        XrHandTrackerEXT trackerR = XR_NULL_HANDLE;
        XrHandJointLocationEXT jointsL[XR_HAND_JOINT_COUNT_EXT];
        XrHandJointLocationEXT jointsR[XR_HAND_JOINT_COUNT_EXT];
        bool isPinchingL = false;
        bool isPinchingR = false;
        bool suppressClickReleaseL = false;
        bool suppressClickReleaseR = false;
        int64_t lastPinchDownNs = 0;
        bool aimFrozenL = false, aimFrozenR = false;
        float frozenAimXL = 0.0f, frozenAimYL = 0.0f;
        float frozenAimXR = 0.0f, frozenAimYR = 0.0f;
        XrHandMicrogestureFlagsMETA prevGesturesL = 0;
        XrHandMicrogestureFlagsMETA prevGesturesR = 0;
        bool initialized = false;
    };

    struct StereoSnapshot
    {
        std::atomic<bool> requested{false};
        std::atomic<bool> ready{false};
        uint32_t width = 0;
        uint32_t height = 0;
        std::vector<jint> eyePixels[kViewCount];
    };

    struct RayState
    {
        bool active = false;
        float originX = 0.0f, originY = 0.0f, originZ = 0.0f;
        float endX = 0.0f, endY = 0.0f, endZ = 0.0f;
        bool hasCursor = false;
        float cursorX = 0.0f, cursorY = 0.0f, cursorZ = 0.0f;
    };

    struct RayRenderResources
    {
        bool ready = false;
        GLuint program = 0;
        GLuint vbo = 0;
        GLuint vao = 0;
        GLint uMvpLoc = -1;
        GLint uColorLoc = -1;
        GLint aPosLoc = -1;
    };

    struct XrCtx
    {
        JavaVM *vm = nullptr;
        jobject callbackRef = nullptr;
        jmethodID onRenderEyeMethod = nullptr;
        XrInstance instance = XR_NULL_HANDLE;
        XrSystemId systemId = XR_NULL_SYSTEM_ID;
        XrSession session = XR_NULL_HANDLE;
        XrSpace appSpace = XR_NULL_HANDLE;
        XrSpace viewSpace = XR_NULL_HANDLE;
        XrSessionState sessionState = XR_SESSION_STATE_UNKNOWN;
        bool sessionRunning = false;
        std::atomic<bool> exitRequested{false};
        XrTime lastPredictedDisplayTime = 0;
        EGLDisplay eglDisplay = EGL_NO_DISPLAY;
        EGLContext eglContext = EGL_NO_CONTEXT;
        EGLConfig eglConfig = nullptr;
        bool supportsEquirect2 = false;
        bool supportsCylinder = false;
        bool warnedMissingEquirect2 = false;
        bool warnedMissingCylinder = false;
        bool supportsHandTracking = false;
        bool supportsHandAim = false;
        bool supportsMicrogestures = false;
        LayerConfig layerConfig{};
        EyeSwapchain eyes[kViewCount];
        InputSystem input{};
        std::atomic<bool> controllerRayEnabled{true};
        RayState rayState[2]{};
        RayRenderResources rayResources{};
        HandSystem hands{};
        StereoSnapshot stereoSnapshot{};
        XrSwapchain hudSwapchain = XR_NULL_HANDLE;
        uint32_t hudSwapchainWidth = 0;
        uint32_t hudSwapchainHeight = 0;
        std::atomic<bool> hudLayerVisible{false};
        std::vector<SwapchainImage> hudSwapchainImages;
        std::atomic<bool> hudFrameUploaded{false};
        std::vector<uint8_t> hudPendingPixels;
        std::atomic<bool> hudBitmapPending{false};
        XrSwapchain panelSwapchain = XR_NULL_HANDLE;
        uint32_t panelSwapchainWidth = 0;
        uint32_t panelSwapchainHeight = 0;
        std::atomic<bool> panelLayerVisible{false};
        std::vector<SwapchainImage> panelSwapchainImages;
        std::atomic<bool> panelFrameUploaded{false};
        std::vector<uint8_t> panelPendingPixels;
        std::atomic<bool> panelBitmapPending{false};
    };

} // namespace xrnative