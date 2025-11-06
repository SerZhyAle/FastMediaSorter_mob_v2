// Package declaration for the main UI components
package com.sza.fastmediasorter.ui

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.sza.fastmediasorter.ui.local.LocalFoldersFragment
import com.sza.fastmediasorter.ui.network.NetworkFragment

/**
 * Adapter for managing the main ViewPager2 that displays the primary app tabs.
 * This adapter creates and manages two main fragments: Local Folders and Network.
 * It extends FragmentStateAdapter to efficiently handle fragment lifecycle and state.
 *
 * @param activity The FragmentActivity that hosts the ViewPager2 using this adapter
 */
class MainPagerAdapter(
    activity: FragmentActivity,
) : FragmentStateAdapter(activity) {
    /**
     * Returns the total number of fragments/pages managed by this adapter.
     * The app has two main tabs: Local Folders (position 0) and Network (position 1).
     *
     * @return The fixed count of 2 fragments
     */
    override fun getItemCount(): Int = 2

    /**
     * Creates and returns the appropriate Fragment for the given position.
     * This method is called by ViewPager2 when it needs to display a page.
     *
     * @param position The position of the fragment to create (0-based index)
     * @return The Fragment instance for the specified position
     * @throws IllegalArgumentException if position is invalid (should never happen in normal usage)
     */
    override fun createFragment(position: Int): Fragment {
        // Use when expression to map position to corresponding fragment
        return when (position) {
            // Position 0: Local Folders tab - shows device media folders
            0 -> LocalFoldersFragment()
            // Position 1: Network tab - shows network connections and remote media
            1 -> NetworkFragment()
            // Invalid position - should not occur but handled defensively
            else -> throw IllegalArgumentException("Invalid position $position")
        }
    }
}
