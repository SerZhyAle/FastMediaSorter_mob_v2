package com.sza.fastmediasorter.ui.settings.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.FragmentSettingsOtherBinding
import com.sza.fastmediasorter.domain.delivery.DeliverableSet
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.TranslationModelPrewarmStatus
import com.sza.fastmediasorter.ui.delivery.DeliveryEnableInterceptor
import com.sza.fastmediasorter.ui.delivery.ExtensionsManagerFragment
import com.sza.fastmediasorter.ui.dialog.SearchableLanguagePickerDialog
import com.sza.fastmediasorter.ui.settings.SettingsViewModel
import com.sza.fastmediasorter.utils.collectOnLifecycle
import com.sza.fastmediasorter.ui.player.helpers.LanguageFlagFormatter
import com.sza.fastmediasorter.ui.player.helpers.TesseractModelManager
import com.sza.fastmediasorter.ui.player.helpers.TranslationLanguageCatalog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class OtherMediaSettingsFragment : BaseSettingsFragment() {

    @Inject
    lateinit var deliveryEnableInterceptor: DeliveryEnableInterceptor

    private val modelManager by lazy { TesseractModelManager(requireContext()) }
    private var rusDownloadJob: Job? = null
    private var ukrDownloadJob: Job? = null

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
            // After migration the row IS the visible element; no extra wrapper container.
            binding.rowEnableTranslation.isVisible = false
            binding.layoutTranslationLanguages.isVisible = false
            binding.layoutTranslationPrewarmStatus.isVisible = false
            binding.rowTranslationLensStyle.isVisible = false

            binding.rowEnableGoogleLens.isVisible = false

            // Hide OCR row and summary
            binding.rowEnableOcr.isVisible = false
            binding.tvOcrSummary.isVisible = false
            binding.layoutOcrFontSize?.isVisible = false
            binding.layoutOcrFontFamily?.isVisible = false
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

        // Google Lens
        bindSwitch(binding.rowEnableGoogleLens) { isChecked ->
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(enableGoogleLens = isChecked))
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

        // OCR Best Models
        binding.btnOcrBestRusDownload.setOnClickListener {
            startDownload("rus")
        }
        binding.btnOcrBestRusDelete.setOnClickListener {
            deleteModel("rus")
        }
        binding.btnOcrBestUkrDownload.setOnClickListener {
            startDownload("ukr")
        }
        binding.btnOcrBestUkrDelete.setOnClickListener {
            deleteModel("ukr")
        }
        binding.layoutExtensionsManager?.let { layout ->
            layout.setOnClickListener {
                // This fragment lives inside the settings ViewPager, whose child FragmentManager does
                // not own android.R.id.content; use the activity FragmentManager so the full-screen
                // overlay attaches to a real container (else: "No view found for id android:id/content").
                requireActivity().supportFragmentManager.beginTransaction()
                    .add(android.R.id.content, ExtensionsManagerFragment(), ExtensionsManagerFragment.TAG)
                    .addToBackStack(null)
                    .commit()
            }
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

    private fun setupOcrEngineSpinners() {
        if (!BuildConfig.IS_NO_LEGAL_FLAVOR) {
            binding.layoutOcrEngineType?.isVisible = false
            binding.layoutPaddleOcrModel?.isVisible = false
            return
        }

        // OCR Engine Type Spinner
        val ocrEngines = arrayOf(
            getString(R.string.ocr_engine_type_tesseract),
            getString(R.string.ocr_engine_type_paddleocr)
        )
        val ocrEngineAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, ocrEngines)
        ocrEngineAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerOcrEngineType?.adapter = ocrEngineAdapter

        binding.spinnerOcrEngineType?.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!isUpdatingFromSettings) {
                    val engineValue = when (position) {
                        0 -> "TESSERACT"
                        1 -> "PADDLE_OCR"
                        else -> "TESSERACT"
                    }
                    Timber.d("S0288: settings ocr engine selector picked engine=$engineValue")
                    val current = viewModel.settings.value
                    viewModel.updateSettings(current.copy(ocrEngineType = engineValue))
                    
                    // Dynamically toggle visibility of paddle ocr model layout
                    binding.layoutPaddleOcrModel?.isVisible = current.enableOcr && engineValue == "PADDLE_OCR"
                }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        // PaddleOCR Model Spinner
        val paddleModels = arrayOf(
            getString(R.string.paddle_ocr_model_cyrillic),
            getString(R.string.paddle_ocr_model_eslav)
        )
        val paddleModelAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, paddleModels)
        paddleModelAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerPaddleOcrModel?.adapter = paddleModelAdapter

        binding.spinnerPaddleOcrModel?.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!isUpdatingFromSettings) {
                    val modelValue = when (position) {
                        0 -> "CYRILLIC"
                        1 -> "EAST_SLAVIC"
                        else -> "CYRILLIC"
                    }
                    val current = viewModel.settings.value
                    viewModel.updateSettings(current.copy(paddleOcrModel = modelValue))
                }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }

    private fun updateOcrVisibility(enabled: Boolean, ocrEngineType: String = viewModel.settings.value.ocrEngineType) {
        binding.layoutOcrFontSize?.isVisible = enabled
        binding.layoutOcrFontFamily?.isVisible = enabled
        binding.layoutOcrBestModels?.isVisible = enabled
        
        val showNoLegalOcr = enabled && BuildConfig.IS_NO_LEGAL_FLAVOR
        binding.layoutOcrEngineType?.isVisible = showNoLegalOcr
        binding.layoutPaddleOcrModel?.isVisible = showNoLegalOcr && ocrEngineType == "PADDLE_OCR"
        
        if (enabled) {
            updateModelStates()
        }
    }

    private fun updateModelStates() {
        if (_binding == null) return

        val isRusInstalled = modelManager.isModelInstalled("rus")
        val isUkrInstalled = modelManager.isModelInstalled("ukr")

        // Russian model UI state
        val isRusDownloading = rusDownloadJob?.isActive == true
        binding.pbOcrBestRusDownload.isVisible = isRusDownloading
        binding.btnOcrBestRusDownload.isVisible = !isRusInstalled && !isRusDownloading
        binding.btnOcrBestRusDelete.isVisible = isRusInstalled && !isRusDownloading
        binding.btnOcrBestRusDownload.isEnabled = !isRusDownloading
        binding.btnOcrBestRusDelete.isEnabled = !isRusDownloading

        if (isRusInstalled) {
            binding.tvOcrBestRusStatus.text = getString(R.string.ocr_best_model_installed)
            binding.tvOcrBestRusStatus.alpha = 1.0f
        } else if (!isRusDownloading) {
            binding.tvOcrBestRusStatus.text = getString(R.string.ocr_best_model_size_mb, "15.0")
            binding.tvOcrBestRusStatus.alpha = 0.7f
        }

        // Ukrainian model UI state
        val isUkrDownloading = ukrDownloadJob?.isActive == true
        binding.pbOcrBestUkrDownload.isVisible = isUkrDownloading
        binding.btnOcrBestUkrDownload.isVisible = !isUkrInstalled && !isUkrDownloading
        binding.btnOcrBestUkrDelete.isVisible = isUkrInstalled && !isUkrDownloading
        binding.btnOcrBestUkrDownload.isEnabled = !isUkrDownloading
        binding.btnOcrBestUkrDelete.isEnabled = !isUkrDownloading

        if (isUkrInstalled) {
            binding.tvOcrBestUkrStatus.text = getString(R.string.ocr_best_model_installed)
            binding.tvOcrBestUkrStatus.alpha = 1.0f
        } else if (!isUkrDownloading) {
            binding.tvOcrBestUkrStatus.text = getString(R.string.ocr_best_model_size_mb, "11.6")
            binding.tvOcrBestUkrStatus.alpha = 0.7f
        }
    }

    private fun startDownload(language: String) {
        val isRus = language == "rus"
        val pb = if (isRus) binding.pbOcrBestRusDownload else binding.pbOcrBestUkrDownload
        val tvStatus = if (isRus) binding.tvOcrBestRusStatus else binding.tvOcrBestUkrStatus

        val job = viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
            pb.isVisible = true
            pb.progress = 0
            updateModelStates() // Disable buttons during download

            val success = modelManager.downloadModel(language) { percent, bytes, total ->
                // Callback runs on background thread, post back to Main
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                    if (_binding != null) {
                        pb.progress = percent
                        tvStatus.text = getString(
                            R.string.ocr_best_model_downloading,
                            percent,
                            formatSizeProgress(bytes, total)
                        )
                        tvStatus.alpha = 1.0f
                    }
                }
            }

            if (isRus) rusDownloadJob = null else ukrDownloadJob = null
            updateModelStates()

            if (!success) {
                android.widget.Toast.makeText(
                    requireContext(),
                    R.string.ocr_best_download_error,
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }

        if (isRus) {
            rusDownloadJob = job
        } else {
            ukrDownloadJob = job
        }
    }

    private fun deleteModel(language: String) {
        val deleted = modelManager.deleteModel(language)
        if (deleted) {
            updateModelStates()
        }
    }

    private fun formatSizeProgress(bytes: Long, total: Long): String {
        val totalMb = if (total > 0) total.toFloat() / (1024 * 1024) else 0f
        val currentMb = bytes.toFloat() / (1024 * 1024)
        val suffix = if (java.util.Locale.getDefault().language == "ru" || java.util.Locale.getDefault().language == "uk") "МБ" else "MB"

        return if (totalMb > 0) {
            "%.1f / %.1f %s".format(currentMb, totalMb, suffix)
        } else {
            "%.1f %s".format(currentMb, suffix)
        }
    }

    private fun observeData() {
        collectOnLifecycle(viewModel.settings) { settings ->
            withSettingsUpdate {
                setSwitchChecked(binding.rowEnableTranslation, settings.enableTranslation)
                updateTranslationVisibility(settings.enableTranslation)

                updateLanguageSelectors(settings)

                setSwitchChecked(binding.rowTranslationLensStyle, settings.translationLensStyle)
                setSwitchChecked(binding.rowEnableGoogleLens, settings.enableGoogleLens)
                setSwitchChecked(binding.rowEnableOcr, settings.enableOcr)
                updateOcrVisibility(settings.enableOcr, settings.ocrEngineType)

                // OCR Engine settings (S0288)
                if (BuildConfig.IS_NO_LEGAL_FLAVOR) {
                    val ocrEnginePosition = when (settings.ocrEngineType) {
                        "TESSERACT" -> 0
                        "PADDLE_OCR" -> 1
                        else -> 0
                    }
                    binding.spinnerOcrEngineType?.setSelection(ocrEnginePosition)

                    val paddleOcrModelPosition = when (settings.paddleOcrModel) {
                        "CYRILLIC" -> 0
                        "EAST_SLAVIC" -> 1
                        else -> 0
                    }
                    binding.spinnerPaddleOcrModel?.setSelection(paddleOcrModelPosition)
                }

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
            }
        }
        collectOnLifecycle(viewModel.translationModelPrewarmStatus) { status ->
            updateTranslationPrewarmStatus(status)
        }
    }

    private fun updateTranslationPrewarmStatus(status: TranslationModelPrewarmStatus) {
        if (!BuildConfig.ENABLE_TRANSLATION || !viewModel.settings.value.enableTranslation) {
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
