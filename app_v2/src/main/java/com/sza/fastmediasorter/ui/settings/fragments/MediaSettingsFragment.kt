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
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.core.debug.StrictModeHelper
import com.sza.fastmediasorter.core.xr.VrMediaSectionContract
import com.sza.fastmediasorter.databinding.FragmentSettingsMediaContainerBinding
import com.sza.fastmediasorter.ui.common.widget.CollapsibleSectionHeader
import com.sza.fastmediasorter.ui.settings.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MediaSettingsFragment : Fragment() {

    /**
     * S0249 Phase 04: optional VR media section. Provided as `NoOpVrMediaSectionContract` on
     * phone-only flavors (`isAvailable = false` → section hidden) and as
     * `VrMediaSectionContractImpl` on vr/noLegal flavors (`isAvailable = true` → section
     * visible with disabled toggle + advisory until XR runtime is detected).
     */
    @Inject lateinit var vrMediaSection: VrMediaSectionContract

    @Inject lateinit var mediaCapabilities: MediaCapabilities

    private var _binding: FragmentSettingsMediaContainerBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SettingsViewModel by activityViewModels()
    
    companion object {
        private const val PREFS_NAME = "media_sections_state"
        private const val KEY_IMAGES_EXPANDED = "section_images_expanded"
        private const val KEY_VIDEO_EXPANDED = "section_video_expanded"
        private const val KEY_VR_EXPANDED = "section_vr_expanded"
        private const val KEY_AUDIO_EXPANDED = "section_audio_expanded"
        private const val KEY_DOCUMENTS_EXPANDED = "section_documents_expanded"
        private const val KEY_OTHER_EXPANDED = "section_other_expanded"
    }

    private data class ExpandableSection(
        val header: CollapsibleSectionHeader,
        val container: View,
        val prefKey: String,
        val defaultExpanded: Boolean,
    )

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

        attachChildFragments()
        setupExpandableSections()
        setupResetSection()
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

    private fun attachChildFragments() {
        if (childFragmentManager.findFragmentByTag("media_images") != null) return

        val transaction = childFragmentManager.beginTransaction()

        if (mediaCapabilities.supportsImages) {
            transaction.replace(binding.containerImages.id, ImagesSettingsFragment(), "media_images")
        } else {
            binding.headerImages.isVisible = false
            binding.containerImages.isVisible = false
        }

        if (mediaCapabilities.supportsVideo) {
            transaction.replace(binding.containerVideo.id, VideoSettingsFragment(), "media_video")
        } else {
            binding.headerVideo.isVisible = false
            binding.containerVideo.isVisible = false
        }

        if (vrMediaSection.isAvailable) {
            val vrFragment = vrMediaSection.createFragment()
            if (vrFragment != null) {
                transaction.replace(binding.containerVr.id, vrFragment, "media_vr")
            } else {
                binding.headerVr.isVisible = false
                binding.containerVr.isVisible = false
            }
        } else {
            binding.headerVr.isVisible = false
            binding.containerVr.isVisible = false
        }

        if (mediaCapabilities.supportsAudio) {
            transaction.replace(binding.containerAudio.id, AudioSettingsFragment(), "media_audio")
        } else {
            binding.headerAudio.isVisible = false
            binding.containerAudio.isVisible = false
        }

        if (mediaCapabilities.supportsDocuments) {
            transaction.replace(binding.containerDocuments.id, DocumentsSettingsFragment(), "media_documents")
        } else {
            binding.headerDocuments.isVisible = false
            binding.containerDocuments.isVisible = false
        }

        transaction.replace(binding.containerOther.id, OtherMediaSettingsFragment(), "media_other")
        transaction.commitNow()
    }

    private fun setupExpandableSections() {
        val savedStates = getSavedSectionStates()
        val sections = listOf(
            ExpandableSection(binding.headerImages, binding.containerImages, KEY_IMAGES_EXPANDED, false),
            ExpandableSection(binding.headerVideo, binding.containerVideo, KEY_VIDEO_EXPANDED, false),
            ExpandableSection(binding.headerVr, binding.containerVr, KEY_VR_EXPANDED, true),
            ExpandableSection(binding.headerAudio, binding.containerAudio, KEY_AUDIO_EXPANDED, false),
            ExpandableSection(binding.headerDocuments, binding.containerDocuments, KEY_DOCUMENTS_EXPANDED, false),
            ExpandableSection(binding.headerOther, binding.containerOther, KEY_OTHER_EXPANDED, false),
        )

        sections.forEach { section ->
            if (!section.header.isVisible) {
                section.container.isVisible = false
                return@forEach
            }

            val expanded = savedStates[section.prefKey] ?: section.defaultExpanded
            section.header.setExpanded(expanded, notify = false)
            section.container.isVisible = expanded
            section.header.setOnExpandedChangeListener { isExpanded ->
                section.container.isVisible = isExpanded
                saveSectionState(section.prefKey, isExpanded)
            }
        }
    }

    fun ensureSectionExpanded(sectionId: String) {
        when (sectionId) {
            "images" -> expandSection(binding.headerImages)
            "video" -> expandSection(binding.headerVideo)
            "vr" -> expandSection(binding.headerVr)
            "audio" -> expandSection(binding.headerAudio)
            "documents" -> expandSection(binding.headerDocuments)
            "other" -> expandSection(binding.headerOther)
        }
    }

    private fun expandSection(header: CollapsibleSectionHeader) {
        if (header.isVisible) {
            header.setExpanded(true)
        }
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
                KEY_VR_EXPANDED to prefs.getBoolean(KEY_VR_EXPANDED, true),  // S0249: default expanded
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
