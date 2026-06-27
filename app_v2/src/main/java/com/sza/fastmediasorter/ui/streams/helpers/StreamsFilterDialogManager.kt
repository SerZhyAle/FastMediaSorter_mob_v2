package com.sza.fastmediasorter.ui.streams.helpers

import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.DialogStreamsFilterBinding
import com.sza.fastmediasorter.ui.dialog.DialogKeyboardDelegate
import com.sza.fastmediasorter.ui.dialog.SearchableOptionPickerDialog
import com.sza.fastmediasorter.ui.streams.StreamsViewModel

/**
 * Hosts the streams filter dialog so [com.sza.fastmediasorter.ui.streams.StreamsActivity] stays free of
 * dialog logic (Rule 3/5). Presents a category row, a language row and an All/Audio/Video media-kind
 * toggle; each row opens a [SearchableOptionPickerDialog]. "Clear filters" lives in the dialog button bar
 * (neutral) next to OK and resets in place without dismissing. Selections are applied live through
 * [onApply] (the ViewModel owns the actual filtering). Category options are flag-less; language options
 * carry a flag where the name resolves to a known language ([StreamLanguageOptionMapper]).
 */
class StreamsFilterDialogManager(
    private val activity: FragmentActivity,
) {

    fun show(
        state: StreamsViewModel.StreamsUiState,
        onApply: (
            category: String?,
            language: String?,
            mediaKind: StreamsViewModel.MediaKindFilter,
            pinnedOnly: Boolean,
        ) -> Unit,
    ) {
        val binding = DialogStreamsFilterBinding.inflate(activity.layoutInflater)
        var category = state.filter.category
        var language = state.filter.language
        var mediaKind = state.filter.mediaKind
        var pinnedOnly = state.filter.pinnedOnly

        fun renderValues() {
            binding.tvCategoryValue.text = category ?: activity.getString(R.string.streams_filter_all)
            binding.tvLanguageValue.text = languageLabel(language)
        }
        renderValues()
        binding.toggleMediaKind.check(mediaKindButtonId(binding, mediaKind))
        binding.checkPinnedOnly.isChecked = pinnedOnly

        binding.rowCategory.setOnClickListener {
            SearchableOptionPickerDialog.newInstance(
                title = activity.getString(R.string.streams_filter_category),
                options = StreamLanguageOptionMapper.categoryOptions(state.facets.categories),
                selectedId = category,
                onPicked = { picked ->
                    category = picked?.id
                    renderValues()
                    onApply(category, language, mediaKind, pinnedOnly)
                },
            ).show(activity.supportFragmentManager, "streams_category_picker")
        }

        binding.rowLanguage.setOnClickListener {
            SearchableOptionPickerDialog.newInstance(
                title = activity.getString(R.string.streams_filter_language),
                options = StreamLanguageOptionMapper.languageOptions(state.facets.languages),
                selectedId = language,
                onPicked = { picked ->
                    language = picked?.id
                    renderValues()
                    onApply(category, language, mediaKind, pinnedOnly)
                },
            ).show(activity.supportFragmentManager, "streams_language_picker")
        }

        binding.toggleMediaKind.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            mediaKind = when (checkedId) {
                binding.btnMediaAudio.id -> StreamsViewModel.MediaKindFilter.AUDIO
                binding.btnMediaVideo.id -> StreamsViewModel.MediaKindFilter.VIDEO
                else -> StreamsViewModel.MediaKindFilter.ALL
            }
            onApply(category, language, mediaKind, pinnedOnly)
        }

        binding.checkPinnedOnly.setOnCheckedChangeListener { _, isChecked ->
            pinnedOnly = isChecked
            onApply(category, language, mediaKind, pinnedOnly)
        }

        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.streams_filter)
            .setView(binding.root)
            .setNeutralButton(R.string.streams_filter_clear, null)
            .setPositiveButton(android.R.string.ok, null)
            .create()
        // Override the neutral click after show() so "Clear filters" resets in place without dismissing.
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                category = null
                language = null
                mediaKind = StreamsViewModel.MediaKindFilter.ALL
                pinnedOnly = false
                renderValues()
                binding.toggleMediaKind.check(binding.btnMediaAll.id)
                binding.checkPinnedOnly.isChecked = false
                onApply(category, language, mediaKind, pinnedOnly)
            }
        }
        // Escape dismisses; Enter confirms via OK (row/toggle changes already apply the filter live).
        DialogKeyboardDelegate.applyTo(dialog) {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.performClick()
        }
        dialog.show()
    }

    private fun mediaKindButtonId(
        binding: DialogStreamsFilterBinding,
        mediaKind: StreamsViewModel.MediaKindFilter,
    ): Int = when (mediaKind) {
        StreamsViewModel.MediaKindFilter.AUDIO -> binding.btnMediaAudio.id
        StreamsViewModel.MediaKindFilter.VIDEO -> binding.btnMediaVideo.id
        StreamsViewModel.MediaKindFilter.ALL -> binding.btnMediaAll.id
    }

    /** Display-cases the active language id ("ukrainian" -> "Ukrainian"); "All" when no language is set. */
    private fun languageLabel(language: String?): String =
        if (language == null) {
            activity.getString(R.string.streams_filter_all)
        } else {
            StreamLanguageOptionMapper.languageOptions(listOf(language)).firstOrNull()?.label ?: language
        }
}
