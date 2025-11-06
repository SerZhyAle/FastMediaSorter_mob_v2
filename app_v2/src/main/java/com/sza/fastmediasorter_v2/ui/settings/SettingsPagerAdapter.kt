package com.sza.fastmediasorter_v2.ui.settings

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class SettingsPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
    
    override fun getItemCount(): Int = 4

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> GeneralSettingsFragment()
            1 -> MediaSettingsFragment()
            2 -> PlaybackSettingsFragment()
            3 -> DestinationsSettingsFragment()
            else -> throw IllegalStateException("Unexpected position $position")
        }
    }
}
