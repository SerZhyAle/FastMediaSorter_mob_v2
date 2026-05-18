package com.sza.fastmediasorter.ui.settings.revised.helpers

import android.view.View
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.commitNow
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.settings.fragments.PlaybackSettingsFragment

class RevisedPlaybackSectionBinder(
    private val fragment: Fragment,
    private val containerId: Int,
) {

    companion object {
        private const val CHILD_TAG = "revised_playback_legacy_content"
    }

    fun attachContent() {
        if (hostedFragment() == null) {
            fragment.childFragmentManager.commitNow {
                replace(containerId, PlaybackSettingsFragment(), CHILD_TAG)
            }
        }
    }

    fun ensureSectionExpanded(sectionId: String) {
        val hostedRoot = hostedFragment()?.view ?: return
        when (sectionId) {
            "playback", "sorting", "sorting_slideshow" -> ensureExpanded(
                hostedRoot,
                R.id.headerSortingSlideshow,
                R.id.containerSortingSlideshow,
            )

            "file_operations" -> ensureExpanded(
                hostedRoot,
                R.id.headerFileOperations,
                R.id.containerFileOperations,
            )

            "grid_view" -> ensureExpanded(
                hostedRoot,
                R.id.headerGridView,
                R.id.containerGridView,
            )

            "player_ui" -> ensureExpanded(
                hostedRoot,
                R.id.headerPlayerUI,
                R.id.containerPlayerUI,
            )

            "touch_zones" -> ensureExpanded(
                hostedRoot,
                R.id.headerTouchZones,
                R.id.containerTouchZones,
            )

            "behaviour" -> ensureExpanded(
                hostedRoot,
                R.id.headerBehaviour,
                R.id.containerBehaviour,
            )
        }
    }

    private fun ensureExpanded(root: View, headerId: Int, contentId: Int) {
        val content = root.findViewById<View>(contentId) ?: return
        if (content.isVisible) return
        root.findViewById<TextView>(headerId)?.performClick()
    }

    private fun hostedFragment(): PlaybackSettingsFragment? {
        return fragment.childFragmentManager.findFragmentByTag(CHILD_TAG) as? PlaybackSettingsFragment
    }
}