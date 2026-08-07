package com.sza.fastmediasorter.ui.streams.helpers

import android.content.Context
import androidx.annotation.StringRes
import com.sza.fastmediasorter.R

/**
 * S1477: display names for the catalog's closed rubric set.
 *
 * The `topic` cell of `streams.csv` used to be free text from whatever source supplied the row, so it
 * could not be translated - 437 distinct values, most of them used once. The offline collector now
 * folds every topic into the 31 rubrics below (`Get-CanonicalTopic` in `collect-stream-candidates.ps1`),
 * and a closed set is small enough to carry real translations.
 *
 * The catalog id stays the untranslated English rubric, because that is what ships in the CSV and what
 * [com.sza.fastmediasorter.ui.streams.StreamsViewModel.StreamsFilter] matches on; only the label shown
 * to the user is localized. An id this build does not know - a catalog published by a newer collector,
 * or a hand-added channel - falls back to its raw text rather than disappearing from the picker.
 */
object StreamTopicRubricCatalog {

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
        // S1476: kept apart from Webcam because a traffic camera republishes a short clip rather than
        // running a continuous feed - the list should not promise an live stream it cannot deliver.
        "Traffic cams" to R.string.streams_rubric_traffic_cams,
        "Reggae" to R.string.streams_rubric_reggae,
        "Shopping" to R.string.streams_rubric_shopping,
        "Business" to R.string.streams_rubric_business,
        "Lifestyle" to R.string.streams_rubric_lifestyle,
        "Adult" to R.string.streams_rubric_adult,
    )

    /** Localized label for a catalog rubric, or the raw value when this build does not know the id. */
    fun label(context: Context, rubric: String?): String? {
        val raw = rubric?.takeIf { it.isNotBlank() } ?: return null
        val res: Int? = LABELS[raw] ?: LABELS.entries.firstOrNull { it.key.equals(raw, ignoreCase = true) }?.value
        return res?.let { context.getString(it) } ?: raw
    }

    @StringRes
    fun labelRes(rubric: String): Int? = LABELS[rubric]
}
