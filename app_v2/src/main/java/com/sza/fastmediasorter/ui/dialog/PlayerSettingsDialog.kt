package com.sza.fastmediasorter.ui.dialog

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.DialogPlayerSettingsBinding

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
        val audioLanguage: LanguageOption = LanguageOption.DEFAULT
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

        // Subtitles checkbox listener - enable/disable language spinner
        binding.cbShowSubtitles.setOnCheckedChangeListener { _, isChecked ->
            binding.spinnerSubtitleLanguage.isEnabled = isChecked
            binding.tvSubtitleLanguageLabel.alpha = if (isChecked) 1.0f else 0.5f
            binding.spinnerSubtitleLanguage.alpha = if (isChecked) 1.0f else 0.5f
        }

        // Setup language spinners
        setupLanguageSpinner(binding.spinnerSubtitleLanguage)
        setupLanguageSpinner(binding.spinnerAudioLanguage)

        // Cancel button
        binding.btnCancel.setOnClickListener {
            dismiss()
        }

        // Apply button
        binding.btnApply.setOnClickListener {
            val settings = collectSettings()
            onSettingsApplied(settings)
            dismiss()
        }
    }

    private fun setupLanguageSpinner(spinner: android.widget.Spinner) {
        val languages = LanguageOption.entries.map { context.getString(it.displayNameResId) }
        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, languages)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
    }

    private fun loadCurrentSettings() {
        // Set playback speed chip
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

        // Set repeat checkbox
        binding.cbRepeatVideo.isChecked = currentSettings.repeatVideo

        // Set subtitles
        binding.cbShowSubtitles.isChecked = currentSettings.showSubtitles
        binding.spinnerSubtitleLanguage.isEnabled = currentSettings.showSubtitles
        binding.tvSubtitleLanguageLabel.alpha = if (currentSettings.showSubtitles) 1.0f else 0.5f
        binding.spinnerSubtitleLanguage.alpha = if (currentSettings.showSubtitles) 1.0f else 0.5f
        binding.spinnerSubtitleLanguage.setSelection(currentSettings.subtitleLanguage.ordinal)

        // Set audio language
        binding.spinnerAudioLanguage.setSelection(currentSettings.audioLanguage.ordinal)
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

        // Get language selections
        val subtitleLanguage = LanguageOption.entries.getOrElse(
            binding.spinnerSubtitleLanguage.selectedItemPosition
        ) { LanguageOption.DEFAULT }
        
        val audioLanguage = LanguageOption.entries.getOrElse(
            binding.spinnerAudioLanguage.selectedItemPosition
        ) { LanguageOption.DEFAULT }

        return PlayerSettings(
            playbackSpeed = playbackSpeed,
            repeatVideo = binding.cbRepeatVideo.isChecked,
            showSubtitles = binding.cbShowSubtitles.isChecked,
            subtitleLanguage = subtitleLanguage,
            audioLanguage = audioLanguage
        )
    }
}
