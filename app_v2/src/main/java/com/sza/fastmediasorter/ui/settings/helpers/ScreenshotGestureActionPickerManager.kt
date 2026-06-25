package com.sza.fastmediasorter.ui.settings.helpers

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.capability.CapabilityAvailability
import com.sza.fastmediasorter.domain.model.ScreenshotGestureAction
import com.sza.fastmediasorter.ui.dialog.ListSelectionAdapter
import com.sza.fastmediasorter.ui.dialog.ListSelectionConfig
import com.sza.fastmediasorter.ui.dialog.ListSelectionDialog

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
        lifecycleOwner: LifecycleOwner,
        current: ScreenshotGestureAction,
        onPicked: (ScreenshotGestureAction) -> Unit
    ) {
        ListSelectionDialog<ScreenshotGestureAction>(
            context,
            ListSelectionConfig(
                title = context.getString(R.string.setting_screenshot_gesture_action_dialog_title),
                lifecycleOwner = lifecycleOwner,
                loader = { availableActions() },
                formatter = object : ListSelectionAdapter.ItemFormatter<ScreenshotGestureAction> {
                    override fun getDisplayName(item: ScreenshotGestureAction): String =
                        labelFor(context, item)
                },
                hasSelection = true,
                isSelected = { it == current },
                allowClear = false,
                emptyMessageRes = R.string.error_unknown,
                errorMessageRes = R.string.error_unknown,
                onSelected = { it?.let(onPicked) },
            ),
        ).show()
    }

    private fun labelResFor(action: ScreenshotGestureAction): Int = when (action) {
        ScreenshotGestureAction.SILENT_SCREENSHOT -> R.string.screenshot_gesture_action_silent
        ScreenshotGestureAction.OPEN_IN_PLAYER -> R.string.screenshot_gesture_action_open_player
        ScreenshotGestureAction.OPEN_IN_DRAW -> R.string.screenshot_gesture_action_open_draw
        ScreenshotGestureAction.OCR_TRANSLATE -> R.string.screenshot_gesture_action_ocr_translate
        ScreenshotGestureAction.SEND_TO_RECIPIENTS -> R.string.screenshot_gesture_action_send_to
        ScreenshotGestureAction.SHARE -> R.string.screenshot_gesture_action_share
        ScreenshotGestureAction.CROP_AND_SHARE -> R.string.screenshot_gesture_action_crop_and_share
        ScreenshotGestureAction.OPEN_APP -> R.string.screenshot_gesture_action_open_app
        ScreenshotGestureAction.OPEN_PANEL -> R.string.screenshot_gesture_action_open_panel
        ScreenshotGestureAction.DO_NOT_USE -> R.string.screenshot_gesture_action_none
    }
}
