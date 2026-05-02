#pragma once

#include "OpenXrCtx.h"

namespace xrnative
{

    // Event-type enum is kept in lockstep with Kotlin `XrInputEventType`.
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
        XR_EVT_POINTER_CLICK_DOWN = 17,
        XR_EVT_POINTER_CLICK_UP = 18,
        XR_EVT_SWIPE_LEFT = 19,
        XR_EVT_SWIPE_RIGHT = 20,
        XR_EVT_SWIPE_UP = 21,
        XR_EVT_SWIPE_DOWN = 22,
        XR_EVT_DOUBLE_PINCH = 23,
    };

    // Event source identifiers — lockstep with Kotlin `XrInputSource`.
    constexpr int32_t XR_SRC_CONTROLLER = 0;
    constexpr int32_t XR_SRC_HAND = 1;

    bool setupActionSet(XrCtx &ctx);
    bool createControllerAimSpaces(XrCtx &ctx);
    void syncInputActions(XrCtx &ctx, JNIEnv *env);
    void syncControllerAimRay(XrCtx &ctx, JNIEnv *env);
    void destroyInputHandles(XrCtx &ctx);
    void releaseInputCallback(XrCtx &ctx, JNIEnv *env);
    void emitInputEvent(XrCtx &ctx, JNIEnv *env, int32_t type, int32_t hand, float value, int32_t source);
    void emitPointerMove(XrCtx &ctx, JNIEnv *env, int32_t hand, float ndcX, float ndcY);
    void emitControllerPointerMove(XrCtx &ctx, JNIEnv *env, int32_t hand, float ndcX, float ndcY);

} // namespace xrnative