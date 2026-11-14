package com.sza.fastmediasorter.ui.settings.fragments

import android.graphics.Rect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.databinding.FragmentSettingsToolsBinding
import com.sza.fastmediasorter.domain.repository.MusicTrackRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.ui.calculator.helpers.CalculatorSettingsStore
import com.sza.fastmediasorter.ui.common.widget.CollapsibleSectionHeader
import com.sza.fastmediasorter.ui.common.widget.CollapsibleSectionsManager
import com.sza.fastmediasorter.ui.settings.SettingsActivity
import com.sza.fastmediasorter.ui.settings.helpers.ToolsCalculatorBlockManager
import com.sza.fastmediasorter.ui.settings.helpers.ToolsStopwatchBlockManager
import com.sza.fastmediasorter.ui.settings.helpers.ToolsTranslationBlockManager
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

/**
 * S4622: the Tools page - the settings of the translation overlay, the OCR camera, the calculator and the
 * stopwatch, which SETTINGS-NAVIGATION 2.1 item A makes a destination of the one settings surface. A host's
 * settings command opens this page through [SettingsActivity.openToolsSectionIntent] with its group named.
 */
@AndroidEntryPoint
class ToolsSettingsFragment : BaseSettingsFragment() {

    private var _binding: FragmentSettingsToolsBinding? = null
    private val binding get() = requireNotNull(_binding) { "Tools page binding is only valid while its view exists" }

    @Inject lateinit var settingsRepository: SettingsRepository

    @Inject lateinit var mediaCapabilities: MediaCapabilities

    @Inject lateinit var musicTrackRepository: MusicTrackRepository

    private var calculatorBlock: ToolsCalculatorBlockManager? = null

    private val sectionsManager by lazy { CollapsibleSectionsManager(requireContext()) }

    override fun collapsibleSections(): CollapsibleSectionsManager = sectionsManager

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsToolsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Timber.d("S4622: Tools page bound")
        val hasTranslation = ToolsTranslationBlockManager(this, binding, settingsRepository).bind()
        if (hasTranslation) {
            register(binding.headerToolsTranslation, binding.containerToolsTranslation, "tools__translation")
        }
        val calculator = ToolsCalculatorBlockManager(binding, CalculatorSettingsStore(requireContext()))
        calculator.bind()
        calculatorBlock = calculator
        register(binding.headerToolsCalculator, binding.containerToolsCalculator, "tools__calculator")
        ToolsStopwatchBlockManager(this, binding, settingsRepository, mediaCapabilities, musicTrackRepository).bind()
        register(binding.headerToolsStopwatch, binding.containerToolsStopwatch, "tools__stopwatch")
        expandSectionFromIntent()
    }

    private fun register(header: CollapsibleSectionHeader, container: View, key: String) {
        sectionsManager.register(header, container, key, defaultExpanded = false)
    }

    // The extra is consumed so a rotation or a return to this page does not re-reveal the group.
    // Called by SettingsActivity too, when another page's row opens a block of this page that already exists.
    fun expandSectionFromIntent() {
        val current = _binding ?: return
        val intent = requireActivity().intent
        val header = headerFor(current, intent.getStringExtra(SettingsActivity.EXTRA_EXPAND_SECTION)) ?: return
        intent.removeExtra(SettingsActivity.EXTRA_EXPAND_SECTION)
        header.setExpanded(true, notify = true)
        header.post { header.requestRectangleOnScreen(Rect(0, 0, header.width, header.height), false) }
    }

    // The translation block is hidden where translation is unavailable, and a hidden group is not revealed.
    private fun headerFor(current: FragmentSettingsToolsBinding, sectionId: String?): CollapsibleSectionHeader? =
        when (sectionId) {
            SettingsActivity.SECTION_TRANSLATION ->
                current.headerToolsTranslation.takeIf { current.cardToolsTranslation.isVisible }
            SettingsActivity.SECTION_CALCULATOR -> current.headerToolsCalculator
            SettingsActivity.SECTION_STOPWATCH -> current.headerToolsStopwatch
            else -> null
        }

    override fun onDestroyView() {
        calculatorBlock?.release()
        calculatorBlock = null
        super.onDestroyView()
        _binding = null
    }
}
