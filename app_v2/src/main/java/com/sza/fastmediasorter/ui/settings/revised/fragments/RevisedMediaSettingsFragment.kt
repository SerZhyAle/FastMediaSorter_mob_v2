package com.sza.fastmediasorter.ui.settings.revised.fragments

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.settings.revised.helpers.RevisedMediaSectionBinder

class RevisedMediaSettingsFragment : Fragment(R.layout.fragment_settings_revised_media) {

    private lateinit var sectionBinder: RevisedMediaSectionBinder

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sectionBinder = RevisedMediaSectionBinder(this, R.id.revisedMediaContentContainer)
        sectionBinder.attachContent()
    }

    fun ensureSectionExpanded(sectionId: String) {
        sectionBinder.ensureSectionExpanded(sectionId)
    }
}