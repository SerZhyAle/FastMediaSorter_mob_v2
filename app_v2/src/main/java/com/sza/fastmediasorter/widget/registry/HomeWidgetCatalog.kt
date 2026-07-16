package com.sza.fastmediasorter.widget.registry

import android.appwidget.AppWidgetManager
import android.content.Context
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.widget.AudioNowPlayingWidgetProvider
import com.sza.fastmediasorter.widget.CalculatorWidgetProvider
import com.sza.fastmediasorter.widget.CameraLaunchWidgetProvider
import com.sza.fastmediasorter.widget.CameraOcrTranslateWidgetProvider
import com.sza.fastmediasorter.widget.CameraPhotosWidgetProvider
import com.sza.fastmediasorter.widget.CameraQuickCaptureWidgetProvider
import com.sza.fastmediasorter.widget.CaptureOcrPanelWidgetProvider
import com.sza.fastmediasorter.widget.ContinueReadingWidgetProvider
import com.sza.fastmediasorter.widget.FavoritesWidgetProvider
import com.sza.fastmediasorter.widget.GameLaunchWidgetProvider
import com.sza.fastmediasorter.widget.QuickAudioRecorderWidgetProvider
import com.sza.fastmediasorter.widget.RandomPhotoFrameWidgetProvider
import com.sza.fastmediasorter.widget.RandomMusicWidgetProvider
import com.sza.fastmediasorter.widget.ScheduledTasksWidgetProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for the in-app "Add widget to home screen" picker.
 *
 * Availability has two layers:
 * - Flavor gating: only providers physically present in this build's merged manifest are offered
 *   (a receiver removed via `tools:node="remove"` in a flavor never appears in [installedProviders]).
 * - Runtime gating: an entry's optional [HomeWidgetEntry.settingGate] is applied against current
 *   [AppSettings] (e.g. the game widget appears only when the embedded game is enabled).
 *
 * No compile-time flavor capability flag is read here (Rule 15) - the manifest is the flavor gate.
 * The configurable Resource-Launch shortcut is intentionally excluded; it keeps its own per-resource
 * pin entry in the resource editor.
 */
@Singleton
class HomeWidgetCatalog @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
) {

    private val allEntries: List<HomeWidgetEntry> = listOf(
        HomeWidgetEntry(
            providerClass = CalculatorWidgetProvider::class.java,
            labelRes = R.string.widget_calculator_label,
            iconRes = R.drawable.ic_widget_calculator,
            descriptionRes = R.string.widget_calculator_description,
        ),
        HomeWidgetEntry(
            providerClass = CameraOcrTranslateWidgetProvider::class.java,
            labelRes = R.string.widget_camera_ocr_translate_label,
            iconRes = R.drawable.ic_camera_ocr_translate,
            descriptionRes = R.string.widget_camera_ocr_translate_description,
        ),
        HomeWidgetEntry(
            // Removed via manifest tools:node="remove" in lite/photos (ENABLE_TRANSLATION=false);
            // the installedProviders gate hides it there without a compile-time flavor flag.
            providerClass = CaptureOcrPanelWidgetProvider::class.java,
            labelRes = R.string.widget_capture_ocr_panel_label,
            iconRes = R.drawable.ic_camera_ocr_translate,
            descriptionRes = R.string.widget_capture_ocr_panel_description,
        ),
        HomeWidgetEntry(
            providerClass = CameraLaunchWidgetProvider::class.java,
            labelRes = R.string.widget_camera_launch_label,
            iconRes = R.drawable.ic_widget_camera_launch_accent,
            descriptionRes = R.string.widget_camera_launch_description,
        ),
        HomeWidgetEntry(
            providerClass = CameraPhotosWidgetProvider::class.java,
            labelRes = R.string.widget_camera_photos_label,
            iconRes = R.drawable.ic_widget_camera_photos,
            descriptionRes = R.string.widget_camera_photos_description,
        ),
        HomeWidgetEntry(
            providerClass = CameraQuickCaptureWidgetProvider::class.java,
            labelRes = R.string.widget_camera_quick_capture_label,
            iconRes = R.drawable.ic_widget_camera_quick_capture,
            descriptionRes = R.string.widget_camera_quick_capture_description,
            // Hide from the picker when camera capture is globally disabled; the trampoline still
            // gates an already-pinned instance defensively (S0369).
            settingGate = { !it.disableCameraCapture },
        ),
        HomeWidgetEntry(
            providerClass = ContinueReadingWidgetProvider::class.java,
            labelRes = R.string.widget_continue_reading_label,
            iconRes = R.drawable.ic_widget_continue_reading,
            descriptionRes = R.string.widget_continue_reading_description,
        ),
        HomeWidgetEntry(
            providerClass = GameLaunchWidgetProvider::class.java,
            labelRes = R.string.game_widget_label,
            iconRes = R.drawable.ic_game_kryvavitsa,
            descriptionRes = R.string.game_widget_description,
            settingGate = { it.embeddedGameEnabled },
        ),
        HomeWidgetEntry(
            providerClass = RandomPhotoFrameWidgetProvider::class.java,
            labelRes = R.string.widget_random_photo_frame_label,
            iconRes = R.drawable.ic_widget_camera_photos,
            descriptionRes = R.string.widget_random_photo_frame_description,
        ),
        HomeWidgetEntry(
            providerClass = RandomMusicWidgetProvider::class.java,
            labelRes = R.string.widget_random_music_label,
            iconRes = R.drawable.ic_widget_random_music,
            descriptionRes = R.string.widget_random_music_description,
        ),
        HomeWidgetEntry(
            providerClass = AudioNowPlayingWidgetProvider::class.java,
            labelRes = R.string.widget_audio_now_playing_label,
            iconRes = R.drawable.ic_widget_random_music,
            descriptionRes = R.string.widget_audio_now_playing_description,
        ),
        HomeWidgetEntry(
            providerClass = QuickAudioRecorderWidgetProvider::class.java,
            labelRes = R.string.widget_quick_audio_recorder_label,
            iconRes = R.drawable.ic_widget_quick_audio_recorder,
            descriptionRes = R.string.widget_quick_audio_recorder_description,
        ),
        HomeWidgetEntry(
            providerClass = FavoritesWidgetProvider::class.java,
            labelRes = R.string.widget_favorites_label,
            iconRes = R.drawable.ic_widget_favorites,
            descriptionRes = R.string.widget_favorites_description,
            settingGate = { it.enableFavorites },
        ),
        HomeWidgetEntry(
            providerClass = ScheduledTasksWidgetProvider::class.java,
            labelRes = R.string.widget_scheduled_tasks_label,
            iconRes = R.drawable.ic_widget_scheduled_tasks,
            descriptionRes = R.string.widget_scheduled_tasks_description,
            settingGate = { it.enableScheduledOperations },
        ),
    )

    /**
     * Widgets pinnable in this build right now: present in the merged manifest (flavor gate) and
     * passing their optional runtime [HomeWidgetEntry.settingGate]. Uses [installedProviders]
     * (API 3+) rather than `getInstalledProvidersForPackage` (API 26+) to stay safe on the
     * `legacy` flavor (minSdk 23).
     */
    suspend fun availableEntries(): List<HomeWidgetEntry> {
        val packageName = context.packageName
        val installed = AppWidgetManager.getInstance(context).installedProviders
            .filter { it.provider.packageName == packageName }
            .map { it.provider.className }
            .toSet()
        val settings = settingsRepository.getSettings().first()
        return allEntries.filter { entry ->
            entry.providerClass.name in installed &&
                (entry.settingGate?.invoke(settings) ?: true)
        }
    }
}
