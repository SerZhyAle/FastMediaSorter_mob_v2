package com.sza.fastmediasorter.vr.openxr

/**
 * Event-type constants shared between the C++ OpenXR action system
 * and the Kotlin dispatcher. Values MUST stay in lockstep with the
 * `XrInputEventType` enum in `app_v2/src/vr/cpp/OpenXrNative.cpp`.
 */
object XrInputEventType {
    const val PAUSE_TOGGLE = 0
    const val EXIT = 1
    const val FILE_OPS = 2
    const val MENU = 3
    const val SEEK_BACKWARD = 4
    const val SEEK_FORWARD = 5
    const val FILE_PREV = 6
    const val FILE_NEXT = 7
    const val VOLUME_UP = 8
    const val VOLUME_DOWN = 9
    const val RECENTER = 10
    const val TOGGLE_IMMERSIVE = 11
    const val CHEATSHEET = 12
    const val ZOOM_START = 13
    const val ZOOM_DELTA = 14
    const val ZOOM_END = 15
    const val ZOOM_RESET = 16

    // ─── Layer E: hand tracking (spec_vr-hand-tracking-tech §5) ────────
    const val POINTER_CLICK_DOWN = 17
    const val POINTER_CLICK_UP = 18
    const val SWIPE_LEFT = 19
    const val SWIPE_RIGHT = 20
    const val SWIPE_UP = 21
    const val SWIPE_DOWN = 22
    const val DOUBLE_PINCH = 23
}

/**
 * Hand identifier used by haptic + input callbacks.
 * Values MUST stay in lockstep with the C++ side.
 */
object XrHand {
    const val LEFT = 0
    const val RIGHT = 1
    const val UNKNOWN = -1
}

/**
 * Input source identifier distinguishing OpenXR Touch controllers from hand tracking.
 * Values MUST stay in lockstep with `XR_SRC_*` in `OpenXrNative.cpp`.
 */
object XrInputSource {
    const val CONTROLLER = 0
    const val HAND = 1
}
