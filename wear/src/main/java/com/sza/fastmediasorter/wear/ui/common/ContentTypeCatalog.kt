package com.sza.fastmediasorter.wear.ui.common

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.WearContentType

/**
 * The single place in this module that knows what a content type looks like.
 *
 * S2003 pillar 4: one reference role owning both halves of the visual contract - the glyph and the
 * semantic tone - so a screen asks rather than decides. The neighbouring surface tickets consume
 * this object instead of re-deriving the rule on each of their screens.
 *
 * The glyphs are the phone's own vectors copied into this module, as `docs/ICON_LEGEND.md` governs:
 * one entity wears one glyph across both apps. The tones are resource references rather than
 * literals, so folding the phone's colour schemes together later re-points this file's colour
 * resources without touching a single screen.
 */
object ContentTypeCatalog {

    @DrawableRes
    fun iconFor(type: WearContentType): Int = when (type) {
        WearContentType.MUSIC -> R.drawable.ic_audio
        WearContentType.VIDEO -> R.drawable.ic_video
        WearContentType.IMAGE -> R.drawable.ic_image
        WearContentType.DOCUMENT -> R.drawable.ic_document
        WearContentType.FOLDER -> R.drawable.ic_folder
        // Streams share the cast glyph exactly as the phone's ResourceTypeIconMap does for both
        // HTTP and RTSP - a second glyph would claim a distinction the app does not make.
        WearContentType.STREAM -> R.drawable.ic_cast
        // The phone's umbrella glyph for "a source registered in this app", already the fallback
        // the home screen gives its Resources section.
        WearContentType.OTHER -> R.drawable.ic_resource
    }

    @ColorRes
    fun tintFor(type: WearContentType): Int = when (type) {
        WearContentType.MUSIC -> R.color.color_media_music
        WearContentType.VIDEO -> R.color.color_media_video
        WearContentType.IMAGE -> R.color.color_media_image
        WearContentType.DOCUMENT -> R.color.color_media_docs
        WearContentType.FOLDER -> R.color.color_media_folder
        WearContentType.STREAM -> R.color.color_media_stream
        WearContentType.OTHER -> R.color.color_media_other
    }

    /**
     * Whether [iconFor] returns a single-tone glyph that [tintFor] may colour.
     *
     * All seven are true today because the seven glyphs were picked single-tone precisely so the
     * tone could be applied - the phone's gold `ic_music_note` and its fixed-colour `ic_virtual_*`
     * trio were rejected for this set for that reason.
     *
     * A caller consults this rather than tinting unconditionally, because an already-coloured
     * vector must keep its own colour: the favourites star `ic_resource_favorites` is the standing
     * example, a fixed amber badge with no tint hook that a semantic tone would repaint. A coloured
     * type added here returns false, and callers already handle that branch.
     *
     * S1124 draws the same distinction on the phone in `core/panel/ResourceTypeIconMap`, so this is
     * a transferred convention rather than a new one.
     */
    fun isMonochrome(type: WearContentType): Boolean = when (type) {
        WearContentType.MUSIC,
        WearContentType.VIDEO,
        WearContentType.IMAGE,
        WearContentType.DOCUMENT,
        WearContentType.FOLDER,
        WearContentType.STREAM,
        WearContentType.OTHER -> true
    }
}
