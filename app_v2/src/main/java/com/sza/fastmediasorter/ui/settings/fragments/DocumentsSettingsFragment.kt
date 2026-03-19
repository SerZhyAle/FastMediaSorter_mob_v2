package com.sza.fastmediasorter.ui.settings.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.FragmentSettingsDocumentsBinding
import com.sza.fastmediasorter.ui.settings.SettingsViewModel
import com.sza.fastmediasorter.ui.settings.helpers.DefaultPlayerHelper
import kotlinx.coroutines.launch

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
        // EPUB feature is only available when ENABLE_EPUB is true
        if (!BuildConfig.ENABLE_EPUB) {
            // Hide EPUB switch and its parent container
            (binding.switchSupportEpub.parent as? View)?.isVisible = false
        }
    }

    private fun setupViews() {
        // Support Text
        bindSwitch(binding.switchSupportText) { isChecked ->
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(supportText = isChecked))
            binding.layoutShowTextLineNumbers.isVisible = isChecked
        }

        // Show Text Line Numbers
        bindSwitch(binding.switchShowTextLineNumbers) { isChecked ->
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(showTextLineNumbers = isChecked))
        }

        // Support PDF
        bindSwitch(binding.switchSupportPdf) { isChecked ->
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(supportPdf = isChecked))
            binding.layoutShowPdfThumbnails.isVisible = isChecked
        }

        // Show PDF Thumbnails
        bindSwitch(binding.switchShowPdfThumbnails) { isChecked ->
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(showPdfThumbnails = isChecked))
        }

        // Support EPUB
        bindSwitch(binding.switchSupportEpub) { isChecked ->
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(supportEpub = isChecked))
        }

        // Help button click handler
        binding.iconHelpLineNumbers.setOnClickListener {
            com.sza.fastmediasorter.ui.dialog.TooltipDialog.show(
                requireContext(),
                com.sza.fastmediasorter.R.string.tooltip_line_numbers_title,
                com.sza.fastmediasorter.R.string.tooltip_line_numbers_message
            )
        }

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
            DefaultPlayerHelper.showSetDefaultDialogForType(this, "application/pdf")
        }
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.settings.collect { settings ->
                    withSettingsUpdate {
                        val isAllFilesEnabled = settings.allFiles

                        setSwitchChecked(binding.switchSupportText, isAllFilesEnabled || settings.supportText)
                        binding.switchSupportText.isEnabled = !isAllFilesEnabled

                        setSwitchChecked(binding.switchSupportPdf, isAllFilesEnabled || settings.supportPdf)
                        binding.switchSupportPdf.isEnabled = !isAllFilesEnabled

                        setSwitchChecked(binding.switchSupportEpub, isAllFilesEnabled || settings.supportEpub)
                        binding.switchSupportEpub.isEnabled = !isAllFilesEnabled

                        setSwitchChecked(binding.switchShowTextLineNumbers, settings.showTextLineNumbers)
                        setSwitchChecked(binding.switchShowPdfThumbnails, settings.showPdfThumbnails)

                        binding.layoutShowTextLineNumbers.isVisible = settings.supportText
                        binding.layoutShowPdfThumbnails.isVisible = settings.supportPdf
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
