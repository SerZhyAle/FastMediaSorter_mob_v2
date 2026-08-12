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
}
