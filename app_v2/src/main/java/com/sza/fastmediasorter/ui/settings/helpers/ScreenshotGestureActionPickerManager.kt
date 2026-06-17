package com.sza.fastmediasorter.ui.settings.helpers

import android.content.Context
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.capability.CapabilityAvailability
import com.sza.fastmediasorter.domain.model.ScreenshotGestureAction

/**
 * Builds the per-direction screenshot-gesture action picker dialog and maps actions to labels.
 * OCR-translate is hidden when the translation capability is not compiled into the build.
 * The fragment owns persistence; this manager only maps and presents.
 */
class ScreenshotGestureActionPickerManager(
    private val capabilityAvailability: CapabilityAvailability
) {

    fun labelFor(context: Context, action: ScreenshotGestureAction): String =
        context.getString(labelResFor(action))

    fun availableActions(): List<ScreenshotGestureAction> =
        ScreenshotGestureAction.entries.filter {
            it != ScreenshotGestureAction.OCR_TRANSLATE || capabilityAvailability.isTranslationAvailable()
        }

    fun showPicker(
        context: Context,
        current: ScreenshotGestureAction,
        onPicked: (ScreenshotGestureAction) -> Unit
    ) {
        val actions = availableActions()
        val labels = actions.map { labelFor(context, it) }.toTypedArray()
        val checked = actions.indexOf(current).coerceAtLeast(0)
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.setting_screenshot_gesture_action_dialog_title)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                onPicked(actions[which])
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun labelResFor(action: ScreenshotGestureAction): Int = when (action) {
        ScreenshotGestureAction.SILENT_SCREENSHOT -> R.string.screenshot_gesture_action_silent
        ScreenshotGestureAction.OPEN_IN_PLAYER -> R.string.screenshot_gesture_action_open_player
        ScreenshotGestureAction.OPEN_IN_DRAW -> R.string.screenshot_gesture_action_open_draw
        ScreenshotGestureAction.OCR_TRANSLATE -> R.string.screenshot_gesture_action_ocr_translate
        ScreenshotGestureAction.SEND_TO_RECIPIENTS -> R.string.screenshot_gesture_action_send_to
        ScreenshotGestureAction.SHARE -> R.string.screenshot_gesture_action_share
        ScreenshotGestureAction.DO_NOT_USE -> R.string.screenshot_gesture_action_none
    }
}
