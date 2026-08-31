package com.sza.fastmediasorter.ui.settings.helpers

import androidx.fragment.app.DialogFragment
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.LauncherDesktopSwipeAction
import com.sza.fastmediasorter.domain.model.LauncherDesktopSwipeDirection
import com.sza.fastmediasorter.domain.model.ScreenshotGestureAction
import com.sza.fastmediasorter.ui.applaunchpanel.edit.AppPickerDialogFragment
import timber.log.Timber

/**
 * S2256: owns the target (app or address) of one launcher desktop swipe direction.
 *
 * The desktop swipes stored a target from the moment the action was picked, but nothing could show or
 * change it afterwards. This manager is the whole flow - open the right chooser for the assigned action,
 * write the result into that direction's payload, and read the current one back for the row - so the
 * host dialog stays a view host (CLAUDE.md Rule 3).
 */
class LauncherSwipePayloadPickerManager(
    private val host: DialogFragment,
    private val currentSettings: () -> AppSettings,
    private val updateSettings: (AppSettings) -> Unit,
) {

    private var pendingAppDirection: LauncherDesktopSwipeDirection? = null

    /**
     * The app picker reports back through the fragment result API, so the direction it was opened for
     * has to outlive the call. Registered once per view lifecycle by the host.
     */
    fun registerAppPickerListener() {
        host.childFragmentManager.setFragmentResultListener(REQUEST_KEY, host.viewLifecycleOwner) { _, bundle ->
            val packageName = bundle.getString(AppPickerDialogFragment.RESULT_PACKAGE).orEmpty()
            val direction = pendingAppDirection
            pendingAppDirection = null
            if (packageName.isNotEmpty() && direction != null) {
                writePayload(direction, packageName)
            }
        }
    }

    /** The target kind [action] needs, or null when it takes none - which is what hides the row. */
    fun targetKindOf(action: LauncherDesktopSwipeAction): TargetKind? =
        when ((action as? LauncherDesktopSwipeAction.EdgeGestureAction)?.action) {
            ScreenshotGestureAction.OPEN_APP -> TargetKind.APP
            ScreenshotGestureAction.OPEN_URL -> TargetKind.URL
            else -> null
        }

    fun payloadOf(direction: LauncherDesktopSwipeDirection): String =
        direction.payloadOf(currentSettings())

    /** Opens the chooser matching [action]; a no-op for an action that carries no target. */
    fun openTargetPicker(direction: LauncherDesktopSwipeDirection, action: LauncherDesktopSwipeAction) {
        Timber.d("S2256: swipe target flow %s kind=%s", direction, targetKindOf(action))
        when (targetKindOf(action)) {
            TargetKind.APP -> showAppPicker(direction)
            TargetKind.URL -> promptUrl(direction)
            null -> Unit
        }
    }

    private fun showAppPicker(direction: LauncherDesktopSwipeDirection) {
        pendingAppDirection = direction
        AppPickerDialogFragment.newInstance(REQUEST_KEY)
            .show(host.childFragmentManager, AppPickerDialogFragment.TAG)
    }

    private fun promptUrl(direction: LauncherDesktopSwipeDirection) {
        GestureUrlTargetDialog.show(
            host = host,
            current = payloadOf(direction),
            onSave = { writePayload(direction, it) },
        )
    }

    /**
     * Empties this direction's target, leaving the action bound to it untouched (the S1036 contract), and
     * answers with the settings just written - the settings flow has not emitted them yet, so a caller
     * re-rendering from [currentSettings] would still read the target being cleared.
     */
    fun clearTarget(direction: LauncherDesktopSwipeDirection): AppSettings = writePayload(direction, "")

    private fun writePayload(direction: LauncherDesktopSwipeDirection, value: String): AppSettings {
        val updated = direction.withPayload(currentSettings(), value)
        updateSettings(updated)
        return updated
    }

    /** Which chooser a direction's action needs, and so which label its target row carries. */
    enum class TargetKind { APP, URL }

    private companion object {
        const val REQUEST_KEY = "launcher_desktop_swipe_app_picker"
    }
}
