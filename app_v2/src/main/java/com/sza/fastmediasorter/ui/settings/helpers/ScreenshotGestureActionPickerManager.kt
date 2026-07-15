package com.sza.fastmediasorter.ui.settings.helpers

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.capability.CapabilityAvailability
import com.sza.fastmediasorter.domain.model.ScreenshotGestureAction

/**
 * Builds the per-direction screenshot-gesture action picker and maps actions to labels.
 * OCR-translate is hidden when the translation capability is not compiled into the build; screen
 * recording (S0797) is hidden when the capture engine is absent (lite/photos/legacy).
 * S1038: the picker is now a grouped list with per-action explanations ([GesturePickerDialog]);
 * action metadata (group + label + explanation) is resolved via [ScreenshotGestureActionCatalog].
 * The fragment owns persistence; this manager only maps and presents.
 */
class ScreenshotGestureActionPickerManager(
    private val capabilityAvailability: CapabilityAvailability,
    private val screenRecordingAvailable: Boolean = false,
) {

    fun labelFor(context: Context, action: ScreenshotGestureAction): String =
        context.getString(ScreenshotGestureActionCatalog.labelResFor(action))

    fun availableActions(): List<ScreenshotGestureAction> =
        ScreenshotGestureAction.entries.filter { action ->
            when (action) {
                ScreenshotGestureAction.OCR_TRANSLATE,
                ScreenshotGestureAction.TAKE_PHOTO_OCR_TRANSLATE ->
                    capabilityAvailability.isTranslationAvailable()
                ScreenshotGestureAction.START_SCREEN_RECORDING -> screenRecordingAvailable
                else -> true
            }
        }

    fun showPicker(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        current: ScreenshotGestureAction,
        onPicked: (ScreenshotGestureAction) -> Unit
    ) {
        GesturePickerDialog(
            context = context,
            title = context.getString(R.string.setting_screenshot_gesture_action_dialog_title),
            lifecycleOwner = lifecycleOwner,
            rows = buildRows(),
            selectedAction = current,
            onPicked = onPicked,
        ).show()
    }

    // Emits one Header per non-empty group followed by its available actions, in group-declaration
    // order (empty groups - e.g. DEVICE/SYSTEM until the later batches - are skipped). Within a group,
    // actions keep ScreenshotGestureAction declaration order.
    private fun buildRows(): List<GesturePickerRow> {
        val available = availableActions().toSet()
        return GestureActionGroup.entries.flatMap { group ->
            val entries = ScreenshotGestureAction.entries.filter {
                it in available && ScreenshotGestureActionCatalog.groupOf(it) == group
            }
            if (entries.isEmpty()) {
                emptyList()
            } else {
                buildList {
                    add(GesturePickerRow.Header(group.titleRes))
                    entries.forEach { action ->
                        add(
                            GesturePickerRow.Entry(
                                action = action,
                                labelRes = ScreenshotGestureActionCatalog.labelResFor(action),
                                explanationRes = ScreenshotGestureActionCatalog.explanationResFor(action),
                                enabled = true,
                            ),
                        )
                    }
                }
            }
        }
    }
}
