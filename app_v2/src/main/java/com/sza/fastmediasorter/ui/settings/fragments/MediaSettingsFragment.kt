package com.sza.fastmediasorter.ui.settings.fragments

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.core.view.isVisible
import android.widget.Toast
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.debug.StrictModeHelper
import com.sza.fastmediasorter.databinding.FragmentSettingsMediaContainerBinding
import com.sza.fastmediasorter.ui.settings.SettingsViewModel
import com.sza.fastmediasorter.ui.settings.helpers.DefaultPlayerHelper

@android.annotation.SuppressLint("SetTextI18n")
class MediaSettingsFragment : Fragment() {

    private var _binding: FragmentSettingsMediaContainerBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SettingsViewModel by activityViewModels()
    
    companion object {
        private const val PREFS_NAME = "media_sections_state"
        private const val KEY_IMAGES_EXPANDED = "section_images_expanded"
        private const val KEY_VIDEO_EXPANDED = "section_video_expanded"
        private const val KEY_AUDIO_EXPANDED = "section_audio_expanded"
        private const val KEY_DOCUMENTS_EXPANDED = "section_documents_expanded"
        private const val KEY_OTHER_EXPANDED = "section_other_expanded"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsMediaContainerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupSectionTitles()
        attachChildFragments()
        setupExpandableSections()
        setupResetSection()
        setupDefaultPlayerButton()
    }

    override fun onResume() {
        super.onResume()
        _binding?.let {
            DefaultPlayerHelper.applyButtonState(
                it.btnSetDefaultMediaPlayer, requireContext(), R.string.settings_set_default_media_player
            )
        }
    }

    private fun setupDefaultPlayerButton() {
        DefaultPlayerHelper.applyButtonState(
            binding.btnSetDefaultMediaPlayer, requireContext(), R.string.settings_set_default_media_player
        )
        binding.btnSetDefaultMediaPlayer.setOnClickListener {
            DefaultPlayerHelper.showSetDefaultDialog(this)
        }
    }

    private fun setupResetSection() {
        binding.btnResetMediaSection.setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.reset_media_section_title)
                .setMessage(R.string.reset_media_section_message)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    viewModel.resetMediaSection()
                    Toast.makeText(
                        requireContext(),
                        R.string.reset_media_section_success,
                        Toast.LENGTH_SHORT
                    ).show()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun setupSectionTitles() {
        updateHeader(binding.headerImages, getString(R.string.settings_category_images), true)
        updateHeader(binding.headerVideo, getString(R.string.settings_category_video), true)
        updateHeader(binding.headerAudio, getString(R.string.settings_category_audio), true)
        updateHeader(binding.headerDocuments, getString(R.string.settings_category_documents), true)
        updateHeader(binding.headerOther, getString(R.string.settings_category_other), true)
    }

    private fun attachChildFragments() {
        if (childFragmentManager.findFragmentByTag("media_images") != null) return

        val transaction = childFragmentManager.beginTransaction()

        if (BuildConfig.SUPPORT_IMAGES) {
            transaction.replace(binding.containerImages.id, ImagesSettingsFragment(), "media_images")
        } else {
            binding.headerImages.isVisible = false
            binding.containerImages.isVisible = false
        }

        if (BuildConfig.SUPPORT_VIDEO) {
            transaction.replace(binding.containerVideo.id, VideoSettingsFragment(), "media_video")
        } else {
            binding.headerVideo.isVisible = false
            binding.containerVideo.isVisible = false
        }

        if (BuildConfig.SUPPORT_AUDIO) {
            transaction.replace(binding.containerAudio.id, AudioSettingsFragment(), "media_audio")
        } else {
            binding.headerAudio.isVisible = false
            binding.containerAudio.isVisible = false
        }

        if (BuildConfig.SUPPORT_DOCUMENTS) {
            transaction.replace(binding.containerDocuments.id, DocumentsSettingsFragment(), "media_documents")
        } else {
            binding.headerDocuments.isVisible = false
            binding.containerDocuments.isVisible = false
        }

        transaction.replace(binding.containerOther.id, OtherMediaSettingsFragment(), "media_other")
        transaction.commitNow()
    }

    private fun setupExpandableSections() {
        // Restore saved states or default to collapsed
        val savedStates = getSavedSectionStates()
        
        bindSectionToggle(
            binding.headerImages, 
            binding.containerImages, 
            getString(R.string.settings_category_images),
            KEY_IMAGES_EXPANDED,
            savedStates[KEY_IMAGES_EXPANDED] ?: false
        )
        bindSectionToggle(
            binding.headerVideo, 
            binding.containerVideo, 
            getString(R.string.settings_category_video),
            KEY_VIDEO_EXPANDED,
            savedStates[KEY_VIDEO_EXPANDED] ?: false
        )
        bindSectionToggle(
            binding.headerAudio, 
            binding.containerAudio, 
            getString(R.string.settings_category_audio),
            KEY_AUDIO_EXPANDED,
            savedStates[KEY_AUDIO_EXPANDED] ?: false
        )
        bindSectionToggle(
            binding.headerDocuments, 
            binding.containerDocuments, 
            getString(R.string.settings_category_documents),
            KEY_DOCUMENTS_EXPANDED,
            savedStates[KEY_DOCUMENTS_EXPANDED] ?: false
        )
        bindSectionToggle(
            binding.headerOther, 
            binding.containerOther, 
            getString(R.string.settings_category_other),
            KEY_OTHER_EXPANDED,
            savedStates[KEY_OTHER_EXPANDED] ?: false
        )
    }

    fun ensureSectionExpanded(sectionId: String) {
        when (sectionId) {
            "images" -> expandSection(binding.headerImages, binding.containerImages, getString(R.string.settings_category_images), KEY_IMAGES_EXPANDED)
            "video" -> expandSection(binding.headerVideo, binding.containerVideo, getString(R.string.settings_category_video), KEY_VIDEO_EXPANDED)
            "audio" -> expandSection(binding.headerAudio, binding.containerAudio, getString(R.string.settings_category_audio), KEY_AUDIO_EXPANDED)
            "documents" -> expandSection(binding.headerDocuments, binding.containerDocuments, getString(R.string.settings_category_documents), KEY_DOCUMENTS_EXPANDED)
            "other" -> expandSection(binding.headerOther, binding.containerOther, getString(R.string.settings_category_other), KEY_OTHER_EXPANDED)
        }
    }

    private fun expandSection(header: android.widget.TextView, content: View, title: String, prefKey: String) {
        if (!header.isVisible) {
            return
        }

        if (!content.isVisible) {
            content.isVisible = true
            updateHeader(header, title, true)
            saveSectionState(prefKey, true)
        }
    }

    private fun bindSectionToggle(
        header: android.widget.TextView, 
        content: View, 
        title: String,
        prefKey: String,
        initiallyExpanded: Boolean
    ) {
        if (!header.isVisible) return
        
        // Set initial state
        content.isVisible = initiallyExpanded
        updateHeader(header, title, initiallyExpanded)

        header.setOnClickListener {
            val expanded = !content.isVisible
            content.isVisible = expanded
            updateHeader(header, title, expanded)
            saveSectionState(prefKey, expanded)
        }
    }

    private fun updateHeader(header: android.widget.TextView, title: String, expanded: Boolean) {
        val prefix = if (expanded) "▼" else "▶"
        header.text = getString(R.string.string_format_two_args, prefix, title)
    }
    
    /**
     * Get saved section states from SharedPreferences.
     * Wrapped in StrictModeHelper to avoid violations during fragment creation.
     */
    private fun getSavedSectionStates(): Map<String, Boolean> {
        return StrictModeHelper.allowDiskReads {
            val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            mapOf(
                KEY_IMAGES_EXPANDED to prefs.getBoolean(KEY_IMAGES_EXPANDED, false),
                KEY_VIDEO_EXPANDED to prefs.getBoolean(KEY_VIDEO_EXPANDED, false),
                KEY_AUDIO_EXPANDED to prefs.getBoolean(KEY_AUDIO_EXPANDED, false),
                KEY_DOCUMENTS_EXPANDED to prefs.getBoolean(KEY_DOCUMENTS_EXPANDED, false),
                KEY_OTHER_EXPANDED to prefs.getBoolean(KEY_OTHER_EXPANDED, false)
            )
        }
    }
    
    /**
     * Save section expanded state to SharedPreferences.
     * Wrapped in StrictModeHelper to avoid violations.
     */
    private fun saveSectionState(key: String, expanded: Boolean) {
        StrictModeHelper.allowDiskWrites {
            requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(key, expanded)
                .apply()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
