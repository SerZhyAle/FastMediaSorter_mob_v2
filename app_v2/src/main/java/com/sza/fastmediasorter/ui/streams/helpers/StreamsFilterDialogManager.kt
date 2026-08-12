package com.sza.fastmediasorter.ui.streams.helpers

import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.DialogStreamsFilterBinding
import com.sza.fastmediasorter.ui.dialog.DialogKeyboardDelegate
import com.sza.fastmediasorter.ui.dialog.SearchableOptionPickerDialog
import com.sza.fastmediasorter.ui.streams.StreamsViewModel
import timber.log.Timber

/**
 * Hosts the streams filter dialog so [com.sza.fastmediasorter.ui.streams.StreamsActivity] stays free of
 * dialog logic (Rule 3/5). Presents the All/Audio/Video media-kind toggle on top, then category and
 * language as two tappable columns, then full-width topic (S1168) and country (S0761) rows; each opens a
 * [SearchableOptionPickerDialog]. "Clear filters" lives in the dialog button bar
 * (neutral) next to OK and resets in place without dismissing. Selections are applied live through
 * [onApply] (the ViewModel owns the actual filtering). Category and topic options are flag-less; language
 * options carry a flag where the name resolves to a known language ([StreamLanguageOptionMapper]); country
 * options carry a flag from the ISO code ([StreamCountryOptionMapper]).
 *
 * Each of the four pickers reports back under its own FragmentResult key, so one FragmentManager can
 * carry all four without a pick reaching the wrong filter.
 */
class StreamsFilterDialogManager(
    private val activity: FragmentActivity,
) {

    fun show(
        state: StreamsViewModel.StreamsUiState,
        onApply: (
            category: String?,
            topic: String?,
            language: String?,
            country: String?,
            mediaKind: StreamsViewModel.MediaKindFilter,
            pinnedOnly: Boolean,
        ) -> Unit,
    ) {
        val binding = DialogStreamsFilterBinding.inflate(activity.layoutInflater)
        var category = state.filter.category
        var topic = state.filter.topic
        var language = state.filter.language
        var country = state.filter.country
        var mediaKind = state.filter.mediaKind
        var pinnedOnly = state.filter.pinnedOnly

        fun renderValues() {
            binding.tvCategoryValue.text = category ?: activity.getString(R.string.streams_filter_all)
            // S1477: the picked rubric is shown localized; `topic` itself stays the catalog id.
            binding.tvTopicValue.text = StreamTopicRubricCatalog.label(activity, topic)
                ?: activity.getString(R.string.streams_filter_all)
            binding.tvLanguageValue.text = languageLabel(language)
            binding.tvCountryValue.text = countryLabel(country)
        }

        fun applyFilters() {
            renderValues()
            onApply(category, topic, language, country, mediaKind, pinnedOnly)
        }
        renderValues()
        // A catalog without topics would open an empty picker, so the row is hidden rather than dead.
        binding.rowTopic.isVisible = state.facets.topics.isNotEmpty()
        binding.toggleMediaKind.check(mediaKindButtonId(binding, mediaKind))
        binding.checkPinnedOnly.isChecked = pinnedOnly

        // Registered per show() because a pick lands in these locals: this parent is a plain AlertDialog,
        // not a DialogFragment, so it dies with the Activity. The FragmentManager holds the pending
        // result, so a pick made after recreation applies the next time the filter dialog is opened.
        // A null id is the picker's leading "All" reset row and reads as "filter cleared".
        val fragmentManager = activity.supportFragmentManager
        fragmentManager.setFragmentResultListener(KEY_CATEGORY, activity) { _, bundle ->
            category = bundle.getString(SearchableOptionPickerDialog.RESULT_OPTION_ID)
            Timber.d("S1331: streams filter category result=%s", category)
            applyFilters()
        }
        fragmentManager.setFragmentResultListener(KEY_TOPIC, activity) { _, bundle ->
            topic = bundle.getString(SearchableOptionPickerDialog.RESULT_OPTION_ID)
            applyFilters()
        }
        fragmentManager.setFragmentResultListener(KEY_LANGUAGE, activity) { _, bundle ->
            language = bundle.getString(SearchableOptionPickerDialog.RESULT_OPTION_ID)
            applyFilters()
        }
        fragmentManager.setFragmentResultListener(KEY_COUNTRY, activity) { _, bundle ->
            country = bundle.getString(SearchableOptionPickerDialog.RESULT_OPTION_ID)
            applyFilters()
        }

        binding.rowCategory.setOnClickListener {
            val options = StreamLanguageOptionMapper.categoryOptions(state.facets.categories)
            openPicker(R.string.streams_filter_category, options, category, KEY_CATEGORY, TAG_CATEGORY)
        }
        binding.rowTopic.setOnClickListener {
            val options = StreamLanguageOptionMapper.rubricOptions(activity, state.facets.topics)
            openPicker(R.string.streams_filter_topic, options, topic, KEY_TOPIC, TAG_TOPIC)
        }
        binding.rowLanguage.setOnClickListener {
            val options = StreamLanguageOptionMapper.languageOptions(state.facets.languages)
            openPicker(R.string.streams_filter_language, options, language, KEY_LANGUAGE, TAG_LANGUAGE)
        }
        binding.rowCountry.setOnClickListener {
            val options = StreamCountryOptionMapper.countryOptions(state.facets.countries)
            openPicker(R.string.streams_filter_country, options, country, KEY_COUNTRY, TAG_COUNTRY)
        }

        binding.toggleMediaKind.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            mediaKind = mediaKindOf(binding, checkedId)
            applyFilters()
        }

        binding.checkPinnedOnly.setOnCheckedChangeListener { _, isChecked ->
            pinnedOnly = isChecked
            applyFilters()
        }

        buildDialog(binding) {
            category = null
            topic = null
            language = null
            country = null
            mediaKind = StreamsViewModel.MediaKindFilter.ALL
            pinnedOnly = false
            binding.toggleMediaKind.check(binding.btnMediaAll.id)
            binding.checkPinnedOnly.isChecked = false
            applyFilters()
        }.show()
    }

    private fun openPicker(
        @StringRes titleRes: Int,
        options: List<SearchableOptionPickerDialog.Option>,
        selectedId: String?,
        requestKey: String,
        tag: String,
    ) {
        SearchableOptionPickerDialog.newInstance(
            title = activity.getString(titleRes),
            options = options,
            selectedId = selectedId,
            requestKey = requestKey,
        ).show(activity.supportFragmentManager, tag)
    }

    /** Builds the filter dialog; [onClear] backs the neutral button, which resets without dismissing. */
    private fun buildDialog(
        binding: DialogStreamsFilterBinding,
        onClear: () -> Unit,
    ): AlertDialog {
        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.streams_filter)
            .setView(binding.root)
            .setNeutralButton(R.string.streams_filter_clear, null)
            .setPositiveButton(android.R.string.ok, null)
            .create()
        // Override the neutral click after show() so "Clear filters" resets in place without dismissing.
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener { onClear() }
        }
        // Escape dismisses; Enter confirms via OK (row/toggle changes already apply the filter live).
        DialogKeyboardDelegate.applyTo(dialog) {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.performClick()
        }
        return dialog
    }

    private fun mediaKindButtonId(
        binding: DialogStreamsFilterBinding,
        mediaKind: StreamsViewModel.MediaKindFilter,
    ): Int = when (mediaKind) {
        StreamsViewModel.MediaKindFilter.AUDIO -> binding.btnMediaAudio.id
        StreamsViewModel.MediaKindFilter.VIDEO -> binding.btnMediaVideo.id
        StreamsViewModel.MediaKindFilter.ALL -> binding.btnMediaAll.id
    }

    private fun mediaKindOf(
        binding: DialogStreamsFilterBinding,
        checkedId: Int,
    ): StreamsViewModel.MediaKindFilter = when (checkedId) {
        binding.btnMediaAudio.id -> StreamsViewModel.MediaKindFilter.AUDIO
        binding.btnMediaVideo.id -> StreamsViewModel.MediaKindFilter.VIDEO
        else -> StreamsViewModel.MediaKindFilter.ALL
    }

    /** Display-cases the active language id ("ukrainian" -> "Ukrainian"); "All" when no language is set. */
    private fun languageLabel(language: String?): String =
        if (language == null) {
            activity.getString(R.string.streams_filter_all)
        } else {
            StreamLanguageOptionMapper.languageOptions(listOf(language)).firstOrNull()?.label ?: language
        }

    /** Decorates the active country code with its flag ("UA" -> "🇺🇦 UA"); "All" when no country is set. */
    private fun countryLabel(country: String?): String =
        if (country == null) {
            activity.getString(R.string.streams_filter_all)
        } else {
            StreamCountryOptionMapper.countryOptions(listOf(country)).firstOrNull()?.label ?: country
        }

    private companion object {
        const val KEY_CATEGORY = "streams_filter_category_pick"
        const val KEY_TOPIC = "streams_filter_topic_pick"
        const val KEY_LANGUAGE = "streams_filter_language_pick"
        const val KEY_COUNTRY = "streams_filter_country_pick"

        const val TAG_CATEGORY = "streams_category_picker"
        const val TAG_TOPIC = "streams_topic_picker"
        const val TAG_LANGUAGE = "streams_language_picker"
        const val TAG_COUNTRY = "streams_country_picker"
    }
}
