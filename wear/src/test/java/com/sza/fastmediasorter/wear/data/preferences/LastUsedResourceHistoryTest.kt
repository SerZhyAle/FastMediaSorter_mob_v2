package com.sza.fastmediasorter.wear.data.preferences

import com.sza.fastmediasorter.wear.domain.model.LastUsedResource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S1974: none of these cases is reachable on a watch without editing DataStore by hand - a repeat
 * opening, a history longer than the widest grid, an entry left by an older build, a record that lost
 * a field - so the rules that produce them are pinned here rather than on the device.
 */
class LastUsedResourceHistoryTest {

    @Test
    fun `a history survives the joined form unchanged`() {
        val entries = listOf(LastUsedResource(FIRST_ID, FIRST_NAME), LastUsedResource(SECOND_ID, SECOND_NAME))

        assertEquals(entries, LastUsedResourceHistory.decode(LastUsedResourceHistory.encode(entries)))
    }

    @Test
    fun `an absent history decodes to nothing`() {
        assertTrue(LastUsedResourceHistory.decode(null).isEmpty())
    }

    @Test
    fun `reopening a remembered resource moves it to the front instead of repeating it`() {
        val current = listOf(LastUsedResource(FIRST_ID, FIRST_NAME), LastUsedResource(SECOND_ID, SECOND_NAME))

        val pushed = LastUsedResourceHistory.push(current, LastUsedResource(SECOND_ID, SECOND_NAME))

        assertEquals(listOf(LastUsedResource(SECOND_ID, SECOND_NAME), LastUsedResource(FIRST_ID, FIRST_NAME)), pushed)
    }

    @Test
    fun `a history never grows past the widest grid`() {
        val full = (1..LastUsedResource.HISTORY_LIMIT).map { LastUsedResource("src-$it", "name-$it") }

        val pushed = LastUsedResourceHistory.push(full, LastUsedResource(FIRST_ID, FIRST_NAME))

        assertEquals(LastUsedResource.HISTORY_LIMIT, pushed.size)
        assertEquals(FIRST_ID, pushed.first().id)
    }

    @Test
    fun `a record that lost a field is dropped rather than reported`() {
        val encoded = LastUsedResourceHistory.encode(listOf(LastUsedResource(FIRST_ID, FIRST_NAME)))

        val decoded = LastUsedResourceHistory.decode(encoded + RECORD_SEPARATOR + "orphan-id")

        assertEquals(listOf(LastUsedResource(FIRST_ID, FIRST_NAME)), decoded)
    }

    private companion object {
        const val RECORD_SEPARATOR = "\u001E"
        const val FIRST_ID = "src-7"
        const val FIRST_NAME = "MyNAS"
        const val SECOND_ID = "src-9"
        const val SECOND_NAME = "Studio"
    }
}
