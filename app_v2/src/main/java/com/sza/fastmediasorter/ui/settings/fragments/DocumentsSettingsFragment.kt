package com.sza.fastmediasorter.ui.settings.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import com.sza.fastmediasorter.utils.collectOnLifecycle
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.common.MediaTypeUtils
import com.sza.fastmediasorter.databinding.FragmentSettingsDocumentsBinding
import com.sza.fastmediasorter.di.MediaCapabilitiesEntryPoint
import com.sza.fastmediasorter.ui.settings.exitAllFilesForManualSupportToggle
import com.sza.fastmediasorter.ui.settings.SettingsViewModel
import com.sza.fastmediasorter.ui.settings.helpers.DefaultPlayerHelper
import dagger.hilt.android.EntryPointAccessors

class DocumentsSettingsFragment : BaseSettingsFragment() {

    private var _binding: FragmentSettingsDocumentsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SettingsViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsDocumentsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        applyFlavorRestrictions()
        setupViews()
        observeData()
    }

    /**
     * Hide UI elements that are not supported by the current product flavor.
     * EPUB support requires ENABLE_EPUB=true.
     */
    private fun applyFlavorRestrictions() {
        // BaseSettingsFragment is not @AndroidEntryPoint; resolve capabilities via Hilt entry point (Rule 14).
        val caps = EntryPointAccessors.fromApplication(
            requireContext().applicationContext,
            MediaCapabilitiesEntryPoint::class.java
        ).mediaCapabilities()
        if (!caps.supportsEpub) {
            // After migration the row IS the visible element; no extra wrapper container.
            binding.rowSupportEpub.isVisible = false
        }
        binding.rowSupportOfficeDocuments.isVisible = MediaTypeUtils.OFFICE_DOCUMENT_EXTENSIONS.isNotEmpty()
    }

    private fun setupViews() {
        bindSwitch(binding.rowSupportText) { isChecked ->
            viewModel.updateSettings { current ->
                current
                    .exitAllFilesForManualSupportToggle(isChecked)
                    .copy(supportText = isChecked)
            }
            binding.rowShowTextLineNumbers.isVisible = isChecked
        }

        bindSwitch(binding.rowShowTextLineNumbers) { isChecked ->
            viewModel.updateSettings { it.copy(showTextLineNumbers = isChecked) }
        }

        bindSwitch(binding.rowSupportPdf) { isChecked ->
            viewModel.updateSettings { current ->
                current
                    .exitAllFilesForManualSupportToggle(isChecked)
                    .copy(supportPdf = isChecked)
            }
            binding.rowShowPdfThumbnails.isVisible = isChecked
        }

        bindSwitch(binding.rowShowPdfThumbnails) { isChecked ->
            viewModel.updateSettings { it.copy(showPdfThumbnails = isChecked) }
        }

        bindSwitch(binding.rowSupportEpub) { isChecked ->
            viewModel.updateSettings { current ->
                current
                    .exitAllFilesForManualSupportToggle(isChecked)
                    .copy(supportEpub = isChecked)
            }
        }

        bindSwitch(binding.rowSupportOfficeDocuments) { isChecked ->
            viewModel.updateSettings { current ->
                current
                    .exitAllFilesForManualSupportToggle(isChecked)
                    .copy(supportOfficeDocuments = isChecked)
            }
        }

        // Help payload for line-numbers row is now wired inline via str_helpTitle/str_helpMessage
        // and opened by SettingsToggleRow's built-in helper button.

        setupDefaultPlayerButton()
    }

    override fun onResume() {
        super.onResume()
        _binding?.let {
            DefaultPlayerHelper.applyButtonState(
                it.btnSetDefaultDocsViewer, requireContext(), R.string.settings_set_default_docs_viewer
            )
        }
    }

    private fun setupDefaultPlayerButton() {
        DefaultPlayerHelper.applyButtonState(
            binding.btnSetDefaultDocsViewer, requireContext(), R.string.settings_set_default_docs_viewer
        )
        binding.btnSetDefaultDocsViewer.setOnClickListener {
            val current = viewModel.settings.value
            if (!current.isPrimaryMediaPlayer) {
                viewModel.updateSettings { it.copy(isPrimaryMediaPlayer = true) }
            }
            DefaultPlayerHelper.showSetDefaultDocumentDialog(this)
        }
    }

    private fun observeData() {
        collectOnLifecycle(viewModel.settings) { settings ->
            withSettingsUpdate {
                val isAllFilesEnabled = settings.allFiles

                setSwitchChecked(binding.rowSupportText, isAllFilesEnabled || settings.supportText)

                setSwitchChecked(binding.rowSupportPdf, isAllFilesEnabled || settings.supportPdf)

                setSwitchChecked(binding.rowSupportEpub, isAllFilesEnabled || settings.supportEpub)

                setSwitchChecked(binding.rowSupportOfficeDocuments, isAllFilesEnabled || settings.supportOfficeDocuments)

                setSwitchChecked(binding.rowShowTextLineNumbers, settings.showTextLineNumbers)
                setSwitchChecked(binding.rowShowPdfThumbnails, settings.showPdfThumbnails)

                binding.rowShowTextLineNumbers.isVisible = settings.supportText
                binding.rowShowPdfThumbnails.isVisible = settings.supportPdf
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
