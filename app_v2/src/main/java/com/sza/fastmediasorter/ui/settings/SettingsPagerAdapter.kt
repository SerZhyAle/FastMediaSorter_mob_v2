package com.sza.fastmediasorter.ui.settings

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.settings.fragments.AboutSettingsFragment
import com.sza.fastmediasorter.ui.settings.fragments.GeneralSettingsFragment
import com.sza.fastmediasorter.ui.settings.fragments.MediaSettingsFragment
import com.sza.fastmediasorter.ui.settings.fragments.OperationsSettingsFragment
import com.sza.fastmediasorter.ui.settings.fragments.PlaybackSettingsFragment
import com.sza.fastmediasorter.ui.settings.fragments.ToolsSettingsFragment

/**
 * Pager adapter for [SettingsActivity] (S0245 refactor) and the one ordered destination list (S4621).
 *
 * General, Media, Player, Management, Launcher where the system launcher is offered, Tools (S4622), then
 * any flavor-supplied [SettingsTabExtension] that reports `isVisible == true`, then About, which
 * SETTINGS-NAVIGATION 2.1 section 3 rule 3 requires to be the last top-level page. The page menu, search
 * and help all read this list, so a page inserted before About cannot route anyone to the wrong position.
 */
class SettingsPagerAdapter(
    activity: FragmentActivity,
    extensions: Set<SettingsTabExtension>,
    showLauncher: Boolean,
) : FragmentStateAdapter(activity) {

    val destinations: List<SettingsDestination> = buildList {
        add(
            SettingsDestination(
                SettingsDestination.GENERAL,
                R.string.settings_tab_general,
                R.drawable.ic_settings,
                "settings.general",
                ::GeneralSettingsFragment,
            )
        )
        add(
            SettingsDestination(
                SettingsDestination.MEDIA,
                R.string.settings_tab_media,
                R.drawable.ic_image,
                "settings.media",
                ::MediaSettingsFragment,
            )
        )
        add(
            SettingsDestination(
                SettingsDestination.PLAYBACK,
                R.string.settings_tab_playback,
                R.drawable.ic_play,
                "settings.playback",
                ::PlaybackSettingsFragment,
            )
        )
        add(
            SettingsDestination(
                SettingsDestination.OPERATIONS,
                R.string.settings_tab_operations,
                R.drawable.ic_folder,
                "settings.operations",
                ::OperationsSettingsFragment,
            )
        )
        if (showLauncher) {
            add(
                SettingsDestination(
                    SettingsDestination.LAUNCHER,
                    R.string.settings_tab_launcher,
                    R.drawable.ic_launcher_mode,
                    "settings.launcher",
                    ::LauncherSettingsFragment,
                )
            )
        }
        add(
            SettingsDestination(
                SettingsDestination.TOOLS,
                R.string.settings_tab_tools,
                R.drawable.ic_apps,
                "settings.tools",
                ::ToolsSettingsFragment,
            )
        )
        extensions
            .filter { it.isVisible }
            .sortedBy { it.order }
            .forEach {
                add(
                    SettingsDestination(
                        it.destinationId,
                        it.tabTitleResId,
                        it.iconResId,
                        SettingsDestination.SCREEN_HELP_SURFACE,
                        it::createFragment,
                    )
                )
            }
        add(
            SettingsDestination(
                SettingsDestination.ABOUT,
                R.string.settings_tab_about,
                R.drawable.ic_info,
                "settings.about",
                ::AboutSettingsFragment,
            )
        )
    }

    override fun getItemCount(): Int = destinations.size

    override fun createFragment(position: Int): Fragment = destinations[position].create()

    /** Pager position of [id], or -1 when this build has no such page. */
    fun indexOf(id: String): Int = destinations.indexOfFirst { it.id == id }

    fun destinationAt(position: Int): SettingsDestination? = destinations.getOrNull(position)
}
