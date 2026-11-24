package com.sza.fastmediasorter.ui.common

import android.content.Context
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.stats.StatsMediaType

/**
 * The single place in app_v2 that knows what colour a content type is.
 *
 * S2046: five independent tables used to assign a hue per media type and contradicted each other -
 * one hue was claimed by two different types at once. A surface asks here instead of deciding, so a
 * new surface costs a call rather than a sixth table.
 *
 * Colours are returned as resource ids, never as resolved ints, so the theme picks the day or the
 * night member on its own. A caller that memoises a resolved value must key that cache by night mode,
 * or it will serve light-theme colours after the user switches to dark.
 *
 * The wear module states the same contract for its own seven types in ui/common/ContentTypeCatalog,
 * against these same resource names - which is why the names are frozen.
 */
object MediaTypeColorCatalog {

    @ColorRes
    fun colorFor(category: MediaColorCategory): Int {
        return when (category) {
            MediaColorCategory.MUSIC -> R.color.color_media_music
            MediaColorCategory.VIDEO -> R.color.color_media_video
            MediaColorCategory.IMAGE -> R.color.color_media_image
            MediaColorCategory.DOCUMENT -> R.color.color_media_docs
            MediaColorCategory.OTHER -> R.color.color_media_other
        }
    }

    // Exhaustive with no else branch on purpose: a new MediaType member must fail compilation here
    // rather than silently inherit a default colour.
    fun categoryOf(type: MediaType): MediaColorCategory = when (type) {
        MediaType.IMAGE, MediaType.GIF -> MediaColorCategory.IMAGE
        MediaType.VIDEO -> MediaColorCategory.VIDEO
        MediaType.AUDIO -> MediaColorCategory.MUSIC
        MediaType.TEXT,
        MediaType.PDF,
        MediaType.EPUB,
        MediaType.OFFICE_DOCUMENT,
        -> MediaColorCategory.DOCUMENT

        MediaType.BINARY_ARCHIVE,
        MediaType.BINARY_DISK,
        MediaType.BINARY_EXECUTABLE,
        MediaType.BINARY_OTHER,
        -> MediaColorCategory.OTHER
    }

    fun categoryOf(type: StatsMediaType): MediaColorCategory = when (type) {
        StatsMediaType.IMAGE -> MediaColorCategory.IMAGE
        StatsMediaType.VIDEO -> MediaColorCategory.VIDEO
        StatsMediaType.AUDIO -> MediaColorCategory.MUSIC
        StatsMediaType.DOCUMENT -> MediaColorCategory.DOCUMENT
        StatsMediaType.OTHER -> MediaColorCategory.OTHER
    }

    @ColorRes
    fun colorFor(type: MediaType): Int = colorFor(categoryOf(type))

    @ColorRes
    fun colorFor(type: StatsMediaType): Int = colorFor(categoryOf(type))

    /** Single source of truth for the media-type filter letters (`I V A G T P E O`) and labels. */
    data class BadgeEntry(
        val type: MediaType,
        val letter: String,
        @StringRes val labelRes: Int,
    )

    private const val LEGEND_SEPARATOR = " \u00B7 "

    val badgeEntries: List<BadgeEntry> = listOf(
        BadgeEntry(MediaType.IMAGE, "I", R.string.media_type_image),
        BadgeEntry(MediaType.VIDEO, "V", R.string.media_type_video),
        BadgeEntry(MediaType.AUDIO, "A", R.string.media_type_audio),
        BadgeEntry(MediaType.GIF, "G", R.string.media_type_gif),
        BadgeEntry(MediaType.TEXT, "T", R.string.media_type_text),
        BadgeEntry(MediaType.PDF, "P", R.string.media_type_pdf),
        BadgeEntry(MediaType.EPUB, "E", R.string.media_type_epub),
        BadgeEntry(MediaType.OFFICE_DOCUMENT, "O", R.string.media_type_office_documents),
    )

    fun supportedBadgeEntries(mediaCapabilities: MediaCapabilities): List<BadgeEntry> =
        badgeEntries.filter { entry ->
            when (entry.type) {
                MediaType.IMAGE, MediaType.GIF -> mediaCapabilities.supportsImages
                MediaType.VIDEO -> mediaCapabilities.supportsVideo
                MediaType.AUDIO -> mediaCapabilities.supportsAudio
                MediaType.TEXT, MediaType.PDF, MediaType.OFFICE_DOCUMENT -> mediaCapabilities.supportsDocuments
                MediaType.EPUB -> mediaCapabilities.supportsEpub
                else -> false
            }
        }

    fun formatChipLabel(context: Context, entry: BadgeEntry): String =
        "${context.getString(entry.labelRes)} (${entry.letter})"

    fun formatDescription(context: Context, types: Set<MediaType>, allFiles: Boolean): String {
        if (allFiles) return context.getString(R.string.all_files)
        return badgeEntries
            .filter { it.type in types }
            .joinToString(", ") { formatChipLabel(context, it) }
    }

    fun formatColoredLegend(context: Context, types: Set<MediaType>): CharSequence {
        val builder = SpannableStringBuilder()
        badgeEntries
            .filter { it.type in types }
            .forEachIndexed { index, entry ->
                if (index > 0) builder.append(LEGEND_SEPARATOR)
                val letterStart = builder.length
                builder.append(entry.letter)
                val letterEnd = builder.length
                val color = ContextCompat.getColor(context, colorFor(entry.type))
                builder.setSpan(ForegroundColorSpan(color), letterStart, letterEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                builder.setSpan(StyleSpan(Typeface.BOLD), letterStart, letterEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                builder.append(' ').append(context.getString(entry.labelRes))
            }
        return builder
    }
}
