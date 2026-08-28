package com.sza.fastmediasorter.wear.ui.common

import com.sza.fastmediasorter.wear.domain.browse.BrowseCategoryCatalog
import com.sza.fastmediasorter.wear.domain.model.WearBrowseCategory
import com.sza.fastmediasorter.wear.domain.model.WearCategoryOrigin
import com.sza.fastmediasorter.wear.domain.model.WearContentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * S2130: the ticket exists because four enumerations of content types drifted apart with nothing
 * failing. The label table in [BrowseCategoryPresentation] is a fifth list keyed by the same tokens,
 * so it gets the guard the other four never had: a token added to the vocabulary without a label here
 * falls through to the browse label, and the distinctness assertion below is what notices.
 */
class BrowseCategoryPresentationTest {

    private val wholeVocabulary: List<WearBrowseCategory>
        get() = BrowseCategoryCatalog.categoriesFor(
            WearCategoryOrigin.PHONE,
            WearContentType.entries.toSet()
        )

    @Test
    fun `every category in the vocabulary has a label`() {
        wholeVocabulary.forEach { category ->
            assertNotEquals(
                "no label declared for token '${category.token}'",
                0,
                BrowseCategoryPresentation.labelFor(category)
            )
        }
    }

    /** The drift guard: an unlabelled token collides with browse, and a collision is the failure. */
    @Test
    fun `no two categories share a label`() {
        val labels = wholeVocabulary.map(BrowseCategoryPresentation::labelFor)
        assertEquals(
            "two categories resolve to the same string resource - a token is missing from the table",
            labels.size,
            labels.distinct().size
        )
    }

    @Test
    fun `every category in the vocabulary has a glyph`() {
        wholeVocabulary.forEach { category ->
            assertNotEquals(
                "no glyph declared for token '${category.token}'",
                0,
                BrowseCategoryPresentation.glyphFor(category)
            )
        }
    }

    /**
     * Recents and all are both [WearContentType.OTHER], so asking the content type alone would draw
     * them identically; the override is the only thing that tells a time filter from a flat listing.
     */
    @Test
    fun `recents keeps its own glyph rather than the one its content type carries`() {
        val recents = categoryFor(BrowseCategoryCatalog.TOKEN_RECENTS)
        val all = categoryFor(BrowseCategoryCatalog.TOKEN_ALL)

        assertEquals(recents.type, all.type)
        assertNotEquals(
            "recents draws the same glyph as all, so the time filter is indistinguishable",
            BrowseCategoryPresentation.glyphFor(all),
            BrowseCategoryPresentation.glyphFor(recents)
        )
    }

    private fun categoryFor(token: String): WearBrowseCategory =
        requireNotNull(BrowseCategoryCatalog.categoryForToken(token)) {
            "the vocabulary no longer carries the '$token' token"
        }
}
