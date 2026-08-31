package com.sza.fastmediasorter.ui.launcher.gadget.di

import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.panel.InternalRouteCatalog
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand
import com.sza.fastmediasorter.ui.launcher.gadget.AudioNowPlayingGadget
import com.sza.fastmediasorter.ui.launcher.gadget.CameraQuickCaptureGadget
import com.sza.fastmediasorter.ui.launcher.gadget.FavoritesGadget
import com.sza.fastmediasorter.ui.launcher.gadget.HomeWidgetGadget
import com.sza.fastmediasorter.ui.launcher.gadget.LauncherGadget
import com.sza.fastmediasorter.ui.launcher.gadget.RandomPhotoFrameGadget
import com.sza.fastmediasorter.ui.launcher.gadget.ScheduledTasksGadget
import com.sza.fastmediasorter.ui.launcher.gadget.YouTubeGadget
import com.sza.fastmediasorter.ui.launcher.gadget.YouTubeMusicGadget
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * S1170: the launcher-desktop counterparts of the home-screen widget catalog, supplied as ONE binding.
 *
 * A collection rather than a parameter each: `LauncherGadgetRegistry` already takes five gadgets by
 * constructor, and adding fourteen more would put it at nineteen parameters - past detekt's
 * `constructorThreshold` of 10, and past the point where the list is readable.
 *
 * Qualified because the payload is `List<LauncherGadget>`, which is exactly the type the registry
 * itself deals in; without the qualifier a future unqualified list binding would silently satisfy this
 * injection point instead.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class HomeWidgetGadgets

@Module
@InstallIn(SingletonComponent::class)
object HomeWidgetGadgetModule {

    /**
     * Ten catalog widgets whose entire behaviour is "open one screen", plus the two that render a live
     * list, the one that drives the playback service, and the two that keep per-instance state - each
     * of those five carrying its own class. 10 + 2 + 1 + 2 = 15 of 15.
     *
     * S1930 closed the last two. `random_photo_frame` and `camera_quick_capture` are keyed on
     * `AppWidgetManager.EXTRA_APPWIDGET_ID` end to end, and a desktop cell has no widget id - so its
     * cell carries a launcher-minted token in its param instead, and the widget chain skips the calls
     * that would hand that number to the platform. Adding a third such widget is one entry here plus
     * one row in `ConfigurableWidgetCatalog`.
     *
     * Every span is the `targetCellWidth` / `targetCellHeight` the widget declares in its own
     * `appwidget-provider`, so a cell lands on the desktop the size its twin has on the Android home
     * screen (strategic §3). Those attributes, not the `minWidth`/`minHeight` dp pair, are the widget's
     * stated default size - the dp values are a resize floor and understate a list widget badly.
     */
    @Provides
    @Singleton
    @HomeWidgetGadgets
    fun provideHomeWidgetGadgets(
        favorites: FavoritesGadget,
        scheduledTasks: ScheduledTasksGadget,
        audioNowPlaying: AudioNowPlayingGadget,
        youtube: YouTubeGadget,
        youtubeMusic: YouTubeMusicGadget,
    ): List<LauncherGadget> = listOf(
        // The two list widgets: their own classes, because a live list is not a fixed command.
        favorites,
        scheduledTasks,
        // Transport buttons are service intents, which the command dispatcher cannot express.
        audioNowPlaying,
        youtube,
        youtubeMusic,
    ) + singleScreenWidgetGadgets() + configurableWidgetGadgets()

    private fun singleScreenWidgetGadgets(): List<LauncherGadget> =
        singleScreenWidgetGadgetsGroup1() + singleScreenWidgetGadgetsGroup2()

    private fun singleScreenWidgetGadgetsGroup1(): List<LauncherGadget> = listOf(
        HomeWidgetGadget(
            key = KEY_CALCULATOR,
            labelRes = R.string.widget_calculator_label,
            iconRes = R.drawable.ic_widget_calculator_accent,
            defaultSpanW = SPAN_SMALL,
            defaultSpanH = SPAN_SMALL,
            command = LauncherCellCommand.Feature(InternalRouteCatalog.KEY_CALCULATOR),
        ),
        HomeWidgetGadget(
            key = KEY_FRONT_FLASHLIGHT,
            labelRes = R.string.widget_front_flashlight_label,
            iconRes = R.drawable.ic_widget_front_flashlight_accent,
            defaultSpanW = SPAN_SMALL,
            defaultSpanH = SPAN_SMALL,
            command = LauncherCellCommand.Feature(InternalRouteCatalog.KEY_FRONT_FLASHLIGHT),
        ),
        HomeWidgetGadget(
            key = KEY_CAMERA_OCR_TRANSLATE,
            labelRes = R.string.widget_camera_ocr_translate_label,
            iconRes = R.drawable.ic_camera_ocr_translate,
            defaultSpanW = SPAN_SMALL,
            defaultSpanH = SPAN_SMALL,
            iconTintable = true,
            command = LauncherCellCommand.Feature(InternalRouteCatalog.KEY_OCR),
        ),
        // Two tap targets on the Android home screen (photos and OCR); one cell here, running the OCR
        // route the user picked it by name for. A second hidden target on one desktop cell would be
        // undiscoverable - nothing on the cell could show which half was tapped.
        HomeWidgetGadget(
            key = KEY_CAPTURE_OCR_PANEL,
            labelRes = R.string.widget_capture_ocr_panel_label,
            iconRes = R.drawable.ic_camera_ocr_translate,
            defaultSpanW = SPAN_MEDIUM,
            defaultSpanH = SPAN_MEDIUM,
            iconTintable = true,
            command = LauncherCellCommand.Feature(InternalRouteCatalog.KEY_OCR),
        ),
        HomeWidgetGadget(
            key = KEY_CAMERA_LAUNCH,
            labelRes = R.string.widget_camera_launch_label,
            iconRes = R.drawable.ic_widget_camera_launch_accent,
            defaultSpanW = SPAN_SMALL,
            defaultSpanH = SPAN_SMALL,
            command = LauncherCellCommand.Feature(InternalRouteCatalog.KEY_CAMERA_LAUNCH),
        ),
    )

    private fun singleScreenWidgetGadgetsGroup2(): List<LauncherGadget> = listOf(
        HomeWidgetGadget(
            key = KEY_CAMERA_PHOTOS,
            labelRes = R.string.widget_camera_photos_label,
            iconRes = R.drawable.ic_widget_camera_photos,
            defaultSpanW = SPAN_SMALL,
            defaultSpanH = SPAN_SMALL,
            iconTintable = true,
            command = LauncherCellCommand.Feature(InternalRouteCatalog.KEY_CAMERA_PHOTOS),
        ),
        HomeWidgetGadget(
            key = KEY_CONTINUE_READING,
            labelRes = R.string.widget_continue_reading_label,
            iconRes = R.drawable.ic_widget_continue_reading,
            defaultSpanW = SPAN_SMALL,
            defaultSpanH = SPAN_SMALL,
            iconTintable = true,
            command = LauncherCellCommand.Feature(InternalRouteCatalog.KEY_CONTINUE_READING),
        ),
        // The game route already models its own disabled state through the catalog's settingsIntent, so
        // a switched-off game opens the setting instead of dead-launching - no extra branch needed here.
        HomeWidgetGadget(
            key = KEY_GAME_LAUNCH,
            labelRes = R.string.game_widget_label,
            iconRes = R.drawable.ic_game_kryvavitsa,
            defaultSpanW = SPAN_SMALL,
            defaultSpanH = SPAN_SMALL,
            command = LauncherCellCommand.Feature(InternalRouteCatalog.KEY_GAME),
        ),
        HomeWidgetGadget(
            key = KEY_RANDOM_MUSIC,
            labelRes = R.string.widget_random_music_label,
            iconRes = R.drawable.ic_widget_random_music,
            defaultSpanW = SPAN_SMALL,
            defaultSpanH = SPAN_SMALL,
            iconTintable = true,
            command = LauncherCellCommand.Feature(InternalRouteCatalog.KEY_RANDOM_MUSIC),
        ),
        HomeWidgetGadget(
            key = KEY_QUICK_AUDIO_RECORDER,
            labelRes = R.string.widget_quick_audio_recorder_label,
            iconRes = R.drawable.ic_widget_quick_audio_recorder,
            defaultSpanW = SPAN_SMALL,
            defaultSpanH = SPAN_SMALL,
            iconTintable = true,
            command = LauncherCellCommand.Feature(InternalRouteCatalog.KEY_QUICK_VOICE),
        ),
    )

    /**
     * S1930: the widgets whose desktop cell owns a configured instance. Constructed rather than
     * injected, like every `HomeWidgetGadget` above - each reads its instance out of the cell param
     * and needs nothing from the graph.
     *
     * Their own function rather than two more lines in the list: that list is at detekt's `LongMethod`
     * ceiling, and this is where the third such widget goes - beside the one row it also owes
     * `ConfigurableWidgetCatalog`.
     */
    private fun configurableWidgetGadgets(): List<LauncherGadget> = listOf(
        RandomPhotoFrameGadget(),
        CameraQuickCaptureGadget(),
    )

    // Mirrors HomeWidgetCatalog.gadgetKey verbatim. Persisted inside a cell's target column from the
    // moment Settings places one, so these are a storage format - never rename them.
    private const val KEY_CALCULATOR = "calculator"
    private const val KEY_FRONT_FLASHLIGHT = "front_flashlight"
    private const val KEY_CAMERA_OCR_TRANSLATE = "camera_ocr_translate"
    private const val KEY_CAPTURE_OCR_PANEL = "capture_ocr_panel"
    private const val KEY_CAMERA_LAUNCH = "camera_launch"
    private const val KEY_CAMERA_PHOTOS = "camera_photos"
    private const val KEY_CONTINUE_READING = "continue_reading"
    private const val KEY_GAME_LAUNCH = "game_launch"
    private const val KEY_RANDOM_MUSIC = "random_music"
    private const val KEY_QUICK_AUDIO_RECORDER = "quick_audio_recorder"

    /** `targetCellWidth`/`targetCellHeight` of 1 - the icon-and-label widgets. */
    private const val SPAN_SMALL = 1

    /** `targetCellWidth`/`targetCellHeight` of 2 - the OCR panel. */
    private const val SPAN_MEDIUM = 2
}
