package com.sza.fastmediasorter.wear.domain.browse

import com.sza.fastmediasorter.wear.domain.model.WearBrowseCategory
import com.sza.fastmediasorter.wear.domain.model.WearCategoryOrigin
import com.sza.fastmediasorter.wear.domain.model.WearContentType
import com.sza.fastmediasorter.wear.domain.model.WearListShape

/**
 * The single place in this module that knows which categories a source offers, and in what order.
 *
 * S2130 pillar 1: one owner of the category *composition*, kept apart from the owner of its
 * appearance in `ui/common/ContentTypeCatalog`. Before this object each of the three category screens
 * wrote its own literal list and its own token, the settings screen enumerated a fourth set, and the
 * phone side hand-mirrored a fifth; nothing failed when one of them was edited alone.
 *
 * ## What decides that a category exists
 *
 * A category exists for an origin when the watch can **present** that content type from that origin.
 * That is deliberately a different question from the one `WearFileCapabilityPolicy` answers, which is
 * what file *operations* a file permits. The two were conflated before S2130, and the conflation gave
 * two wrong answers: the watch's own store was refused Documents on the grounds that "a document there
 * has no address", though that storage class does permit sending a file to the phone and
 * `MediaStore.Files` addresses documents perfectly well; and a network share was refused every
 * category because no operation is allowed on it, though the watch shows and plays network video today.
 *
 * Being able to present a type means a query for it exists. Where one does not, the category stays
 * out rather than appearing and being permanently empty - see [isPresentable] for the two absences
 * that remain, and for why each of them is a different absence.
 */
object BrowseCategoryCatalog {

    const val TOKEN_RECENTS = "recents"
    const val TOKEN_VIDEOS = "videos"
    const val TOKEN_MUSIC = "music"
    const val TOKEN_PHOTOS = "photos"
    const val TOKEN_DOCUMENTS = "documents"
    const val TOKEN_ALL = "all"

    /**
     * Not a route argument like the other six: the folder browser is reached by its own route, which
     * carries no media type at all. The token exists so the entry has an identity in [VOCABULARY] and
     * so [shapeForToken] can answer for it, not so it can be put in a navigation call.
     */
    const val TOKEN_BROWSE = "browse"

    /**
     * The types a user may switch off in settings.
     *
     * The navigational entries - recents, all, browse - are not in here: they are ways of looking at
     * whatever is allowed, not content types of their own, and switching "all" off would mean
     * something the settings screen never offered to mean.
     */
    val DISABLEABLE_TYPES: Set<WearContentType> = setOf(
        WearContentType.MUSIC,
        WearContentType.VIDEO,
        WearContentType.IMAGE,
        WearContentType.DOCUMENT
    )

    /**
     * The full ordered vocabulary, in the order the owner named it on the Phone screen: recents,
     * video, audio, images, documents, all, browse.
     */
    private val VOCABULARY: List<WearBrowseCategory> = listOf(
        WearBrowseCategory(WearContentType.OTHER, TOKEN_RECENTS, WearListShape.FLAT_MEDIA),
        WearBrowseCategory(WearContentType.VIDEO, TOKEN_VIDEOS, WearListShape.FLAT_MEDIA),
        WearBrowseCategory(WearContentType.MUSIC, TOKEN_MUSIC, WearListShape.FLAT_MEDIA),
        WearBrowseCategory(WearContentType.IMAGE, TOKEN_PHOTOS, WearListShape.FLAT_MEDIA),
        WearBrowseCategory(WearContentType.DOCUMENT, TOKEN_DOCUMENTS, WearListShape.FLAT_MEDIA),
        WearBrowseCategory(WearContentType.OTHER, TOKEN_ALL, WearListShape.FLAT_MEDIA),
        WearBrowseCategory(WearContentType.FOLDER, TOKEN_BROWSE, WearListShape.FOLDER_WALK)
    )

    /**
     * The categories [origin] offers, already narrowed to what the user left enabled.
     *
     * [allowedTypes] only ever removes a member of [DISABLEABLE_TYPES]; a navigational entry survives
     * any settings state, because no setting claims to control it.
     */
    fun categoriesFor(
        origin: WearCategoryOrigin,
        allowedTypes: Set<WearContentType>
    ): List<WearBrowseCategory> = VOCABULARY.filter { category ->
        isPresentable(category, origin) && isEnabled(category.type, allowedTypes)
    }

    /** The token [category] travels by, which is also its identity in this vocabulary. */
    fun tokenFor(category: WearBrowseCategory): String = category.token

    /**
     * The list shape a route argument asks for.
     *
     * A null token is the folder browser: its route carries no media type, and that absence is the
     * only thing distinguishing it from the "all" entry, which is a flat list of every media file.
     * Collapsing those two is exactly the defect S2130 exists to fix.
     */
    fun shapeForToken(token: String?): WearListShape {
        if (token == null || token == TOKEN_BROWSE) {
            return WearListShape.FOLDER_WALK
        }
        return VOCABULARY.firstOrNull { it.token == token }?.shape ?: WearListShape.FLAT_MEDIA
    }

    /** The category carrying [token], or null when the token names none. */
    fun categoryForToken(token: String?): WearBrowseCategory? =
        token?.let { wanted -> VOCABULARY.firstOrNull { it.token == wanted } }

    private fun isEnabled(type: WearContentType, allowedTypes: Set<WearContentType>): Boolean =
        type !in DISABLEABLE_TYPES || type in allowedTypes

    /**
     * Whether the watch can present this category from this origin.
     *
     * The two watch-side origins used to share one narrow set of three media types. They no longer
     * do, and only one of them is narrow at all now:
     *
     * - The watch's own store now offers all seven. It reaches documents through `MediaStore.Files`,
     *   answers a flat mixed listing by merging the collections it queries, and since S2201 has a
     *   folder walk behind `browse`. That walk spans both halves of watch storage, because only one
     *   of them is reachable by a filesystem walk: the app's own roots are enumerated directly and
     *   hold files MediaStore never indexed, while shared storage - which no app may walk at
     *   targetSdk 36 without special access - is reconstructed as a hierarchy by grouping rows on
     *   `RELATIVE_PATH`.
     * - A network share is listed one directory at a time over SMB, FTP or SFTP, with no index to
     *   sort by date across directories and no document handling, so it stays at the three media
     *   types it can filter a single listing down to.
     *
     * The paired phone has all seven because the phone answers with both list shapes already.
     */
    private fun isPresentable(category: WearBrowseCategory, origin: WearCategoryOrigin): Boolean =
        when (origin) {
            WearCategoryOrigin.PHONE -> true
            WearCategoryOrigin.LOCAL -> true
            WearCategoryOrigin.NETWORK_SOURCE -> category.type in NETWORK_PRESENTABLE_TYPES
        }

    /** What a network share can show: the three media types a single directory listing can filter. */
    private val NETWORK_PRESENTABLE_TYPES: Set<WearContentType> = setOf(
        WearContentType.MUSIC,
        WearContentType.VIDEO,
        WearContentType.IMAGE
    )
}
