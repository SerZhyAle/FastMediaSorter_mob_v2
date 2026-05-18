package com.sza.fastmediasorter.ui.settings.revised.helpers

import androidx.fragment.app.Fragment
import androidx.fragment.app.commitNow
import com.sza.fastmediasorter.ui.settings.fragments.MediaSettingsFragment

class RevisedMediaSectionBinder(
    private val fragment: Fragment,
    private val containerId: Int,
) {

    companion object {
        private const val CHILD_TAG = "revised_media_legacy_content"
    }

    fun attachContent() {
        if (hostedFragment() == null) {
            fragment.childFragmentManager.commitNow {
                replace(containerId, MediaSettingsFragment(), CHILD_TAG)
            }
        }
    }

    fun ensureSectionExpanded(sectionId: String) {
        hostedFragment()?.ensureSectionExpanded(sectionId)
    }

    private fun hostedFragment(): MediaSettingsFragment? {
        return fragment.childFragmentManager.findFragmentByTag(CHILD_TAG) as? MediaSettingsFragment
    }
}