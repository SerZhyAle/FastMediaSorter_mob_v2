package com.sza.fastmediasorter.ui.common

import androidx.annotation.ColorRes
import com.sza.fastmediasorter.R
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
}
