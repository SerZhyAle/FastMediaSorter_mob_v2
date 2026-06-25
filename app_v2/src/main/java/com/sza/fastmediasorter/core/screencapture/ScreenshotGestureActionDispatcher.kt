package com.sza.fastmediasorter.core.screencapture

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.sza.fastmediasorter.core.capability.CapabilityAvailability
import com.sza.fastmediasorter.core.share.SystemShareInvoker
import com.sza.fastmediasorter.domain.model.ScreenshotGestureAction
import com.sza.fastmediasorter.domain.model.ScreenshotGestureDirection
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.ui.applaunchpanel.AppLaunchPanelActivity
import com.sza.fastmediasorter.ui.player.standalone.PhotoVideoStandaloneActivity
import dagger.Lazy
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject

/**
 * Resolves a screenshot gesture direction to its configured action and runs the configured
 * post-capture route (open in player / draw / OCR-translate / share). Silent save is handled by the
 * capture services themselves; this dispatcher only adds the extra behaviour for non-silent actions.
 */
class ScreenshotGestureActionDispatcher @Inject constructor(
    private val settingsRepository: Lazy<SettingsRepository>,
    private val capabilityAvailability: CapabilityAvailability
) {

    /** Resolves the action configured for [direction]. Callers feed the result to [handlePreCaptureAction]. */
    suspend fun actionFor(direction: ScreenshotGestureDirection): ScreenshotGestureAction {
        val settings = settingsRepository.get().getSettings().first()
        return when (direction) {
            ScreenshotGestureDirection.DOWN -> settings.screenshotGestureActionDown
            ScreenshotGestureDirection.RIGHT -> settings.screenshotGestureActionRight
            ScreenshotGestureDirection.UP -> settings.screenshotGestureActionUp
        }
    }

    /**
     * Runs actions that need no screen capture and tells the caller whether to stop. Returns true when
     * [action] was fully handled here, so the caller skips consent/capture entirely:
     * [ScreenshotGestureAction.DO_NOT_USE] is a silent no-op; [ScreenshotGestureAction.OPEN_APP] brings
     * the app to the foreground (existing task reordered to front, preserving its state, or cold start).
     * Returns false for capture-backed actions, which proceed through the normal capture path.
     */
    fun handlePreCaptureAction(context: Context, action: ScreenshotGestureAction): Boolean = when (action) {
        ScreenshotGestureAction.DO_NOT_USE -> true
        ScreenshotGestureAction.OPEN_APP -> {
            launchApp(context)
            true
        }
        ScreenshotGestureAction.OPEN_PANEL -> {
            launchPanel(context)
            true
        }
        else -> false
    }

    private fun launchApp(context: Context) {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        if (intent == null) {
            Timber.w("ScreenshotGestureActionDispatcher: no launch intent for package")
            return
        }
        // getLaunchIntentForPackage returns a MAIN/LAUNCHER intent that already carries
        // FLAG_ACTIVITY_NEW_TASK; re-adding it is explicit because the caller is a Service with no task
        // of its own. Starting it reorders the app's existing default-affinity task to the front
        // (preserving its state) or cold-starts the app when no task exists.
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure { Timber.w(it, "ScreenshotGestureActionDispatcher: failed to launch app") }
    }

    private fun launchPanel(context: Context) {
        // The dispatcher runs in a Service with no task of its own, so the transparent panel host needs
        // FLAG_ACTIVITY_NEW_TASK. AppLaunchPanelActivity is singleTask + excludeFromRecents, so it floats
        // over the foreground app and does not linger as a separate recents entry.
        val intent = Intent(context, AppLaunchPanelActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure { Timber.w(it, "ScreenshotGestureActionDispatcher: failed to open app launch panel") }
    }

    /** Launches the route configured for [action]. No-op for silent/disabled; degrades to silent save when [savedUri] is null. */
    fun runPostSave(context: Context, action: ScreenshotGestureAction, savedUri: Uri?) {
        when (action) {
            // OPEN_APP / OPEN_PANEL / DO_NOT_USE never reach here (handled pre-capture), kept for when-exhaustiveness.
            ScreenshotGestureAction.SILENT_SCREENSHOT,
            ScreenshotGestureAction.OPEN_APP,
            ScreenshotGestureAction.OPEN_PANEL,
            ScreenshotGestureAction.DO_NOT_USE -> return

            ScreenshotGestureAction.OPEN_IN_PLAYER -> openInViewer(context, savedUri, autoAction = null)
            ScreenshotGestureAction.OPEN_IN_DRAW ->
                openInViewer(context, savedUri, autoAction = PhotoVideoStandaloneActivity.AUTO_ACTION_DRAW)

            ScreenshotGestureAction.OCR_TRANSLATE -> {
                val autoAction = if (capabilityAvailability.isTranslationAvailable()) {
                    PhotoVideoStandaloneActivity.AUTO_ACTION_TRANSLATE
                } else {
                    Timber.i("ScreenshotGestureActionDispatcher: translation unavailable, opening player instead")
                    null
                }
                openInViewer(context, savedUri, autoAction)
            }

            ScreenshotGestureAction.SEND_TO_RECIPIENTS -> {
                openInViewer(context, savedUri, autoAction = PhotoVideoStandaloneActivity.AUTO_ACTION_SEND_TO)
            }

            ScreenshotGestureAction.CROP_AND_SHARE -> {
                openInViewer(context, savedUri, autoAction = PhotoVideoStandaloneActivity.AUTO_ACTION_CROP_AND_SHARE)
            }

            ScreenshotGestureAction.SHARE -> {
                if (savedUri == null) {
                    Timber.i("ScreenshotGestureActionDispatcher: SHARE skipped, no saved URI")
                    return
                }
                SystemShareInvoker.invokeFiles(context, listOf(savedUri), mime = MIME_PNG)
            }
        }
    }

    private fun openInViewer(context: Context, savedUri: Uri?, autoAction: String?) {
        if (savedUri == null) {
            Timber.i("ScreenshotGestureActionDispatcher: open skipped, no saved URI")
            return
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(savedUri, MIME_PNG)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            setClass(context, PhotoVideoStandaloneActivity::class.java)
            if (autoAction != null) {
                putExtra(PhotoVideoStandaloneActivity.EXTRA_AUTO_ACTION, autoAction)
            }
        }
        runCatching { context.startActivity(intent) }
            .onFailure { Timber.w(it, "ScreenshotGestureActionDispatcher: failed to open viewer") }
    }

    private companion object {
        private const val MIME_PNG = "image/png"
    }
}
