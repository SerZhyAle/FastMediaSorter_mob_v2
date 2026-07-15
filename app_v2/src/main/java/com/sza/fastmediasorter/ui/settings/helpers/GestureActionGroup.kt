package com.sza.fastmediasorter.ui.settings.helpers

import androidx.annotation.StringRes
import com.sza.fastmediasorter.R

/**
 * S1038: presentation grouping for the gesture-action picker. Declaration order is the section order
 * shown in the picker; groups with no available action are skipped (see [ScreenshotGestureActionCatalog]).
 * DEVICE and SYSTEM carry no existing actions yet - they are populated by the later action batches
 * (phases 03/05) and stay hidden until then.
 */
enum class GestureActionGroup(@StringRes val titleRes: Int) {
    CAPTURE(R.string.gesture_action_group_capture),
    CAMERA(R.string.gesture_action_group_camera),
    LAUNCH(R.string.gesture_action_group_launch),
    DEVICE(R.string.gesture_action_group_device),
    SYSTEM(R.string.gesture_action_group_system),
    UTILITY(R.string.gesture_action_group_utility),
    DISABLED(R.string.gesture_action_group_disabled),
}
