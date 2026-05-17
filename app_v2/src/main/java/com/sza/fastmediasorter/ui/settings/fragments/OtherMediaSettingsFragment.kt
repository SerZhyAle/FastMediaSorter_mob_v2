package com.sza.fastmediasorter.ui.settings.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.FragmentSettingsOtherBinding
import com.sza.fastmediasorter.ui.settings.SettingsViewModel
import com.sza.fastmediasorter.utils.collectOnLifecycle

class OtherMediaSettingsFragment : Fragment() {

    private var _binding: FragmentSettingsOtherBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: SettingsViewModel by activityViewModels()
    private var isUpdatingFromSettings = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsOtherBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        applyFlavorRestrictions()
        setupViews()
        observeData()
    }
    
    /**
     * Hide UI elements that are not supported by the current device hardware / Android version.
     * OCR availability is decided by [com.sza.fastmediasorter.core.util.DeviceCapabilities],
     * which uses physical RAM + API level — never the per-process heap limit. This is the
     * correct signal for a feature like ML Kit text recognition; using [MemoryTier] here
     * incorrectly disabled OCR on Quest 3 / canonical emulator (7 GB RAM, 512 MB heap).
     */
    private fun applyDeviceCapabilityRestrictions() {
        val support = com.sza.fastmediasorter.core.util.DeviceCapabilities.ocrSupport(requireContext())
        if (support is com.sza.fastmediasorter.core.util.DeviceCapabilities.OcrSupport.Unsupported) {
            val ocrContainer = binding.switchEnableOcr.parent as? View
            ocrContainer?.let {
                // Disable OCR switch and show explanation
                binding.switchEnableOcr.isEnabled = false
                binding.switchEnableOcr.isChecked = false
                binding.tvOcrSummary.isVisible = true
                val reasonStringRes = when (support.reason) {
                    com.sza.fastmediasorter.core.util.DeviceCapabilities.OcrUnavailableReason.OS_TOO_OLD ->
                        com.sza.fastmediasorter.R.string.ocr_unavailable_reason_os
                    com.sza.fastmediasorter.core.util.DeviceCapabilities.OcrUnavailableReason.DEVICE_TOO_WEAK ->
                        com.sza.fastmediasorter.R.string.ocr_unavailable_reason_ram
                }
                binding.tvOcrSummary.text = getString(
                    com.sza.fastmediasorter.R.string.ocr_requires_newer_device,
                    getString(reasonStringRes),
                    support.apiLevel
                )
                binding.tvOcrSummary.alpha = 1.0f

                // Force-disable OCR in settings only if the device truly can't run it
                val current = viewModel.settings.value
                if (current.enableOcr) {
                    viewModel.updateSettings(current.copy(enableOcr = false))
                }

                timber.log.Timber.i(
                    "OtherMediaSettingsFragment: OCR disabled - reason=${support.reason}, " +
                        "API=${support.apiLevel}, totalRAM=${"%.2f".format(support.totalRamGb)}GB, " +
                        "isLowRamDevice=${support.isLowRamDevice}"
                )
            }

            // Hide OCR font settings
            binding.layoutOcrFontSize?.isVisible = false
            binding.layoutOcrFontFamily?.isVisible = false
        }
    }
    
    /**
     * Hide UI elements that are not supported by the current product flavor.
     * Translation and OCR features require ENABLE_TRANSLATION=true.
     */
    private fun applyFlavorRestrictions() {
        // Translation and OCR features are only available when ENABLE_TRANSLATION is true
        if (!BuildConfig.ENABLE_TRANSLATION) {
            // Hide translation toggle and its parent container
            (binding.switchEnableTranslation.parent as? View)?.isVisible = false
            binding.layoutTranslationLanguages.isVisible = false
            binding.layoutTranslationLensStyle.isVisible = false
            
            // Hide Google Lens parent container
            (binding.switchEnableGoogleLens.parent as? View)?.isVisible = false
            
            // Hide OCR switch parent container and summary
            (binding.switchEnableOcr.parent as? View)?.isVisible = false
            binding.tvOcrSummary.isVisible = false
            binding.layoutOcrFontSize?.isVisible = false
            binding.layoutOcrFontFamily?.isVisible = false
        } else {
            // Flavor supports OCR, but check device capability
            applyDeviceCapabilityRestrictions()
        }
    }

    private fun setupViews() {
        // Translation toggle
        binding.switchEnableTranslation.setOnCheckedChangeListener { _, isChecked ->
            if (!isUpdatingFromSettings) {
                val current = viewModel.settings.value
                viewModel.updateSettings(current.copy(enableTranslation = isChecked))
                updateTranslationVisibility(isChecked)
            }
        }

        // Translation language spinners
        setupLanguageSpinners()

        // Swap languages button
        binding.btnSwapLanguages.setOnClickListener {
            val sourceCode = viewModel.settings.value.translationSourceLanguage
            val targetCode = viewModel.settings.value.translationTargetLanguage
            
            // Cannot swap if source is auto-detect
            if (sourceCode != "auto") {
                viewModel.updateSettings(viewModel.settings.value.copy(
                    translationSourceLanguage = targetCode,
                    translationTargetLanguage = sourceCode
                ))
            }
        }

        // Translation Lens Style
        binding.switchTranslationLensStyle.setOnCheckedChangeListener { _, isChecked ->
            if (!isUpdatingFromSettings) {
                val current = viewModel.settings.value
                viewModel.updateSettings(current.copy(translationLensStyle = isChecked))
            }
        }

        // Google Lens
        binding.switchEnableGoogleLens.setOnCheckedChangeListener { _, isChecked ->
            if (!isUpdatingFromSettings) {
                val current = viewModel.settings.value
                viewModel.updateSettings(current.copy(enableGoogleLens = isChecked))
            }
        }

        // OCR
        binding.switchEnableOcr.setOnCheckedChangeListener { _, isChecked ->
            if (!isUpdatingFromSettings) {
                val current = viewModel.settings.value
                viewModel.updateSettings(current.copy(enableOcr = isChecked))
                updateOcrVisibility(isChecked)
            }
        }
        
        // OCR Font Settings
        setupOcrFontSpinners()

        // Help button click handlers
        binding.iconHelpTranslation?.setOnClickListener {
            com.sza.fastmediasorter.ui.dialog.TooltipDialog.show(
                requireContext(),
                com.sza.fastmediasorter.R.string.tooltip_translation_title,
                com.sza.fastmediasorter.R.string.tooltip_translation_message
            )
        }

        binding.iconHelpTranslationBlocks?.setOnClickListener {
            com.sza.fastmediasorter.ui.dialog.TooltipDialog.show(
                requireContext(),
                com.sza.fastmediasorter.R.string.tooltip_translation_blocks_title,
                com.sza.fastmediasorter.R.string.tooltip_translation_blocks_message
            )
        }
    }

    private fun setupLanguageSpinners() {
        val sourceLanguages = arrayOf(
            "Auto-detect", "English", "Russian", "Ukrainian", "Spanish", "French", "German",
            "Chinese", "Japanese", "Korean", "Arabic", "Portuguese", "Hindi",
            "Italian", "Turkish", "Polish", "Dutch", "Thai", "Persian", "Greek",
            "Indonesian", "Maltese"
        )
        
        val targetLanguages = arrayOf(
            "English", "Russian", "Ukrainian", "Spanish", "French", "German",
            "Chinese", "Japanese", "Korean", "Arabic", "Portuguese", "Hindi",
            "Italian", "Turkish", "Polish", "Dutch", "Thai", "Persian", "Greek",
            "Indonesian", "Maltese"
        )

        val sourceAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, sourceLanguages)
        sourceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        
        val targetAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, targetLanguages)
        targetAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        binding.spinnerTranslationSourceLanguage.adapter = sourceAdapter
        binding.spinnerTranslationTargetLanguage.adapter = targetAdapter

        binding.spinnerTranslationSourceLanguage.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!isUpdatingFromSettings) {
                    val langCode = getLanguageCode(position, isSource = true)
                    val current = viewModel.settings.value
                    viewModel.updateSettings(current.copy(translationSourceLanguage = langCode))
                }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        binding.spinnerTranslationTargetLanguage.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!isUpdatingFromSettings) {
                    val langCode = getLanguageCode(position, isSource = false)
                    val current = viewModel.settings.value
                    viewModel.updateSettings(current.copy(translationTargetLanguage = langCode))
                }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }

    private fun getLanguageCode(position: Int, isSource: Boolean = true): String {
        return if (isSource) {
            when (position) {
                0 -> "auto"
                1 -> "en"
                2 -> "ru"
                3 -> "uk"
                4 -> "es"
                5 -> "fr"
                6 -> "de"
                7 -> "zh"
                8 -> "ja"
                9 -> "ko"
                10 -> "ar"
                11 -> "pt"
                12 -> "hi"
                13 -> "it"
                14 -> "tr"
                15 -> "pl"
                16 -> "nl"
                17 -> "th"
                18 -> "fa"
                19 -> "el"
                20 -> "id"
                21 -> "mt"
                else -> "auto"
            }
        } else {
            when (position) {
                0 -> "en"
                1 -> "ru"
                2 -> "uk"
                3 -> "es"
                4 -> "fr"
                5 -> "de"
                6 -> "zh"
                7 -> "ja"
                8 -> "ko"
                9 -> "ar"
                10 -> "pt"
                11 -> "hi"
                12 -> "it"
                13 -> "tr"
                14 -> "pl"
                15 -> "nl"
                16 -> "th"
                17 -> "fa"
                18 -> "el"
                19 -> "id"
                20 -> "mt"
                else -> "en"
            }
        }
    }

    private fun getLanguagePosition(code: String, isSource: Boolean = true): Int {
        return if (isSource) {
            when (code) {
                "auto" -> 0
                "en" -> 1
                "ru" -> 2
                "uk" -> 3
                "es" -> 4
                "fr" -> 5
                "de" -> 6
                "zh" -> 7
                "ja" -> 8
                "ko" -> 9
                "ar" -> 10
                "pt" -> 11
                "hi" -> 12
                "it" -> 13
                "tr" -> 14
                "pl" -> 15
                "nl" -> 16
                "th" -> 17
                "fa" -> 18
                "el" -> 19
                "id" -> 20
                "mt" -> 21
                else -> 0
            }
        } else {
            when (code) {
                "en" -> 0
                "ru" -> 1
                "uk" -> 2
                "es" -> 3
                "fr" -> 4
                "de" -> 5
                "zh" -> 6
                "ja" -> 7
                "ko" -> 8
                "ar" -> 9
                "pt" -> 10
                "hi" -> 11
                "it" -> 12
                "tr" -> 13
                "pl" -> 14
                "nl" -> 15
                "th" -> 16
                "fa" -> 17
                "el" -> 18
                "id" -> 19
                "mt" -> 20
                else -> 0
            }
        }
    }

    private fun updateTranslationVisibility(enabled: Boolean) {
        binding.layoutTranslationLanguages.isVisible = enabled
        binding.layoutTranslationLensStyle.isVisible = enabled
    }
    
    private fun setupOcrFontSpinners() {
        // Font Size
        val fontSizes = arrayOf(
            getString(R.string.font_size_auto),
            getString(R.string.font_size_minimum),
            getString(R.string.font_size_small),
            getString(R.string.font_size_medium),
            getString(R.string.font_size_large),
            getString(R.string.font_size_huge)
        )
        val fontSizeAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, fontSizes)
        fontSizeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerOcrFontSize?.adapter = fontSizeAdapter
        
        binding.spinnerOcrFontSize?.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!isUpdatingFromSettings) {
                    val fontSizeValue = when (position) {
                        0 -> "AUTO"
                        1 -> "MINIMUM"
                        2 -> "SMALL"
                        3 -> "MEDIUM"
                        4 -> "LARGE"
                        5 -> "HUGE"
                        else -> "AUTO"
                    }
                    val current = viewModel.settings.value
                    viewModel.updateSettings(current.copy(ocrDefaultFontSize = fontSizeValue))
                }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
        
        // Font Family
        val fontFamilies = arrayOf(
            getString(R.string.font_family_default),
            getString(R.string.font_family_serif),
            getString(R.string.font_family_monospace)
        )
        val fontFamilyAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, fontFamilies)
        fontFamilyAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerOcrFontFamily?.adapter = fontFamilyAdapter
        
        binding.spinnerOcrFontFamily?.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!isUpdatingFromSettings) {
                    val fontFamilyValue = when (position) {
                        0 -> "DEFAULT"
                        1 -> "SERIF"
                        2 -> "MONOSPACE"
                        else -> "DEFAULT"
                    }
                    val current = viewModel.settings.value
                    viewModel.updateSettings(current.copy(ocrDefaultFontFamily = fontFamilyValue))
                }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }
    
    private fun updateOcrVisibility(enabled: Boolean) {
        binding.layoutOcrFontSize?.isVisible = enabled
        binding.layoutOcrFontFamily?.isVisible = enabled
    }

    private fun observeData() {
        collectOnLifecycle(viewModel.settings) { settings ->
            isUpdatingFromSettings = true

            binding.switchEnableTranslation.isChecked = settings.enableTranslation
            updateTranslationVisibility(settings.enableTranslation)

            binding.spinnerTranslationSourceLanguage.setSelection(getLanguagePosition(settings.translationSourceLanguage, isSource = true))
            binding.spinnerTranslationTargetLanguage.setSelection(getLanguagePosition(settings.translationTargetLanguage, isSource = false))

            binding.switchTranslationLensStyle.isChecked = settings.translationLensStyle
            binding.switchEnableGoogleLens.isChecked = settings.enableGoogleLens
            binding.switchEnableOcr.isChecked = settings.enableOcr
            updateOcrVisibility(settings.enableOcr)

            // OCR Font Settings
            val fontSizePosition = when (settings.ocrDefaultFontSize) {
                "AUTO" -> 0
                "MINIMUM" -> 1
                "SMALL" -> 2
                "MEDIUM" -> 3
                "LARGE" -> 4
                "HUGE" -> 5
                else -> 0
            }
            binding.spinnerOcrFontSize?.setSelection(fontSizePosition)

            val fontFamilyPosition = when (settings.ocrDefaultFontFamily) {
                "DEFAULT" -> 0
                "SERIF" -> 1
                "MONOSPACE" -> 2
                else -> 0 // Default to DEFAULT
            }
            binding.spinnerOcrFontFamily?.setSelection(fontFamilyPosition)

            isUpdatingFromSettings = false
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
