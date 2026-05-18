package com.sza.fastmediasorter.ui.settings.revised

import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.settings.revised.fragments.RevisedGeneralSettingsFragment
import com.sza.fastmediasorter.ui.settings.revised.fragments.RevisedMediaSettingsFragment
import com.sza.fastmediasorter.ui.settings.revised.fragments.RevisedOperationsSettingsFragment
import com.sza.fastmediasorter.ui.settings.revised.fragments.RevisedPlaybackSettingsFragment
import com.sza.fastmediasorter.ui.settings.revised.fragments.RevisedSettingsPlaceholderFragment

class RevisedSettingsPagerAdapter(
    activity: FragmentActivity,
) : FragmentStateAdapter(activity) {

    private val entries: List<TabEntry> = listOf(
        TabEntry(R.string.settings_tab_general),
        TabEntry(R.string.settings_tab_media),
        TabEntry(R.string.settings_tab_playback),
        TabEntry(R.string.settings_tab_operations),
    )

    override fun getItemCount(): Int = entries.size

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> RevisedGeneralSettingsFragment()
            1 -> RevisedMediaSettingsFragment()
            2 -> RevisedPlaybackSettingsFragment()
            3 -> RevisedOperationsSettingsFragment()
            else -> RevisedSettingsPlaceholderFragment.newInstance(entries[position].titleResId)
        }
    }

    @StringRes
    fun getTabTitleResId(position: Int): Int = entries[position].titleResId

    private data class TabEntry(
        @StringRes val titleResId: Int,
    )
}