package com.sza.fastmediasorter.core.panel

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import com.sza.fastmediasorter.core.game.GameLaunchIntents
import com.sza.fastmediasorter.ui.browse.BrowseActivity
import com.sza.fastmediasorter.ui.calculator.CalculatorActivity
import com.sza.fastmediasorter.ui.cameraocr.CameraOcrTranslateActivity
import com.sza.fastmediasorter.ui.main.MainActivity
import com.sza.fastmediasorter.ui.player.standalone.PhotoVideoStandaloneActivity
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

    fun calculator(context: Context): Intent =
        Intent(context, CalculatorActivity::class.java).withPanelFlags()

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

    // S0978: reuse the same standalone camera/photo trampolines the left-edge gesture dispatcher uses
    // (PhotoCaptureLaunchActivity auto-captures then routes; CameraLaunchActivity.videoIntent opens the
    // camera in video mode). The AUTO_ACTION_* constants match the gesture path's routing exactly.
    fun takePhotoSendTo(context: Context): Intent =
        PhotoCaptureLaunchActivity.intent(context, PhotoVideoStandaloneActivity.AUTO_ACTION_SEND_TO).withPanelFlags()

    fun takePhotoEdit(context: Context): Intent =
        PhotoCaptureLaunchActivity.intent(context, PhotoVideoStandaloneActivity.AUTO_ACTION_DRAW).withPanelFlags()

    fun takePhotoOcrTranslate(context: Context): Intent =
        PhotoCaptureLaunchActivity.intent(context, PhotoVideoStandaloneActivity.AUTO_ACTION_TRANSLATE).withPanelFlags()

    fun startVideoRecording(context: Context): Intent =
        CameraLaunchActivity.videoIntent(context).withPanelFlags()

    private fun Intent.withPanelFlags(): Intent = addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Matches the key `FavoritesWidgetProvider` / `MainActivity` already agree on (S0134). */
    private const val EXTRA_OPEN_FAVORITES = "open_favorites"
}
