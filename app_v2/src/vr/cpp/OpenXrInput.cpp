#include "OpenXrInput.h"

#include "OpenXrLog.h"

#include <cmath>
#include <cstring>
#include <ctime>
#include <vector>

using namespace xrnative;

#define LOGI(...) nativeLogEmit(ANDROID_LOG_INFO, __VA_ARGS__)
#define LOGW(...) nativeLogEmit(ANDROID_LOG_WARN, __VA_ARGS__)
#define LOGE(...) nativeLogEmit(ANDROID_LOG_ERROR, __VA_ARGS__)
#define LOGD(...) nativeLogEmit(ANDROID_LOG_DEBUG, __VA_ARGS__)

namespace
{

    // Analog thresholds tuned per UX spec §4.1.1 / §4.1.2.
    constexpr float kStickThreshold = 0.7f;
    constexpr float kStickReturnZone = 0.15f; // Hysteresis: must return near zero before re-trigger.
    constexpr float kGripPressThresh = 0.7f;
    constexpr float kGripReleaseThresh = 0.4f;
    constexpr float kGripDeltaMin = 0.01f;
    constexpr int64_t kYLongPressNs = 800'000'000LL;      // 0.8 s
    constexpr int64_t kBothGripsHoldNs = 1'000'000'000LL; // 1.0 s

    int64_t monotonicNowNs()
    {
        struct timespec ts;
        clock_gettime(CLOCK_MONOTONIC, &ts);
        return static_cast<int64_t>(ts.tv_sec) * 1'000'000'000LL + static_cast<int64_t>(ts.tv_nsec);
    }

} // namespace

void xrnative::emitInputEvent(XrCtx &ctx, JNIEnv *env, int32_t type, int32_t hand, float value, int32_t source)
{
    auto &io = ctx.input;
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

void xrnative::emitPointerMove(XrCtx &ctx, JNIEnv *env, int32_t hand, float ndcX, float ndcY)
{
    auto &io = ctx.input;
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
bool xrnative::setupActionSet(XrCtx &ctx)
{
    auto &io = ctx.input;
    if (io.initialized)
    {
        LOGW("setupActionSet: already initialized, skipping");
        return true;
    }
    if (ctx.instance == XR_NULL_HANDLE || ctx.session == XR_NULL_HANDLE)
    {
        LOGE("setupActionSet: instance/session missing");
        return false;
    }

    XrActionSetCreateInfo asci{XR_TYPE_ACTION_SET_CREATE_INFO};
    std::strncpy(asci.actionSetName, "playback", XR_MAX_ACTION_SET_NAME_SIZE - 1);
    std::strncpy(asci.localizedActionSetName, "Playback controls", XR_MAX_LOCALIZED_ACTION_SET_NAME_SIZE - 1);
    asci.priority = 0;
    XrResult r = xrCreateActionSet(ctx.instance, &asci, &io.actionSet);
    if (XR_FAILED(r))
    {
        LOGE("setupActionSet: xrCreateActionSet failed: %d", static_cast<int>(r));
        return false;
    }

    XrPath handLeft = XR_NULL_PATH, handRight = XR_NULL_PATH;
    xrStringToPath(ctx.instance, "/user/hand/left", &handLeft);
    xrStringToPath(ctx.instance, "/user/hand/right", &handRight);

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
    xrStringToPath(ctx.instance, "/user/hand/left/input/x/click", &pLeftX);
    xrStringToPath(ctx.instance, "/user/hand/left/input/y/click", &pLeftY);
    xrStringToPath(ctx.instance, "/user/hand/left/input/menu/click", &pLeftMenu);
    xrStringToPath(ctx.instance, "/user/hand/left/input/thumbstick", &pLeftThumb);
    xrStringToPath(ctx.instance, "/user/hand/left/input/thumbstick/click", &pLeftThumbClk);
    xrStringToPath(ctx.instance, "/user/hand/left/input/squeeze/value", &pLeftGrip);
    xrStringToPath(ctx.instance, "/user/hand/left/output/haptic", &pLeftHaptic);
    xrStringToPath(ctx.instance, "/user/hand/right/input/a/click", &pRightA);
    xrStringToPath(ctx.instance, "/user/hand/right/input/b/click", &pRightB);
    xrStringToPath(ctx.instance, "/user/hand/right/input/thumbstick", &pRightThumb);
    xrStringToPath(ctx.instance, "/user/hand/right/input/thumbstick/click", &pRightThumbClk);
    xrStringToPath(ctx.instance, "/user/hand/right/input/squeeze/value", &pRightGrip);
    xrStringToPath(ctx.instance, "/user/hand/right/output/haptic", &pRightHaptic);
    xrStringToPath(ctx.instance, "/user/hand/left/input/aim/pose", &pLeftAim);
    xrStringToPath(ctx.instance, "/user/hand/right/input/aim/pose", &pRightAim);
    xrStringToPath(ctx.instance, "/user/hand/left/input/trigger/value", &pLeftTrigger);
    xrStringToPath(ctx.instance, "/user/hand/right/input/trigger/value", &pRightTrigger);

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
        xrStringToPath(ctx.instance, profileStr, &profilePath);
        XrInteractionProfileSuggestedBinding sugg{XR_TYPE_INTERACTION_PROFILE_SUGGESTED_BINDING};
        sugg.interactionProfile = profilePath;
        sugg.countSuggestedBindings = static_cast<uint32_t>(bindings.size());
        sugg.suggestedBindings = bindings.data();
        XrResult sr = xrSuggestInteractionProfileBindings(ctx.instance, &sugg);
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
    suggestProfile("/interaction_profiles/oculus/touch_controller");
    suggestProfile("/interaction_profiles/oculus/touch_pro_controller");
    suggestProfile("/interaction_profiles/meta/touch_plus_controller");

    XrSessionActionSetsAttachInfo asai{XR_TYPE_SESSION_ACTION_SETS_ATTACH_INFO};
    asai.countActionSets = 1;
    asai.actionSets = &io.actionSet;
    XrResult ar = xrAttachSessionActionSets(ctx.session, &asai);
    if (XR_FAILED(ar))
    {
        LOGE("setupActionSet: xrAttachSessionActionSets failed: %d", static_cast<int>(ar));
        return false;
    }

    io.initialized = true;
    // Create aim pose action spaces for controller ray NDC projection.
    createControllerAimSpaces(ctx);
    LOGI("setupActionSet: input system ready (17 actions, 3 profiles attached)");
    return true;
}

// Create XrActionSpace for each hand's aim pose action.
// Called after xrAttachSessionActionSets when session + action set are both ready.
bool xrnative::createControllerAimSpaces(XrCtx &ctx)
{
    auto &io = ctx.input;
    auto createSpace = [&](XrAction act, XrSpace &space) -> bool
    {
        if (act == XR_NULL_HANDLE)
            return false;
        XrActionSpaceCreateInfo asci{XR_TYPE_ACTION_SPACE_CREATE_INFO};
        asci.action = act;
        asci.subactionPath = XR_NULL_PATH;
        asci.poseInActionSpace.orientation = {0.0f, 0.0f, 0.0f, 1.0f};
        asci.poseInActionSpace.position = {0.0f, 0.0f, 0.0f};
        XrResult r = xrCreateActionSpace(ctx.session, &asci, &space);
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
// Called from renderFrame(); runs on xr-render-thread.
void xrnative::syncInputActions(XrCtx &ctx, JNIEnv *env)
{
    auto &io = ctx.input;
    if (!io.initialized || io.actionSet == XR_NULL_HANDLE || !ctx.sessionRunning)
        return;

    XrActiveActionSet activeSet{};
    activeSet.actionSet = io.actionSet;
    activeSet.subactionPath = XR_NULL_PATH;
    XrActionsSyncInfo sync{XR_TYPE_ACTIONS_SYNC_INFO};
    sync.countActiveActionSets = 1;
    sync.activeActionSets = &activeSet;
    if (XR_FAILED(xrSyncActions(ctx.session, &sync)))
        return;

    auto readBool = [&](XrAction act) -> bool
    {
        if (act == XR_NULL_HANDLE)
            return false;
        XrActionStateGetInfo gi{XR_TYPE_ACTION_STATE_GET_INFO};
        gi.action = act;
        XrActionStateBoolean st{XR_TYPE_ACTION_STATE_BOOLEAN};
        if (XR_FAILED(xrGetActionStateBoolean(ctx.session, &gi, &st)))
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
        if (XR_FAILED(xrGetActionStateFloat(ctx.session, &gi, &st)))
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
        if (XR_FAILED(xrGetActionStateVector2f(ctx.session, &gi, &st)))
            return zero;
        return st.isActive ? st.currentState : zero;
    };

    // ── Boolean rising-edge detection ───────────────────────────────────
    bool a = readBool(io.aPauseToggle);
    if (a && !io.prevA)
        emitInputEvent(ctx, env, XR_EVT_PAUSE_TOGGLE, 1, 0.0f, XR_SRC_CONTROLLER);
    io.prevA = a;

    bool b = readBool(io.bExit);
    if (b && !io.prevB)
        emitInputEvent(ctx, env, XR_EVT_EXIT, 1, 0.0f, XR_SRC_CONTROLLER);
    io.prevB = b;

    bool x = readBool(io.xExit);
    if (x && !io.prevX)
        emitInputEvent(ctx, env, XR_EVT_EXIT, 0, 0.0f, XR_SRC_CONTROLLER);
    io.prevX = x;

    bool menu = readBool(io.menuCtrl);
    if (menu && !io.prevMenu)
        emitInputEvent(ctx, env, XR_EVT_MENU, 0, 0.0f, XR_SRC_CONTROLLER);
    io.prevMenu = menu;

    bool tL = readBool(io.thumbClickL);
    if (tL && !io.prevThumbL)
        emitInputEvent(ctx, env, XR_EVT_TOGGLE_IMMERSIVE, 0, 0.0f, XR_SRC_CONTROLLER);
    io.prevThumbL = tL;

    bool tR = readBool(io.thumbClickR);
    if (tR && !io.prevThumbR)
        emitInputEvent(ctx, env, XR_EVT_RECENTER, 1, 0.0f, XR_SRC_CONTROLLER);
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
            emitInputEvent(ctx, env, XR_EVT_CHEATSHEET, 0, 0.0f, XR_SRC_CONTROLLER);
            io.yLongPressEmitted = true;
        }
    }
    else if (!y && io.prevY)
    {
        if (!io.yLongPressEmitted && (monotonicNowNs() - io.yPressTimeNs) < kYLongPressNs)
            emitInputEvent(ctx, env, XR_EVT_FILE_OPS, 0, 0.0f, XR_SRC_CONTROLLER);
    }
    io.prevY = y;

    // ── Grip: analog zoom ──────────────────────────────────────────────
    auto processGrip = [&](float current, float &prev, int hand)
    {
        if (current > kGripPressThresh && prev <= kGripPressThresh)
            emitInputEvent(ctx, env, XR_EVT_ZOOM_START, hand, current, XR_SRC_CONTROLLER);
        else if (current < kGripReleaseThresh && prev >= kGripReleaseThresh)
            emitInputEvent(ctx, env, XR_EVT_ZOOM_END, hand, current, XR_SRC_CONTROLLER);
        else if (current > kGripPressThresh)
        {
            float d = current - prev;
            if (d > kGripDeltaMin || d < -kGripDeltaMin)
                emitInputEvent(ctx, env, XR_EVT_ZOOM_DELTA, hand, d, XR_SRC_CONTROLLER);
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
            emitInputEvent(ctx, env, XR_EVT_ZOOM_RESET, -1, 0.0f, XR_SRC_CONTROLLER);
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
        if (!edgeX)
        {
            if (v.x > kStickThreshold)
            {
                emitInputEvent(ctx,
                               env,
                               isLeft ? XR_EVT_SEEK_FORWARD : XR_EVT_FILE_NEXT,
                               isLeft ? 0 : 1,
                               v.x,
                               XR_SRC_CONTROLLER);
                edgeX = true;
            }
            else if (v.x < -kStickThreshold)
            {
                emitInputEvent(ctx,
                               env,
                               isLeft ? XR_EVT_SEEK_BACKWARD : XR_EVT_FILE_PREV,
                               isLeft ? 0 : 1,
                               -v.x,
                               XR_SRC_CONTROLLER);
                edgeX = true;
            }
        }
        else if (v.x > -kStickReturnZone && v.x < kStickReturnZone)
        {
            edgeX = false;
        }
        if (!edgeY)
        {
            if (v.y > kStickThreshold)
            {
                emitInputEvent(ctx, env, XR_EVT_VOLUME_UP, isLeft ? 0 : 1, v.y, XR_SRC_CONTROLLER);
                edgeY = true;
            }
            else if (v.y < -kStickThreshold)
            {
                emitInputEvent(ctx, env, XR_EVT_VOLUME_DOWN, isLeft ? 0 : 1, -v.y, XR_SRC_CONTROLLER);
                edgeY = true;
            }
        }
        else if (v.y > -kStickReturnZone && v.y < kStickReturnZone)
        {
            edgeY = false;
        }
    };
    processStick(io.stickL, io.stickLEdgeXActive, io.stickLEdgeYActive, true);
    processStick(io.stickR, io.stickREdgeXActive, io.stickREdgeYActive, false);
}

// Destroy OpenXR action handles (no env required).
void xrnative::destroyInputHandles(XrCtx &ctx)
{
    auto &io = ctx.input;
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
    if (io.aimSpaceL != XR_NULL_HANDLE)
    {
        xrDestroySpace(io.aimSpaceL);
        io.aimSpaceL = XR_NULL_HANDLE;
    }
    if (io.aimSpaceR != XR_NULL_HANDLE)
    {
        xrDestroySpace(io.aimSpaceR);
        io.aimSpaceR = XR_NULL_HANDLE;
    }
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
void xrnative::releaseInputCallback(XrCtx &ctx, JNIEnv *env)
{
    auto &io = ctx.input;
    if (io.inputCallbackRef && env)
        env->DeleteGlobalRef(io.inputCallbackRef);
    io.inputCallbackRef = nullptr;
    io.onInputEventMethod = nullptr;
    io.onPointerMoveMethod = nullptr;
    io.onControllerPointerMoveMethod = nullptr;
}

void xrnative::emitControllerPointerMove(XrCtx &ctx, JNIEnv *env, int32_t hand, float ndcX, float ndcY)
{
    auto &io = ctx.input;
    if (!io.inputCallbackRef || !io.onControllerPointerMoveMethod || !env)
        return;
    env->CallVoidMethod(io.inputCallbackRef, io.onControllerPointerMoveMethod,
                        static_cast<jint>(hand),
                        static_cast<jfloat>(ndcX),
                        static_cast<jfloat>(ndcY));
}

// Project controller aim pose onto the UI plane and emit NDC.
// UI plane: flat quad at 1.5 m forward in local space (same as hand ray).
void xrnative::syncControllerAimRay(XrCtx &ctx, JNIEnv *env)
{
    auto &io = ctx.input;
    if (!io.initialized || !ctx.sessionRunning || !ctx.viewSpace)
        return;
    // WHY: use the most recent predicted display time cached by renderFrame.
    // Aim space location must be queried with the frame's predicted time for low latency.
    XrTime t = ctx.lastPredictedDisplayTime;
    if (t == 0)
        return;

    constexpr float kPlaneDistance = 1.5f;
    const bool rayEnabled = ctx.controllerRayEnabled.load();

    auto processHand = [&](XrSpace aimSpace, int handIdx)
    {
        if (aimSpace == XR_NULL_HANDLE)
        {
            if (rayEnabled) { ctx.rayState[handIdx].active = false; }
            emitControllerPointerMove(ctx, env, handIdx, 2.0f, 2.0f);
            return;
        }
        XrSpaceLocation loc{XR_TYPE_SPACE_LOCATION};
        if (XR_FAILED(xrLocateSpace(aimSpace, ctx.viewSpace, t, &loc)))
        {
            if (rayEnabled) { ctx.rayState[handIdx].active = false; }
            emitControllerPointerMove(ctx, env, handIdx, 2.0f, 2.0f);
            return;
        }
        const bool valid =
            (loc.locationFlags & XR_SPACE_LOCATION_POSITION_VALID_BIT) &&
            (loc.locationFlags & XR_SPACE_LOCATION_ORIENTATION_VALID_BIT);
        if (!valid)
        {
            if (rayEnabled) { ctx.rayState[handIdx].active = false; }
            emitControllerPointerMove(ctx, env, handIdx, 2.0f, 2.0f);
            return;
        }
        const XrQuaternionf &q = loc.pose.orientation;
        float rx = 2.0f * (q.x * q.z - q.w * q.y);
        float ry = 2.0f * (q.y * q.z + q.w * q.x);
        float rz = -(1.0f - 2.0f * (q.x * q.x + q.y * q.y));

        const XrVector3f &origin = loc.pose.position;
        if (std::fabs(rz) < 1e-6f)
        {
            if (rayEnabled) { ctx.rayState[handIdx].active = false; }
            emitControllerPointerMove(ctx, env, handIdx, 2.0f, 2.0f);
            return;
        }
        float hitT = (-kPlaneDistance - origin.z) / rz;
        if (hitT < 0.0f)
        {
            if (rayEnabled) { ctx.rayState[handIdx].active = false; }
            emitControllerPointerMove(ctx, env, handIdx, 2.0f, 2.0f);
            return;
        }
        float hitX = origin.x + hitT * rx;
        float hitY = origin.y + hitT * ry;

        constexpr float kHudHalfW = 0.5f;
        constexpr float kHudHalfH = 0.15f;
        constexpr float kHudCentreY = -0.35f;
        float ndcX = hitX / kHudHalfW;
        float ndcY = (hitY - kHudCentreY) / kHudHalfH;
        emitControllerPointerMove(ctx, env, handIdx, ndcX, ndcY);

        if (rayEnabled)
        {
            ctx.rayState[handIdx].active = true;
            ctx.rayState[handIdx].originX = origin.x;
            ctx.rayState[handIdx].originY = origin.y;
            ctx.rayState[handIdx].originZ = origin.z;
            const bool insideHud = (std::fabs(ndcX) <= 1.0f) && (std::fabs(ndcY) <= 1.0f);
            if (insideHud)
            {
                ctx.rayState[handIdx].endX = hitX;
                ctx.rayState[handIdx].endY = hitY;
                ctx.rayState[handIdx].endZ = -kPlaneDistance;
                ctx.rayState[handIdx].hasCursor = true;
                ctx.rayState[handIdx].cursorX = hitX;
                ctx.rayState[handIdx].cursorY = hitY;
                ctx.rayState[handIdx].cursorZ = -kPlaneDistance;
            }
            else
            {
                constexpr float kMaxRayMeters = 5.0f;
                ctx.rayState[handIdx].endX = origin.x + kMaxRayMeters * rx;
                ctx.rayState[handIdx].endY = origin.y + kMaxRayMeters * ry;
                ctx.rayState[handIdx].endZ = origin.z + kMaxRayMeters * rz;
                ctx.rayState[handIdx].hasCursor = false;
            }
        }
        // Ray endpoints written above; rendering happens in OpenXrRayDraw::drawControllerRays.
    };

    processHand(io.aimSpaceL, 0);
    processHand(io.aimSpaceR, 1);
}