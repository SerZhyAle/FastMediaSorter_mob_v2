package com.sza.fastmediasorter.ui.settings.revised.fragments

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.settings.revised.helpers.RevisedPlaybackSectionBinder

class RevisedPlaybackSettingsFragment : Fragment(R.layout.fragment_settings_revised_playback) {

    private lateinit var sectionBinder: RevisedPlaybackSectionBinder

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sectionBinder = RevisedPlaybackSectionBinder(this, R.id.revisedPlaybackContentContainer)
        sectionBinder.attachContent()
    }

    fun ensureSectionExpanded(sectionId: String) {
        sectionBinder.ensureSectionExpanded(sectionId)
    }
}