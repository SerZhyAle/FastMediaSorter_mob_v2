package com.sza.fastmediasorter.core.panel

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import com.sza.fastmediasorter.core.game.GameLaunchIntents
import com.sza.fastmediasorter.ui.applaunchpanel.AppLaunchPanelActivity
import com.sza.fastmediasorter.ui.browse.BrowseActivity
import com.sza.fastmediasorter.ui.calculator.CalculatorActivity
import com.sza.fastmediasorter.ui.cameraocr.CameraOcrTranslateActivity
import com.sza.fastmediasorter.ui.flashlight.FrontFlashlightActivity
import com.sza.fastmediasorter.ui.main.MainActivity
import com.sza.fastmediasorter.ui.networkmonitor.NetworkMonitorActivity
import com.sza.fastmediasorter.ui.networkmonitor.NetworkMonitorSection
import com.sza.fastmediasorter.ui.player.standalone.PhotoVideoStandaloneActivity
import com.sza.fastmediasorter.ui.settings.SettingsActivity
import com.sza.fastmediasorter.ui.streams.StreamsActivity
import com.sza.fastmediasorter.widget.CameraLaunchActivity
import com.sza.fastmediasorter.widget.CameraQuickCaptureActivity
import com.sza.fastmediasorter.widget.CameraQuickCaptureLaunchManager
import com.sza.fastmediasorter.widget.LinkDownloadLaunchActivity
import com.sza.fastmediasorter.widget.PhotoCaptureLaunchActivity
import com.sza.fastmediasorter.widget.QuickAudioRecorderActivity
import com.sza.fastmediasorter.widget.ScreenRecordingLaunchActivity

/**
 * Builds the launch [Intent] for each panel internal route, reusing the exact entry points the
 * home-screen widgets already use (strategic S0663 ADR-1) - no new navigation is introduced here.
 * Every intent gets [Intent.FLAG_ACTIVITY_NEW_TASK], matching the existing panel launch path.
 */
object AppLaunchPanelRouteIntents {

    // S1103: a launcher cell that opens the quick-access panel overlay itself.
    fun appLaunchPanel(context: Context): Intent =
        Intent(context, AppLaunchPanelActivity::class.java).withPanelFlags()

    fun calculator(context: Context): Intent =
        Intent(context, CalculatorActivity::class.java).withPanelFlags()

    fun networkMonitor(
        context: Context,
        section: NetworkMonitorSection = NetworkMonitorSection.Summary,
    ): Intent = NetworkMonitorActivity.createIntent(context, section).withPanelFlags()

    fun networkMonitor(context: Context, sectionKey: String): Intent =
        networkMonitor(context, NetworkMonitorSection.fromKey(sectionKey))

    fun networkMonitorSettings(context: Context): Intent =
        Intent(context, SettingsActivity::class.java)
            .putExtra(SettingsActivity.EXTRA_INITIAL_TAB, SettingsActivity.TAB_OPERATIONS)
            .withPanelFlags()

    fun game(context: Context): Intent =
        GameLaunchIntents.game(context).withPanelFlags()

    fun ocr(context: Context): Intent =
        CameraOcrTranslateActivity.createIntent(context).withPanelFlags()

    fun streams(context: Context): Intent =
        Intent(context, StreamsActivity::class.java).withPanelFlags()

    fun favorites(context: Context): Intent =
        Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_OPEN_FAVORITES, true)
            .withPanelFlags()

    fun resource(context: Context, resourceId: Long): Intent =
        BrowseActivity.createIntent(context, resourceId).withPanelFlags()

    fun quickCamera(context: Context): Intent =
        Intent(context, CameraQuickCaptureActivity::class.java).apply {
            action = CameraQuickCaptureActivity.ACTION_CAPTURE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, CameraQuickCaptureLaunchManager.PANEL_APP_WIDGET_ID)
        }.withPanelFlags()

    fun quickVoice(context: Context): Intent =
        Intent(context, QuickAudioRecorderActivity::class.java)
            .setAction(QuickAudioRecorderActivity.ACTION_TOGGLE)
            .withPanelFlags()

    fun screenRecording(context: Context): Intent =
        Intent(context, ScreenRecordingLaunchActivity::class.java).withPanelFlags()

    fun linkDownload(context: Context): Intent =
        Intent(context, LinkDownloadLaunchActivity::class.java).withPanelFlags()

    // S1796: the flashlight is a plain Activity of ours, so no widget trampoline is reused here.
    fun frontFlashlight(context: Context): Intent =
        FrontFlashlightActivity.createIntent(context).withPanelFlags()

    fun frontFlashlightSettings(context: Context): Intent =
        Intent(context, SettingsActivity::class.java)
            .putExtra(SettingsActivity.EXTRA_INITIAL_TAB, SettingsActivity.TAB_OPERATIONS)
            .withPanelFlags()

    // S0978: reuse the same standalone camera/photo trampolines the left-edge gesture dispatcher uses
    // (PhotoCaptureLaunchActivity auto-captures then routes; CameraLaunchActivity.videoIntent opens the
    // camera in video mode). The AUTO_ACTION_* constants match the gesture path's routing exactly.
    fun takePhotoSendTo(context: Context): Intent =
        PhotoCaptureLaunchActivity.intent(context, PhotoVideoStandaloneActivity.AUTO_ACTION_SEND_TO).withPanelFlags()

    fun takePhotoEdit(context: Context): Intent =
        PhotoCaptureLaunchActivity.intent(context, PhotoVideoStandaloneActivity.AUTO_ACTION_DRAW).withPanelFlags()

    // S1042: OCR/translate opens the unified crop + language + OCR/translate screen (camera source),
    // matching the gesture path - no longer a plain capture routed into the full-screen viewer.
    fun takePhotoOcrTranslate(context: Context): Intent =
        CameraOcrTranslateActivity.createIntent(context).withPanelFlags()

    fun startVideoRecording(context: Context): Intent =
        CameraLaunchActivity.videoIntent(context).withPanelFlags()

    // S1170: the five destinations the mechanical home-screen widgets fire that no route covered yet.
    // Each mirrors its provider's PendingIntent so a launcher desktop cell and the same widget on the
    // Android home screen land on the identical screen - action constants are referenced, never retyped.

    fun cameraPhotos(context: Context): Intent =
        Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_CAMERA_PHOTOS)
            .withWidgetEntryFlags()

    /**
     * Photo mode, i.e. [CameraLaunchActivity] without the force-video extra [startVideoRecording] sets.
     *
     * `CameraLaunchWidgetProvider` also stamps a `fms://cam-launch/<widgetId>` data URI on this intent,
     * purely so two pinned instances do not collapse onto one cached PendingIntent. A launcher cell has
     * no widget id and no PendingIntent, so there is nothing to keep distinct and the URI is omitted.
     */
    fun cameraLaunch(context: Context): Intent =
        Intent(context, CameraLaunchActivity::class.java)
            .setAction(CameraLaunchActivity.ACTION_LAUNCH)
            .withPanelFlags()

    fun continueReading(context: Context): Intent =
        Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_START_SLIDESHOW)
            .withWidgetEntryFlags()

    fun randomMusic(context: Context): Intent =
        Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_RANDOM_MUSIC)
            .withWidgetEntryFlags()

    fun scheduledTasks(context: Context): Intent =
        Intent(context, SettingsActivity::class.java)
            .putExtra(SettingsActivity.EXTRA_OPEN_SCHEDULED, true)
            .withWidgetEntryFlags()

    private fun Intent.withPanelFlags(): Intent = addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * S1170: the flags the widget providers use for their long-lived hosts (MainActivity, SettingsActivity).
     * `CLEAR_TOP` is what makes a second tap reach the running instance's `onNewIntent` and re-read the
     * action instead of stacking a duplicate - dropping it would silently change where the tap lands.
     */
    private fun Intent.withWidgetEntryFlags(): Intent =
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    /** Matches the key `FavoritesWidgetProvider` / `MainActivity` already agree on (S0134). */
    private const val EXTRA_OPEN_FAVORITES = "open_favorites"
}
