package com.sza.fastmediasorter.ui.settings.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.capability.CapabilityAvailability
import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.core.xr.VrMediaSectionContract
import com.sza.fastmediasorter.databinding.FragmentSettingsMediaContainerBinding
import com.sza.fastmediasorter.ui.common.widget.CollapsibleSectionHeader
import com.sza.fastmediasorter.ui.common.widget.CollapsibleSectionsManager
import com.sza.fastmediasorter.ui.settings.SettingsViewModel
import com.sza.fastmediasorter.util.showBoundTo
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
// S1161: see GeneralSettingsFragment - the collapsed-group grid is installed by the base class, so every
// settings tab gets the landscape columns rather than only Management.
class MediaSettingsFragment : BaseSettingsFragment() {

    /**
     * S0249 Phase 04: optional VR media section. Provided as `NoOpVrMediaSectionContract` on
     * phone-only flavors (`isAvailable = false` → section hidden) and as
     * `VrMediaSectionContractImpl` on vr/noLegal flavors (`isAvailable = true` → section
     * visible with disabled toggle + advisory until XR runtime is detected).
     */
    @Inject lateinit var vrMediaSection: VrMediaSectionContract

    @Inject lateinit var mediaCapabilities: MediaCapabilities

    @Inject lateinit var capabilityAvailability: CapabilityAvailability

    private var _binding: FragmentSettingsMediaContainerBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SettingsViewModel by activityViewModels()
    
    // S0535: unified collapsible groups - one orchestrator + consolidated store replaces the
    // fragment-local section state machine; lazy child-fragment attach is kept via the expand hook.
    private val sectionsManager by lazy { CollapsibleSectionsManager(requireContext()) }

    private data class MediaChildSection(
        val header: CollapsibleSectionHeader,
        val container: View,
        val key: String,
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
        checkAndExpandSectionFromIntent()
    }

    /**
     * S0780: deep-link from the streams-panel "Configure" entry - expand the Streams group and scroll
     * its header into view. The extra is consumed so it does not re-fire on rotation. Settings types
     * are referenced fully-qualified to keep this baselined file's import block untouched (detekt).
     */
    private fun checkAndExpandSectionFromIntent() {
        val activityIntent = requireActivity().intent
        val key = com.sza.fastmediasorter.ui.settings.SettingsActivity.EXTRA_EXPAND_SECTION
        val sectionId = activityIntent.getStringExtra(key) ?: return
        if (sectionId != com.sza.fastmediasorter.ui.settings.SettingsActivity.SECTION_STREAMS) return
        activityIntent.removeExtra(key)
        ensureSectionExpanded(sectionId)
        val header = binding.headerStreams
        header.post {
            val bounds = android.graphics.Rect(0, 0, header.width, header.height)
            header.requestRectangleOnScreen(bounds, false)
        }
    }

    private fun setupResetSection() {
        binding.btnResetMediaSection.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_FastMediaSorter_MaterialAlertDialog_Destructive)
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
                .showBoundTo(this@MediaSettingsFragment)
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
            MediaChildSection(binding.headerImages, binding.containerImages, "media__images", false, "media_images",
                if (mediaCapabilities.supportsImages) ({ ImagesSettingsFragment() }) else null),
            MediaChildSection(binding.headerVideo, binding.containerVideo, "media__video", false, "media_video",
                if (mediaCapabilities.supportsVideo) ({ VideoSettingsFragment() }) else null),
            // S0249: VR section defaults to expanded (preserved across the unification).
            MediaChildSection(binding.headerVr, binding.containerVr, "media__vr", true, "media_vr",
                if (vrFragment != null) ({ vrFragment }) else null),
            MediaChildSection(binding.headerAudio, binding.containerAudio, "media__audio", false, "media_audio",
                if (mediaCapabilities.supportsAudio) ({ AudioSettingsFragment() }) else null),
            MediaChildSection(binding.headerDocuments, binding.containerDocuments, "media__documents", false, "media_documents",
                if (mediaCapabilities.supportsDocuments) ({ DocumentsSettingsFragment() }) else null),
            MediaChildSection(binding.headerOther, binding.containerOther, "media__other", false, "media_other",
                { OtherMediaSettingsFragment() }),
            MediaChildSection(binding.headerStreams, binding.containerStreams, "media__streams", false, "media_streams",
                if (capabilityAvailability.isStreamsAvailable()) ({ StreamsSettingsFragment() }) else null),
        )
    }

    private fun ensureChildAttached(containerId: Int, tag: String, factory: () -> Fragment) {
        if (childFragmentManager.findFragmentByTag(tag) != null) return
        childFragmentManager.beginTransaction()
            .replace(containerId, factory(), tag)
            .commitNow()
    }

    private fun setupSections() {
        buildSections().forEach { section ->
            val factory = section.factory
            if (factory == null) {
                section.header.isVisible = false
                section.container.isVisible = false
                return@forEach
            }
            sectionsManager.register(
                header = section.header,
                container = section.container,
                key = section.key,
                defaultExpanded = section.defaultExpanded,
            ) { expanded ->
                // Lazy first-expand attach: build the child only when the section becomes visible.
                if (expanded) ensureChildAttached(section.container.id, section.tag, factory)
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
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
