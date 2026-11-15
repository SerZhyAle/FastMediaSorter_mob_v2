package com.sza.fastmediasorter.ui.settings

import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.settings.fragments.GeneralSettingsFragment
import com.sza.fastmediasorter.ui.settings.fragments.MediaSettingsFragment
import com.sza.fastmediasorter.ui.settings.fragments.OperationsSettingsFragment
import com.sza.fastmediasorter.ui.settings.fragments.PlaybackSettingsFragment

/**
 * Pager adapter for [SettingsActivity] (S0245 refactor).
 *
 * Combines the static 4 tabs (General / Media / Playback / Operations) with any
 * flavor-supplied [SettingsTabExtension] entries that report `isVisible == true`. Phone
 * flavors contribute no extensions, so behaviour is unchanged for them.
 */
class SettingsPagerAdapter(
    activity: FragmentActivity,
    extensions: Set<SettingsTabExtension>,
) : FragmentStateAdapter(activity) {

    private val entries: List<TabEntry> = buildList {
        add(TabEntry(R.string.settings_tab_general, ::GeneralSettingsFragment))
        add(TabEntry(R.string.settings_tab_media, ::MediaSettingsFragment))
        add(TabEntry(R.string.settings_tab_playback, ::PlaybackSettingsFragment))
        add(TabEntry(R.string.settings_tab_operations, ::OperationsSettingsFragment))
        extensions
            .filter { it.isVisible }
            .sortedBy { it.order }
            .forEach { add(TabEntry(it.tabTitleResId, it::createFragment)) }
    }

    override fun getItemCount(): Int = entries.size

    override fun createFragment(position: Int): Fragment = entries[position].fragmentCreator()

    @StringRes
    fun getTabTitleResId(position: Int): Int = entries[position].titleResId

    private data class TabEntry(
        @StringRes val titleResId: Int,
        val fragmentCreator: () -> Fragment,
    )
}
