package com.sza.fastmediasorter.wear.ui.streams.helpers

import android.content.Context
import com.sza.fastmediasorter.wear.R

/**
 * S2146: display names for the catalogue's closed rubric set, on the watch.
 *
 * The mirror of the phone's `StreamTopicRubricCatalog` (S1477). Both modules read one `streams.csv`
 * whose `topic` cell the offline collector folds into the closed set below, but only the phone ever
 * gained this layer - so the watch's filter dialog listed raw lowercase English, which is the
 * mechanical cause of the owner's complaint recorded in strategic §1. The string values themselves are
 * copied verbatim from `:app_v2` and pinned as `Mirrored` pairs in
 * `scripts/quality/wear-mirrored-strings.psd1`, so a rubric reworded on one side alone fails a gate
 * rather than silently making the two devices disagree.
 *
 * The id stays the untranslated English rubric, because that is what the CSV ships and what the
 * projection filters on; only the shown label is localized. An id this build does not know - a
 * catalogue published by a newer collector - falls back to its own text and stays selectable, which is
 * strategic §11 criterion 1.
 */
object WearStreamRubricCatalog {

    private val LABELS: Map<String, Int> = mapOf(
        "General" to R.string.streams_rubric_general,
        "News" to R.string.streams_rubric_news,
        "Pop" to R.string.streams_rubric_pop,
        "Religious" to R.string.streams_rubric_religious,
        "Kids" to R.string.streams_rubric_kids,
        "Oldies" to R.string.streams_rubric_oldies,
        "Movies & Series" to R.string.streams_rubric_movies_series,
        "Country & Folk" to R.string.streams_rubric_country_folk,
        "Rock" to R.string.streams_rubric_rock,
        "Sports" to R.string.streams_rubric_sports,
        "World" to R.string.streams_rubric_world,
        "Jazz & Blues" to R.string.streams_rubric_jazz_blues,
        "Chillout" to R.string.streams_rubric_chillout,
        "Electronic" to R.string.streams_rubric_electronic,
        "Hip-hop" to R.string.streams_rubric_hip_hop,
        "Talk" to R.string.streams_rubric_talk,
        "Local radio" to R.string.streams_rubric_local_radio,
        "Comedy" to R.string.streams_rubric_comedy,
        "Classical" to R.string.streams_rubric_classical,
        "Latin" to R.string.streams_rubric_latin,
        "Documentary" to R.string.streams_rubric_documentary,
        "Education" to R.string.streams_rubric_education,
        "Test" to R.string.streams_rubric_test,
        "R&B & Soul" to R.string.streams_rubric_rnb_soul,
        "Metal" to R.string.streams_rubric_metal,
        "Webcam" to R.string.streams_rubric_webcam,
        "Traffic cams" to R.string.streams_rubric_traffic_cams,
        "Reggae" to R.string.streams_rubric_reggae,
        "Shopping" to R.string.streams_rubric_shopping,
        "Business" to R.string.streams_rubric_business,
        "Lifestyle" to R.string.streams_rubric_lifestyle,
        "Adult" to R.string.streams_rubric_adult,
    )

    /** Localized label for a catalogue rubric, or the raw value when this build does not know the id. */
    fun label(context: Context, rubric: String?): String? {
        val raw = rubric?.takeIf { it.isNotBlank() } ?: return null
        val res = LABELS[raw] ?: LABELS.entries.firstOrNull { it.key.equals(raw, ignoreCase = true) }?.value
        return res?.let { context.getString(it) } ?: raw
    }
}
