package com.sza.fastmediasorter.core.panel

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import com.sza.fastmediasorter.core.game.GameLaunchIntents
import com.sza.fastmediasorter.ui.applaunchpanel.AppLaunchPanelActivity
import com.sza.fastmediasorter.ui.browse.BrowseActivity
import com.sza.fastmediasorter.ui.calculator.CalculatorActivity
import com.sza.fastmediasorter.ui.cameraocr.CameraOcrTranslateActivity
import com.sza.fastmediasorter.ui.flashlight.FlashlightToggleActivity
import com.sza.fastmediasorter.ui.flashlight.FrontFlashlightActivity
import com.sza.fastmediasorter.ui.flashlight.WaterFlashlightActivity
import com.sza.fastmediasorter.ui.main.MainActivity
import com.sza.fastmediasorter.ui.mirror.MirrorActivity
import com.sza.fastmediasorter.ui.networkmonitor.NetworkMonitorActivity
import com.sza.fastmediasorter.ui.networkmonitor.NetworkMonitorSection
import com.sza.fastmediasorter.ui.networkmonitor.putNetworkMonitorLauncherOrigin
import com.sza.fastmediasorter.ui.player.standalone.PhotoVideoStandaloneActivity
import com.sza.fastmediasorter.ui.settings.SettingsActivity
import com.sza.fastmediasorter.ui.sos.SosActivity
import com.sza.fastmediasorter.ui.stopwatch.StopwatchActivity
import com.sza.fastmediasorter.ui.streams.StreamsActivity
import com.sza.fastmediasorter.ui.systeminfo.SystemInfoActivity
import com.sza.fastmediasorter.ui.tourist.TouristInfoActivity
import com.sza.fastmediasorter.ui.wear.WearCompanionActivity
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
@Suppress("TooManyFunctions") // S2997: one builder per route; grows with each sub-program
object AppLaunchPanelRouteIntents {

    // S1103: a launcher cell that opens the quick-access panel overlay itself.
    fun appLaunchPanel(context: Context): Intent =
        Intent(context, AppLaunchPanelActivity::class.java).withPanelFlags()

    fun calculator(context: Context): Intent =
        Intent(context, CalculatorActivity::class.java).withPanelFlags()

    // S1856: the calculator's own toggle lives on the Operations tab, next to the network monitor's
    // and the flashlight's - a disabled route opens its setting instead of dead-launching.
    fun calculatorSettings(context: Context): Intent =
        Intent(context, SettingsActivity::class.java)
            .putExtra(SettingsActivity.EXTRA_INITIAL_TAB, SettingsActivity.TAB_OPERATIONS)
            .withPanelFlags()

    fun stopwatch(context: Context): Intent = StopwatchActivity.createIntent(context).withPanelFlags()

    // S1411 ADR-5: the stopwatch's switch sits on the Operations tab beside the calculator's, so a
    // disabled route opens that tab exactly as the calculator's does.
    fun stopwatchSettings(context: Context): Intent =
        Intent(context, SettingsActivity::class.java)
            .putExtra(SettingsActivity.EXTRA_INITIAL_TAB, SettingsActivity.TAB_OPERATIONS)
            .withPanelFlags()

    fun networkMonitor(
        context: Context,
        section: NetworkMonitorSection = NetworkMonitorSection.Summary,
    ): Intent = NetworkMonitorActivity.createIntent(context, section)
        .putNetworkMonitorLauncherOrigin()
        .withPanelFlags()

    fun networkMonitor(context: Context, sectionKey: String): Intent =
        networkMonitor(context, NetworkMonitorSection.fromKey(sectionKey))

    fun networkMonitorSettings(context: Context): Intent =
        Intent(context, SettingsActivity::class.java)
            .putExtra(SettingsActivity.EXTRA_INITIAL_TAB, SettingsActivity.TAB_OPERATIONS)
            .withPanelFlags()

    // S1733: a transparent host of ours, like the flashlight - there is no widget trampoline to reuse,
    // because system information had no entry point outside the settings screen before this ticket.
    fun systemInfo(context: Context): Intent =
        SystemInfoActivity.createIntent(context).withPanelFlags()

    fun systemInfoSettings(context: Context): Intent =
        Intent(context, SettingsActivity::class.java)
            .putExtra(SettingsActivity.EXTRA_INITIAL_TAB, SettingsActivity.TAB_OPERATIONS)
            .withPanelFlags()

    // S2922: Tourist dashboard subprogram.
    fun touristInfo(context: Context): Intent =
        TouristInfoActivity.createIntent(context).withPanelFlags()

    // S2997: the tourist toggle sits on the Operations tab beside the others, so a disabled route opens
    // that tab exactly as the calculator's does.
    fun touristSettings(context: Context): Intent =
        Intent(context, SettingsActivity::class.java)
            .putExtra(SettingsActivity.EXTRA_INITIAL_TAB, SettingsActivity.TAB_OPERATIONS)
            .withPanelFlags()

    // S1883: the same host window the settings button and the programs entry open, so all four
    // surfaces are one behaviour rather than several that resemble each other.
    fun wearCompanion(context: Context): Intent =
        WearCompanionActivity.createIntent(context).withPanelFlags()

    // Unlike the routes above, this one names its section: the companion's settings are a group on the
    // Operations tab, and landing at the top of that tab would leave the user to hunt for it.
    fun wearCompanionSettings(context: Context): Intent =
        Intent(context, SettingsActivity::class.java)
            .putExtra(SettingsActivity.EXTRA_INITIAL_TAB, SettingsActivity.TAB_OPERATIONS)
            .putExtra(SettingsActivity.EXTRA_EXPAND_SECTION, SettingsActivity.SECTION_WEAR)
            .withPanelFlags()

    // S2881: the two watch-listen routes are absent from this object on purpose. The object sits at
    // detekt's TooManyFunctions ceiling, and the trampoline's own createIntent already carries the
    // NEW_TASK flag - so the catalog's route rows build their intent straight from the activity.

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

    fun broadcast(context: Context): Intent =
        Intent(context, com.sza.fastmediasorter.ui.broadcast.BroadcastEntryActivity::class.java)
            .setAction(com.sza.fastmediasorter.ui.broadcast.BroadcastEntryActivity.ACTION_OPEN_BROADCAST_ENTRY)
            .withPanelFlags()

    fun broadcastSettings(context: Context): Intent =
        Intent(context, SettingsActivity::class.java)
            .putExtra(SettingsActivity.EXTRA_INITIAL_TAB, SettingsActivity.TAB_OPERATIONS)
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

    fun physicalFlashlight(context: Context): Intent =
        Intent(context, FlashlightToggleActivity::class.java).withPanelFlags()

    // S2516: the water flashlight shares the front flashlight's settings tab, so its settings intent
    // is that one - both switches live in the same operations section.
    fun waterFlashlight(context: Context): Intent =
        WaterFlashlightActivity.createIntent(context).withPanelFlags()

    // S3216: the distress signal is our own Activity, like the flashlights above - no widget
    // trampoline is reused, because the program had no entry point before this ticket.
    fun sos(context: Context): Intent =
        SosActivity.createIntent(context).withPanelFlags()

    // S2211: black screen as an autonomous sub-program.
    fun blackScreen(context: Context): Intent =
        Intent(context, com.sza.fastmediasorter.ui.blackscreen.BlackScreenActivity::class.java).withPanelFlags()

    // S1924: the mirror is our own Activity, like the front flashlight - no widget trampoline reused.
    fun mirror(context: Context): Intent =
        MirrorActivity.createIntent(context).withPanelFlags()

    fun mirrorSettings(context: Context): Intent =
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
