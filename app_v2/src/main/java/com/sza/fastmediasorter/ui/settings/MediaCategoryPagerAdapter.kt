package com.sza.fastmediasorter.ui.settings

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.ui.settings.fragments.AudioSettingsFragment
import com.sza.fastmediasorter.ui.settings.fragments.DocumentsSettingsFragment
import com.sza.fastmediasorter.ui.settings.fragments.ImagesSettingsFragment
import com.sza.fastmediasorter.ui.settings.fragments.OtherMediaSettingsFragment
import com.sza.fastmediasorter.ui.settings.fragments.PlaybackSettingsFragment
import com.sza.fastmediasorter.ui.settings.fragments.VideoSettingsFragment

/**
 * Media category pager adapter for settings.
 * Dynamically shows/hides tabs based on product flavor BuildConfig flags.
 */
class MediaCategoryPagerAdapter(private val hostFragment: Fragment) : FragmentStateAdapter(hostFragment) {
    
    /**
     * Represents a media category tab with its fragment creator and visibility condition.
     */
    private data class MediaCategory(
        val nameResId: Int,
        val fragmentCreator: () -> Fragment,
        val isEnabled: Boolean
    )
    
    /**
     * All possible media categories, filtered by flavor support.
     */
    private val categories: List<MediaCategory> = listOf(
        MediaCategory(com.sza.fastmediasorter.R.string.settings_category_images, { ImagesSettingsFragment() }, BuildConfig.SUPPORT_IMAGES),
        MediaCategory(com.sza.fastmediasorter.R.string.settings_category_video, { VideoSettingsFragment() }, BuildConfig.SUPPORT_VIDEO),
        MediaCategory(com.sza.fastmediasorter.R.string.settings_category_audio, { AudioSettingsFragment() }, BuildConfig.SUPPORT_AUDIO),
        MediaCategory(com.sza.fastmediasorter.R.string.settings_category_documents, { DocumentsSettingsFragment() }, BuildConfig.SUPPORT_DOCUMENTS),
        MediaCategory(com.sza.fastmediasorter.R.string.settings_category_other, { OtherMediaSettingsFragment() }, true) // Always show Other (has GIF settings)
    ).filter { it.isEnabled }
    
    override fun getItemCount(): Int = categories.size

    override fun createFragment(position: Int): Fragment {
        return categories[position].fragmentCreator()
    }
    
    /**
     * Get tab title for position.
     */
    fun getTabTitle(position: Int): String {
        return categories.getOrNull(position)?.let { hostFragment.getString(it.nameResId) } ?: ""
    }
}
