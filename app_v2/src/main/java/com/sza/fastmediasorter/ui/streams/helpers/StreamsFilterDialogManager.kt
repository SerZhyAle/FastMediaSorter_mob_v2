package com.sza.fastmediasorter.ui.streams.helpers

import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.DialogStreamsFilterBinding
import com.sza.fastmediasorter.ui.dialog.DialogKeyboardDelegate
import com.sza.fastmediasorter.ui.dialog.SearchableOptionPickerDialog
import com.sza.fastmediasorter.ui.streams.StreamsViewModel

/**
 * Hosts the streams filter dialog so [com.sza.fastmediasorter.ui.streams.StreamsActivity] stays free of
 * dialog logic (Rule 3/5). Presents the All/Audio/Video media-kind toggle on top, then category and
 * language as two tappable columns, then full-width topic (S1168) and country (S0761) rows; each opens a
 * [SearchableOptionPickerDialog]. "Clear filters" lives in the dialog button bar
 * (neutral) next to OK and resets in place without dismissing. Selections are applied live through
 * [onApply] (the ViewModel owns the actual filtering). Category and topic options are flag-less; language
 * options carry a flag where the name resolves to a known language ([StreamLanguageOptionMapper]); country
 * options carry a flag from the ISO code ([StreamCountryOptionMapper]).
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
            binding.tvTopicValue.text = topic ?: activity.getString(R.string.streams_filter_all)
            binding.tvLanguageValue.text = languageLabel(language)
            binding.tvCountryValue.text = countryLabel(country)
        }
        renderValues()
        // A catalog without topics would open an empty picker, so the row is hidden rather than dead.
        binding.rowTopic.isVisible = state.facets.topics.isNotEmpty()
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
                    onApply(category, topic, language, country, mediaKind, pinnedOnly)
                },
            ).show(activity.supportFragmentManager, "streams_category_picker")
        }

        binding.rowTopic.setOnClickListener {
            SearchableOptionPickerDialog.newInstance(
                title = activity.getString(R.string.streams_filter_topic),
                options = StreamLanguageOptionMapper.categoryOptions(state.facets.topics),
                selectedId = topic,
                onPicked = { picked ->
                    topic = picked?.id
                    renderValues()
                    onApply(category, topic, language, country, mediaKind, pinnedOnly)
                },
            ).show(activity.supportFragmentManager, "streams_topic_picker")
        }

        binding.rowLanguage.setOnClickListener {
            SearchableOptionPickerDialog.newInstance(
                title = activity.getString(R.string.streams_filter_language),
                options = StreamLanguageOptionMapper.languageOptions(state.facets.languages),
                selectedId = language,
                onPicked = { picked ->
                    language = picked?.id
                    renderValues()
                    onApply(category, topic, language, country, mediaKind, pinnedOnly)
                },
            ).show(activity.supportFragmentManager, "streams_language_picker")
        }

        binding.rowCountry.setOnClickListener {
            showCountryPicker(state.facets.countries, country) { picked ->
                country = picked
                renderValues()
                onApply(category, topic, language, country, mediaKind, pinnedOnly)
            }
        }

        binding.toggleMediaKind.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            mediaKind = when (checkedId) {
                binding.btnMediaAudio.id -> StreamsViewModel.MediaKindFilter.AUDIO
                binding.btnMediaVideo.id -> StreamsViewModel.MediaKindFilter.VIDEO
                else -> StreamsViewModel.MediaKindFilter.ALL
            }
            onApply(category, topic, language, country, mediaKind, pinnedOnly)
        }

        binding.checkPinnedOnly.setOnCheckedChangeListener { _, isChecked ->
            pinnedOnly = isChecked
            onApply(category, topic, language, country, mediaKind, pinnedOnly)
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
                topic = null
                language = null
                country = null
                mediaKind = StreamsViewModel.MediaKindFilter.ALL
                pinnedOnly = false
                renderValues()
                binding.toggleMediaKind.check(binding.btnMediaAll.id)
                binding.checkPinnedOnly.isChecked = false
                onApply(category, topic, language, country, mediaKind, pinnedOnly)
            }
        }
        // Escape dismisses; Enter confirms via OK (row/toggle changes already apply the filter live).
        DialogKeyboardDelegate.applyTo(dialog) {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.performClick()
        }
        dialog.show()
    }

    /** Launches the country picker; [onPicked] receives the chosen code (null = cleared/All). */
    private fun showCountryPicker(
        countries: List<String>,
        selected: String?,
        onPicked: (String?) -> Unit,
    ) {
        SearchableOptionPickerDialog.newInstance(
            title = activity.getString(R.string.streams_filter_country),
            options = StreamCountryOptionMapper.countryOptions(countries),
            selectedId = selected,
            onPicked = { picked -> onPicked(picked?.id) },
        ).show(activity.supportFragmentManager, "streams_country_picker")
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

    /** Decorates the active country code with its flag ("UA" -> "🇺🇦 UA"); "All" when no country is set. */
    private fun countryLabel(country: String?): String =
        if (country == null) {
            activity.getString(R.string.streams_filter_all)
        } else {
            StreamCountryOptionMapper.countryOptions(listOf(country)).firstOrNull()?.label ?: country
        }
}
