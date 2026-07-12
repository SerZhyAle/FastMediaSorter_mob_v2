package com.sza.fastmediasorter.core.screencapture

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.sza.fastmediasorter.core.capability.CapabilityAvailability
import com.sza.fastmediasorter.core.share.SystemShareInvoker
import com.sza.fastmediasorter.domain.model.ScreenshotGestureAction
import com.sza.fastmediasorter.domain.model.ScreenshotGestureDirection
import com.sza.fastmediasorter.domain.model.ScreenshotGestureZone
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.ui.applaunchpanel.AppLaunchPanelActivity
import com.sza.fastmediasorter.ui.player.standalone.PhotoVideoStandaloneActivity
import com.sza.fastmediasorter.widget.CameraLaunchActivity
import com.sza.fastmediasorter.widget.PhotoCaptureLaunchActivity
import com.sza.fastmediasorter.widget.QuickAudioRecorderActivity
import com.sza.fastmediasorter.widget.ScreenRecordingLaunchActivity
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

    /**
     * Resolves the action configured for [zone] + [direction] (one of the 12 edge-band slots, S0847).
     * Callers feed the result to [handlePreCaptureAction].
     */
    suspend fun actionFor(
        zone: ScreenshotGestureZone,
        direction: ScreenshotGestureDirection
    ): ScreenshotGestureAction {
        val settings = settingsRepository.get().getSettings().first()
        return settings.screenshotGestureAction(zone, direction)
    }

    /** S0847: the set of edge bands currently enabled - the overlay host shows a strip only for these. */
    suspend fun enabledZones(): Set<ScreenshotGestureZone> {
        val settings = settingsRepository.get().getSettings().first()
        return ScreenshotGestureZone.entries
            .filterTo(mutableSetOf()) { settings.screenshotGestureZoneEnabled(it) }
    }

    /** S1008: the set of enabled bands whose grey strip guide should be visible (enabled AND strip-visible). */
    suspend fun stripVisibleZones(): Set<ScreenshotGestureZone> {
        val settings = settingsRepository.get().getSettings().first()
        return ScreenshotGestureZone.entries.filterTo(mutableSetOf()) {
            settings.screenshotGestureZoneEnabled(it) && settings.screenshotGestureZoneStripVisible(it)
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
        ScreenshotGestureAction.LAUNCH_CAMERA -> {
            launchCamera(context)
            true
        }
        ScreenshotGestureAction.TAKE_PHOTO -> {
            launchPhotoCapture(context, autoAction = null)
            true
        }
        ScreenshotGestureAction.TAKE_PHOTO_SEND_TO -> {
            launchPhotoCapture(context, PhotoVideoStandaloneActivity.AUTO_ACTION_SEND_TO)
            true
        }
        ScreenshotGestureAction.TAKE_PHOTO_EDIT -> {
            launchPhotoCapture(context, PhotoVideoStandaloneActivity.AUTO_ACTION_DRAW)
            true
        }
        ScreenshotGestureAction.TAKE_PHOTO_OCR_TRANSLATE -> {
            val action = if (capabilityAvailability.isTranslationAvailable()) {
                PhotoVideoStandaloneActivity.AUTO_ACTION_TRANSLATE
            } else {
                Timber.i("ScreenshotGestureActionDispatcher: translation unavailable, saving photo")
                null
            }
            launchPhotoCapture(context, action)
            true
        }
        ScreenshotGestureAction.START_VIDEO_RECORDING -> {
            launchVideoCamera(context)
            true
        }
        ScreenshotGestureAction.START_AUDIO_RECORDING -> {
            launchAudioRecorder(context)
            true
        }
        ScreenshotGestureAction.START_SCREEN_RECORDING -> {
            launchScreenRecording(context)
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

    private fun launchCamera(context: Context) {
        // S0788: reuse the widget's no-UI camera trampoline - it resolves the default capture
        // destination, handles the CAMERA permission and opens the in-app camera. The dispatcher runs in
        // a Service with no task of its own, so the transparent launcher needs FLAG_ACTIVITY_NEW_TASK.
        val intent = Intent(context, CameraLaunchActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure { Timber.w(it, "ScreenshotGestureActionDispatcher: failed to launch camera") }
    }

    private fun launchPhotoCapture(context: Context, autoAction: String?) {
        // S0790-S0794: transparent trampoline auto-captures a photo, then saves+toasts (autoAction null)
        // or routes it into the standalone viewer (send-to / draw editor / OCR-translate).
        val intent = PhotoCaptureLaunchActivity.intent(context, autoAction)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure { Timber.w(it, "ScreenshotGestureActionDispatcher: failed to launch photo capture") }
    }

    private fun launchVideoCamera(context: Context) {
        // S0795: reuse the S0788 camera trampoline but force video mode - the user frames and taps
        // record, then the clip is saved to the public Movies folder like the camera widget.
        val intent = CameraLaunchActivity.videoIntent(context)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure { Timber.w(it, "ScreenshotGestureActionDispatcher: failed to launch video camera") }
    }

    private fun launchAudioRecorder(context: Context) {
        // S0796: reuse the quick voice-recorder trampoline (S0349) - it gates RECORD_AUDIO and toggles
        // the recording foreground service. Explicit same-app launch from the dispatcher's Service.
        val intent = Intent(context, QuickAudioRecorderActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure { Timber.w(it, "ScreenshotGestureActionDispatcher: failed to start audio recorder") }
    }

    private fun launchScreenRecording(context: Context) {
        // S0797: reuse the screen-recording controller via a transparent trampoline that owns the
        // FragmentActivity + permission launchers controller.launch() requires. The action is offered
        // only where the capture engine is present, so the trampoline no-ops on other flavors.
        val intent = Intent(context, ScreenRecordingLaunchActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure { Timber.w(it, "ScreenshotGestureActionDispatcher: failed to launch screen recording") }
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
            // Pre-capture actions never reach here (handled in handlePreCaptureAction), kept for when-exhaustiveness.
            ScreenshotGestureAction.SILENT_SCREENSHOT,
            ScreenshotGestureAction.OPEN_APP,
            ScreenshotGestureAction.OPEN_PANEL,
            ScreenshotGestureAction.LAUNCH_CAMERA,
            ScreenshotGestureAction.TAKE_PHOTO,
            ScreenshotGestureAction.TAKE_PHOTO_SEND_TO,
            ScreenshotGestureAction.TAKE_PHOTO_EDIT,
            ScreenshotGestureAction.TAKE_PHOTO_OCR_TRANSLATE,
            ScreenshotGestureAction.START_VIDEO_RECORDING,
            ScreenshotGestureAction.START_AUDIO_RECORDING,
            ScreenshotGestureAction.START_SCREEN_RECORDING,
            ScreenshotGestureAction.DO_NOT_USE -> return

            ScreenshotGestureAction.OPEN_IN_PLAYER -> openInViewer(context, savedUri, autoAction = null)
            ScreenshotGestureAction.OPEN_IN_DRAW ->
                openInViewer(
                    context,
                    savedUri,
                    autoAction = PhotoVideoStandaloneActivity.AUTO_ACTION_DRAW,
                    // S0837: this is a just-captured screenshot; a default save inside the draw editor
                    // should overwrite it in place (keeping a separate copy only on explicit "Save as..").
                    overwriteSourceOnSave = true,
                )

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

    private fun openInViewer(
        context: Context,
        savedUri: Uri?,
        autoAction: String?,
        overwriteSourceOnSave: Boolean = false,
    ) {
        if (savedUri == null) {
            Timber.i("ScreenshotGestureActionDispatcher: open skipped, no saved URI")
            return
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(savedUri, MIME_PNG)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            // S0837: the draw editor overwrites the source on a default save, so grant write too.
            if (overwriteSourceOnSave) addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            setClass(context, PhotoVideoStandaloneActivity::class.java)
            if (autoAction != null) {
                putExtra(PhotoVideoStandaloneActivity.EXTRA_AUTO_ACTION, autoAction)
            }
            if (overwriteSourceOnSave) {
                putExtra(PhotoVideoStandaloneActivity.EXTRA_DRAW_OVERWRITE_SOURCE, true)
            }
        }
        runCatching { context.startActivity(intent) }
            .onFailure { Timber.w(it, "ScreenshotGestureActionDispatcher: failed to open viewer") }
    }

    private companion object {
        private const val MIME_PNG = "image/png"
    }
}
