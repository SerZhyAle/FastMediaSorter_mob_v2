package com.sza.fastmediasorter.ui.settings.fragments

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
import com.sza.fastmediasorter.databinding.FragmentSettingsMediaContainerBinding
import com.sza.fastmediasorter.ui.settings.SettingsViewModel

class MediaSettingsFragment : Fragment() {

    private var _binding: FragmentSettingsMediaContainerBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SettingsViewModel by activityViewModels()

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
        bindSectionToggle(binding.headerImages, binding.containerImages, getString(R.string.settings_category_images))
        bindSectionToggle(binding.headerVideo, binding.containerVideo, getString(R.string.settings_category_video))
        bindSectionToggle(binding.headerAudio, binding.containerAudio, getString(R.string.settings_category_audio))
        bindSectionToggle(binding.headerDocuments, binding.containerDocuments, getString(R.string.settings_category_documents))
        bindSectionToggle(binding.headerOther, binding.containerOther, getString(R.string.settings_category_other))
    }

    fun ensureSectionExpanded(sectionId: String) {
        when (sectionId) {
            "images" -> expandSection(binding.headerImages, binding.containerImages, getString(R.string.settings_category_images))
            "video" -> expandSection(binding.headerVideo, binding.containerVideo, getString(R.string.settings_category_video))
            "audio" -> expandSection(binding.headerAudio, binding.containerAudio, getString(R.string.settings_category_audio))
            "documents" -> expandSection(binding.headerDocuments, binding.containerDocuments, getString(R.string.settings_category_documents))
            "other" -> expandSection(binding.headerOther, binding.containerOther, getString(R.string.settings_category_other))
        }
    }

    private fun expandSection(header: android.widget.TextView, content: View, title: String) {
        if (!header.isVisible) {
            return
        }

        if (!content.isVisible) {
            content.isVisible = true
            updateHeader(header, title, true)
        }
    }

    private fun bindSectionToggle(header: android.widget.TextView, content: View, title: String) {
        if (!header.isVisible || !content.isVisible) return

        header.setOnClickListener {
            val expanded = !content.isVisible
            content.isVisible = expanded
            updateHeader(header, title, expanded)
        }
    }

    private fun updateHeader(header: android.widget.TextView, title: String, expanded: Boolean) {
        val prefix = if (expanded) "▼" else "▶"
        header.text = "$prefix $title"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
