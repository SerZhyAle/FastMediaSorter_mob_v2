package com.sza.fastmediasorter.core.screencapture

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.sza.fastmediasorter.core.capability.CapabilityAvailability
import com.sza.fastmediasorter.core.share.SystemShareInvoker
import com.sza.fastmediasorter.domain.model.ScreenshotGestureAction
import com.sza.fastmediasorter.domain.model.ScreenshotGestureDirection
import com.sza.fastmediasorter.domain.repository.SettingsRepository
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

    /** Pre-capture gate: callers skip capture entirely when this returns [ScreenshotGestureAction.DO_NOT_USE]. */
    suspend fun actionFor(direction: ScreenshotGestureDirection): ScreenshotGestureAction {
        val settings = settingsRepository.get().getSettings().first()
        return when (direction) {
            ScreenshotGestureDirection.DOWN -> settings.screenshotGestureActionDown
            ScreenshotGestureDirection.RIGHT -> settings.screenshotGestureActionRight
            ScreenshotGestureDirection.UP -> settings.screenshotGestureActionUp
        }
    }

    /** Launches the route configured for [action]. No-op for silent/disabled; degrades to silent save when [savedUri] is null. */
    fun runPostSave(context: Context, action: ScreenshotGestureAction, savedUri: Uri?) {
        when (action) {
            ScreenshotGestureAction.SILENT_SCREENSHOT,
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
