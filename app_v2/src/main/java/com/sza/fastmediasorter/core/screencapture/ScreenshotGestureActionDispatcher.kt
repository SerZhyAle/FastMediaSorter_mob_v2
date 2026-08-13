package com.sza.fastmediasorter.core.screencapture

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import com.sza.fastmediasorter.core.capability.CapabilityAvailability
import com.sza.fastmediasorter.core.screencapture.gesture.DeviceActionHandler
import com.sza.fastmediasorter.core.screencapture.gesture.GestureAccessibilityActions
import com.sza.fastmediasorter.core.screencapture.gesture.LaunchActionHandler
import com.sza.fastmediasorter.core.screencapture.gesture.MediaActionHandler
import com.sza.fastmediasorter.core.share.SystemShareInvoker
import com.sza.fastmediasorter.domain.model.ScreenshotGestureAction
import com.sza.fastmediasorter.domain.model.ScreenshotGestureDirection
import com.sza.fastmediasorter.domain.model.ScreenshotGestureZone
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.ui.applaunchpanel.AppLaunchPanelActivity
import com.sza.fastmediasorter.ui.cameraocr.CameraOcrTranslateActivity
import com.sza.fastmediasorter.ui.player.standalone.PhotoVideoStandaloneActivity
import com.sza.fastmediasorter.widget.CameraLaunchActivity
import com.sza.fastmediasorter.widget.PhotoCaptureLaunchActivity
import com.sza.fastmediasorter.widget.QuickAudioRecorderActivity
import com.sza.fastmediasorter.widget.ScreenRecordingLaunchActivity
import dagger.Lazy
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

/**
 * Resolves a screenshot gesture direction to its configured action and runs the configured
 * post-capture route (open in player / draw / OCR-translate / share). Silent save is handled by the
 * capture services themselves; this dispatcher only adds the extra behaviour for non-silent actions.
 */
class ScreenshotGestureActionDispatcher @Inject constructor(
    private val settingsRepository: Lazy<SettingsRepository>,
    private val capabilityAvailability: CapabilityAvailability,
    private val deviceActionHandler: DeviceActionHandler,
    private val mediaActionHandler: MediaActionHandler,
    private val launchActionHandler: LaunchActionHandler,
    // S1038: empty on every flavor except noLegal, which contributes the accessibility-backed performer.
    private val accessibilityActions: Set<@JvmSuppressWildcards GestureAccessibilityActions>,
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

    /**
     * S1162: every slot of [zones] resolved from a single settings snapshot.
     *
     * The gesture hint has to render the moment a band is touched, and [actionFor] suspends - a touch
     * handler cannot wait on it. Resolving twelve slots through [actionFor] would also re-read the
     * settings twelve times for one overlay show.
     */
    suspend fun actionsForZones(
        zones: Set<ScreenshotGestureZone>
    ): Map<ScreenshotGestureZone, Map<ScreenshotGestureDirection, ScreenshotGestureAction>> {
        if (zones.isEmpty()) return emptyMap()
        val settings = settingsRepository.get().getSettings().first()
        return zones.associateWith { zone ->
            ScreenshotGestureDirection.entries.associateWith { direction ->
                settings.screenshotGestureAction(zone, direction)
            }
        }
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
     * [ScreenshotGestureAction.DO_NOT_USE] is a silent no-op; [ScreenshotGestureAction.OPEN_APP] launches
     * the app chosen for this slot, or brings FastMediaSorter to the foreground when none is chosen.
     * Returns false for capture-backed actions, which proceed through the normal capture path.
     * S1038: [zone] + [direction] identify the slot so OPEN_URL can resolve its per-slot payload address.
     * S1036: OPEN_APP reads the same per-slot payload, holding a package name instead of an address.
     */
    suspend fun handlePreCaptureAction(
        context: Context,
        action: ScreenshotGestureAction,
        zone: ScreenshotGestureZone,
        direction: ScreenshotGestureDirection,
    ): Boolean = when (action) {
        ScreenshotGestureAction.DO_NOT_USE -> true
        ScreenshotGestureAction.OPEN_APP -> {
            launchSelectedApp(context, payloadFor(zone, direction))
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
            // S1042: route straight to the unified OCR/translate crop screen (camera source), giving the
            // same crop + language + OCR/translate UI as photos. Fall back to a plain photo save where
            // translation is unavailable for this flavor.
            if (capabilityAvailability.isTranslationAvailable()) {
                launchOcrCaptureFlow(context)
            } else {
                Timber.i("ScreenshotGestureActionDispatcher: translation unavailable, saving photo")
                launchPhotoCapture(context, autoAction = null)
            }
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
        // S1038: device-control + media actions run before (and instead of) any capture. Each handler
        // owns its action set and returns true, so the gesture skips consent/capture entirely.
        ScreenshotGestureAction.TOGGLE_FLASHLIGHT,
        ScreenshotGestureAction.BRIGHTNESS_MAX,
        ScreenshotGestureAction.BRIGHTNESS_NORMAL -> deviceActionHandler.handle(context, action)
        ScreenshotGestureAction.VOLUME_UP,
        ScreenshotGestureAction.VOLUME_DOWN,
        ScreenshotGestureAction.VOLUME_MUTE,
        ScreenshotGestureAction.MEDIA_PLAY_PAUSE,
        ScreenshotGestureAction.MEDIA_NEXT,
        ScreenshotGestureAction.MEDIA_PREV -> mediaActionHandler.handle(context, action)
        // S1038: launch/intent actions. OPEN_URL alone needs the per-slot payload; the rest ignore it.
        ScreenshotGestureAction.OPEN_URL ->
            launchActionHandler.handle(context, action, payloadFor(zone, direction))
        ScreenshotGestureAction.OPEN_ASSISTANT,
        ScreenshotGestureAction.OPEN_GEMINI,
        ScreenshotGestureAction.CREATE_KEEP_NOTE,
        ScreenshotGestureAction.SET_ALARM,
        ScreenshotGestureAction.SET_TIMER,
        ScreenshotGestureAction.NEW_CALENDAR_EVENT -> launchActionHandler.handle(context, action, payload = "")
        // S1038: SYSTEM actions run through the noLegal accessibility seam. Always handled (skip capture);
        // the performer degrades internally when the service is off, and the set is empty on other flavors.
        ScreenshotGestureAction.OPEN_NOTIFICATION_SHADE,
        ScreenshotGestureAction.OPEN_QUICK_SETTINGS,
        ScreenshotGestureAction.LOCK_SCREEN,
        ScreenshotGestureAction.TOGGLE_SPLIT_SCREEN,
        ScreenshotGestureAction.PREVIOUS_APP -> {
            val performer = accessibilityActions.firstOrNull()
            if (performer != null) {
                performer.perform(action)
            } else {
                Timber.w("ScreenshotGestureActionDispatcher: %s unavailable on this build", action)
            }
            true
        }
        else -> false
    }

    // S1038: resolves the per-slot payload from the persisted settings. The value is deliberately
    // untyped: OPEN_URL reads it as a target address, S1036's OPEN_APP reads it as a package name.
    private suspend fun payloadFor(
        zone: ScreenshotGestureZone,
        direction: ScreenshotGestureDirection,
    ): String = settingsRepository.get().getSettings().first().screenshotGesturePayload(zone, direction)

    /**
     * S1036: launches the package chosen for the triggering slot. A blank payload means the user chose
     * no app, and an unresolvable one means the chosen app was removed or disabled since; both degrade
     * to [launchApp] so the gesture keeps doing something visible instead of silently failing.
     */
    private fun launchSelectedApp(context: Context, packageName: String) {
        if (packageName.isBlank()) {
            launchApp(context)
            return
        }
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (intent == null) {
            Timber.i("ScreenshotGestureActionDispatcher: no launch intent for %s, opening own app", packageName)
            launchApp(context)
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure {
                Timber.w(it, "ScreenshotGestureActionDispatcher: failed to launch %s", packageName)
                launchApp(context)
            }
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
            // S1038: device-control + media actions are pre-capture (handled in handlePreCaptureAction),
            // so they never reach runPostSave; listed here only to keep the when total.
            ScreenshotGestureAction.TOGGLE_FLASHLIGHT,
            ScreenshotGestureAction.BRIGHTNESS_MAX,
            ScreenshotGestureAction.BRIGHTNESS_NORMAL,
            ScreenshotGestureAction.VOLUME_UP,
            ScreenshotGestureAction.VOLUME_DOWN,
            ScreenshotGestureAction.VOLUME_MUTE,
            ScreenshotGestureAction.MEDIA_PLAY_PAUSE,
            ScreenshotGestureAction.MEDIA_NEXT,
            ScreenshotGestureAction.MEDIA_PREV,
            // S1038: launch/intent actions are pre-capture (handled in handlePreCaptureAction); listed
            // here only to keep the when total.
            ScreenshotGestureAction.OPEN_ASSISTANT,
            ScreenshotGestureAction.OPEN_GEMINI,
            ScreenshotGestureAction.CREATE_KEEP_NOTE,
            ScreenshotGestureAction.OPEN_URL,
            ScreenshotGestureAction.SET_ALARM,
            ScreenshotGestureAction.SET_TIMER,
            ScreenshotGestureAction.NEW_CALENDAR_EVENT,
            // S1038: SYSTEM actions are pre-capture (handled in handlePreCaptureAction); listed here only
            // to keep the when total.
            ScreenshotGestureAction.OPEN_NOTIFICATION_SHADE,
            ScreenshotGestureAction.OPEN_QUICK_SETTINGS,
            ScreenshotGestureAction.LOCK_SCREEN,
            ScreenshotGestureAction.TOGGLE_SPLIT_SCREEN,
            ScreenshotGestureAction.PREVIOUS_APP,
            // S1042: OCR_TRANSLATE is handled pre-save by the capture service (stages the raw frame to a
            // temp file and calls launchOcrCropFlow, so only the cropped result reaches the gallery); it
            // never arrives here. Grouped with the no-ops to keep the when total.
            ScreenshotGestureAction.OCR_TRANSLATE,
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

    /** S1042: open the unified OCR/translate crop screen with the camera as source (take-photo action). */
    private fun launchOcrCaptureFlow(context: Context) {
        val intent = CameraOcrTranslateActivity.createIntent(context)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure { Timber.w(it, "ScreenshotGestureActionDispatcher: failed to open OCR capture flow") }
    }

    /**
     * S1042: open the unified OCR/translate crop screen over an already-captured [sourceFile]
     * (a staged screenshot). Called by the capture service for the OCR_TRANSLATE screenshot action.
     */
    fun launchOcrCropFlow(context: Context, sourceFile: File) {
        val intent = CameraOcrTranslateActivity.createIntent(context, sourceFile.absolutePath)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure { Timber.w(it, "ScreenshotGestureActionDispatcher: failed to open OCR crop flow") }
    }

    /**
     * S1042: writes [bitmap] to a private app-cache PNG so the OCR crop flow can decode it without the
     * raw screenshot touching the gallery. Shared by both capture backends (MediaProjection service on
     * standard, accessibility service on noLegal). Returns the file, or null on failure; the crop flow
     * deletes it after decode. Compress off the main thread (callers wrap in Dispatchers.IO).
     */
    fun stageOcrSourceFile(context: Context, bitmap: Bitmap): File? = runCatching {
        val dir = File(context.cacheDir, "ocr_capture").apply { mkdirs() }
        val file = File(dir, "ocr_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, OCR_SOURCE_PNG_QUALITY, out)
            out.flush()
        }
        file
    }.getOrElse {
        Timber.e(it, "ScreenshotGestureActionDispatcher: stageOcrSourceFile failed")
        null
    }

    private companion object {
        private const val MIME_PNG = "image/png"

        // Lossless PNG for the temp OCR source frame - quality is ignored for PNG but required by the API.
        private const val OCR_SOURCE_PNG_QUALITY = 100
    }
}
