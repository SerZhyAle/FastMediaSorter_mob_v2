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
import timber.log.Timber

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

    private data class MediaChildSection(
        val header: CollapsibleSectionHeader,
        val container: View,
        val prefKey: String,
        val defaultExpanded: Boolean,
        val tag: String,
        // null factory => section has no child fragment (capability off or VR unavailable): header/container hidden.
        val factory: (() -> Fragment)?,
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

        setupSections()
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

    /**
     * Builds the section descriptors. A null `factory` marks a section whose child fragment must
     * not exist (capability off, or VR runtime unavailable) - its header/container are hidden and
     * no fragment is ever attached. `createFragment()` is evaluated once here only to decide VR
     * availability; the actual attach is deferred to [ensureChildAttached] on first expand.
     */
    private fun buildSections(): List<MediaChildSection> {
        val vrFragment = if (vrMediaSection.isAvailable) vrMediaSection.createFragment() else null
        return listOf(
            MediaChildSection(binding.headerImages, binding.containerImages, KEY_IMAGES_EXPANDED, false, "media_images",
                if (mediaCapabilities.supportsImages) ({ ImagesSettingsFragment() }) else null),
            MediaChildSection(binding.headerVideo, binding.containerVideo, KEY_VIDEO_EXPANDED, false, "media_video",
                if (mediaCapabilities.supportsVideo) ({ VideoSettingsFragment() }) else null),
            MediaChildSection(binding.headerVr, binding.containerVr, KEY_VR_EXPANDED, true, "media_vr",
                if (vrFragment != null) ({ vrFragment }) else null),
            MediaChildSection(binding.headerAudio, binding.containerAudio, KEY_AUDIO_EXPANDED, false, "media_audio",
                if (mediaCapabilities.supportsAudio) ({ AudioSettingsFragment() }) else null),
            MediaChildSection(binding.headerDocuments, binding.containerDocuments, KEY_DOCUMENTS_EXPANDED, false, "media_documents",
                if (mediaCapabilities.supportsDocuments) ({ DocumentsSettingsFragment() }) else null),
            MediaChildSection(binding.headerOther, binding.containerOther, KEY_OTHER_EXPANDED, false, "media_other",
                { OtherMediaSettingsFragment() }),
        )
    }

    private fun ensureChildAttached(containerId: Int, tag: String, factory: () -> Fragment) {
        if (childFragmentManager.findFragmentByTag(tag) != null) return
        childFragmentManager.beginTransaction()
            .replace(containerId, factory(), tag)
            .commitNow()
    }

    private fun setupSections() {
        Timber.d("S0474: media child fragments attached lazily on section expand")
        val savedStates = getSavedSectionStates()
        buildSections().forEach { section ->
            val factory = section.factory
            if (factory == null) {
                section.header.isVisible = false
                section.container.isVisible = false
                return@forEach
            }

            val expanded = savedStates[section.prefKey] ?: section.defaultExpanded
            if (expanded) {
                ensureChildAttached(section.container.id, section.tag, factory)
            }
            section.header.setExpanded(expanded, notify = false)
            section.container.isVisible = expanded
            section.header.setOnExpandedChangeListener { isExpanded ->
                if (isExpanded) {
                    ensureChildAttached(section.container.id, section.tag, factory)
                }
                section.container.isVisible = isExpanded
                saveSectionState(section.prefKey, isExpanded)
            }
        }
    }

    fun ensureSectionExpanded(sectionId: String) {
        val section = buildSections().firstOrNull { it.tag == "media_$sectionId" } ?: return
        val factory = section.factory ?: return
        if (!section.header.isVisible) return
        // Search navigation can target a collapsed section whose child was never attached.
        // commitNow() makes the child's views exist immediately so navigateToTarget finds them.
        ensureChildAttached(section.container.id, section.tag, factory)
        section.header.setExpanded(true)
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
