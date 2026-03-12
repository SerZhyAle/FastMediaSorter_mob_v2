package com.sza.fastmediasorter.ui.settings

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.sza.fastmediasorter.ui.settings.fragments.BackupRestoreFragment
import com.sza.fastmediasorter.ui.settings.fragments.DestinationsSettingsFragment
import com.sza.fastmediasorter.ui.settings.fragments.GeneralSettingsFragment
import com.sza.fastmediasorter.ui.settings.fragments.MediaSettingsFragment
import com.sza.fastmediasorter.ui.settings.fragments.PlaybackSettingsFragment

class SettingsPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
    
    override fun getItemCount(): Int = 5

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> GeneralSettingsFragment()
            1 -> MediaSettingsFragment()
            2 -> PlaybackSettingsFragment()
            3 -> DestinationsSettingsFragment()
            4 -> BackupRestoreFragment()
            else -> throw IllegalStateException("Unexpected position $position")
        }
    }
}
