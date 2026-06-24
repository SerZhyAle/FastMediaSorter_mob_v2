package com.sza.fastmediasorter.ui.settings.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.capability.CapabilityAvailability
import com.sza.fastmediasorter.databinding.FragmentSettingsOtherBinding
import com.sza.fastmediasorter.domain.delivery.DeliverableSet
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.TranslationModelPrewarmStatus
import com.sza.fastmediasorter.ui.delivery.DeliveryEnableInterceptor
import com.sza.fastmediasorter.ui.delivery.ExtensionsManagerFragment
import com.sza.fastmediasorter.ui.dialog.SearchableLanguagePickerDialog
import com.sza.fastmediasorter.ui.dialog.SimpleValueChoiceDialog
import com.sza.fastmediasorter.ui.settings.SettingsViewModel
import com.sza.fastmediasorter.utils.collectOnLifecycle
import com.sza.fastmediasorter.ui.player.helpers.LanguageFlagFormatter
import com.sza.fastmediasorter.ui.player.helpers.TranslationLanguageCatalog
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class OtherMediaSettingsFragment : BaseSettingsFragment() {

    @Inject
    lateinit var deliveryEnableInterceptor: DeliveryEnableInterceptor

    @Inject
    lateinit var capabilityAvailability: CapabilityAvailability

    private var _binding: FragmentSettingsOtherBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SettingsViewModel by activityViewModels()

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
     * which uses physical RAM + API level - never the per-process heap limit. This is the
     * correct signal for a feature like ML Kit text recognition; using [MemoryTier] here
     * incorrectly disabled OCR on Quest 3 / canonical emulator (7 GB RAM, 512 MB heap).
     */
    private fun applyDeviceCapabilityRestrictions() {
        val support = com.sza.fastmediasorter.core.util.DeviceCapabilities.ocrSupport(requireContext())
        if (support is com.sza.fastmediasorter.core.util.DeviceCapabilities.OcrSupport.Unsupported) {
            // After migration the row IS the visible element; no extra wrapper container.
            binding.rowEnableOcr.isEnabled = false
            binding.rowEnableOcr.setCheckedSilently(false)
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

            binding.rowOcrFontSize?.isVisible = false
            binding.rowOcrFontFamily?.isVisible = false
        }
    }

    /**
     * Hide UI elements that are not supported by the current product flavor.
     * Translation and OCR features require ENABLE_TRANSLATION=true.
     */
    private fun applyFlavorRestrictions() {
        // Translation and OCR rows are visible only when the build compiles the translation capability.
        if (!capabilityAvailability.isTranslationAvailable()) {
            // After migration the row IS the visible element; no extra wrapper container.
            binding.rowEnableTranslation.isVisible = false
            binding.layoutTranslationLanguages.isVisible = false
            binding.layoutTranslationPrewarmStatus.isVisible = false
            binding.rowTranslationLensStyle.isVisible = false

            // Hide OCR row and summary
            binding.rowEnableOcr.isVisible = false
            binding.tvOcrSummary.isVisible = false
            binding.rowOcrFontSize?.isVisible = false
            binding.rowOcrFontFamily?.isVisible = false
        } else {
            // Flavor supports OCR, but check device capability
            applyDeviceCapabilityRestrictions()
        }
    }

    private fun setupViews() {
        // Translation toggle - help payload folded into the row. Turning it ON gates on the
        // TRANSLATION set being installed (S0386 Phase 06); on refusal the toggle reverts to OFF.
        bindSwitch(binding.rowEnableTranslation) { isChecked ->
            if (isChecked) {
                deliveryEnableInterceptor.requireInstalled(
                    host = this,
                    set = DeliverableSet.TRANSLATION,
                    onReady = {
                        viewModel.updateSettings(viewModel.settings.value.copy(enableTranslation = true))
                        updateTranslationVisibility(true)
                    },
                    onUnavailable = { setSwitchChecked(binding.rowEnableTranslation, false) }
                )
            } else {
                viewModel.updateSettings(viewModel.settings.value.copy(enableTranslation = false))
                updateTranslationVisibility(false)
            }
        }

        setupLanguageSelectors()
        binding.btnTranslationPrewarmRetry.contentDescription =
            getString(R.string.translation_model_prewarm_retry)
        binding.btnTranslationPrewarmRetry.setOnClickListener {
            viewModel.retryTranslationModelPrewarm()
        }

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

        // Translation Lens Style - help payload folded into the row
        bindSwitch(binding.rowTranslationLensStyle) { isChecked ->
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(translationLensStyle = isChecked))
        }

        // OCR - turning it ON gates on the OCR_ENGINES set being installed (S0386 Phase 06);
        // on refusal the toggle reverts to OFF.
        bindSwitch(binding.rowEnableOcr) { isChecked ->
            if (isChecked) {
                deliveryEnableInterceptor.requireInstalled(
                    host = this,
                    set = DeliverableSet.OCR_ENGINES,
                    onReady = {
                        val current = viewModel.settings.value
                        viewModel.updateSettings(current.copy(enableOcr = true))
                        updateOcrVisibility(true, current.ocrEngineType)
                    },
                    onUnavailable = { setSwitchChecked(binding.rowEnableOcr, false) }
                )
            } else {
                val current = viewModel.settings.value
                viewModel.updateSettings(current.copy(enableOcr = false))
                updateOcrVisibility(false, current.ocrEngineType)
            }
        }

        // OCR Font Settings
        setupOcrFontSpinners()

        // OCR Engine Settings (S0288)
        setupOcrEngineSpinners()

        // OCR language models (Russian/Ukrainian) are managed in the Downloadable Extensions screen
        // (S0386 Phase 12.3) - the inline download UI was removed from this group to avoid duplication.
        binding.layoutExtensionsManager?.setOnRowClickListener {
            // This fragment lives inside the settings ViewPager, whose child FragmentManager does
            // not own android.R.id.content; use the activity FragmentManager so the full-screen
            // overlay attaches to a real container (else: "No view found for id android:id/content").
            requireActivity().supportFragmentManager.beginTransaction()
                .add(android.R.id.content, ExtensionsManagerFragment(), ExtensionsManagerFragment.TAG)
                .addToBackStack(null)
                .commit()
        }
    }

    private fun setupLanguageSelectors() {
        binding.spinnerTranslationSourceLanguage.setOnClickListener {
            showLanguagePicker(SearchableLanguagePickerDialog.Mode.SOURCE)
        }
        binding.spinnerTranslationTargetLanguage.setOnClickListener {
            showLanguagePicker(SearchableLanguagePickerDialog.Mode.TARGET)
        }
    }

    private fun showLanguagePicker(mode: SearchableLanguagePickerDialog.Mode) {
        val settings = viewModel.settings.value
        val selectedCode = when (mode) {
            SearchableLanguagePickerDialog.Mode.SOURCE -> settings.translationSourceLanguage
            SearchableLanguagePickerDialog.Mode.TARGET -> settings.translationTargetLanguage
        }
        val tag = "${SearchableLanguagePickerDialog.TAG}_${mode.name}"
        if (childFragmentManager.findFragmentByTag(tag) != null) return

        SearchableLanguagePickerDialog.newInstance(
            selectedCode = selectedCode,
            mode = mode,
            interfaceLanguage = settings.language
        ) { language ->
            val current = viewModel.settings.value
            val updated = when (mode) {
                SearchableLanguagePickerDialog.Mode.SOURCE ->
                    current.copy(translationSourceLanguage = language.code)
                SearchableLanguagePickerDialog.Mode.TARGET ->
                    current.copy(translationTargetLanguage = language.code)
            }
            viewModel.updateSettings(updated)
        }.show(childFragmentManager, tag)
    }

    private fun updateLanguageSelectors(settings: AppSettings) {
        applyLanguageLabel(
            view = binding.spinnerTranslationSourceLanguage,
            code = settings.translationSourceLanguage,
            interfaceLanguage = settings.language,
            titleRes = R.string.translation_source_language
        )
        applyLanguageLabel(
            view = binding.spinnerTranslationTargetLanguage,
            code = settings.translationTargetLanguage,
            interfaceLanguage = settings.language,
            titleRes = R.string.translation_target_language
        )
    }

    private fun applyLanguageLabel(
        view: android.widget.TextView,
        code: String,
        interfaceLanguage: String,
        @androidx.annotation.StringRes titleRes: Int
    ) {
        val displayLocale = Locale.forLanguageTag(interfaceLanguage)
        val item = TranslationLanguageCatalog.findLanguage(code, displayLocale)
            ?: TranslationLanguageCatalog.findLanguage("en", displayLocale)
        if (item != null) {
            LanguageFlagFormatter.applyLabel(view, item)
            view.contentDescription = "${getString(titleRes)}: ${LanguageFlagFormatter.plainLabel(item)}"
        } else {
            val fallback = code.uppercase(Locale.ROOT)
            view.text = fallback
            view.contentDescription = "${getString(titleRes)}: $fallback"
        }
    }

    private fun updateTranslationVisibility(enabled: Boolean) {
        binding.layoutTranslationLanguages.isVisible = enabled
        binding.rowTranslationLensStyle.isVisible = enabled
    }

    // Ordered option lists for the OCR trigger rows: key = persisted value, label = localized text.
    private val ocrFontSizeOptions: List<SimpleValueChoiceDialog.Option> by lazy {
        listOf(
            SimpleValueChoiceDialog.Option("AUTO", getString(R.string.font_size_auto)),
            SimpleValueChoiceDialog.Option("MINIMUM", getString(R.string.font_size_minimum)),
            SimpleValueChoiceDialog.Option("SMALL", getString(R.string.font_size_small)),
            SimpleValueChoiceDialog.Option("MEDIUM", getString(R.string.font_size_medium)),
            SimpleValueChoiceDialog.Option("LARGE", getString(R.string.font_size_large)),
            SimpleValueChoiceDialog.Option("HUGE", getString(R.string.font_size_huge))
        )
    }

    private val ocrFontFamilyOptions: List<SimpleValueChoiceDialog.Option> by lazy {
        listOf(
            SimpleValueChoiceDialog.Option("DEFAULT", getString(R.string.font_family_default)),
            SimpleValueChoiceDialog.Option("SERIF", getString(R.string.font_family_serif)),
            SimpleValueChoiceDialog.Option("MONOSPACE", getString(R.string.font_family_monospace))
        )
    }

    private val ocrEngineOptions: List<SimpleValueChoiceDialog.Option> by lazy {
        listOf(
            SimpleValueChoiceDialog.Option("TESSERACT", getString(R.string.ocr_engine_type_tesseract)),
            SimpleValueChoiceDialog.Option("PADDLE_OCR", getString(R.string.ocr_engine_type_paddleocr))
        )
    }

    private val paddleOcrModelOptions: List<SimpleValueChoiceDialog.Option> by lazy {
        listOf(
            SimpleValueChoiceDialog.Option("CYRILLIC", getString(R.string.paddle_ocr_model_cyrillic)),
            SimpleValueChoiceDialog.Option("EAST_SLAVIC", getString(R.string.paddle_ocr_model_eslav))
        )
    }

    private fun setupOcrFontSpinners() {
        binding.rowOcrFontSize?.setOnRowClickListener {
            Timber.d("S0646: OCR font-size row tapped, opening list-choice dialog")
            val settings = viewModel.settings.value
            SimpleValueChoiceDialog(
                requireContext(),
                viewLifecycleOwner,
                title = getString(R.string.ocr_font_size),
                options = ocrFontSizeOptions,
                currentKey = settings.ocrDefaultFontSize,
                onSelected = { key ->
                    key?.let {
                        viewModel.updateSettings(viewModel.settings.value.copy(ocrDefaultFontSize = it))
                    }
                }
            ).show()
        }

        binding.rowOcrFontFamily?.setOnRowClickListener {
            Timber.d("S0646: OCR font-family row tapped, opening list-choice dialog")
            val settings = viewModel.settings.value
            SimpleValueChoiceDialog(
                requireContext(),
                viewLifecycleOwner,
                title = getString(R.string.ocr_font_family),
                options = ocrFontFamilyOptions,
                currentKey = settings.ocrDefaultFontFamily,
                onSelected = { key ->
                    key?.let {
                        viewModel.updateSettings(viewModel.settings.value.copy(ocrDefaultFontFamily = it))
                    }
                }
            ).show()
        }
    }

    private fun setupOcrEngineSpinners() {
        if (!capabilityAvailability.isOcrEngineSelectionAvailable()) {
            binding.rowOcrEngineType?.isVisible = false
            binding.rowPaddleOcrModel?.isVisible = false
            return
        }

        binding.rowOcrEngineType?.setOnRowClickListener {
            Timber.d("S0646: OCR engine-type row tapped, opening list-choice dialog")
            val settings = viewModel.settings.value
            SimpleValueChoiceDialog(
                requireContext(),
                viewLifecycleOwner,
                title = getString(R.string.ocr_engine_type),
                options = ocrEngineOptions,
                currentKey = settings.ocrEngineType,
                onSelected = { key ->
                    key?.let {
                        Timber.d("S0288: settings ocr engine selector picked engine=$it")
                        val current = viewModel.settings.value
                        viewModel.updateSettings(current.copy(ocrEngineType = it))
                        refreshOcrRowValues(current.copy(ocrEngineType = it))
                        // Preserve the engine -> model coupling: PaddleOCR model row is only relevant for PADDLE_OCR.
                        binding.rowPaddleOcrModel?.isVisible = current.enableOcr && it == "PADDLE_OCR"
                    }
                }
            ).show()
        }

        binding.rowPaddleOcrModel?.setOnRowClickListener {
            Timber.d("S0646: PaddleOCR-model row tapped, opening list-choice dialog")
            val settings = viewModel.settings.value
            SimpleValueChoiceDialog(
                requireContext(),
                viewLifecycleOwner,
                title = getString(R.string.paddle_ocr_model),
                options = paddleOcrModelOptions,
                currentKey = settings.paddleOcrModel,
                onSelected = { key ->
                    key?.let {
                        viewModel.updateSettings(viewModel.settings.value.copy(paddleOcrModel = it))
                    }
                }
            ).show()
        }
    }

    /**
     * Sets each OCR trigger row's value text to the label matching the persisted key.
     */
    private fun refreshOcrRowValues(settings: AppSettings) {
        binding.rowOcrFontSize?.setValue(labelForKey(ocrFontSizeOptions, settings.ocrDefaultFontSize))
        binding.rowOcrFontFamily?.setValue(labelForKey(ocrFontFamilyOptions, settings.ocrDefaultFontFamily))
        binding.rowOcrEngineType?.setValue(labelForKey(ocrEngineOptions, settings.ocrEngineType))
        binding.rowPaddleOcrModel?.setValue(labelForKey(paddleOcrModelOptions, settings.paddleOcrModel))
    }

    private fun labelForKey(options: List<SimpleValueChoiceDialog.Option>, key: String): String? =
        options.firstOrNull { it.key == key }?.label

    private fun updateOcrVisibility(enabled: Boolean, ocrEngineType: String = viewModel.settings.value.ocrEngineType) {
        binding.rowOcrFontSize?.isVisible = enabled
        binding.rowOcrFontFamily?.isVisible = enabled

        val showNoLegalOcr = enabled && capabilityAvailability.isOcrEngineSelectionAvailable()
        binding.rowOcrEngineType?.isVisible = showNoLegalOcr
        binding.rowPaddleOcrModel?.isVisible = showNoLegalOcr && ocrEngineType == "PADDLE_OCR"

        refreshOcrRowValues(viewModel.settings.value)
    }

    private fun observeData() {
        collectOnLifecycle(viewModel.settings) { settings ->
            withSettingsUpdate {
                setSwitchChecked(binding.rowEnableTranslation, settings.enableTranslation)
                updateTranslationVisibility(settings.enableTranslation)

                updateLanguageSelectors(settings)

                setSwitchChecked(binding.rowTranslationLensStyle, settings.translationLensStyle)
                setSwitchChecked(binding.rowEnableOcr, settings.enableOcr)
                updateOcrVisibility(settings.enableOcr, settings.ocrEngineType)
            }
        }
        collectOnLifecycle(viewModel.translationModelPrewarmStatus) { status ->
            updateTranslationPrewarmStatus(status)
        }
    }

    private fun updateTranslationPrewarmStatus(status: TranslationModelPrewarmStatus) {
        if (!capabilityAvailability.isTranslationAvailable() || !viewModel.settings.value.enableTranslation) {
            binding.layoutTranslationPrewarmStatus.isVisible = false
            binding.btnTranslationPrewarmRetry.isVisible = false
            return
        }

        val statusTextRes = when (status) {
            TranslationModelPrewarmStatus.Idle -> null
            is TranslationModelPrewarmStatus.Downloading -> R.string.translation_model_prewarm_downloading
            is TranslationModelPrewarmStatus.Ready -> R.string.translation_model_prewarm_ready
            is TranslationModelPrewarmStatus.Failed -> R.string.translation_model_prewarm_failed
        }

        binding.layoutTranslationPrewarmStatus.isVisible = statusTextRes != null
        binding.btnTranslationPrewarmRetry.isVisible = status is TranslationModelPrewarmStatus.Failed
        if (statusTextRes != null) {
            binding.tvTranslationPrewarmStatus.text = getString(statusTextRes)
            binding.tvTranslationPrewarmStatus.contentDescription =
                binding.tvTranslationPrewarmStatus.text
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
