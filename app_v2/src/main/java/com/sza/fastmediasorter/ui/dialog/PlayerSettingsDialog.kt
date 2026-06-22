package com.sza.fastmediasorter.ui.dialog

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.DialogPlayerSettingsBinding
import com.sza.fastmediasorter.domain.model.StereoMode

/**
 * Dialog for video player settings:
 * - Playback speed (0.25x - 2.0x)
 * - Repeat video
 * - Subtitles on/off and language
 * - Audio track language
 * 
 * Settings are applied immediately and persist for the current player session.
 */
class PlayerSettingsDialog(
    context: Context,
    private val currentSettings: PlayerSettings,
    private val onSettingsApplied: (PlayerSettings) -> Unit
) : Dialog(context) {

    private lateinit var binding: DialogPlayerSettingsBinding

    /**
     * Player settings data class
     */
    data class PlayerSettings(
        val playbackSpeed: Float = 1.0f,
        val repeatVideo: Boolean = false,
        val showSubtitles: Boolean = false,
        val subtitleLanguage: LanguageOption = LanguageOption.DEFAULT,
        val audioLanguage: LanguageOption = LanguageOption.DEFAULT,
        // 3D SBS stereo preference - AUTO means let StereoDetector decide from metadata/AR
        val stereoMode: StereoMode = StereoMode.AUTO
    )

    /**
     * Language options for subtitles and audio tracks
     */
    enum class LanguageOption(val code: String, val displayNameResId: Int) {
        DEFAULT("default", R.string.language_default),
        ENGLISH("en", R.string.language_english),
        RUSSIAN("ru", R.string.language_russian),
        UKRAINIAN("uk", R.string.language_ukrainian);

        companion object {
            fun fromCode(code: String): LanguageOption {
                return entries.find { it.code == code } ?: DEFAULT
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DialogPlayerSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.9).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )

        setupUI()
        loadCurrentSettings()
    }

    private fun setupUI() {
        // Speed chip group listener
        binding.chipGroupSpeed.setOnCheckedStateChangeListener { _, _ ->
            // Just for tracking, actual value read on apply
        }

        // Subtitles toggle listener - enable/disable language dropdown.
        // S0567: SettingsDropdownRow.setEnabled handles its own title + dimming, so no separate alpha.
        binding.cbShowSubtitles.setOnCheckedChangeListener { isChecked ->
            binding.spinnerSubtitleLanguage.isEnabled = isChecked
        }

        setupLanguageSpinner(binding.spinnerSubtitleLanguage)
        setupLanguageSpinner(binding.spinnerAudioLanguage)

        // 3D stereo mode radio group
        setupStereoSection()

        binding.btnCancel.setOnClickListener {
            dismiss()
        }

        binding.btnApply.setOnClickListener {
            val settings = collectSettings()
            onSettingsApplied(settings)
            dismiss()
        }
    }

    private fun setupStereoSection() {
        // OU is a future option - keep it in the list but disabled so users know it's coming
        binding.radio3DOU?.isEnabled = false
    }

    // S0567: subtitle/audio dropdowns migrated from raw Spinner to SettingsDropdownRow (ADR-1).
    private fun setupLanguageSpinner(row: com.sza.fastmediasorter.ui.common.widget.SettingsDropdownRow) {
        val languages = LanguageOption.entries.map { context.getString(it.displayNameResId) as CharSequence }
        row.setEntries(languages)
    }

    private fun loadCurrentSettings() {
        val speedChipId = when (currentSettings.playbackSpeed) {
            0.25f -> R.id.chipSpeed025
            0.5f -> R.id.chipSpeed05
            1.0f -> R.id.chipSpeed1
            1.25f -> R.id.chipSpeed125
            1.5f -> R.id.chipSpeed15
            1.75f -> R.id.chipSpeed175
            2.0f -> R.id.chipSpeed2
            else -> R.id.chipSpeed1
        }
        binding.chipGroupSpeed.check(speedChipId)

        binding.cbRepeatVideo.isChecked = currentSettings.repeatVideo

        binding.cbShowSubtitles.isChecked = currentSettings.showSubtitles
        // S0567: SettingsDropdownRow.setEnabled handles its own dimming.
        binding.spinnerSubtitleLanguage.isEnabled = currentSettings.showSubtitles
        binding.spinnerSubtitleLanguage.setSelection(currentSettings.subtitleLanguage.ordinal)

        binding.spinnerAudioLanguage.setSelection(currentSettings.audioLanguage.ordinal)

        // Set 3D stereo mode radio - SBS_HALF maps to SBS_FULL in dialog (both shown as "SBS")
        val radioId = when (currentSettings.stereoMode) {
            StereoMode.SBS_FULL, StereoMode.SBS_HALF -> R.id.radio3DSbs
            StereoMode.OU -> R.id.radio3DOU
            StereoMode.MONO -> R.id.radio3DMono
            else -> R.id.radio3DAuto  // AUTO and UNKNOWN both default to Auto
        }
        binding.radioGroup3D?.check(radioId)
    }

    private fun collectSettings(): PlayerSettings {
        // Get playback speed from selected chip
        val playbackSpeed = when (binding.chipGroupSpeed.checkedChipId) {
            R.id.chipSpeed025 -> 0.25f
            R.id.chipSpeed05 -> 0.5f
            R.id.chipSpeed1 -> 1.0f
            R.id.chipSpeed125 -> 1.25f
            R.id.chipSpeed15 -> 1.5f
            R.id.chipSpeed175 -> 1.75f
            R.id.chipSpeed2 -> 2.0f
            else -> 1.0f
        }

        val subtitleLanguage = LanguageOption.entries.getOrElse(
            binding.spinnerSubtitleLanguage.getSelectedIndex()
        ) { LanguageOption.DEFAULT }

        val audioLanguage = LanguageOption.entries.getOrElse(
            binding.spinnerAudioLanguage.getSelectedIndex()
        ) { LanguageOption.DEFAULT }

        // Map selected radio button back to StereoMode enum
        val stereoMode = when (binding.radioGroup3D?.checkedRadioButtonId) {
            R.id.radio3DSbs -> StereoMode.SBS_FULL
            R.id.radio3DOU -> StereoMode.OU
            R.id.radio3DMono -> StereoMode.MONO
            else -> StereoMode.AUTO
        }

        return PlayerSettings(
            playbackSpeed = playbackSpeed,
            repeatVideo = binding.cbRepeatVideo.isChecked,
            showSubtitles = binding.cbShowSubtitles.isChecked,
            subtitleLanguage = subtitleLanguage,
            audioLanguage = audioLanguage,
            stereoMode = stereoMode
        )
    }
}
