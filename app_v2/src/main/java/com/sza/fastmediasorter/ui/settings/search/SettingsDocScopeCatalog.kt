package com.sza.fastmediasorter.ui.settings.search

import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.settings.SettingsSearchDestination

/**
 * One documentation-only settings surface: a layout outside [SettingsSearchLayoutCatalog] that
 * still hosts a persisted, user-facing setting.
 *
 * [hostKey] is the manifest `key` of the row/button on a cataloged settings screen that opens
 * this surface, so the published reference can tell the reader where to find it. A non-empty
 * value MUST name a view id already present in `docs/settings/settings-manifest.json`. An empty
 * value means the surface has no settings-screen entry point at all - it is reachable only from
 * the player, camera, or media UI - and the renderer must say so rather than invent a path.
 */
data class DocScopeSurface(
    val layoutResId: Int,
    val sectionId: String,
    val destination: SettingsSearchDestination,
    val hostKey: String
)

/**
 * S1313: source of truth for settings-bearing layouts that must appear in the published
 * documentation (`SETTINGS_REFERENCE*.md` / `settings-manifest.json`) but must NOT be added to
 * [SettingsSearchLayoutCatalog] - S1035 §6.6 already settled that in-app search indexes only the
 * settings-screen entry point for a dialog-hosted settings group, never the rows inside the
 * dialog itself (a dialog is not part of the activity view hierarchy
 * `SettingsActivity.navigateToTarget` can `findViewById` into, so indexing its rows would only
 * produce dead search results - the exact failure mode S0604 de-indexed transient buttons for).
 * This catalog and [SettingsSearchLayoutCatalog] are disjoint by construction;
 * `assert-settings-catalog-complete.ps1` gates that every settings-row layout on disk is in
 * exactly one of the two, or in `docs/settings/settings-scope-exclusions.json`.
 */
object SettingsDocScopeCatalog {

    val surfaces: List<DocScopeSurface> = listOf(
        DocScopeSurface(
            R.layout.dialog_launcher_settings,
            "launcher",
            SettingsSearchDestination.GENERAL,
            "rowLauncherSettings"
        ),
        DocScopeSurface(
            R.layout.dialog_edge_gesture_config,
            "gestures",
            SettingsSearchDestination.OPERATIONS,
            "btnOpenEdgeGestureConfig"
        ),
        DocScopeSurface(
            R.layout.dialog_default_apps,
            "defaultApps",
            SettingsSearchDestination.OPERATIONS,
            "btnOpenDefaultAppsDialog"
        ),
        // Opened only from the camera-capture viewfinder (CameraSettingsCallbackHandler), never
        // from a settings screen - no settings-screen control leads here, so hostKey stays empty.
        DocScopeSurface(
            R.layout.dialog_camera_settings,
            "camera",
            SettingsSearchDestination.OPERATIONS,
            ""
        ),
        // Opened only from the camera-OCR capture flow (CameraOcrTranslateActivity), same reason.
        DocScopeSurface(
            R.layout.dialog_camera_ocr_settings,
            "camera",
            SettingsSearchDestination.OPERATIONS,
            ""
        ),
        // Opened from the on-screen translation overlay, not a settings screen.
        DocScopeSurface(
            R.layout.dialog_translation_settings,
            "translation",
            SettingsSearchDestination.MEDIA,
            ""
        ),
        // S1433: the GNSS track opt-in lives inside the Network Monitor's Satellites section, not on a
        // settings screen, so hostKey stays empty. It is registered as its own one-row layout rather than
        // as the whole GNSS fragment: this scan also indexes MaterialButtons, and the fragment's share
        // button is an action, not a setting.
        DocScopeSurface(
            R.layout.view_network_monitor_gnss_track,
            "networkMonitor",
            SettingsSearchDestination.OPERATIONS,
            ""
        )
    )

    private val byLayout: Map<Int, DocScopeSurface> = surfaces.associateBy { it.layoutResId }

    fun sectionFor(layoutResId: Int): DocScopeSurface? = byLayout[layoutResId]

    /**
     * S1788: Wear OS settings entry definition (Compose-based settings with no XML layout).
     */
    data class WearDocEntry(
        val key: String,
        val sectionId: String = "wear",
        val destination: SettingsSearchDestination = SettingsSearchDestination.MEDIA,
        val layout: String,
        val kind: String,
        val titleEn: String,
        val titleRu: String,
        val titleUk: String
    )

    /**
     * S1788: Catalog of Wear OS settings published to the settings manifest and reference docs.
     */
    val wearEntries: List<WearDocEntry> = listOf(
        WearDocEntry(
            key = "wearDownloadAlbumArt",
            layout = "wear_other_settings",
            kind = "TOGGLE_ROW",
            titleEn = "Download album art",
            titleRu = "Загружать обложки",
            titleUk = "Завантажувати обкладинки"
        ),
        WearDocEntry(
            key = "wearEnableAudio",
            layout = "wear_media_types_settings",
            kind = "TOGGLE_ROW",
            titleEn = "Enable audio",
            titleRu = "Включить аудио",
            titleUk = "Увімкнути аудіо"
        ),
        WearDocEntry(
            key = "wearEnableImages",
            layout = "wear_media_types_settings",
            kind = "TOGGLE_ROW",
            titleEn = "Enable images",
            titleRu = "Включить изображения",
            titleUk = "Увімкнути зображення"
        ),
        WearDocEntry(
            key = "wearEnableSlideshow",
            layout = "wear_slideshow_settings",
            kind = "TOGGLE_ROW",
            titleEn = "Enable slideshow",
            titleRu = "Включить слайд-шоу",
            titleUk = "Увімкнути слайд-шоу"
        ),
        WearDocEntry(
            key = "wearEnableVideo",
            layout = "wear_media_types_settings",
            kind = "TOGGLE_ROW",
            titleEn = "Enable video",
            titleRu = "Включить видео",
            titleUk = "Увімкнути відео"
        ),
        // S1781: the watch's Screen section - one view shared by the home screen and the Resources
        // page, and a keep-awake flag that covers everything except the three players.
        WearDocEntry(
            key = "wearKeepScreenAwake",
            layout = "wear_screen_settings",
            kind = "TOGGLE_ROW",
            titleEn = "Keep screen on",
            titleRu = "Не гасить экран",
            titleUk = "Не гасити екран"
        ),
        WearDocEntry(
            key = "wearSlideshowInterval",
            layout = "wear_slideshow_settings",
            kind = "SPINNER",
            titleEn = "Slideshow interval",
            titleRu = "Интервал слайд-шоу",
            titleUk = "Інтервал слайд-шоу"
        ),
        WearDocEntry(
            key = "wearSlideshowWaitForFinish",
            layout = "wear_slideshow_settings",
            kind = "TOGGLE_ROW",
            titleEn = "Wait for finish",
            titleRu = "Ждать завершения",
            titleUk = "Чекати завершення"
        ),
        WearDocEntry(
            key = "wearViewMode",
            layout = "wear_screen_settings",
            kind = "RADIO_GROUP",
            titleEn = "Screens view",
            titleRu = "Вид экранов",
            titleUk = "Вигляд екранів"
        ),
        // S1730: the file list keeps its own view, so the watch's Screen section carries two - one
        // for the navigation screens above and this one for the two file lists.
        WearDocEntry(
            key = "wearFileListViewMode",
            layout = "wear_screen_settings",
            kind = "RADIO_GROUP",
            titleEn = "Files view",
            titleRu = "Вид файлов",
            titleUk = "Вигляд файлів"
        )
    )
}
