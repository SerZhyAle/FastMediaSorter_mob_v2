package com.sza.fastmediasorter.ui.settings.helpers

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.capability.CapabilityAvailability
import com.sza.fastmediasorter.domain.model.ScreenshotGestureAction
import com.sza.fastmediasorter.util.showBoundTo

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
    // S1038: SYSTEM-group actions run through the noLegal accessibility seam; hidden where it is absent.
    private val systemActionsAvailable: Boolean = false,
    // S2256: the launcher route exists only where the home surface is compiled in; the host supplies the
    // seam's own answer rather than each call site re-deciding it.
    private val launcherRouteAvailable: Boolean = false,
) {

    fun labelFor(context: Context, action: ScreenshotGestureAction): String =
        context.getString(ScreenshotGestureActionCatalog.labelResFor(action))

    fun availableActions(): List<ScreenshotGestureAction> =
        ScreenshotGestureAction.entries.filter { action ->
            ScreenshotGestureActionCatalog.isAvailableOnApi(action) && when (action) {
                ScreenshotGestureAction.OCR_TRANSLATE,
                ScreenshotGestureAction.TAKE_PHOTO_OCR_TRANSLATE ->
                    capabilityAvailability.isTranslationAvailable()
                ScreenshotGestureAction.START_SCREEN_RECORDING -> screenRecordingAvailable
                ScreenshotGestureAction.LOCK_SCREEN,
                ScreenshotGestureAction.TOGGLE_SPLIT_SCREEN,
                ScreenshotGestureAction.PREVIOUS_APP -> systemActionsAvailable
                ScreenshotGestureAction.OPEN_ALL_APPS -> launcherRouteAvailable
                else -> true
            }
        }

    /**
     * S2256: the picker's rows, exposed so a host offering a superset can prepend its own actions.
     * The launcher route is deliberately absent - it travels through [launcherRouteItem] instead, which
     * is what puts it at the head of its group on every surface rather than wherever the enum declares it.
     */
    fun pickerItems(): List<GesturePickerItem<ScreenshotGestureAction>> =
        availableActions()
            .filterNot { it == ScreenshotGestureAction.OPEN_ALL_APPS }
            .map { GesturePickerItem(it, ScreenshotGestureActionCatalog.metaFor(it)) }

    /** The launcher route as this surface offers it, or null where the build has no launcher. */
    fun launcherRouteItem(): GesturePickerItem<ScreenshotGestureAction>? =
        if (launcherRouteAvailable) {
            GesturePickerItem(
                ScreenshotGestureAction.OPEN_ALL_APPS,
                ScreenshotGestureActionCatalog.metaFor(ScreenshotGestureAction.OPEN_ALL_APPS),
            )
        } else {
            null
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
            rows = GesturePickerRowBuilder().build(pickerItems(), listOfNotNull(launcherRouteItem())),
            selectedKey = current,
            onPicked = onPicked,
        ).showBoundTo(lifecycleOwner)
    }
}
