package com.sza.fastmediasorter.ui.settings

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment

/**
 * S4621: one top-level settings page (SETTINGS-NAVIGATION 2.1 section 2).
 *
 * [id] is the stable key the remembered page, the per-page scroll offset, search routing and help
 * read; the pager position is derived from it and never stored.
 */
data class SettingsDestination(
    val id: String,
    @StringRes val titleRes: Int,
    @DrawableRes val iconRes: Int,
    val helpSurfaceId: String,
    val create: () -> Fragment,
) {
    companion object {
        const val GENERAL = "general"
        const val MEDIA = "media"
        const val PLAYBACK = "playback"
        const val OPERATIONS = "operations"

        /** S4622: the former launcher settings dialog, a page-sized topic (2.1 ADR-1). */
        const val LAUNCHER = "launcher"

        /** S4622: settings moved out of the calculator, stopwatch and translation hosts (2.1 item A). */
        const val TOOLS = "tools"
        const val ABOUT = "about"

        /** Help answer for a page that has no help-map row of its own (a flavor extension). */
        const val SCREEN_HELP_SURFACE = "SettingsActivity"
    }
}
