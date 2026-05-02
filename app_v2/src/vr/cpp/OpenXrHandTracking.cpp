#include "OpenXrHandTracking.h"

#include "OpenXrInput.h"
#include "OpenXrLog.h"

#include <ctime>

using namespace xrnative;

#define LOGI(...) nativeLogEmit(ANDROID_LOG_INFO, __VA_ARGS__)
#define LOGW(...) nativeLogEmit(ANDROID_LOG_WARN, __VA_ARGS__)
#define LOGE(...) nativeLogEmit(ANDROID_LOG_ERROR, __VA_ARGS__)
#define LOGD(...) nativeLogEmit(ANDROID_LOG_DEBUG, __VA_ARGS__)

namespace
{

    // Modality switch: hand tracking is suppressed while controllers emit events
    // within this window. 2 s matches spec §3.3 ("inactive for > 2.0 seconds").
    constexpr int64_t kControllerIdleSwitchNs = 2'000'000'000LL;

    // Pinch hysteresis thresholds (spec §5.1 / §6).
    constexpr float kPinchPressThreshold = 0.9f;
    constexpr float kPinchReleaseThreshold = 0.6f;
    constexpr float kPinchAimFreezeThreshold = 0.5f; // Freeze raycast once past this.
    constexpr int64_t kDoublePinchWindowNs = 300'000'000LL;

    int64_t monotonicNowNs()
    {
        struct timespec ts;
        clock_gettime(CLOCK_MONOTONIC, &ts);
        return static_cast<int64_t>(ts.tv_sec) * 1'000'000'000LL + static_cast<int64_t>(ts.tv_nsec);
    }

} // namespace

bool xrnative::initHandTracking(XrCtx &ctx)
{
    auto &h = ctx.hands;
    if (h.initialized)
        return true;
    if (!ctx.supportsHandTracking)
    {
        LOGI("initHandTracking: XR_EXT_hand_tracking unsupported on this runtime — Layer E disabled");
        return false;
    }
    if (ctx.instance == XR_NULL_HANDLE || ctx.session == XR_NULL_HANDLE)
    {
        LOGE("initHandTracking: instance/session missing");
        return false;
    }

    // Resolve function pointers lazily — if any entry is missing we treat hand
    // tracking as absent rather than aborting session creation.
    auto resolveProc = [&](const char *name) -> PFN_xrVoidFunction
    {
        PFN_xrVoidFunction raw = nullptr;
        XrResult r = xrGetInstanceProcAddr(ctx.instance, name, &raw);
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
        XrResult r = h.pfnCreate(ctx.session, &ci, &outTracker);
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
         static_cast<int>(ctx.supportsHandAim),
         static_cast<int>(ctx.supportsMicrogestures));
    return true;
}

void xrnative::destroyHandTracking(XrCtx &ctx)
{
    auto &h = ctx.hands;
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

// Per-frame hand polling. Runs AFTER syncInputActions so the controller modality
// gate sees the freshest controller edge timestamp.
void xrnative::syncHandTracking(XrCtx &ctx, JNIEnv *env)
{
    auto &h = ctx.hands;
    auto &io = ctx.input;
    if (!h.initialized || !ctx.sessionRunning)
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
            emitInputEvent(ctx, env, XR_EVT_POINTER_CLICK_UP, 0, 0.0f, XR_SRC_HAND);
        if (h.isPinchingR && !h.suppressClickReleaseR)
            emitInputEvent(ctx, env, XR_EVT_POINTER_CLICK_UP, 1, 0.0f, XR_SRC_HAND);
        if (h.isPinchingL || h.isPinchingR || h.aimFrozenL || h.aimFrozenR)
        {
            emitPointerMove(ctx, env, 0, 2.0f, 2.0f);
            emitPointerMove(ctx, env, 1, 2.0f, 2.0f);
        }
        h.isPinchingL = h.isPinchingR = false;
        h.suppressClickReleaseL = h.suppressClickReleaseR = false;
        h.aimFrozenL = h.aimFrozenR = false;
        h.lastPinchDownNs = 0;
        return;
    }

    if (ctx.appSpace == XR_NULL_HANDLE)
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
        if (ctx.supportsHandAim)
        {
            aimState.next = nextChain;
            nextChain = &aimState;
        }
        if (ctx.supportsMicrogestures)
        {
            mgState.next = nextChain;
            nextChain = &mgState;
        }
        locations.next = nextChain;

        XrHandJointsLocateInfoEXT locateInfo{};
        locateInfo.type = XR_TYPE_HAND_JOINTS_LOCATE_INFO_EXT;
        locateInfo.baseSpace = ctx.appSpace;
        locateInfo.time = predictedTime;

        XrResult r = h.pfnLocate(tracker, &locateInfo, &locations);
        if (XR_FAILED(r) || !locations.isActive)
        {
            if (pinchState && !suppressClickRelease)
            {
                emitInputEvent(ctx, env, XR_EVT_POINTER_CLICK_UP, handIdx, 0.0f, XR_SRC_HAND);
            }
            pinchState = false;
            suppressClickRelease = false;
            frozen = false;
            prevGestures = 0;
            emitPointerMove(ctx, env, handIdx, 2.0f, 2.0f);
            return;
        }

        // Suppress hand input while the user is performing the Meta system gesture.
        // COMPUTED must be set before any other status bit can be trusted.
        const bool aimComputed = ctx.supportsHandAim &&
                                 (aimState.status & XR_HAND_TRACKING_AIM_COMPUTED_BIT_FB) != 0;
        if (aimComputed &&
            (aimState.status & XR_HAND_TRACKING_AIM_SYSTEM_GESTURE_BIT_FB) != 0)
        {
            if (pinchState && !suppressClickRelease)
            {
                emitInputEvent(ctx, env, XR_EVT_POINTER_CLICK_UP, handIdx, 0.0f, XR_SRC_HAND);
            }
            pinchState = false;
            suppressClickRelease = false;
            frozen = false;
            emitPointerMove(ctx, env, handIdx, 2.0f, 2.0f);
            return;
        }

        float ndcX = 2.0f, ndcY = 2.0f;
        if (aimComputed && (aimState.status & XR_HAND_TRACKING_AIM_VALID_BIT_FB))
        {
            const XrPosef &p = aimState.aimPose;
            const float qx = p.orientation.x;
            const float qy = p.orientation.y;
            const float qz = p.orientation.z;
            const float qw = p.orientation.w;
            const float fx = -2.0f * (qx * qz + qw * qy);
            const float fy = -2.0f * (qy * qz - qw * qx);
            const float fz = -(1.0f - 2.0f * (qx * qx + qy * qy));

            const float planeDist = ctx.layerConfig.distanceMeters;
            const float halfW = ctx.layerConfig.widthMeters * 0.5f;
            const float halfH = ctx.layerConfig.heightMeters * 0.5f;
            if (fz < -1e-4f)
            {
                const float t = (-planeDist - p.position.z) / fz;
                if (t > 0.0f)
                {
                    const float ix = p.position.x + t * fx;
                    const float iy = p.position.y + t * fy;
                    if (halfW > 1e-4f && halfH > 1e-4f)
                    {
                        ndcX = ix / halfW;
                        ndcY = iy / halfH;
                    }
                }
            }
        }

        // Aim-freeze: once pinch strength crosses mid-threshold, lock the NDC XY.
        const float pinchStrength = aimComputed ? aimState.pinchStrengthIndex : 0.0f;
        if (pinchStrength >= kPinchAimFreezeThreshold)
        {
            if (!frozen)
            {
                frozen = true;
                frozenX = ndcX;
                frozenY = ndcY;
            }
            ndcX = frozenX;
            ndcY = frozenY;
        }
        else
        {
            frozen = false;
        }

        emitPointerMove(ctx, env, handIdx, ndcX, ndcY);

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
                emitInputEvent(ctx, env, XR_EVT_DOUBLE_PINCH, handIdx, pinchStrength, XR_SRC_HAND);
            }
            else
            {
                suppressClickRelease = false;
                h.lastPinchDownNs = nowNs;
                emitInputEvent(ctx, env, XR_EVT_POINTER_CLICK_DOWN, handIdx, pinchStrength, XR_SRC_HAND);
            }
        }
        else if (pinchState && pinchStrength <= kPinchReleaseThreshold)
        {
            pinchState = false;
            if (!suppressClickRelease)
                emitInputEvent(ctx, env, XR_EVT_POINTER_CLICK_UP, handIdx, pinchStrength, XR_SRC_HAND);
            suppressClickRelease = false;
        }

        // Microgestures — emit one event per bit that became set this sync.
        if (ctx.supportsMicrogestures && mgState.gesturesSinceLastSync != prevGestures)
        {
            XrHandMicrogestureFlagsMETA rising = mgState.gesturesSinceLastSync & ~prevGestures;
            if (rising & XR_HAND_MICROGESTURE_SWIPE_LEFT_META)
                emitInputEvent(ctx, env, XR_EVT_SWIPE_LEFT, handIdx, 0.0f, XR_SRC_HAND);
            if (rising & XR_HAND_MICROGESTURE_SWIPE_RIGHT_META)
                emitInputEvent(ctx, env, XR_EVT_SWIPE_RIGHT, handIdx, 0.0f, XR_SRC_HAND);
            if (rising & XR_HAND_MICROGESTURE_SWIPE_UP_META)
                emitInputEvent(ctx, env, XR_EVT_SWIPE_UP, handIdx, 0.0f, XR_SRC_HAND);
            if (rising & XR_HAND_MICROGESTURE_SWIPE_DOWN_META)
                emitInputEvent(ctx, env, XR_EVT_SWIPE_DOWN, handIdx, 0.0f, XR_SRC_HAND);
            prevGestures = mgState.gesturesSinceLastSync;
        }
    };

    processHand(h.trackerL, 0, h.jointsL, h.isPinchingL, h.suppressClickReleaseL, h.aimFrozenL, h.frozenAimXL, h.frozenAimYL, h.prevGesturesL);
    processHand(h.trackerR, 1, h.jointsR, h.isPinchingR, h.suppressClickReleaseR, h.aimFrozenR, h.frozenAimXR, h.frozenAimYR, h.prevGesturesR);
}