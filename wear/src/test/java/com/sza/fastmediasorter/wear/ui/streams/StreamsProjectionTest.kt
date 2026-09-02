package com.sza.fastmediasorter.wear.ui.streams

import com.sza.fastmediasorter.wear.domain.model.WearStreamChannel
import com.sza.fastmediasorter.wear.domain.model.WearStreamUsage
import com.sza.fastmediasorter.wear.domain.model.foldWearStreamIdentity
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * S2146: the new default order, tested on the projection itself rather than through the ViewModel.
 *
 * Strategic §11 criteria 4 and 5 are both statements about what `computeDisplayChannels` returns, and
 * driving it through the ViewModel would add a real-time settle for the input pause to every case
 * without observing anything the direct call does not.
 */
class StreamsProjectionTest {

    @Test
    fun `an empty counter orders exactly as name ascending`() {
        val byUsage = project(sortOrder = StreamSortOrder.MOST_USED, usage = emptyMap())
        val byName = project(sortOrder = StreamSortOrder.NAME_ASC, usage = emptyMap())

        assertEquals(listOf("Alpha FM", "Beta Radio", "Gamma TV"), byUsage.map { it.name })
        assertEquals(byName.map { it.name }, byUsage.map { it.name })
    }

    @Test
    fun `more plays outrank fewer`() {
        val result = project(
            sortOrder = StreamSortOrder.MOST_USED,
            usage = usageOf(GAMMA to (2 to 100L), ALPHA to (1 to 100L))
        )

        assertEquals(listOf("Gamma TV", "Alpha FM", "Beta Radio"), result.map { it.name })
    }

    @Test
    fun `an equal count breaks by the more recent play`() {
        val result = project(
            sortOrder = StreamSortOrder.MOST_USED,
            usage = usageOf(ALPHA to (1 to 100L), BETA to (1 to 900L))
        )

        // Beta leads on recency alone; Gamma has never been played, so it sinks below both.
        assertEquals(listOf("Beta Radio", "Alpha FM", "Gamma TV"), result.map { it.name })
    }

    @Test
    fun `pinned channels still lead after a most-used sort`() {
        val result = project(
            sortOrder = StreamSortOrder.MOST_USED,
            usage = usageOf(GAMMA to (5 to 900L)),
            pinned = setOf(foldWearStreamIdentity(BETA))
        )

        // S1954's partition runs after the sort, so the pin wins the top slot even though Gamma is the
        // most played - and the unpinned remainder keeps the order the new key gave it.
        assertEquals(listOf("Beta Radio", "Gamma TV", "Alpha FM"), result.map { it.name })
    }

    private fun project(
        sortOrder: StreamSortOrder,
        usage: Map<String, WearStreamUsage>,
        pinned: Set<String> = emptySet()
    ): List<WearStreamChannel> = computeDisplayChannels(
        ProjectionInputs(
            channels = CATALOG,
            sortOrder = sortOrder,
            usageByIdentity = usage,
            pinnedIdentities = pinned
        )
    )

    private fun usageOf(vararg entries: Pair<String, Pair<Int, Long>>): Map<String, WearStreamUsage> =
        entries.associate { (url, counts) ->
            val identity = foldWearStreamIdentity(url)
            identity to WearStreamUsage(identity, playCount = counts.first, lastPlayedAt = counts.second)
        }

    private companion object {
        const val ALPHA = "https://example.invalid/alpha"
        const val BETA = "https://example.invalid/beta"
        const val GAMMA = "https://example.invalid/gamma"

        val CATALOG = listOf(
            channel(id = "1", name = "Gamma TV", url = GAMMA, kind = "VIDEO"),
            channel(id = "2", name = "Alpha FM", url = ALPHA, kind = "AUDIO"),
            channel(id = "3", name = "Beta Radio", url = BETA, kind = "AUDIO")
        )

        fun channel(id: String, name: String, url: String, kind: String) = WearStreamChannel(
            id = id,
            name = name,
            url = url,
            mediaKind = kind
        )
    }
}
