// S0283 Phase 01: XR Input module implementation.
// Manages controller actions, hand tracking lifecycles, and haptic feedback.

#include "xr_input.h"
#include <android/log.h>
#include <cmath>
#include <cstdio>
#include <cstring>
#include <vector>

namespace fms::xr {

namespace {
constexpr const char* kLogTag = "S0283.XrInput";
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, kLogTag, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,  kLogTag, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, kLogTag, __VA_ARGS__)

// OpenXR dynamic function pointers
PFN_xrCreateHandTrackerEXT pfnCreateHandTrackerEXT = nullptr;
PFN_xrDestroyHandTrackerEXT pfnDestroyHandTrackerEXT = nullptr;
PFN_xrLocateHandJointsEXT pfnLocateHandJointsEXT = nullptr;
PFN_xrApplyHapticFeedback pfnApplyHapticFeedback = nullptr;

XrInstance g_instance{XR_NULL_HANDLE};
XrSession  g_session{XR_NULL_HANDLE};

XrHandTrackerEXT g_leftHandTracker{XR_NULL_HANDLE};
XrHandTrackerEXT g_rightHandTracker{XR_NULL_HANDLE};

// OpenXR Actions
XrActionSet g_actionSet{XR_NULL_HANDLE};
XrAction    g_exitAction{XR_NULL_HANDLE};
XrAction    g_prevAction{XR_NULL_HANDLE};        // Left select (trigger / pinch strength)
XrAction    g_nextAction{XR_NULL_HANDLE};        // Right select (trigger / pinch strength)
XrAction    g_gripAction{XR_NULL_HANDLE};        // Drag placement
XrAction    g_thumbstickAction{XR_NULL_HANDLE};  // Volume & Parallax controls
XrAction    g_aimPoseAction{XR_NULL_HANDLE};     // Aim ray pose
XrAction    g_hapticAction{XR_NULL_HANDLE};      // Tactile feedback

// Spaces for aim poses
XrSpace     g_leftAimSpace{XR_NULL_HANDLE};
XrSpace     g_rightAimSpace{XR_NULL_HANDLE};

bool g_prevTriggerDownState[2] = {false, false};

} // namespace

HandInputState g_handInputStates[2];

NativeResult xr_input_init(XrInstance instance, XrSession session) {
    LOGD("xr_input_init: initializing XR input system");
    g_instance = instance;
    g_session = session;

    // 1. Resolve dynamic OpenXR function pointers (Step 01.2)
    xrGetInstanceProcAddr(g_instance, "xrCreateHandTrackerEXT", reinterpret_cast<PFN_xrVoidFunction*>(&pfnCreateHandTrackerEXT));
    xrGetInstanceProcAddr(g_instance, "xrDestroyHandTrackerEXT", reinterpret_cast<PFN_xrVoidFunction*>(&pfnDestroyHandTrackerEXT));
    xrGetInstanceProcAddr(g_instance, "xrLocateHandJointsEXT", reinterpret_cast<PFN_xrVoidFunction*>(&pfnLocateHandJointsEXT));
    xrGetInstanceProcAddr(g_instance, "xrApplyHapticFeedback", reinterpret_cast<PFN_xrVoidFunction*>(&pfnApplyHapticFeedback));

    LOGD("Input function pointers: createHandTracker=%p destroyHandTracker=%p locateHandJoints=%p applyHaptic=%p",
         reinterpret_cast<void*>(pfnCreateHandTrackerEXT),
         reinterpret_cast<void*>(pfnDestroyHandTrackerEXT),
         reinterpret_cast<void*>(pfnLocateHandJointsEXT),
         reinterpret_cast<void*>(pfnApplyHapticFeedback));

    // 2. Initialize Left/Right Hand Trackers (Step 01.3)
    if (pfnCreateHandTrackerEXT) {
        XrHandTrackerCreateInfoEXT leftInfo{XR_TYPE_HAND_TRACKER_CREATE_INFO_EXT};
        leftInfo.hand = XR_HAND_LEFT_EXT;
        leftInfo.handJointSet = XR_HAND_JOINT_SET_DEFAULT_EXT;
        XrResult r = pfnCreateHandTrackerEXT(g_session, &leftInfo, &g_leftHandTracker);
        if (XR_FAILED(r)) {
            LOGW("Failed to create Left Hand Tracker: %d", (int)r);
        } else {
            LOGD("Left Hand Tracker initialized successfully");
        }

        XrHandTrackerCreateInfoEXT rightInfo{XR_TYPE_HAND_TRACKER_CREATE_INFO_EXT};
        rightInfo.hand = XR_HAND_RIGHT_EXT;
        rightInfo.handJointSet = XR_HAND_JOINT_SET_DEFAULT_EXT;
        r = pfnCreateHandTrackerEXT(g_session, &rightInfo, &g_rightHandTracker);
        if (XR_FAILED(r)) {
            LOGW("Failed to create Right Hand Tracker: %d", (int)r);
        } else {
            LOGD("Right Hand Tracker initialized successfully");
        }
    } else {
        LOGW("Hand tracking extension pointers not resolved. Skipping tracker creation.");
    }

    // 3. Create Action Set and Actions (Step 01.4)
    XrActionSetCreateInfo asInfo{XR_TYPE_ACTION_SET_CREATE_INFO};
    std::snprintf(asInfo.actionSetName, sizeof(asInfo.actionSetName), "diagnostic_input");
    std::snprintf(asInfo.localizedActionSetName, sizeof(asInfo.localizedActionSetName), "Diagnostic input");
    XrResult r = xrCreateActionSet(g_instance, &asInfo, &g_actionSet);
    if (XR_FAILED(r)) {
        LOGE("xrCreateActionSet failed: %d", (int)r);
        return NativeResult::UnexpectedRuntimeError;
    }

    // Exit Action (Boolean)
    XrActionCreateInfo aiExit{XR_TYPE_ACTION_CREATE_INFO};
    std::snprintf(aiExit.actionName, sizeof(aiExit.actionName), "exit_click");
    std::snprintf(aiExit.localizedActionName, sizeof(aiExit.localizedActionName), "Exit session");
    aiExit.actionType = XR_ACTION_TYPE_BOOLEAN_INPUT;
    xrCreateAction(g_actionSet, &aiExit, &g_exitAction);

    // Select/Click Actions (Float for analog triggers / pinch strengths)
    XrActionCreateInfo aiPrev{XR_TYPE_ACTION_CREATE_INFO};
    std::snprintf(aiPrev.actionName, sizeof(aiPrev.actionName), "prev_trigger");
    std::snprintf(aiPrev.localizedActionName, sizeof(aiPrev.localizedActionName), "Previous media / Left select");
    aiPrev.actionType = XR_ACTION_TYPE_FLOAT_INPUT;
    xrCreateAction(g_actionSet, &aiPrev, &g_prevAction);

    XrActionCreateInfo aiNext{XR_TYPE_ACTION_CREATE_INFO};
    std::snprintf(aiNext.actionName, sizeof(aiNext.actionName), "next_trigger");
    std::snprintf(aiNext.localizedActionName, sizeof(aiNext.localizedActionName), "Next media / Right select");
    aiNext.actionType = XR_ACTION_TYPE_FLOAT_INPUT;
    xrCreateAction(g_actionSet, &aiNext, &g_nextAction);

    // Grip Squeeze Actions (Float for analog grip drag placement)
    XrActionCreateInfo aiGrip{XR_TYPE_ACTION_CREATE_INFO};
    std::snprintf(aiGrip.actionName, sizeof(aiGrip.actionName), "grip_squeeze");
    std::snprintf(aiGrip.localizedActionName, sizeof(aiGrip.localizedActionName), "Grip squeeze");
    aiGrip.actionType = XR_ACTION_TYPE_FLOAT_INPUT;
    xrCreateAction(g_actionSet, &aiGrip, &g_gripAction);

    // Thumbstick Actions (Vector2)
    XrActionCreateInfo aiStick{XR_TYPE_ACTION_CREATE_INFO};
    std::snprintf(aiStick.actionName, sizeof(aiStick.actionName), "thumbstick_input");
    std::snprintf(aiStick.localizedActionName, sizeof(aiStick.localizedActionName), "Thumbstick input");
    aiStick.actionType = XR_ACTION_TYPE_VECTOR2F_INPUT;
    xrCreateAction(g_actionSet, &aiStick, &g_thumbstickAction);

    // Aim Ray Action (Pose)
    XrActionCreateInfo aiAim{XR_TYPE_ACTION_CREATE_INFO};
    std::snprintf(aiAim.actionName, sizeof(aiAim.actionName), "aim_pose");
    std::snprintf(aiAim.localizedActionName, sizeof(aiAim.localizedActionName), "Aim pose");
    aiAim.actionType = XR_ACTION_TYPE_POSE_INPUT;
    xrCreateAction(g_actionSet, &aiAim, &g_aimPoseAction);

    // Haptic Action (Vibration Output)
    XrActionCreateInfo aiHaptic{XR_TYPE_ACTION_CREATE_INFO};
    std::snprintf(aiHaptic.actionName, sizeof(aiHaptic.actionName), "haptic_feedback");
    std::snprintf(aiHaptic.localizedActionName, sizeof(aiHaptic.localizedActionName), "Tactile vibration feedback");
    aiHaptic.actionType = XR_ACTION_TYPE_VIBRATION_OUTPUT;
    xrCreateAction(g_actionSet, &aiHaptic, &g_hapticAction);

    // 4. Bind Interaction Profiles
    auto suggestProfile = [&](const char* profilePath,
                              const std::vector<std::pair<XrAction, const char*>>& items) {
        XrPath profile; xrStringToPath(g_instance, profilePath, &profile);
        std::vector<XrActionSuggestedBinding> bindings;
        bindings.reserve(items.size());
        for (const auto& [action, path] : items) {
            if (action == XR_NULL_HANDLE) continue;
            XrPath p; xrStringToPath(g_instance, path, &p);
            bindings.push_back({action, p});
        }
        XrInteractionProfileSuggestedBinding s{XR_TYPE_INTERACTION_PROFILE_SUGGESTED_BINDING};
        s.interactionProfile = profile;
        s.countSuggestedBindings = (uint32_t)bindings.size();
        s.suggestedBindings = bindings.data();
        XrResult sr = xrSuggestInteractionProfileBindings(g_instance, &s);
        if (XR_FAILED(sr)) LOGW("xrSuggestInteractionProfileBindings(%s)=%d", profilePath, (int)sr);
    };

    // Oculus Touch Controller profile with symmetric mappings (Step 01.4)
    suggestProfile("/interaction_profiles/oculus/touch_controller", {
        {g_exitAction, "/user/hand/left/input/x/click"},
        {g_exitAction, "/user/hand/right/input/a/click"},
        {g_prevAction, "/user/hand/left/input/trigger/value"},
        {g_nextAction, "/user/hand/right/input/trigger/value"},
        {g_gripAction, "/user/hand/left/input/squeeze/value"},
        {g_gripAction, "/user/hand/right/input/squeeze/value"},
        {g_thumbstickAction, "/user/hand/left/input/thumbstick"},
        {g_thumbstickAction, "/user/hand/right/input/thumbstick"},
        {g_aimPoseAction, "/user/hand/left/input/aim/pose"},
        {g_aimPoseAction, "/user/hand/right/input/aim/pose"},
        {g_hapticAction, "/user/hand/left/output/haptic"},
        {g_hapticAction, "/user/hand/right/output/haptic"},
    });

    // Cross-vendor Hand Interaction Profile (XR_EXT_hand_interaction) (Step 01.1 & Step 01.4)
    suggestProfile("/interaction_profiles/ext/hand_interaction_ext", {
        {g_prevAction, "/user/hand/left/input/pinch_ext/value"},
        {g_nextAction, "/user/hand/right/input/pinch_ext/value"},
        {g_gripAction, "/user/hand/left/input/grasp_ext/value"},
        {g_gripAction, "/user/hand/right/input/grasp_ext/value"},
        {g_aimPoseAction, "/user/hand/left/input/aim_ext/pose"},
        {g_aimPoseAction, "/user/hand/right/input/aim_ext/pose"},
    });

    // 5. Attach Session Action Sets
    XrSessionActionSetsAttachInfo attach{XR_TYPE_SESSION_ACTION_SETS_ATTACH_INFO};
    attach.countActionSets = 1;
    attach.actionSets = &g_actionSet;
    r = xrAttachSessionActionSets(g_session, &attach);
    if (XR_FAILED(r)) {
        LOGE("xrAttachSessionActionSets failed: %d", (int)r);
        return NativeResult::UnexpectedRuntimeError;
    }

    // 6. Create Action Spaces for Left/Right Aim Ray Poses
    XrActionSpaceCreateInfo leftAimInfo{XR_TYPE_ACTION_SPACE_CREATE_INFO};
    leftAimInfo.action = g_aimPoseAction;
    leftAimInfo.poseInActionSpace.orientation = {0.0f, 0.0f, 0.0f, 1.0f};
    leftAimInfo.poseInActionSpace.position = {0.0f, 0.0f, 0.0f};
    XrPath leftPath; xrStringToPath(g_instance, "/user/hand/left", &leftPath);
    leftAimInfo.subactionPath = leftPath;
    r = xrCreateActionSpace(g_session, &leftAimInfo, &g_leftAimSpace);
    if (XR_FAILED(r)) {
        LOGW("Failed to create Left Aim Space: %d", (int)r);
    }

    XrActionSpaceCreateInfo rightAimInfo{XR_TYPE_ACTION_SPACE_CREATE_INFO};
    rightAimInfo.action = g_aimPoseAction;
    rightAimInfo.poseInActionSpace.orientation = {0.0f, 0.0f, 0.0f, 1.0f};
    rightAimInfo.poseInActionSpace.position = {0.0f, 0.0f, 0.0f};
    XrPath rightPath; xrStringToPath(g_instance, "/user/hand/right", &rightPath);
    rightAimInfo.subactionPath = rightPath;
    r = xrCreateActionSpace(g_session, &rightAimInfo, &g_rightAimSpace);
    if (XR_FAILED(r)) {
        LOGW("Failed to create Right Aim Space: %d", (int)r);
    }

    LOGD("xr_input_init complete");
    return NativeResult::Ok;
}

void xr_input_poll(XrSpace baseSpace, XrTime predictedTime) {
    if (g_actionSet == XR_NULL_HANDLE) return;

    // Sync Action Sets
    XrActiveActionSet aas{g_actionSet, XR_NULL_PATH};
    XrActionsSyncInfo syncInfo{XR_TYPE_ACTIONS_SYNC_INFO};
    syncInfo.countActiveActionSets = 1;
    syncInfo.activeActionSets = &aas;
    XrResult r = xrSyncActions(g_session, &syncInfo);
    if (XR_FAILED(r)) {
        // Silent ignore in transient states
        return;
    }

    XrPath subactionPaths[2] = {XR_NULL_PATH, XR_NULL_PATH};
    xrStringToPath(g_instance, "/user/hand/left", &subactionPaths[0]);
    xrStringToPath(g_instance, "/user/hand/right", &subactionPaths[1]);

    for (int hand = 0; hand < 2; ++hand) {
        HandInputState& state = g_handInputStates[hand];
        state.active = false;
        state.isHand = false;

        // Check if hand joints are tracking to set the hand tracking flag
        if (pfnLocateHandJointsEXT) {
            XrHandTrackerEXT tracker = (hand == 0) ? g_leftHandTracker : g_rightHandTracker;
            if (tracker != XR_NULL_HANDLE) {
                XrHandJointsLocateInfoEXT locateInfo{XR_TYPE_HAND_JOINTS_LOCATE_INFO_EXT};
                locateInfo.baseSpace = baseSpace;
                locateInfo.time = predictedTime;

                // Allocate joint array
                XrHandJointLocationEXT jointLocations[XR_HAND_JOINT_COUNT_EXT];
                XrHandJointLocationsEXT locations{XR_TYPE_HAND_JOINT_LOCATIONS_EXT};
                locations.jointCount = XR_HAND_JOINT_COUNT_EXT;
                locations.jointLocations = jointLocations;

                XrResult locResult = pfnLocateHandJointsEXT(tracker, &locateInfo, &locations);
                if (XR_SUCCEEDED(locResult) && locations.isActive) {
                    state.isHand = true;
                }
            }
        }

        // Poll Exit Action
        XrActionStateGetInfo exitGetInfo{XR_TYPE_ACTION_STATE_GET_INFO};
        exitGetInfo.action = g_exitAction;
        exitGetInfo.subactionPath = subactionPaths[hand];
        XrActionStateBoolean exitState{XR_TYPE_ACTION_STATE_BOOLEAN};
        if (xrGetActionStateBoolean(g_session, &exitGetInfo, &exitState) == XR_SUCCESS) {
            if (exitState.isActive && exitState.changedSinceLastSync && exitState.currentState) {
                LOGD("S0283: Exit action triggered, requesting session exit");
                xr_session_request_exit();
            }
        }

        // Check if this hand is active / tracking aim ray
        XrActionStateGetInfo aimGetInfo{XR_TYPE_ACTION_STATE_GET_INFO};
        aimGetInfo.action = g_aimPoseAction;
        aimGetInfo.subactionPath = subactionPaths[hand];
        XrActionStatePose aimState{XR_TYPE_ACTION_STATE_POSE};
        xrGetActionStatePose(g_session, &aimGetInfo, &aimState);

        if (aimState.isActive) {
            state.active = true;

            // Locate Aim ray in space
            XrSpace aimSpace = (hand == 0) ? g_leftAimSpace : g_rightAimSpace;
            if (aimSpace != XR_NULL_HANDLE) {
                XrSpaceLocation aimLoc{XR_TYPE_SPACE_LOCATION};
                XrResult locR = xrLocateSpace(aimSpace, baseSpace, predictedTime, &aimLoc);
                if (XR_SUCCEEDED(locR) && (aimLoc.locationFlags & XR_SPACE_LOCATION_POSITION_VALID_BIT) &&
                    (aimLoc.locationFlags & XR_SPACE_LOCATION_ORIENTATION_VALID_BIT)) {
                    state.pointerPose = aimLoc.pose;
                }
            }

            // Poll Select (Trigger / Pinch) Action
            XrActionStateGetInfo selectGetInfo{XR_TYPE_ACTION_STATE_GET_INFO};
            selectGetInfo.action = (hand == 0) ? g_prevAction : g_nextAction;
            selectGetInfo.subactionPath = subactionPaths[hand];
            XrActionStateFloat selectState{XR_TYPE_ACTION_STATE_FLOAT};
            xrGetActionStateFloat(g_session, &selectGetInfo, &selectState);

            if (selectState.isActive) {
                state.triggerValue = selectState.currentState;
                // Threshold trigger click (0.4f)
                bool isDown = (selectState.currentState >= 0.4f);
                state.triggerClicked = (isDown && !g_prevTriggerDownState[hand]);
                state.triggerDown = isDown;
                g_prevTriggerDownState[hand] = isDown;
            }

            // Poll Grip Action
            XrActionStateGetInfo gripGetInfo{XR_TYPE_ACTION_STATE_GET_INFO};
            gripGetInfo.action = g_gripAction;
            gripGetInfo.subactionPath = subactionPaths[hand];
            XrActionStateFloat gripState{XR_TYPE_ACTION_STATE_FLOAT};
            xrGetActionStateFloat(g_session, &gripGetInfo, &gripState);

            if (gripState.isActive) {
                state.gripDown = (gripState.currentState >= 0.4f);
            }

            // Poll Thumbstick Action
            XrActionStateGetInfo stickGetInfo{XR_TYPE_ACTION_STATE_GET_INFO};
            stickGetInfo.action = g_thumbstickAction;
            stickGetInfo.subactionPath = subactionPaths[hand];
            XrActionStateVector2f stickState{XR_TYPE_ACTION_STATE_VECTOR2F};
            xrGetActionStateVector2f(g_session, &stickGetInfo, &stickState);

            if (stickState.isActive) {
                state.thumbstickX = stickState.currentState.x;
                state.thumbstickY = stickState.currentState.y;
            }
        }
    }
}

void xr_input_apply_haptic(int hand, float durationSeconds, float frequency, float amplitude) {
    if (!pfnApplyHapticFeedback || g_hapticAction == XR_NULL_HANDLE) return;

    XrPath subactionPath = XR_NULL_PATH;
    if (hand == 0) {
        xrStringToPath(g_instance, "/user/hand/left", &subactionPath);
    } else {
        xrStringToPath(g_instance, "/user/hand/right", &subactionPath);
    }

    XrHapticActionInfo actionInfo{XR_TYPE_HAPTIC_ACTION_INFO};
    actionInfo.action = g_hapticAction;
    actionInfo.subactionPath = subactionPath;

    XrHapticVibration vibration{XR_TYPE_HAPTIC_VIBRATION};
    vibration.duration = static_cast<XrDuration>(durationSeconds * 1e9f); // convert to nanoseconds
    vibration.frequency = frequency;
    vibration.amplitude = amplitude;

    XrResult r = pfnApplyHapticFeedback(g_session, &actionInfo, reinterpret_cast<const XrHapticBaseHeader*>(&vibration));
    if (XR_FAILED(r)) {
        LOGW("xrApplyHapticFeedback failed: %d", (int)r);
    }
}

void xr_input_shutdown() {
    LOGD("xr_input_shutdown: tearing down XR input system");

    // Clean up spaces
    if (g_leftAimSpace != XR_NULL_HANDLE) {
        xrDestroySpace(g_leftAimSpace);
        g_leftAimSpace = XR_NULL_HANDLE;
    }
    if (g_rightAimSpace != XR_NULL_HANDLE) {
        xrDestroySpace(g_rightAimSpace);
        g_rightAimSpace = XR_NULL_HANDLE;
    }

    // Clean up trackers (Step 01.3)
    if (pfnDestroyHandTrackerEXT) {
        if (g_leftHandTracker != XR_NULL_HANDLE) {
            pfnDestroyHandTrackerEXT(g_leftHandTracker);
            g_leftHandTracker = XR_NULL_HANDLE;
        }
        if (g_rightHandTracker != XR_NULL_HANDLE) {
            pfnDestroyHandTrackerEXT(g_rightHandTracker);
            g_rightHandTracker = XR_NULL_HANDLE;
        }
    }

    // Clean up Action Set
    if (g_actionSet != XR_NULL_HANDLE) {
        xrDestroyActionSet(g_actionSet);
        g_actionSet = XR_NULL_HANDLE;
        g_exitAction = XR_NULL_HANDLE;
        g_prevAction = XR_NULL_HANDLE;
        g_nextAction = XR_NULL_HANDLE;
        g_gripAction = XR_NULL_HANDLE;
        g_thumbstickAction = XR_NULL_HANDLE;
        g_aimPoseAction = XR_NULL_HANDLE;
        g_hapticAction = XR_NULL_HANDLE;
    }

    pfnCreateHandTrackerEXT = nullptr;
    pfnDestroyHandTrackerEXT = nullptr;
    pfnLocateHandJointsEXT = nullptr;
    pfnApplyHapticFeedback = nullptr;
}

} // namespace fms::xr
