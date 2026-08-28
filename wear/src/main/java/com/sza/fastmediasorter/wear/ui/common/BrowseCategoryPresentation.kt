package com.sza.fastmediasorter.wear.ui.common

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.browse.BrowseCategoryCatalog
import com.sza.fastmediasorter.wear.domain.model.WearBrowseCategory
import com.sza.fastmediasorter.wear.domain.model.WearContentType

/**
 * How a browse category is written and drawn.
 *
 * S2130 pillar 3: `BrowseCategoryCatalog` decides which categories a source offers, this object
 * decides what each of them looks like, and neither knows the other's answer. The split is the same
 * one [ContentTypeCatalog] already makes for a content type, and it is here rather than in the domain
 * layer for the same reason: a `@StringRes` id and a `@DrawableRes` id are Android types, and
 * `WearBrowseCategory` carries none.
 *
 * The label keys keep their `wear_phone_*` names although all three category screens now read them.
 * Renaming seven keys across thirteen locales would move no user-visible text and would put a
 * translation round trip in front of a behaviour fix; the names record where the wording was first
 * settled, not which screen may use it.
 *
 * ## The image label
 *
 * One category, one key: `wear_phone_images`. The Local and Network screens used to read the generic
 * `photos` key ("Photos") while the Phone screen read this one ("Images"), so the same row was named
 * two different things depending on the way in. The owner named the word he wanted directly - images,
 * not photos - and that is the surviving key.
 */
object BrowseCategoryPresentation {

    /**
     * The label [category] is written with.
     *
     * The `else` branch is unreachable while the vocabulary and this table agree, which
     * `BrowseCategoryPresentationTest` is what keeps true. It falls back to the browse label rather
     * than throwing because a chip drawn with the wrong word is recoverable and a crash on a home
     * screen is not.
     */
    @StringRes
    fun labelFor(category: WearBrowseCategory): Int = when (category.token) {
        BrowseCategoryCatalog.TOKEN_RECENTS -> R.string.wear_phone_recents
        BrowseCategoryCatalog.TOKEN_VIDEOS -> R.string.wear_phone_video
        BrowseCategoryCatalog.TOKEN_MUSIC -> R.string.wear_phone_audio
        BrowseCategoryCatalog.TOKEN_PHOTOS -> R.string.wear_phone_images
        BrowseCategoryCatalog.TOKEN_DOCUMENTS -> R.string.wear_phone_documents
        BrowseCategoryCatalog.TOKEN_ALL -> R.string.wear_phone_all
        else -> R.string.wear_phone_browse
    }

    /**
     * The glyph [category] is drawn with.
     *
     * Recents is the one entry that is not a content type - it is a time filter over every type - so
     * it carries its own symbol and borrows only the tone. Everything else asks [ContentTypeCatalog],
     * which keeps one glyph per entity across both apps.
     */
    @DrawableRes
    fun glyphFor(category: WearBrowseCategory): Int =
        if (category.token == BrowseCategoryCatalog.TOKEN_RECENTS) {
            R.drawable.ic_history
        } else {
            ContentTypeCatalog.iconFor(category.type)
        }

    /**
     * The semantic tone for a category glyph, or none when the painter already carries its own colour.
     *
     * The catalog is asked rather than tinted blindly because an already-coloured vector must keep
     * what it has. This is the one copy of that guard for the three category screens; before S2130 the
     * Local and Phone screens held a private copy each and the Network screen applied no tone at all,
     * which is the defect the owner reported as chips that were not coloured like their siblings.
     */
    @Composable
    fun tintFor(type: WearContentType): Color =
        if (ContentTypeCatalog.isMonochrome(type)) {
            colorResource(ContentTypeCatalog.tintFor(type))
        } else {
            Color.Unspecified
        }
}
