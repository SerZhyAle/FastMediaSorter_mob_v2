package com.sza.fastmediasorter.wear.ui.streams

import com.sza.fastmediasorter.wear.domain.model.WearStreamChannel
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * S2146: the facet lists are ordered by how many channels carry each value, which strategic §11
 * criterion 2 makes part of acceptance.
 *
 * Tested on the two derivations directly. They are pure functions of the catalogue, so nothing here
 * needs the ViewModel, its input pause, or an Android runtime.
 */
class StreamsFacetOrderTest {

    @Test
    fun `a more populated rubric precedes a less populated one`() {
        val facets = deriveTopicFacets(
            listOf(
                channel(id = "1", topic = "Rock"),
                channel(id = "2", topic = "News"),
                channel(id = "3", topic = "News"),
                channel(id = "4", topic = "News")
            )
        )

        assertEquals(listOf("News", "Rock"), facets.map { it.id })
        assertEquals(listOf(3, 1), facets.map { it.channelCount })
    }

    @Test
    fun `rubrics with an equal count break by id ascending`() {
        val facets = deriveTopicFacets(
            listOf(
                channel(id = "1", topic = "Sports"),
                channel(id = "2", topic = "Comedy"),
                channel(id = "3", topic = "Metal")
            )
        )

        assertEquals(listOf("Comedy", "Metal", "Sports"), facets.map { it.id })
    }

    @Test
    fun `a comma-joined cell counts towards each of its languages`() {
        val facets = deriveLanguageFacets(
            listOf(
                channel(id = "1", language = "german, french"),
                channel(id = "2", language = "german")
            )
        )

        // Split before counting: an "german, french" facet would be a row nobody could pick, and the
        // projection's filter matches the same split values.
        assertEquals(listOf("german", "french"), facets.map { it.id })
        assertEquals(listOf(2, 1), facets.map { it.channelCount })
    }

    @Test
    fun `the three interface languages lead even when another language has more channels`() {
        val facets = deriveLanguageFacets(
            List(50) { channel(id = "g$it", language = "german") } +
                listOf(
                    channel(id = "u1", language = "ukrainian"),
                    channel(id = "e1", language = "english"),
                    channel(id = "e2", language = "english"),
                    channel(id = "r1", language = "russian")
                )
        )

        // Strategic §2 goal 3. German outnumbers all three and still comes fourth; among the three the
        // order is the app's own authoring order, not their counts - english leads on 2 against
        // russian's 1, but ukrainian's 1 still precedes russian's 1 because the rank decides first.
        assertEquals(listOf("english", "russian", "ukrainian", "german"), facets.map { it.id })
        assertEquals(listOf(2, 1, 1, 50), facets.map { it.channelCount })
    }

    private fun channel(
        id: String,
        topic: String? = null,
        language: String? = null
    ) = WearStreamChannel(
        id = id,
        name = "Channel $id",
        url = "https://example.invalid/$id",
        mediaKind = "AUDIO",
        topic = topic,
        language = language
    )
}
