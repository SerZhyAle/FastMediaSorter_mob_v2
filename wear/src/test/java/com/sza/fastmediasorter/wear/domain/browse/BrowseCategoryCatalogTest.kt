package com.sza.fastmediasorter.wear.domain.browse

import com.sza.fastmediasorter.wear.domain.model.WearCategoryOrigin
import com.sza.fastmediasorter.wear.domain.model.WearContentType
import com.sza.fastmediasorter.wear.domain.model.WearListShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S2130: the defect this catalog replaces was four vocabularies drifting apart with nothing failing.
 * The cross-origin token assertions below are that missing failure - they are what turns "the same
 * category means the same thing everywhere" from a convention into something a machine enforces.
 */
class BrowseCategoryCatalogTest {

    private companion object {
        val ALL_TYPES: Set<WearContentType> = WearContentType.entries.toSet()

        val PHONE_ORDER: List<String> = listOf(
            BrowseCategoryCatalog.TOKEN_RECENTS,
            BrowseCategoryCatalog.TOKEN_VIDEOS,
            BrowseCategoryCatalog.TOKEN_MUSIC,
            BrowseCategoryCatalog.TOKEN_PHOTOS,
            BrowseCategoryCatalog.TOKEN_DOCUMENTS,
            BrowseCategoryCatalog.TOKEN_ALL,
            BrowseCategoryCatalog.TOKEN_BROWSE
        )
    }

    @Test
    fun `phone origin offers the seven entries the owner named, in order`() {
        val tokens = BrowseCategoryCatalog
            .categoriesFor(WearCategoryOrigin.PHONE, ALL_TYPES)
            .map { it.token }

        assertEquals(PHONE_ORDER, tokens)
    }

    @Test
    fun `every offered category carries a non-blank token`() {
        WearCategoryOrigin.entries.forEach { origin ->
            BrowseCategoryCatalog.categoriesFor(origin, ALL_TYPES).forEach { category ->
                assertTrue(
                    "origin $origin offered a category with a blank token",
                    category.token.isNotBlank()
                )
            }
        }
    }

    @Test
    fun `a disabled type is absent from every origin`() {
        val withoutVideo = ALL_TYPES - WearContentType.VIDEO

        WearCategoryOrigin.entries.forEach { origin ->
            val tokens = BrowseCategoryCatalog.categoriesFor(origin, withoutVideo).map { it.token }
            assertFalse(
                "origin $origin still offered video after it was disabled",
                tokens.contains(BrowseCategoryCatalog.TOKEN_VIDEOS)
            )
        }
    }

    @Test
    fun `navigational entries survive an empty allowed set`() {
        val tokens = BrowseCategoryCatalog
            .categoriesFor(WearCategoryOrigin.PHONE, emptySet())
            .map { it.token }

        assertEquals(
            listOf(
                BrowseCategoryCatalog.TOKEN_RECENTS,
                BrowseCategoryCatalog.TOKEN_ALL,
                BrowseCategoryCatalog.TOKEN_BROWSE
            ),
            tokens
        )
    }

    @Test
    fun `a content type keeps one token across every origin`() {
        val byOrigin = WearCategoryOrigin.entries.map { origin ->
            BrowseCategoryCatalog.categoriesFor(origin, ALL_TYPES)
                .filter { it.type in BrowseCategoryCatalog.DISABLEABLE_TYPES }
                .associate { it.type to it.token }
        }

        byOrigin.forEach { mapping ->
            mapping.forEach { (type, token) ->
                byOrigin.forEach { other ->
                    val otherToken = other[type] ?: return@forEach
                    assertEquals("type $type is addressed by two different tokens", token, otherToken)
                }
            }
        }
    }

    @Test
    fun `all and browse are different shapes`() {
        val allShape = BrowseCategoryCatalog.shapeForToken(BrowseCategoryCatalog.TOKEN_ALL)
        val browseShape = BrowseCategoryCatalog.shapeForToken(BrowseCategoryCatalog.TOKEN_BROWSE)

        assertEquals(WearListShape.FLAT_MEDIA, allShape)
        assertEquals(WearListShape.FOLDER_WALK, browseShape)
        assertNotEquals(allShape, browseShape)
    }

    @Test
    fun `an absent token is the folder browser`() {
        assertEquals(WearListShape.FOLDER_WALK, BrowseCategoryCatalog.shapeForToken(null))
    }

    @Test
    fun `recents is a flat list`() {
        assertEquals(
            WearListShape.FLAT_MEDIA,
            BrowseCategoryCatalog.shapeForToken(BrowseCategoryCatalog.TOKEN_RECENTS)
        )
    }

    /**
     * S2201 completed this origin, and S2495 added the eighth entry: the watch's own store is now the
     * only origin offering more than the paired phone, because the notes it lists exist nowhere else.
     * Until the folder walk existed it stopped at six, and the assertion below is what would catch a
     * later change silently dropping one back out.
     *
     * Voice notes sit immediately before browse deliberately: the order is what puts the two in one
     * row of a three-column view, which is the arrangement the owner asked for.
     */
    @Test
    fun `the watch own store offers the eight entries the owner named, in order`() {
        val tokens = BrowseCategoryCatalog
            .categoriesFor(WearCategoryOrigin.LOCAL, ALL_TYPES)
            .map { it.token }

        assertEquals(
            listOf(
                BrowseCategoryCatalog.TOKEN_RECENTS,
                BrowseCategoryCatalog.TOKEN_VIDEOS,
                BrowseCategoryCatalog.TOKEN_MUSIC,
                BrowseCategoryCatalog.TOKEN_PHOTOS,
                BrowseCategoryCatalog.TOKEN_DOCUMENTS,
                BrowseCategoryCatalog.TOKEN_ALL,
                BrowseCategoryCatalog.TOKEN_VOICE_NOTES,
                BrowseCategoryCatalog.TOKEN_BROWSE
            ),
            tokens
        )
    }

    /**
     * S2495: the notes are recorded on this watch and indexed by this app. An entry offered to the
     * paired phone or to a network share would open a list that is empty by construction rather than
     * by circumstance, which reads to a user as a broken screen and not as an absence of content.
     */
    @Test
    fun `only the watch own store offers voice notes`() {
        WearCategoryOrigin.entries.forEach { origin ->
            val offered = BrowseCategoryCatalog
                .categoriesFor(origin, ALL_TYPES)
                .map { it.token }
                .contains(BrowseCategoryCatalog.TOKEN_VOICE_NOTES)

            assertEquals(
                "origin $origin disagrees about offering voice notes",
                origin == WearCategoryOrigin.LOCAL,
                offered
            )
        }
    }

    /** A user switching a media type off must not lose an entry no setting claims to control. */
    @Test
    fun `voice notes survive every content type being switched off`() {
        val tokens = BrowseCategoryCatalog
            .categoriesFor(WearCategoryOrigin.LOCAL, emptySet())
            .map { it.token }

        assertTrue(
            "voice notes vanished with the media types, though no setting turns them off",
            tokens.contains(BrowseCategoryCatalog.TOKEN_VOICE_NOTES)
        )
    }

    /**
     * The entry S2201 added, kept pinned separately for the reason it was pinned when it was absent:
     * a seventh token appearing or disappearing should fail here by name rather than as an
     * off-by-one in a whole-list expectation. It must stay present because a folder walk now exists
     * behind it, over the app's own roots and a hierarchy grouped from MediaStore `RELATIVE_PATH`.
     */
    @Test
    fun `the watch own store offers the folder walk`() {
        val tokens = BrowseCategoryCatalog
            .categoriesFor(WearCategoryOrigin.LOCAL, ALL_TYPES)
            .map { it.token }

        assertTrue(
            "the local origin dropped the folder walk it has a surface for",
            tokens.contains(BrowseCategoryCatalog.TOKEN_BROWSE)
        )
    }

    /**
     * The two watch-side origins used to share one narrowing constant, so widening either widened
     * both. They are narrow for different reasons - a query gap on the watch store, a directory-at-a-
     * time protocol on a share - and this is what keeps the split from being quietly undone.
     */
    @Test
    fun `a network share offers less than the watch own store`() {
        val local = BrowseCategoryCatalog
            .categoriesFor(WearCategoryOrigin.LOCAL, ALL_TYPES)
            .map { it.token }
        val network = BrowseCategoryCatalog
            .categoriesFor(WearCategoryOrigin.NETWORK_SOURCE, ALL_TYPES)
            .map { it.token }

        assertNotEquals(local, network)
        assertEquals(
            listOf(
                BrowseCategoryCatalog.TOKEN_VIDEOS,
                BrowseCategoryCatalog.TOKEN_MUSIC,
                BrowseCategoryCatalog.TOKEN_PHOTOS
            ),
            network
        )
        assertTrue(
            "a category a share cannot serve reached the network origin",
            network.all { it in local }
        )
    }

    @Test
    fun `categoriesForSource returns single category for audio-only resource`() {
        val source = com.sza.fastmediasorter.wear.domain.model.NetworkSource(
            type = com.sza.fastmediasorter.wear.domain.model.NetworkSourceType.SMB,
            name = "Audio Library",
            server = "192.168.1.10",
            username = "user",
            password = "pass",
            supportedMediaTypes = listOf("AUDIO")
        )
        val categories = BrowseCategoryCatalog.categoriesForSource(source, ALL_TYPES)
        assertEquals(1, categories.size)
        assertEquals(BrowseCategoryCatalog.TOKEN_MUSIC, categories.first().token)
    }

    @Test
    fun `categoriesForSource includes documents when supported by source`() {
        val source = com.sza.fastmediasorter.wear.domain.model.NetworkSource(
            type = com.sza.fastmediasorter.wear.domain.model.NetworkSourceType.SMB,
            name = "Docs Library",
            server = "192.168.1.10",
            username = "user",
            password = "pass",
            supportedMediaTypes = listOf("PDF", "TEXT")
        )
        val categories = BrowseCategoryCatalog.categoriesForSource(source, ALL_TYPES)
        assertEquals(1, categories.size)
        assertEquals(BrowseCategoryCatalog.TOKEN_DOCUMENTS, categories.first().token)
    }

    @Test
    fun `categoriesForSource includes all and browse when allFiles is true`() {
        val source = com.sza.fastmediasorter.wear.domain.model.NetworkSource(
            type = com.sza.fastmediasorter.wear.domain.model.NetworkSourceType.SMB,
            name = "Full Share",
            server = "192.168.1.10",
            username = "user",
            password = "pass",
            allFiles = true
        )
        val tokens = BrowseCategoryCatalog.categoriesForSource(source, ALL_TYPES).map { it.token }
        assertTrue(tokens.contains(BrowseCategoryCatalog.TOKEN_ALL))
        assertTrue(tokens.contains(BrowseCategoryCatalog.TOKEN_BROWSE))
        assertTrue(tokens.contains(BrowseCategoryCatalog.TOKEN_DOCUMENTS))
    }
}
