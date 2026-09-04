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
     * S2495: the watch's own voice notes, reached by the recorder's note list rather than by a media
     * query. Like [TOKEN_BROWSE] it is not a route argument - the note list reads the app's own index
     * and carries no media type - and like the navigational entries it is not switchable in settings:
     * it is a way of looking at one store, not a content type a user may turn off.
     */
    const val TOKEN_VOICE_NOTES = "voice_notes"

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
     * video, audio, images, documents, all, voice notes, browse.
     *
     * Voice notes sit immediately before browse so the two land in one row of a multi-column view,
     * which is the arrangement the owner asked for (S2495 strategic §5.1 pillar 6).
     */
    private val VOCABULARY: List<WearBrowseCategory> = listOf(
        WearBrowseCategory(WearContentType.OTHER, TOKEN_RECENTS, WearListShape.FLAT_MEDIA),
        WearBrowseCategory(WearContentType.VIDEO, TOKEN_VIDEOS, WearListShape.FLAT_MEDIA),
        WearBrowseCategory(WearContentType.MUSIC, TOKEN_MUSIC, WearListShape.FLAT_MEDIA),
        WearBrowseCategory(WearContentType.IMAGE, TOKEN_PHOTOS, WearListShape.FLAT_MEDIA),
        WearBrowseCategory(WearContentType.DOCUMENT, TOKEN_DOCUMENTS, WearListShape.FLAT_MEDIA),
        WearBrowseCategory(WearContentType.OTHER, TOKEN_ALL, WearListShape.FLAT_MEDIA),
        WearBrowseCategory(WearContentType.OTHER, TOKEN_VOICE_NOTES, WearListShape.FLAT_MEDIA),
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

    /**
     * S2487: The categories a specific network source offers based on its phone-configured
     * `supportedMediaTypes` and `allFiles` flag, narrowed by what the user left enabled in Wear settings.
     */
    fun categoriesForSource(
        source: com.sza.fastmediasorter.wear.domain.model.NetworkSource?,
        allowedTypes: Set<WearContentType>
    ): List<WearBrowseCategory> {
        if (source == null) {
            return categoriesFor(WearCategoryOrigin.NETWORK_SOURCE, allowedTypes)
        }
        val supportedTypes = mutableSetOf<WearContentType>()
        if (source.allFiles) {
            supportedTypes.addAll(DISABLEABLE_TYPES)
        } else if (!source.supportedMediaTypes.isNullOrEmpty()) {
            for (typeStr in source.supportedMediaTypes) {
                when (typeStr.uppercase()) {
                    "AUDIO" -> supportedTypes.add(WearContentType.MUSIC)
                    "VIDEO" -> supportedTypes.add(WearContentType.VIDEO)
                    "IMAGE", "GIF" -> supportedTypes.add(WearContentType.IMAGE)
                    "TEXT", "PDF", "EPUB", "OFFICE_DOCUMENT", "DOCUMENT" -> supportedTypes.add(WearContentType.DOCUMENT)
                }
            }
        } else {
            supportedTypes.addAll(NETWORK_PRESENTABLE_TYPES)
        }

        return VOCABULARY.filter { category ->
            val isSupportedBySource = when (category.token) {
                TOKEN_ALL, TOKEN_BROWSE -> source.allFiles
                TOKEN_RECENTS -> false
                // S2495: stated rather than left to the type test, which only refuses it by accident.
                TOKEN_VOICE_NOTES -> false
                TOKEN_MUSIC -> WearContentType.MUSIC in supportedTypes
                TOKEN_VIDEOS -> WearContentType.VIDEO in supportedTypes
                TOKEN_PHOTOS -> WearContentType.IMAGE in supportedTypes
                TOKEN_DOCUMENTS -> WearContentType.DOCUMENT in supportedTypes
                else -> category.type in supportedTypes
            }
            isSupportedBySource && isEnabled(category.type, allowedTypes)
        }
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
     *
     * S2495: voice notes are the exception that belongs to one origin only. The notes are recorded on
     * this watch and indexed by this app, so neither the paired phone nor a network share holds any -
     * an entry offered there would open a list that is empty by construction, not by circumstance.
     */
    private fun isPresentable(category: WearBrowseCategory, origin: WearCategoryOrigin): Boolean =
        when (origin) {
            WearCategoryOrigin.PHONE -> category.token != TOKEN_VOICE_NOTES
            WearCategoryOrigin.LOCAL -> true
            WearCategoryOrigin.NETWORK_SOURCE ->
                category.token != TOKEN_VOICE_NOTES && category.type in NETWORK_PRESENTABLE_TYPES
        }

    /** What a network share can show: the three media types a single directory listing can filter. */
    private val NETWORK_PRESENTABLE_TYPES: Set<WearContentType> = setOf(
        WearContentType.MUSIC,
        WearContentType.VIDEO,
        WearContentType.IMAGE
    )
}
